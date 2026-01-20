@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import tk.glucodata.ui.util.findActivity
import tk.glucodata.ui.util.fullRestart
import tk.glucodata.ui.util.hardRestart
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.SensorBluetooth
import tk.glucodata.ui.components.StyledSwitch
import tk.glucodata.ui.theme.labelLargeExpressive
import tk.glucodata.ui.viewmodel.DashboardViewModel
//import tk.glucodata.ui.components.ExportDataDialog
import java.util.Locale

/**
 * M3 Expressive Settings Screen
 *
 * Cards in the same section are connected with:
 * - First card: rounded top corners only
 * - Middle cards: no corners (squared)
 * - Last card: rounded bottom corners only
 * - 2dp gap between cards in a group
 */
@Composable
fun ExpressiveSettingsScreen(
    navController: NavController,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // States
    val unit by viewModel.unit.collectAsState()
    val isMmol = unit == "mmol/L"
    val patchedLibreEnabled by viewModel.patchedLibreBroadcastEnabled.collectAsState()
    val notificationChartEnabled by viewModel.notificationChartEnabled.collectAsState()

    val hasLowAlarm by viewModel.hasLowAlarm.collectAsState()
    val lowAlarmValue by viewModel.lowAlarmThreshold.collectAsState()
    val lowAlarmSoundMode by viewModel.lowAlarmSoundMode.collectAsState()
    val hasHighAlarm by viewModel.hasHighAlarm.collectAsState()
    val highAlarmValue by viewModel.highAlarmThreshold.collectAsState()
    val highAlarmSoundMode by viewModel.highAlarmSoundMode.collectAsState()
    val targetLowValue by viewModel.targetLow.collectAsState()
    val targetHighValue by viewModel.targetHigh.collectAsState()

    // Dialog states
    var showUnitDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showFactoryResetDialog by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAODSettingsSheet by remember { mutableStateOf(false) }
    var showNotificationSettingsSheet by remember { mutableStateOf(false) }



    // Advanced settings
    var googleScan by remember { mutableStateOf(Natives.getGoogleScan()) }
    var turbo by remember { mutableStateOf(Natives.getpriority()) }
    var autoConnect by remember { mutableStateOf(Natives.getAndroid13()) }

    val currentLocale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
    val currentLangName = currentLocale.displayLanguage.replaceFirstChar { it.uppercase() }

    val themeLabel = when(themeMode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(), // Add status bar padding
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
    ) {
        // Title
        item(key = "title") {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 16.dp, bottom = 24.dp)
            )
        }     // === GENERAL SECTION ===
