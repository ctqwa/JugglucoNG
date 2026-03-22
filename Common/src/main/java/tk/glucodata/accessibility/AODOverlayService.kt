package tk.glucodata.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.TextView
import tk.glucodata.Applic
import tk.glucodata.BatteryTrace
import tk.glucodata.GlucosePoint
import tk.glucodata.Natives
import tk.glucodata.NotificationHistorySource
import tk.glucodata.NotificationChartDrawer
import tk.glucodata.Notify
import tk.glucodata.R
import tk.glucodata.SensorBluetooth
import tk.glucodata.SuperGattCallback
import tk.glucodata.logic.TrendEngine
import java.util.Locale

class AODOverlayService : AccessibilityService(), SensorEventListener {
    companion object {
        private const val PERIODIC_REFRESH_MS = 60_000L
        private const val BROADCAST_FOLLOW_UP_MS = 750L
    }

    private var windowManager: WindowManager? = null
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentLuxAlpha = 1.0f // Default to full brightness
    private var cachedBaseOpacity = 1.0f // Cached from preferences

    // State for AOD behavior
    private var isScreenOn = true
    private var isLocked = false
    
    // Keep lock-screen position stable for the whole visible session.
    private var xOffset = 0
    private var yOffset = 0
    private var currentOverlayPosition = "TOP"

    private val broadcastFollowUpRunnable = Runnable {
        if (isLocked && overlayView?.visibility == View.VISIBLE) {
            BatteryTrace.bump("aod.overlay.refresh.followup", logEvery = 20L)
            updateOverlayContent()
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "tk.glucodata.action.GLUCOSE_UPDATE" -> {
                    if (isLocked && overlayView?.visibility == View.VISIBLE) {
                        BatteryTrace.bump("aod.overlay.refresh.broadcast", logEvery = 20L)
                        updateOverlayContent()
                        handler.removeCallbacks(broadcastFollowUpRunnable)
                        handler.postDelayed(broadcastFollowUpRunnable, BROADCAST_FOLLOW_UP_MS)
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    updateVisibility()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    // Immediately check keyguard state - faster than waiting for USER_PRESENT
                    checkAndUpdateLockState()
                    // Also schedule quick rechecks to catch unlock faster
                    scheduleQuickLockCheck()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // User fully unlocked - guaranteed hide
                    isLocked = false
                    updateVisibility()
                    cancelQuickLockCheck()
                }
            }
        }
    }
    
    private val quickLockCheckRunnable = object : Runnable {
        private var checkCount = 0
        override fun run() {
            checkAndUpdateLockState()
            checkCount++
            // Keep checking for up to 2 seconds after screen on (20 checks @ 100ms)
            if (isLocked && checkCount < 20) {
                handler.postDelayed(this, 100L)
            } else {
                checkCount = 0
            }
        }
        fun reset() { checkCount = 0 }
    }
    
    private fun scheduleQuickLockCheck() {
        quickLockCheckRunnable.reset()
        handler.removeCallbacks(quickLockCheckRunnable)
        handler.postDelayed(quickLockCheckRunnable, 100L)
    }
    
    private fun cancelQuickLockCheck() {
        handler.removeCallbacks(quickLockCheckRunnable)
    }
    
