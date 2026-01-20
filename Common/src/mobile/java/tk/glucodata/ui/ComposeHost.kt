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
import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.material3.Slider
import tk.glucodata.ui.components.StyledSwitch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.text.Layout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.graphics.RectangleShape
import tk.glucodata.ui.util.findActivity
import tk.glucodata.ui.util.hardRestart
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.togetherWith

import androidx.compose.ui.draw.alpha

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally

import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tk.glucodata.Natives
import tk.glucodata.SensorBluetooth
import tk.glucodata.QRmake
import tk.glucodata.R
import tk.glucodata.MainActivity
import android.widget.Toast
import tk.glucodata.ui.viewmodel.DashboardViewModel
import tk.glucodata.ui.theme.displayLargeExpressive
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.res.stringResource
import java.util.Locale
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextDecoration
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange





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
    val rate: Float? = null
)

fun getTrendIcon(rate: Float, modifier: Modifier = Modifier): ImageVector =
    when {
        rate > 0.5f -> Icons.Rounded.TrendingUp
        rate < -0.5f -> Icons.Rounded.TrendingDown
        else -> Icons.Rounded.TrendingFlat
    }

@Composable
fun getTrendDescription(rate: Float): String {
    return when {
        rate > 0 -> stringResource(R.string.trend_rising, rate)
        rate < 0 -> stringResource(R.string.trend_falling, abs(rate))
        else -> stringResource(R.string.trend_unchanged)
    }
}

data class DisplayValues(
    val primaryValue: Float,
    val secondaryValue: Float? = null,
    val tertiaryValue: Float? = null,
    val primaryStr: String,
    val secondaryStr: String? = null,
    val tertiaryStr: String? = null,
    val fullFormatted: String
)

/**
 * Get display values based on view mode and optional calibration.
 * When calibration is active, calibrated value becomes primary and others shift down.
 */
fun getDisplayValues(
    point: GlucosePoint, 
    viewMode: Int, 
    unit: String,
    calibratedValue: Float? = null
): DisplayValues {
    val rawStr = if (point.rawValue < 30) String.format("%.1f", point.rawValue) else point.rawValue.toInt().toString()
    val valStr = if (point.value < 30) String.format("%.1f", point.value) else point.value.toInt().toString()
    val calStr = calibratedValue?.let { if (it < 30) String.format("%.1f", it) else it.toInt().toString() }
    
    // If calibration is active, it becomes primary and everything shifts down
    if (calibratedValue != null && calStr != null) {
        return when (viewMode) {
            1 -> DisplayValues( // Raw → Calibrated primary, Raw secondary
                primaryValue = calibratedValue,
                secondaryValue = point.rawValue,
                primaryStr = calStr,
                secondaryStr = rawStr,
                fullFormatted = "$calStr · $rawStr $unit"
            )
            2 -> DisplayValues( // Auto + Raw → Calibrated primary, Auto secondary, Raw tertiary
                primaryValue = calibratedValue,
                secondaryValue = point.value,
                tertiaryValue = point.rawValue,
                primaryStr = calStr,
                secondaryStr = valStr,
                tertiaryStr = rawStr,
                fullFormatted = "$calStr · $valStr · $rawStr $unit"
            )
            3 -> DisplayValues( // Raw + Auto → Calibrated primary, Raw secondary, Auto tertiary
                primaryValue = calibratedValue,
                secondaryValue = point.rawValue,
                tertiaryValue = point.value,
                primaryStr = calStr,
                secondaryStr = rawStr,
                tertiaryStr = valStr,
                fullFormatted = "$calStr · $rawStr · $valStr $unit"
            )
            else -> DisplayValues( // Auto → Calibrated primary, Auto secondary
                primaryValue = calibratedValue,
                secondaryValue = point.value,
                primaryStr = calStr,
                secondaryStr = valStr,
                fullFormatted = "$calStr · $valStr $unit"
            )
        }
    }
    
    // No calibration - original logic
    return when (viewMode) {
        1 -> DisplayValues( // Raw
            primaryValue = point.rawValue,
            primaryStr = rawStr,
            fullFormatted = "$rawStr $unit"
        )
        2 -> DisplayValues( // Auto + Raw
            primaryValue = point.value,
            secondaryValue = point.rawValue,
            primaryStr = valStr,
            secondaryStr = rawStr,
            fullFormatted = "$valStr · $rawStr $unit"
        )
        3 -> DisplayValues( // Raw + Auto
            primaryValue = point.rawValue,
            secondaryValue = point.value,
            primaryStr = rawStr,
            secondaryStr = valStr,
            fullFormatted = "$rawStr · $valStr $unit"
        )
        else -> DisplayValues( // Auto (0)
            primaryValue = point.value,
            primaryStr = valStr,
            fullFormatted = "$valStr $unit"
        )
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
    D3("3D", 72)
}

@Keep
fun setComposeContent(activity: AppCompatActivity, legacyView: View?) {
    // CRITICAL FIX: Hide the native legacy view (histogram/nanovg) to prevent
    // double-rendering, GPU overdraw, and visual glitches (bleeding through navbar).
    legacyView?.visibility = View.GONE

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
        typography = tk.glucodata.ui.theme.AppTypography,
        content = content
    )
}


sealed class CalibrationSheetState {
    object Hidden : CalibrationSheetState()
    data class New(val auto: Float, val raw: Float, val timestamp: Long) : CalibrationSheetState()
    data class Edit(val entity: tk.glucodata.data.calibration.CalibrationEntity) : CalibrationSheetState()
}