//        item(key = "general_label") { SectionLabel(stringResource(R.string.general_settings), topPadding = 0.dp) }

        item(key = "general_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsItem(
                    title = stringResource(R.string.unit),
                    subtitle = unit,
                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.List,
                    position = CardPosition.TOP,
                    onClick = { showUnitDialog = true }
                )
                SettingsItem(
                    title = stringResource(R.string.theme_title),
                    subtitle = themeLabel,
                    icon = if(themeMode == ThemeMode.LIGHT) Icons.Default.LightMode else Icons.Default.DarkMode,
                    position = CardPosition.MIDDLE,
                    onClick = { showThemeDialog = true }
                )

                SettingsItem(
                    title = stringResource(R.string.languagename),
                    subtitle = currentLangName,
                    icon = Icons.Default.Language,
                    position = CardPosition.BOTTOM,
                    onClick = { showLanguageDialog = true }
                )
            }
        }

        // === NOTIFICATIONS ===
        item(key = "notif_label") { SectionLabel(stringResource(R.string.notifications)) }

        item(key = "notif_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

                SettingsItem(
                    title = "Notification Settings",
                    subtitle = "Customize notification shade",
                    icon = Icons.Default.ClearAll,
                    position = CardPosition.TOP,
                    onClick = { showNotificationSettingsSheet = true }
                )
                SettingsItem(
                    title = "Lock Screen (AOD)",
                    subtitle = "Customize always-on display",
                    icon = Icons.Default.Visibility,
                    position = CardPosition.MIDDLE,
                    onClick = { showAODSettingsSheet = true }
                )
                SettingsItem(
                    title = stringResource(R.string.glucose_alerts_title),
                    subtitle = "Low/High, predictive and others",
//                    subtitle = buildString {
//                        val alertsEnabled = mutableListOf<String>()
//                        if (hasLowAlarm) alertsEnabled.add("Low")
//                        if (hasHighAlarm) alertsEnabled.add("High")
//                        if (alertsEnabled.isEmpty()) "No alerts enabled"
//                        else "${alertsEnabled.joinToString(", ")} alerts enabled"
//                    },
                    icon = Icons.Default.AddAlert,
                    showArrow = true,
                    position = CardPosition.BOTTOM,
                    onClick = { navController.navigate("settings/alerts") }
                )
            }
        }
        // === ALERTS ===

        item(key = "alerts_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

            }
        }
        // === EXCHANGES ===
        item(key = "exchange_label") { SectionLabel(stringResource(R.string.exchanges)) }

        item(key = "exchange_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.xdripbroadcast),
                    subtitle = stringResource(R.string.patchedlibrebroadcast),
                    checked = patchedLibreEnabled,
                    icon = Icons.Default.Share, // Using Share icon as placeholder for Broadcast
                    position = CardPosition.TOP,
                    onCheckedChange = { viewModel.togglePatchedLibreBroadcast(it) }
                )
                SettingsItem(
                    title = stringResource(R.string.mirror),
                    subtitle = stringResource(R.string.mirror_desc),
                    showArrow = true,
                    icon = Icons.Default.Devices,
                    position = CardPosition.MIDDLE,
                    onClick = { navController.navigate("settings/mirror") }
                )
                SettingsItem(
                    title = stringResource(R.string.nightscout_config),
                    subtitle = stringResource(R.string.nightscout_desc),
                    showArrow = true,
                    icon = Icons.Default.CloudUpload,
                    position = CardPosition.BOTTOM,
                    onClick = { navController.navigate("settings/nightscout") }
                )
            }
        }




        // === TARGETS ===
        item(key = "targets_label") { SectionLabel(stringResource(R.string.target_range_title)) }

        item(key = "targets_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SliderItem(
                    title = stringResource(R.string.low_label),
                    value = targetLowValue,
                    unit = unit,
                    range = if (isMmol) 2.0f..8.0f else 40f..140f,
                    position = CardPosition.TOP,
                    onValueChange = { viewModel.setTargetLow(it) }
                )
                SliderItem(
                    title = stringResource(R.string.high_label),
                    value = targetHighValue,
                    unit = unit,
                    range = if (isMmol) 6.0f..16.0f else 100f..350f,
                    position = CardPosition.BOTTOM,
                    onValueChange = { viewModel.setTargetHigh(it) }
                )
            }
        }

        // === ADVANCED ===
        item(key = "adv_label") { SectionLabel(stringResource(R.string.advanced_title)) }

        item(key = "adv_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.googlescan),
                    subtitle = stringResource(R.string.google_scan_desc),
                    checked = googleScan,
                    icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                    position = CardPosition.TOP,
                    onCheckedChange = { Natives.setGoogleScan(it); googleScan = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.turbo_title),
                    subtitle = stringResource(R.string.turbo_desc),
                    checked = turbo,
                    icon = Icons.Default.Speed,
                    position = CardPosition.MIDDLE,
                    onCheckedChange = { Natives.setpriority(it); turbo = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.autoconnect_title),
                    subtitle = stringResource(R.string.autoconnect_desc),
                    checked = autoConnect,
                    icon = Icons.Default.Autorenew,
                    position = CardPosition.MIDDLE,
                    onCheckedChange = { SensorBluetooth.setAutoconnect(it); autoConnect = it }
                )
                SettingsItem(
                    title = stringResource(R.string.debug_logs),
                    subtitle = stringResource(R.string.debug_logs_desc),
                    showArrow = true,
                    icon = Icons.Default.BugReport,
                    position = CardPosition.BOTTOM,
                    onClick = { navController.navigate("settings/debug") }
                )
            }
        }

        // === DATA MANAGEMENT ===
        item(key = "data_label") { SectionLabel(stringResource(R.string.data_management)) }

        item(key = "data_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsItem(
                    title = stringResource(R.string.export_data),
                    subtitle = stringResource(R.string.export_data_desc),
                    icon = androidx.compose.material.icons.Icons.Default.CloudUpload,
                    position = CardPosition.TOP,
                    onClick = { showExportDialog = true }
                )
                // Import logic (Room based)
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        if (uri != null) {
                            scope.launch {
                                // Show loading? For now just toast result
                                val result = tk.glucodata.data.HistoryExporter.importFromCsv(context, uri)
                                withContext(Dispatchers.Main) {
                                    val msg = if (result.success) 
                                        "Imported: ${result.successCount} readings. Failed: ${result.failCount}"
                                    else 
                                        "Import Failed: ${result.errorMessage}"
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )

                SettingsItem(
                    title = stringResource(R.string.import_data),
                    subtitle = stringResource(R.string.import_data_desc),
                    icon = Icons.Default.FolderOpen,
                    position = CardPosition.BOTTOM,
                    onClick = { 
                        importLauncher.launch(arrayOf("text/*", "text/csv"))
                    }
                )
            }
        }

        // === DANGER ===
        item(key = "danger_label") { SectionLabel(stringResource(R.string.danger_zone), isError = true) }

        item(key = "danger_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Restart App (Low Severity)
                DangerItem(
                    title = "Restart App",
                    subtitle = "Force restart the application",
                    icon = Icons.Filled.Refresh,
                    position = CardPosition.TOP,
                    severity = DangerSeverity.LOW,
                    onClick = { context.findActivity()?.fullRestart() }
                )
                DangerItem(
                    title = stringResource(R.string.clear_history),
                    subtitle = stringResource(R.string.clear_history_desc_short),
                    icon = Icons.Filled.History,
                    position = CardPosition.MIDDLE,
                    severity = DangerSeverity.LOW,
                    onClick = { showClearHistoryDialog = true }
                )
                DangerItem(
                    title = stringResource(R.string.clear_app_data),
                    subtitle = stringResource(R.string.clear_app_data_desc),
                    icon = Icons.Filled.Delete,
                    position = CardPosition.MIDDLE,
                    severity = DangerSeverity.MEDIUM,
                    onClick = { showClearDataDialog = true }
                )
                DangerItem(
                    title = stringResource(R.string.factory_reset),
                    subtitle = stringResource(R.string.factory_reset_desc),
                    icon = Icons.Filled.Warning,
                    position = CardPosition.BOTTOM,
                    severity = DangerSeverity.HIGH,
                    onClick = { showFactoryResetDialog = true }
                )
            }
        }

        // === ABOUT ===
        item(key = "about") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Dialogs
    if (showUnitDialog) UnitPickerDialog(isMmol, { 
        viewModel.setUnit(it)
        showUnitDialog = false
        context.findActivity()?.hardRestart() 
    }, { showUnitDialog = false })
    if (showThemeDialog) ThemePickerDialog(themeMode, { onThemeChanged(it); showThemeDialog = false }, { showThemeDialog = false })
    if (showLanguageDialog) LanguagePickerDialog { showLanguageDialog = false }
    if (showClearHistoryDialog) ConfirmActionDialog(stringResource(R.string.clean_history_confirm), stringResource(R.string.clear_history_desc_long), Icons.Filled.History, { scope.launch { tk.glucodata.data.DataManagement.clearHistory() }; showClearHistoryDialog = false }, { showClearHistoryDialog = false })
    if (showClearDataDialog) ConfirmActionDialog(
        stringResource(R.string.clean_data_confirm), 
        stringResource(R.string.clear_app_data_desc), 
        Icons.Filled.Delete, 
        { 
            scope.launch { 
                tk.glucodata.data.DataManagement.clearAppData()
                // Full restart to kill process and clear native memory
                context.findActivity()?.fullRestart()
            }
            showClearDataDialog = false 
        }, 
        { showClearDataDialog = false }, 
        isDestructive = true
    )
    if (showFactoryResetDialog) ConfirmActionDialog(
        stringResource(R.string.factory_reset_confirm), 
        stringResource(R.string.factory_reset_alert), 
        Icons.Filled.Warning, 
        { 
            scope.launch { 
                tk.glucodata.data.DataManagement.factoryReset()
                // Full restart to kill process and clear native memory
                context.findActivity()?.fullRestart()
            }
        }, 
        { showFactoryResetDialog = false }, 
        isDestructive = true
    )
    
    if (showExportDialog) {
        val sheetState = rememberModalBottomSheetState()
        ExportDataSheet(
            onDismiss = { showExportDialog = false },
            sheetState = sheetState
        )
    }

    // ... Bottom of function ...

    if (showAODSettingsSheet) {
        val sheetState = rememberModalBottomSheetState()
        AODSettingsSheet(
            onDismiss = { showAODSettingsSheet = false },
            sheetState = sheetState,
            context = context
        )
    }

    if (showNotificationSettingsSheet) {
        val sheetState = rememberModalBottomSheetState()
        NotificationSettingsSheet(
            onDismiss = { showNotificationSettingsSheet = false },
            sheetState = sheetState,
            context = context,
            viewModel = viewModel
        )
    }
}

