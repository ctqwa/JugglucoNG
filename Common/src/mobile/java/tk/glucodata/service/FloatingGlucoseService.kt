package tk.glucodata.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tk.glucodata.data.GlucoseRepository
import tk.glucodata.data.settings.FloatingSettingsRepository
import tk.glucodata.ui.overlay.FloatingGlucoseOverlay
import tk.glucodata.Natives

class FloatingGlucoseService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private companion object {
        private const val FLOATING_HISTORY_WINDOW_MS = 6L * 60L * 60L * 1000L
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var settingsRepository: FloatingSettingsRepository
    private val glucoseRepository = GlucoseRepository()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = FloatingSettingsRepository(this)

        setupOverlay()
        observeSettings()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        // Satisfy Foreground Service requirement immediately
        startForeground(81431, createForegroundNotification())
    }
    
    private fun createForegroundNotification(): android.app.Notification {
        val channelId = "glucoseNotification"
        val channelName = "JugglucoNG Service"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = android.app.NotificationChannel(channelId, channelName, android.app.NotificationManager.IMPORTANCE_HIGH)
            chan.lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            chan.setShowBadge(true) // Standard behavior
            chan.description = "Glucose Levels"
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(chan)
        }
        
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            android.app.Notification.Builder(this)
        }
        
        val prefs = getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        val hideIcon = prefs.getBoolean("notification_hide_status_icon", false)
        val icon = if (hideIcon) tk.glucodata.R.drawable.transparent_icon else tk.glucodata.R.drawable.novalue
        
        return builder.setOngoing(true)
            .setSmallIcon(icon)
            .setContentTitle("JugglucoNG Overlay")
            .setContentText("Service is running")
            .setCategory(android.app.Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        // If the system kills the service, recreate it with a null intent
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // State for Dynamic Island
    data class CutoutData(val width: androidx.compose.ui.unit.Dp, val bottom: androidx.compose.ui.unit.Dp)
    private val cutoutData = kotlinx.coroutines.flow.MutableStateFlow(CutoutData(0.dp, 0.dp))
    private val statusBarHeight = kotlinx.coroutines.flow.MutableStateFlow(0.dp)
    
    // ...

    private fun setupOverlay() {
        if (composeView != null) return

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingGlucoseService)
            setViewTreeViewModelStoreOwner(this@FloatingGlucoseService)
            setViewTreeSavedStateRegistryOwner(this@FloatingGlucoseService)
            
            // Listen for Insets to detect Cutout reliably
            setOnApplyWindowInsetsListener { v, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val cutout = insets.displayCutout
                    val density = resources.displayMetrics.density
                    
                    // Gap (Width of Cutout + Buffer)
                    val widthPx = if (cutout != null && !cutout.boundingRects.isEmpty()) {
                        cutout.boundingRects[0].width()
                    } else {
                        0
                    }
                    
                    // Height: Use systemWindowInsetTop (Status Bar Height)
                    // The user wants it to fit in the status bar, not the deep safe area.
                    val sbHeightPx = insets.systemWindowInsetTop
                    val cutoutBottomPx = if (cutout != null && !cutout.boundingRects.isEmpty()) {
                        cutout.boundingRects[0].bottom
                    } else 0
                    
                    // Emit
                    cutoutData.value = CutoutData(
                        if (widthPx > 0) (widthPx / density).dp else 0.dp,
                        (cutoutBottomPx / density).dp
                    )
                    statusBarHeight.value = (sbHeightPx / density).dp
                }
                insets
            }
            
            setContent {
                FloatingGlucoseOverlay(
                    repository = settingsRepository,
                    historyFlow = glucoseRepository.getHistoryFlow(
                        System.currentTimeMillis() - FLOATING_HISTORY_WINDOW_MS,
                        Natives.getunit() == 1
                    ),
                    onUpdatePosition = { x, y -> updateViewPosition(x, y) },
                    cutoutDataFlow = cutoutData,
                    statusBarHeightFlow = statusBarHeight
                )
            }
        }
        
        // ... (LayoutParams init)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // Initial position
        val (startX, startY) = settingsRepository.getPosition()
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = startX
        layoutParams.y = startY

        try {
            windowManager?.addView(composeView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateViewPosition(xDelta: Int, yDelta: Int) {
        if (composeView == null || windowManager == null) return
        
        layoutParams.x += xDelta
        layoutParams.y += yDelta
        
        try {
            windowManager?.updateViewLayout(composeView, layoutParams)
            
            serviceScope.launch {
                settingsRepository.savePosition(layoutParams.x, layoutParams.y)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.isEnabled.collectLatest { enabled ->
                if (!enabled) {
                    stopSelf()
                }
            }
        }
        
        serviceScope.launch {
            settingsRepository.isDynamicIslandEnabled.collectLatest { isIsland ->
                if (composeView != null && windowManager != null) {
                    if (isIsland) {
                        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        layoutParams.x = 0
                        layoutParams.y = 0
                    } else {
                        val (x, y) = settingsRepository.getPosition()
                        layoutParams.gravity = Gravity.TOP or Gravity.START
                        layoutParams.x = x
                        layoutParams.y = y
                    }
                    try {
                        windowManager?.updateViewLayout(composeView, layoutParams)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()
        if (composeView != null) {
            try {
                windowManager?.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            composeView = null
        }
        super.onDestroy()
    }
}
