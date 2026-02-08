package tk.glucodata.ui.components

import androidx.compose.foundation.clickable
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

enum class SensorType {
    SIBIONICS,
    LIBRE,
    DEXCOM,
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.select_sensor_type),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Sibionics
            SensorTypeItem(
                icon = Icons.Default.QrCodeScanner,
                title = stringResource(R.string.sibionics_sensor),
                subtitle = stringResource(R.string.sibionics_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.SIBIONICS)
                    onDismiss()
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Libre 2/3
            SensorTypeItem(
                icon = Icons.Default.Nfc,
                title = stringResource(R.string.libre_sensor),
                subtitle = stringResource(R.string.libre_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.LIBRE)
                    onDismiss()
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Dexcom
            SensorTypeItem(
                icon = Icons.Default.Bluetooth,
                title = stringResource(R.string.dexcom_sensor),
                subtitle = stringResource(R.string.dexcom_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.DEXCOM)
                    onDismiss()
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // AiDex / LinX
            SensorTypeItem(
                icon = Icons.Default.Bluetooth,
                title = "AiDex / LinX", // TODO: R.string.aidex_sensor
                subtitle = "Configure AiDex or LinX CGM via Bluetooth",
                onClick = {
                    onSensorSelected(SensorType.AIDEX)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun SensorTypeItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
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