    private fun checkAndUpdateLockState() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        isLocked = keyguardManager.isKeyguardLocked
        updateVisibility()
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (overlayView != null && isLocked) {
                BatteryTrace.bump("aod.overlay.refresh.periodic", logEvery = 20L)
                updateOverlayContent()
                applyBurnInProtection()
                handler.postDelayed(this, PERIODIC_REFRESH_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction("tk.glucodata.action.GLUCOSE_UPDATE")
        }
        androidx.core.content.ContextCompat.registerReceiver(this, screenStateReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        isLocked = keyguardManager.isKeyguardLocked
        updateVisibility()
    }

    private fun createOverlay() {
        // View creation is handled by showOverlay() when needed.
    }

    private fun updateVisibility() {
        // Only show while the device is actually locked. Showing on mere screen-off can
        // leave stale overlays hanging around during unlock transitions.
        val shouldShow = isLocked
        
        if (shouldShow) {
            showOverlay()
        } else {
            hideOverlay()
        }
    }
    


    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            
            // Logarithmic curve for natural brightness perception
            // Similar to how Android system AOD handles it
            // log10(1) = 0, log10(100) = 2, log10(1000) = 3
            val minLux = 1f
            val maxLux = 200f
            val minAlpha = 0.3f
            val maxAlpha = 1.0f
            
            val clampedLux = lux.coerceIn(minLux, maxLux)
            // Logarithmic mapping: more sensitive at low light
            val logMin = Math.log(minLux.toDouble())
            val logMax = Math.log(maxLux.toDouble())
            val logLux = Math.log(clampedLux.toDouble())
            val normalized = ((logLux - logMin) / (logMax - logMin)).toFloat()
            
            val targetLuxAlpha = minAlpha + normalized * (maxAlpha - minAlpha)
            
            // Smoothing factor (0.15 = ~2-3 seconds to reach target)
            val smoothingFactor = 0.15f
            currentLuxAlpha = (currentLuxAlpha * (1 - smoothingFactor)) + (targetLuxAlpha * smoothingFactor)
            
            // Only update view if alpha changed significantly (> 3%)
            val newAlpha = cachedBaseOpacity * currentLuxAlpha
            val currentViewAlpha = overlayView?.alpha ?: -1f
            
            if (kotlin.math.abs(newAlpha - currentViewAlpha) > 0.03f) {
                updateAlpha()
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private fun showOverlay() {
        if (overlayView == null) {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.aod_overlay, null)
            overlayView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT
            )
            params?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params?.y = 100
            
            try {
                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {}
            chooseOverlayPosition()
        }
        updateOverlayContent()
        applyBurnInProtection(force = true)
        handler.removeCallbacks(updateRunnable)
        handler.postDelayed(updateRunnable, PERIODIC_REFRESH_MS)
        
        // Cache opacity preference (avoid reading SharedPrefs in sensor callback)
        val prefs = getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        cachedBaseOpacity = prefs.getFloat("aod_opacity", 1.0f)
        
        // Register light sensor with batching (reduces wakeups)
        lightSensor?.let {
            if (android.os.Build.VERSION.SDK_INT >= 19) {
                // Batch events for up to 1 second to reduce CPU wakeups
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 1000000)
            } else {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }
    
    private fun hideOverlay() {
        // Unregister light sensor
        sensorManager?.unregisterListener(this)
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(broadcastFollowUpRunnable)
        
        // Remove the view entirely - instant disappearance like xDrip
        val view = overlayView
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {}
            overlayView = null
            params = null
            currentOverlayPosition = "TOP"
        }
    }

    private fun chooseOverlayPosition() {
        val prefs = getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        val positions = prefs.getStringSet("aod_positions", setOf("TOP")) ?: setOf("TOP")
        val activePositions = if (positions.isNotEmpty()) positions.toList() else listOf("TOP")
        currentOverlayPosition = activePositions.random()
        xOffset = 0
        yOffset = 0
    }
    
    // Polling removed as requested
    
    private fun applyBurnInProtection(force: Boolean = false) {
        val view = overlayView ?: return
        val p = params ?: return
        
        val prefs = getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        val opacity = prefs.getFloat("aod_opacity", 1.0f)
        val textScale = prefs.getFloat("aod_text_scale", 1.0f)
        val chartScale = prefs.getFloat("aod_chart_scale", 1.5f)
        
        // Read "Positions" Set (Multi-select)
        // Default to TOP if empty or missing
        // Alignment logic
        val alignment = prefs.getString("aod_alignment", "CENTER") ?: "CENTER"
        
        // Apply Opacity (combined with light sensor)
        updateAlpha()
        
        // Apply Scaling
        val glucoseImg = view.findViewById<ImageView>(R.id.notification_glucose)
        val arrowImg = view.findViewById<ImageView>(R.id.notification_arrow)
        val chartImg = view.findViewById<ImageView>(R.id.notification_chart)
        val statusText = view.findViewById<TextView>(R.id.notification_status)

        // Text/Arrow scaling is now handled during bitmap generation/textSize setting
        // to ensure high resolution. We do not scale X/Y here.
        
        // Remove View scaling to prevent blur. Dimensions are handled in bitmap generation.
        chartImg?.scaleX = 1.0f
        chartImg?.scaleY = 1.0f
        
        // Keep the root centered and only move content inside it. This avoids visible
        // whole-overlay shifting when the value width changes on the lock screen.
        val rootLayout = view.findViewById<android.widget.LinearLayout>(R.id.aod_root)
        val textContainer = view.findViewById<android.widget.LinearLayout>(R.id.aod_text_container)
        var alignGravity = Gravity.CENTER_HORIZONTAL
        when(alignment) {
            "LEFT" -> alignGravity = Gravity.START
            "CENTER" -> alignGravity = Gravity.CENTER_HORIZONTAL
            "RIGHT" -> alignGravity = Gravity.END
        }
        if (rootLayout != null) {
             rootLayout.gravity = Gravity.CENTER_HORIZONTAL
        }
        if (textContainer != null) {
            textContainer.gravity = alignGravity
        }

        // Apply Vertical Position
        var baseGravity = Gravity.CENTER_HORIZONTAL // This is WindowManager gravity
        var baseY = 0
        
        when(currentOverlayPosition) {
            "TOP" -> {
                baseGravity = baseGravity or Gravity.TOP
                baseY = 100 // Status bar clearance
            }
            "CENTER" -> {
                baseGravity = baseGravity or Gravity.CENTER_VERTICAL
                baseY = 0
            }
            "BOTTOM" -> {
                baseGravity = baseGravity or Gravity.BOTTOM
                baseY = 100 // Nav bar clearance
            }
        }
        
        p.gravity = baseGravity
        p.x = xOffset
        p.y = baseY + yOffset
        
        try {
            windowManager?.updateViewLayout(view, p)
        } catch (e: Exception) {}
    }

    private fun updateOverlayContent() {
        val view = overlayView ?: return

        // 1. Fetch Data
        val endT = System.currentTimeMillis()
        val startT = endT - 3 * 60 * 60 * 1000L
        val isMmol = tk.glucodata.ui.util.GlucoseFormatter.isMmolApp()
        val activeSensorSerial = NotificationHistorySource.resolveSensorSerial(Natives.lastsensorname())

        var chartPoints: List<GlucosePoint>
        try {
            chartPoints = NotificationHistorySource.getDisplayHistory(startT, isMmol, activeSensorSerial)
        } catch (e: Exception) {
            chartPoints = ArrayList()
        }

        // ViewMode & Status
        var viewMode = 0
        var statusText = ""

        if (activeSensorSerial != null && SensorBluetooth.blueone != null) {
            synchronized(SensorBluetooth.gattcallbacks) {
                for (cb in SensorBluetooth.gattcallbacks) {
                    if (cb.SerialNumber != null && cb.SerialNumber == activeSensorSerial) {
                        statusText = cb.constatstatusstr
                        viewMode = Natives.getViewMode(cb.dataptr)
                        break
                    }
                }
            }
        }

        val isRawMode = (viewMode == 1 || viewMode == 3)
        val hasCalibration = tk.glucodata.NightscoutCalibration.hasCalibrationForViewMode(activeSensorSerial, viewMode)
        val hideInitialWhenCalibrated = hasCalibration &&
            tk.glucodata.data.calibration.CalibrationManager.shouldHideInitialWhenCalibrated()
        val prefs = getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        val showSecondary = prefs.getBoolean("aod_show_secondary", false)
        val trendResult = if (chartPoints.isNotEmpty()) {
            TrendEngine.calculateTrend(chartPoints, useRaw = isRawMode, isMmol = isMmol)
        } else {
            TrendEngine.TrendResult(TrendEngine.TrendState.Unknown, 0f, 0f, 0f, 0f)
        }

        // Current Value
        var glvalue = 0f
        var valStr = "---"
        var time = 0L

        val current = tk.glucodata.CurrentGlucoseSource.getFresh()

        if (current != null) {
            time = current.timeMillis
            if (current.numericValue > 0.1f) {
                glvalue = current.numericValue
                valStr = tk.glucodata.Notify.formatGlucoseText(
                    current.valueText,
                    current.numericValue,
                    chartPoints,
                    viewMode,
                    time,
                    activeSensorSerial
                ).toString()
            } else {
                // Fallback to history reconstruction only when the live string cannot be parsed.
                var rawVal = 0f
                var autoVal = 0f
                if (chartPoints.isNotEmpty()) {
                    for (i in chartPoints.size - 1 downTo 0) {
                        val p = chartPoints[i]
                        if (kotlin.math.abs(p.timestamp - time) < 60000) {
                            rawVal = p.rawValue
                            autoVal = p.value
                            break
                        }
                    }
                    if (rawVal < 0.1f && autoVal < 0.1f) {
                        val latest = chartPoints[chartPoints.size - 1]
                        rawVal = latest.rawValue
                        autoVal = latest.value
                    }
                }

                val calibratedVal = if (hasCalibration) {
                    val baseVal = if (isRawMode) rawVal else autoVal
                    if (baseVal.isFinite() && baseVal > 0.1f) {
                        val autoMgdl = if (isMmol) autoVal * 18.0182f else autoVal
                        val rawMgdl = if (isMmol) rawVal * 18.0182f else rawVal
                        val calibratedMgdl = tk.glucodata.NightscoutCalibration.getCalibratedValueForViewMode(
                            activeSensorSerial,
                            viewMode,
                            autoMgdl,
                            rawMgdl,
                            time
                        )
                        if (calibratedMgdl > 0f && isMmol) calibratedMgdl / 18.0182f else calibratedMgdl
                    } else {
                        0f
                    }
                } else 0f

                valStr = when {
                    hasCalibration && hideInitialWhenCalibrated && viewMode == 2 -> {
                        val calText = tk.glucodata.ui.util.GlucoseFormatter.format(calibratedVal, isMmol)
                        val rawSecondary = rawVal.takeIf { it > 0.1f }?.let {
                            tk.glucodata.ui.util.GlucoseFormatter.format(it, isMmol)
                        }
                        if (rawSecondary != null) "$calText · $rawSecondary" else calText
                    }
                    hasCalibration && hideInitialWhenCalibrated && viewMode == 3 -> {
                        val calText = tk.glucodata.ui.util.GlucoseFormatter.format(calibratedVal, isMmol)
                        val autoSecondary = autoVal.takeIf { it > 0.1f }?.let {
                            tk.glucodata.ui.util.GlucoseFormatter.format(it, isMmol)
                        }
                        if (autoSecondary != null) "$calText · $autoSecondary" else calText
                    }
                    hasCalibration && hideInitialWhenCalibrated -> {
                        tk.glucodata.ui.util.GlucoseFormatter.format(calibratedVal, isMmol)
                    }
                    hasCalibration && (viewMode == 2 || viewMode == 3) -> {
                        val secondary = if (viewMode == 3) tk.glucodata.ui.util.GlucoseFormatter.format(rawVal, isMmol) else tk.glucodata.ui.util.GlucoseFormatter.format(autoVal, isMmol)
                        val tertiary = if (viewMode == 3) tk.glucodata.ui.util.GlucoseFormatter.format(autoVal, isMmol) else tk.glucodata.ui.util.GlucoseFormatter.format(rawVal, isMmol)
                        "${tk.glucodata.ui.util.GlucoseFormatter.format(calibratedVal, isMmol)} · $secondary · $tertiary"
                    }
                    hasCalibration -> {
                        val base = if (isRawMode) tk.glucodata.ui.util.GlucoseFormatter.format(rawVal, isMmol) else tk.glucodata.ui.util.GlucoseFormatter.format(autoVal, isMmol)
                        "${tk.glucodata.ui.util.GlucoseFormatter.format(calibratedVal, isMmol)} · $base"
                    }
                    viewMode == 2 || viewMode == 3 -> {
                        val primary = if (viewMode == 3) tk.glucodata.ui.util.GlucoseFormatter.format(rawVal, isMmol) else tk.glucodata.ui.util.GlucoseFormatter.format(autoVal, isMmol)
                        val secondary = if (viewMode == 3) tk.glucodata.ui.util.GlucoseFormatter.format(autoVal, isMmol) else tk.glucodata.ui.util.GlucoseFormatter.format(rawVal, isMmol)
                        "$primary / $secondary"
                    }
                    else -> {
                        tk.glucodata.ui.util.GlucoseFormatter.format(if (isRawMode) rawVal else autoVal, isMmol)
                    }
                }

                glvalue = if (hasCalibration) calibratedVal else if (isRawMode) rawVal else autoVal
            }
        } else if (chartPoints.isNotEmpty()) {
            val p = chartPoints[chartPoints.size - 1]
            glvalue = p.value
            try {
                valStr = tk.glucodata.ui.util.GlucoseFormatter.format(glvalue, isMmol)
            } catch (e: Exception) {}
        }

        if (!showSecondary) {
            valStr = valStr.substringBefore(" · ").substringBefore(" · ")
        }

        val glucoseColor = NotificationChartDrawer.getGlucoseColor(this, glvalue, isMmol)

        // Draw Components
        val fontSource = prefs.getString("aod_font_source", "APP") ?: "APP"
        val fontWeight = prefs.getInt("aod_font_weight", 400)
        val textScale = prefs.getFloat("aod_text_scale", 1.5f)
        val chartScale = prefs.getFloat("aod_chart_scale", 1.5f) // Fetch chart scale too
        val useSystemFont = fontSource == "SYSTEM"
        
        // Arrow Settings
        val showArrow = prefs.getBoolean("aod_show_arrow", true)
        val arrowScale = prefs.getFloat("aod_arrow_scale", 2.0f)

        // Use textScale here for high-res bitmap generation
        val textBitmap = NotificationChartDrawer.drawGlucoseText(this, valStr, glucoseColor, textScale, fontWeight, useSystemFont)
        val textImg = view.findViewById<ImageView>(R.id.notification_glucose)
        textImg?.setImageBitmap(textBitmap)

        // Pass combined scale to arrow
        val arrowImg = view.findViewById<ImageView>(R.id.notification_arrow)
        if (showArrow) {
            val arrowBitmap = NotificationChartDrawer.drawArrow(
                this,
                trendResult.velocity,
                isMmol,
                glucoseColor,
                textScale * arrowScale
            )
            arrowImg?.setImageBitmap(arrowBitmap)
            arrowImg?.visibility = View.VISIBLE
        } else {
            arrowImg?.visibility = View.GONE
        }

// CHART VISIBILITY CHECK
        // prefs already defined above
        val showChart = prefs.getBoolean("aod_show_chart", true)
        val chartImg = view.findViewById<ImageView>(R.id.notification_chart)

        if (showChart) {
            val dm = resources.displayMetrics
            val baseChartHeightPx = (200 * dm.density).toInt()
            val renderWidth = (dm.widthPixels * 1.5f).toInt()
            val renderHeight = (baseChartHeightPx * 1.5f).toInt()

            val chartBitmap = NotificationChartDrawer.drawChart(
                this,
                chartPoints,
                renderWidth,
                renderHeight,
                isMmol,
                viewMode,
                true,
                hasCalibration,
                activeSensorSerial
            )
            if (chartImg != null) {
                chartImg.layoutParams = chartImg.layoutParams?.also { params ->
                    params.height = (baseChartHeightPx * chartScale).toInt()
                }
                chartImg.setImageBitmap(chartBitmap)
                chartImg.visibility = View.VISIBLE
            }
        } else {
             chartImg?.visibility = View.GONE
        }

        val statusView = view.findViewById<TextView>(R.id.notification_status)
        if (statusView != null) {
            // Apply Scaling
            statusView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f * textScale)

            if (statusText.isNotEmpty()) {
                statusView.visibility = View.GONE
                statusView.text = statusText
            } else {
                statusView.visibility = View.GONE
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (isScreenOn && packageName != null && !isLockscreenPackage(packageName)) {
                isLocked = false
                cancelQuickLockCheck()
                updateVisibility()
            } else {
                checkAndUpdateLockState()
            }
        }
    }

    override fun onInterrupt() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {}
            overlayView = null
        }
        try {
            unregisterReceiver(screenStateReceiver)
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {}
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(broadcastFollowUpRunnable)
    }

    private fun isLockscreenPackage(packageName: String): Boolean {
        return packageName == "com.android.systemui" || packageName.contains("systemui")
    }
    
    private fun updateAlpha() {
        val view = overlayView ?: return
        view.alpha = cachedBaseOpacity * currentLuxAlpha
    }
    


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