@Composable
fun MainApp(themeMode: ThemeMode, onThemeChanged: (ThemeMode) -> Unit) {
    val navController = rememberNavController()
    val dashboardViewModel: DashboardViewModel = viewModel()
    
    // Hoisted Calibration Sheet State
    var calibrationSheetState by remember { mutableStateOf<CalibrationSheetState>(CalibrationSheetState.Hidden) }
    
    // Shared Data for Sheet
    val glucoseHistory by dashboardViewModel.glucoseHistory.collectAsState()
    val unit by dashboardViewModel.unit.collectAsState()
    val viewMode by dashboardViewModel.viewMode.collectAsState()
    val currentGlucose by dashboardViewModel.currentGlucose.collectAsState()
    
    // Reactive Calibration State
    val calibrations by tk.glucodata.data.calibration.CalibrationManager.calibrations.collectAsState()
    val isRawEnabled by tk.glucodata.data.calibration.CalibrationManager.isEnabledForRaw.collectAsState()
    val isAutoEnabled by tk.glucodata.data.calibration.CalibrationManager.isEnabledForAuto.collectAsState()
    
    val onTriggerCalibration: (CalibrationSheetState) -> Unit = { state ->
        calibrationSheetState = state
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Handle back button to exit app when on start destination
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    BackHandler(enabled = currentRoute == "dashboard") {
        // OPTION 1 (Current): Traditional Android - Back button exits/destroys app
        (context as? Activity)?.finish()

        // OPTION 2 (Alternative): Modern UX - Back = Home (minimizes instead of destroying)
        // Uncomment below to make Back button minimize the app instead of destroying it.
        // This keeps the app in memory like pressing Home, avoiding reload delay.
        // (context as? Activity)?.moveTaskToBack(true)
    }

    // Navigation Items Logic (Shared)
    // Top-level routes that appear in the navbar
    val topLevelRoutes = setOf("dashboard", "sensors", "settings")
    
    // Map subpages to their parent top-level destination
    fun getParentRoute(route: String?): String? = when {
        route == null -> null
        route.startsWith("settings/") -> "settings"
        route.startsWith("sensors/") -> "sensors"
        route == "calibrations" -> "dashboard"  // calibrations is a dashboard subpage
        else -> null
    }
    
    val onNavigate = { route: String ->
        val parentOfCurrent = getParentRoute(currentRoute)
        val isOnSubpageOf = parentOfCurrent == route
        
        when {
            // If we're on a subpage of the clicked nav item, pop back to it
            isOnSubpageOf -> navController.popBackStack(route, inclusive = false)
            // If we're on a different top-level or subpage, navigate normally
            currentRoute != route -> navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // Already on the destination, do nothing
        }
    }

    // Define items for use in both Bar and Rail
    data class NavItem(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)
    val navItems = listOf(
        NavItem("dashboard", stringResource(R.string.dashboard), Icons.Filled.ShowChart, Icons.Outlined.ShowChart),
        NavItem("sensors", stringResource(R.string.sensor), Icons.Filled.Sensors, Icons.Outlined.Sensors),
        NavItem("settings", stringResource(R.string.settings), Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    if (isLandscape) {
        // --- LANDSCAPE: Navigation Rail on Left ---
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                Spacer(modifier = Modifier.weight(1f)) // Center vertically? Or top? Usually top or center.
                // Let's center them vertically for likely better ergonomics in landscape phone

                navItems.forEach { item ->
                    val isSelected = currentRoute == item.route || getParentRoute(currentRoute) == item.route
                    NavigationRailItem(
                        icon = {
                            TabIcon(
                                isSelected = isSelected,
                                selectedIcon = item.selectedIcon,
                                unselectedIcon = item.unselectedIcon,
                                description = item.label
                            )
                        },
                        label = { Text(item.label) },
                        selected = isSelected,
                        onClick = { onNavigate(item.route) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // Content Area
            Scaffold(contentWindowInsets = WindowInsets(0.dp)) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "dashboard",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable("dashboard") { 
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            calibrations = calibrations,
                            onNavigateToCalibrations = { navController.navigate("calibrations") },
                            onTriggerCalibration = onTriggerCalibration
                        ) 
                    }
                    composable("sensors") { SensorScreen() }
                    composable("settings") { ExpressiveSettingsScreen(navController, themeMode, onThemeChanged, dashboardViewModel) }
                    composable("settings/nightscout") { NightscoutSettingsScreen(navController) }
                    composable("settings/mirror") { MirrorSettingsScreen(navController) }
                    composable("settings/mirror/edit/{pos}") { backStackEntry ->
                        val pos = backStackEntry.arguments?.getString("pos")?.toIntOrNull() ?: -1
                        MirrorEditScreen(navController, pos)
                    }
                    composable("settings/debug") { DebugSettingsScreen(navController) }
                    composable("settings/alerts") { tk.glucodata.ui.alerts.AlertSettingsScreen(navController) }
                    composable("calibrations") { 
                        val isMmol = dashboardViewModel.unit.value.contains("mmol", ignoreCase = true)
                        val viewMode by dashboardViewModel.viewMode.collectAsState()
                        tk.glucodata.ui.calibration.CalibrationListScreen(
                            navController = navController, 
                            isMmol = isMmol, 
                            viewMode = viewMode,
                            onAdd = {
                                // Logic to get current value for "Add"
                                // We can use history.firstOrNull() as latest point
                                val latest = glucoseHistory.firstOrNull()
                                val autoVal = latest?.value ?: try { currentGlucose.toFloat() } catch (e: Exception) { 0f }
                                val rawVal = latest?.rawValue ?: autoVal
                                onTriggerCalibration(CalibrationSheetState.New(autoVal, rawVal, System.currentTimeMillis()))
                            },
                            onEdit = { entity ->
                                onTriggerCalibration(CalibrationSheetState.Edit(entity))
                            }
                        ) 
                    }
                }
            }
        }
    } else {
        // --- PORTRAIT: Bottom Navigation Bar ---
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // Fix: Prevent double padding for child Scaffolds
            bottomBar = {
                NavigationBar {
                    navItems.forEach { item ->
                        val isSelected = currentRoute == item.route || getParentRoute(currentRoute) == item.route
                        NavigationBarItem(
                            icon = {
                                TabIcon(
                                    isSelected = isSelected,
                                    selectedIcon = item.selectedIcon,
                                    unselectedIcon = item.unselectedIcon,
                                    description = item.label
                                )
                            },
                            label = { Text(item.label) },
                            selected = isSelected,
                            onClick = { onNavigate(item.route) }
                        )
                    }
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
                composable("dashboard") { 
                    DashboardScreen(
                        viewModel = dashboardViewModel, 
                        onNavigateToCalibrations = { navController.navigate("calibrations") },
                        onTriggerCalibration = onTriggerCalibration
                    ) 
                }
                composable("sensors") { SensorScreen() }
                composable("settings") { ExpressiveSettingsScreen(navController, themeMode, onThemeChanged, dashboardViewModel) }
                composable("settings/nightscout") { NightscoutSettingsScreen(navController) }
                composable("settings/mirror") { MirrorSettingsScreen(navController) }
                composable("settings/mirror/edit/{pos}") { backStackEntry ->
                    val pos = backStackEntry.arguments?.getString("pos")?.toIntOrNull() ?: -1
                    MirrorEditScreen(navController, pos)
                }
                composable("settings/debug") { DebugSettingsScreen(navController) }
                composable("settings/alerts") { tk.glucodata.ui.alerts.AlertSettingsScreen(navController) }
                composable("calibrations") { 
                    val isMmol = dashboardViewModel.unit.value.contains("mmol", ignoreCase = true)
                    val viewMode by dashboardViewModel.viewMode.collectAsState()
                    tk.glucodata.ui.calibration.CalibrationListScreen(
                        navController = navController, 
                        isMmol = isMmol, 
                        viewMode = viewMode,
                        onAdd = {
                            val latest = glucoseHistory.firstOrNull()
                            val autoVal = latest?.value ?: try { currentGlucose.toFloat() } catch (e: Exception) { 0f }
                            val rawVal = latest?.rawValue ?: autoVal
                            onTriggerCalibration(CalibrationSheetState.New(autoVal, rawVal, System.currentTimeMillis()))
                        },
                        onEdit = { entity ->
                            onTriggerCalibration(CalibrationSheetState.Edit(entity))
                        }
                    ) 
                }
            }
        }
    }
    
    // --- CALIBRATION BOTTOM SHEET (Global) ---
    val sheetState = calibrationSheetState
    if (sheetState !is CalibrationSheetState.Hidden) {
        // Resolve initial values
        val (initAuto, initRaw, initTime) = when(sheetState) {
            is CalibrationSheetState.New -> Triple(sheetState.auto, sheetState.raw, sheetState.timestamp)
            is CalibrationSheetState.Edit -> Triple(sheetState.entity.sensorValue, sheetState.entity.sensorValueRaw, sheetState.entity.timestamp)
            else -> Triple(0f, 0f, 0L) // Should not happen
        }
    
        tk.glucodata.ui.calibration.CalibrationBottomSheet(
            onDismiss = { calibrationSheetState = CalibrationSheetState.Hidden },
            initialValueAuto = initAuto,
            initialValueRaw = initRaw,
            initialTimestamp = initTime,
            glucoseHistory = glucoseHistory.map { tk.glucodata.GlucosePoint(it.timestamp, it.value, it.rawValue) },
            isMmol = unit.contains("mmol", ignoreCase = true),
            viewMode = viewMode,
            onNavigateToHistory = {
                calibrationSheetState = CalibrationSheetState.Hidden
                navController.navigate("calibrations")
            }
        )
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    calibrations: List<tk.glucodata.data.calibration.CalibrationEntity> = emptyList(),
    onNavigateToCalibrations: () -> Unit = {},
    onTriggerCalibration: (CalibrationSheetState) -> Unit = {}
) {
    val context = LocalContext.current

    // This runs every time the Activity/Fragment/Screen hits the ON_RESUME state
    // PERFORMANCE FIX: Refreshes stale data after Home button to prevent chart issues
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    val currentGlucose by viewModel.currentGlucose.collectAsState()
    val currentRate by viewModel.currentRate.collectAsState()
    val sensorName by viewModel.sensorName.collectAsState()
    val daysRemaining by viewModel.daysRemaining.collectAsState()
    val glucoseHistory by viewModel.glucoseHistory.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val targetLow by viewModel.targetLow.collectAsState()
    val targetHigh by viewModel.targetHigh.collectAsState()
    val sensorStatus by viewModel.sensorStatus.collectAsState()
    val sensorProgress by viewModel.sensorProgress.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val activeSensorList by viewModel.activeSensorList.collectAsState()
    val sensorHoursRemaining by viewModel.sensorHoursRemaining.collectAsState()
    val currentDay by viewModel.currentDay.collectAsState()

    // Initialize Calibration Manager
    LaunchedEffect(Unit) {
        tk.glucodata.data.calibration.CalibrationManager.init(context)
        tk.glucodata.data.calibration.CalibrationManager.loadCalibrations()
    }

    // State for wizards (matching SensorScreen pattern)
    var showSibionicsWizard by remember { mutableStateOf(false) }
    var showLibreWizard by remember { mutableStateOf(false) }
    var showDexcomWizard by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Import launcher for CSV files
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = tk.glucodata.data.HistoryExporter.importFromCsv(context, uri)
                if (result.success) {
                    android.widget.Toast.makeText(context, "Imported ${result.successCount} readings", android.widget.Toast.LENGTH_LONG).show()
                    viewModel.refreshData()
                } else {
                    android.widget.Toast.makeText(context, "Import failed: ${result.errorMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Sibionics Setup Wizard (Full Screen)
    if (showSibionicsWizard) {
        tk.glucodata.ui.setup.SibionicsSetupWizard(
            onDismiss = { showSibionicsWizard = false },
            onComplete = {
                showSibionicsWizard = false
                viewModel.refreshData()
            }
        )
        return // Exit early to show wizard full screen
    }
    
    // Libre Setup Wizard
    if (showLibreWizard) {
        tk.glucodata.ui.setup.LibreSetupWizard(
            onDismiss = { showLibreWizard = false },
            onScanNfc = {
                tk.glucodata.MainActivity.launchQrScan()
                showLibreWizard = false
            }
        )
        return
    }
    
    // Dexcom Setup Wizard
    if (showDexcomWizard) {
        tk.glucodata.ui.setup.DexcomSetupWizard(
            onDismiss = { showDexcomWizard = false },
            onScan = {
                tk.glucodata.MainActivity.launchQrScan()
                showDexcomWizard = false
            }
        )
        return
    }
    
    // Snackbar state for undo actions
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
        // FAB removed - empty state now has inline cards
    ) { padding ->
        val latestPoint = remember(glucoseHistory) { glucoseHistory.maxByOrNull { it.timestamp } }
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // --- REUSABLE UI SECTIONS ---


        val recentReadings = remember(glucoseHistory) {
            glucoseHistory.takeLast(10).reversed().distinctBy { it.timestamp }
        }

        // Compute calibrated value for current reading (respects viewMode)
        val isRawModeHero = viewMode == 1 || viewMode == 3
        val calibratedValue = remember(latestPoint, viewMode) {
            if (latestPoint != null && tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(isRawModeHero)) {
                val baseValue = if (isRawModeHero) latestPoint.rawValue else latestPoint.value
                tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(baseValue, latestPoint.timestamp, isRawModeHero)
            } else null
        }

        // Dashboard State
        var timeRange by rememberSaveable { mutableStateOf(TimeRange.H3) } // Default to 3 Hours

        // --- LAYOUT LOGIC ---
        
        // Empty state check
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else if (glucoseHistory.isEmpty()) {
            tk.glucodata.ui.components.DashboardEmptyState(
                onSensorSelected = { type ->
                    when (type) {
                        tk.glucodata.ui.components.SensorType.SIBIONICS -> showSibionicsWizard = true
                        tk.glucodata.ui.components.SensorType.LIBRE -> showLibreWizard = true
                        tk.glucodata.ui.components.SensorType.DEXCOM -> showDexcomWizard = true
                    }
                },
                onImportHistory = {
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                },
                modifier = Modifier.padding(padding)
            )
        } else if (isLandscape) {
            // LANDSCAPE: SPLIT VIEW
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp), // Check inset handling
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Pane: Status + Info + History (Scrollable)
                LazyColumn(
                    modifier = Modifier.weight(0.25f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp), // Gap between Header and History?
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                     item {
                        DashboardCombinedHeader(
                            currentGlucose = currentGlucose,
                            currentRate = currentRate,
                            viewMode = viewMode,
                            latestPoint = latestPoint,
                            sensorName = sensorName,
                            daysRemaining = daysRemaining,
                            activeSensors = activeSensorList,
                            sensorStatus = sensorStatus,
                            sensorProgress = sensorProgress,
                            sensorHoursRemaining = sensorHoursRemaining,
                            currentDay = currentDay,
                            history = glucoseHistory, // Advanced Trend
                            calibratedValue = calibratedValue,
                            onHeroClick = {
                                val autoVal = latestPoint?.value ?: try { currentGlucose.toFloat() } catch (e: Exception) { 0f }
                                val rawVal = latestPoint?.rawValue ?: autoVal
                                onTriggerCalibration(CalibrationSheetState.New(autoVal, rawVal, System.currentTimeMillis()))
                            }
                        )
                    }
                    
                    item {
                        RecentReadingsCard(
                            recentReadings = recentReadings,
                            unit = unit,
                            viewMode = viewMode
                        ) { index, item ->
                            ReadingRow(
                                point = item,
                                unit = unit,
                                viewMode = viewMode,
                                    index = index,
                                    totalCount = recentReadings.size,
                                    history = recentReadings,
                                    calibrations = calibrations,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }

                // Right Pane: Big Chart (Full Height)
                Column(
                    modifier = Modifier
                        .weight(0.75f)
                        .fillMaxHeight()
                        .padding(vertical = 16.dp)
                ) {
                    DashboardChartSection(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        glucoseHistory = glucoseHistory,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        unit = unit,
                        calibrations = calibrations,
                        viewMode = viewMode,
                        onTimeRangeSelected = { timeRange = it },
                        selectedTimeRange = timeRange,
                        onPointClick = { point -> 
                            onTriggerCalibration(CalibrationSheetState.New(point.value, point.rawValue, point.timestamp))
                        },
                        onCalibrationClick = { cal ->
                            onTriggerCalibration(CalibrationSheetState.Edit(cal))
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                }
            }
        } else {
            // PORTRAIT: UNIFIED VERTICAL SCROLL
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), // Gap between Header, Chart, History
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                 item {
                    DashboardCombinedHeader(
                        currentGlucose = currentGlucose,
                        currentRate = currentRate,
                        viewMode = viewMode,
                        latestPoint = latestPoint,
                        sensorName = sensorName,
                        daysRemaining = daysRemaining,
                        activeSensors = activeSensorList,
                        sensorStatus = sensorStatus,
                        sensorProgress = sensorProgress,
                        sensorHoursRemaining = sensorHoursRemaining,
                        currentDay = currentDay,
                        history = glucoseHistory, // Advanced Trend
                        calibratedValue = calibratedValue,
                        onHeroClick = {
                            val autoVal = latestPoint?.value ?: try { currentGlucose.toFloat() } catch (e: Exception) { 0f }
                            val rawVal = latestPoint?.rawValue ?: autoVal
                            onTriggerCalibration(CalibrationSheetState.New(autoVal, rawVal, System.currentTimeMillis()))
                        }
                    )
                }

                item {
                    // Portrait Chart: Flexible height
                    DashboardChartSection(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp),
                        glucoseHistory = glucoseHistory,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        unit = unit,
                        calibrations = calibrations,
                        viewMode = viewMode,
                        onTimeRangeSelected = { timeRange = it },
                        selectedTimeRange = timeRange,
                        onPointClick = { point -> 
                            onTriggerCalibration(CalibrationSheetState.New(point.value, point.rawValue, point.timestamp))
                        },
                        onCalibrationClick = { cal ->
                            onTriggerCalibration(CalibrationSheetState.Edit(cal))
                        }
                    )
                }


                item {
                    RecentReadingsCard(
                        recentReadings = recentReadings,
                        unit = unit,
                        viewMode = viewMode
                    ) { index, item ->
                        ReadingRow(
                            point = item,
                            unit = unit,
                            viewMode = viewMode,
                            index = index,
                            totalCount = recentReadings.size,
                            history = recentReadings,
                            calibrations = calibrations,
                            modifier = Modifier
                                .animateItem()
                                .clickable { 
                                    onTriggerCalibration(CalibrationSheetState.New(item.value, item.rawValue, item.timestamp)) 
                                }
                        )
                    }
                }

                // Calibrations Card
                item {
                    CalibrationsCard(
                        viewMode = viewMode,
                        isMmol = unit.contains("mmol", ignoreCase = true),
                        onAddCalibration = {
                            val autoVal = latestPoint?.value ?: try { currentGlucose.toFloat() } catch (e: Exception) { 0f }
                            val rawVal = latestPoint?.rawValue ?: autoVal
                            onTriggerCalibration(CalibrationSheetState.New(autoVal, rawVal, System.currentTimeMillis()))
                        },
                        onEditCalibration = { cal ->
                            onTriggerCalibration(CalibrationSheetState.Edit(cal))
                        },
                        onViewHistory = onNavigateToCalibrations,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
    
}

// --- EXTRACTED COMPONENTS (Performance Optimization) ---



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
    calibrations: List<tk.glucodata.data.calibration.CalibrationEntity> = emptyList(),
    onPointClick: ((GlucosePoint) -> Unit)? = null,
    onCalibrationClick: ((tk.glucodata.data.calibration.CalibrationEntity) -> Unit)? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
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
                        selectedTimeRange = selectedTimeRange,
                        onPointClick = onPointClick,
                        onCalibrationClick = onCalibrationClick
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_data_available)) }
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
    viewMode: Int = 0,
    calibrations: List<tk.glucodata.data.calibration.CalibrationEntity> = emptyList(),
    onDateSelected: (Long) -> Unit = {},
    selectedTimeRange: TimeRange? = null,
    onPointClick: ((GlucosePoint) -> Unit)? = null,
    onCalibrationClick: ((tk.glucodata.data.calibration.CalibrationEntity) -> Unit)? = null
) {
    // --- THEME & PAINTS ---
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val tertiaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) // Lighter shade for 3rd line
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

    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    
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
    val formatTime = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val formatDate = remember { java.text.SimpleDateFormat("EEE dd", java.util.Locale.getDefault()) }

    // Reusable objects to avoid allocation on every frame
    val reusablePath = remember { Path() }
    val reusableDate = remember { java.util.Date() }

    // Hoist intervals array to avoid allocation in Canvas loop
    val gridIntervals = remember {
        longArrayOf(
            15 * 60 * 1000L,     // 15m
            30 * 60 * 1000L,     // 30m
            60 * 60 * 1000L,     // 1h
            2 * 60 * 60 * 1000L, // 2h
            4 * 60 * 60 * 1000L, // 4h
            6 * 60 * 60 * 1000L, // 6h
            12 * 60 * 60 * 1000L,// 12h
            24 * 60 * 60 * 1000L // 24h
        )
    }

    // Hoisted PathEffect for dashed lines (Zero-Allocation)
    val dashEffect = remember { androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }

    // Label Cache to avoid SimpleDateFormat overhead during scroll
    // Maps Timestamp -> Formatted String
    val labelCache = remember { mutableMapOf<Long, String>() }

    // --- VIEWPORT STATE ---
    // ... (rest of viewport state)


    // --- VIEWPORT STATE ---
    val now = System.currentTimeMillis()
    val latestDataTimestamp = safeData.lastOrNull()?.timestamp ?: 0L
    val earliestDataTimestamp = safeData.firstOrNull()?.timestamp ?: 0L

    var lastAutoScrolledTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    // Jitter fix: Track the auto-scroll job to cancel it on user interaction
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var visibleDuration by rememberSaveable { mutableLongStateOf(3L * 60 * 60 * 1000) }
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

    // Auto-scroll logic: Only jump if we are already "near" the end (monitor mode)
    // or if this is the first load.
    // Auto-scroll logic: Only jump if we are explicitly RESUMED (Active)

    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    // Flag to detect immediate Resume/Startup so we can FORCE snap to latest
    // (ignoring the 1h check initially) as per User Request.
    var justResumed by remember { mutableStateOf(true) }

    // TRACKING INACTIVITY FOR GRAPH RESET
    // Fix: If app is backgrounded for a long time (e.g. overnight), the saved graph state
    // (centerTime, visibleDuration) becomes stale and "borked".
    // We implement a 10-minute timeout: if resumed after >10 mins, reset graph state.
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
                    // Only reset if we have valid data to snap to, otherwise wait for data load
                    if (latestDataTimestamp > 0) {
                        visibleDuration = 3L * 60 * 60 * 1000 // Default 3h
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
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // React to Time Range Selection (User Request)
    // If external time range changes, we snap visibleDuration to it and center on latest.
    LaunchedEffect(selectedTimeRange) {
        selectedTimeRange?.let { range ->
            val newDuration = range.hours * 60 * 60 * 1000L
            if (visibleDuration != newDuration) {
                visibleDuration = newDuration
                // Snap to latest data when changing range for immediate feedback
                if (latestDataTimestamp > 0) {
                     centerTime = latestDataTimestamp - visibleDuration / 2
                }
            }
        }
    }

    LaunchedEffect(latestDataTimestamp, isResumed) {
        if (isResumed && latestDataTimestamp > lastAutoScrolledTimestamp) {
            val currentEnd = centerTime + visibleDuration / 2

            // Robust Logic:
            // 1. If we JUST Resumed (or started), we force snap (User: "exited via Home... regardless of 1h").
            // 2. If we are Active/Monitoring (last update was recent), we snap.
            // 3. If we are Active but viewing History (dist > 1h), we STAY PUT.

            val dist = kotlin.math.abs(lastAutoScrolledTimestamp - currentEnd)
            val isMonitoring = lastAutoScrolledTimestamp == 0L || dist < 60 * 60 * 1000

            if (justResumed || isMonitoring) {
                centerTime = latestDataTimestamp - visibleDuration / 2
            }
            lastAutoScrolledTimestamp = latestDataTimestamp

            // Clear flag after processing the "Resume" frame
            justResumed = false
        }
    }

    // --- Y-AXIS STATE (Manual Scaling) ---
    // Adaptive Scaling Logic (User Request)
    // Defaults: mmol/L: 1.5 - 14.0 | mg/dL: 27 - 250
    // "Robust" Expansion: Only expand if > 3 points exceed the range (ignores single spikes).
    // DYNAMIC SCALING: Y-axis adapts to visible data with smooth animations.

    val isMmol = targetHigh <= 12
    val defaultMin = if (isMmol) 1.5f else 27f
    val defaultMax = if (isMmol) 14f else 250f

    // --- Y-AXIS SCALING ---
    // Simple, stable approach: rememberSaveable so it survives recomposition
    // User can manually adjust via gestures, values persist
    var yMin by rememberSaveable { mutableFloatStateOf(defaultMin) }
    var yMax by rememberSaveable { mutableFloatStateOf(defaultMax) }

    // --- INTERACTION STATE ---
    var selectedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    var isScrubbing by remember { mutableStateOf(false) } // Touching the line?

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

    // --- DATA CAPTURE FOR GESTURES ---
    // Use rememberUpdatedState to ensure the running gesture coroutine always sees the latest data
    val currentSafeData by rememberUpdatedState(safeData)
    val currentViewMode by rememberUpdatedState(viewMode)
    val curYMin by rememberUpdatedState(yMin)
    val curYMax by rememberUpdatedState(yMax)
    val currentCenterTime by rememberUpdatedState(centerTime)
    val currentVisibleDuration by rememberUpdatedState(visibleDuration)

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
        // If exact match not found, we use 'low' as insertion point (-idx - 1 logic)
        // binarySearch returns: index of the search key, if it is contained in the list; otherwise, (-(insertion point) - 1).
        // Since we did manual, if not found, 'idx' is -1. The standard equivalent would be... we just need nearest.

        if (idx >= 0) return data[idx]

        // Find closest neighbor
        // 'low' should be the insertion point
        val insPoint = low

        if (insPoint >= data.size) return data.lastOrNull()
        if (insPoint <= 0) return data.firstOrNull()

        val p1 = data[insPoint - 1]
        val p2 = data[insPoint]
        return if (kotlin.math.abs(p1.timestamp - snappedTime) < kotlin.math.abs(p2.timestamp - snappedTime)) p1 else p2
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
                        // Cancel any active auto-scroll (e.g. "Back to Now") immediately on touch
                        // This prevents the animation from fighting with manual scroll (jitter fix)
                        val down = awaitFirstDown(requireUnconsumed = true)
                        autoScrollJob?.cancel()
                        autoScrollJob = null

                        // STRICT DOUBLE TAP DETECTION
                        // Only trigger if:
                        // 1. Previous gesture was a tap (not a scroll)
                        // 2. Short duration since then (<300ms)
                        // 3. Close spatial proximity (<100px)
                        val now = System.currentTimeMillis()
                        val isDoubleTapStart = lastGestureWasTap &&
                                (now - lastTapTime < 300) &&
                                (down.position - lastTapPos).getDistance() < 100.dp.toPx()

                        var isOneFingerZoom = isDoubleTapStart

                        // Kill inertia
                        coroutineScope.launch { inertiaAnim.snapTo(0f) }
                        velocityTracker.resetTracking()

                        // --- HIT TEST (Hit Logic reused) ---
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val chartHeight = height - 30.dp.toPx()
                        val viewportStart = currentCenterTime - currentVisibleDuration / 2
                        val timeAtTouch = viewportStart + ((down.position.x / width).toDouble() * currentVisibleDuration)
                        val pointAtTouch = getPointAt(timeAtTouch)
                        var touchThreshold = 35.dp.toPx()

                        // Only allow scrubbing if purely single tap start (not double tap sequence)
                        isScrubbing = if (pointAtTouch != null && !isOneFingerZoom) {
                            val timeDiff = timeAtTouch - pointAtTouch.timestamp
                            if (timeDiff > 15 * 60 * 1000) false else {
                                // When calibration is on and is primary, use calibrated value for touch target
                                val isRawMode = currentViewMode == 1 || currentViewMode == 3
                                val hasCalibration = tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(isRawMode)
                                val v = if (hasCalibration) {
                                    val baseV = if (isRawMode) pointAtTouch.rawValue else pointAtTouch.value
                                    tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(baseV, pointAtTouch.timestamp, isRawMode)
                                } else if (isRawMode) {
                                    pointAtTouch.rawValue
                                } else {
                                    pointAtTouch.value
                                }
                                val dataY = chartHeight - ((v - curYMin) / (curYMax - curYMin)) * chartHeight
                                abs(down.position.y - dataY) < touchThreshold
                            }
                        } else {
                            false
                        }

                        if (isScrubbing) {
                            selectedPoint = pointAtTouch
                        }

                        var change = down
                        var totalDragDistance = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val newChange = event.changes.firstOrNull() ?: break
                            if (newChange.changedToUp()) break

                            velocityTracker.addPointerInputChange(newChange)

                            val pointerCount = event.changes.size

                            if (isOneFingerZoom) {
                                // ONE FINGER ZOOM MODE (Double-Tap-Drag)
                                val panY = newChange.position.y - change.position.y

                                // Only apply zoom if there's meaningful vertical movement
                                if (abs(panY) > 2f) {
                                    // EXPONENTIAL ZOOM (Smoother feel)
                                    // panY > 0 (Down) -> Zoom IN (Duration shrinks)
                                    // Form: newDur = oldDur * exp(-panY * sensitivity)
                                    val zoomSensitivity = 3f / height // Adjust constant for speed
                                    val zoomFactor = kotlin.math.exp(-panY * zoomSensitivity)

                                    val newDuration = (visibleDuration * zoomFactor).toLong()
                                    visibleDuration = newDuration.coerceIn(minDuration, maxDuration)
                                    totalDragDistance += abs(panY) // Mark as dragged, not tap
                                }
                                newChange.consume()

                            } else if (pointerCount > 1) {
                                // 2-FINGER ZOOM
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    val effectiveZoom = 1f + (zoomChange - 1f) * 2.0f
                                    val newDuration = (visibleDuration / effectiveZoom).toLong()
                                    visibleDuration = newDuration.coerceIn(minDuration, maxDuration)
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                // 1-FINGER PAN / SCRUB
                                if (isScrubbing) {
                                    val currentFrac = (newChange.position.x / width).toDouble()
                                    val currentTime = viewportStart + (currentFrac * visibleDuration)
                                    selectedPoint = getPointAt(currentTime)
                                } else {
                                    val panX = newChange.position.x - change.position.x
                                    val panY = newChange.position.y - change.position.y
                                    val dragDist = kotlin.math.sqrt(panX * panX + panY * panY)
                                    totalDragDistance += dragDist

                                    if (abs(panX) > abs(panY)) {
                                        // Horizontal pan
                                        val timePerPixel = visibleDuration.toFloat() / width
                                        val timeDelta = -(panX * timePerPixel).toLong()
                                        centerTime = (centerTime + timeDelta).coerceAtMost(maxAllowedTime)
                                    } else if (totalDragDistance > 30f) {
                                        // Vertical scale
                                        val scaleFactor = panY * (curYMax - curYMin) / height * 2f
                                        if (change.position.y < height / 2) {
                                            yMax = (curYMax + scaleFactor).coerceAtLeast(curYMin + 10f)
                                        } else {
                                            yMin = (curYMin + scaleFactor).coerceAtLeast(0f)
                                        }
                                    }
                                }
                                newChange.consume()
                            }
                            change = newChange
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
                                        visibleDuration = (visibleDuration / 2f).toLong().coerceIn(minDuration, maxDuration)
                                    }
                                } else {
                                    // SINGLE TAP (Selection)
                                    val isFutureTap = pointAtTouch != null &&
                                            pointAtTouch.timestamp == currentSafeData.lastOrNull()?.timestamp &&
                                            timeAtTouch > pointAtTouch.timestamp

                                    if (isFutureTap) selectedPoint = pointAtTouch else selectedPoint = null
                                }
                            } else if (!isOneFingerZoom) {
                                // FLING - simple defaults
                                val velocity = velocityTracker.calculateVelocity()
                                val vx = velocity.x
                                if (abs(vx) > 1000f) { // High threshold - ignore small movements
                                    // Velocity-dependent boost: fast swipes get more acceleration
                                    val boost = if (abs(vx) > 3000f) 2f else if (abs(vx) > 2000f) 1.5f else 1f
                                    coroutineScope.launch {
                                        var lastVal = 0f
                                        inertiaAnim.snapTo(0f)
                                        inertiaAnim.animateDecay(
                                            initialVelocity = -vx * boost,
                                            animationSpec = exponentialDecay(frictionMultiplier = 2.0f) // Higher friction = stops faster
                                        ) {
                                            val delta = this.value - lastVal
                                            val tPerPix = visibleDuration.toFloat() / size.width.toFloat()
                                            centerTime = (centerTime + (delta * tPerPix).toLong()).coerceAtMost(maxAllowedTime)
                                            lastVal = this.value
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Smooth zoom animation
            val animatedVisibleDuration by animateFloatAsState(
                targetValue = visibleDuration.toFloat(),
                animationSpec = spring<Float>(stiffness = Spring.StiffnessMedium),
                label = "ChartZoomAnimation"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val bottomAxisHeight = 30.dp.toPx()
                val chartHeight = height - bottomAxisHeight

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

                // Maps Value to Y (Inverted: High value = Low Y)
                // Hoist state reads for performance loop
                val cYMin = yMin
                val cYRange = yMax - cYMin
                
                fun valToY(v: Float): Float {
                    if (cYRange < 0.001f) return chartHeight / 2 // Prevent div/0
                    return chartHeight - ((v - cYMin) / cYRange) * chartHeight
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
                        val textBounds = android.graphics.Rect()
                        axisTextPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
                        val centeredY = y + (textBounds.height() / 2f)
                        val labelPadding = 16f * density
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText, labelPadding, centeredY, axisTextPaint
                        )
                    }
                    yVal += yStep.toInt()
                }

                // --- 2. DRAW X-AXIS GRID ---
                // Intervals: 5m, 15m, 30m, 1h, 2h, 4h, 8h, 12h, 24h
                val intervals = listOf(
                    5 * 60000L, 15 * 60000L, 30 * 60000L,
                    60 * 60000L, 120 * 60000L, 240 * 60000L, 
                    480 * 60000L, 720 * 60000L, 1440 * 60000L
                )
                
                // Pick interval keeping labels ~120px apart
                val pxPerMs = width / animDur
                val minMs = 120f / pxPerMs
                val gridInterval = intervals.firstOrNull { it >= minMs } ?: intervals.last()

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
                                    dateLabel, x, height - 25f, 
                                    xTextPaint.apply { typeface = graphFontBold }
                                )
                                xTextPaint.typeface = graphFont
                            } else {
                                // Time Line
                                drawLine(gridColor, Offset(x, 0f), Offset(x, chartHeight), 1f)
                                val timeLabel = labelCache.getOrPut(tGrid) {
                                    reusableDate.time = tGrid
                                    formatTime.format(reusableDate)
                                }
                                drawContext.canvas.nativeCanvas.drawText(timeLabel, x, height - 28f, xTextPaint)
                            }
                        }
                        tGrid += gridInterval
                    }
                }


                // --- 3. DATA LINES (Optimized with Decimation) ---
                if (endIdx > startIdx) {
                    val gapThreshold = 900000L // 15 mins

                    // Helper to draw a line series (Inlined Logic)
                    fun drawOptimized(isRaw: Boolean, color: androidx.compose.ui.graphics.Color, strokeWidth: Float) {
                        reusablePath.rewind()
                        
                        var first = true
                        var lastX = -10000f // Far off screen
                        var lastTimestamp = 0L

                        for (i in startIdx until endIdx) {
                            val p = safeData[i]
                            val v = if (isRaw) p.rawValue else p.value
                            
                            // 1. Data Validity
                            if (v.isNaN() || v < 0.1f) {
                                first = true
                                continue
                            }

                            // 2. Coordinate Mapping
                            // Use inlined logic or local funcs (local funcs are fast enough if no alloc)
                            val px = timeToX(p.timestamp)
                            
                            // PERFORMANCE: Decimation
                            // If x is within 0.5px of last drawn point, SKIP drawing (unless gap)
                            // This reduces 50,000 points to ~2000 points (screen width)
                            if (!first && kotlin.math.abs(px - lastX) < 0.7f) {
                                // We skip this point. 
                                // Ideally we should min/max here for accuracy but for 1px it's negligible for smooth lines.
                                continue 
                            }

                            val py = valToY(v)

                            // 3. Coordinate Safety
                            if (!px.isFinite() || !py.isFinite()) {
                                first = true
                                continue
                            }

                            // 4. Gap Check
                            if (!first && (p.timestamp - lastTimestamp) > gapThreshold) {
                                first = true
                            }
                            
                            if (first) { 
                                reusablePath.moveTo(px, py) 
                                first = false 
                            } else { 
                                reusablePath.lineTo(px, py) 
                            }
                            
                            lastX = px
                            lastTimestamp = p.timestamp
                        }
                        
                        drawPath(reusablePath, color, style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }

                    // --- DRAW BASED ON VIEW MODE ---
                    // ViewMode logic simplified
                    val drawRaw = viewMode == 1 || viewMode == 2 || viewMode == 3
                    val drawAuto = viewMode == 0 || viewMode == 2 || viewMode == 3
                    val isRawModeChart = viewMode == 1 || viewMode == 3
                    val hasCalibration = tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(isRawModeChart)
                    
                    if (drawRaw) {
                         // Raw line: Demote based on mode and calibration
                         // In mode 2 (Auto+Raw) with calibration: Raw is tertiary (3rd)
                         val color = when {
                             hasCalibration && viewMode == 2 -> tertiaryColor  // 3rd line (raw is least important)
                             hasCalibration || viewMode == 2 -> secondaryColor
                             else -> primaryColor
                         }
                         val width = if (hasCalibration || viewMode == 2) 2.dp.toPx() else 3.dp.toPx()
                         drawOptimized(true, color, width)
                    }
                    
                    if (drawAuto) {
                        // Auto line: Demote based on mode and calibration 
                        // In mode 3 (Raw+Auto) with calibration: Auto is tertiary (3rd)
                        val color = when {
                            hasCalibration && viewMode == 3 -> tertiaryColor  // 3rd line (auto is least important)
                            hasCalibration || viewMode == 3 -> secondaryColor
                            else -> primaryColor
                        }
                        val width = if (hasCalibration || viewMode == 3) 2.dp.toPx() else 3.dp.toPx()
                        drawOptimized(false, color, width)
                    }
                    
                    // Draw calibrated line ON TOP (primary when active)
                    if (hasCalibration) {
                        reusablePath.rewind()
                        var first = true
                        var lastX = -10000f
                        var lastTimestamp = 0L
                        
                        for (i in startIdx until endIdx) {
                            val p = safeData[i]
                            val baseValue = if (viewMode == 1 || viewMode == 3) p.rawValue else p.value
                            val calibratedV = tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(baseValue, p.timestamp, isRawModeChart)
                            
                            if (calibratedV.isNaN() || calibratedV < 0.1f) {
                                first = true
                                continue
                            }
                            
                            val px = timeToX(p.timestamp)
                            if (!first && kotlin.math.abs(px - lastX) < 0.7f) continue
                            
                            val py = valToY(calibratedV)
                            if (!px.isFinite() || !py.isFinite()) {
                                first = true
                                continue
                            }
                            
                            if (!first && (p.timestamp - lastTimestamp) > gapThreshold) {
                                first = true
                            }
                            
                            if (first) { 
                                reusablePath.moveTo(px, py)
                                first = false
                            } else {
                                reusablePath.lineTo(px, py)
                            }
                            
                            lastX = px
                            lastTimestamp = p.timestamp
                        }
                        
                        drawPath(reusablePath, primaryColor, style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
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
                        val v = if (useRaw) p.rawValue else p.value

                        if (v.isNaN() || v < 0.1f) continue

                        if (v < minVal) { minVal = v; minPoint = p }
                        if (v > maxVal) { maxVal = v; maxPoint = p }
                    }

                    // Helper to draw
                    fun drawIndicator(point: GlucosePoint, valToDraw: Float) {
                        val y = valToY(valToDraw)
                        val x = timeToX(point.timestamp)

                        if (y.isFinite() && x.isFinite() && y in 0f..chartHeight && x in 0f..width) {
                            val label = String.format("%.1f", valToDraw)
                            
                            // Text - Vertically centered with guide line, 4dp left padding
                            val indicatorBounds = android.graphics.Rect()
                            axisTextPaint.getTextBounds(label, 0, label.length, indicatorBounds)
                            val centeredY = y + (indicatorBounds.height() / 2f)
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
                    val calX = timeToX(cal.timestamp)
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
                val cursorX = selectedPoint?.let { timeToX(it.timestamp) }
                if (cursorX != null && cursorX in 0f..width) {
                    drawLine(hoverLineColor, Offset(cursorX, 0f), Offset(cursorX, chartHeight), 2.dp.toPx())

                    selectedPoint?.let { p ->
                        val dotRadius = 5.dp.toPx()
                        val isRawModeDot = viewMode == 1 || viewMode == 3
                        val hasCalibrationDot = tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(isRawModeDot)
                        
                        // Draw dots for active lines (demoted when calibration active)
                         if (viewMode == 1 || viewMode == 2 || viewMode == 3) {
                             val color = if (hasCalibrationDot) secondaryColor else if (viewMode == 1 || viewMode == 3) primaryColor else secondaryColor
                             val py = valToY(p.rawValue)
                             if (py.isFinite()) drawCircle(color, dotRadius, Offset(cursorX, py))
                         }
                         if (viewMode == 0 || viewMode == 2 || viewMode == 3) {
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
            selectedPoint?.let { point ->
                // Calculate X position to follow cursor
                val viewportStart = centerTime - visibleDuration / 2
                val xFraction = (point.timestamp - viewportStart).toFloat() / visibleDuration.toFloat()
                // Clamp horizontal position to keep card on screen (assuming approx card width ~120dp)
                // We use specific offsets in standard DP
                val cardXOffset = (constraints.maxWidth * xFraction).coerceIn(0f, constraints.maxWidth.toFloat())

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
                    val baseValue = if (isRawModeTT) point.rawValue else point.value
                    tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(baseValue, point.timestamp, isRawModeTT)
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
                                y = 48.dp.roundToPx()
                            )
                        }
                        // 2. Center
                        .graphicsLayer { translationX = -size.width / 2f }
                        .widthIn(min = 48.dp)
                        .clip(cardShape) // Clip ripple to match rounded corners
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
                                y = (2).dp.roundToPx() // Center text on same line as X-axis labels
                            )
                        }
                        .graphicsLayer { translationX = -size.width / 2f }
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
                val calXOffset = (constraints.maxWidth * calXFraction).coerceIn(0f, constraints.maxWidth.toFloat())
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
                                y = 2.dp.roundToPx()
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

            androidx.compose.animation.AnimatedVisibility(
                visible = !isToday && !isRecent,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                val headerDate = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date(centerTime))

                // Same style as bottom Time Chip
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
                        contentDescription = "Jump to Now"
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
//                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = TimeRange.values()
            val configuration = LocalConfiguration.current
            // Graceful collapse: on narrow screens (<380dp), collapse "Back to Now" width
            val isCompact = configuration.screenWidthDp < 380

            // Date Picker Button (Left side, always visible)
            FilledTonalIconButton(
                onClick = { showDatePicker = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),

                modifier = Modifier.size(width = if (isCompact) 32.dp else 40.dp, height = 32.dp) // Collapse to square on small screens
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.DateRange,
                    contentDescription = "Jump to Date",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(width = if (isCompact) 4.dp else 8.dp))

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
                        // Cancel any active scroll when interacting with tabs
                        autoScrollJob?.cancel()

                        if (isSel) {
                            // "Back to Now" logic with Smart Scroll animation
                            autoScrollJob = coroutineScope.launch {
                                val targetTime = now - visibleDuration / 2
                                val diff = targetTime - centerTime

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
                        } else {
                            visibleDuration = rangeDur
                            val maxCenter = now - visibleDuration / 2
                            if (centerTime > maxCenter) {
                                centerTime = maxCenter
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    modifier = Modifier
                        .height(32.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                    ) {
                        if (isSel) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = iconRotation }
                            )
                        }

                        Text(
                            text = range.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.width(width = if (isCompact) 4.dp else 8.dp))


            // "Back to Now" Button (Expressive: End of Row)
            val now = System.currentTimeMillis()
            val targetTime = now - visibleDuration / 2
            val isAtNow = abs(centerTime - targetTime) < 60 * 60 * 1000 // 1 hour threshold (Old behavior)

            AnimatedVisibility(
                visible = !isAtNow,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start) + scaleIn(),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start) + scaleOut()
            ) {

                Surface(
                    onClick = {
                        // Cancel previous if any
                        autoScrollJob?.cancel()

                        autoScrollJob = coroutineScope.launch {
                            val maxScroll = 12 * 60 * 60 * 1000L
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
                    },
                    shape = RoundedCornerShape(28.dp),
                    // Subtle surface color for visibility
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
//                            .padding(start = 4.dp)
                        .size(width = if (isCompact) 32.dp else 40.dp, height = 32.dp) // Collapse to square on small screens
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.LastPage,
                            contentDescription = "Back to Now",
                            modifier = Modifier.size(24.dp)
                        )
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
                            autoScrollJob?.cancel()
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




fun buildGlucoseString(
    dvs: DisplayValues,
    primaryColor: Color,
    secondaryColor: Color,
    unitColor: Color,
    includeUnit: Boolean = false,
    unit: String = "",
    tertiaryColor: Color? = null
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        withStyle(androidx.compose.ui.text.SpanStyle(color = primaryColor)) {
            append(dvs.primaryStr)

            // If single value, append unit here if requested
            if (includeUnit && dvs.secondaryStr == null) {
                append(" ")
                withStyle(androidx.compose.ui.text.SpanStyle(color = unitColor)) {
                    append(unit)
                }
            }
        }
        if (dvs.secondaryStr != null) {
            append(" · ")
            withStyle(androidx.compose.ui.text.SpanStyle(color = secondaryColor)) {
                append(dvs.secondaryStr)
            }
        }
        // Tertiary value (when 3 values exist)
        if (dvs.tertiaryStr != null) {
            append(" · ")
            withStyle(androidx.compose.ui.text.SpanStyle(color = tertiaryColor ?: secondaryColor.copy(alpha = 0.5f))) {
                append(dvs.tertiaryStr)
            }
        }
    }
}

@Composable
fun ReadingRow(
    point: GlucosePoint,
    unit: String,
    viewMode: Int = 0,
    index: Int = 0,
    totalCount: Int = 1,
    history: List<GlucosePoint> = emptyList(), // Advanced Trend: Need history
    calibrations: List<tk.glucodata.data.calibration.CalibrationEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    // --- DYNAMIC COLOR LOGIC ---
    // User Request: 
    // 1. Fresh (0-60s): Active Color -> Stock Color.
    // 2. Normal (60-90s): Stock Color.
    // 3. Stale (>90s): Stock Color -> Darker Shade.
    
    val isActive = index == 0
    val isHistoryStart = index == 1
    val isHistoryEnd = index == totalCount - 1
    
    val activeColor = MaterialTheme.colorScheme.secondaryContainer
    val stockColor = MaterialTheme.colorScheme.surfaceContainerLow
    val staleColor = MaterialTheme.colorScheme.surfaceContainerHighest // Slightly darker/different

    val ageState = remember(point.timestamp) { mutableStateOf(System.currentTimeMillis() - point.timestamp) }
    
    // Timer to update age for the active item
    if (isActive) {
        LaunchedEffect(point.timestamp) {
            while (true) {
                ageState.value = System.currentTimeMillis() - point.timestamp
                delay(1000) // Update every second
            }
        }
    }

    val containerColor = if (isActive) {
        val age = ageState.value
        when {
            age < 60_000 -> {
                // Phase 1: Fade to Stock
                val progress = age / 60_000f
                androidx.compose.ui.graphics.Color(
                    androidx.core.graphics.ColorUtils.blendARGB(
                        activeColor.toArgb(),
                        stockColor.toArgb(),
                        progress.coerceIn(0f, 1f)
                    )
                )
            }
            age < 90_000 -> {
                // Phase 2: Stock (Stable)
                stockColor
            }
            else -> {
                 // Phase 3: Stale (Darken slowly)
                 // "slowly start getting different darker shade"
                 // Let's cap the darkening at 100% after another 60s (just to have a bound)
                 val staleProgress = ((age - 90_000) / 60_000f).coerceIn(0f, 1f)
                 androidx.compose.ui.graphics.Color(
                    androidx.core.graphics.ColorUtils.blendARGB(
                        stockColor.toArgb(),
                        staleColor.toArgb(),
                        staleProgress
                    )
                )
            }
        }
    } else {
        // History: Always Stock
        stockColor
    }

    // Shape: 
    // Active (Hero): "4dp bottom radius" to imply continuity. Top matches parent (16dp).
    // History: Rectangle (relies on parent clipping for its 4dp bottom).
    val shape = if (isActive) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    } else {
        RectangleShape
    }

    // Divider Alpha Logic
    val baseDividerAlpha = 0.1f // User Request: "perhaps make it 0.1f"
    val dividerAlpha = if (isActive) {
        val age = ageState.value
        when {
            age < 60_000 -> {
                // Fresh: Color is Distinct -> Stock. Divider: Hidden -> Visible.
                val progress = age / 60_000f
                baseDividerAlpha * progress.coerceIn(0f, 1f)
            }
            age < 90_000 -> {
                // Normal: Color is Stock. Divider: Visible.
                baseDividerAlpha
            }
            else -> {
                // Stale: Color is Stock -> Distinct. Divider: Visible -> Hidden.
                val staleProgress = ((age - 90_000) / 60_000f).coerceIn(0f, 1f)
                baseDividerAlpha * (1f - staleProgress)
            }
        }
    } else {
        baseDividerAlpha
    }
    
    // --- ADVANCED TREND ENGINE ---
    // Calculate on the fly using the passed history subset
    val trendResult = remember(history, index) {
        val relevantHistory = if (history.isNotEmpty()) history.drop(index) else listOf(point)
        val nativeList = relevantHistory.map { tk.glucodata.GlucosePoint(it.timestamp, it.value, it.rawValue) }
        tk.glucodata.logic.TrendEngine.calculateTrend(nativeList, useRaw = (viewMode == 1 || viewMode == 3))
    }
    
    // Wrap in Column to place Divider below the Surface
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            // User Request: Kill shadows (0dp)
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp), // Restored "stock-ish" padding (was 8)
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time (Left)
                // Active: Bold/Medium. History: Small.
                // User Request: "first one same size as others" -> All Body Small
                val timeStyle = MaterialTheme.typography.bodySmall
                val timeColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                val timeWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                
                Text(
                    text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(point.timestamp)),
                    style = timeStyle,
                    fontWeight = timeWeight,
                    color = timeColor
                )

                // Value (Right)
                val isRawModeRR = viewMode == 1 || viewMode == 3
                val hasCalibrationRR = tk.glucodata.data.calibration.CalibrationManager.hasActiveCalibration(isRawModeRR)
                val calibratedValueRR = if (hasCalibrationRR) {
                    val baseValue = if (isRawModeRR) point.rawValue else point.value
                    tk.glucodata.data.calibration.CalibrationManager.getCalibratedValue(baseValue, point.timestamp, isRawModeRR)
                } else null
                val dvs = getDisplayValues(point, viewMode, unit, calibratedValueRR)
                // Colors
                val primaryColor = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha=0.8f)
                val secondaryColor = if (isActive)  MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.8f)
                val unitColor = secondaryColor.copy(alpha = 0.6f)
                val tertiaryColor = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)


                // Text Style: "first one same size as others" -> All Title Medium
                val valueStyle = MaterialTheme.typography.titleMedium
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Calibration indicator - shows on reading where calibration was added (respects mode)
                    if (tk.glucodata.data.calibration.CalibrationManager.hasCalibrationAt(point.timestamp, isRawModeRR)) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    Text(
                        text = buildGlucoseString(dvs, primaryColor, secondaryColor, unitColor, true, "", tertiaryColor),
                        style = valueStyle.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier
                    )
                    
                    // Advanced Trend Indicator
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    tk.glucodata.ui.components.TrendIndicator(
                        trendResult = trendResult,
                        color = tertiaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        // Render Divider
        if (index < totalCount - 1) {
             HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = dividerAlpha), 
                thickness = 1.dp
            )
        }
    }
}
/*
    // --- PREVIOUS IMPLEMENTATION (Commented as requested) ---
    val backgroundColor = lerp(
        start = MaterialTheme.colorScheme.surfaceVariant,
        stop = MaterialTheme.colorScheme.primaryContainer,
        fraction = highlightAlpha.value
    )

    // Determine if we need to show date (for old data, not from today)
    val timeDisplay = remember(point.timestamp) {
        val now = System.currentTimeMillis()
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val isFromToday = point.timestamp >= todayStart
        
        if (isFromToday) {
            // Fresh data: just time "HH:mm"
            point.time
        } else {
            // Old data: show date + time "EEE dd HH:mm"
            val dateFormat = java.text.SimpleDateFormat("EEE dd HH:mm", java.util.Locale.getDefault())
            dateFormat.format(java.util.Date(point.timestamp))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp) // Minimum touch target / visual rhythm
            .padding(horizontal = 16.dp, vertical = 16.dp), // Increased vertical padding to 16dp for "Editorial" feel
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically // Center content vertically
    ) {
        // Time Column (Secondary Hierarchy)
        Text(
            text = timeDisplay,
            style = MaterialTheme.typography.bodySmall, // Receded (12sp)
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        // Value Column (Primary Hierarchy)
        val dvs = getDisplayValues(point, viewMode, unit)
        val primaryColor = MaterialTheme.colorScheme.onSurface
        val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1.0f)
        val unitColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

        // Bold & Prominent
        Text(
            text = buildGlucoseString(dvs, primaryColor, secondaryColor, unitColor, true, unit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }
*/


