@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui.alerts

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import tk.glucodata.Applic
import tk.glucodata.Notify
import tk.glucodata.R
import tk.glucodata.alerts.*
import tk.glucodata.ui.components.StyledSwitch
import tk.glucodata.ui.theme.displayLargeExpressive
import tk.glucodata.ui.theme.labelLargeExpressive
import tk.glucodata.ui.util.ConnectedButtonGroup

/**
 * State-of-the-art Alert Settings Screen with Material 3 Expressive design.
 * Inspired by xDrip+ best practices.
 */
@Composable
fun AlertSettingsScreen(
    navController: NavController
) {
    val isMmol = Applic.unit == 1
    
    // Load all alert configs
    val configs = remember {
        mutableStateMapOf<AlertType, AlertConfig>().apply {
            AlertType.entries.forEach { put(it, AlertRepository.loadConfig(it)) }
        }
    }

    // Group alerts by category
    val glucoseAlerts = remember {
        listOf(
            AlertType.VERY_LOW,
            AlertType.LOW,
            AlertType.HIGH,
            AlertType.VERY_HIGH
        )
    }

    val predictiveAlerts = remember {
        listOf(
            AlertType.PRE_LOW,
            AlertType.PRE_HIGH,
            AlertType.PERSISTENT_HIGH
        )
    }

    val otherAlerts = remember {
        listOf(
            AlertType.MISSED_READING,
            AlertType.LOSS,
            AlertType.SENSOR_EXPIRY
        )
    }

    // Track expanded states
    var expandedType by remember { mutableStateOf<AlertType?>(null) }
    // Track sound picker state
    var showSoundPicker by remember { mutableStateOf<AlertType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.glucose_alerts_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // === GLUCOSE ALERTS SECTION ===
            item {
                SectionHeader(
                    title = "Glucose Alerts",
                    icon = Icons.Default.Bloodtype
                )
            }

            items(glucoseAlerts, key = { it.name }) { type ->
                val config = configs[type] ?: return@items
                AlertCard(
                    config = config,
                    isMmol = isMmol,
                    isExpanded = expandedType == type,
                    position = getCardPosition(type, glucoseAlerts),
                    onToggle = { enabled ->
                        val updated = config.copy(enabled = enabled)
                        configs[type] = updated
                        AlertRepository.saveConfig(updated)
                    },
                    onExpand = { expandedType = if (expandedType == type) null else type },
                    onConfigChange = { updated ->
                        configs[type] = updated
                        AlertRepository.saveConfig(updated)
                    },
                    onPickSound = { showSoundPicker = type }
                )
            }

            // === PREDICTIVE ALERTS SECTION ===
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(
                    title = "Predictive Alerts",
                    icon = Icons.AutoMirrored.Filled.TrendingDown
                )
            }

            items(predictiveAlerts, key = { it.name }) { type ->
                val config = configs[type] ?: return@items
                AlertCard(
                    config = config,
                    isMmol = isMmol,
                    isExpanded = expandedType == type,
                    position = getCardPosition(type, predictiveAlerts),
                    onToggle = { enabled ->
                        val updated = config.copy(enabled = enabled)
                        configs[type] = updated
                        AlertRepository.saveConfig(updated)
                    },
                    onExpand = { expandedType = if (expandedType == type) null else type },
                    onConfigChange = { updated ->
                        configs[type] = updated
                        AlertRepository.saveConfig(updated)
                    },
                    onPickSound = { showSoundPicker = type }
                )
            }

            // === OTHER ALERTS SECTION ===
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(
                    title = "Other Alerts",
                    icon = Icons.Default.NotificationsActive
                )
            }

            items(otherAlerts, key = { it.name }) { type ->
                val config = configs[type] ?: return@items
                AlertCard(
                    config = config,
                    isMmol = isMmol,
                    isExpanded = expandedType == type,
                    position = getCardPosition(type, otherAlerts),
                    onToggle = { enabled ->
                        val updated = config.copy(enabled = enabled)
                        configs[type] = updated
                        AlertRepository.saveConfig(updated)
                    },
                    onExpand = { expandedType = if (expandedType == type) null else type },
                    onConfigChange = { updated ->
                        configs[type] = updated
                        AlertRepository.saveConfig(updated)
                    },
                    onPickSound = { showSoundPicker = type }
                )
            }

            // === SNOOZE SECTION ===
            item {
                Spacer(Modifier.height(24.dp))
                PreemptiveSnoozeCard()
            }

            // Bottom padding
            item { Spacer(Modifier.height(100.dp)) }
        }

        // Sound Picker Dialog
        if (showSoundPicker != null) {
            val type = showSoundPicker!!
            val config = configs[type]
            if (config != null) {
                SoundPicker(
                    currentUri = config.customSoundUri,
                    onSoundSelected = { uri ->
                        val updated = config.copy(customSoundUri = uri)
                        configs[type] = updated
                        AlertRepository.saveConfig(updated)
                        showSoundPicker = null
                    },
                    onDismiss = { showSoundPicker = null }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class CardPosition { TOP, MIDDLE, BOTTOM, SINGLE }

private fun <T> getCardPosition(item: T, list: List<T>): CardPosition {
    val index = list.indexOf(item)
    return when {
        list.size == 1 -> CardPosition.SINGLE
        index == 0 -> CardPosition.TOP
        index == list.lastIndex -> CardPosition.BOTTOM
        else -> CardPosition.MIDDLE
    }
}

private fun cardShape(position: CardPosition, radius: androidx.compose.ui.unit.Dp = 12.dp): RoundedCornerShape {
    return when (position) {
        CardPosition.SINGLE -> RoundedCornerShape(radius)
        CardPosition.TOP -> RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 4.dp, bottomEnd = 4.dp)
        CardPosition.MIDDLE -> RoundedCornerShape(4.dp)
        CardPosition.BOTTOM -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = radius, bottomEnd = radius)
    }
}

@Composable
private fun AlertCard(
    config: AlertConfig,
    isMmol: Boolean,
    isExpanded: Boolean,
    position: CardPosition,
    onToggle: (Boolean) -> Unit,
    onExpand: () -> Unit,
    onConfigChange: (AlertConfig) -> Unit,
    onPickSound: () -> Unit
) {
    val context = LocalContext.current
    val (icon, accentColor) = getAlertIconAndColor(config.type)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape(position)), // Explicit clip for animation performance
        shape = cardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            // Main row (always visible) - minimum 72dp touch target
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored icon container
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 0.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = accentColor.copy(alpha = 0.12f)
                )
                {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),

                            )
                    }
                }

                // Title and subtitle
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = stringResource(config.type.nameResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val subtitle = buildString {
                        config.threshold?.let { append(formatThreshold(it, isMmol)) }
                        config.durationMinutes?.let {
                            if (isNotEmpty()) append(" • ")
                            append("$it min")
                        }
                        if (!config.enabled) {
                            if (isNotEmpty()) append(" • ")
                            append("Disabled")
                        }
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Expand indicator
                IconButton(
                    onClick = onExpand,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Enable/Disable switch with proper touch target
                StyledSwitch(
                    checked = config.enabled,
                    onCheckedChange = onToggle
                )
            }

            // Expanded content with faster animation
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(150)),
                exit = shrinkVertically(animationSpec = tween(100))
            ) {
                Column { // Removed redundant animateContentSize() as AnimatedVisibility handles it
                    AlertSettingsExpanded(
                        config = config,
                        isMmol = isMmol,
                        onConfigChange = onConfigChange,
                        onTest = {
                            // Explicitly reset state for TEST to ensure it always fires
                            // (ignoring previous triggers or suppressed state)
                            AlertStateTracker.resetState(config.type)
                            // Use Notify.testTrigger to simulate real alarm flow
                            Notify.testTrigger(config.type.id)
                        },
                        onPickSound = onPickSound
                    )
                }
            }
        }
    }
}

