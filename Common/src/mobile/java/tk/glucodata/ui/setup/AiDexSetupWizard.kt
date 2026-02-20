package tk.glucodata.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.microtechmd.blecomm.BlecommLoader
import kotlinx.coroutines.launch
import tk.glucodata.R
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
    val ui = rememberWizardUiMetrics()
    var currentStep by remember { mutableStateOf(AiDexSetupStep.SCAN) }
    var selectedDeviceName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var vendorLibAvailable by remember { mutableStateOf(BlecommLoader.ensureLoaded(context)) }
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val installed = BlecommLoader.installFromDocument(context, uri)
        vendorLibAvailable = BlecommLoader.ensureLoaded(context)
        val message = if (installed && vendorLibAvailable) {
            context.getString(R.string.installedlibrary)
        } else {
            context.getString(R.string.cantextract, BlecommLoader.requiredLibraryFileName())
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aidex_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
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
                    ui = ui,
                    vendorLibAvailable = vendorLibAvailable,
                    onUploadProprietary = { uploadLauncher.launch(arrayOf("*/*")) },
                    onDeviceSelected = { rawName, address ->
                        if (!vendorLibAvailable) {
                            Toast.makeText(context, context.getString(R.string.wronglibrary), Toast.LENGTH_LONG).show()
                            uploadLauncher.launch(arrayOf("*/*"))
                            return@AiDexScanStep
                        }
                        val name = normalizeAiDexSerial(rawName)
                        if (name == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.aidex_parse_error, rawName),
                                Toast.LENGTH_LONG
                            ).show()
                            return@AiDexScanStep
                        }
                        
                        selectedDeviceName = name
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
                        Spacer(Modifier.height(ui.spacerMedium))
                        Text(stringResource(R.string.aidex_connecting_to, selectedDeviceName))
                    }
                }
                AiDexSetupStep.SUCCESS -> Box(
                     modifier = Modifier.fillMaxSize(),
                     contentAlignment = Alignment.Center
                ) {
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (ui.compact) 56.dp else 64.dp)
                        )
                        Spacer(Modifier.height(ui.spacerMedium))
                        Text(stringResource(R.string.aidex_connected), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(ui.spacerLarge))
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.height(ui.buttonHeight)
                        ) {
                            Text(stringResource(R.string.finish_setup))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiDexScanStep(
    ui: WizardUiMetrics,
    vendorLibAvailable: Boolean,
    onUploadProprietary: () -> Unit,
    onDeviceSelected: (String, String) -> Unit
) {
    data class ScanCandidate(
        val address: String,
        val rawName: String
    )

    var devices by remember { mutableStateOf<List<ScanCandidate>>(emptyList()) }
    val scanner = rememberBleScanner()
    
    // Start Scanning Effect
    DisposableEffect(Unit) {
        scanner.startScan { result ->
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: return@startScan
            normalizeAiDexSerial(name) ?: return@startScan
            // val mfg = record?.getManufacturerSpecificData(0x59)
            // Relaxed filter: Don't check for 0x59 manufacturer ID to support variants (Linx, Lumiflex)
            // if (record != null && mfg == null) return@startScan
            if (devices.none { it.address == device.address }) {
                devices = devices + ScanCandidate(
                    address = device.address,
                    rawName = name
                )
            }
        }
        onDispose { scanner.stopScan() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(ui.spacerMedium))
        Text(
            stringResource(R.string.aidex_searching_sensors),
            modifier = Modifier.padding(ui.horizontalPadding),
            style = MaterialTheme.typography.titleMedium
        )
        if (!vendorLibAvailable) {
            Spacer(Modifier.height(ui.spacerMedium))
            Card(
                modifier = Modifier
                    .padding(horizontal = ui.horizontalPadding)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.wronglibrary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(ui.spacerMedium))
                    Button(
                        onClick = onUploadProprietary,
                        modifier = Modifier.height(ui.buttonHeight)
                    ) {
                        Text(stringResource(R.string.upload))
                    }
                }
            }
        }
        
        LazyColumn {
            items(devices) { device ->
                val name = device.rawName.ifBlank { stringResource(R.string.unknown) }
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
    val xPrefixed = Regex("X\\s*-?\\s*([A-Z0-9]{8,})", RegexOption.IGNORE_CASE)
    val xMatch = xPrefixed.find(rawName)
    if (xMatch != null) {
        val body = xMatch.groupValues[1].uppercase()
        return "X-$body"
    }

    // Some AiDex family sensors advertise with a product prefix (for example "Vista-...")
    // instead of the canonical "X-..." serial format used internally by the app.
    val familyPrefixed = Regex("(?:AIDEX|LINX|LUMIFLEX|VISTA)\\s*[-_]?\\s*([A-Z0-9]{8,})", RegexOption.IGNORE_CASE)
    val familyMatch = familyPrefixed.find(rawName)
    if (familyMatch != null) {
        val body = familyMatch.groupValues[1].uppercase()
        return "X-$body"
    }

    val cleaned = rawName.trim().replace(" ", "")
    if (cleaned.length == 11 && cleaned.all { it.isLetterOrDigit() }) {
        return "X-${cleaned.uppercase()}"
    }
    return null
}