@Composable
fun SearchScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text(stringResource(R.string.filter_by_date)) },
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

    val notificationChartEnabled by viewModel.notificationChartEnabled.collectAsState()

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

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars // Ensure status bar padding
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
        var showLanguageDialog by remember { mutableStateOf(false) }

        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Spacer(modifier = Modifier.padding(horizontal = 16.dp))

        // --- UNIT ---
        ListItem(
            headlineContent = { Text(stringResource(R.string.unit)) },
            supportingContent = { Text(unit) },
            modifier = Modifier.clickable { showUnitDialog = true }
        )
        if (showUnitDialog) {
            AlertDialog(
                onDismissRequest = { showUnitDialog = false },
                text = {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            "Select Unit",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        listOf("mg/dL" to 0, "mmol/L" to 1).forEach { (label, value) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setUnit(value)
                                        showUnitDialog = false
                                        context.findActivity()?.hardRestart()
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (if (isMmol) 1 else 0) == value, onClick = null)
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showUnitDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // --- THEME ---
        val themeLabel = when(themeMode) {
            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
            ThemeMode.DARK -> stringResource(R.string.theme_dark)
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.theme_title)) },
            supportingContent = { Text(themeLabel) },
            modifier = Modifier.clickable { showThemeDialog = true }
        )
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                text = {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            "Choose theme",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        ThemeMode.values().forEach { mode ->
                            val label = when(mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onThemeChanged(mode)
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = themeMode == mode, onClick = null)
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // --- LANGUAGE ---
        // Supported Languages: be, de, fr, it, nl, pl, pt, ru, sv, tr, uk, zh, en
        val currentLocale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
        val currentLangName = currentLocale.displayLanguage.capitalize()

        ListItem(
            headlineContent = { Text(stringResource(R.string.languagename)) },
            supportingContent = { Text(currentLangName) },
            modifier = Modifier.clickable { showLanguageDialog = true }
        )

        if (showLanguageDialog) {
            val languages = listOf(
                stringResource(R.string.system_default) to null,
                "English" to "en",
                "Belarusian" to "be",
                "Chinese" to "zh",
                "German" to "de",
                "French" to "fr",
                "Italian" to "it",
                "Dutch" to "nl",
                "Polish" to "pl",
                "Portuguese" to "pt",
                "Russian" to "ru",
                "Swedish" to "sv",
                "Turkish" to "tr",
                "Ukrainian" to "uk"
            )

            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.select_language)) },
                text = {
                    LazyColumn {
                        items(languages) { (name, tag) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val appLocale = if (tag != null) LocaleListCompat.forLanguageTags(tag) else LocaleListCompat.getEmptyLocaleList()
                                        AppCompatDelegate.setApplicationLocales(appLocale)
                                        showLanguageDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = name)
                                if ((tag == null && AppCompatDelegate.getApplicationLocales().isEmpty) ||
                                    (tag != null && AppCompatDelegate.getApplicationLocales().toLanguageTags().contains(tag))) {
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.Check, contentDescription = "Selected")
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }


        // --- NOTIFICATION CHART ---
        ListItem(
            headlineContent = { Text(stringResource(R.string.notification_chart_title)) },
            supportingContent = { Text(stringResource(R.string.notification_chart_desc)) },
            trailingContent = {
                StyledSwitch(
                    checked = notificationChartEnabled,
                    onCheckedChange = { viewModel.toggleNotificationChart(it) }
                )
            }
        )



        // M3 Grid: 24dp between major sections
        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.exchanges), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(16.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.xdripbroadcast)) },
            supportingContent = { Text(stringResource(R.string.patchedlibrebroadcast)) },
            trailingContent = {
                StyledSwitch(
                    checked = patchedLibreEnabled,
                    onCheckedChange = { viewModel.togglePatchedLibreBroadcast(it) }
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.mirror)) },
            supportingContent = { Text(stringResource(R.string.mirror_desc)) },
            modifier = Modifier.clickable { navController.navigate("settings/mirror") }
        )


        ListItem(
            headlineContent = { Text(stringResource(R.string.nightscout_config)) },
            supportingContent = { Text(stringResource(R.string.nightscout_desc)) },
            modifier = Modifier.clickable { navController.navigate("settings/nightscout") }
        )



        // M3 Grid: 24dp between major sections
        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.glucose_alerts_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(16.dp))

        AlarmCard(
            title = stringResource(R.string.lowglucosealarm), // Or R.string.lowglucose
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
            title = stringResource(R.string.highglucosealarm), // Or R.string.highglucose
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

        // M3 Grid: 24dp between major sections
        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.target_range_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        TargetCard(
            title = stringResource(R.string.low_label),
            value = targetLowValue,
            unit = unit,
            range = if (isMmol) 2.0f..8.0f else 40f..140f,
            onValueChange = { viewModel.setTargetLow(it) },
            modifier = Modifier.padding(horizontal = 16.dp)

        )
        Spacer(modifier = Modifier.height(16.dp))

        TargetCard(
            title = stringResource(R.string.high_label),
            value = targetHighValue,
            unit = unit,
            range = if (isMmol) 6.0f..16.0f else 100f..350f,
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

        // M3 Grid: 24dp between major sections
        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.advanced_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(16.dp))

        // Google Scan
        ListItem(
            headlineContent = { Text(stringResource(R.string.googlescan)) },
            supportingContent = { Text(stringResource(R.string.google_scan_desc)) },
            trailingContent = {
                var googleScan by remember { mutableStateOf(Natives.getGoogleScan()) }
                StyledSwitch(
                    checked = googleScan,
                    onCheckedChange = {
                        Natives.setGoogleScan(it)
                        googleScan = it
                    }
                )
            }
        )

        // Turbo (High Priority)
        ListItem(
            headlineContent = { Text(stringResource(R.string.turbo_title)) },
            supportingContent = { Text(stringResource(R.string.turbo_desc)) },
            trailingContent = {
                var turbo by remember { mutableStateOf(Natives.getpriority()) }
                StyledSwitch(
                    checked = turbo,
                    onCheckedChange = {
                        Natives.setpriority(it)
                        turbo = it
                    }
                )
            }
        )

        // Android (AutoConnect)
        ListItem(
            headlineContent = { Text(stringResource(R.string.autoconnect_title)) },
            supportingContent = { Text(stringResource(R.string.autoconnect_desc)) },
            trailingContent = {
                var autoConnect by remember { mutableStateOf(Natives.getAndroid13()) }
                StyledSwitch(
                    checked = autoConnect,
                    onCheckedChange = {
                        SensorBluetooth.setAutoconnect(it)
                        autoConnect = it
                    }
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.debug_logs_title)) },
            supportingContent = { Text(stringResource(R.string.debug_logs_desc)) },
            modifier = Modifier.clickable { navController.navigate("settings/debug") }
        )


        // M3 Grid: 24dp between major sections
        Spacer(modifier = Modifier.height(24.dp))

        //--- DATA MANAGEMENT SECTION ---
        Text(
            text = "Data Management",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        // M3 Grid: 16dp spacing after section header
        Spacer(modifier = Modifier.height(16.dp))

        val coroutineScope = rememberCoroutineScope()
        var showExportDialog by remember { mutableStateOf(false) }
        var exportType by remember { mutableStateOf("csv") }

        // EXPORT LAUNCHER
        val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("*/*")
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val format = exportType
                    val unitStr = if (isMmol) "mmol/L" else "mg/dL"
                    val data = tk.glucodata.data.HistoryRepository.getHistoryBlocking(0L, isMmol)

                    val success = if (format == "csv") {
                        tk.glucodata.data.HistoryExporter.exportToCsv(context, uri, data, unitStr)
                    } else {
                        tk.glucodata.data.HistoryExporter.exportToReadable(context, uri, data, unitStr)
                    }

                    Toast.makeText(context, if (success) "Export successful" else "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // IMPORT LAUNCHER
        val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val result = tk.glucodata.data.HistoryExporter.importFromCsv(context, uri)
                    if (result.success) {
                        Toast.makeText(context, "Imported ${result.successCount} readings", Toast.LENGTH_LONG).show()
                        viewModel.refreshData()
                    } else {
                        Toast.makeText(context, "Import failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Export Format Dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Select Export Format") },
                text = {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { exportType = "csv" }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = exportType == "csv", onClick = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(stringResource(R.string.csv_file))
                                Text(stringResource(R.string.csv_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { exportType = "readable" }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = exportType == "readable", onClick = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(stringResource(R.string.csv_file))
                                Text(stringResource(R.string.csv_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { exportType = "readable" }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = exportType == "readable", onClick = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(stringResource(R.string.text_file))
                                Text(stringResource(R.string.text_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExportDialog = false
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        val ext = if (exportType == "csv") "csv" else "txt"
                        exportLauncher.launch("juggluco_history_$date.$ext")
                    }) { Text(stringResource(R.string.export_button)) }
                },
                dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        // Export History - M3 ListItem pattern (fully clickable)
        ListItem(
            headlineContent = { Text(stringResource(R.string.export_history_title)) },
            supportingContent = { Text(stringResource(R.string.export_history_sub)) },
            modifier = Modifier.clickable { showExportDialog = true },
            leadingContent = {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )

        // Import History - M3 ListItem pattern (fully clickable)
        ListItem(
            headlineContent = { Text(stringResource(R.string.import_history_title)) },
            supportingContent = { Text(stringResource(R.string.import_history_sub)) },
            modifier = Modifier.clickable { importLauncher.launch(arrayOf("text/csv", "text/plain", "*/*")) },
            leadingContent = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        )

        // M3 Grid: 24dp before new subsection
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Dangerous Actions",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.error
        )
        // M3 Grid: 16dp after subsection header
        Spacer(modifier = Modifier.height(16.dp))
        var showClearHistoryDialog by remember { mutableStateOf(false) }
        var showClearDataDialog by remember { mutableStateOf(false) }
        var showFactoryResetDialog by remember { mutableStateOf(false) }
        var isClearing by remember { mutableStateOf(false) }

        // Clear History Card - Description + Action button pattern
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Remove all glucose readings from database",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showClearHistoryDialog = true },
                    enabled = !isClearing,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isClearing) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("Clear History")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Clear App Data Card - Description + Action button pattern
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Remove history and cache, keep settings",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showClearDataDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    enabled = !isClearing,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isClearing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError)
                    else Text("Clear Data")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Factory Reset Card - Description + Action button pattern
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Remove everything - app returns to first-run state",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showFactoryResetDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    enabled = !isClearing,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isClearing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError)
                    else Text("Factory Reset")
                }
            }
        }

        // Confirmation Dialogs
        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                title = { Text(stringResource(R.string.clear_history_title)) },
                text = { Text(stringResource(R.string.clear_history_confirmation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isClearing = true
                            coroutineScope.launch {
                                tk.glucodata.data.DataManagement.clearHistory()
                                isClearing = false
                                showClearHistoryDialog = false
                            }
                        }
                    ) { Text(stringResource(R.string.clear_button)) }
                },
                dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.clear_app_data_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.clear_app_data_intro))
                        Text(stringResource(R.string.all_glucose_readings))
                        Text(stringResource(R.string.cache_files))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.settings_preserved))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.cannot_be_undone_warning))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isClearing = true
                            coroutineScope.launch {
                                tk.glucodata.data.DataManagement.clearAppData()
                                isClearing = false
                                showClearDataDialog = false
                                (context as? Activity)?.finishAffinity()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.clear_data_button)) }
                },
                dismissButton = { TextButton(onClick = { showClearDataDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        if (showFactoryResetDialog) {
            AlertDialog(
                onDismissRequest = { showFactoryResetDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.factory_reset_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.factory_reset_intro))
                        Text(stringResource(R.string.all_glucose_readings))
                        Text(stringResource(R.string.all_settings))
                        Text(stringResource(R.string.cache_files))
                        Text(stringResource(R.string.sensor_data))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.factory_reset_outcome), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.cannot_be_undone_critical), color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isClearing = true
                            coroutineScope.launch {
                                tk.glucodata.data.DataManagement.factoryReset()
                                // Close app after factory reset
                                (context as? Activity)?.finishAffinity()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.reset_everything_button)) }
                },
                dismissButton = { TextButton(onClick = { showFactoryResetDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }

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
}
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
                StyledSwitch(checked = enabled, onCheckedChange = onToggle)
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
                Text(stringResource(R.string.alert_sound), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = soundMode == 0,
                        onClick = { onSoundChange(0) },
                        label = { Text(stringResource(R.string.vibrate_only)) }
                    )
                    FilterChip(
                        selected = soundMode == 1,
                        onClick = { onSoundChange(1) },
                        label = { Text(stringResource(R.string.sound_vibrate)) }
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
    
    // Start/stop real-time polling based on screen visibility
    DisposableEffect(Unit) {
        viewModel.startPolling() // Start 2-second refresh when screen visible
        onDispose {
            viewModel.stopPolling() // Stop when leaving screen
        }
    }
    
    // State for sensor type picker and wizards
    var showSensorPicker by remember { mutableStateOf(false) }
    var showSibionicsWizard by remember { mutableStateOf(false) }
    var showLibreWizard by remember { mutableStateOf(false) }
    var showDexcomWizard by remember { mutableStateOf(false) }
    
    // Sensor Type Picker Bottom Sheet
    if (showSensorPicker) {
        tk.glucodata.ui.components.SensorTypePicker(
            onDismiss = { showSensorPicker = false },
            onSensorSelected = { type ->
                when (type) {
                    tk.glucodata.ui.components.SensorType.SIBIONICS -> showSibionicsWizard = true
                    tk.glucodata.ui.components.SensorType.LIBRE -> showLibreWizard = true
                    tk.glucodata.ui.components.SensorType.DEXCOM -> showDexcomWizard = true
                }
            }
        )
    }
    
    // Sibionics Setup Wizard (Full Screen)
    if (showSibionicsWizard) {
        tk.glucodata.ui.setup.SibionicsSetupWizard(
            onDismiss = { showSibionicsWizard = false },
            onComplete = {
                showSibionicsWizard = false
                viewModel.refreshSensors()
            }
        )
        return // Exit early to show wizard full screen
    }
    
    // Libre Setup Wizard
    if (showLibreWizard) {
        tk.glucodata.ui.setup.LibreSetupWizard(
            onDismiss = { showLibreWizard = false },
            onScanNfc = {
                // Launch existing NFC/Libre flow
                tk.glucodata.MainActivity.launchQrScan()
                showLibreWizard = false
            }
        )
        return
    }
    
    // Dexcom Setup Wizard
    if (showDexcomWizard) {
        tk.glucodata.ui.setup.DexcomSetupWizard(
            onDismiss = { showDexcomWizard = false },
            onScan = {
                // Launch existing Dexcom/QR flow
                tk.glucodata.MainActivity.launchQrScan()
                showDexcomWizard = false
            }
        )
        return
    }

    // Use Box instead of Scaffold to avoid double padding from parent nav
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        if (sensors.isEmpty()) {
            // Show sensor selection cards for empty state
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.sensors_title),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(start = 28.dp, end = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                tk.glucodata.ui.components.SensorsEmptyState(
                    onSensorSelected = { type ->
                        when (type) {
                            tk.glucodata.ui.components.SensorType.SIBIONICS -> showSibionicsWizard = true
                            tk.glucodata.ui.components.SensorType.LIBRE -> showLibreWizard = true
                            tk.glucodata.ui.components.SensorType.DEXCOM -> showDexcomWizard = true
                        }
                    }
                )
            }
        } else {
            // LazyColumn with header that scrolls
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
            ) {
                // Scrollable header
                item {
                    Text(
                        text = stringResource(R.string.sensors_title),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(start = 16.dp, bottom = 24.dp)
                    )
                }
                items(sensors) { sensor ->
                    SensorCard(sensor, viewModel)
                }
            }
        }
        
        // FAB overlay - only show when sensors exist
        if (sensors.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showSensorPicker = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Sensor",
                    modifier = Modifier.size(24.dp)
                )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall, // Smaller label
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium, // Larger value for scannability
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SensorCard(sensor: tk.glucodata.ui.viewmodel.SensorInfo, viewModel: tk.glucodata.ui.viewmodel.SensorViewModel) {
    var showTerminateDialog by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showUnifiedResetDialog by remember { mutableStateOf(false) }
    var keepAutoCalChecked by remember { mutableStateOf(false) }
    var showReconnectDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var wipeDataChecked by remember { mutableStateOf(false) }
    var keepDataChecked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope() // Fix: Add missing scope

    if (showTerminateDialog) {
        AlertDialog(
            onDismissRequest = {
                showTerminateDialog = false
                keepDataChecked = false
            },
            title = { Text(stringResource(R.string.disconnect_sensor_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.disconnect_sensor_desc))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = keepDataChecked,
                            onCheckedChange = { keepDataChecked = it }
                        )
                        Text(stringResource(R.string.keep_data))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.terminateSensor(sensor.serial, !keepDataChecked)
                    showTerminateDialog = false
                    keepDataChecked = false
                }) { Text(stringResource(R.string.disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTerminateDialog = false
                    keepDataChecked = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showReconnectDialog) {
        AlertDialog(
            onDismissRequest = {
                showReconnectDialog = false
                wipeDataChecked = false
            },
            title = { Text(stringResource(R.string.reconnect_sensor_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.reconnect_sensor_desc))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = wipeDataChecked,
                            onCheckedChange = { wipeDataChecked = it }
                        )
                        Text(stringResource(R.string.wipe_data))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reconnectSensor(sensor.serial, wipeDataChecked)
                    showReconnectDialog = false
                    wipeDataChecked = false
                }) { Text(stringResource(R.string.reconnect)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReconnectDialog = false
                    wipeDataChecked = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showForgetDialog) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text(stringResource(R.string.forget_sensor_title)) },
            text = { Text(stringResource(R.string.forget_sensor_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forgetSensor(sensor.serial)
                    showForgetDialog = false
                }) { Text(stringResource(R.string.forget)) }
            },
            dismissButton = {
                TextButton(onClick = { showForgetDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_sensor_title)) },
            text = { Text(stringResource(R.string.reset_sensor_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSensor(sensor.serial)
                    showResetDialog = false
                }) { Text(stringResource(R.string.reset_sensor)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.restart_autocal_title)) },
            text = { Text(stringResource(R.string.restart_autocal_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    // Launch coroutine to handle sequence
                    viewModel.clearCalibration(sensor.serial)
                    // Reconnect handles its own async disconnect/delay logic now
                    viewModel.reconnectSensor(sensor.serial, false)
                    showClearDialog = false
                }) { Text(stringResource(R.string.restart)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showUnifiedResetDialog) {
        AlertDialog(
            onDismissRequest = { 
                showUnifiedResetDialog = false
                keepAutoCalChecked = false 
            },
            title = { Text(stringResource(R.string.reset_sensor_title)) },
            text = { 
                Column {
                    Text(stringResource(R.string.unified_reset_desc))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { keepAutoCalChecked = !keepAutoCalChecked }
                    ) {
                        Checkbox(
                            checked = keepAutoCalChecked,
                            onCheckedChange = { keepAutoCalChecked = it }
                        )
                        Text(stringResource(R.string.keep_auto_calibration))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (keepAutoCalChecked) {
                        viewModel.resetSensor(sensor.serial)  // Hardware reset only
                    } else {
                        viewModel.clearAll(sensor.serial)     // Full reset
                    }
                    showUnifiedResetDialog = false
                    keepAutoCalChecked = false
                }) { Text(stringResource(R.string.reset_sensor)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showUnifiedResetDialog = false 
                    keepAutoCalChecked = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe sensor data?") },
            text = { Text("This will clear all data for this sensor from the app. The connection will remain active.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.wipeSensorData(sensor.serial)
                        showWipeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Wipe Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    val isStreaming = sensor.streaming
    // Visual Feedback: Darken card when disconnected/paused
    val containerColor = if (isStreaming) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentAlpha = if (isStreaming) 1f else 0.9f


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp), 
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(28.dp) 
    ) {
        // --- Dynamic Background Logic ---
        val now = System.currentTimeMillis()
        val start = sensor.startMs
        val end = if (sensor.expectedEndMs > 0) sensor.expectedEndMs 
                  else if (sensor.officialEndMs > 0) sensor.officialEndMs 
                  else start + (14L * 24 * 3600 * 1000)
        
        val totalDuration = (end - start).coerceAtLeast(1) // Avoid div/0
        val usedDuration = (now - start).coerceAtLeast(0)
        val progress = (usedDuration.toFloat() / totalDuration).coerceIn(0f, 1f)
        
        // Color Shift: Safe -> Warning (80%) -> Critical (95%)
        val fillColor = when {
            progress > 0.95f -> MaterialTheme.colorScheme.error
            progress > 0.80f -> MaterialTheme.colorScheme.tertiary 
            else -> MaterialTheme.colorScheme.primary
        }
        val fillAlpha = 0.12f // Light tint

        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Dynamic Fill Layer
            // FIX: Only show fill if start date is valid (> Jan 1 2020)
            // Prevents "100% Red Fill" bug when startMs is 0 or invalid (1970).
            if (sensor.startMs > 1577836800000L) {
                 Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(fillColor.copy(alpha = fillAlpha))
                )
            }

            // 2. Content Layer with Color Indicator
            Row(modifier = Modifier.fillMaxWidth().padding(0.dp).alpha(contentAlpha)) {
                // Color indicator bar - shows sensor's assigned color
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            sensor.color.copy(alpha = if (sensor.isActive) 1f else 0.4f),
                            RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                        )
                )
                
                Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                    val statusText = if (isStreaming) stringResource(R.string.enabled_status) else stringResource(R.string.disabled_status)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Title with optional "Active" badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${sensor.serial} • $statusText", style = MaterialTheme.typography.titleLarge)
                                // Toggle Main Sensor Badge
                                Spacer(modifier = Modifier.width(8.dp))
                                val isMain = sensor.isActive
                                
                                val badgeColor = if(isMain) sensor.color else sensor.color.copy(alpha=0.6f)
                                val badgeBg = if(isMain) sensor.color.copy(alpha = 0.15f) else Color.Transparent
                                val badgeBorder = if(isMain) null else androidx.compose.foundation.BorderStroke(1.dp, sensor.color.copy(alpha=0.3f))

                                // Touch Target Wrapper (48dp minimum)
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .clickable { if(!isMain) viewModel.setMain(sensor.serial) }
                                        .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp), // Check imports
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        color = badgeBg,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        border = badgeBorder
                                    ) {
                                        Icon(
                                            imageVector = if (isMain) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = if (isMain) "Active" else "Set Main",
                                            tint = badgeColor,
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .size(18.dp)
                                        )
                                    }
                                }
                            }
                            // Feature: Detailed Sensor Status
                            Spacer(modifier = Modifier.height(8.dp)) 

                    if (sensor.detailedStatus.isNotEmpty()) {
                        Text(
                            text = sensor.detailedStatus,
                            style = MaterialTheme.typography.titleSmall, // Bigger than labelMedium
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else if (sensor.connectionStatus.isNotEmpty()) {
                         Text(
                            text = sensor.connectionStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                        }
                        
                        // Logic: Show Pause if running, Play if stopped (to resume)
                        IconButton(
                            onClick = {
                                if (isStreaming) {
                                    android.util.Log.d("SensorCard", "Pause button clicked for: ${sensor.serial}")
                                    viewModel.disconnectSensor(sensor.serial)
                                } else {
                                    android.util.Log.d("SensorCard", "Play button clicked for: ${sensor.serial}")
                                    viewModel.reconnectSensor(sensor.serial, false)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceDim.copy(alpha=0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Toggle Sensor",
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
//                } // Close Column (content)
//                } // Close Column (content)
//            } // Close Row (color indicator wrapper)
//            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer // Tonal separation
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clean Label-Value rows
                    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val valueStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)

                    val DataRow = @Composable { label: String, value: String ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = labelStyle)
                            Text(value, style = valueStyle)
                        }
                    }

                    if (sensor.connectionStatus.isNotEmpty()) {
                        DataRow(stringResource(R.string.last_ble_status), sensor.connectionStatus)
                    }
                    DataRow(stringResource(R.string.sensor_address), sensor.deviceAddress)
                    
                    // FIX: Use Long timestamp directly to avoid String Parsing Locale bugs in formatSensorTime
                    // User reported "100% Fill / Red Color" bug in English Locale, likely due to startMs being 0 or parse fail.
                    // We also ensure we only show valid dates.
                    if (sensor.startMs > 1577836800000L) { // > Jan 1 2020
                        DataRow(stringResource(R.string.sensor_started), formatSensorTime(sensor.startMs.toString()))
                    }

                    if (sensor.officialEndMs > 0) {
                        DataRow(stringResource(R.string.sensor_ends_officially), formatSensorTime(sensor.officialEndMs.toString()))
                    } else if (sensor.officialEnd.isNotEmpty()) {
                        DataRow(stringResource(R.string.sensor_ends_officially), formatSensorTime(sensor.officialEnd))
                    }

                    if (sensor.expectedEndMs > 0) {
                        DataRow(stringResource(R.string.sensor_expected_end), formatSensorTime(sensor.expectedEndMs.toString()))
                    } else if (sensor.expectedEnd.isNotEmpty()) {
                       DataRow(stringResource(R.string.sensor_expected_end), formatSensorTime(sensor.expectedEnd))
                    }

                }
            }
//
//            Card(
//                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), // Secondary Container
//                shape = RoundedCornerShape(12.dp),
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Column(
//                    modifier = Modifier.padding(12.dp),
//                    verticalArrangement = Arrangement.spacedBy(4.dp)
//                ) {
//                    if (sensor.connectionStatus.isNotEmpty()) {
//                        InfoRow(stringResource(R.string.last_ble_status), sensor.connectionStatus)
//                    }
//                    InfoRow(stringResource(R.string.sensor_address), sensor.deviceAddress)
//
//                    InfoRow(stringResource(R.string.sensor_started), formatSensorTime(sensor.starttime))
//                    if (sensor.officialEnd.isNotEmpty()) {
//                        InfoRow(stringResource(R.string.sensor_ends_officially), formatSensorTime(sensor.officialEnd))
//                    }
//                    if (sensor.expectedEnd.isNotEmpty()) {
//                        InfoRow(stringResource(R.string.sensor_expected_end), formatSensorTime(sensor.expectedEnd))
//                    }
//                    // InfoRow("Streaming", if (sensor.streaming) "Enabled" else "Disabled")
//                }
//            }

            Spacer(modifier = Modifier.height(16.dp)) // More breathing room (M3 Expressive)
//            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
//            Spacer(modifier = Modifier.height(16.dp))

            // Calibration Mode (Sibionics Only) - M3 Expressive Connected Button Group
            if (sensor.isSibionics) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.calibration_algorithm),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // M3 Expressive Trailing Action (Compact)
                    if (sensor.viewMode != 1) {
                        FilledTonalButton(
                        onClick = { showClearDialog = true },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
//                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
//                        Spacer(Modifier.width(4.dp))
//                        Text(stringResource(R.string.restart), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                    }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    val autoStr = stringResource(R.string.auto)
                    val rawStr = stringResource(R.string.raw)
                    val autoRawStr = stringResource(R.string.auto_raw)
                    val rawAutoStr = stringResource(R.string.raw_auto)

                    val modes = listOf(autoStr, rawStr, autoRawStr, rawAutoStr)
                    modes.forEachIndexed { index, title ->
                        FilterChip(
                            selected = sensor.viewMode == index,
                            onClick = { viewModel.setCalibrationMode(sensor.serial, index) },
                            label = { Text(title) }
                        )
                    }
                }

//                BUTTONS ALT

//                Text(
//                    stringResource(R.string.calibration_algorithm),
//                    style = MaterialTheme.typography.labelLarge,
//                    color = MaterialTheme.colorScheme.primary
//                )
//                Spacer(modifier = Modifier.height(16.dp))
//
//                val autoStr = stringResource(R.string.auto)
//                val rawStr = stringResource(R.string.raw)
//                val autoRawStr = stringResource(R.string.auto_raw)
//                val rawAutoStr = stringResource(R.string.raw_auto)
//                val modes = listOf(autoStr, rawStr, autoRawStr, rawAutoStr)
//
//                // M3 Connected Button Group - single container, flush buttons
//                Surface(
//                    shape = RoundedCornerShape(90), // Full pill/capsule
//                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Row(
//                        modifier = Modifier.padding(0.dp)
//                    ) {
//                        modes.forEachIndexed { index, title ->
//                            val isSelected = sensor.viewMode == index
//
//                            // Selected state with animated background
//                            val backgroundColor by animateColorAsState(
//                                targetValue = if (isSelected)
//                                    MaterialTheme.colorScheme.secondaryContainer
//                                else
//                                    Color.Transparent,
//                                animationSpec = tween(200),
//                                label = "bgColor$index"
//                            )
//
//                            Surface(
//                                onClick = { viewModel.setCalibrationMode(sensor.serial, index) },
//                                shape = RoundedCornerShape(90), // Each button is also pill-shaped
//                                color = backgroundColor,
//                                modifier = Modifier.weight(1f)
//                            ) {
//                                Row(
//                                    horizontalArrangement = Arrangement.Center,
//                                    verticalAlignment = Alignment.CenterVertically,
//                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
//                                ) {
//                                    // Star icon for selected state (like the reference)
//                                    if (isSelected) {
//
//                                        Spacer(modifier = Modifier.width(4.dp))
//                                    }
//                                    Text(
//                                        text = title,
//                                        style = MaterialTheme.typography.labelMedium,
//                                        color = if (isSelected)
//                                            MaterialTheme.colorScheme.onSecondaryContainer
//                                        else
//                                            MaterialTheme.colorScheme.onSurfaceVariant,
//                                        maxLines = 1
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
                Spacer(modifier = Modifier.height(8.dp))
            }



            if (sensor.isSibionics && sensor.viewMode != 1) {

                // Button moved to Calibration Algorithm header

                Spacer(modifier = Modifier.height(16.dp))

                var customEnabled by remember(sensor.customCalEnabled) { mutableStateOf(sensor.customCalEnabled) }
                var customIndex by remember(sensor.customCalIndex) { mutableStateOf(sensor.customCalIndex.toFloat()) }
                var customAutoReset by remember(sensor.customCalAutoReset) { mutableStateOf(sensor.customCalAutoReset) }

                // Helper for slider labels
                val labels = listOf("12 hours", "24 hours", "2 days", "3 days", "7 days", "14 days", "20 days")

                // Calculate max allowed index based on sensor age
                val sensorAge = System.currentTimeMillis() - sensor.startMs
                val maxAllowedIndex = if (sensor.startMs > 0) {
                    when {
                        sensorAge >= 20L * 24 * 3600 * 1000 -> 6
                        sensorAge >= 14L * 24 * 3600 * 1000 -> 5
                        sensorAge >= 7L * 24 * 3600 * 1000 -> 4
                        sensorAge >= 3L * 24 * 3600 * 1000 -> 3
                        sensorAge >= 2L * 24 * 3600 * 1000 -> 2
                        sensorAge >= 24L * 3600 * 1000 -> 1
                        else -> 0
                    }
                } else {
                    6 // Fallback if start time unknown
                }

                // Coerce current index to valid range
                // Note: We don't auto-update the backend here to avoid side effects, but we visually clamp the slider
                val safeIndex = customIndex.coerceIn(0f, maxAllowedIndex.toFloat())
                val currentLabel = labels.getOrElse(safeIndex.toInt()) { "12 hours" }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.auto_calibration_mode), style = MaterialTheme.typography.titleMedium)
//                             if (!customEnabled) {
//                                 Text("Juggluco native", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                             }
                        if (customEnabled) {
                            Text(
                                text = "$currentLabel ${stringResource(R.string.window_label)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.juggluco_native),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StyledSwitch(
                        checked = customEnabled,
                        onCheckedChange = { enabled ->
                            customEnabled = enabled
                            viewModel.updateCustomCalibration(sensor.serial, enabled, safeIndex.toInt(), customAutoReset)
                        }
                    )
                }

                AnimatedVisibility(visible = customEnabled) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (maxAllowedIndex < 6) {
//                             Text(
//                                text = "Limited by sensor age (${(sensorAge / (3600 * 1000 * 24))} days)",
//                                style = MaterialTheme.typography.labelSmall,
//                                color = MaterialTheme.colorScheme.secondary,
//                                modifier = Modifier.padding(horizontal = 8.dp)
//                            )
                        }
                        Slider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            value = safeIndex,
                            onValueChange = { customIndex = it },
                            valueRange = 0f..maxAllowedIndex.toFloat(),
                            steps = if (maxAllowedIndex > 0) maxAllowedIndex - 1 else 0,
                            onValueChangeFinished = {
                                viewModel.updateCustomCalibration(sensor.serial, true, customIndex.toInt().coerceAtMost(maxAllowedIndex), customAutoReset)
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val newVal = !customAutoReset
                                customAutoReset = newVal
                                viewModel.updateCustomCalibration(sensor.serial, true, customIndex.toInt(), newVal)
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = customAutoReset,
                                onCheckedChange = { checked ->
                                    customAutoReset = checked
                                    viewModel.updateCustomCalibration(sensor.serial, true, customIndex.toInt(), checked)
                                }
                            )
                            Text(stringResource(R.string.auto_restart_algorithm), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (sensor.isSibionics2) {


                Spacer(modifier = Modifier.height(16.dp))

                // Auto reset
                // LOGIC:
                // < 25 means Enabled (Standard range is 1-22).
                // >= 25 (e.g. 300) means Disabled/Never.
                val isAutoResetEnabled = sensor.autoResetDays < 25


                Column(modifier = Modifier.fillMaxWidth()) {
                    // Use Int for stepper value
                    var daysValue by remember(sensor.autoResetDays) {
                        mutableStateOf(if (isAutoResetEnabled) sensor.autoResetDays else 20)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Title only
                        Text(
                            stringResource(R.string.auto_reset_title), 
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        // Right: Integrated Floating Stepper + Switch
                        // Peak 2026: Capsule containment, Ghost state, Spring animation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp) // Extra spacing before switch
                        ) {
                            // Animate stepper scale and alpha with spring
                            val stepperScale by animateFloatAsState(
                                targetValue = if (isAutoResetEnabled) 1f else 0.95f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "stepperScale"
                            )
                            val stepperAlpha by animateFloatAsState(
                                targetValue = if (isAutoResetEnabled) 1f else 0.38f,
                                animationSpec = tween(250),
                                label = "stepperAlpha"
                            )
                            
                            // M3 Peak 2026: Capsule Container wraps the entire stepper
                            Surface(
                                shape = MaterialTheme.shapes.large, // Pill-like capsule
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = stepperScale
                                        scaleY = stepperScale
                                        alpha = stepperAlpha
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    // Minus button - 40dp touch target, 16dp visible icon
                                    IconButton(
                                        onClick = {
                                            if (isAutoResetEnabled && daysValue > 1) {
                                                daysValue--
                                                viewModel.setAutoResetDays(sensor.serial, daysValue)
                                            }
                                        },
                                        enabled = isAutoResetEnabled && daysValue > 1,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isAutoResetEnabled && daysValue > 1)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                        )
                                    }
                                    
                                    // Value display - the single source of truth
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (isAutoResetEnabled) 
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = stringResource(R.string.auto_reset_days, daysValue),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAutoResetEnabled)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                    
                                    // Plus button - 40dp touch target, 16dp visible icon
                                    IconButton(
                                        onClick = {
                                            if (isAutoResetEnabled && daysValue < 22) {
                                                daysValue++
                                                viewModel.setAutoResetDays(sensor.serial, daysValue)
                                            }
                                        },
                                        enabled = isAutoResetEnabled && daysValue < 22,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isAutoResetEnabled && daysValue < 22)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                        )
                                    }
                                }
                            }
                            
                            StyledSwitch(
                                checked = isAutoResetEnabled,
                                onCheckedChange = { enabled ->
                                    val newValue = if (enabled) daysValue else 300
                                    viewModel.setAutoResetDays(sensor.serial, newValue)
                                }
                            )
                        }
                    }
                }
            } // End isSibionics2 block

            // --- ACTION BUTTONS (Always Visible) ---
            Spacer(modifier = Modifier.height(24.dp))

            // Row 1: Unified Reset Button (Sibionics only - full width, styled like "Previous calibrations")
            if (sensor.isSibionics) {
                FilledTonalButton(
                    onClick = { showUnifiedResetDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsBackupRestore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_sensor))
                }
            }

            // Row 2: Reconnect | Disconnect (styled like "Clear all" / "Calibrate" - connected button group)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)  // No gap for connected look
            ) {
                // Reconnect - Left side (tonal, like "Clear all")
                FilledTonalButton(
                    onClick = { showReconnectDialog = true },
//                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        bottomStart = 12.dp,
                        topEnd = 4.dp,
                        bottomEnd = 4.dp
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.reconnect), maxLines = 1)
                }
                
//                Spacer(modifier = Modifier.width(4.dp))
                
                // Disconnect - Right side (error container)
                FilledTonalButton(
                    onClick = { showTerminateDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        bottomStart = 4.dp,
                        topEnd = 12.dp,
                        bottomEnd = 12.dp
                    ),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.disconnect), maxLines = 1)
                }
            }
            }
        }
    }
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
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Nightscout Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // Auto-save on exit
        DisposableEffect(Unit) {
            onDispose {
                Natives.setNightUploader(url, secret, isActive, isV3)
                Natives.setpostTreatments(sendTreatments)
                // Optional: Toast or logging, but user wanted standard app behavior which is silent save usually
            }
        }

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
                    StyledSwitch(checked = isActive, onCheckedChange = { isActive = it })
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
                    StyledSwitch(checked = sendTreatments, onCheckedChange = { sendTreatments = it })
                }
            )

            // Mobile only V3 check
            ListItem(
                headlineContent = { Text("Use V3 API") },
                supportingContent = { Text("Experimental") },
                trailingContent = {
                    StyledSwitch(checked = isV3, onCheckedChange = { isV3 = it })
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