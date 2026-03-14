@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tk.glucodata.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import tk.glucodata.R
import tk.glucodata.ui.components.CardPosition
import tk.glucodata.ui.components.MasterSwitchCard
import tk.glucodata.ui.components.SectionLabel
import tk.glucodata.ui.components.SettingsSwitchItem
import tk.glucodata.ui.viewmodel.DashboardViewModel

@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
    }
    val notificationChartEnabled by viewModel.notificationChartEnabled.collectAsState()

    var fontSize by rememberSaveable { mutableFloatStateOf(prefs.getFloat("notification_font_size", 1.0f)) }
    var fontType by rememberSaveable { mutableIntStateOf(prefs.getInt("notification_font_family", 0)) }
    var fontWeight by rememberSaveable { mutableIntStateOf(prefs.getInt("notification_font_weight", 400)) }
    var showArrow by rememberSaveable { mutableStateOf(prefs.getBoolean("notification_show_arrow", true)) }
    var arrowSize by rememberSaveable { mutableFloatStateOf(prefs.getFloat("notification_arrow_size", 1.0f)) }
    var collapsedChart by rememberSaveable { mutableStateOf(prefs.getBoolean("notification_chart_collapsed", false)) }
    var showTargetRange by rememberSaveable { mutableStateOf(prefs.getBoolean("notification_chart_target_range", true)) }
    var statusIconScale by rememberSaveable { mutableFloatStateOf(prefs.getFloat("notification_status_icon_scale", 1.0f)) }

    fun save() {
        prefs.edit()
            .putFloat("notification_font_size", fontSize)
            .putInt("notification_font_family", fontType)
            .putInt("notification_font_weight", fontWeight)
            .putBoolean("notification_show_arrow", showArrow)
            .putFloat("notification_arrow_size", arrowSize)
            .putBoolean("notification_chart_collapsed", collapsedChart)
            .putBoolean("notification_chart_target_range", showTargetRange)
            .putFloat("notification_status_icon_scale", statusIconScale)
            .apply()
    }

    LegacySettingsScaffold(navController = navController, title = "Notification Settings") {
        SectionLabel("Font", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))

        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = fontType == 0,
                onClick = { fontType = 0; save() },
                label = { Text(stringResource(R.string.font_app_plex)) }
            )
            FilterChip(
                selected = fontType == 1,
                onClick = { fontType = 1; save() },
                label = { Text(stringResource(R.string.font_system_google_sans)) }
            )
        }

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.font_weight_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    300 to stringResource(R.string.theme_light),
                    400 to stringResource(R.string.regular),
                    500 to stringResource(R.string.medium)
                ).forEach { (weight, label) ->
                    FilterChip(
                        selected = fontWeight == weight,
                        onClick = { fontWeight = weight; save() },
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        LegacySliderControl(
            label = "Font Size: ${(fontSize * 100).toInt()}%",
            value = fontSize,
            onValueChange = { fontSize = it; save() },
            range = 0.6f..1.5f
        )
        Spacer(Modifier.height(4.dp))
        LegacySliderControl(
            label = "Status Bar Icon Size: ${(statusIconScale * 100).toInt()}%",
            value = statusIconScale,
            onValueChange = { statusIconScale = it; save() },
            range = 0f..1.25f
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            SettingsSwitchItem(
                title = "Show Trend Arrow",
                checked = showArrow,
                onCheckedChange = { showArrow = it; save() },
                icon = null,
                position = CardPosition.TOP
            )
            SettingsSwitchItem(
                title = "Show Chart (Expanded)",
                subtitle = "Chart when notification is expanded",
                checked = notificationChartEnabled,
                onCheckedChange = { viewModel.toggleNotificationChart(it) },
                icon = null,
                position = CardPosition.MIDDLE
            )
            SettingsSwitchItem(
                title = "Show Chart (Collapsed)",
                subtitle = "Compact chart in collapsed view",
                checked = collapsedChart,
                onCheckedChange = { collapsedChart = it; save() },
                icon = null,
                position = CardPosition.MIDDLE
            )
            SettingsSwitchItem(
                title = "Show Target Range",
                subtitle = "Highlight target glucose range on chart",
                checked = showTargetRange,
                onCheckedChange = { showTargetRange = it; save() },
                icon = null,
                position = CardPosition.BOTTOM
            )
        }

        if (showArrow) {
            Spacer(Modifier.height(4.dp))
            LegacySliderControl(
                label = "Arrow Size: ${(arrowSize * 100).toInt()}%",
                value = arrowSize,
                onValueChange = { arrowSize = it; save() },
                range = 0.5f..1.5f
            )
        }
    }
}

