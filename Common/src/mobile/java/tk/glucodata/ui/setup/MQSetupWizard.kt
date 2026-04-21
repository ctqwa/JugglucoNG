// JugglucoNG — MQ/Glutec Setup Wizard
// BLE-scan onboarding: scan for Nordic UART advertisers, let the user pick a
// transmitter, register it via MQRegistry, and optionally capture the vendor
// login needed to refresh bootstrap tokens when the Glutec backend expires them.

package tk.glucodata.ui.setup

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.Log
import tk.glucodata.R
import tk.glucodata.drivers.mq.MQBootstrapClient
import tk.glucodata.drivers.mq.MQConstants
import tk.glucodata.drivers.mq.MQRegistry
import tk.glucodata.ui.util.BleDeviceScanner
import tk.glucodata.ui.util.rememberBleScanner

private const val MQ_QR_EXAMPLE = "ABC123"

private enum class MQSetupStep {
    SCAN,
    CONNECTING,
    SUCCESS,
}

private data class MQScanCandidate(
    val address: String,
    val displayName: String,
    val isLikelyMq: Boolean,
    val advertisesNus: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MQSetupWizard(
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val tag = "MQSetupWizard"
    val ui = rememberWizardUiMetrics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(MQSetupStep.SCAN) }
    var selectedLabel by remember { mutableStateOf("") }
    var qrCodeContent by remember { mutableStateOf("") }
    var authPhone by remember { mutableStateOf(MQRegistry.loadAuthPhone(context).orEmpty()) }
    var authPassword by remember { mutableStateOf(MQRegistry.loadAuthPassword(context).orEmpty()) }
    var showManualQrEntry by remember { mutableStateOf(false) }

    if (showManualQrEntry) {
        MQManualQrEntryDialog(
            initialValue = qrCodeContent,
            onDismiss = { showManualQrEntry = false },
            onConfirm = { code ->
                qrCodeContent = code
                showManualQrEntry = false
            },
        )
    }

    BackHandler {
        if (currentStep == MQSetupStep.SCAN) onDismiss() else currentStep = MQSetupStep.SCAN
    }

    LaunchedEffect(currentStep) {
        if (currentStep == MQSetupStep.SUCCESS) {
            delay(SENSOR_SETUP_SUCCESS_AUTO_ADVANCE_MS)
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mq_setup_title)) },
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
            label = "MQWizard"
        ) { step ->
            when (step) {
                MQSetupStep.SCAN -> MQScanStep(
                    ui = ui,
                    qrCodeContent = qrCodeContent,
                    authPhone = authPhone,
                    authPassword = authPassword,
                    onQrCodeChanged = { qrCodeContent = normalizeMqQrCode(it) },
                    onAuthPhoneChanged = { authPhone = it },
                    onAuthPasswordChanged = { authPassword = it },
                    onShowManualQrEntry = { showManualQrEntry = true },
                    onDeviceSelected = { candidate ->
                        val addressCanonical = candidate.address
                        selectedLabel = candidate.displayName.ifBlank { addressCanonical }
                        currentStep = MQSetupStep.CONNECTING
                        scope.launch {
                            try {
                                val normalizedQr = normalizeMqQrCode(qrCodeContent).takeIf { it.isNotBlank() }
                                val normalizedPhone = authPhone.trim()
                                val password = authPassword
                                if (normalizedPhone.isNotEmpty() && password.isNotBlank()) {
                                    MQRegistry.saveAuthCredentials(context, normalizedPhone, password)
                                }
                                val bootstrapConfig = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val result = MQBootstrapClient.fetchBestEffort(
                                            context = context,
                                            bleId = addressCanonical,
                                            qrCode = normalizedQr,
                                            authToken = MQRegistry.loadAuthToken(context),
                                            credentials = MQRegistry.loadAuthCredentials(context),
                                        )
                                        result.refreshedToken?.let { MQRegistry.saveAuthToken(context, it) }
                                        result.config
                                    }.onFailure {
                                        Log.w(tag, "MQ bootstrap prefetch failed: ${it.message}")
                                    }.getOrNull()
                                }
                                val sensorId = MQRegistry.addSensor(
                                    context = context,
                                    displayName = candidate.displayName.ifBlank { null },
                                    address = addressCanonical,
                                    qrCodeContent = normalizedQr,
                                    connectNow = false,
                                    bootstrapConfig = bootstrapConfig,
                                )
                                if (sensorId == null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.nobluetooth),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    currentStep = MQSetupStep.SCAN
                                    return@launch
                                }
                                MQRegistry.connectSensor(context, sensorId)
                                delay(2000)
                                currentStep = MQSetupStep.SUCCESS
                            } catch (t: Throwable) {
                                Log.e(tag, "Failed to add MQ sensor: ${t.message}")
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.nobluetooth),
                                    Toast.LENGTH_LONG
                                ).show()
                                currentStep = MQSetupStep.SCAN
                            }
                        }
                    }
                )
                MQSetupStep.CONNECTING -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SensorSetupConnectingScreen(
                        ui = ui,
                        sensorLabel = selectedLabel.ifBlank { null }
                    )
                }
                MQSetupStep.SUCCESS -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SensorSetupSuccessScreen(
                        ui = ui,
                        sensorLabel = selectedLabel.ifBlank { null }
                    )
                }
            }
        }
    }
}

