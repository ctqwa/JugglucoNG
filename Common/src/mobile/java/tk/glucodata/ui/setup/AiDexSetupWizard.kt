package tk.glucodata.ui.setup

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.launch
import tk.glucodata.SensorBluetooth
import tk.glucodata.ui.util.rememberBleScanner

enum class AiDexSetupStep {
    SCAN,
    CONNECTING,
    SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDexSetupWizard(
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(AiDexSetupStep.SCAN) }
    var selectedDeviceName by remember { mutableStateOf("") }
    var selectedDeviceAddress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AiDex / LinX Setup") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier.padding(padding),
            label = "AiDexWizard"
        ) { step ->
            when (step) {
                AiDexSetupStep.SCAN -> AiDexScanStep(
                    onDeviceSelected = { rawName, address ->
                        val name = normalizeAiDexSerial(rawName)
                        if (name == null) {
                            Toast.makeText(
                                context,
                                "AiDex: couldn't parse sensor serial from \"$rawName\"",
                                Toast.LENGTH_LONG
                            ).show()
                            return@AiDexScanStep
                        }
                        
                        selectedDeviceName = name
                        selectedDeviceAddress = address
                        currentStep = AiDexSetupStep.CONNECTING
                        
                        // Initiate Connection Logic
                        scope.launch {
                            // 1. Add to Persistence & SensorBluetooth
                            SensorBluetooth.addAiDexSensor(context, name, address)
                            
                            // 2. Wait a bit then show success
                            kotlinx.coroutines.delay(2000)
                            currentStep = AiDexSetupStep.SUCCESS
                        }
                    }
                )
                AiDexSetupStep.CONNECTING -> Box(
                     modifier = Modifier.fillMaxSize(),
                     contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Connecting to $selectedDeviceName...")
                    }
                }
                AiDexSetupStep.SUCCESS -> Box(
                     modifier = Modifier.fillMaxSize(),
                     contentAlignment = Alignment.Center
                ) {
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Connected!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = onComplete) {
                            Text("Finish")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiDexScanStep(
    onDeviceSelected: (String, String) -> Unit
) {
    var devices by remember { mutableStateOf<List<android.bluetooth.BluetoothDevice>>(emptyList()) }
    val scanner = rememberBleScanner()
    
    // Start Scanning Effect
    DisposableEffect(Unit) {
        scanner.startScan { result ->
            val device = result.device
            val name = device.name ?: return@startScan
            val serial = normalizeAiDexSerial(name) ?: return@startScan
            val record = result.scanRecord
            // val mfg = record?.getManufacturerSpecificData(0x59)
            // Relaxed filter: Don't check for 0x59 manufacturer ID to support variants (Linx, Lumiflex)
            // if (record != null && mfg == null) return@startScan
            if (devices.none { it.address == device.address }) {
                devices = devices + device
            }
        }
        onDispose { scanner.stopScan() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(
            "Searching for sensors...",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium
        )
        
        LazyColumn {
            items(devices) { device ->
                val name = device.name ?: "Unknown"
                val serial = normalizeAiDexSerial(name)
                if (serial == null) return@items
                ListItem(
                    headlineContent = { Text("$name ($serial)") },
                    supportingContent = { Text(device.address) },
                    leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                    modifier = Modifier.clickable { 
                        onDeviceSelected(name, device.address) 
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

private fun normalizeAiDexSerial(rawName: String): String? {
    val regex = Regex("X\\s*-?\\s*([A-Z0-9]{8,})", RegexOption.IGNORE_CASE)
    val match = regex.find(rawName)
    if (match != null) {
        val body = match.groupValues[1].uppercase()
        return "X-$body"
    }
    val cleaned = rawName.trim().replace(" ", "")
    if (cleaned.length == 11 && cleaned.all { it.isLetterOrDigit() }) {
        return "X-${cleaned.uppercase()}"
    }
    return null
}