/**
 * Test an alert by playing its sound/vibration briefly.
 */

@Composable
private fun AlertSettingsExpanded(
    config: AlertConfig,
    isMmol: Boolean,
    onConfigChange: (AlertConfig) -> Unit,
    onTest: () -> Unit,
    onPickSound: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === Test Button ===
        FilledTonalButton(
            onClick = onTest,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Test Alert")
        }

        HorizontalDivider()

        // === Triggers Section ===
        // Threshold
        config.threshold?.let { threshold ->
            ThresholdSlider(
                label = "Threshold",
                value = threshold,
                isMmol = isMmol,
                range = getThresholdRange(config.type, isMmol),
                onValueChange = { onConfigChange(config.copy(threshold = it)) }
            )
        }

        // Durations
        if (config.durationMinutes != null || config.forecastMinutes != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                config.durationMinutes?.let {
                    Box(Modifier.weight(1f)) {
                        DurationSlider(
                            label = "Alert after",
                            value = it,
                            range = 5..120,
                            stepSize = 5,
                            onValueChange = { val_ -> onConfigChange(config.copy(durationMinutes = val_)) }
                        )
                    }
                }
                config.forecastMinutes?.let {
                    Box(Modifier.weight(1f)) {
                        DurationSlider(
                            label = "Look ahead",
                            value = it,
                            range = 10..60,
                            stepSize = 5,
                            onValueChange = { val_ -> onConfigChange(config.copy(forecastMinutes = val_)) }
                        )
                    }
                }
            }
        }

        HorizontalDivider()

