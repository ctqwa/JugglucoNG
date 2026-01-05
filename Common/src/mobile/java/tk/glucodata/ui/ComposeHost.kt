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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.zIndex
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
import androidx.compose.animation.togetherWith
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    val primaryStr: String,
    val secondaryStr: String? = null,
    val fullFormatted: String
)

fun getDisplayValues(point: GlucosePoint, viewMode: Int, unit: String): DisplayValues {
    val rawStr = if (point.rawValue < 30) String.format("%.1f", point.rawValue) else point.rawValue.toInt().toString()
    val valStr = if (point.value < 30) String.format("%.1f", point.value) else point.value.toInt().toString()

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
        content = content
    )
}

@Composable
fun MainApp(themeMode: ThemeMode, onThemeChanged: (ThemeMode) -> Unit) {
    val navController = rememberNavController()
    val dashboardViewModel: DashboardViewModel = viewModel()
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
    val onNavigate = { route: String ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
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
                    val isSelected = currentRoute == item.route
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
                    composable("dashboard") { DashboardScreen(dashboardViewModel) }
                    composable("sensors") { SensorScreen() }
                    composable("settings") { SettingsScreen(navController, themeMode, onThemeChanged, dashboardViewModel) }
                    composable("settings/nightscout") { NightscoutSettingsScreen(navController) }
                    composable("settings/mirror") { MirrorSettingsScreen(navController) }
                    composable("settings/mirror/edit/{pos}") { backStackEntry ->
                        val pos = backStackEntry.arguments?.getString("pos")?.toIntOrNull() ?: -1
                        MirrorEditScreen(navController, pos)
                    }
                    composable("settings/debug") { DebugSettingsScreen(navController) }
                }
            }
        }
    } else {
        // --- PORTRAIT: Bottom Navigation Bar ---
        Scaffold(
            bottomBar = {
                NavigationBar {
                    navItems.forEach { item ->
                        val isSelected = currentRoute == item.route
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
                composable("dashboard") { DashboardScreen(dashboardViewModel) }
                composable("sensors") { SensorScreen() }
                composable("settings") { SettingsScreen(navController, themeMode, onThemeChanged, dashboardViewModel) }
                composable("settings/nightscout") { NightscoutSettingsScreen(navController) }
                composable("settings/mirror") { MirrorSettingsScreen(navController) }
                composable("settings/mirror/edit/{pos}") { backStackEntry ->
                    val pos = backStackEntry.arguments?.getString("pos")?.toIntOrNull() ?: -1
                    MirrorEditScreen(navController, pos)
                }
                composable("settings/debug") { DebugSettingsScreen(navController) }
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
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
    val viewMode by viewModel.viewMode.collectAsState()

    Scaffold(
        // Parent MainApp handles system insets, so we reset them here to avoid double-padding
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            if (glucoseHistory.isEmpty()) {
                FloatingActionButton(onClick = {
                    tk.glucodata.MainActivity.launchQrScan()
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_sensor))
                }
            }
        }
    ) { padding ->
        val latestPoint = remember(glucoseHistory) { glucoseHistory.maxByOrNull { it.timestamp } }
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // --- REUSABLE UI SECTIONS ---


        val recentReadings = remember(glucoseHistory) {
            glucoseHistory.takeLast(10).reversed().distinctBy { it.timestamp }
        }

        // --- LAYOUT LOGIC ---

        if (isLandscape) {
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
                ) {
                    item { DashboardStatusSection(currentGlucose, currentRate, viewMode, latestPoint) }
                    item { DashboardMetaInfoSection(sensorName, daysRemaining) }
                    itemsIndexed(recentReadings, key = { index, item -> "${item.timestamp}_$index" }) { _, item ->
                        ReadingRow(
                            point = item,
                            unit = unit,
                            viewMode = viewMode,
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                // Right Pane: Big Chart (Full Height)
                Box(
                    modifier = Modifier
                        .weight(0.75f)
                        .fillMaxHeight()
                        .padding(vertical = 16.dp)
                ) {
                    DashboardChartSection(
                        modifier = Modifier.fillMaxSize(),
                        glucoseHistory = glucoseHistory,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        unit = unit,
                        viewMode = viewMode
                    )
                }
            }
        } else {
            // PORTRAIT: UNIFIED VERTICAL SCROLL
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                item { DashboardStatusSection(currentGlucose, currentRate, viewMode, latestPoint) }
                item { DashboardMetaInfoSection(sensorName, daysRemaining) }

                item {
                    // Portrait Chart: Flexible height
                    DashboardChartSection(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 520.dp),
                        glucoseHistory = glucoseHistory,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        unit = unit,
                        viewMode = viewMode
                    )
                }

                itemsIndexed(recentReadings, key = { index, item -> "${item.timestamp}_$index" }) { _, item ->
                    ReadingRow(
                        point = item,
                        unit = unit,
                        viewMode = viewMode,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

// --- EXTRACTED COMPONENTS (Performance Optimization) ---

@Composable
fun DashboardStatusSection(
    currentGlucose: String,
    currentRate: Float,
    viewMode: Int,
    latestPoint: GlucosePoint?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    val primaryColor = MaterialTheme.colorScheme.onSurface
                    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val unitColor = MaterialTheme.colorScheme.onSurfaceVariant

                    val finalDisplayGlucose = remember(currentGlucose, viewMode, latestPoint, primaryColor, secondaryColor) {
                        if (latestPoint != null) {
                            val dvs = getDisplayValues(latestPoint, viewMode, "")
                            buildGlucoseString(dvs, primaryColor, secondaryColor, unitColor, includeUnit = false)
                        } else {
                            androidx.compose.ui.text.AnnotatedString(currentGlucose)
                        }
                    }

                    // M3 Expressive: Animated value change (slide up with fade)
                    AnimatedContent(
                        targetState = finalDisplayGlucose,
                        transitionSpec = {
                            (slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { -it / 2 } + fadeIn(animationSpec = tween(200)))
                                .togetherWith(
                                    slideOutVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                    ) { it / 2 } + fadeOut(animationSpec = tween(100))
                                )
                        },
                        label = "GlucoseValueAnimation"
                    ) { glucoseValue ->
                        Text(
                            glucoseValue,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))

                // M3 Expressive: Animated trend icon
                AnimatedContent(
                    targetState = currentRate,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                                slideInVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) { if (targetState > initialState) -it / 3 else it / 3 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(100)) +
                                        slideOutVertically() { if (targetState > initialState) it / 3 else -it / 3 }
                            )
                    },
                    label = "TrendIconAnimation"
                ) { rate ->
                    Icon(
                        imageVector = getTrendIcon(rate),
                        contentDescription = getTrendDescription(rate),
                        modifier = Modifier.size(48.dp).padding(4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardMetaInfoSection(
    sensorName: String,
    daysRemaining: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sensorName.isNotEmpty()) Text(sensorName, style = MaterialTheme.typography.titleMedium)
        if (daysRemaining.isNotEmpty()) Text(daysRemaining, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun DashboardChartSection(
    modifier: Modifier,
    glucoseHistory: List<GlucosePoint>,
    targetLow: Float,
    targetHigh: Float,
    unit: String,
    viewMode: Int
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (glucoseHistory.isNotEmpty()) {
                InteractiveGlucoseChart(
                    fullData = glucoseHistory,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    unit = unit,
                    viewMode = viewMode
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_data_available)) }
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
    onDateSelected: (Long) -> Unit = {}  // Callback when user picks a date to jump to
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

    val isMmol = targetHigh <= 12
    val defaultMin = if (isMmol) 1.5f else 27f
    val defaultMax = if (isMmol) 14f else 250f

    var yMin by rememberSaveable {
        var initMin = defaultMin
        if (safeData.isNotEmpty()) {
            val outlierCount = 3
            val lowPoints = safeData.count { it.value < defaultMin }
            if (lowPoints > outlierCount) {
                // We have frequent low values, drop floor
                val dataMin = safeData.minOf { it.value }
                initMin = (dataMin - (if(isMmol) 0.5f else 10f)).coerceAtLeast(0f)
            }
        }
        mutableFloatStateOf(initMin)
    }

    var yMax by rememberSaveable {
        var initMax = defaultMax
        if (safeData.isNotEmpty()) {
            val outlierCount = 3
            val highPoints = safeData.count { it.value > defaultMax }
            if (highPoints > outlierCount) {
                // We have frequent high values, raise ceiling
                val dataMax = safeData.maxOf { it.value }
                initMax = dataMax + (if(isMmol) 1f else 20f)
            }
        }
        mutableFloatStateOf(initMax)
    }

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
                                val v = if (currentViewMode == 1 || currentViewMode == 3) pointAtTouch.rawValue else pointAtTouch.value
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

                // Viewport (Calculated using animated duration for smooth zoom)
                val currentDur = animatedVisibleDuration.toLong()
                val viewportStart = centerTime - currentDur / 2
                val viewportEnd = centerTime + currentDur / 2

                // Optimization: Don't filter list. Calculate indices.
                // 1. Find start index using Binary Search (Manual to avoid lambda allocation)
                // Use the larger valid duration for padding to avoid popping during zoom out
                val paddingTime = maxOf(visibleDuration, currentDur) / 5
                val searchStart = viewportStart - paddingTime
                val searchEnd = viewportEnd + paddingTime

                // Manual Binary Search for Start Index
                var low = 0
                var high = safeData.size - 1
                var startIndex = -1
                while (low <= high) {
                    val mid = (low + high) ushr 1
                    val midVal = safeData[mid].timestamp
                    if (midVal < searchStart) low = mid + 1
                    else if (midVal > searchStart) high = mid - 1
                    else { startIndex = mid; break }
                }
                if (startIndex < 0) startIndex = low
                startIndex = startIndex.coerceIn(0, safeData.size)

                // Manual Binary Search for End Index
                high = safeData.size - 1 // Reset high, keep low as optimization? No, reset.
                // low = startIndex // Optimization: End is definitely after start
                low = 0
                var endIndex = -1
                while (low <= high) {
                    val mid = (low + high) ushr 1
                    val midVal = safeData[mid].timestamp
                    if (midVal < searchEnd) low = mid + 1
                    else if (midVal > searchEnd) high = mid - 1
                    else { endIndex = mid; break }
                }
                if (endIndex < 0) endIndex = low
                endIndex = endIndex.coerceIn(0, safeData.size)

                // --- HELPERS ---
                // Use animated duration for X-axis scaling
                fun timeToX(t: Long): Float = ((t - viewportStart).toFloat() / animatedVisibleDuration) * width
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
                // Calculate Grid Interval based on text width avoidance
                // Increased spacing to 120px to prevent text crumbling (User Request)
                val pxPerMin = width / (visibleDuration / 60000f)
                val minSpacingPx = 120f
                val minSpacingMins = minSpacingPx / pxPerMin

                val gridInterval = gridIntervals.firstOrNull { it / 60000f >= minSpacingMins } ?: gridIntervals.last()

                // --- FORMATTERS & TOOLS (Hoisted) ---
                // (Moved to top of function due to Composable context requirement)

                // --- 1. DRAW GRID ---
                // ... Grid Logic ...

                // Fix: align t to local timezone so we hit 00:00 correctly
                val tzOffset = java.util.TimeZone.getDefault().getOffset(viewportStart).toLong()
                var t = ((viewportStart + tzOffset) / gridInterval) * gridInterval - tzOffset

                while (t <= viewportEnd + gridInterval) {
                    val x = timeToX(t)
                    if (x in -50f..width + 50f) { // Allow slight overdraw for edges
                        cal.timeInMillis = t

                        // Check for Midnight (Date Boundary)
                        val isMidnight = cal.get(java.util.Calendar.HOUR_OF_DAY) == 0 && cal.get(java.util.Calendar.MINUTE) == 0
                        val isDayStart = isMidnight

                        if (isDayStart) {
                            // Date Line
                            drawLine(gridColor.copy(alpha=0.8f), Offset(x, 0f), Offset(x, chartHeight), 3f)

                            val dateLabel = labelCache.getOrPut(t) {
                                reusableDate.time = t
                                formatDate.format(reusableDate)
                            }
                            drawContext.canvas.nativeCanvas.drawText(dateLabel, x, height - 25f, xTextPaint.apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
                            // Reset paint
                            xTextPaint.typeface = android.graphics.Typeface.DEFAULT
                        } else {
                            // Time Line
                            drawLine(gridColor, Offset(x, 0f), Offset(x, chartHeight), 1f)

                            // Optimization: Use Cache to avoid SimpleDateFormat on every frame
                            val timeLabel = labelCache.getOrPut(t) {
                                reusableDate.time = t
                                formatTime.format(reusableDate)
                            }
                            drawContext.canvas.nativeCanvas.drawText(timeLabel, x, height - 10f, xTextPaint)
                        }
                    }
                    t += gridInterval
                }


                // --- 3. DATA LINES ---
                // --- 3. DATA LINES ---
                if (endIndex > startIndex) {
                    // Optimized: Inline drawing (Manual Unroll) to avoid closure allocations

                    // --- RAW LINE ---
                    if (viewMode == 1 || viewMode == 3 || viewMode == 2) {
                        reusablePath.rewind()
                        var first = true
                        var lastX = -100f

                        for (i in startIndex until endIndex) {
                            val p = safeData[i]
                            // Raw value handling: filter invalid < 0.1 checks
                            val v = if(p.rawValue < 0.1f) p.value else p.rawValue

                            val px = timeToX(p.timestamp)
                            // DECIMATION: Aggressive (1.0px) to reduce GPU load
                            if (!first && kotlin.math.abs(px - lastX) < 1.0f) continue
                            lastX = px
                            val py = valToY(v)
                            if (first) { reusablePath.moveTo(px, py); first = false }
                            else { reusablePath.lineTo(px, py) }
                        }

                        // Color Logic: Mode 2 uses Secondary for Raw, others (1, 3) use Primary
                        val color = if (viewMode == 2) secondaryColor else primaryColor
                        val width = if (viewMode == 2) 2.dp.toPx() else 3.dp.toPx()
                        drawPath(reusablePath, color, style = Stroke(width = width))
                    }

                    // --- AUTO / CALIBRATED LINE ---
                    if (viewMode == 0 || viewMode == 2 || viewMode == 3) {
                        reusablePath.rewind()
                        var first = true
                        var lastX = -100f

                        for (i in startIndex until endIndex) {
                            val p = safeData[i]
                            val v = p.value // Always use value

                            val px = timeToX(p.timestamp)
                            // DECIMATION: Aggressive (1.0px) to reduce GPU load
                            if (!first && kotlin.math.abs(px - lastX) < 1.0f) continue
                            lastX = px
                            val py = valToY(v)
                            if (first) { reusablePath.moveTo(px, py); first = false }
                            else { reusablePath.lineTo(px, py) }
                        }

                        // Color Logic: Mode 3 uses Secondary for Auto, others (0, 2) use Primary
                        val color = if (viewMode == 3) secondaryColor else primaryColor
                        val width = if (viewMode == 3) 2.dp.toPx() else 3.dp.toPx()
                        drawPath(reusablePath, color, style = Stroke(width = width))
                    }
                }

                // --- 4. MIN/MAX INDICATORS (On Left) ---
                if (endIndex > startIndex) {
                    // Decide what to show based on viewMode
                    var minPoint: GlucosePoint = safeData[startIndex]
                    var maxPoint: GlucosePoint = safeData[startIndex]
                    var minVal = Float.MAX_VALUE
                    var maxVal = Float.MIN_VALUE

                    // Filter out 0s if checking Raw
                    for (i in startIndex until endIndex) {
                        val p = safeData[i]
                        val v = if (viewMode == 1 || viewMode == 3) p.rawValue else p.value

                        // Ignore invalid raw values if in raw mode
                        if ((viewMode == 1 || viewMode == 3) && v < 0.1f) continue

                        if (v < minVal) {
                            minVal = v
                            minPoint = p
                        }
                        if (v > maxVal) {
                            maxVal = v
                            maxPoint = p
                        }
                    }

                    fun drawIndicator(point: GlucosePoint) {
                        val v = if (viewMode == 1 || viewMode == 3) point.rawValue else point.value
                        // Sanity check
                        if (v < 0.1f) return

                        val y = valToY(v)
                        val x = timeToX(point.timestamp)

                        if (y in 0f..chartHeight && x in 0f..width) {
                            // ... (Drawing code matches original context)
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
                                pathEffect = dashEffect
                            )
                            axisTextPaint.color = android.graphics.Color.GRAY
                        }
                    }
                    // Only draw if we found valid points
                    if (maxVal > Float.MIN_VALUE) drawIndicator(maxPoint)
                    if (minVal < Float.MAX_VALUE) drawIndicator(minPoint)
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
                            val dotRadius = 5.dp.toPx()

                            // Raw Dot (Modes 1, 2, 3)
                            if (viewMode == 1 || viewMode == 2 || viewMode == 3) {
                                val color = if (viewMode == 1 || viewMode == 3) primaryColor else secondaryColor
                                drawCircle(color, dotRadius, Offset(x, valToY(p.rawValue)))
                            }

                            // Auto Dot (Modes 0, 2, 3)
                            if (viewMode == 0 || viewMode == 2 || viewMode == 3) {
                                val color = if (viewMode == 0 || viewMode == 2) primaryColor else secondaryColor
                                drawCircle(color, dotRadius, Offset(x, valToY(p.value)))
                            }

                            // Bubble REMOVED (Replaced by Composable Overlay)
                            // val timeLabel = p.time ...
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
                val cardShape = RoundedCornerShape(12.dp) // Match likely default Card shape

                // AXIS TEXT MATCHING
                val axisFontSize = 12.sp

                val dvs = getDisplayValues(point, viewMode, unit)

                // --- 1. INFO CARD (Top) ---
                // "Current Status Card styling" -> primaryContainer
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // 1. Move to Cursor X, Fixed Y
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = cardXOffset.toInt(),
                                y = 48.dp.roundToPx()
                            )
                        }
                        // 2. Center
                        .graphicsLayer { translationX = -size.width / 2f }
                        .widthIn(min = 48.dp),
                    shape = cardShape,
                    color = statusCardColor.copy(alpha = 1f),
                    contentColor = statusContentColor.copy(alpha = 1f),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                        }

                        Text(
                            text = styledText,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // --- 2. TIME BUBBLE (Bottom) ---

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        // Move to Cursor X, Offset flush with bottom axis text
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = cardXOffset.toInt(),
                                y = (5).dp.roundToPx()
                            )
                        }
                        // Center
                        .graphicsLayer { translationX = -size.width / 2f }
                        .wrapContentWidth(),
                    shape = cardShape,
                    color = statusCardColor.copy(alpha = 1f),
                    contentColor = statusContentColor.copy(alpha = 1f),
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = point.time,
                        fontSize = axisFontSize, // Match Axis (10.sp)
                        // "make the bubble bigger" -> Increase padding
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            // Brace removed here to keep subsequent elements inside BoxWithConstraints



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
                    .padding(8.dp)
            ) {
                val headerDate = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date(centerTime))

                Text(
                    text = headerDate,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
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
                .horizontalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 4.dp, start = 0.dp, end = 0.dp), // Reduced padding
            horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally), // Tighter spacing
            verticalAlignment = Alignment.CenterVertically // Center vertical
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

                // Animated values
                val containerColor by animateColorAsState(
                    targetValue = if (isSel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
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
                    shape = RoundedCornerShape(100),
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
                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
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
                    shape = RoundedCornerShape(12.dp),
                    // "Similar background when its active" -> SecondaryContainer
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
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
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
    unit: String = ""
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
                if (includeUnit) {
                    append(" ")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = unitColor)) {
//                        append(unit)
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingRow(
    point: GlucosePoint,
    unit: String,
    viewMode: Int = 0,
    modifier: Modifier = Modifier
) {
    // M3 Expressive: Local smooth fade-out
    val highlightAlpha = remember(point.timestamp) { Animatable(0f) }

    LaunchedEffect(point.timestamp) {
        val age = System.currentTimeMillis() - point.timestamp
        if (age < 60_000) {
            val remaining = 60_000 - age
            val startAlpha = 0.4f * (remaining.toFloat() / 60_000f)
            highlightAlpha.snapTo(startAlpha)
            highlightAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remaining.toInt(), easing = LinearEasing)
            )
        }
    }

    val backgroundColor = lerp(
        start = MaterialTheme.colorScheme.surfaceVariant,
        stop = MaterialTheme.colorScheme.primaryContainer,
        fraction = highlightAlpha.value
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Time Column
        Text(
            text = point.time,
            style = MaterialTheme.typography.bodyMedium
        )

        // Value Column
        val dvs = getDisplayValues(point, viewMode, unit)
        val primaryColor = MaterialTheme.colorScheme.onSurface
        val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1.0f) // More visible
        val unitColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Least visible

        Text(
            text = buildGlucoseString(dvs, primaryColor, secondaryColor, unitColor, true, unit),
            fontWeight = FontWeight.Bold
        )
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                Switch(
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
                Switch(
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
                Switch(
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
            headlineContent = { Text("Turbo (high priority)") },
            supportingContent = { Text("Requests high connection priority") },
            trailingContent = {
                var turbo by remember { mutableStateOf(Natives.getpriority()) }
                Switch(
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
            headlineContent = { Text("Android (autoConnect)") },
            supportingContent = { Text("Uses passive background connection") },
            trailingContent = {
                var autoConnect by remember { mutableStateOf(Natives.getAndroid13()) }
                Switch(
                    checked = autoConnect,
                    onCheckedChange = {
                        SensorBluetooth.setAutoconnect(it)
                        autoConnect = it
                    }
                )
            }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.debug_logs)) },
            supportingContent = { Text("View trace.log and logcat") },
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
                                Text("CSV File (.csv)", fontWeight = FontWeight.Bold)
                                Text("Best for Excel/Analysis", style = MaterialTheme.typography.bodySmall)
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
                                Text("Readable Text (.txt)", fontWeight = FontWeight.Bold)
                                Text("Best for sharing/printing", style = MaterialTheme.typography.bodySmall)
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
                    }) { Text("Export") }
                },
                dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } }
            )
        }

        // Export History - M3 ListItem pattern (fully clickable)
        ListItem(
            headlineContent = { Text("Export History") },
            supportingContent = { Text("Save glucose readings to CSV or text file") },
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
            headlineContent = { Text("Import History") },
            supportingContent = { Text("Restore glucose readings from CSV file") },
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
                title = { Text("Clear History?") },
                text = { Text("This will remove all glucose readings from the database. This action cannot be undone.") },
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
                    ) { Text("Clear") }
                },
                dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } }
            )
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Clear App Data?") },
                text = {
                    Column {
                        Text("This will remove:")
                        Text("• All glucose readings")
                        Text("• Cache files")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Settings will be preserved.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("This action cannot be undone.", fontWeight = FontWeight.Bold)
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
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Clear Data") }
                },
                dismissButton = { TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") } }
            )
        }

        if (showFactoryResetDialog) {
            AlertDialog(
                onDismissRequest = { showFactoryResetDialog = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("⚠️ Factory Reset?") },
                text = {
                    Column {
                        Text("This will REMOVE EVERYTHING:", fontWeight = FontWeight.Bold)
                        Text("• All glucose readings")
                        Text("• All settings")
                        Text("• Cache files")
                        Text("• Sensor data")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("The app will return to first-run state.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("THIS CANNOT BE UNDONE!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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
                    ) { Text("RESET EVERYTHING") }
                },
                dismissButton = { TextButton(onClick = { showFactoryResetDialog = false }) { Text("Cancel") } }
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
                tk.glucodata.MainActivity.launchQrScan()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Sensor")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.sensors_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(16.dp))

            if (sensors.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_sensors_found))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
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
    val scope = rememberCoroutineScope() // Fix: Add missing scope

    if (showTerminateDialog) {
        AlertDialog(
            onDismissRequest = {
                showTerminateDialog = false
                wipeDataChecked = false
            },
            title = { Text(stringResource(R.string.disconnect_sensor_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.disconnect_sensor_desc))
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
                    viewModel.terminateSensor(sensor.serial, wipeDataChecked)
                    showTerminateDialog = false
                    wipeDataChecked = false
                }) { Text(stringResource(R.string.disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTerminateDialog = false
                    wipeDataChecked = false
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

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.reset_all_title)) },
            text = { Text(stringResource(R.string.reset_all_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll(sensor.serial)
                    showClearAllDialog = false
                }) { Text(stringResource(R.string.reset_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text(stringResource(R.string.cancel)) }
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
            val statusText = if (sensor.streaming) stringResource(R.string.enabled_status) else stringResource(R.string.disabled_status)
            val titleText = "${sensor.serial} • $statusText"
            Text(titleText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                if (sensor.connectionStatus.isNotEmpty()) {
                    InfoRow(stringResource(R.string.last_ble_status), sensor.connectionStatus)
                }
                InfoRow(stringResource(R.string.sensor_address), sensor.deviceAddress)

                InfoRow(stringResource(R.string.sensor_started), formatSensorTime(sensor.starttime))
                if (sensor.officialEnd.isNotEmpty()) {
                    InfoRow(stringResource(R.string.sensor_ends_officially), formatSensorTime(sensor.officialEnd))
                }
                if (sensor.expectedEnd.isNotEmpty()) {
                    InfoRow(stringResource(R.string.sensor_expected_end), formatSensorTime(sensor.expectedEnd))
                }
//                InfoRow("Streaming", if (sensor.streaming) "Enabled" else "Disabled")

            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Calibration Mode
            Text(stringResource(R.string.calibration_algorithm), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(16.dp))



            // --- CUSTOM AUTO-CALIBRATION (Sibionics Only) ---
            if (sensor.isSibionics2 && sensor.viewMode != 1) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    FilledTonalButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.reset_autocal_button), maxLines = 1)
                    }
                }

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
                        Text("Custom auto-calibration", style = MaterialTheme.typography.titleMedium)
//                             if (!customEnabled) {
//                                 Text("Juggluco native", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                             }
                        if (customEnabled) {
                            Text(
                                text = "$currentLabel window",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Juggluco native",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
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
                             Text(
                                text = "Limited by sensor age (${(sensorAge / (3600 * 1000 * 24))} days)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
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
                            Text("Auto restart algorithm")
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
                            Text(stringResource(R.string.auto_reset_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (isAutoResetEnabled) stringResource(R.string.auto_reset_days, sliderValue.toInt()) else stringResource(R.string.auto_reset_never),
                                style = MaterialTheme.typography.bodySmall,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),

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
                        Text(stringResource(R.string.reset_sensor), maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.reset_all), maxLines = 1)
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
                        Text(stringResource(R.string.disconnect))
                    }
                    OutlinedButton(
                        onClick = { showReconnectDialog = true },
                        modifier = Modifier.weight(1f) // Equal width
                    ) {
                        Text(stringResource(R.string.reconnect))
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