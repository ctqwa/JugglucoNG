package tk.glucodata.ui.alerts

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.alerts.AlertConfig
import tk.glucodata.alerts.AlertType
import tk.glucodata.alerts.AlertDeliveryMode
import tk.glucodata.alerts.VolumeProfile
import tk.glucodata.ui.components.StyledSwitch
import tk.glucodata.ui.util.ConnectedButtonGroup

@Composable
fun GlobalAlertSettingsCard(
    allConfigs: Map<AlertType, AlertConfig>,
    onMasterToggle: (Boolean) -> Unit,
    onApplyToAll: (AlertConfig) -> Unit,
    onPickSound: (AlertConfig, (AlertConfig) -> Unit) -> Unit,
    onTest: (AlertConfig) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    // Calculate global enabled state (Are ALL enabled? Or AT LEAST ONE?)
    // Master Switch logic: If ANY are enabled, switch is ON. Toggling OFF disables all.
    // Toggling ON enables all.
    val isMasterEnabled = allConfigs.values.any { it.enabled }
    
    // Draft config for global settings. Initialize with some defaults or the first available config.
    // We only care about the shared settings (sound, styles, overrides).
    var draftConfig by remember { 
        mutableStateOf(allConfigs.values.firstOrNull() ?: AlertConfig(AlertType.LOW)) 
    }

    // Keep draft updated if needed, or just let it diverge?
    // Let it diverge. It's a preset generator.

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.animateContentSize()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                     Text(
                        text = "Master Alert Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isExpanded) {
                        Text(
                            text = if (isMasterEnabled) "Global: Active" else "Global: All Alerts Disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // Chevron (Before Switch, but right-aligned due to Column weight)
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(16.dp))
                
                // Master Switch
                StyledSwitch(
                    checked = isMasterEnabled,
                    onCheckedChange = onMasterToggle,
                )
            }

            // Expanded Content
            if (isExpanded) {
                 Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                 
                 Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                 ) {
                     // === Test Button ===
                     FilledTonalButton(
                         onClick = { onTest(draftConfig) },
                         modifier = Modifier.fillMaxWidth(),
                         contentPadding = PaddingValues(8.dp)
                     ) {
                         Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                         Spacer(Modifier.width(8.dp))
                         Text("Test Alert")
                     }

                     // === Feedback Modes (Sound, Vibrate, Flash) ===
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Modes")
                        val modes = listOf("Sound", "Vibrate", "Flash")
                        val selectedModes = mutableListOf<String>().apply {
                            if (draftConfig.soundEnabled) add("Sound")
                            if (draftConfig.vibrationEnabled) add("Vibrate")
                            if (draftConfig.flashEnabled) add("Flash")
                        }

                        ConnectedButtonGroup(
                            options = modes,
                            selectedOptions = selectedModes,
                            multiSelect = true,
                            onOptionSelected = { mode ->
                                when(mode) {
                                    "Sound" -> draftConfig = draftConfig.copy(soundEnabled = !draftConfig.soundEnabled)
                                    "Vibrate" -> draftConfig = draftConfig.copy(vibrationEnabled = !draftConfig.vibrationEnabled)
                                    "Flash" -> draftConfig = draftConfig.copy(flashEnabled = !draftConfig.flashEnabled)
                                }
                            },
                            label = { Text(it) },
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Alert Style")
                        ConnectedButtonGroup(
                            options = AlertDeliveryMode.entries,
                            selectedOption = draftConfig.deliveryMode,
                            onOptionSelected = { draftConfig = draftConfig.copy(deliveryMode = it) },
                            label = { Text(it.displayName, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.fillMaxWidth(),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    // === Intensity ===
                     Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Intensity")
                        ConnectedButtonGroup(
                            options = listOf(VolumeProfile.HIGH, VolumeProfile.MEDIUM, VolumeProfile.ASCENDING),
                            selectedOption = if (draftConfig.volumeProfile in listOf(VolumeProfile.VIBRATE_ONLY, VolumeProfile.SILENT)) VolumeProfile.MEDIUM else draftConfig.volumeProfile,
                            onOptionSelected = { draftConfig = draftConfig.copy(volumeProfile = it) },
                            label = { Text(it.displayName, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.fillMaxWidth(),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    // === Sound (Conditional) ===
                    AnimatedVisibility(visible = draftConfig.soundEnabled) {
                        OutlinedCard(
                            onClick = {
                                onPickSound(draftConfig) { updatedDraft ->
                                    draftConfig = updatedDraft
                                }
                            },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f), // Slight background for contrast
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
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
                                        if (draftConfig.customSoundUri == null) "Default System Sound" else "Custom Sound Selected",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold 
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }

                    //  Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                     
                    // === Override DND ===
                     Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DoNotDisturb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            "Override Do Not Disturb",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        StyledSwitch(
                            checked = draftConfig.overrideDND,
                            onCheckedChange = { draftConfig = draftConfig.copy(overrideDND = it) }
                        )
                    }

                    
                    // Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))

                    // === Time Range ===
                    TimeRangeSettings(
                        enabled = draftConfig.timeRangeEnabled,
                        startHour = draftConfig.activeStartHour,
                        endHour = draftConfig.activeEndHour,
                        onEnabledChange = { draftConfig = draftConfig.copy(timeRangeEnabled = it) },
                        onStartChange = { draftConfig = draftConfig.copy(activeStartHour = it) },
                        onEndChange = { draftConfig = draftConfig.copy(activeEndHour = it) }
                    )

                    // === Retry ===
                    RetrySettings(
                        enabled = draftConfig.retryEnabled,
                        intervalMinutes = draftConfig.retryIntervalMinutes,
                        retryCount = draftConfig.retryCount,
                        onEnabledChange = { draftConfig = draftConfig.copy(retryEnabled = it) },
                        onIntervalChange = { draftConfig = draftConfig.copy(retryIntervalMinutes = it) },
                        onCountChange = { draftConfig = draftConfig.copy(retryCount = it) }
                    )
                    
                    // Spacer(Modifier.height(2.dp))

                    // === Snooze ===
                    DurationSlider(
                        label = "Default Snooze",
                        value = draftConfig.defaultSnoozeMinutes,
                        range = 5..60,
                        stepSize = 5,
                        onValueChange = { draftConfig = draftConfig.copy(defaultSnoozeMinutes = it) }
                    )

                    // === Apply Button ===
                    Button(
                        onClick = { onApplyToAll(draftConfig) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Apply to All Alerts")
                    }
                 }
            }
        }
    }
}