@Composable
fun NotificationSettingsSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    context: android.content.Context,
    viewModel: DashboardViewModel
) {
    val prefs = context.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
    val notificationChartEnabled by viewModel.notificationChartEnabled.collectAsState()
    
    // Font Settings
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("notification_font_size", 1.0f)) }
    var fontWeight by remember { mutableIntStateOf(prefs.getInt("notification_font_weight", 400)) }
    
    // Arrow Settings
    var showArrow by remember { mutableStateOf(prefs.getBoolean("notification_show_arrow", true)) }
    var arrowSize by remember { mutableFloatStateOf(prefs.getFloat("notification_arrow_size", 1.0f)) }
    
    // Visibility Toggles
    var showStatus by remember { mutableStateOf(prefs.getBoolean("notification_show_status", true)) }
    var collapsedChart by remember { mutableStateOf(prefs.getBoolean("notification_chart_collapsed", false)) }
    var showTargetRange by remember { mutableStateOf(prefs.getBoolean("notification_chart_target_range", true)) }
    
    val scope = rememberCoroutineScope()
    fun save() {
        scope.launch {
            prefs.edit()
                 .putFloat("notification_font_size", fontSize)
                 .putInt("notification_font_weight", fontWeight)
                 .putBoolean("notification_show_arrow", showArrow)
                 .putFloat("notification_arrow_size", arrowSize)
                 .putBoolean("notification_show_status", showStatus)
                 .putBoolean("notification_chart_collapsed", collapsedChart)
                 .putBoolean("notification_chart_target_range", showTargetRange)
                 .apply()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Notification Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            // === FONT SECTION ===
            SectionLabel("Text", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))

            SliderControl(
                label = "Font Size: ${(fontSize * 100).toInt()}%",
                value = fontSize,
                onValueChange = { fontSize = it; save() },
                range = 0.6f..1.5f
            )

            // Font Weight - only on Android 12+ (API 31) where RemoteViews supports setFontVariationSettings
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                Text("Font Weight", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                     // Simplified Options: Regular (400) and Medium (500)
                     FilterChip(
                         selected = fontWeight == 400,
                         onClick = { fontWeight = 400; save() },
                         label = { Text("Regular") }
                     )
                     FilterChip(
                         selected = fontWeight == 500,
                         onClick = { fontWeight = 500; save() },
                         label = { Text("Medium") }
                     )
                }
            }

            // === ARROW SECTION ===
            Spacer(Modifier.height(16.dp))
            SectionLabel("Trend Arrow", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))

            SettingsSwitchItem(
                title = "Show Arrow",
                checked = showArrow,
                onCheckedChange = { showArrow = it; save() },
                icon = null,
                position = CardPosition.SINGLE
            )

            if (showArrow) {
                SliderControl(
                    label = "Arrow Size: ${(arrowSize * 100).toInt()}%",
                    value = arrowSize,
                    onValueChange = { arrowSize = it; save() },
                    range = 0.5f..1.5f
                )
            }

            // === VISIBILITY SECTION ===
            Spacer(Modifier.height(16.dp))
            SectionLabel("Elements", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))

            SettingsSwitchItem(
                title = "Show Status Text",
                subtitle = "Sensor connection status",
                checked = showStatus,
                onCheckedChange = { showStatus = it; save() },
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
    }
}