//         === Feedback Section ===
//        Text(
//            "Feedback",
//            style = MaterialTheme.typography.displayLarge,
//            color = MaterialTheme.colorScheme.primary
//        )

        // 1. Delivery Style (Notification vs Alarm)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Alert Style")
            ConnectedButtonGroup(
                options = AlertDeliveryMode.entries,
                selectedOption = config.deliveryMode,
                onOptionSelected = { onConfigChange(config.copy(deliveryMode = it)) },
                label = { mode -> 
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelLarge
//                        fontWeight = FontWeight.Medium
                    ) 
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
            // 2. Intensity Profile (High, Medium, Ascending)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Intensity")
                ConnectedButtonGroup(
                    options = listOf(VolumeProfile.HIGH, VolumeProfile.MEDIUM, VolumeProfile.ASCENDING),
                    selectedOption = if (config.volumeProfile in listOf(VolumeProfile.VIBRATE_ONLY, VolumeProfile.SILENT)) VolumeProfile.MEDIUM else config.volumeProfile,
                    onOptionSelected = { onConfigChange(config.copy(volumeProfile = it)) },
                    label = { profile -> Text(profile.displayName,
                        style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

        // 3. Feedback Modes (Sound, Vibration, Flash)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Modes")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                // Sound
                FilterChip(
                    selected = config.soundEnabled,
                    onClick = { onConfigChange(config.copy(soundEnabled = !config.soundEnabled)) },
                    label = { Text("Sound") },
                    leadingIcon = {
                        Icon(
                            if (config.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            null, Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                // Vibration
                FilterChip(
                    selected = config.vibrationEnabled,
                    onClick = { onConfigChange(config.copy(vibrationEnabled = !config.vibrationEnabled)) },
                    label = { Text("Vibrate") },
                    leadingIcon = {
                         Icon(
                            if(config.vibrationEnabled) Icons.Default.Vibration else Icons.Default.Smartphone, // Fallback icon
                            null, Modifier.size(18.dp)
                        )
                    }
                )

                // Flash
                FilterChip(
                    selected = config.flashEnabled,
                    onClick = { onConfigChange(config.copy(flashEnabled = !config.flashEnabled)) },
                    label = { Text("Flash") },
                    leadingIcon = {
                        Icon(
                            if(config.flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            null, Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        // Sound Selection (Only if Sound is enabled)
        AnimatedVisibility(visible = config.soundEnabled) {
            OutlinedCard(
                onClick = onPickSound,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Alert Sound", style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (config.customSoundUri == null) "Default System Sound" else "Custom Sound Selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold 
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        
        HorizontalDivider()
        
        // === Advanced / Overrides ===
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Override DND
            ListItem(
                headlineContent = { Text("Override Do Not Disturb") },
                leadingContent = { Icon(Icons.Default.DoNotDisturb, null) },
                trailingContent = {
                    Switch(
                        checked = config.overrideDND,
                        onCheckedChange = { onConfigChange(config.copy(overrideDND = it)) }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            
            // Snooze time
             ListItem(
                headlineContent = { Text("Default Snooze") },
                supportingContent = { Text("${config.defaultSnoozeMinutes} min") },
                leadingContent = { Icon(Icons.Default.Snooze, null) },
                trailingContent = {
                    // Simple stepper or reused slider? Let's use a mini slider or just buttons in a row for common values
                    // For now, keep it simple, maybe just a click to open dialog? 
                    // Let's stick to the slider inside the item for now to avoid complexity
                },
                 colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            // Inline slider for snooze
            DurationSlider(
                label = "",
                value = config.defaultSnoozeMinutes,
                range = 5..120,
                stepSize = 5,
                onValueChange = { onConfigChange(config.copy(defaultSnoozeMinutes = it)) }
            )
        }

        HorizontalDivider()

        // === Time & Retry (Collapsible or just standard) ===
        // Keeping as standard composables for now
        TimeRangeSettings(
            enabled = config.timeRangeEnabled,
            startHour = config.activeStartHour,
            endHour = config.activeEndHour,
            onEnabledChange = { onConfigChange(config.copy(timeRangeEnabled = it)) },
            onStartChange = { onConfigChange(config.copy(activeStartHour = it)) },
            onEndChange = { onConfigChange(config.copy(activeEndHour = it)) }
        )

        RetrySettings(
            enabled = config.retryEnabled,
            intervalMinutes = config.retryIntervalMinutes,
            retryCount = config.retryCount,
            onEnabledChange = { onConfigChange(config.copy(retryEnabled = it)) },
            onIntervalChange = { onConfigChange(config.copy(retryIntervalMinutes = it)) },
            onCountChange = { onConfigChange(config.copy(retryCount = it)) }
        )
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    isMmol: Boolean,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    // Calculate step size based on range and unit
    // Lower ranges need finer control (0.1 mmol or 1 mg/dL)
    val stepSize = if (isMmol) {
        if (range.start < 5f) 0.1f else 0.2f  // Finer steps for low values
    } else {
        if (range.start < 100f) 1f else 5f   // Finer steps for low values
    }
    val steps = ((range.endInclusive - range.start) / stepSize).toInt() - 1

    Column {
        var sliderValue by remember { mutableStateOf(value) }
        LaunchedEffect(value) {
            sliderValue = value
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                // Use LOCAL sliderValue for real-time updates
                formatThreshold(sliderValue, isMmol),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val rounded = (kotlin.math.round(sliderValue / stepSize) * stepSize)
                onValueChange(rounded)
            },
            valueRange = range,
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Duration slider with snapping to useful values (every 5 mins).
 */
@Composable
private fun DurationSlider(
    label: String,
    value: Int,
    range: IntRange,
    stepSize: Int = 5,
    onValueChange: (Int) -> Unit
) {
    val steps = ((range.last - range.first) / stepSize) - 1

    Column {
        var sliderValue by remember { mutableStateOf(value.toFloat()) }
        LaunchedEffect(value) {
            sliderValue = value.toFloat()
        }

        // Calculate display value based on current slider position, rounded to step
        val displayValue = (kotlin.math.round(sliderValue / stepSize) * stepSize).toInt()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$displayValue min",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val rounded = (kotlin.math.round(sliderValue / stepSize) * stepSize).toInt()
                onValueChange(rounded)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeliveryModeSelector(
    mode: AlertDeliveryMode,
    onModeChange: (AlertDeliveryMode) -> Unit
) {
    Column {
        Text(
            "Alert Style",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AlertDeliveryMode.entries.forEach { deliveryMode ->
                FilterChip(
                    selected = mode == deliveryMode,
                    onClick = { onModeChange(deliveryMode) },
                    label = { Text(deliveryMode.displayName) },
                    leadingIcon = if (mode == deliveryMode) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VolumeProfileSelector(
    profile: VolumeProfile,
    onProfileChange: (VolumeProfile) -> Unit
) {
    Column {
        Text(
            "Volume/Vibration Profile",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VolumeProfile.entries.take(3).forEach { volumeProfile ->
                FilterChip(
                    selected = profile == volumeProfile,
                    onClick = { onProfileChange(volumeProfile) },
                    label = { Text(volumeProfile.displayName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VolumeProfile.entries.drop(3).forEach { volumeProfile ->
                FilterChip(
                    selected = profile == volumeProfile,
                    onClick = { onProfileChange(volumeProfile) },
                    label = { Text(volumeProfile.displayName) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.weight(1f)) // Balance the row
        }
    }
}

@Composable
private fun SnoozeDurationSelector(
    minutes: Int,
    onMinutesChange: (Int) -> Unit
) {
    DurationSlider(
        label = "Default snooze",
        value = minutes,
        range = 5..120,
        stepSize = 5,
        onValueChange = onMinutesChange
    )
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PreemptiveSnoozeCard() {
    var showDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .clickable { showDialog = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Snooze,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Preemptive Snooze",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Snooze alerts before they trigger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
    
    if (showDialog) {
        PreemptiveSnoozeDialog(
            onDismiss = { showDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreemptiveSnoozeDialog(
    onDismiss: () -> Unit
) {
    var snoozeLow by remember { mutableStateOf(false) }
    var snoozeHigh by remember { mutableStateOf(false) }
    var snoozeDuration by remember { mutableStateOf(30) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Snooze, contentDescription = null) },
        title = { Text("Preemptive Snooze") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Snooze alerts before they trigger. Useful after eating or taking insulin.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    FilterChip(
                        selected = snoozeLow,
                        onClick = { snoozeLow = !snoozeLow },
                        label = { Text("Low Alerts") },
                        leadingIcon = if (snoozeLow) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = snoozeHigh,
                        onClick = { snoozeHigh = !snoozeHigh },
                        label = { Text("High Alerts") },
                        leadingIcon = if (snoozeHigh) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
                
                DurationSlider(
                    label = "Duration",
                    value = snoozeDuration,
                    range = 15..120,
                    stepSize = 15,
                    onValueChange = { snoozeDuration = it }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    if (snoozeLow || snoozeHigh) {
                        SnoozeManager.preemptiveSnooze(snoozeLow, snoozeHigh, snoozeDuration)
                    }
                    onDismiss()
                },
                enabled = snoozeLow || snoozeHigh
            ) {
                Text("Snooze")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// === NEW: Time Range Settings ===
@Composable
private fun TimeRangeSettings(
    enabled: Boolean,
    startHour: Int?,
    endHour: Int?,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Active time range",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            StyledSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
        
        AnimatedVisibility(
            visible = enabled,
            enter = expandVertically(animationSpec = tween(durationMillis = 200)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 200))
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("From", style = MaterialTheme.typography.bodySmall)
                    HourPicker(
                        value = startHour ?: 22,
                        onValueChange = onStartChange
                    )
                    Text("to", style = MaterialTheme.typography.bodySmall)
                    HourPicker(
                        value = endHour ?: 8,
                        onValueChange = onEndChange
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTimeRange(startHour ?: 22, endHour ?: 8),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HourPicker(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    
    Surface(
        onClick = { showDropdown = true },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format("%02d:00", value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    
    DropdownMenu(
        expanded = showDropdown,
        onDismissRequest = { showDropdown = false }
    ) {
        (0..23).forEach { hour ->
            DropdownMenuItem(
                text = { Text(String.format("%02d:00", hour)) },
                onClick = {
                    onValueChange(hour)
                    showDropdown = false
                }
            )
        }
    }
}

private fun formatTimeRange(start: Int, end: Int): String {
    val startStr = String.format("%02d:00", start)
    val endStr = String.format("%02d:00", end)
    return if (start > end) {
        "$startStr → $endStr (overnight)"
    } else {
        "$startStr → $endStr"
    }
}

// === NEW: Retry Settings ===
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RetrySettings(
    enabled: Boolean,
    intervalMinutes: Int,
    retryCount: Int,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onCountChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Retry if no reaction",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Re-alert if not dismissed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StyledSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
        
        AnimatedVisibility(
            visible = enabled,
            enter = expandVertically(animationSpec = tween(durationMillis = 200)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 200))
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text("Retry every", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 3, 5, 10, 15).forEach { minutes ->
                        FilterChip(
                            selected = intervalMinutes == minutes,
                            onClick = { onIntervalChange(minutes) },
                            label = { Text("${minutes}m") }
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Text("Max retries", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0 to "∞", 1 to "1", 2 to "2", 3 to "3", 5 to "5").forEach { (count, label) ->
                        FilterChip(
                            selected = retryCount == count,
                            onClick = { onCountChange(count) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

// ---- Helper functions ----

private fun getAlertIconAndColor(type: AlertType): Pair<ImageVector, Color> {
    return when (type) {
        AlertType.VERY_LOW -> Icons.Default.Warning to Color(0xFFE53935)
        AlertType.LOW -> Icons.Default.ArrowDownward to Color(0xFFFF7043)
        AlertType.HIGH -> Icons.Default.ArrowUpward to Color(0xFFFFB300)
        AlertType.VERY_HIGH -> Icons.Default.Warning to Color(0xFFFF6F00)
        AlertType.PRE_LOW -> Icons.AutoMirrored.Filled.TrendingDown to Color(0xFFFF8A65)
        AlertType.PRE_HIGH -> Icons.AutoMirrored.Filled.TrendingUp to Color(0xFFFFCA28)
        AlertType.PERSISTENT_HIGH -> Icons.Default.Timer to Color(0xFFFFA726)
        AlertType.MISSED_READING -> Icons.Default.SignalWifiOff to Color(0xFF78909C)
        AlertType.LOSS -> Icons.Default.BluetoothDisabled to Color(0xFF90A4AE)
        AlertType.SENSOR_EXPIRY -> Icons.Default.Schedule to Color(0xFF7E57C2)
        else -> Icons.Default.Notifications to Color(0xFF42A5F5)  // Default blue
    }
}

private fun formatThreshold(value: Float, isMmol: Boolean): String {
    return if (isMmol) {
        String.format("%.1f", value)
    } else {
        String.format("%.0f", value)
    }
}

private fun getThresholdRange(type: AlertType, isMmol: Boolean): ClosedFloatingPointRange<Float> {
    return when (type) {
        AlertType.VERY_LOW -> if (isMmol) 2.0f..4.0f else 36f..70f
        AlertType.LOW -> if (isMmol) 3.0f..5.5f else 54f..100f
        AlertType.HIGH -> if (isMmol) 7.0f..15.0f else 126f..270f
        AlertType.VERY_HIGH -> if (isMmol) 10.0f..20.0f else 180f..360f
        AlertType.PRE_LOW -> if (isMmol) 3.5f..6.0f else 63f..108f
        AlertType.PRE_HIGH -> if (isMmol) 7.0f..14.0f else 126f..252f
        AlertType.PERSISTENT_HIGH -> if (isMmol) 7.0f..15.0f else 126f..270f
        else -> if (isMmol) 2.0f..20.0f else 36f..360f
    }
}


@Composable
private fun SoundSelector(
    currentUri: String?,
    onSoundClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Sound",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedCard(
            onClick = onSoundClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                   Text(
                        text = if (currentUri.isNullOrEmpty()) "Default Notification Sound" else "Custom Sound Selected",
                        style = MaterialTheme.typography.bodyLarge
                   )
                   if (!currentUri.isNullOrEmpty()) {
                       Text(
                           text = "Tap to change",
                           style = MaterialTheme.typography.bodySmall,
                           color = MaterialTheme.colorScheme.onSurfaceVariant
                       )
                   }
                }
            }
        }
    }
}
