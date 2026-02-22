package tk.glucodata.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tk.glucodata.R
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.glance.appwidget.compose

@Composable
fun DashboardChartSection(
    modifier: Modifier,
    glucoseHistory: List<GlucosePoint>,
    targetLow: Float,
    targetHigh: Float,
    unit: String,
    viewMode: Int,
    onTimeRangeSelected: (TimeRange) -> Unit,
    selectedTimeRange: TimeRange,
    isExpanded: Boolean = false,
    expandedProgress: Float = if (isExpanded) 1f else 0f,
    onToggleExpanded: (() -> Unit)? = null,
    calibrations: List<tk.glucodata.data.calibration.CalibrationEntity> = emptyList(),
    onPointClick: ((GlucosePoint) -> Unit)? = null,
    onCalibrationClick: ((tk.glucodata.data.calibration.CalibrationEntity) -> Unit)? = null
) {
    val chartContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(bottom = 0.dp)) {
             Box(modifier = Modifier.weight(1f)) {
                if (glucoseHistory.isNotEmpty()) {
                    InteractiveGlucoseChart(
                        fullData = glucoseHistory,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        unit = unit,
                        viewMode = viewMode,
                        calibrations = calibrations,
                        onTimeRangeSelected = onTimeRangeSelected,
                        selectedTimeRange = selectedTimeRange,
                        isExpanded = isExpanded,
                        expandedProgress = expandedProgress,
                        onToggleExpanded = onToggleExpanded,
                        onPointClick = onPointClick,
                        onCalibrationClick = onCalibrationClick
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_data_available)) }
                }
                }
        }
    }
    val safeExpandedProgress = expandedProgress.coerceIn(0f, 1f)
    val collapseVisualProgress = (((1f - safeExpandedProgress) - 0.06f) / 0.94f).coerceIn(0f, 1f)
    val containerColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        collapseVisualProgress
    )
    val cornerRadius = (16.dp * collapseVisualProgress).coerceAtLeast(0.dp)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        tonalElevation = 2.dp * collapseVisualProgress,
        shadowElevation = 0.dp
    ) {
        chartContent()
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun InteractiveGlucoseChart(
    fullData: List<GlucosePoint>,
    targetLow: Float,
    targetHigh: Float,
    unit: String,
    viewMode: Int = 0,
    calibrations: List<tk.glucodata.data.calibration.CalibrationEntity> = emptyList(),
    onDateSelected: (Long) -> Unit = {},
    onTimeRangeSelected: ((TimeRange) -> Unit)? = null,
    selectedTimeRange: TimeRange? = null,
    isExpanded: Boolean = false,
    expandedProgress: Float = if (isExpanded) 1f else 0f,
    onToggleExpanded: (() -> Unit)? = null,
    onPointClick: ((GlucosePoint) -> Unit)? = null,
    onCalibrationClick: ((tk.glucodata.data.calibration.CalibrationEntity) -> Unit)? = null
) {
    // --- THEME & PAINTS ---
    val isDark = isSystemInDarkTheme()
    // User requested stronger dark mode lines ("oddly pale").
    // Standard M3 dark primary is pastel. We use a more saturated blue for data.
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val tertiaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) // Lighter shade for 3rd line
    val pointColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.125f)
    // 1. Select the correct Material Green shade (300 for Dark, 700 for Light)
    val materialGreen = if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)
    // 2. Apply "Container" level opacity (0.12f is standard for M3 highlights)
    val targetBandColor = materialGreen.copy(alpha = 0.12f)
