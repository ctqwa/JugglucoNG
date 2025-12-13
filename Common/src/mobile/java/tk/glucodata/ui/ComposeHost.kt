@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.Keep
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tk.glucodata.Natives
import tk.glucodata.QRmake
import tk.glucodata.MainActivity
import android.widget.Toast
import tk.glucodata.ui.viewmodel.DashboardViewModel
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.google.zxing.integration.android.IntentIntegrator
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.animation.Crossfade
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material.icons.filled.Check
import kotlin.math.ceil
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RangeSlider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
//import androidx.compose.material.icons.filled.TrendingUp
//import androidx.compose.material.icons.filled.TrendingDown
//import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff




// Helper to format timestamps or date strings into "Mon 20.05.2024 14:30"
fun formatSensorTime(rawTime: String): String {
    if (rawTime.isBlank()) return ""

    val outputFormat = java.text.SimpleDateFormat("EEE dd.MM.yyyy HH:mm", java.util.Locale.getDefault())

    // 1. Try as Unix Timestamp (Digits only)
    if (rawTime.all { it.isDigit() }) {
        val timestamp = rawTime.toLongOrNull()
        if (timestamp != null) {
            val millis = if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
            return outputFormat.format(java.util.Date(millis))
        }
    }

    // 2. Try as Full Date "yyyy-MM-dd HH:mm:ss"
    try {
        val fullDateParser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val date = fullDateParser.parse(rawTime)
        if (date != null) return outputFormat.format(date)
    } catch (e: Exception) {
        // Ignore and try next format
    }

    // 3. Try as Partial Date "MM-dd HH:mm:ss" (What you likely have)
    try {
        val partialParser = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val date = partialParser.parse(rawTime)

        if (date != null) {
            // Fix the Year (partial date defaults to 1970)
            val cal = java.util.Calendar.getInstance()
            cal.time = date

            // Set to Current Year
            val nowCal = java.util.Calendar.getInstance()
            val currentYear = nowCal.get(java.util.Calendar.YEAR)
            cal.set(java.util.Calendar.YEAR, currentYear)

            // Smart Logic: If the resulting date is more than 30 days in the future,
            // it likely belongs to the PREVIOUS year (e.g. It\'s Jan 2025, but date is "12-31")
            // Sensors usually don\'t have future start dates.
            if (cal.timeInMillis > System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)) {
                cal.add(java.util.Calendar.YEAR, -1)
            }

            return outputFormat.format(cal.time)
        }
    } catch (e: Exception) {
        // Ignore
    }

    // 4. Fallback: Return raw string if nothing matched
    return rawTime
}

data class GlucosePoint(
    val value: Float, 
    val time: String, 
    val timestamp: Long = 0L, 
    val rawValue: Float = 0f,
    val rate: Float = 0f
)

fun getTrendIcon(rate: Float, modifier: Modifier = Modifier): ImageVector =
    when {
        rate > 0.5f -> Icons.Rounded.TrendingUp
        rate < -0.5f -> Icons.Rounded.TrendingDown
        else -> Icons.Rounded.TrendingFlat
    }

fun getTrendDescription(rate: Float): String {
    return when {
        rate > 0 -> "The trend is rising by $rate percent."
        rate < 0 -> "The trend is falling by ${Math.abs(rate)} percent."
        else -> "The trend remains unchanged."
    }
}


enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class TimeRange(val label: String, val hours: Int) {
    H1("1H", 1),
    H3("3H", 3),
    H6("6H", 6),
    H12("12H", 12),
    H24("24H", 24),
    D3("3D", 72),
//    D7("7D", 168)

}