@Composable
fun AODSettingsSheet(onDismiss: () -> Unit, sheetState: SheetState, context: android.content.Context) {
    val prefs = context.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
    
    var opacity by remember { mutableFloatStateOf(prefs.getFloat("aod_opacity", 1.0f)) }
    var textScale by remember { mutableFloatStateOf(prefs.getFloat("aod_text_scale", 1.5f)) }
    var chartScale by remember { mutableFloatStateOf(prefs.getFloat("aod_chart_scale", 1.5f)) }
    var showChart by remember { mutableStateOf(prefs.getBoolean("aod_show_chart", true)) }
    
    // Position: Multi-select stored as Set<String>
    var positions by remember { 
        mutableStateOf(prefs.getStringSet("aod_positions", setOf("TOP")) ?: setOf("TOP")) 
    }

    // Alignment: Single select (LEFT, CENTER, RIGHT)
    var alignment by remember { mutableStateOf(prefs.getString("aod_alignment", "CENTER") ?: "CENTER") }

    // FONT VISUALS
    var fontSource by remember { mutableStateOf(prefs.getString("aod_font_source", "APP") ?: "APP") }
    var fontWeight by remember { mutableIntStateOf(prefs.getInt("aod_font_weight", 400)) }

    // Auto-save logic scope
    val scope = rememberCoroutineScope()
    fun save() {
        scope.launch {
            prefs.edit()
                 .putFloat("aod_opacity", opacity)
                 .putFloat("aod_text_scale", textScale)
                 .putFloat("aod_chart_scale", chartScale)
                 .putBoolean("aod_show_chart", showChart)
                 .putStringSet("aod_positions", positions)
                 .putString("aod_alignment", alignment)
                 .putString("aod_font_source", fontSource)
                 .putInt("aod_font_weight", fontWeight)
                 .apply()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "AOD Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            // Enable Button
            SettingsItem(
                title = "Enable Overlay service",
                subtitle = "Opens Accessibility Settings",
                icon = Icons.Default.SettingsAccessibility,
                position = CardPosition.SINGLE,
                onClick = {
                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // === VISUALS SECTION ===
            Text("Visuals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            // FONT SOURCE
            Text("Font Source", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                 FilterChip(
                     selected = fontSource == "APP",
                     onClick = { fontSource = "APP"; save() },
                     label = { Text("App (IBM Plex)") }
                 )
                 FilterChip(
                     selected = fontSource == "SYSTEM",
                     onClick = { fontSource = "SYSTEM"; save() },
                     label = { Text("System (Google Sans)") }
                 )
            }

            // FONT WEIGHT
            Text("Font Weight", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                 FilterChip(
                     selected = fontWeight == 300,
                     onClick = { fontWeight = 300; save() },
                     label = { Text("Light") }
                 )
                 FilterChip(
                     selected = fontWeight == 400,
                     onClick = { fontWeight = 400; save() },
                     label = { Text("Regular") }
                 )
                 FilterChip(
                     selected = fontWeight == 500,
                     onClick = { fontWeight = 500; save() },
                     label = { Text("Medium") }
                 )
            }
            
            Spacer(Modifier.height(16.dp))
            SectionLabel("Layout", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))

            // Multi-Position Selection
            Text("Active Positions (Randomized)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                 listOf("TOP", "CENTER", "BOTTOM").forEach { pos ->
                     FilterChip(
                         selected = positions.contains(pos),
                         onClick = { 
                             val newSet = positions.toMutableSet()
                             if (newSet.contains(pos)) {
                                 if (newSet.size > 1) newSet.remove(pos) // Prevent empty set
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

            // Alignment Selection
            Text("Text Alignment", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                 listOf("LEFT", "CENTER", "RIGHT").forEach { align ->
                     FilterChip(
                         selected = alignment == align,
                         onClick = { alignment = align; save() },
                         label = { Text(align) },
                         leadingIcon = null // Radio behavior essentially
                     )
                 }
            }
            
            Spacer(Modifier.height(24.dp))
            SectionLabel("Appearance", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))

            // Show Chart Toggle
            SettingsSwitchItem(
                title = "Show Chart",
                checked = showChart,
                onCheckedChange = { showChart = it; save() },
                icon = null,
                position = CardPosition.SINGLE
            )

            // Opacity
            SliderControl(
                label = "Opacity: ${(opacity * 100).toInt()}%",
                value = opacity,
                onValueChange = { opacity = it; save() },
                range = 0.1f..1.0f
            )

            // Text Scale
            SliderControl(
                label = "Text Size: ${(textScale * 100).toInt()}%",
                value = textScale,
                onValueChange = { textScale = it; save() },
                range = 0.5f..3.0f
            )

            // Chart Scale
            if (showChart) {
                SliderControl(
                    label = "Chart Size: ${(chartScale * 100).toInt()}%",
                    value = chartScale,
                    onValueChange = { chartScale = it; save() },
                    range = 0.5f..2.0f
                )
            }
        }
    }
}

@Composable
fun SliderControl(label: String, value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun SectionLabel(text: String, isError: Boolean = false, topPadding: Dp = 24.dp, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = topPadding, bottom = 8.dp)
    )
}







// ============================================================================
// CARD POSITION - determines corner radius
// ============================================================================

enum class CardPosition {
    SINGLE, // All corners rounded
    TOP,    // Top corners rounded
    MIDDLE, // No corners rounded
    BOTTOM  // Bottom corners rounded
}

private fun cardShape(position: CardPosition, radius: Dp = 12.dp): RoundedCornerShape {
    return when (position) {
        CardPosition.SINGLE -> RoundedCornerShape(radius)
        CardPosition.TOP -> RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 4.dp, bottomEnd = 4.dp)
        CardPosition.MIDDLE -> RoundedCornerShape(4.dp)
        CardPosition.BOTTOM -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = radius, bottomEnd = radius)
    }
}



@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.SINGLE, // Added position
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh // restored color
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp) // M3 Two-line item standard
                .padding(horizontal = 16.dp, vertical = 16.dp), // Coherent spacing
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp).size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium) // Heavier weight (500) for legibility
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (showArrow) {
                Icon(
                    Icons.Filled.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    position: CardPosition = CardPosition.SINGLE
) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            StyledSwitch(
                checked = checked,
                onCheckedChange = null // Handled by parent click
            )
        },
        position = position
    )
}