@Composable
fun FloatingGlucoseSettingsScreen(
    navController: NavController,
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val repository = remember { viewModel.floatingRepository }

    val isEnabled by repository.isEnabled.collectAsState(initial = false)
    val isTransparent by repository.isTransparent.collectAsState(initial = false)
    val showSecondary by repository.showSecondary.collectAsState(initial = false)
    val fontSource by repository.fontSource.collectAsState(initial = "APP")
    val fontSize by repository.fontSize.collectAsState(initial = 16f)
    val fontWeight by repository.fontWeight.collectAsState(initial = "REGULAR")
    val showArrow by repository.showArrow.collectAsState(initial = true)
    val cornerRadius by repository.cornerRadius.collectAsState(initial = 28f)
    val opacity by repository.backgroundOpacity.collectAsState(initial = 0.6f)
    val isDynamicIsland by repository.isDynamicIslandEnabled.collectAsState(initial = false)
    val verticalOffset by repository.islandVerticalOffset.collectAsState(initial = 0f)
    val manualGap by repository.islandGap.collectAsState(initial = 0f)
    val useSubtleOutline by repository.useSubtleOutline.collectAsState(initial = false)
    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = Settings.canDrawOverlays(context)
    }

    LegacySettingsScaffold(navController = navController, title = stringResource(R.string.floatglucose)) {
        MasterSwitchCard(
            title = stringResource(R.string.enable_overlay),
            subtitle = if (hasPermission) "Display over other apps" else "Overlay permission required",
            checked = isEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !hasPermission) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    viewModel.toggleFloatingGlucose(enabled)
                }
            },
            icon = Icons.Default.Layers,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (isEnabled && !hasPermission) {
            Spacer(Modifier.height(16.dp))
            WarningPanel(
                text = stringResource(R.string.floating_permission_required),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.appearance), topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.background),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                FilterChip(
                    selected = !isTransparent,
                    onClick = { repository.setTransparent(false) },
                    label = { Text(stringResource(R.string.filled)) }
                )
                FilterChip(
                    selected = isTransparent,
                    onClick = { repository.setTransparent(true) },
                    label = { Text(stringResource(R.string.transparent)) }
                )
        }

        if (!isTransparent) {
            Spacer(modifier = Modifier.height(8.dp))
            LegacySliderControl(
                label = stringResource(R.string.corner_radius_dp, cornerRadius.toInt()),
                value = cornerRadius,
                onValueChange = { repository.setCornerRadius(it) },
                range = 0f..48f,
                steps = 24
            )
            Spacer(modifier = Modifier.height(8.dp))
            LegacySliderControl(
                label = stringResource(R.string.background_opacity_percent, (opacity * 100).toInt()),
                value = opacity,
                onValueChange = { repository.setBackgroundOpacity(it) },
                range = 0.1f..1.0f,
                steps = 18
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        SettingsSwitchItem(
            title = "Use subtle outline",
            subtitle = if (useSubtleOutline) "Thin edge highlight" else "Soft shadow around overlay",
            checked = useSubtleOutline,
            onCheckedChange = { repository.setUseSubtleOutline(it) },
            position = CardPosition.SINGLE,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
        SectionLabel(stringResource(R.string.position_layout), topPadding = 16.dp, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(8.dp))

        SettingsSwitchItem(
            title = stringResource(R.string.dynamic_island_mode),
            subtitle = stringResource(R.string.dynamic_island_desc),
            checked = isDynamicIsland,
            onCheckedChange = { repository.setDynamicIslandEnabled(it) },
            position = CardPosition.SINGLE,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (isDynamicIsland) {
            Spacer(modifier = Modifier.height(8.dp))
            LegacySliderControl(
                label = stringResource(R.string.vertical_offset_dp, verticalOffset.toInt()),
                value = verticalOffset,
                onValueChange = { repository.setIslandVerticalOffset(it) },
                range = 0f..50f,
                steps = 50
            )

            Spacer(modifier = Modifier.height(4.dp))
            val gapDisplay = if (manualGap == 0f) stringResource(R.string.auto) else stringResource(R.string.dp_value, manualGap.toInt())
            LegacySliderControl(
                label = stringResource(R.string.gap_width_value, gapDisplay),
                value = manualGap,
                onValueChange = { repository.setIslandGap(it) },
                range = 0f..200f,
                steps = 40
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.metrics), topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
            SettingsSwitchItem(
                title = stringResource(R.string.display_secondary_values),
                subtitle = stringResource(R.string.display_secondary_values_desc),
                checked = showSecondary,
                onCheckedChange = { repository.setShowSecondary(it) },
                position = CardPosition.TOP
            )
            SettingsSwitchItem(
                title = stringResource(R.string.show_trend_arrow),
                checked = showArrow,
                onCheckedChange = { repository.setShowArrow(it) },
                position = CardPosition.BOTTOM
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.typography), topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.font_source),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                FilterChip(
                    selected = fontSource == "APP",
                    onClick = { repository.setFontSource("APP") },
                    label = { Text(stringResource(R.string.font_app_plex)) }
                )
                FilterChip(
                    selected = fontSource == "SYSTEM",
                    onClick = { repository.setFontSource("SYSTEM") },
                    label = { Text(stringResource(R.string.font_system_sans)) }
                )
        }

        Spacer(modifier = Modifier.height(24.dp))
        LegacySliderControl(
            label = stringResource(R.string.size_sp, fontSize.toInt()),
            value = fontSize,
            onValueChange = { repository.setFontSize(it) },
            range = 12f..48f,
            steps = 35
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.font_weight_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("LIGHT", "REGULAR", "MEDIUM").forEach { weight ->
                val labelRes = when (weight) {
                    "LIGHT" -> R.string.theme_light
                    "REGULAR" -> R.string.regular
                    else -> R.string.medium
                }
                FilterChip(
                    selected = fontWeight == weight,
                    onClick = { repository.setFontWeight(weight) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
    }
}

@Composable
fun AodSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
    }
    var serviceEnabled by remember { mutableStateOf(isAodAccessibilityEnabled(context)) }

    var opacity by rememberSaveable { mutableFloatStateOf(prefs.getFloat("aod_opacity", 1.0f)) }
    var textScale by rememberSaveable { mutableFloatStateOf(prefs.getFloat("aod_text_scale", 1.5f)) }
    var chartScale by rememberSaveable { mutableFloatStateOf(prefs.getFloat("aod_chart_scale", 1.5f)) }
    var showChart by rememberSaveable { mutableStateOf(prefs.getBoolean("aod_show_chart", true)) }
    var showArrow by rememberSaveable { mutableStateOf(prefs.getBoolean("aod_show_arrow", true)) }
    var showSecondary by rememberSaveable { mutableStateOf(prefs.getBoolean("aod_show_secondary", false)) }
    var arrowScale by rememberSaveable { mutableFloatStateOf(prefs.getFloat("aod_arrow_scale", 1.0f)) }
    var positions by rememberSaveable { mutableStateOf(prefs.getStringSet("aod_positions", setOf("TOP")) ?: setOf("TOP")) }
    var alignment by rememberSaveable { mutableStateOf(prefs.getString("aod_alignment", "CENTER") ?: "CENTER") }
    var fontSource by rememberSaveable { mutableStateOf(prefs.getString("aod_font_source", "APP") ?: "APP") }
    var fontWeight by rememberSaveable { mutableIntStateOf(prefs.getInt("aod_font_weight", 400)) }

    fun save() {
        prefs.edit()
            .putFloat("aod_opacity", opacity)
            .putFloat("aod_text_scale", textScale)
            .putFloat("aod_chart_scale", chartScale)
            .putBoolean("aod_show_chart", showChart)
            .putBoolean("aod_show_arrow", showArrow)
            .putBoolean("aod_show_secondary", showSecondary)
            .putFloat("aod_arrow_scale", arrowScale)
            .putStringSet("aod_positions", positions)
            .putString("aod_alignment", alignment)
            .putString("aod_font_source", fontSource)
            .putInt("aod_font_weight", fontWeight)
            .apply()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceEnabled = isAodAccessibilityEnabled(context)
    }

    LegacySettingsScaffold(navController = navController, title = "AOD Settings") {
        MasterSwitchCard(
            title = "Enable Overlay service",
            subtitle = if (serviceEnabled) "Accessibility service enabled" else "Open Accessibility settings",
            checked = serviceEnabled,
            onCheckedChange = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            icon = Icons.Default.SettingsAccessibility,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.visuals),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Text(
            stringResource(R.string.font_source),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = fontSource == "APP",
                onClick = { fontSource = "APP"; save() },
                label = { Text(stringResource(R.string.font_app_plex)) }
            )
            FilterChip(
                selected = fontSource == "SYSTEM",
                onClick = { fontSource = "SYSTEM"; save() },
                label = { Text(stringResource(R.string.font_system_google_sans)) }
            )
        }

        Text(
            stringResource(R.string.font_weight_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                300 to stringResource(R.string.theme_light),
                400 to stringResource(R.string.regular),
                500 to stringResource(R.string.medium)
            ).forEach { (weight, label) ->
                FilterChip(
                    selected = fontWeight == weight,
                    onClick = { fontWeight = weight; save() },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Layout", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            stringResource(R.string.active_positions_randomized),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("TOP", "CENTER", "BOTTOM").forEach { pos ->
                FilterChip(
                    selected = positions.contains(pos),
                    onClick = {
                        val newSet = positions.toMutableSet()
                        if (newSet.contains(pos)) {
                            if (newSet.size > 1) newSet.remove(pos)
                        } else {
                            newSet.add(pos)
                        }
                        positions = newSet
                        save()
                    },
                    label = { Text(pos) },
                    leadingIcon = if (positions.contains(pos)) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.text_alignment),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("LEFT", "CENTER", "RIGHT").forEach { align ->
                FilterChip(
                    selected = alignment == align,
                    onClick = { alignment = align; save() },
                    label = { Text(align) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Appearance", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
            SettingsSwitchItem(
                title = "Show Chart",
                checked = showChart,
                onCheckedChange = { showChart = it; save() },
                icon = null,
                position = CardPosition.TOP
            )
            SettingsSwitchItem(
                title = "Show Trend Arrow",
                checked = showArrow,
                onCheckedChange = { showArrow = it; save() },
                icon = null,
                position = CardPosition.MIDDLE
            )
            SettingsSwitchItem(
                title = stringResource(R.string.display_secondary_values),
                subtitle = stringResource(R.string.display_secondary_values_desc),
                checked = showSecondary,
                onCheckedChange = { showSecondary = it; save() },
                icon = null,
                position = CardPosition.BOTTOM
            )
        }

        LegacySliderControl(
            label = "Opacity: ${(opacity * 100).toInt()}%",
            value = opacity,
            onValueChange = { opacity = it; save() },
            range = 0.1f..1.0f
        )
        LegacySliderControl(
            label = "Text Size: ${(textScale * 100).toInt()}%",
            value = textScale,
            onValueChange = { textScale = it; save() },
            range = 0.5f..6.0f
        )
        if (showChart) {
            LegacySliderControl(
                label = "Chart Size: ${(chartScale * 100).toInt()}%",
                value = chartScale,
                onValueChange = { chartScale = it; save() },
                range = 0.5f..2.0f
            )
        }
        if (showArrow) {
            LegacySliderControl(
                label = "Arrow Size: ${(arrowScale * 100).toInt()}%",
                value = arrowScale,
                onValueChange = { arrowScale = it; save() },
                range = 0.5f..2.0f
            )
        }
    }
}

@Composable
private fun LegacySettingsScaffold(
    navController: NavController,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.Top,
            content = content
        )
    }
}

@Composable
private fun LegacySliderControl(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun WarningPanel(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun isAodAccessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
        info.resolveInfo?.serviceInfo?.packageName == context.packageName &&
            info.resolveInfo?.serviceInfo?.name == tk.glucodata.accessibility.AODOverlayService::class.java.name
    }
}
