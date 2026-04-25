package tk.glucodata.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import tk.glucodata.Natives
import tk.glucodata.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryExportSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var daysText by remember { mutableStateOf("30") }
    var isExporting by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                val isMmol = Natives.getunit() == 1
                val days = daysText.toLongOrNull() ?: 30L
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (days * 24 * 60 * 60 * 1000L)
                val data = tk.glucodata.data.GlucoseRepository().getHistory(startTime, isMmol)
                val unit = if (isMmol) "mmol/L" else "mg/dL"
                val success = tk.glucodata.data.HistoryExporter.exportToCsv(
                    context = context,
                    uri = uri,
                    data = data,
                    unit = unit,
                    startMillis = startTime,
                    endMillis = endTime
                )

                withContext(Dispatchers.Main) {
                    isExporting = false
                    Toast.makeText(
                        context,
                        if (success) context.getString(R.string.export_successful) else context.getString(R.string.export_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }
        }
    }

    val textLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                val isMmol = Natives.getunit() == 1
                val days = daysText.toLongOrNull() ?: 30L
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (days * 24 * 60 * 60 * 1000L)
                val data = tk.glucodata.data.GlucoseRepository().getHistory(startTime, isMmol)
                val unit = if (isMmol) "mmol/L" else "mg/dL"
                val success = tk.glucodata.data.HistoryExporter.exportToReadable(
                    context = context,
                    uri = uri,
                    data = data,
                    unit = unit,
                    startMillis = startTime,
                    endMillis = endTime
                )

                withContext(Dispatchers.Main) {
                    isExporting = false
                    Toast.makeText(
                        context,
                        if (success) context.getString(R.string.export_successful) else context.getString(R.string.export_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.export_data),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text(stringResource(R.string.days_to_export)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val fileName = "Juggluco_Export_${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(System.currentTimeMillis())}.csv"
                        csvLauncher.launch(fileName)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.export_complete_csv))
                }

                OutlinedButton(
                    onClick = {
                        val fileName = "Juggluco_Report_${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(System.currentTimeMillis())}.txt"
                        textLauncher.launch(fileName)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.export_readable_report))
                }
            }
        }
    }
}
