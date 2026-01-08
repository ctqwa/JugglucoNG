@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.SensorBluetooth
import tk.glucodata.ui.viewmodel.DashboardViewModel
import tk.glucodata.ui.components.StyledSwitch
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
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
        
        // === GENERAL SECTION ===
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
            SettingsSwitchItem(
                title = stringResource(R.string.notification_chart_title),
                subtitle = stringResource(R.string.notification_chart_desc),
                checked = notificationChartEnabled,
                icon = Icons.Default.Notifications,
                position = CardPosition.SINGLE,
                onCheckedChange = { viewModel.toggleNotificationChart(it) }
            )
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
        
        // === ALERTS ===
        item(key = "alerts_label") { SectionLabel(stringResource(R.string.glucose_alerts_title)) }
        
        item(key = "alerts_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AlarmItem(
                    title = stringResource(R.string.lowglucosealarm),
                    enabled = hasLowAlarm,
                    value = lowAlarmValue,
                    unit = unit,
                    range = if (isMmol) 2.0f..6.0f else 36f..108f,
                    soundMode = lowAlarmSoundMode,
                    position = CardPosition.TOP,
                    onToggle = { viewModel.setLowAlarm(it, lowAlarmValue) },
                    onValueChange = { viewModel.setLowAlarm(true, it) },
                    onSoundChange = { viewModel.setAlarmSound(0, it) }
                )
                AlarmItem(
                    title = stringResource(R.string.highglucosealarm),
                    enabled = hasHighAlarm,
                    value = highAlarmValue,
                    unit = unit,
                    range = if (isMmol) 6.0f..25.0f else 108f..450f,
                    soundMode = highAlarmSoundMode,
                    position = CardPosition.BOTTOM,
                    onToggle = { viewModel.setHighAlarm(it, highAlarmValue) },
                    onValueChange = { viewModel.setHighAlarm(true, it) },
                    onSoundChange = { viewModel.setAlarmSound(1, it) }
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
                    icon = Icons.Default.BluetoothSearching,
                    position = CardPosition.TOP,
                    onCheckedChange = { Natives.setGoogleScan(it); googleScan = it }
                )
                SettingsSwitchItem(
                    title = "Turbo (high priority)",
                    subtitle = "Requests high connection priority",
                    checked = turbo,
                    icon = Icons.Default.Speed,
                    position = CardPosition.MIDDLE,
                    onCheckedChange = { Natives.setpriority(it); turbo = it }
                )
                SettingsSwitchItem(
                    title = "AutoConnect",
                    subtitle = "Uses passive background connection",
                    checked = autoConnect,
                    icon = Icons.Default.Autorenew,
                    position = CardPosition.MIDDLE,
                    onCheckedChange = { SensorBluetooth.setAutoconnect(it); autoConnect = it }
                )
                SettingsItem(
                    title = stringResource(R.string.debug_logs),
                    subtitle = "View trace.log and logcat",
                    showArrow = true,
                    icon = Icons.Default.BugReport,
                    position = CardPosition.BOTTOM,
                    onClick = { navController.navigate("settings/debug") }
                )
            }
        }
        
        // === DANGER ===
        item(key = "danger_label") { SectionLabel("Danger Zone", isError = true) }
        
        item(key = "danger_group") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DangerItem(
                    title = "Clear History",
                    subtitle = "Remove all glucose readings",
                    icon = Icons.Filled.History,
                    position = CardPosition.TOP,
                    onClick = { showClearHistoryDialog = true }
                )
                DangerItem(
                    title = "Clear App Data",
                    subtitle = "Remove history and cache, keep settings",
                    icon = Icons.Filled.Delete,
                    position = CardPosition.MIDDLE,
                    onClick = { showClearDataDialog = true }
                )
                DangerItem(
                    title = "Factory Reset",
                    subtitle = "Remove everything, return to first-run state",
                    icon = Icons.Filled.Warning,
                    position = CardPosition.BOTTOM,
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
                Text("JugglucoNG", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("GPL-3.0 • Fork of Juggluco", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    
    // Dialogs
    if (showUnitDialog) UnitPickerDialog(isMmol, { viewModel.setUnit(it); showUnitDialog = false }, { showUnitDialog = false })
    if (showThemeDialog) ThemePickerDialog(themeMode, { onThemeChanged(it); showThemeDialog = false }, { showThemeDialog = false })
    if (showLanguageDialog) LanguagePickerDialog { showLanguageDialog = false }
    if (showClearHistoryDialog) ConfirmActionDialog("Clear History?", "Remove all glucose readings. Cannot be undone.", Icons.Filled.History, { scope.launch { tk.glucodata.data.DataManagement.clearHistory() }; showClearHistoryDialog = false }, { showClearHistoryDialog = false })
    if (showClearDataDialog) ConfirmActionDialog("Clear App Data?", "Remove history and cache. Settings preserved.", Icons.Filled.Delete, { scope.launch { tk.glucodata.data.DataManagement.clearAppData() }; showClearDataDialog = false }, { showClearDataDialog = false }, isDestructive = true)
    if (showFactoryResetDialog) ConfirmActionDialog("Factory Reset?", "Remove EVERYTHING. Cannot be undone!", Icons.Filled.Warning, { scope.launch { tk.glucodata.data.DataManagement.factoryReset(); (context as? Activity)?.finishAffinity() } }, { showFactoryResetDialog = false }, isDestructive = true)
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

private fun cardShape(position: CardPosition, radius: Dp = 16.dp): RoundedCornerShape {
    return when (position) {
        CardPosition.SINGLE -> RoundedCornerShape(radius)
        CardPosition.TOP -> RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 4.dp, bottomEnd = 4.dp)
        CardPosition.MIDDLE -> RoundedCornerShape(4.dp)
        CardPosition.BOTTOM -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = radius, bottomEnd = radius)
    }
}

@Composable
private fun SectionLabel(text: String, isError: Boolean = false, topPadding: Dp = 24.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = topPadding, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.SINGLE // Added position
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh // restored color
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp), // Reverted vertical padding to 12dp standard
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
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
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
                    Text("Alert Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = soundMode == 0,
                            onClick = { onSoundChange(0) },
                            label = { Text("Vibrate") },
                            leadingIcon = { if(soundMode == 0) Icon(Icons.Filled.Check, null) }
                        )
                        FilterChip(
                            selected = soundMode == 1,
                            onClick = { onSoundChange(1) },
                            label = { Text("Sound & Vibrate") },
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
    position: CardPosition = CardPosition.SINGLE
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape(position),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                null, 
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 16.dp).size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
            }
        }
    }
}

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
                    text = "Select Unit",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                listOf("mg/dL" to 0, "mmol/L" to 1).forEach { (label, value) ->
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
                    TextButton(onClick = onDismiss) { Text("Cancel") }
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
                    text = "Choose Theme",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                ThemeMode.values().forEach { mode ->
                    val label = when(mode) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }
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
                    TextButton(onClick = onDismiss) { Text("Cancel") }
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
                    text = "Select Language",
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
                    TextButton(onClick = onDismiss) { Text("Cancel") }
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
            ) { Text(if (isDestructive) "Delete" else "Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
