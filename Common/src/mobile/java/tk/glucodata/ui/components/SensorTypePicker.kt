package tk.glucodata.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.ui.util.rememberAdaptiveWindowMetrics

enum class SensorType {
    SIBIONICS,
    LIBRE,
    DEXCOM,
    ACCUCHEK,
    CARESENS_AIR,
    AIDEX
}

/**
 * Bottom sheet to select which type of sensor to add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorTypePicker(
    onDismiss: () -> Unit,
    onSensorSelected: (SensorType) -> Unit
) {
    data class SensorTypeEntry(
        val type: SensorType,
        val icon: ImageVector,
        val titleRes: Int,
        val subtitleRes: Int
    )

    val compact = rememberAdaptiveWindowMetrics().isCompact
    val horizontalPadding = if (compact) 12.dp else 16.dp
    val bottomPadding = if (compact) 20.dp else 32.dp
    val itemSpacing = if (compact) 8.dp else 10.dp
    val itemVerticalPadding = if (compact) 12.dp else 14.dp
    val iconContainerSize = if (compact) 42.dp else 48.dp
    val iconInnerPadding = if (compact) 10.dp else 12.dp
    val sensorEntries = listOf(
        SensorTypeEntry(
            type = SensorType.SIBIONICS,
            icon = Icons.Default.QrCodeScanner,
            titleRes = R.string.sibionics_sensor,
            subtitleRes = R.string.sibionics_sensor_desc
        ),
        SensorTypeEntry(
            type = SensorType.LIBRE,
            icon = Icons.Default.Nfc,
            titleRes = R.string.libre_sensor,
            subtitleRes = R.string.libre_sensor_desc
        ),
        SensorTypeEntry(
            type = SensorType.DEXCOM,
            icon = Icons.Default.QrCodeScanner,
            titleRes = R.string.dexcom_sensor,
            subtitleRes = R.string.dexcom_sensor_desc
        ),
        SensorTypeEntry(
            type = SensorType.ACCUCHEK,
            icon = Icons.Default.QrCodeScanner,
            titleRes = R.string.accuchek_sensor,
            subtitleRes = R.string.accuchek_sensor_desc
        ),
        SensorTypeEntry(
            type = SensorType.CARESENS_AIR,
            icon = Icons.Default.QrCodeScanner,
            titleRes = R.string.caresens_air_sensor,
            subtitleRes = R.string.caresens_air_sensor_desc
        ),
        SensorTypeEntry(
            type = SensorType.AIDEX,
            icon = Icons.Default.Bluetooth,
            titleRes = R.string.aidex_sensor,
            subtitleRes = R.string.aidex_sensor_desc
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = bottomPadding)
        ) {
            Text(
                text = stringResource(R.string.select_sensor_type),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = if (compact) 12.dp else 16.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
                sensorEntries.forEach { entry ->
                    SensorTypeItem(
                        icon = entry.icon,
                        title = stringResource(entry.titleRes),
                        subtitle = stringResource(entry.subtitleRes),
                        onClick = {
                            onSensorSelected(entry.type)
                            onDismiss()
                        },
                        itemVerticalPadding = itemVerticalPadding,
                        iconContainerSize = iconContainerSize,
                        iconInnerPadding = iconInnerPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorTypeItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    itemVerticalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    iconContainerSize: androidx.compose.ui.unit.Dp = 48.dp,
    iconInnerPadding: androidx.compose.ui.unit.Dp = 12.dp
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = itemVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(iconContainerSize)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(iconInnerPadding)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
