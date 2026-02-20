package tk.glucodata.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.alerts.AlertConfig
import tk.glucodata.alerts.AlertDeliveryMode
import tk.glucodata.alerts.VolumeProfile
import tk.glucodata.ui.components.StyledSwitch
import tk.glucodata.ui.util.ConnectedButtonGroup

/**
 * A shared component that renders the standard list of alert configuration options.
 * Valid for Master, Regular, and Custom alerts.
 * 
 * Options included:
 * - Feedback Modes (Sound, Vibrate, Flash)
 * - Alert Style (Notification, Alarm, Both)
 * - Intensity (High, Medium, Ascending)
 * - Sound Picker (Conditional)
 * - Override Do Not Disturb
 * - Active Time Range
 * - Retry Settings
 * - Default Snooze
 */
@Composable
fun CommonAlertSettings(
    config: AlertConfig,
    onConfigChange: (AlertConfig) -> Unit,
    onPickSound: (AlertConfig) -> Unit,
    onTest: () -> Unit,
    // Optional Header Content (e.g., Thresholds)
    headerContent: (@Composable () -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
         // === Test Button ===
         FilledTonalButton(
             onClick = onTest,
             modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
             contentPadding = PaddingValues(8.dp)
         ) {
             Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
             Spacer(Modifier.width(8.dp))
             Text(stringResource(R.string.test_alert))
         }
         
         // === Header (Thresholds/Durations) ===
         headerContent?.invoke()

         // === Feedback Modes (Sound, Vibrate, Flash) ===
         // Source: GlobalAlertSettingsCard.kt
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(stringResource(R.string.modes))
            val modes = listOf("Sound", "Vibrate", "Flash")
            val selectedModes = mutableListOf<String>().apply {
                if (config.soundEnabled) add("Sound")
                if (config.vibrationEnabled) add("Vibrate")
                if (config.flashEnabled) add("Flash")
            }

            ConnectedButtonGroup(
                options = modes,
                selectedOptions = selectedModes,
                multiSelect = true,
                onOptionSelected = { mode ->
                    val newConfig = when(mode) {
                        "Sound" -> config.copy(soundEnabled = !config.soundEnabled)
                        "Vibrate" -> config.copy(vibrationEnabled = !config.vibrationEnabled)
                        "Flash" -> config.copy(flashEnabled = !config.flashEnabled)
                        else -> config
                    }
                    onConfigChange(newConfig)
                },
                label = {
                    val labelRes = when (it) {
                        "Sound" -> R.string.soundname
                        "Vibrate" -> R.string.vibrationname
                        else -> R.string.flash
                    }
                    Text(stringResource(labelRes))
                },
                icon = { mode ->
                    when(mode) {
                         "Sound" -> if(selectedModes.contains(mode)) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.VolumeOff
                         "Vibrate" -> if(selectedModes.contains(mode)) Icons.Default.Vibration else Icons.Default.Smartphone
                         "Flash" -> if(selectedModes.contains(mode)) Icons.Default.FlashOn else Icons.Default.FlashOff
                         else -> null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f), // Transparent-ish on PrimaryContainer
                unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // === Alert Style ===
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(stringResource(R.string.alert_style))
            ConnectedButtonGroup(
                options = AlertDeliveryMode.entries,
                selectedOption = config.deliveryMode,
                onOptionSelected = { onConfigChange(config.copy(deliveryMode = it)) },
                label = { Text(it.displayName, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.fillMaxWidth(),
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        // === Intensity ===
         Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(stringResource(R.string.intensity))
            ConnectedButtonGroup(
                options = listOf(VolumeProfile.HIGH, VolumeProfile.MEDIUM, VolumeProfile.ASCENDING),
                selectedOption = if (config.volumeProfile in listOf(VolumeProfile.VIBRATE_ONLY, VolumeProfile.SILENT)) VolumeProfile.MEDIUM else config.volumeProfile,
                onOptionSelected = { onConfigChange(config.copy(volumeProfile = it)) },
                label = { Text(it.displayName, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.fillMaxWidth(),
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(2.dp))

        // === Sound Settings (Conditional) ===
        AnimatedVisibility(visible = config.soundEnabled) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Alert Sound Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onPickSound(config) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.alert_sound),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            getSoundDisplayText(config.customSoundUri, config.type.id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Override Silent Mode toggle (inside Sound section)
                ClickableToggleRow(
                    icon = Icons.Default.VolumeOff,
                    title = stringResource(R.string.override_silent_mode),
                    subtitle = stringResource(R.string.override_silent_mode_desc),
                    checked = config.overrideDND,
                    onCheckedChange = { onConfigChange(config.copy(overrideDND = it)) }
                )
            }
        }

        // === Time Range ===
        TimeRangeSettings(
            enabled = config.timeRangeEnabled,
            startHour = config.activeStartHour,
            startMinute = config.activeStartMinute,
            endHour = config.activeEndHour,
            endMinute = config.activeEndMinute,
            onEnabledChange = { onConfigChange(config.copy(timeRangeEnabled = it)) },
            onStartChange = { hour, minute -> onConfigChange(config.copy(activeStartHour = hour, activeStartMinute = minute)) },
            onEndChange = { hour, minute -> onConfigChange(config.copy(activeEndHour = hour, activeEndMinute = minute)) }
        )

        // === Retry ===
        RetrySettings(
            enabled = config.retryEnabled,
            intervalMinutes = config.retryIntervalMinutes,
            retryCount = config.retryCount,
            onEnabledChange = { onConfigChange(config.copy(retryEnabled = it)) },
            onIntervalChange = { onConfigChange(config.copy(retryIntervalMinutes = it)) },
            onCountChange = { onConfigChange(config.copy(retryCount = it)) }
        )
        
        // === Snooze ===
        DurationSlider(
            label = stringResource(R.string.default_snooze),
            value = config.defaultSnoozeMinutes,
            range = 5..60,
            stepSize = 5,
            onValueChange = { onConfigChange(config.copy(defaultSnoozeMinutes = it)) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
