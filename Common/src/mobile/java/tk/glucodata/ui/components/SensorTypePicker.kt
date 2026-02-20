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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.R

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
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp <= 360 || configuration.screenHeightDp <= 700
    val horizontalPadding = if (compact) 12.dp else 16.dp
    val bottomPadding = if (compact) 20.dp else 32.dp
    val dividerPadding = if (compact) 6.dp else 8.dp
    val itemVerticalPadding = if (compact) 8.dp else 12.dp
    val iconContainerSize = if (compact) 42.dp else 48.dp
    val iconInnerPadding = if (compact) 10.dp else 12.dp

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
            
            // Sibionics
            SensorTypeItem(
                icon = Icons.Default.QrCodeScanner,
                title = stringResource(R.string.sibionics_sensor),
                subtitle = stringResource(R.string.sibionics_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.SIBIONICS)
                    onDismiss()
                },
                itemVerticalPadding = itemVerticalPadding,
                iconContainerSize = iconContainerSize,
                iconInnerPadding = iconInnerPadding
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))
            
            // Libre 2/3
            SensorTypeItem(
                icon = Icons.Default.Nfc,
                title = stringResource(R.string.libre_sensor),
                subtitle = stringResource(R.string.libre_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.LIBRE)
                    onDismiss()
                },
                itemVerticalPadding = itemVerticalPadding,
                iconContainerSize = iconContainerSize,
                iconInnerPadding = iconInnerPadding
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))
            
            // Dexcom
            SensorTypeItem(
                icon = Icons.Default.Bluetooth,
                title = stringResource(R.string.dexcom_sensor),
                subtitle = stringResource(R.string.dexcom_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.DEXCOM)
                    onDismiss()
                },
                itemVerticalPadding = itemVerticalPadding,
                iconContainerSize = iconContainerSize,
                iconInnerPadding = iconInnerPadding
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))

            // Accu-Chek SmartGuide
            SensorTypeItem(
                icon = Icons.Default.QrCodeScanner,
                title = stringResource(R.string.accuchek_sensor),
                subtitle = stringResource(R.string.accuchek_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.ACCUCHEK)
                    onDismiss()
                },
                itemVerticalPadding = itemVerticalPadding,
                iconContainerSize = iconContainerSize,
                iconInnerPadding = iconInnerPadding
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))

            // CareSens Air
            SensorTypeItem(
                icon = Icons.Default.Bluetooth,
                title = stringResource(R.string.caresens_air_sensor),
                subtitle = stringResource(R.string.caresens_air_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.CARESENS_AIR)
                    onDismiss()
                },
                itemVerticalPadding = itemVerticalPadding,
                iconContainerSize = iconContainerSize,
                iconInnerPadding = iconInnerPadding
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = dividerPadding))

            // AiDex / LinX
            SensorTypeItem(
                icon = Icons.Default.Bluetooth,
                title = stringResource(R.string.aidex_sensor),
                subtitle = stringResource(R.string.aidex_sensor_desc),
                onClick = {
                    onSensorSelected(SensorType.AIDEX)
                    onDismiss()
                },
                itemVerticalPadding = itemVerticalPadding,
                iconContainerSize = iconContainerSize,
                iconInnerPadding = iconInnerPadding
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = itemVerticalPadding),
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
