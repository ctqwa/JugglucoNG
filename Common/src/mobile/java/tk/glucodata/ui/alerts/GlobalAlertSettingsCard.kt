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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.R
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
                        text = stringResource(R.string.master_alert_control),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isExpanded) {
                        Text(
                            text = if (isMasterEnabled) stringResource(R.string.global_active) else stringResource(R.string.global_all_alerts_disabled),
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
                    modifier = Modifier.padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                 ) {
                     // === Shared Settings Component ===
                     CommonAlertSettings(
                         config = draftConfig,
                         onConfigChange = { draftConfig = it },
                         onPickSound = { 
                            onPickSound(draftConfig) { updatedDraft ->
                                draftConfig = updatedDraft
                            }
                         },
                         onTest = { onTest(draftConfig) }
                     )

                    // === Apply Button ===
                    Button(
                        onClick = { onApplyToAll(draftConfig) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.apply_to_all_alerts))
                    }
                 }
            }
        }
    }
}