@Composable
fun AlarmItem(
    title: String,
    enabled: Boolean,
    value: Float,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    soundMode: Int,
    onToggle: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onSoundChange: (Int) -> Unit,
    position: CardPosition = CardPosition.SINGLE
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val displayValue = if (sliderValue < 30) String.format("%.1f", sliderValue) else sliderValue.toInt().toString()

    Surface(
        onClick = { onToggle(!enabled) },
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (enabled) {
                        Text("$displayValue $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                StyledSwitch(
                    checked = enabled,
                    onCheckedChange = null
                )
            }

            // Expandable content
            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onValueChange(sliderValue) },
                        valueRange = range
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.alert_mode), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = soundMode == 0,
                            onClick = { onSoundChange(0) },
                            label = { Text(stringResource(R.string.vibrate_only)) },
                            leadingIcon = { if(soundMode == 0) Icon(Icons.Filled.Check, null) }
                        )
                        FilterChip(
                            selected = soundMode == 1,
                            onClick = { onSoundChange(1) },
                            label = { Text(stringResource(R.string.sound_vibrate)) },
                            leadingIcon = { if(soundMode == 1) Icon(Icons.Filled.Check, null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SliderItem(
    title: String,
    value: Float,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    position: CardPosition = CardPosition.SINGLE
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val displayValue = if (sliderValue < 30) String.format("%.1f", sliderValue) else sliderValue.toInt().toString()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("$displayValue $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChange(sliderValue) },
                valueRange = range
            )
        }
    }
}

@Composable
fun DangerItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    position: CardPosition = CardPosition.SINGLE,
    severity: DangerSeverity = DangerSeverity.HIGH
) {
    val (bgColor, contentColor) = when (severity) {
        DangerSeverity.LOW -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
        DangerSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        DangerSeverity.HIGH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape(position),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                tint = contentColor,
                modifier = Modifier.padding(end = 16.dp).size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.7f))
            }
        }
    }
}