//    val targetBandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val hoverLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val minMaxLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val safeExpandedProgress = expandedProgress.coerceIn(0f, 1f)
    val chartLayoutConfiguration = LocalConfiguration.current
    val isCompactChartLayout = chartLayoutConfiguration.screenWidthDp <= 360 || chartLayoutConfiguration.screenHeightDp <= 700
    val chartUnderlayBottomDp = ((if (isCompactChartLayout) 88f else 112f) * safeExpandedProgress).dp
    val chartUnderlayBottomPx = with(LocalDensity.current) { chartUnderlayBottomDp.toPx() }
    val chartUnderlayBottomIntPx = with(LocalDensity.current) { chartUnderlayBottomDp.roundToPx() }
    val chartPlotBottomGapPx = with(LocalDensity.current) { (4.dp * safeExpandedProgress).toPx() }
    val bottomAxisHeightPx = with(LocalDensity.current) { 32.dp.toPx() }
    val axisLabelBackgroundColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        safeExpandedProgress
    )

    // Using colors from StatsScreen.kt as requested
    val TirVeryLowColor = Color(0xFFF0A24A)
    val TirLowColor = Color(0xFFE7C85A)
    val TirHighColor = Color(0xFFC56F33)
    val TirVeryHighColor = Color(0xFFA44B2D)

    // Using the "High" color for the generic high tint base, and "Low" for low tint base
    // Adjusting alpha for visibility on graph background
    val lowOutOfRangeTintBase = TirLowColor
    val highOutOfRangeTintBase = TirHighColor


    val context = LocalContext.current
    // 1. Get Vibrator & Check Capabilities ONCE (Performance)
    val hapticsConfig = remember(context) {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(Vibrator::class.java)
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Check if the device has an advanced haptic engine (Linear Resonant Actuator)
        // primitive_low_tick is the best proxy for "High Quality Motor"
        val hasCrispHaptics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vib?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) == true
        } else {
            false
        }

        Triple(vib, hasCrispHaptics, context)
    }

    val (vibrator, hasCrispHaptics, _) = hapticsConfig

    // 2. Throttling State
    // We store the last time we ACTUALLY vibrated to prevent "buzzing"
    var lastHapticExecutionTime by remember { mutableLongStateOf(0L) }


    val view = LocalView.current
    val density = context.resources.displayMetrics.density
    val dashboardPrefs = remember(context) {
        context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
    }
    var scrubValueLabelOffsetDp by rememberSaveable {
        mutableFloatStateOf(dashboardPrefs.getFloat("dashboard_scrub_value_label_offset_dp", 48f))
    }
    var scrubTimeLabelOffsetDp by rememberSaveable {
        mutableFloatStateOf(dashboardPrefs.getFloat("dashboard_scrub_time_label_offset_dp", 2f))
    }
    var isAdjustingScrubLabel by remember { mutableStateOf(false) }
    
    val graphFont = remember(context) {
        ResourcesCompat.getFont(context, R.font.ibm_plex_sans_var)
    }
    val graphFontBold = remember(graphFont) {
        if (graphFont != null) android.graphics.Typeface.create(graphFont, android.graphics.Typeface.BOLD) else android.graphics.Typeface.DEFAULT_BOLD
    }

    // Paints
    val axisTextPaint = remember(graphFont) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 10f * density
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = graphFont
        }
    }
    val xTextPaint = remember(graphFont) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 10f * density
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = graphFont
        }
    }


    // --- ONE-TIME INIT ---
    // Ensure Fast Random Access for the drawing loop (critical for performance)
    val safeData = remember(fullData) {
        if (fullData is java.util.RandomAccess) fullData else ArrayList(fullData)
    }

    // --- FORMATTERS & TOOLS (Hoisted for Performance) ---
    val cal = remember { java.util.Calendar.getInstance() }
    val formatDate = remember { java.text.SimpleDateFormat("EEE dd", java.util.Locale.getDefault()) }

    // Reusable objects to avoid allocation on every frame
    val reusablePath = remember { Path() }
    val reusableRawPath = remember { Path() }
    val reusableAutoPath = remember { Path() }
    val reusableDate = remember { java.util.Date() }

    // Hoist intervals array to avoid allocation in Canvas loop
    val gridIntervals = remember {
        longArrayOf(
            5 * 60 * 1000L,      // 5m
            15 * 60 * 1000L,     // 15m
            30 * 60 * 1000L,     // 30m
            60 * 60 * 1000L,     // 1h
            2 * 60 * 60 * 1000L, // 2h
            4 * 60 * 60 * 1000L, // 4h
            8 * 60 * 60 * 1000L, // 8h
            12 * 60 * 60 * 1000L,// 12h
            24 * 60 * 60 * 1000L // 24h
        )
    }

    // Hoisted PathEffect for dashed lines (Zero-Allocation)
    val dashEffect = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }

    // Label Cache to avoid SimpleDateFormat overhead during scroll
    // Maps Timestamp -> Formatted String
    val labelCache = remember { mutableMapOf<Long, String>() }
    val axisLabelBounds = remember { android.graphics.Rect() }
    val indicatorLabelBounds = remember { android.graphics.Rect() }

    // --- VIEWPORT STATE ---
    val now = System.currentTimeMillis()
    val latestDataTimestamp = safeData.lastOrNull()?.timestamp ?: 0L
    val earliestDataTimestamp = safeData.firstOrNull()?.timestamp ?: 0L

    var lastAutoScrolledTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    // Jitter fix: Track the auto-scroll job to cancel it on user interaction
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    // FIX: Single source of truth for duration, initialized from range but independent thereafter.
    // Explicit key "visibleDuration" ensures it's saved/restored correctly across recompositions and navigations.
    // NOTE: Removed 'key' parameter to fix deprecation warning. Relying on default saving.
    // We now manage 'lastAppliedTimeRange' to ensure manual zooms aren't overwritten.
    var visibleDuration by rememberSaveable { 
        mutableLongStateOf((selectedTimeRange?.hours?.toLong() ?: 3L) * 60L * 60L * 1000L) 
    }
    
    var preZoomDuration by rememberSaveable { mutableLongStateOf(0L) } // For toggle zoom
    var centerTime by rememberSaveable { mutableLongStateOf(now - visibleDuration / 2) }

    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = centerTime,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // Only allow selecting dates we have data for
                return utcTimeMillis >= earliestDataTimestamp && utcTimeMillis <= now
            }
        }
    )

    // Auto-scroll logic: Only jump if we are explicitly RESUMED (Active)
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    // Flag to detect immediate Resume/Startup so we can FORCE snap to latest
    // (ignoring the 1h check initially) as per User Request.
    var justResumed by remember { mutableStateOf(true) }
    var isUserInteracting by remember { mutableStateOf(false) }
    var lastInteractionTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    var suppressDoubleTapUntil by rememberSaveable { mutableLongStateOf(0L) }

    // TRACKING INACTIVITY FOR GRAPH RESET
    var lastActiveTime by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
                justResumed = true

                val currentTime = System.currentTimeMillis()
                // Check for 10-minute timeout (10 * 60 * 1000 = 600000 ms)
                if (currentTime - lastActiveTime > 600000) {
                    // TIMEOUT EXCEEDED: Reset Graph State
                    if (latestDataTimestamp > 0) {
                        visibleDuration = (selectedTimeRange?.hours?.toLong() ?: 3L) * 60 * 60 * 1000
                        lastAutoScrolledTimestamp = 0L // Reset auto-scroll memory
                        centerTime = latestDataTimestamp - visibleDuration / 2 // Snap to live
                    }
                }
            }
            else if (event == Lifecycle.Event.ON_PAUSE) {
                isResumed = false
                lastActiveTime = System.currentTimeMillis() // Save time on pause
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // FIX: React to Time Range Selection explicitly.
    // Ensure we don't clobber the user's manual zoom if they just navigated away and back.
    // 'lastAppliedRangeName' tracks the last *user-selected* time range applied to this chart.
    // If 'selectedTimeRange' (prop) matches what we last applied, we do nothing (preserve zoom).
    // If it differs (user clicked a button in parent), we update.
    var lastAppliedRangeName by rememberSaveable { mutableStateOf(selectedTimeRange?.name) }

    LaunchedEffect(selectedTimeRange) {
        if (selectedTimeRange != null && selectedTimeRange.name != lastAppliedRangeName) {
            lastAppliedRangeName = selectedTimeRange.name
            val target = selectedTimeRange.hours * 60 * 60 * 1000L
            if (visibleDuration != target) {
                visibleDuration = target
                // Snap to latest data when changing range for immediate feedback
                if (latestDataTimestamp > 0) {
                     centerTime = latestDataTimestamp - target / 2
                }
            }
        }
    }

    LaunchedEffect(latestDataTimestamp, isResumed, isUserInteracting, autoScrollJob, lastInteractionTimestamp) {
        if (isResumed && latestDataTimestamp > lastAutoScrolledTimestamp) {
            if (isUserInteracting || autoScrollJob != null || System.currentTimeMillis() - lastInteractionTimestamp < 1200L) {
                return@LaunchedEffect
            }
            val currentEnd = centerTime + visibleDuration / 2

            // Robust Logic:
            // 1. If we JUST Resumed (or started), we force snap (User: "exited via Home... regardless of 1h").
            // 2. If we are Active/Monitoring (last update was recent), we snap.
            // 3. If we are Active but viewing History (dist > 1h), we STAY PUT.

            val dist = kotlin.math.abs(lastAutoScrolledTimestamp - currentEnd)
            val isMonitoring = lastAutoScrolledTimestamp == 0L || dist < 60 * 60 * 1000

            // User request: Don't snap just because we resumed unless we were monitoring. 
            // We rely on timeout logic in ON_RESUME to handle stale state for long absences.
            if (isMonitoring) {
                centerTime = latestDataTimestamp - visibleDuration / 2
            }
            lastAutoScrolledTimestamp = latestDataTimestamp

            // Clear flag after processing the "Resume" frame
            justResumed = false
        }
    }

    // --- Y-AXIS STATE (Manual Scaling) ---
    val isMmol = if (unit.isNotEmpty()) tk.glucodata.ui.util.GlucoseFormatter.isMmol(unit) else tk.glucodata.ui.util.GlucoseFormatter.isMmolApp()
    val defaultMin = if (isMmol) 0.1f else 2f
    val defaultMax = if (isMmol) 13.5f else 236f
    val minYAxisSpan = if (isMmol) 6f else 108f

    // --- Y-AXIS SCALING ---
    var yMin by rememberSaveable { mutableFloatStateOf(defaultMin) }
    var yMax by rememberSaveable { mutableFloatStateOf(defaultMax) }

    // --- INTERACTION STATE ---
    var selectedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    var isScrubbing by remember { mutableStateOf(false) } // Touching the line?
    var lastScrubHapticTimestamp by remember { mutableLongStateOf(Long.MIN_VALUE) }

    // Auto-dismiss selection if off-screen (User Request)
    LaunchedEffect(centerTime, visibleDuration, selectedPoint) {
        selectedPoint?.let { p ->
            val start = centerTime - visibleDuration / 2
            val end = centerTime + visibleDuration / 2
            // Allow a small buffer so it doesn't flicker on edge
            if (p.timestamp < start || p.timestamp > end) {
                selectedPoint = null
            }
        }
    }

    // Physics / Animation
    val coroutineScope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }
    val inertiaAnim = remember { Animatable(0f) }

    // Limits
    val minDuration = 10L * 60 * 1000
    val maxDuration = 72L * 60 * 60 * 1000
    val maxAllowedTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000)

    fun cancelAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun startAutoScrollTo(targetTime: Long) {
        cancelAutoScroll()
        val maxScroll = 12 * 60 * 60 * 1000L
        val job = coroutineScope.launch {
            val diff = targetTime - centerTime
            var startScroll = centerTime
            if (abs(diff) > maxScroll) {
                startScroll = targetTime - (if (diff > 0) maxScroll else -maxScroll)
                centerTime = startScroll
            }
            androidx.compose.animation.core.Animatable(startScroll.toFloat()).animateTo(
                targetValue = targetTime.toFloat(),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) {
                centerTime = value.toLong()
            }
        }
        autoScrollJob = job
        job.invokeOnCompletion {
            if (autoScrollJob === job) {
                autoScrollJob = null
            }
        }
    }