@Composable
private fun MQScanStep(
    ui: WizardUiMetrics,
    qrCodeContent: String,
    authPhone: String,
    authPassword: String,
    onQrCodeChanged: (String) -> Unit,
    onAuthPhoneChanged: (String) -> Unit,
    onAuthPasswordChanged: (String) -> Unit,
    onShowManualQrEntry: () -> Unit,
    onDeviceSelected: (MQScanCandidate) -> Unit,
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<MQScanCandidate>>(emptyList()) }
    val scanner = rememberBleScanner()
    var scanPermissionGranted by remember { mutableStateOf(hasBleScanPermissions(context)) }
    var bluetoothEnabled by remember { mutableStateOf(scanner.isBluetoothEnabled()) }
    var scanRetryKey by remember { mutableStateOf(0) }
    var scanError by remember { mutableStateOf<BleDeviceScanner.ScanStartError?>(null) }
    var requestedPermissionOnce by remember { mutableStateOf(false) }
    var showAllDevices by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scanPermissionGranted = hasBleScanPermissions(context)
        bluetoothEnabled = scanner.isBluetoothEnabled()
        scanError = null
        scanRetryKey += 1
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothEnabled = scanner.isBluetoothEnabled()
        scanError = null
        scanRetryKey += 1
    }

    val requestScanPermission = {
        val required = requiredBleScanPermissions()
        if (required.isEmpty()) {
            scanPermissionGranted = true
            scanRetryKey += 1
        } else {
            permissionLauncher.launch(required)
        }
    }

    LaunchedEffect(Unit) {
        if (!scanPermissionGranted && !requestedPermissionOnce) {
            requestedPermissionOnce = true
            requestScanPermission()
        }
    }

    DisposableEffect(scanPermissionGranted, bluetoothEnabled, scanRetryKey, showAllDevices) {
        if (!scanPermissionGranted || !bluetoothEnabled) {
            scanner.stopScan()
            devices = emptyList()
            return@DisposableEffect onDispose { scanner.stopScan() }
        }

        devices = emptyList()
        scanner.startScan(
            onResult = { result ->
                val device = result.device
                val address = try {
                    device.address
                } catch (_: SecurityException) {
                    null
                } ?: return@startScan

                val record = result.scanRecord
                val scanName = try {
                    device.name
                } catch (_: SecurityException) {
                    null
                }
                val nameCandidates = listOfNotNull(
                    scanName,
                    record?.deviceName,
                ).mapNotNull { it.trim().takeIf(String::isNotBlank) }

                val bestName = nameCandidates.firstOrNull().orEmpty()
                val advertisesNus = record?.serviceUuids?.any { it.uuid == MQConstants.NUS_SERVICE } == true
                val nameLooksMq = nameCandidates.any(MQConstants::isMqDevice)
                val isLikelyMq = advertisesNus || nameLooksMq

                if (!showAllDevices && !isLikelyMq) return@startScan

                if (devices.none { it.address.equals(address, ignoreCase = true) }) {
                    devices = devices + MQScanCandidate(
                        address = address,
                        displayName = bestName,
                        isLikelyMq = isLikelyMq,
                        advertisesNus = advertisesNus,
                    )
                }
            },
            onError = { error ->
                scanError = error
                when (error) {
                    BleDeviceScanner.ScanStartError.NoPermission -> scanPermissionGranted = false
                    BleDeviceScanner.ScanStartError.BluetoothDisabled -> bluetoothEnabled = false
                    else -> Unit
                }
            }
        )
        onDispose { scanner.stopScan() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(ui.spacerMedium))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ui.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.mq_searching_sensors),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { showAllDevices = !showAllDevices }) {
                Text(
                    if (showAllDevices) stringResource(R.string.show_sensors_only)
                    else stringResource(R.string.see_all_devices)
                )
            }
        }

        Spacer(Modifier.height(ui.spacerSmall))
        Card(
            modifier = Modifier
                .padding(horizontal = ui.horizontalPadding)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.scan_transmitter_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                InlineQrScannerCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    onScanResult = onQrCodeChanged,
                )
                Spacer(Modifier.height(8.dp))
                if (qrCodeContent.isNotBlank()) {
                    Text(
                        text = "${stringResource(R.string.scan_sensor_qr)}: $qrCodeContent",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = onShowManualQrEntry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.enter_code_manually))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = authPhone,
                    onValueChange = onAuthPhoneChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = authPassword,
                    onValueChange = onAuthPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) {
                                    stringResource(R.string.hide_password)
                                } else {
                                    stringResource(R.string.show_password)
                                },
                            )
                        }
                    },
                )
            }
        }

        if (!scanPermissionGranted || !bluetoothEnabled || scanError != null) {
            Spacer(Modifier.height(ui.spacerMedium))
            Card(
                modifier = Modifier
                    .padding(horizontal = ui.horizontalPadding)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val messageRes = when {
                        !scanPermissionGranted && Build.VERSION.SDK_INT >= 31 -> R.string.turn_on_nearby_devices_permission
                        !scanPermissionGranted -> R.string.turn_on_location_permission
                        !bluetoothEnabled || scanError is BleDeviceScanner.ScanStartError.BluetoothDisabled -> R.string.bluetooth_is_turned_off
                        else -> R.string.nobluetooth
                    }
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(ui.spacerMedium))
                    val buttonRes = when {
                        !scanPermissionGranted -> R.string.permission
                        !bluetoothEnabled || scanError is BleDeviceScanner.ScanStartError.BluetoothDisabled -> R.string.enable_bluetooth
                        else -> R.string.search_bluetooth
                    }
                    Button(
                        onClick = {
                            when {
                                !scanPermissionGranted -> requestScanPermission()
                                !bluetoothEnabled || scanError is BleDeviceScanner.ScanStartError.BluetoothDisabled -> {
                                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }
                                else -> {
                                    scanError = null
                                    scanPermissionGranted = hasBleScanPermissions(context)
                                    bluetoothEnabled = scanner.isBluetoothEnabled()
                                    scanRetryKey += 1
                                }
                            }
                        },
                        modifier = Modifier.height(ui.buttonHeight)
                    ) {
                        Text(stringResource(buttonRes))
                    }
                }
            }
        }

        if (devices.isEmpty() && scanPermissionGranted && bluetoothEnabled) {
            Spacer(Modifier.height(ui.spacerMedium))
            Text(
                stringResource(R.string.mq_no_sensors_hint),
                modifier = Modifier.padding(horizontal = ui.horizontalPadding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn {
            items(devices) { device ->
                if (!showAllDevices && !device.isLikelyMq) return@items
                val title = device.displayName.ifBlank { stringResource(R.string.unknown) }
                val supporting = when {
                    device.advertisesNus -> stringResource(R.string.mq_detected_label, device.address)
                    device.isLikelyMq -> device.address
                    else -> stringResource(R.string.mq_selectable_unrecognized, device.address)
                }
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(supporting) },
                    leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                    modifier = Modifier.clickable { onDeviceSelected(device) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MQManualQrEntryDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val normalized = remember(value) { normalizeMqQrCode(value) }
    val isValid = normalized.length == 6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_code_manually)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    value = input.uppercase().filter { it.isLetterOrDigit() }.take(6)
                },
                label = { Text(stringResource(R.string.scan_sensor_qr)) },
                placeholder = { Text(MQ_QR_EXAMPLE) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
                isError = value.isNotBlank() && !isValid,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(normalized) }, enabled = isValid) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun normalizeMqQrCode(raw: String): String =
    raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