enum class DangerSeverity { LOW, MEDIUM, HIGH }

// ============================================================================
// DIALOGS - M3 Expressive Style with proper layout
// ============================================================================

@Composable
private fun UnitPickerDialog(isMmol: Boolean, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.select_unit),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                listOf(stringResource(R.string.unit_mg) to 0, stringResource(R.string.unit_mmol) to 1).forEach { (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onSelect(value) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (if (isMmol) 1 else 0) == value, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun ThemePickerDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.choose_theme),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                ThemeMode.values().forEach { mode ->
                    val label = when(mode) { ThemeMode.SYSTEM -> stringResource(R.string.theme_system); ThemeMode.LIGHT -> stringResource(R.string.theme_light); ThemeMode.DARK -> stringResource(R.string.theme_dark) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == mode, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    val languages = listOf(
        "System" to null,
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
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.select_language),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    languages.forEach { (name, tag) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    val locale = if (tag != null) LocaleListCompat.forLanguageTags(tag) else LocaleListCompat.getEmptyLocaleList()
                                    AppCompatDelegate.setApplicationLocales(locale)
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if ((tag == null && AppCompatDelegate.getApplicationLocales().isEmpty) ||
                                (tag != null && AppCompatDelegate.getApplicationLocales().toLanguageTags().contains(tag))) {
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null, tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()
            ) { Text(if (isDestructive) stringResource(R.string.delete) else stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ExportDataSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var daysText by remember { mutableStateOf("30") }
    var isExporting by remember { mutableStateOf(false) }
    
    // Launchers for creating files
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri -> 
            if (uri != null) {
                isExporting = true
                scope.launch {
                    val isMmol = Natives.getunit() == 1
                    val days = daysText.toLongOrNull() ?: 30L
                    val startTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
                    
                    // Fetch data from Room
                    val data = tk.glucodata.data.HistoryRepository(context).getHistory(startTime, isMmol)
                    val unit = if (isMmol) "mmol/L" else "mg/dL"
                    
                    val success = tk.glucodata.data.HistoryExporter.exportToCsv(context, uri, data, unit)
                    
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        Toast.makeText(context, if (success) "Export Successful" else "Export Failed", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            }
        }
    )
    
    val textLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri -> 
            if (uri != null) {
                isExporting = true
                scope.launch {
                    val isMmol = Natives.getunit() == 1
                    val days = daysText.toLongOrNull() ?: 30L
                    val startTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
                    
                    val data = tk.glucodata.data.HistoryRepository(context).getHistory(startTime, isMmol)
                    val unit = if (isMmol) "mmol/L" else "mg/dL"
                    
                    val success = tk.glucodata.data.HistoryExporter.exportToReadable(context, uri, data, unit)
                    
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        Toast.makeText(context, if (success) "Export Successful" else "Export Failed", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp) // Add bottom padding for better touch area
        ) {
            Text(
                text = stringResource(R.string.export_data),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isExporting) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                 OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text(stringResource(R.string.days_to_export)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            val fileName = "Juggluco_Export_${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(System.currentTimeMillis())}.csv"
                            csvLauncher.launch(fileName) 
                        }, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.List, null, modifier = Modifier.padding(end = 8.dp))
                        Text("Export complete CSV") 
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            val fileName = "Juggluco_Report_${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(System.currentTimeMillis())}.txt"
                            textLauncher.launch(fileName) 
                        }, 
                        modifier = Modifier.fillMaxWidth()
                    ) { 
                         Icon(androidx.compose.material.icons.Icons.Default.Info, null, modifier = Modifier.padding(end = 8.dp))
                         Text("Export readable report") 
                    }
                }
            }
        }
    }
}