//    fun performSubtleTick(isFrequent: Boolean = false) {
//        val feedbackType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
//            if (isFrequent) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
//        } else {
//            if (isFrequent) HapticFeedbackConstants.NO_HAPTICS else HapticFeedbackConstants.KEYBOARD_TAP
//        }
//        view.performHapticFeedback(feedbackType)
//    }

    fun performSubtleTick(isFrequent: Boolean = false) {
        val now = System.currentTimeMillis()

        // --- SAFEGUARD: DYNAMIC RATE LIMITING ---
        // High-End: 15ms gap (allows ~60 ticks/sec). Feels like "texture".
        // Low-End: 70ms gap (allows ~14 ticks/sec). Ensures motor stops spinning between ticks.
        val minGap = if (hasCrispHaptics) 15L else 70L

        if (now - lastHapticExecutionTime < minGap) {
            return // Skip this tick to save the user's sanity
        }

        lastHapticExecutionTime = now

        // --- EXECUTION ---
        if (hasCrispHaptics && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // High-End Path: Variable amplitude
                val amplitude = if (isFrequent) 0.2f else 0.5f
                vibrator?.vibrate(
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, amplitude)
                        .compose()
                )
            } catch (e: Exception) {
                // Fallback safe
            }
        } else {
            // Low-End Path: Standard View Haptics
            // We use KEYBOARD_TAP or VIRTUAL_KEY as they are usually shorter than CLOCK_TICK
            // on older Android versions.
            val feedbackConstant = if (isFrequent) {
                // On old phones, TEXT_HANDLE_MOVE is often the quietest standard constant
                HapticFeedbackConstants.TEXT_HANDLE_MOVE
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(feedbackConstant)
        }
    }

    fun markProgrammaticViewportChange(now: Long = System.currentTimeMillis()) {
        lastInteractionTimestamp = now
        suppressDoubleTapUntil = now + 450L
    }

    // --- DATA CAPTURE FOR GESTURES ---
    // Use rememberUpdatedState to ensure the running gesture coroutine always sees the latest data
    val currentSafeData by rememberUpdatedState(safeData)
    val currentViewMode by rememberUpdatedState(viewMode)

    // --- DATA HELPER (Fixed Interpolation) ---
    fun getPointAt(timeAtTapRaw: Double): GlucosePoint? {
        val data = currentSafeData // Always use fresh data
        val minuteInMillis = 60000.0
        val snappedTime = (kotlin.math.round(timeAtTapRaw / minuteInMillis) * minuteInMillis).toLong()

        // Manual Binary Search (Zero-Allocation)
        var low = 0
        var high = data.size - 1
        var idx = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = data[mid].timestamp
            if (midVal < snappedTime) low = mid + 1
            else if (midVal > snappedTime) high = mid - 1
            else { idx = mid; break }
        }
        
        if (idx >= 0) return data[idx]

        // Find closest neighbor
        val insPoint = low

        if (insPoint >= data.size) return data.lastOrNull()
        if (insPoint <= 0) return data.firstOrNull()

        val p1 = data[insPoint - 1]
        val p2 = data[insPoint]
        return if (kotlin.math.abs(p1.timestamp - snappedTime) < kotlin.math.abs(p2.timestamp - snappedTime)) p1 else p2
    }

    fun performScrubHaptic(point: GlucosePoint?) {
        if (point == null) return
        if (point.timestamp != lastScrubHapticTimestamp) {
            performSubtleTick(isFrequent = true)
            lastScrubHapticTimestamp = point.timestamp
        }
    }





    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    // Manual Gesture Handler for:
                    // 1. Pan / Inertia
                    // 2. Pinch Zoom
                    // 3. One-Finger Zoom (Double-Tap + Drag)
                    // 4. Tap to Select

                    var lastGestureWasTap = false
                    var lastTapTime = 0L
                    var lastTapPos = Offset.Zero

                    awaitEachGesture {
                        isUserInteracting = true
                        try {
                            // FIX: Use requireUnconsumed = true (default) to respect z-order.
                            // This prevents the chart from hijacking touches meant for the floating buttons.
                            val down = awaitFirstDown()
                            val gestureStartTime = System.currentTimeMillis()
                            lastInteractionTimestamp = gestureStartTime
                            cancelAutoScroll()
                            if (isAdjustingScrubLabel) {
                                down.consume()
                                while (true) {
                                    val holdEvent = awaitPointerEvent()
                                    holdEvent.changes.forEach { it.consume() }
                                    if (holdEvent.changes.any { it.changedToUp() }) break
                                }
                                return@awaitEachGesture
                            }

                            // STRICT DOUBLE TAP DETECTION
                            // Only trigger if:
                            // 1. Previous gesture was a tap (not a scroll)
                            // 2. Short duration since then (<300ms)
                            // 3. Close spatial proximity (<100px)
                            val isDoubleTapStart = gestureStartTime >= suppressDoubleTapUntil &&
                                    lastGestureWasTap &&
                                    (gestureStartTime - lastTapTime < 300) &&
                                    (down.position - lastTapPos).getDistance() < 100.dp.toPx()

                            var isOneFingerZoom = isDoubleTapStart

                            // Kill inertia
                            coroutineScope.launch { inertiaAnim.snapTo(0f) }
                            velocityTracker.resetTracking()

                            // --- HIT TEST (Hit Logic reused) ---
                            val width = size.width.toFloat()
                            val rightPaddingPx = (16.dp.toPx() * safeExpandedProgress)
                            val usefulWidth = (width - rightPaddingPx).coerceAtLeast(1f)
                            val contentHeight =
                                (size.height.toFloat() - chartUnderlayBottomPx).coerceAtLeast(1f)
                            val chartHeight =
                                (contentHeight - 32.dp.toPx() - chartPlotBottomGapPx).coerceAtLeast(
                                    1f
                                )
                            val downDuration = visibleDuration
                            val downCenterTime = centerTime
                            val viewportStart = downCenterTime - downDuration / 2
                            val downX = down.position.x.coerceIn(0f, usefulWidth)
                            val timeAtTouch =
                                viewportStart + ((downX / usefulWidth).toDouble() * downDuration)
                            val pointAtTouch = getPointAt(timeAtTouch)
                            var touchThreshold = 32.dp.toPx()

                            // Only allow scrubbing if purely single tap start (not double tap sequence)
                            isScrubbing = if (pointAtTouch != null && !isOneFingerZoom) {
                                val timeDiff = timeAtTouch - pointAtTouch.timestamp
                                if (timeDiff > 15 * 60 * 1000) false else {
                                    // When calibration is on and is primary, use calibrated value for touch target
                                    val isRawMode = currentViewMode == 1 || currentViewMode == 3
                                    val hasCalibration =
                                        tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(
                                            isRawMode
                                        )
                                    val v = if (hasCalibration) {
                                        val baseV =
                                            if (isRawMode) pointAtTouch.rawValue else pointAtTouch.value
                                        tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(
                                            baseV,
                                            pointAtTouch.timestamp,
                                            isRawMode
                                        )
                                    } else if (isRawMode) {
                                        pointAtTouch.rawValue
                                    } else {
                                        pointAtTouch.value
                                    }
                                    val liveYMin = yMin
                                    val liveYMax = yMax
                                    val dataY =
                                        chartHeight - ((v - liveYMin) / (liveYMax - liveYMin)) * chartHeight
                                    abs(down.position.y - dataY) < touchThreshold
                                }
                            } else {
                                false
                            }

                            if (isScrubbing) {
                                selectedPoint = pointAtTouch
                                performScrubHaptic(pointAtTouch)
                            }

                            var change = down
                            var totalDragDistance = 0f
                            var lastPointerCount = 1

                            while (true) {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.count { it.pressed }
                                val newChange = event.changes.firstOrNull { it.pressed }
                                    ?: event.changes.firstOrNull() ?: break
                                if (pointerCount == 0 || newChange.changedToUp()) break
                                if (pointerCount != lastPointerCount) {
                                    change = newChange
                                    lastPointerCount = pointerCount
                                    velocityTracker.resetTracking()
                                    continue
                                }
                                if (isAdjustingScrubLabel) {
                                    event.changes.forEach { it.consume() }
                                    change = newChange
                                    continue
                                }

                                velocityTracker.addPointerInputChange(newChange)
                                if (pointerCount > 1) {
                                    isOneFingerZoom = false
                                }

                                if (isOneFingerZoom && pointerCount == 1) {
                                    // ONE FINGER ZOOM MODE (Double-Tap-Drag)
                                    val panY = newChange.position.y - change.position.y

                                    // Only apply zoom if there's meaningful vertical movement
                                    if (abs(panY) > 2f) {
                                        // EXPONENTIAL ZOOM (Smoother feel)
                                        // panY > 0 (Down) -> Zoom IN (Duration shrinks)
                                        // Form: newDur = oldDur * exp(-panY * sensitivity)
                                        val zoomSensitivity =
                                            3f / contentHeight // Adjust constant for speed
                                        val zoomFactor = kotlin.math.exp(-panY * zoomSensitivity)

                                        val newDuration = (visibleDuration * zoomFactor).toLong()
                                        visibleDuration =
                                            newDuration.coerceIn(minDuration, maxDuration)
                                        totalDragDistance += abs(panY) // Mark as dragged, not tap
                                    }
                                    newChange.consume()

                                } else if (pointerCount > 1) {
                                    // 2-FINGER ZOOM
                                    totalDragDistance += viewConfiguration.touchSlop
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        val effectiveZoom = 1f + (zoomChange - 1f) * 2.0f
                                        val newDuration = (visibleDuration / effectiveZoom).toLong()
                                        visibleDuration =
                                            newDuration.coerceIn(minDuration, maxDuration)
                                    }
                                    event.changes.forEach { it.consume() }
                                } else {
                                    // 1-FINGER PAN / SCRUB
                                    if (isScrubbing) {
                                        val clampedX =
                                            newChange.position.x.coerceIn(0f, usefulWidth)
                                        val currentFrac = (clampedX / usefulWidth).toDouble()
                                        val currentViewportStart = centerTime - visibleDuration / 2
                                        val currentTime =
                                            currentViewportStart + (currentFrac * visibleDuration)
                                        val updatedPoint = getPointAt(currentTime)
                                        selectedPoint = updatedPoint
                                        performScrubHaptic(updatedPoint)
                                    } else {
                                        val panX = newChange.position.x - change.position.x
                                        val panY = newChange.position.y - change.position.y
                                        val dragDist = kotlin.math.sqrt(panX * panX + panY * panY)
                                        totalDragDistance += dragDist

                                        if (abs(panX) > abs(panY)) {
                                            // Horizontal pan
                                            val timePerPixel =
                                                visibleDuration.toFloat() / usefulWidth
                                            val timeDelta = -(panX * timePerPixel).toLong()
                                            centerTime =
                                                (centerTime + timeDelta).coerceAtMost(maxAllowedTime)
                                        } else if (totalDragDistance > 30f) {
                                            // Vertical scale
                                            val liveYMin = yMin
                                            val liveYMax = yMax
                                            val scaleFactor =
                                                panY * (liveYMax - liveYMin) / contentHeight * 2f
                                            if (change.position.y < contentHeight / 2f) {
                                                yMax =
                                                    (liveYMax + scaleFactor).coerceAtLeast(liveYMin + minYAxisSpan)
                                            } else {
                                                yMin = (liveYMin + scaleFactor).coerceAtLeast(0f)
                                            }
                                        }
                                    }
                                    newChange.consume()
                                }
                                change = newChange
                                lastPointerCount = pointerCount
                            }

                            // ON UP
                            val wasTap = totalDragDistance < viewConfiguration.touchSlop
                            lastGestureWasTap = wasTap && !isOneFingerZoom && !isScrubbing

                            if (wasTap) {
                                lastTapTime = System.currentTimeMillis()
                                lastTapPos = change.position
                            }

                            if (!isScrubbing) {
                                if (wasTap) {
                                    // TAP DETECTED
                                    if (isOneFingerZoom) {
                                        // DOUBLE TAP TOGGLE ZOOM
                                        if (preZoomDuration > 0) {
                                            visibleDuration = preZoomDuration
                                            preZoomDuration = 0L
                                        } else {
                                            preZoomDuration = visibleDuration
                                            visibleDuration = (visibleDuration / 2f).toLong()
                                                .coerceIn(minDuration, maxDuration)
                                        }
                                    } else {
                                        // SINGLE TAP (Selection)
                                        val isFutureTap = pointAtTouch != null &&
                                                pointAtTouch.timestamp == currentSafeData.lastOrNull()?.timestamp &&
                                                timeAtTouch > pointAtTouch.timestamp

                                        if (isFutureTap) selectedPoint =
                                            pointAtTouch else selectedPoint = null
                                    }
                                } else if (!isOneFingerZoom) {
                                    // FLING - simple defaults
                                    val velocity = velocityTracker.calculateVelocity()
                                    val vx = velocity.x
                                    if (abs(vx) > 1000f) { // High threshold - ignore small movements
                                        // Velocity-dependent boost: fast swipes get more acceleration
                                        val boost =
                                            if (abs(vx) > 3000f) 2f else if (abs(vx) > 2000f) 1.5f else 1f
                                        coroutineScope.launch {
                                            var lastVal = 0f
                                            inertiaAnim.snapTo(0f)
                                            inertiaAnim.animateDecay(
                                                initialVelocity = -vx * boost,
                                                animationSpec = exponentialDecay(frictionMultiplier = 2.0f) // Higher friction = stops faster
                                            ) {
                                                val delta = this.value - lastVal
                                                val rightPaddingPx =
                                                    (16.dp.toPx() * safeExpandedProgress)
                                                val usefulWidth =
                                                    (size.width.toFloat() - rightPaddingPx).coerceAtLeast(
                                                        1f
                                                    )
                                                val tPerPix =
                                                    visibleDuration.toFloat() / usefulWidth
                                                centerTime =
                                                    (centerTime + (delta * tPerPix).toLong()).coerceAtMost(
                                                        maxAllowedTime
                                                    )
                                                lastVal = this.value
                                            }
                                        }
                                    }
                                }
                            }
                        } finally {
                            isUserInteracting = false
                            lastInteractionTimestamp = System.currentTimeMillis()
                        }
                    }
                }
        ) {
            // Smooth zoom animation (DO NOT TOUCH)
            val animatedVisibleDuration by animateFloatAsState(
                targetValue = visibleDuration.toFloat(),
                animationSpec = spring<Float>(stiffness = Spring.StiffnessMedium),
                label = "ChartZoomAnimation"
            )

            // Calculate gradient brush logic outside Canvas (Optimized)
            // Pre-calculate stops based on Y position (requires mapping High/Low to Y)
            val heightPx = constraints.maxHeight.toFloat()
            val chartHeightPx = (heightPx - chartUnderlayBottomPx - bottomAxisHeightPx - chartPlotBottomGapPx).coerceAtLeast(1f)
            
            val limitYHigh = if (yMax - yMin > 0.001f) {
                (chartHeightPx * (1f - (targetHigh - yMin) / (yMax - yMin))).coerceIn(-2000f, chartHeightPx + 2000f)
            } else 0f
            
            val limitYLow = if (yMax - yMin > 0.001f) {
                 (chartHeightPx * (1f - (targetLow - yMin) / (yMax - yMin))).coerceIn(-2000f, chartHeightPx + 2000f)
            } else 0f

            val gradientBrush = remember(limitYHigh, limitYLow, chartHeightPx, isDark, primaryColor) {
                val highTintColor = if (isDark) highOutOfRangeTintBase.copy(alpha = 0.6f) else highOutOfRangeTintBase.copy(alpha = 0.9f)
                val lowTintColor = lowOutOfRangeTintBase.copy(alpha = if (isDark) 0.6f else 0.9f)
                val fadePx = 20f
                
                if (chartHeightPx > 0) {
                     val stops = arrayOfNulls<Pair<Float, Color>>(5)
                        var stopCount = 0
                        
                        // High Region (Red) - fades IN from Red to Primary above line
                        // Pixels < limitYHigh are High.
                        stops[stopCount++] = 0f to highTintColor
                        if (limitYHigh > 0) {
                            val fadeStart = (limitYHigh - fadePx).coerceAtLeast(0f)
                            val stopHighStart = (fadeStart / chartHeightPx).coerceIn(0f, 1f)
                            stops[stopCount++] = stopHighStart to highTintColor
                            
                            val stopHighEnd = (limitYHigh / chartHeightPx).coerceIn(stopHighStart, 1f)
                            stops[stopCount++] = stopHighEnd to primaryColor
                        } else {
                            stops[stopCount++] = 0f to primaryColor
                        }
                        
                        // Low Region (Yellow) - fades OUT from Primary to Yellow below line
                        // Pixels > limitYLow are Low.
                        if (limitYLow < chartHeightPx) {
                            val stopLowStart = (limitYLow / chartHeightPx).coerceIn(0f, 1f)
                            stops[stopCount++] = stopLowStart to primaryColor
                            
                            val fadeEnd = (limitYLow + fadePx).coerceAtMost(chartHeightPx)
                            val stopLowEnd = (fadeEnd / chartHeightPx).coerceIn(stopLowStart, 1f)
                            stops[stopCount++] = stopLowEnd to lowTintColor
                        }
                        
                        // Ensure last stop is at 1f
                        val lastStop = stops[stopCount - 1]?.first ?: 0f
                        if (lastStop < 1f) {
                             // Last color used was lowTintColor if we added low stops, or primary if not.
                             // Actually better to just build with list for safety given complexity
                             val safeStops = mutableListOf<Pair<Float, Color>>()
                             for (i in 0 until stopCount) {
                                 stops[i]?.let { safeStops.add(it) }
                             }
                             // Add final stop
                             if (limitYLow < chartHeightPx) safeStops.add(1f to lowTintColor)
                             else safeStops.add(1f to primaryColor)
                             
                             Brush.verticalGradient(
                                *safeStops.toTypedArray(),
                                startY = 0f,
                                endY = chartHeightPx
                             )
                        } else {
                             // Array copy for safety
                             val safeStops = Array(stopCount) { i -> stops[i]!! }
                             Brush.verticalGradient(
                                *safeStops,
                                startY = 0f,
                                endY = chartHeightPx
                             )
                        }
                } else {
                     Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy =
                            androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    }
            ) {
                val width = size.width
                val rightPaddingPx = (16.dp.toPx() * safeExpandedProgress)
                val dataWidth = (width - rightPaddingPx).coerceAtLeast(1f)
                val contentHeight = (size.height - chartUnderlayBottomPx).coerceAtLeast(1f)
                val bottomAxisHeight = 32.dp.toPx()
                val chartHeight = (contentHeight - bottomAxisHeight - chartPlotBottomGapPx).coerceAtLeast(1f)
                if (labelCache.size > 200) labelCache.clear()

                // Viewport Logic (STABLE)
                // Use animated duration for smooth zoom, but ensure logic matches
                val animDur = animatedVisibleDuration.coerceAtLeast(minDuration.toFloat())
                val currentDur = animDur.toLong()
                
                // Calculate Viewport
                val viewportStart = centerTime - currentDur / 2
                val viewportEnd = centerTime + currentDur / 2
                
                // Data Access (Strict Guards)
                if (safeData.isEmpty()) return@Canvas

                // 2. SEARCH RANGE (With ample padding to prevent popping)
                val padding = currentDur / 2 // 50% padding on each side
                val searchStart = viewportStart - padding
                val searchEnd = viewportEnd + padding

                // 3. BINARY SEARCH (Standard Library)
                // binarySearchBy returns: index if found, or -(insertion point) - 1
                // Calculate visible range indices with padding for connecting lines
                // startIdx: Include point just BEFORE viewport start
                val startIdx = safeData.binarySearchBy(searchStart) { it.timestamp }
                    .let { if (it < 0) -it - 2 else it } // if not found, insertion point - 1
                    .coerceIn(0, safeData.size)
                
                // endIdx: Include point just AFTER viewport end (for exclusive loop)
                val endIdx = safeData.binarySearchBy(searchEnd) { it.timestamp }
                    .let { if (it < 0) -it else it + 1 } // if not found, insertion point + 1
                    .coerceIn(startIdx, safeData.size)

                // 4. COORDINATE MAPPING (Inline for performance)
                // Maps timestamp to X relative to CURRENT viewport
                fun timeToX(t: Long): Float {
                    return ((t - viewportStart).toFloat() / animDur) * width
                }
                fun timeToDataX(t: Long): Float {
                    return ((t - viewportStart).toFloat() / animDur) * dataWidth
                }

                // Maps Value to Y (Inverted: High value = Low Y)
                // Hoist state reads for performance loop
                val cYMin = yMin
                val cYRange = yMax - cYMin
                
                fun valToY(v: Float): Float {
                    if (cYRange < 0.001f) return chartHeight / 2 // Prevent div/0
                    val y = chartHeight - ((v - cYMin) / cYRange) * chartHeight
                    return y.coerceIn(-2000f, chartHeight + 2000f) // Clamp for safety against huge values
                }

                // --- 1. DRAW Y-AXIS GRID ---
                val yStep = if (cYRange < 25) 2f else 50f
                var yVal = (kotlin.math.ceil(yMin / yStep) * yStep).toInt() // integer steps
                
                while (yVal < yMax) {
                    val y = valToY(yVal.toFloat())
                    if (y in 0f..chartHeight) {
                        drawLine(gridColor, Offset(0f, y), Offset(width, y), 1f)
                        // Text - Vertically centered with grid line, 4dp left padding
                        val labelText = yVal.toString()
                        axisTextPaint.getTextBounds(labelText, 0, labelText.length, axisLabelBounds)
                        val centeredY = y + (axisLabelBounds.height() / 2f)
                        val labelPadding = 16f * density
                        val textWidth = axisTextPaint.measureText(labelText)
                        val backgroundPadH = 8f * density
                        val backgroundPadV = 3f * density
                        drawRoundRect(
                            color = axisLabelBackgroundColor,
                            topLeft = Offset(
                                x = labelPadding - backgroundPadH,
                                y = centeredY - axisLabelBounds.height() - backgroundPadV
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                width = textWidth + (backgroundPadH * 2f),
                                height = axisLabelBounds.height().toFloat() + (backgroundPadV * 2f)
                            ),
                            cornerRadius = CornerRadius(8f * density, 8f * density)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText, labelPadding, centeredY, axisTextPaint
                        )
                    }
                    yVal += yStep.toInt()
                }

                // --- 2. DRAW X-AXIS GRID ---
                // Intervals: 5m, 15m, 30m, 1h, 2h, 4h, 8h, 12h, 24h
                // Pick interval keeping labels ~120px apart
                val pxPerMs = width / animDur
                val minMs = (120f / pxPerMs).toLong()
                val gridInterval = gridIntervals.firstOrNull { it >= minMs } ?: gridIntervals.last()

                // Align t to timezone day boundary
                val tzOffset = java.util.TimeZone.getDefault().getOffset(viewportStart).toLong()
                
                // Start drawing from just before viewport to cover edge
                var tGrid = ((viewportStart + tzOffset) / gridInterval) * gridInterval - tzOffset
                if (tGrid < viewportStart) tGrid += gridInterval

                // Limit loop to prevent freeze if interval is tiny (sanity check)
                val loopLimit = viewportEnd + gridInterval
                
                // Safety: Don't draw if interval is dangerously small 
                 if (gridInterval > 1000L) {
                    while (tGrid <= loopLimit) {
                        val x = timeToX(tGrid)
                        if (x > -50f && x < width + 50f) {
                            cal.timeInMillis = tGrid
                            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                            val m = cal.get(java.util.Calendar.MINUTE)
                            val isMidnight = h == 0 && m == 0
                            
                            if (isMidnight) {
                                // Date Line
                                drawLine(gridColor.copy(alpha=0.8f), Offset(x, 0f), Offset(x, chartHeight), 3f)
                                val dateLabel = labelCache.getOrPut(tGrid) {
                                    reusableDate.time = tGrid
                                    formatDate.format(reusableDate)
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    dateLabel, x, contentHeight - 25f,
                                    xTextPaint.apply { typeface = graphFontBold }
                                )
                                xTextPaint.typeface = graphFont
                            } else {
                                // Time Line
                                drawLine(gridColor, Offset(x, 0f), Offset(x, chartHeight), 1f)
                                val timeLabel = labelCache.getOrPut(tGrid) {
                                    val hh = if (h < 10) "0$h" else h.toString()
                                    val mm = if (m < 10) "0$m" else m.toString()
                                    "$hh:$mm"
                                }
                                drawContext.canvas.nativeCanvas.drawText(timeLabel, x, contentHeight - 28f, xTextPaint)
                            }
                        }
                        tGrid += gridInterval
                    }
                }

                val isRawModeChart = viewMode == 1 || viewMode == 3
                val hasCalibration = tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(isRawModeChart)
                val hideInitialWhenCalibrated = hasCalibration &&
                    tk.glucodata.data.calibration.CalibrationManager.shouldHideInitialWhenCalibrated()

                // --- 3. DATA LINES (Unified & Optimized) ---
                if (endIdx > startIdx) {
                    val gapThreshold = 900000L // 15 mins
                    // Gradient Colors: High (Red) -> Normal (Primary) -> Low (Yellow)
                    val highTintColor = highOutOfRangeTintBase.copy(alpha = if (isDark) 0.1f else 0.2f)
                    val lowTintColor = lowOutOfRangeTintBase.copy(alpha = if (isDark) 0.1f else 0.2f) // Boosted for visibility
                    
                    val hideRawSource = hideInitialWhenCalibrated && isRawModeChart
                    val hideAutoSource = hideInitialWhenCalibrated && !isRawModeChart
                    val drawRaw = !hideRawSource && (viewMode == 1 || viewMode == 2 || viewMode == 3)
                    val drawAuto = !hideAutoSource && (viewMode == 0 || viewMode == 2 || viewMode == 3)

                    // Only tint the main/active line using gradient. No density check as requested.
                    val doTintRaw = !hasCalibration && (viewMode == 1 || viewMode == 3)
                    val doTintAuto = !hasCalibration && (viewMode == 0 || viewMode == 2)
                    val doTintCal = hasCalibration
                    
                    val needsGradient = doTintRaw || doTintAuto || doTintCal
                    
                    // Colors
                    val rawColor = when {
                        hasCalibration && viewMode == 2 -> tertiaryColor
                        hasCalibration || viewMode == 2 -> secondaryColor
                        else -> primaryColor // Used if not tinted
                    }
                    val autoColor = when {
                        hasCalibration && viewMode == 3 -> tertiaryColor
                        hasCalibration || viewMode == 3 -> secondaryColor
                        else -> primaryColor // Used if not tinted
                    }
                    
                    // Stroke Widths
                    val rawStrokeWidth = if (hasCalibration || viewMode == 2) 2.dp.toPx() else 3.dp.toPx()
                    val autoStrokeWidth = if (hasCalibration || viewMode == 3) 2.dp.toPx() else 3.dp.toPx()
                    val calStrokeWidth = 3.dp.toPx()

                    // Reset Paths
                    if (drawRaw) {
                        reusableRawPath.rewind()
                    }
                    if (drawAuto) {
                        reusableAutoPath.rewind()
                    }
                    if (hasCalibration) {
                        reusablePath.rewind()
                    }

                    // Unified Loop State
                    var rawFirst = true
                    var rawLastX = -10000f
                    var rawLastY = -10000f // Track Y for spike detection
                    var rawLastTimestamp = 0L

                    var autoFirst = true
                    var autoLastX = -10000f
                    var autoLastY = -10000f
                    var autoLastTimestamp = 0L
                    
                    var calFirst = true
                    var calLastX = -10000f
                    var calLastY = -10000f
                    var calLastTimestamp = 0L

                    // Optimization: Pre-calculate scaling factors to avoid repeated division in valToY/timeToDataX
                    val timeScale = dataWidth / animDur
                    val yScale = if (cYRange < 0.001f) 0f else chartHeight / cYRange
                    
                    // Use Round caps/joins for better visuals
                    val strokeCap = StrokeCap.Round
                    val strokeJoin = StrokeJoin.Round

                    for (i in startIdx until endIdx) {
                        val p = safeData[i]
                        // X is shared for all lines at this timestamp
                        val px = (p.timestamp - viewportStart) * timeScale
                        
                        if (!px.isFinite()) continue

                        // --- RAW LINE ---
                        if (drawRaw) {
                            // FAST PATH DECIMATION: Check X proximity first
                            if (!rawFirst && kotlin.math.abs(px - rawLastX) < 0.8f) {
                                // If horizontally close, check vertical spike using CHEAP value (raw/auto)
                                val rawV = p.rawValue
                                val rawY = chartHeight - ((rawV - cYMin) * yScale)
                                if (kotlin.math.abs(rawY - rawLastY) < 1.0f) {
                                    continue // SKIP drawing this point
                                }
                            }

                            val v = p.rawValue
                            if (v.isNaN() || v < 0.1f) {
                                rawFirst = true
                            } else {
                                val rawY = chartHeight - ((v - cYMin) * yScale)
                                val py = rawY.coerceIn(-2000f, chartHeight + 2000f)
                                
                                if (!py.isFinite()) {
                                    rawFirst = true
                                } else {
                                    if (!rawFirst && (p.timestamp - rawLastTimestamp) > gapThreshold) {
                                        rawFirst = true
                                    }
                                    
                                    if (rawFirst) {
                                        reusableRawPath.moveTo(px, py)
                                        rawFirst = false
                                    } else {
                                        reusableRawPath.lineTo(px, py)
                                    }
                                    rawLastX = px
                                    rawLastY = py
                                    rawLastTimestamp = p.timestamp
                                }
                            }
                        }

                        // --- AUTO LINE ---
                        if (drawAuto) {
                            if (!autoFirst && kotlin.math.abs(px - autoLastX) < 0.8f) {
                                val autoV = p.value
                                val autoY = chartHeight - ((autoV - cYMin) * yScale)
                                if (kotlin.math.abs(autoY - autoLastY) < 1.0f) {
                                    continue
                                }
                            }

                            val v = p.value
                            if (v.isNaN() || v < 0.1f) {
                                autoFirst = true
                            } else {
                                val autoY = chartHeight - ((v - cYMin) * yScale)
                                val py = autoY.coerceIn(-2000f, chartHeight + 2000f)
                                
                                if (!py.isFinite()) {
                                    autoFirst = true
                                } else {
                                    if (!autoFirst && (p.timestamp - autoLastTimestamp) > gapThreshold) {
                                        autoFirst = true
                                    }
                                    
                                    if (autoFirst) {
                                        reusableAutoPath.moveTo(px, py)
                                        autoFirst = false
                                    } else {
                                        reusableAutoPath.lineTo(px, py)
                                    }
                                    autoLastX = px
                                    autoLastY = py
                                    autoLastTimestamp = p.timestamp
                                }
                            }
                        }
                        
                        // --- CALIBRATION LINE ---
                        if (hasCalibration) {
                            if (!calFirst && kotlin.math.abs(px - calLastX) < 0.8f) {
                                val baseV = if (isRawModeChart) p.rawValue else p.value
                                val proxyY = chartHeight - ((baseV - cYMin) * yScale)
                                if (kotlin.math.abs(proxyY - calLastY) < 1.0f) {
                                     continue
                                }
                            }

                            val baseV = if (isRawModeChart) p.rawValue else p.value
                            val v = tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(baseV, p.timestamp, isRawModeChart)
                            
                            if (v.isNaN() || v < 0.1f) {
                                calFirst = true
                            } else {
                                val calY = chartHeight - ((v - cYMin) * yScale)
                                val py = calY.coerceIn(-2000f, chartHeight + 2000f)
                                
                                if (!py.isFinite()) {
                                    calFirst = true
                                } else {
                                    if (!calFirst && (p.timestamp - calLastTimestamp) > gapThreshold) {
                                        calFirst = true
                                    }
                                    
                                    if (calFirst) {
                                        reusablePath.moveTo(px, py)
                                        calFirst = false
                                    } else {
                                        reusablePath.lineTo(px, py)
                                    }
                                    calLastX = px
                                    calLastY = py
                                    calLastTimestamp = p.timestamp
                                }
                            }
                        }
                    }

                    // --- DRAW PATHS ---
                    // Using Gradient Brush for primary/active lines for smooth transition

                    if (drawRaw) {
                        if (doTintRaw) {
                            drawPath(reusableRawPath, brush = gradientBrush, style = Stroke(width = rawStrokeWidth, cap = strokeCap, join = strokeJoin))
                        } else {
                            drawPath(reusableRawPath, rawColor, style = Stroke(width = rawStrokeWidth, cap = strokeCap, join = strokeJoin))
                        }
                    }
                    if (drawAuto) {
                        if (doTintAuto) {
                            drawPath(reusableAutoPath, brush = gradientBrush, style = Stroke(width = autoStrokeWidth, cap = strokeCap, join = strokeJoin))
                        } else {
                            drawPath(reusableAutoPath, autoColor, style = Stroke(width = autoStrokeWidth, cap = strokeCap, join = strokeJoin))
                        }
                    }
                    if (hasCalibration) {
                        if (doTintCal) {
                            drawPath(reusablePath, brush = gradientBrush, style = Stroke(width = calStrokeWidth, cap = strokeCap, join = strokeJoin))
                        } else {
                            drawPath(reusablePath, primaryColor, style = Stroke(width = calStrokeWidth, cap = strokeCap, join = strokeJoin))
                        }
                    }
                }

                // --- 4. MIN/MAX INDICATORS (Restored & Optimized) ---
                if (endIdx > startIdx) {
                    var minPoint = safeData[startIdx]
                    var maxPoint = safeData[startIdx]
                    var minVal = Float.MAX_VALUE
                    var maxVal = Float.MIN_VALUE

                    // Single fast pass for min/max
                    for (i in startIdx until endIdx) {
                        val p = safeData[i]
                        // Determine value based on mode commonality
                        // If showing Raw (Mode 1) or Raw-Primary (Mode 3), prioritize Raw
                        val useRaw = viewMode == 1 || viewMode == 3
                        val v = if (hideInitialWhenCalibrated) {
                            val baseValue = if (useRaw) p.rawValue else p.value
                            tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(
                                baseValue,
                                p.timestamp,
                                useRaw
                            )
                        } else {
                            if (useRaw) p.rawValue else p.value
                        }

                        if (v.isNaN() || v < 0.1f) continue

                        if (v < minVal) { minVal = v; minPoint = p }
                        if (v > maxVal) { maxVal = v; maxPoint = p }
                    }

                    // Helper to draw
                    fun drawIndicator(point: GlucosePoint, valToDraw: Float) {
                        val y = valToY(valToDraw)
                        val x = timeToDataX(point.timestamp)

                        if (y.isFinite() && x.isFinite() && y in 0f..chartHeight && x in 0f..width) {
                            val label = tk.glucodata.ui.util.GlucoseFormatter.format(valToDraw, isMmol)

                            // Text - Vertically centered with guide line, 4dp left padding
                            axisTextPaint.getTextBounds(label, 0, label.length, indicatorLabelBounds)
                            val centeredY = y + (indicatorLabelBounds.height() / 2f)
                            val indicatorPadding = 16f * density
                            val textWidth = axisTextPaint.measureText(label)

                            drawContext.canvas.nativeCanvas.drawText(
                                label, indicatorPadding, centeredY,
                                axisTextPaint.apply { color = android.graphics.Color.DKGRAY } // Dark Gray for visibility
                            )
                            // Guide line - starts after text
                            val lineStartX = indicatorPadding + textWidth + 8f * density
                            drawLine(
                                color = minMaxLineColor,
                                start = Offset(lineStartX, y),
                                end = Offset(x, y),
                                strokeWidth = 1f,
                                pathEffect = dashEffect
                            )
                            // Restore paint color (axisTextPaint is shared)
                            axisTextPaint.color = android.graphics.Color.GRAY
                        }
                    }

                    if (maxVal > Float.MIN_VALUE) drawIndicator(maxPoint, maxVal)
                    if (minVal < Float.MAX_VALUE && minVal != maxVal) drawIndicator(minPoint, minVal)
                }

                // --- 5. TARGET RANGE ---
                val yHigh = valToY(targetHigh)
                val yLow = valToY(targetLow)
                // Only draw if valid
                if (yHigh.isFinite() && yLow.isFinite()) {
                    drawRect(targetBandColor, topLeft = Offset(0f, yHigh), size = Size(width, yLow - yHigh))
                }

                // --- 6. CALIBRATION MARKERS ---
                // Draw permanent vertical lines for calibration points in visible range (respects mode)
                val isRawModeMarkers = viewMode == 1 || viewMode == 3
                val calibrations = tk.glucodata.data.calibration.CalibrationManager.getVisibleCalibrations(isRawModeMarkers)
                val visibleCalibrations = calibrations.filter { it.timestamp in viewportStart..viewportEnd }

                visibleCalibrations.forEach { cal ->
                    val calX = timeToDataX(cal.timestamp)
                    if (calX in 0f..width) {
                        // Draw vertical line with opacity
                        drawLine(
                            color = primaryColor.copy(alpha = 0.4f),
                            start = Offset(calX, 0f),
                            end = Offset(calX, chartHeight),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }

                // --- 7. CURSOR ---
                val cursorX = selectedPoint?.let { timeToDataX(it.timestamp) }
                if (cursorX != null && cursorX in 0f..width) {
                    drawLine(hoverLineColor, Offset(cursorX, 0f), Offset(cursorX, chartHeight), 2.dp.toPx())

                    selectedPoint?.let { p ->
                        val dotRadius = 5.dp.toPx()
                        val isRawModeDot = viewMode == 1 || viewMode == 3
                        val hasCalibrationDot = tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(isRawModeDot)
                        val hideRawDot = hideInitialWhenCalibrated && isRawModeDot
                        val hideAutoDot = hideInitialWhenCalibrated && !isRawModeDot

                        // Draw dots for active lines (demoted when calibration active)
                         if (!hideRawDot && (viewMode == 1 || viewMode == 2 || viewMode == 3) && p.rawValue > 0.1f) {
                              val color = if (hasCalibrationDot) secondaryColor else if (viewMode == 1 || viewMode == 3) primaryColor else secondaryColor
                              val py = valToY(p.rawValue)
                              if (py.isFinite()) drawCircle(color, dotRadius, Offset(cursorX, py))
                          }
                         if (!hideAutoDot && (viewMode == 0 || viewMode == 2 || viewMode == 3)) {
                             val color = if (hasCalibrationDot) secondaryColor else if (viewMode == 0 || viewMode == 2) primaryColor else secondaryColor
                             val py = valToY(p.value)
                             if (py.isFinite()) drawCircle(color, dotRadius, Offset(cursorX, py))
                         }

                         // Draw calibrated dot on top (primary when active)
                         if (hasCalibrationDot) {
                             val baseValue = if (viewMode == 1 || viewMode == 3) p.rawValue else p.value
                             val calibratedV = tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(baseValue, p.timestamp, isRawModeDot)
                             val py = valToY(calibratedV)
                             if (py.isFinite()) drawCircle(primaryColor, dotRadius, Offset(cursorX, py))
                         }
                    }
                }
            }

            // --- INFO CARD ---
            val overlayRightPaddingPx = 16f * density * safeExpandedProgress
            val overlayDataWidthPx = (constraints.maxWidth.toFloat() - overlayRightPaddingPx).coerceAtLeast(1f)

            selectedPoint?.let { point ->
                // Calculate X position to follow cursor
                val viewportStart = centerTime - visibleDuration / 2
                val xFraction = (point.timestamp - viewportStart).toFloat() / visibleDuration.toFloat()
                // Clamp horizontal position to keep card on screen (assuming approx card width ~120dp)
                // We use specific offsets in standard DP
                val cardXOffset = (overlayDataWidthPx * xFraction).coerceIn(0f, overlayDataWidthPx)

                // Resolve Colors matching Graph Lines

                // --- COLORS & STYLING ---
                // MATCH GRAPH COLORS EXACTLY
                val textPrimaryColor = MaterialTheme.colorScheme.primary
                val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

                // Capture Theme Colors outside non-composable builder
                val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

                // User Feedback: "same color as graph bg" -> Use distinct colors.
                // Info Card: Standard SurfaceContainer (Distinct from base Surface)
                // --- COLORS & STYLING ---
                // MATCH "Current Status Card" (Top Card, lines 500-503) EXACTLY
                // Top Card uses: colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                // Default Card Shape is usually Medium (12.dp) in M3.

                val statusCardColor = MaterialTheme.colorScheme.primaryContainer
                val statusContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                val cardShape = RoundedCornerShape(12.dp) // M3 standard card corner radius

                // AXIS TEXT MATCHING
                val axisFontSize = 12.sp

                // Compute calibrated value for tooltip
                val isRawModeTT = viewMode == 1 || viewMode == 3
                val hasCalibrationTT = tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(isRawModeTT)
                val calibratedValueTT = if (hasCalibrationTT) {
                    val baseValue = if (isRawModeTT && point.rawValue > 0.1f) point.rawValue else point.value
                    if (baseValue > 0.1f) {
                        tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(baseValue, point.timestamp, isRawModeTT)
                    } else {
                        null
                    }
                } else null
                val dvs = getDisplayValues(point, viewMode, unit, calibratedValueTT)

                // --- 1. INFO CARD (Top) ---
                // "Current Status Card styling" -> primaryContainer
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(2f) // Above calibration tooltips
                        // 1. Move to Cursor X, Fixed Y
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = cardXOffset.toInt(),
                                y = scrubValueLabelOffsetDp.dp.roundToPx()
                            )
                        }
                        // 2. Center
                        .graphicsLayer { translationX = -size.width / 2f }
                        .widthIn(min = 48.dp)
                        .clip(cardShape) // Clip ripple to match rounded corners
                        .pointerInput(constraints.maxHeight) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { isAdjustingScrubLabel = true },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val minOffset = 8f
                                    val effectiveHeight =
                                        (constraints.maxHeight - chartUnderlayBottomIntPx).coerceAtLeast(
                                            56.dp.roundToPx()
                                        )
                                    val maxOffset =
                                        ((effectiveHeight - 56.dp.roundToPx()) / density).coerceAtLeast(
                                            minOffset
                                        )
                                    scrubValueLabelOffsetDp =
                                        (scrubValueLabelOffsetDp + dragAmount.y / density).coerceIn(
                                            minOffset,
                                            maxOffset
                                        )
                                },
                                onDragEnd = {
                                    isAdjustingScrubLabel = false
                                    dashboardPrefs.edit()
                                        .putFloat(
                                            "dashboard_scrub_value_label_offset_dp",
                                            scrubValueLabelOffsetDp
                                        )
                                        .apply()
                                },
                                onDragCancel = { isAdjustingScrubLabel = false }
                            )
                        }
                        .clickable { onPointClick?.invoke(point) },
                    shape = cardShape,
                    color = statusCardColor.copy(alpha = 1f),
                    contentColor = statusContentColor.copy(alpha = 1f),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Colored Text Logic (Keep matching graph lines for values)
                        val styledText = androidx.compose.ui.text.buildAnnotatedString {
                            // Primary Value
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(dvs.primaryStr)
                            }

                            // Separator & Secondary
                            dvs.secondaryStr?.let { sec ->
                                withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(" · ")
                                }
                                withStyle(androidx.compose.ui.text.SpanStyle(color = LocalContentColor.current.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)) {
                                    append(sec)
                                }
                            }

                            // Tertiary (when 3 values exist)
                            dvs.tertiaryStr?.let { ter ->
                                withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    append(" · ")
                                }
                                withStyle(androidx.compose.ui.text.SpanStyle(color = LocalContentColor.current.copy(alpha = 0.4f))) {
                                    append(ter)
                                }
                            }
                        }

                        Text(
                            text = styledText,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // --- 2. TIME CHIP (Bottom) - Text centered on same line as X-axis labels ---
                // Height: 32dp, Corner: 8dp, Padding: horizontal 8dp
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(2f) // Above calibration tooltips
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = cardXOffset.toInt(),
                                y = scrubTimeLabelOffsetDp.dp.roundToPx() - chartUnderlayBottomIntPx // Keep chip aligned with visible x-axis band
                            )
                        }
                        .graphicsLayer { translationX = -size.width / 2f }
                        .pointerInput(constraints.maxHeight) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { isAdjustingScrubLabel = true },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val effectiveHeight =
                                        (constraints.maxHeight - chartUnderlayBottomIntPx).coerceAtLeast(
                                            40.dp.roundToPx()
                                        )
                                    val minOffset =
                                        -((effectiveHeight - 40.dp.roundToPx()) / density)
                                    val maxOffset = 16f
                                    scrubTimeLabelOffsetDp =
                                        (scrubTimeLabelOffsetDp + dragAmount.y / density).coerceIn(
                                            minOffset,
                                            maxOffset
                                        )
                                },
                                onDragEnd = {
                                    isAdjustingScrubLabel = false
                                    dashboardPrefs.edit()
                                        .putFloat(
                                            "dashboard_scrub_time_label_offset_dp",
                                            scrubTimeLabelOffsetDp
                                        )
                                        .apply()
                                },
                                onDragCancel = { isAdjustingScrubLabel = false }
                            )
                        }
                        .height(32.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = point.time,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Brace removed here to keep subsequent elements inside BoxWithConstraints

            // --- CALIBRATION TOOLTIPS ---
            // Show permanent tooltips for calibration points in visible range (respects mode)
            val isRawModeTooltip = viewMode == 1 || viewMode == 3
            val calibrationsTooltip = tk.glucodata.data.calibration.CalibrationManager.getVisibleCalibrations(isRawModeTooltip)
            val viewportStartTooltip = centerTime - visibleDuration / 2
            val viewportEndTooltip = centerTime + visibleDuration / 2
            val visibleCalibrationsTooltip = calibrationsTooltip.filter { it.timestamp in viewportStartTooltip..viewportEndTooltip }

            visibleCalibrationsTooltip.forEach { cal ->
                val calXFraction = (cal.timestamp - viewportStartTooltip).toFloat() / visibleDuration.toFloat()
                val calXOffset = (overlayDataWidthPx * calXFraction).coerceIn(0f, overlayDataWidthPx)
                val calTimeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val calTimeStr = calTimeFormat.format(java.util.Date(cal.timestamp))

                // Top: Value chip with waterdrop icon (clickable to edit)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(1f) // Below scrubbing tooltip
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = calXOffset.toInt(),
                                y = 8.dp.roundToPx()
                            )
                        }
                        .graphicsLayer { translationX = -size.width / 2f }
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            // Open CalibrationBottomSheet to edit this calibration
                            onCalibrationClick?.invoke(cal)
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(java.util.Locale.getDefault(), if (unit.contains("mmol", true)) "%.1f" else "%.0f", cal.userValue),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom: Time chip
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(1f) // Below scrubbing tooltip
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = calXOffset.toInt(),
                                y = 0.dp.roundToPx() - chartUnderlayBottomIntPx
                            )
                        }
                        .graphicsLayer { translationX = -size.width / 2f }
                        .height(24.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = calTimeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            // --- DATE HEADER OVERLAY ---
            // Show only if NOT today AND not "recent" (to avoid showing date when day just started)
            val now = System.currentTimeMillis()
            val dayCheckFormat = java.text.SimpleDateFormat("yyyyDDD", java.util.Locale.getDefault())
            val isToday = dayCheckFormat.format(java.util.Date(now)) == dayCheckFormat.format(java.util.Date(centerTime))
            val isRecent = abs(now - centerTime) < 4 * 60 * 60 * 1000L // 4 Hour buffer for "just started" days
            val showHeaderDate = !isToday && !isRecent
            val headerDate = remember(centerTime) {
                java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date(centerTime))
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showHeaderDate,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = headerDate,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            /*
            // --- BACK TO NOW BUTTON ---
            // Show if centerTime is more than 5 minutes away from "real now"
            val isFarFromNow = abs(centerTime - (System.currentTimeMillis() - visibleDuration / 2)) > 60 * 60 * 1000

            androidx.compose.animation.AnimatedVisibility(
                visible = isFarFromNow,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                // M3 Expressive: FilledTonalIconButton is lighter than FAB ("less like an action button")
                // Icon: LastPage (>|) implies "Go to End/Now"
                FilledTonalIconButton(
                    onClick = {
                        val realNow = System.currentTimeMillis()
                        val targetTime = realNow - visibleDuration / 2
                        val diff = targetTime - centerTime

                        coroutineScope.launch {
                            // "Smart Scroll": Avoid crazy jumps
                            val maxScroll = 12 * 60 * 60 * 1000L // 12 Hours
                            var startScroll = centerTime

                            // If distance is huge, snap closer first
                            if (abs(diff) > maxScroll) {
                                startScroll = targetTime - (if (diff > 0) maxScroll else -maxScroll)
                                centerTime = startScroll
                            }

                            // Animate the remaining distance
                            androidx.compose.animation.core.Animatable(startScroll.toFloat()).animateTo(
                                targetValue = targetTime.toFloat(),
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) {
                                centerTime = value.toLong()
                            }
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.size(48.dp) // Slightly larger than standard 40dp for touch target
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.LastPage,
                        contentDescription = null
                    )
                }
            }
            */
        }

        // --- ZOOM BUTTONS (Expressive Connected Group) ---
        // --- ZOOM BUTTONS (M3 Expressive: Text + Pill Selection) ---
        // Refined based on user feedback:
        // 1. Alignment: Centered Horizontally (Arrangement) and Vertically.
        // 2. Spacing: Top 16dp, Bottom reduced to 4dp (tighter).
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val items = TimeRange.values()
        val now = System.currentTimeMillis()
        val targetTime = now - visibleDuration / 2
        val isAtNow = abs(centerTime - targetTime) < 60 * 60 * 1000 // 1 hour threshold (Old behavior)
        val showBackToNow = !isAtNow

        val isCompact = configuration.screenWidthDp < 380
        val baseInset = (12f * safeExpandedProgress).dp
        val baseInnerStartPadding = (8f * safeExpandedProgress).dp
        val baseInnerEndPadding = (4f * safeExpandedProgress).dp
        val baseVerticalPadding = (12f - (4f * safeExpandedProgress)).dp
        val baseOuterButtonWidth = if (isCompact) 32.dp else 40.dp
        val baseOuterButtonHeight = 32.dp
        val baseOuterIconSize = 20.dp
        val baseBackIconSize = 24.dp
        val baseRangeHeight = 32.dp
        val baseRangeHorizontalPadding = 8.dp
        val baseRangeClockGap = 4.dp
        val baseRangeClockSize = 16.dp
        val baseInterItemSpacing = if (isCompact) 4.dp else 8.dp
        val baseLabelStyle = MaterialTheme.typography.labelLarge
        val baseLabelFontSize = baseLabelStyle.fontSize.takeIf { it.value.isFinite() } ?: 14.sp
        val baseTextPaint = remember(baseLabelFontSize, density) {
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = with(density) { baseLabelFontSize.toPx() }
            }
        }

        val baseRangeLabelWidthsPx = items.map { range ->
            baseTextPaint.measureText(range.label)
        }
        val baseRangesRequiredPx = with(density) {
            baseRangeLabelWidthsPx.sum() +
                (items.size * (baseRangeHorizontalPadding * 2).toPx()) +
                (baseRangeClockSize + baseRangeClockGap).toPx()
        }
        val baseFixedRequiredPx = with(density) {
            (baseInnerStartPadding + baseInnerEndPadding + baseOuterButtonWidth + baseInterItemSpacing).toPx() +
                if (showBackToNow) (baseInterItemSpacing + baseOuterButtonWidth).toPx() else 0f
        }
        val availableWidthPx = with(density) {
            (configuration.screenWidthDp.dp - (baseInset * 2f)).coerceAtLeast(1.dp).toPx()
        }
        val requiredWidthPx = (baseFixedRequiredPx + baseRangesRequiredPx).coerceAtLeast(1f)
        val pickerScale = (availableWidthPx / requiredWidthPx).coerceIn(0.60f, 1f)

        fun scaled(dp: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = dp * pickerScale
        val scaledLabelStyle = baseLabelStyle.copy(fontSize = baseLabelStyle.fontSize * pickerScale)
        val pickerInsetHorizontal = scaled(baseInset).coerceAtLeast(2.dp)
        val pickerVerticalOffset = -(chartUnderlayBottomDp + (80.dp * safeExpandedProgress))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = pickerVerticalOffset)
                .zIndex(1f)
                .padding(horizontal = pickerInsetHorizontal),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f * safeExpandedProgress),
                        shape = RoundedCornerShape(scaled(16.dp))
                    )
                    .padding(
                        start = scaled(baseInnerStartPadding),
                        end = scaled(baseInnerEndPadding),
                        top = scaled(baseVerticalPadding),
                        bottom = scaled(baseVerticalPadding)
                    ),
                horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Date Picker Button (Left side, always visible)
            FilledTonalIconButton(
                onClick = { showDatePicker = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),

                modifier = Modifier.size(width = scaled(baseOuterButtonWidth), height = scaled(baseOuterButtonHeight))
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.DateRange,
                    contentDescription = "Jump to Date",
                    modifier = Modifier.size(scaled(baseOuterIconSize))
                )
            }

            Spacer(Modifier.width(width = scaled(baseInterItemSpacing)))

            items.forEach { range ->
                val rangeDur = range.hours * 60 * 60 * 1000L
                val isSel = abs(visibleDuration - rangeDur) < 1000

                // M3 Expressive Animation specs - bouncy springs
                val bouncySpec = spring<Float>(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
                val colorSpec = spring<Color>(stiffness = Spring.StiffnessMediumLow)

                val containerColor by animateColorAsState(
                    // Fix: Interpolate alpha of the SAME color to avoid "dark/gray" ghosting during transition
                    targetValue = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0f),
                    animationSpec = colorSpec,
                    label = "ButtonContainerColor"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = colorSpec,
                    label = "ButtonContentColor"
                )

                // M3 Expressive: Scale pop on selection
                val scale by animateFloatAsState(
                    targetValue = if (isSel) 1f else 1f,
                    animationSpec = bouncySpec,
                    label = "ButtonScale"
                )

                // M3 Expressive: Icon rotation (fun subtle touch)
                val iconRotation by animateFloatAsState(
                    targetValue = if (isSel) 360f else 0f,
                    animationSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "IconRotation"
                )

                Surface(
                    onClick = {
                        val now = System.currentTimeMillis()
                        performSubtleTick()
                        markProgrammaticViewportChange(now)
                        // Cancel any active scroll when interacting with tabs
                        cancelAutoScroll()

                        if (isSel) {
                            // "Back to Now" logic with Smart Scroll animation
                            startAutoScrollTo(now - visibleDuration / 2)
                        } else {
                            visibleDuration = rangeDur
                            onTimeRangeSelected?.invoke(range)
                            val maxCenter = now - visibleDuration / 2
                            if (centerTime > maxCenter) {
                                centerTime = maxCenter
                            }
                        }
                    },
                    shape = RoundedCornerShape(scaled(28.dp)),
                    color = containerColor,
                    contentColor = contentColor,
                    modifier = Modifier
                        .height(scaled(baseRangeHeight))
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(horizontal = scaled(baseRangeHorizontalPadding))
                    ) {
                        if (isSel) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = scaled(baseRangeClockGap))
                                    .size(scaled(baseRangeClockSize))
                                    .graphicsLayer { rotationZ = iconRotation }
                            )
                        }

                        Text(
                            text = range.label,
                            style = scaledLabelStyle,
                            softWrap = false,
                            maxLines = 1,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            // "Back to Now" Button (Expressive: End of Row)
            AnimatedVisibility(
                visible = showBackToNow,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start) + scaleIn(),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start) + scaleOut()
            ) {
                Spacer(Modifier.width(width = scaled(baseInterItemSpacing)))

                Surface(
                    onClick = {
                        // Cancel previous if any
                        performSubtleTick()
                        markProgrammaticViewportChange()
                        startAutoScrollTo(targetTime)
                    },
                    shape = RoundedCornerShape(28.dp),
                    // Subtle surface color for visibility
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .size(width = scaled(baseOuterButtonWidth), height = scaled(baseOuterButtonHeight))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.LastPage,
                            contentDescription = "Back to Now",
                            modifier = Modifier.size(scaled(baseBackIconSize))
                        )
                    }
                }
            }
        }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            // Jump to selected date
                            performSubtleTick()
                            markProgrammaticViewportChange()
                            cancelAutoScroll()
                            centerTime = selectedDate + (12 * 60 * 60 * 1000) // Center on noon of selected day
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.go))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