@Keep
fun setComposeContent(activity: AppCompatActivity, legacyView: View?) {
    activity.setContent {
        val prefs = activity.getSharedPreferences(activity.packageName + "_preferences", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        var themeMode by remember { mutableStateOf(ThemeMode.valueOf(savedTheme)) }

        JugglucoTheme(themeMode = themeMode) {
            MainApp(
                themeMode = themeMode,
                onThemeChanged = { newMode ->
                    themeMode = newMode
                    prefs.edit().putString("theme_mode", newMode.name).apply()
                }
            )
        }
    }
}



// ...

@Composable
fun JugglucoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFF90CAF9),
            secondary = Color(0xFF81D4FA),
            tertiary = Color(0xFFCE93D8),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onTertiary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
        else -> lightColorScheme(
            primary = Color(0xFF1565C0),
            secondary = Color(0xFF039BE5),
            tertiary = Color(0xFF7B1FA2),
            background = Color(0xFFFAFAFA),
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun MainApp(themeMode: ThemeMode, onThemeChanged: (ThemeMode) -> Unit) {
    val navController = rememberNavController()
    val dashboardViewModel: DashboardViewModel = viewModel()
    val context = LocalContext.current
    
    // Handle back button to exit app when on start destination
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    
    BackHandler(enabled = currentRoute == "dashboard") {
        (context as? Activity)?.finish()
    }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = currentBackStackEntry?.destination?.route

                val onNavigate = { route: String ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                // --- DASHBOARD ---
                NavigationBarItem(
                    icon = {
                        TabIcon(
                            isSelected = currentRoute == "dashboard",
                            selectedIcon = Icons.Filled.ShowChart,
                            unselectedIcon = Icons.Outlined.ShowChart
                            ,
                            description = "Dashboard"
                        )
                    },
                    label = { Text("Dashboard") },
                    selected = currentRoute == "dashboard",
                    onClick = { onNavigate("dashboard") }
                )

                // --- SENSORS ---
                NavigationBarItem(
                    icon = {
                        TabIcon(
                            isSelected = currentRoute == "sensors",
                            selectedIcon = Icons.Filled.Sensors,
                            unselectedIcon = Icons.Outlined.Sensors,
                            description = "Sensors"
                        )
                    },
                    label = { Text("Sensors") },
                    selected = currentRoute == "sensors",
                    onClick = { onNavigate("sensors") }
                )

                // --- SETTINGS ---
                NavigationBarItem(
                    icon = {
                        TabIcon(
                            isSelected = currentRoute == "settings",
                            selectedIcon = Icons.Filled.Settings,
                            unselectedIcon = Icons.Outlined.Settings,
                            description = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = { onNavigate("settings") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding),
            // Use a fast fade (200ms) for a snappy feel that isn't jarring
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable("dashboard") { DashboardScreen(dashboardViewModel) }
            composable("sensors") { SensorScreen() }
            composable("settings") { SettingsScreen(navController, themeMode, onThemeChanged, dashboardViewModel) }
            composable("settings/nightscout") { NightscoutSettingsScreen(navController) }
            composable("settings/mirror") { MirrorSettingsScreen(navController) }
            composable("settings/mirror/edit/{pos}") { backStackEntry ->
                val pos = backStackEntry.arguments?.getString("pos")?.toIntOrNull() ?: -1
                MirrorEditScreen(navController, pos)
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val context = LocalContext.current
// This runs every time the Activity/Fragment/Screen hits the ON_RESUME state
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshData()
    }

    val currentGlucose by viewModel.currentGlucose.collectAsState()
    val currentRate by viewModel.currentRate.collectAsState()
    val sensorName by viewModel.sensorName.collectAsState()
    val daysRemaining by viewModel.daysRemaining.collectAsState()

    val glucoseData by viewModel.glucoseHistory.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val targetLow by viewModel.targetLow.collectAsState()
    val targetHigh by viewModel.targetHigh.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    Scaffold(
        // FIX: Remove system bar insets (handled by parent MainApp)
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            if (glucoseData.isEmpty()) {
                FloatingActionButton(onClick = {
                    if (context is Activity) {
                         val integrator = IntentIntegrator(context)
                         integrator.setRequestCode(MainActivity.REQUEST_BARCODE)
                         integrator.initiateScan()
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Sensor")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // FIX: Only pad horizontally, let content touch top/bottom naturally
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Optional: Small spacer if it feels too tight to the status bar
            Spacer(modifier = Modifier.height(8.dp))

            val latestPoint = remember(glucoseData) { glucoseData.maxByOrNull { it.timestamp } }

            // ... (Rest of DashboardScreen content remains exactly as you have it) ...
            // Just paste your existing Card, Chart, and LazyColumn code here.

            // Current Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            val finalDisplayGlucose = remember(currentGlucose, viewMode, latestPoint) {
                                if (latestPoint != null && (viewMode == 1 || viewMode == 2)) {
                                    val rawVal = latestPoint.rawValue
                                    val rawStr = if (rawVal < 30) String.format("%.1f", rawVal) else rawVal.toInt().toString()
                                    when (viewMode) {
                                        1 -> rawStr
                                        2 -> "$currentGlucose · $rawStr"
                                        else -> currentGlucose
                                    }
                                } else {
                                    currentGlucose
                                }
                            }
                            Text(text = finalDisplayGlucose, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = unit, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(  8.dp) )

                        Icon(imageVector = getTrendIcon(currentRate), contentDescription = getTrendDescription(currentRate), modifier = Modifier.size(48.dp).padding(  4.dp))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (sensorName.isNotEmpty()) Text(sensorName, style = MaterialTheme.typography.titleMedium)
                if (daysRemaining.isNotEmpty()) Text(daysRemaining, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Card(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (glucoseData.isNotEmpty()) {
                        InteractiveGlucoseChart(
                            fullData = glucoseData,
                            targetLow = targetLow,
                            targetHigh = targetHigh,
                            unit = unit,
                            viewMode = viewMode
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data available") }
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(glucoseData.takeLast(10).reversed()) { item ->
                    ReadingRow(item, unit, viewMode)
                }
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun InteractiveGlucoseChart(
    fullData: List<GlucosePoint>,
    targetLow: Float,
    targetHigh: Float,
    unit: String,
    viewMode: Int = 0
) {
    // --- THEME & PAINTS ---
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val pointColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    // 1. Select the correct Material Green shade (300 for Dark, 700 for Light)
    val isDark = isSystemInDarkTheme()
    val materialGreen = if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)
    // 2. Apply "Container" level opacity (0.12f is standard for M3 highlights)
    val targetBandColor = materialGreen.copy(alpha = 0.12f)
//    val targetBandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val hoverLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val minMaxLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    val density = LocalContext.current.resources.displayMetrics.density

    // Paints
    val axisTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 10f * density
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    val xTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 10f * density
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    // --- VIEWPORT STATE ---
    val now = System.currentTimeMillis()
    // Safe Data: Ensure sorted
    val safeData = remember(fullData) { fullData.sortedBy { it.timestamp } }
    val latestDataTimestamp = safeData.lastOrNull()?.timestamp ?: 0L

    var lastAutoScrolledTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    var visibleDuration by rememberSaveable { mutableLongStateOf(3L * 60 * 60 * 1000) }
    var centerTime by rememberSaveable { mutableLongStateOf(now - visibleDuration / 2) }

    // Auto-scroll logic
    LaunchedEffect(latestDataTimestamp) {
        if (latestDataTimestamp > lastAutoScrolledTimestamp) {
            centerTime = System.currentTimeMillis() - visibleDuration / 2
            lastAutoScrolledTimestamp = latestDataTimestamp
        }
    }

    // --- Y-AXIS STATE (Manual Scaling) ---
    var yMin by rememberSaveable { mutableFloatStateOf(1f) }
    var yMax by rememberSaveable { mutableFloatStateOf(if(targetHigh > 12) 20f else 13f) }

    // --- INTERACTION STATE ---
    var selectedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    var isScrubbing by remember { mutableStateOf(false) } // Touching the line?

    // Physics / Animation
    val coroutineScope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }
    val inertiaAnim = remember { Animatable(0f) }

    // Limits
    val minDuration = 10L * 60 * 1000
    val maxDuration = 72L * 60 * 60 * 1000
    val maxAllowedTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000)

    // --- DATA HELPER (Fixed Interpolation) ---
    fun getPointAt(timeAtTapRaw: Double): GlucosePoint? {
        val minuteInMillis = 60000.0
        val snappedTime = (kotlin.math.round(timeAtTapRaw / minuteInMillis) * minuteInMillis).toLong()
        val idx = safeData.binarySearch { it.timestamp.compareTo(snappedTime) }

        if (idx >= 0) {
            val p = safeData[idx]
            // CRITICAL FIX: If raw is missing (0), fallback to calibrated value so it doesn't drop
            val cleanRaw = if (p.rawValue < 0.1f) p.value else p.rawValue
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(snappedTime))
            return p.copy(time = timeStr, rawValue = cleanRaw)
        }

        val insertionPoint = -(idx + 1)
        if (insertionPoint > 0 && insertionPoint < safeData.size) {
            val p1 = safeData[insertionPoint - 1]
            val p2 = safeData[insertionPoint]

            if (p2.timestamp > p1.timestamp) {
                val fraction = (snappedTime - p1.timestamp).toFloat() / (p2.timestamp - p1.timestamp)

                // Interpolate Value
                val interpValue = p1.value + (p2.value - p1.value) * fraction

                // CRITICAL FIX: Handle 0.0 Raw Values in interpolation
                val r1 = if(p1.rawValue < 0.1f) p1.value else p1.rawValue
                val r2 = if(p2.rawValue < 0.1f) p2.value else p2.rawValue
                val interpRaw = r1 + (r2 - r1) * fraction

                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(snappedTime))
                return GlucosePoint(interpValue, timeStr, snappedTime, interpRaw, 0f)
            }
        }
        return null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Kill inertia
                        coroutineScope.launch { inertiaAnim.snapTo(0f) }
                        velocityTracker.resetTracking()

                        // --- HIT TEST ---
                        // Did the user touch the line?
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val chartHeight = height - 30.dp.toPx()

                        // Calculate Time at touch
                        val viewportStart = centerTime - visibleDuration / 2
                        val timeAtTouch =
                            viewportStart + ((down.position.x / width).toDouble() * visibleDuration)

                        // Get rough Y position of data at this time
                        val pointAtTouch = getPointAt(timeAtTouch)

                        // Determine visual Y position of the line
                        val dataY = if (pointAtTouch != null) {
                            val v = if (viewMode == 1) pointAtTouch.rawValue else pointAtTouch.value
                            chartHeight - ((v - yMin) / (yMax - yMin)) * chartHeight
                        } else -1000f

                        // Threshold: 60dp around the line for scrubbing
                        val touchThreshold = 60.dp.toPx()
                        isScrubbing = abs(down.position.y - dataY) < touchThreshold

                        if (isScrubbing) {
                            // INITIAL SELECTION
                            selectedPoint = pointAtTouch
                        }

                        var change = down
                        while (true) {
                            val event = awaitPointerEvent()
                            val newChange = event.changes.firstOrNull() ?: break
                            if (newChange.changedToUp()) break

                            velocityTracker.addPointerInputChange(newChange)

                            // Check for Multitouch (Zoom)
                            val pointerCount = event.changes.size
                            if (pointerCount > 1) {
                                // 2-Finger Zoom Logic
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    val newDuration = (visibleDuration / zoomChange).toLong()
                                    visibleDuration = newDuration.coerceIn(minDuration, maxDuration)
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                // 1-Finger Logic
                                if (isScrubbing) {
                                    // MODE A: SCRUBBING (Move Cursor)
                                    val currentFrac = (newChange.position.x / width).toDouble()
                                    val currentTime =
                                        viewportStart + (currentFrac * visibleDuration)
                                    selectedPoint = getPointAt(currentTime)
                                } else {
                                    // MODE B: PAN / SCALE
                                    val panX = newChange.position.x - change.position.x
                                    val panY = newChange.position.y - change.position.y

                                    // 1. Pan Time (Horizontal)
                                    if (abs(panX) > abs(panY)) {
                                        val timePerPixel = visibleDuration.toFloat() / width
                                        val timeDelta = -(panX * timePerPixel).toLong()
                                        centerTime =
                                            (centerTime + timeDelta).coerceAtMost(maxAllowedTime)
                                    }
                                    // 2. Scale Y (Vertical) - Top vs Bottom Half
                                    else {
                                        val scaleFactor = panY * (yMax - yMin) / height * 2f
                                        if (change.position.y < height / 2) {
                                            yMax = (yMax + scaleFactor).coerceAtLeast(yMin + 10f)
                                        } else {
                                            yMin = (yMin + scaleFactor).coerceAtLeast(0f)
                                        }
                                    }
                                }
                                newChange.consume()
                            }
                            change = newChange
                        }

                        // Finger Up - Inertia (Only if panning)
                        if (!isScrubbing) {
                            val velocity = velocityTracker.calculateVelocity()
                            val vx = velocity.x

                            if (abs(vx) > 1000f) {
                                coroutineScope.launch {
                                    var lastVal = 0f
                                    inertiaAnim.snapTo(0f)
                                    inertiaAnim.animateDecay(
                                        initialVelocity = -vx,
                                        animationSpec = exponentialDecay(frictionMultiplier = 2f)
                                    ) {
                                        val delta = this.value - lastVal
                                        val tPerPix =
                                            visibleDuration.toFloat() / size.width.toFloat()
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
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val bottomAxisHeight = 30.dp.toPx()
                val chartHeight = height - bottomAxisHeight

                // Viewport
                val viewportStart = centerTime - visibleDuration / 2
                val viewportEnd = centerTime + visibleDuration / 2
                val paddingTime = visibleDuration / 5

                // Filter Data
                val visiblePoints = safeData.filter {
                    it.timestamp >= (viewportStart - paddingTime) && it.timestamp <= (viewportEnd + paddingTime)
                }

                // --- HELPERS ---
                fun timeToX(t: Long): Float = ((t - viewportStart).toFloat() / visibleDuration.toFloat()) * width
                fun valToY(v: Float): Float = chartHeight - ((v - yMin) / (yMax - yMin)) * chartHeight

                // --- 1. DRAW Y-AXIS GRID ---
                val yRangeSpan = yMax - yMin
                val yStep = if (yRangeSpan < 25) 2f else 50f
                // Start drawing from the calculated minimum
                var yDraw = (kotlin.math.ceil(yMin / yStep) * yStep).toInt()

                // Loop until we reach the max
                while (yDraw < yMax) {
                    val y = valToY(yDraw.toFloat())

                    // Draw the line ONLY if it's actually on screen
                    if (y >= 0 && y <= chartHeight) {
                        drawLine(gridColor, Offset(0f, y), Offset(width, y), 1f)
                    }

                    // Draw the Text ALWAYS, but force it to stay on screen
                    // .coerceIn(20f, ...) prevents it from going off the top
                    val textY = y.coerceIn(20f, chartHeight - 5f)

                    drawContext.canvas.nativeCanvas.drawText(
                        yDraw.toString(),
                        10f,
                        textY - 6f,
                        axisTextPaint
                    )

                    yDraw += yStep.toInt()
                }

                // --- 2. DRAW X-AXIS GRID ---
                val gridInterval = when {
                    visibleDuration < 2 * 60 * 60 * 1000 -> 15 * 60 * 1000L
                    visibleDuration < 6 * 60 * 60 * 1000 -> 30 * 60 * 1000L
                    else -> 2 * 60 * 60 * 1000L
                }
                var t = (viewportStart / gridInterval) * gridInterval
                while (t <= viewportEnd + gridInterval) {
                    val x = timeToX(t)
                    if (x in 0f..width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, chartHeight), 1f)
                        val label = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t))
                        drawContext.canvas.nativeCanvas.drawText(label, x, height - 10f, xTextPaint)
                    }
                    t += gridInterval
                }

                // --- 3. DATA LINES ---
                if (visiblePoints.size > 1) {
                    fun drawPath(useRaw: Boolean, color: Color, width: Float) {
                        val path = Path()
                        val first = visiblePoints.first()
                        val startVal = if(useRaw) (if(first.rawValue < 0.1f) first.value else first.rawValue) else first.value
                        path.moveTo(timeToX(first.timestamp), valToY(startVal))

                        visiblePoints.drop(1).forEach {
                            val v = if(useRaw) (if(it.rawValue < 0.1f) it.value else it.rawValue) else it.value
                            path.lineTo(timeToX(it.timestamp), valToY(v))
                        }
                        drawPath(path, color, style = Stroke(width = width))
                    }
                    if (viewMode == 1 || viewMode == 2) drawPath(true, secondaryColor, 2.dp.toPx())
                    if (viewMode == 0 || viewMode == 2) drawPath(false, primaryColor, 3.dp.toPx())
                }

                // --- 4. MIN/MAX INDICATORS (On Left) ---
                if (visiblePoints.isNotEmpty()) {
                    // Decide what to show based on viewMode
                    val minPoint: GlucosePoint
                    val maxPoint: GlucosePoint

                    // Filter out 0s if checking Raw
                    if (viewMode == 1) {
                        minPoint = visiblePoints.filter { it.rawValue > 0.1f }.minByOrNull { it.rawValue } ?: visiblePoints.first()
                        maxPoint = visiblePoints.filter { it.rawValue > 0.1f }.maxByOrNull { it.rawValue } ?: visiblePoints.first()
                    } else {
                        minPoint = visiblePoints.minByOrNull { it.value } ?: visiblePoints.first()
                        maxPoint = visiblePoints.maxByOrNull { it.value } ?: visiblePoints.first()
                    }

                    fun drawIndicator(point: GlucosePoint) {
                        val v = if (viewMode == 1) point.rawValue else point.value
                        val y = valToY(v)
                        val x = timeToX(point.timestamp)

                        if (y in 0f..chartHeight && x in 0f..width) {
                            // Label on Left
                            val label = String.format("%.1f", v)
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                35f, // Near Left Axis
                                y - 5f,
                                axisTextPaint.apply { color = android.graphics.Color.DKGRAY }
                            )
                            // Subtle dashed line from label to point
                            drawLine(
                                color = minMaxLineColor,
                                start = Offset(80f, y),
                                end = Offset(x, y),
                                strokeWidth = 1f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                            axisTextPaint.color = android.graphics.Color.GRAY
                        }
                    }
                    drawIndicator(maxPoint)
                    drawIndicator(minPoint)
                }

                // --- 5. TARGET RANGE ---
                val yHigh = valToY(targetHigh)
                val yLow = valToY(targetLow)
                drawRect(targetBandColor, topLeft = Offset(0f, yHigh), size = Size(width, yLow - yHigh))

                // --- 6. CURSOR & BUBBLE ---
                val cursorX = if (selectedPoint != null) timeToX(selectedPoint!!.timestamp) else null
                cursorX?.let { x ->
                    if (x in 0f..width) {
                        drawLine(hoverLineColor, Offset(x, 0f), Offset(x, chartHeight), 2.dp.toPx())

                        selectedPoint?.let { p ->
                            // Draw Dots
                            if (viewMode == 1 || viewMode == 2) drawCircle(secondaryColor, 5.dp.toPx(), Offset(x, valToY(p.rawValue)))
                            if (viewMode == 0 || viewMode == 2) drawCircle(pointColor, 6.dp.toPx(), Offset(x, valToY(p.value)))

                            // Bubble
                            val timeLabel = p.time
                            val textBounds = android.graphics.Rect()
                            xTextPaint.getTextBounds(timeLabel, 0, timeLabel.length, textBounds)
                            val labelWidth = textBounds.width() + 40f
                            val labelHeight = 12.dp.toPx()
                            val bubbleLeft = (x - labelWidth/2).coerceIn(0f, width - labelWidth)

                            drawRoundRect(pointColor, topLeft = Offset(bubbleLeft, height - labelHeight - 5f), size = Size(labelWidth, labelHeight), cornerRadius = CornerRadius(4.dp.toPx()))

                            val isDark = (pointColor.red + pointColor.green + pointColor.blue) / 3 < 0.5
                            xTextPaint.color = if(isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                            drawContext.canvas.nativeCanvas.drawText(timeLabel, bubbleLeft + labelWidth/2, height - 10f, xTextPaint)
                            xTextPaint.color = android.graphics.Color.GRAY
                        }
                    }
                }
            }

            // --- INFO CARD ---
            selectedPoint?.let { point ->
                Card(modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val valStr = String.format("%.1f", point.value)
                        val rawStr = String.format("%.1f", point.rawValue)
                        val txt = if(viewMode == 2) "$valStr · $rawStr $unit" else "$valStr $unit"
                        Text(txt, color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.Bold)
//                        Text(point.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // --- ZOOM BUTTONS ---
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TimeRange.values().forEach { range ->
                val rangeDur = range.hours * 60 * 60 * 1000L
                val isSel = abs(visibleDuration - rangeDur) < 1000
                FilterChip(
                    selected = isSel,
                    onClick = { visibleDuration = rangeDur; centerTime = System.currentTimeMillis() - visibleDuration/2 },
                    label = { Text(range.label) },
//                    leadingIcon = if(isSel) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null
                )
            }
        }
    }
}
@Composable
fun ReadingRow(point: GlucosePoint, unit: String, viewMode: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Time Column
        Text(
            text = point.time,
            style = MaterialTheme.typography.bodyMedium
        )

        // Value Column
        val valToUse = if (viewMode == 1) point.rawValue else point.value
        val valStr = if (valToUse < 30) String.format("%.1f", valToUse) else valToUse.toInt().toString()

        if (viewMode == 2) {
            // Mode 2: Show "Calibrated | Raw"
            // Fixed typo: used point.rawValue instead of point.c
            val rawStr = if (point.rawValue < 30) String.format("%.1f", point.rawValue) else point.rawValue.toInt().toString()
            Text(
                text = "$valStr · $rawStr $unit",
                fontWeight = FontWeight.Bold
            )
        } else {
            // Mode 0 or 1: Show single value
            Text(
                text = "$valStr $unit",
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
fun SearchScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Filter by date") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsScreen(navController: androidx.navigation.NavController, themeMode: ThemeMode, onThemeChanged: (ThemeMode) -> Unit, viewModel: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val unit by viewModel.unit.collectAsState()
    val isMmol = unit == "mmol/L"
    val uriHandler = LocalUriHandler.current

    val xDripEnabled by viewModel.xDripBroadcastEnabled.collectAsState()
    val patchedLibreEnabled by viewModel.patchedLibreBroadcastEnabled.collectAsState()

    var showUnitDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Alarms
    val hasLowAlarm by viewModel.hasLowAlarm.collectAsState()
    val lowAlarmValue by viewModel.lowAlarmThreshold.collectAsState()
    val lowAlarmSoundMode by viewModel.lowAlarmSoundMode.collectAsState()

    val hasHighAlarm by viewModel.hasHighAlarm.collectAsState()
    val highAlarmValue by viewModel.highAlarmThreshold.collectAsState()
    val highAlarmSoundMode by viewModel.highAlarmSoundMode.collectAsState()

    // Targets
    val targetLowValue by viewModel.targetLow.collectAsState()
    val targetHighValue by viewModel.targetHigh.collectAsState()

    // --- GRAPH RANGE STATE (Connect these to your ViewModel!) ---
    // Example: val graphMinVal by viewModel.graphMin.collectAsState()
    var graphMinVal by remember { mutableFloatStateOf(if (isMmol) 0f else 0f) }
    var graphMaxVal by remember { mutableFloatStateOf(if (isMmol) 25f else 400f) }

    // Scroll State for the Fix
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // <--- SCROLL FIX
//            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Spacer(modifier = Modifier.padding(horizontal = 16.dp)) // Add padding to text)

        // --- UNIT ---
        ListItem(
            headlineContent = { Text("Unit") },
            supportingContent = { Text(unit) },
            modifier = Modifier.clickable { showUnitDialog = true }
        )
        if (showUnitDialog) {
            AlertDialog(
                onDismissRequest = { showUnitDialog = false },
                title = { Text("Select Unit") },
                text = {
                    Column {
                        listOf("mg/dL" to 0, "mmol/L" to 1).forEach { (label, value) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setUnit(value)
                                        showUnitDialog = false
                                    }
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (if (isMmol) 1 else 0) == value, onClick = null)
                                Text(text = label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showUnitDialog = false }) { Text("Cancel") } }
            )
        }

        // --- THEME ---
        ListItem(
            headlineContent = { Text("Theme") },
            supportingContent = { Text(themeMode.name.lowercase().capitalize()) },
            modifier = Modifier.clickable { showThemeDialog = true }
        )
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Select Theme") },
                text = {
                    Column {
                        ThemeMode.values().forEach { mode ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onThemeChanged(mode)
                                        showThemeDialog = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = themeMode == mode, onClick = null)
                                Text(text = mode.name.lowercase().capitalize(), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") } }
            )
        }

        ListItem(
            headlineContent = { Text("Use Google Scan") },
            supportingContent = { Text("Use Google Play Services for QR scanning") },
            trailingContent = {
                var googleScan by remember { mutableStateOf(Natives.getGoogleScan()) }
                Switch(
                    checked = googleScan,
                    onCheckedChange = { 
                        Natives.setGoogleScan(it)
                        googleScan = it
                    }
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text("Exchange Data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))

        ListItem(
            headlineContent = { Text("xDrip Broadcast") },
            supportingContent = { Text("Libre (patched App)") },
            trailingContent = {
                Switch(
                    checked = patchedLibreEnabled,
                    onCheckedChange = { viewModel.togglePatchedLibreBroadcast(it) }
                )
            }
        )

        ListItem(
            headlineContent = { Text("Mirror") },
            supportingContent = { Text("Share/Receive data via Internet/Home Net") },
            modifier = Modifier.clickable { navController.navigate("settings/mirror") }
        )

        
        ListItem(
            headlineContent = { Text("Nightscout") },
            supportingContent = { Text("Upload to Nightscout") },
            modifier = Modifier.clickable { navController.navigate("settings/nightscout") }
        )



        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- GLUCOSE ALERTS ---
        Text("Blood Glucose Alerts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(16.dp))

        AlarmCard(
            title = "Low",
            enabled = hasLowAlarm,
            value = lowAlarmValue,
            unit = unit,
            soundMode = lowAlarmSoundMode,
            range = if (isMmol) 2.0f..6.0f else 36f..108f,
            onToggle = { viewModel.setLowAlarm(it, lowAlarmValue) },
            onValueChange = { viewModel.setLowAlarm(true, it) },
            onSoundChange = { viewModel.setAlarmSound(0, it) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        AlarmCard(
            title = "High",
            enabled = hasHighAlarm,
            value = highAlarmValue,
            unit = unit,
            soundMode = highAlarmSoundMode,
            range = if (isMmol) 6.0f..25.0f else 108f..450f,
            onToggle = { viewModel.setHighAlarm(it, highAlarmValue) },
            onValueChange = { viewModel.setHighAlarm(true, it) },
            onSoundChange = { viewModel.setAlarmSound(1, it) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- TARGET RANGES ---
        Text("Target Glucose Range", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        TargetCard(
            title = "Low",
            value = targetLowValue,
            unit = unit,
            range = if (isMmol) 2.0f..8.0f else 40f..140f,
            onValueChange = { viewModel.setTargetLow(it) },
            modifier = Modifier.padding(horizontal = 16.dp)

        )
        Spacer(modifier = Modifier.height(16.dp))

        TargetCard(
            title = "High",
            value = targetHighValue,
            unit = unit,
            range = if (isMmol) 6.0f..20.0f else 100f..350f,
            onValueChange = { viewModel.setTargetHigh(it) },
            modifier = Modifier.padding(horizontal = 16.dp)

        )

//        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
//
//        // --- GRAPH BOUNDS (New Section) ---
//        Text("Graph Y-Axis Scale", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
//
//        // Reusing TargetCard logic for consistency
//        TargetCard(
//            title = "Graph Min (Bottom)",
//            value = graphMinVal,
//            unit = unit,
//            range = if (isMmol) 0f..10f else 0f..180f,
//            onValueChange = {
//                graphMinVal = it
//                // TODO: viewModel.setGraphMin(it)
//            }
//        )
//        Spacer(modifier = Modifier.height(16.dp))
//
//        TargetCard(
//            title = "Graph Max (Top)",
//            value = graphMaxVal,
//            unit = unit,
//            range = if (isMmol) 10f..40f else 180f..600f,
//            onValueChange = {
//                graphMaxVal = it
//                // TODO: viewModel.setGraphMax(it)
//            }
//        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- ABOUT SECTION ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // App Name & Logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Using generic icon to avoid build errors if R.mipmap.ic_launcher is missing
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "JugglucoNG",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "github.com/ctqwa/JugglucoNG",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/ctqwa/JugglucoNG")
                }
            )
            Text(
                text = "• GPL-3.0 •",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "A fork of Juggluco https://github.com/j-kaltes/Juggluco/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )


        }

        Spacer(modifier = Modifier.height(80.dp))
    }

        // Add bottom padding so the last item isn't covered by navigation bar
        Spacer(modifier = Modifier.height(80.dp))
    }



@Composable
fun TargetCard(
    title: String,
    value: Float,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            var sliderVal by remember(value) { mutableStateOf(value.coerceIn(range)) }

            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                val currentSliderValStr = if (sliderVal < 30) String.format("%.1f", sliderVal) else sliderVal.toInt().toString()
                Text(
                    text = "$currentSliderValStr $unit",
                    style = MaterialTheme.typography.titleSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Slider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                onValueChangeFinished = { onValueChange(sliderVal) },
                valueRange = range,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AlarmCard(
    title: String,
    enabled: Boolean,
    value: Float,
    unit: String,
    soundMode: Int,
    range: ClosedFloatingPointRange<Float>,
    onToggle: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onSoundChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    val valStr = if (value < 30) String.format("%.1f", value) else value.toInt().toString()
//                    Text("Threshold: $valStr $unit", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            
            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))
                
                var sliderVal by remember(value) { mutableStateOf(value.coerceIn(range)) }
                
                // Display current slider value
                val currentSliderValStr = if (sliderVal < 30) String.format("%.1f", sliderVal) else sliderVal.toInt().toString()
                Text(
                    text = "$currentSliderValStr $unit",
                    style = MaterialTheme.typography.labelLarge, 
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Slider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    onValueChangeFinished = { onValueChange(sliderVal) },
                    valueRange = range,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Alert Sound", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = soundMode == 0,
                        onClick = { onSoundChange(0) },
                        label = { Text("Vibrate") }
                    )
                    FilterChip(
                        selected = soundMode == 1,
                        onClick = { onSoundChange(1) },
                        label = { Text("Sound & Vibrate") }
                    )
                }
            }
        }
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
}

@Composable
fun SensorScreen(viewModel: tk.glucodata.ui.viewmodel.SensorViewModel = viewModel()) {
    val context = LocalContext.current
    val sensors by viewModel.sensors.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshSensors()
    }

    Scaffold(
        // FIX: Remove system bar insets
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (context is Activity) {
                     val integrator = IntentIntegrator(context)
                     integrator.setRequestCode(MainActivity.REQUEST_BARCODE)
                     integrator.initiateScan()
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Sensor")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                // FIX: Only pad horizontally
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Sensors", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))

            if (sensors.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No sensors found")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sensors) { sensor ->
                        SensorCard(sensor, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun TabIcon(
    isSelected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    description: String
) {
    AnimatedContent(
        targetState = isSelected,
        transitionSpec = {
            if (targetState) {
                // Selected: Filled icon "pops" in (Scale + Fade)
                (scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            } else {
                // Deselected: Outline icon fades back in normal
                fadeIn(animationSpec = tween(200))
                    .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)))
            }
        },
        label = "TabIconAnimation"
    ) { selected ->
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = description
        )
    }
}


@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SensorCard(sensor: tk.glucodata.ui.viewmodel.SensorInfo, viewModel: tk.glucodata.ui.viewmodel.SensorViewModel) {
    var showTerminateDialog by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showReconnectDialog by remember { mutableStateOf(false) }
    var wipeDataChecked by remember { mutableStateOf(false) }

    if (showTerminateDialog) {
        AlertDialog(
            onDismissRequest = { 
                showTerminateDialog = false 
                wipeDataChecked = false
            },
            title = { Text("Disconnect Sensor?") },
            text = { 
                Column {
                    Text("This will permanently stop the sensor session.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = wipeDataChecked,
                            onCheckedChange = { wipeDataChecked = it }
                        )
                        Text("Wipe sensor data")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.terminateSensor(sensor.serial, wipeDataChecked)
                    showTerminateDialog = false 
                    wipeDataChecked = false
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showTerminateDialog = false 
                    wipeDataChecked = false
                }) { Text("Cancel") }
            }
        )
    }

    if (showReconnectDialog) {
        AlertDialog(
            onDismissRequest = { 
                showReconnectDialog = false 
                wipeDataChecked = false
            },
            title = { Text("Reconnect Sensor?") },
            text = { 
                Column {
                    Text("This will reset the connection and attempt to reconnect.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = wipeDataChecked,
                            onCheckedChange = { wipeDataChecked = it }
                        )
                        Text("Wipe sensor data")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reconnectSensor(sensor.serial, wipeDataChecked)
                    showReconnectDialog = false
                    wipeDataChecked = false
                }) { Text("Reconnect") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showReconnectDialog = false 
                    wipeDataChecked = false
                }) { Text("Cancel") }
            }
        )
    }
    
    if (showForgetDialog) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text("Forget Sensor?") },
            text = { Text("This will remove the sensor from the list. It may reappear if scanned again.") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.forgetSensor(sensor.serial)
                    showForgetDialog = false 
                }) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { showForgetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset sensor?") },
            text = { Text("This will reset the Sibionics 2 sensor. Continue?") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.resetSensor(sensor.serial)
                    showResetDialog = false 
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Reset calibration?") },
            text = { Text("Clear all calibration data and restart algorithm?") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.clearCalibration(sensor.serial)
                    showClearDialog = false 
                }) { Text("Reset calibration") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Reset all?") },
            text = { Text("Clear all old data, calibrations, and reset the sensor?") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.clearAll(sensor.serial)
                    showClearAllDialog = false 
                }) { Text("Reset all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
//            val titleText = sensor.serial
                val titleText = if (sensor.streaming) "${sensor.serial} • Enabled" else "${sensor.serial} • Disabled"
                Text(titleText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                if (sensor.connectionStatus.isNotEmpty()) {
                    InfoRow("Last BLE status", sensor.connectionStatus)
                }
                InfoRow("Address", sensor.deviceAddress)
                InfoRow("Started", formatSensorTime(sensor.starttime))
                if (sensor.officialEnd.isNotEmpty()) {
                    InfoRow("Ends Officially", formatSensorTime(sensor.officialEnd))
                }
                if (sensor.expectedEnd.isNotEmpty()) {
                    InfoRow("Expected End", formatSensorTime(sensor.expectedEnd))
                }
//                InfoRow("Streaming", if (sensor.streaming) "Enabled" else "Disabled")

            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Calibration Mode
            Text("Calibration Algorithm", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val modes = listOf("Auto", "Raw", "Auto+Raw")
                modes.forEachIndexed { index, title ->
                    FilterChip(
                        selected = sensor.viewMode == index,
                        onClick = { viewModel.setCalibrationMode(sensor.serial, index) },
                        label = { Text(title) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                FilledTonalButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset auto calibration", maxLines = 1)
                }
            }
            if (sensor.isSibionics2) {

                Spacer(modifier = Modifier.height(16.dp))
//
//                var sliderPosition by remember(sensor.autoResetDays) { mutableStateOf(sensor.autoResetDays.toFloat().coerceIn(0f, 22f)) }
//
//                Column(modifier = Modifier.fillMaxWidth()) {
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Text("Auto Reset", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
//                        Text(
//                            text = if (sliderPosition == 0f) "Never" else "${sliderPosition.toInt()} days",
//                            style = MaterialTheme.typography.bodyMedium,
//                            fontWeight = FontWeight.SemiBold
//                        )
//                    }
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    Slider(
//                        value = sliderPosition,
//                        onValueChange = { sliderPosition = it },
//                        onValueChangeFinished = {
//                            viewModel.setAutoResetDays(sensor.serial, sliderPosition.toInt())
//                        },
//                        valueRange = 0f..22f,
//                        modifier = Modifier.fillMaxWidth()
//                    )
//                }
//


//                // -- New Auto Reset slider
//
//                // Logic: 0 = Never/Off. 1..22 = On.
//                val isAutoResetEnabled = sensor.autoResetDays > 0
//
//                // Initialize slider to existing value (or 14 if currently off)
//                var sliderValue by remember(sensor.autoResetDays) {
//                    mutableStateOf(if (isAutoResetEnabled) sensor.autoResetDays.toFloat() else 14f)
//                }
//
//                Column(modifier = Modifier.fillMaxWidth()) {
//                    // Header Row with Switch
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column {
//                            Text("Auto Reset", style = MaterialTheme.typography.titleMedium)
//                            Text(
//                                text = if (isAutoResetEnabled) "${sliderValue.toInt()} days" else "Never",
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                        }
//                        Switch(
//                            checked = isAutoResetEnabled,
//                            onCheckedChange = { enabled ->
//                                val newValue = if (enabled) sliderValue.toInt() else 0
//                                viewModel.setAutoResetDays(sensor.serial, newValue)
//                            }
//                        )
//                    }
//
//                    // Slider (Only visible if enabled)
//                    AnimatedVisibility(visible = isAutoResetEnabled) {
//                        Column {
//                            Spacer(modifier = Modifier.height(8.dp))
//                            Slider(
//                                value = sliderValue,
//                                onValueChange = { sliderValue = it },
//                                valueRange = 1f..22f, // Range is 1-22
//                                steps = 20,           // 20 steps between 1 and 22
//                                onValueChangeFinished = {
//                                    viewModel.setAutoResetDays(sensor.serial, sliderValue.toInt())
//                                }
//                            )
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.SpaceBetween
//                            ) {
//                                Text("1d", style = MaterialTheme.typography.labelSmall)
//                                Text("22d", style = MaterialTheme.typography.labelSmall)
//                            }
//                        }
//                    }
//                }
                // Auto reset
                // LOGIC:
                // < 25 means Enabled (Standard range is 1-22).
                // >= 25 (e.g. 300) means Disabled/Never.
                val isAutoResetEnabled = sensor.autoResetDays < 25

                // Initialize slider. If enabled, use current. If disabled (300), default visual to 21.
                var sliderValue by remember(sensor.autoResetDays) {
                    mutableStateOf(if (isAutoResetEnabled) sensor.autoResetDays.toFloat() else 21f)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto reset", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (isAutoResetEnabled) "${sliderValue.toInt()} days" else "Never",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoResetEnabled,
                            onCheckedChange = { enabled ->
                                // ON: Set to slider value (e.g., 20)
                                // OFF: Set to 300 (Effective Infinity)
                                val newValue = if (enabled) sliderValue.toInt() else 300
                                viewModel.setAutoResetDays(sensor.serial, newValue)
                            }
                        )
                    }
//                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedVisibility(visible = isAutoResetEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Slider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                valueRange = 1f..22f,
                                steps = 20,
                                onValueChangeFinished = {
                                    // Save the actual value (e.g. 18)
                                    viewModel.setAutoResetDays(sensor.serial, sliderValue.toInt())
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),

                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {}
//                            {
//                                Text("1d", style = MaterialTheme.typography.labelSmall)
//                                Text("22d", style = MaterialTheme.typography.labelSmall)
//                            }
                        }
                    }
                }




                // --- ACTION BUTTONS (Always Visible) ---


                Spacer(modifier = Modifier.height(8.dp))

                // Row 1: Reset Actions
                // Use FlowRow or ScrollRow if you have too many, but standard Row fits 3 on most phones.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Use smaller text or icon-only if space is tight
                    FilledTonalButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset sensor", maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset all", maxLines = 1)
                    }
                }
                // Row 2: Main Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp) // Space between buttons
                ) {
                    OutlinedButton(
                        onClick = { showTerminateDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f) // Equal width
                    ) {
                        Text("Disconnect")
                    }
                    OutlinedButton(
                        onClick = { showReconnectDialog = true },
                        modifier = Modifier.weight(1f) // Equal width
                    ) {
                        Text("Reconnect")
                    }
                }
            } // End Column
        } // End Card
}
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightscoutSettingsScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    
    // Load initial values from Natives
    // Note: Natives methods are static and blocking, but usually fast enough for UI.
    // In a perfect world, we'd wrap this in a ViewModel/Suspend, but sticking to existing pattern.
    var url by remember { mutableStateOf(Natives.getnightuploadurl() ?: "") }
    var secret by remember { mutableStateOf(Natives.getnightuploadsecret() ?: "") }
    var isActive by remember { mutableStateOf(Natives.getuseuploader()) }
    var sendTreatments by remember { mutableStateOf(Natives.getpostTreatments()) }
    // V3 is only for phone, watch doesn't support it according to NightPost.java logic (isWearable check)
    // Assuming mobile context here.
    var isV3 by remember { mutableStateOf(Natives.getnightscoutV3()) }
    
    var showSecret by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nightscout Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // Save Actions
                        // Calling Natives.setNightUploader(url, secret, active, v3)
                        Natives.setNightUploader(url, secret, isActive, isV3)
                        Natives.setpostTreatments(sendTreatments)
                        navController.popBackStack()
                        android.widget.Toast.makeText(context, "Saved", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Active Switch
            ListItem(
                headlineContent = { Text("Active") },
                supportingContent = { Text("Enable Nightscout Upload") },
                trailingContent = {
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            )
            
            HorizontalDivider()

            // URL
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Nightscout URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://my-nightscout.herokuapp.com") }
            )
            
            // Secret
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("API Secret") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showSecret) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                   val image = if (showSecret)
                       Icons.Filled.Visibility
                   else
                       Icons.Filled.VisibilityOff

                   IconButton(onClick = { showSecret = !showSecret }) {
                       Icon(imageVector = image, contentDescription = if (showSecret) "Hide password" else "Show password")
                   }
                }
            )

            HorizontalDivider()
            
            // Send Treatments
            ListItem(
                headlineContent = { Text("Send Amounts") },
                supportingContent = { Text("Upload Insulin/Carbs (Treatments)") },
                trailingContent = {
                    Switch(checked = sendTreatments, onCheckedChange = { sendTreatments = it })
                }
            )
            
            // Mobile only V3 check
            ListItem(
                headlineContent = { Text("Use V3 API") },
                supportingContent = { Text("Experimental") },
                trailingContent = {
                    Switch(checked = isV3, onCheckedChange = { isV3 = it })
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Resend / Sync Buttons
            Button(
                onClick = { 
                    Natives.wakeuploader()
                    android.widget.Toast.makeText(context, "Sending now...", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Now")
            }
            
            OutlinedButton(
                onClick = {
                     Natives.resetuploader()
                     android.widget.Toast.makeText(context, "Resend triggered", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Resend Data (Reset)")
            }

        }
    }
}