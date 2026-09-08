// JugglucoNG — CT-14 (CT2) Setup Wizard
//
// BLE-scan onboarding mirroring AnytimeSetupWizard, minus the calibration QR
// step: CT2 has no inputKAndR equivalent in the vendor SDK (see
// docs/ct-driver-plan.md §B1), so the wizard goes straight from BLE scan to
// registration and connect.

package tk.glucodata.ui.setup

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tk.glucodata.Log
import tk.glucodata.R
import tk.glucodata.drivers.anytime.AnytimeConstants
import tk.glucodata.drivers.anytime.AnytimeRegistry
import tk.glucodata.ui.util.BleDeviceScanner
import tk.glucodata.ui.util.rememberBleScanner

private enum class Ct14SetupStep { SCAN, CONNECTING, SUCCESS }

private data class Ct14ScanCandidate(
    val address: String,
    val displayName: String,
    val isLikelyCt14: Boolean,
    val advertisesLegacyService: Boolean,
    val familyEntry: AnytimeConstants.FamilyEntry,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ct14SetupWizard(
    onDismiss: () -> Unit,
    onNavigateToReadiness: () -> Unit = {},
    onComplete: () -> Unit,
) {
    val tag = "Ct14SetupWizard"
    val ui = rememberWizardUiMetrics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(Ct14SetupStep.SCAN) }
    var selectedLabel by remember { mutableStateOf("") }

    BackHandler {
        when (currentStep) {
            Ct14SetupStep.SCAN -> onDismiss()
            else -> currentStep = Ct14SetupStep.SCAN
        }
    }

    LaunchedEffect(currentStep) {
        if (currentStep == Ct14SetupStep.SUCCESS) {
            delay(SENSOR_SETUP_SUCCESS_AUTO_ADVANCE_MS)
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ct14_setup_title)) },
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
            label = "Ct14Wizard"
        ) { step ->
            when (step) {
                Ct14SetupStep.SCAN -> Ct14ScanStep(
                    ui = ui,
                    onNavigateToReadiness = onNavigateToReadiness,
                    onDeviceSelected = { candidate ->
                        selectedLabel = candidate.displayName.ifBlank { candidate.address }
                        currentStep = Ct14SetupStep.CONNECTING
                        scope.launch {
                            try {
                                val sensorId = AnytimeRegistry.addSensor(
                                    context = context,
                                    displayName = candidate.displayName.ifBlank { null },
                                    address = candidate.address,
                                    deviceName = candidate.displayName.ifBlank { null },
                                    qrCodeContent = null,
                                    connectNow = false,
                                )
                                if (sensorId == null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.nobluetooth),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    currentStep = Ct14SetupStep.SCAN
                                    return@launch
                                }
                                AnytimeRegistry.connectSensor(context, sensorId)
                                delay(2000)
                                currentStep = Ct14SetupStep.SUCCESS
                            } catch (t: Throwable) {
                                Log.e(tag, "Failed to add CT-14 sensor: ${t.message}")
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.nobluetooth),
                                    Toast.LENGTH_LONG
                                ).show()
                                currentStep = Ct14SetupStep.SCAN
                            }
                        }
                    }
                )
                Ct14SetupStep.CONNECTING -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SensorSetupConnectingScreen(
                        ui = ui,
                        sensorLabel = selectedLabel.ifBlank { null }
                    )
                }
                Ct14SetupStep.SUCCESS -> Box(
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
private fun Ct14ScanStep(
    ui: WizardUiMetrics,
    onNavigateToReadiness: () -> Unit,
    onDeviceSelected: (Ct14ScanCandidate) -> Unit,
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<Ct14ScanCandidate>>(emptyList()) }
    val scanner = rememberBleScanner()
    var scanPermissionGranted by remember { mutableStateOf(hasBleScanPermissions(context)) }
    var bluetoothEnabled by remember { mutableStateOf(scanner.isBluetoothEnabled()) }
    var scanRetryKey by remember { mutableStateOf(0) }
    var scanError by remember { mutableStateOf<BleDeviceScanner.ScanStartError?>(null) }
    var requestedPermissionOnce by remember { mutableStateOf(false) }
    var showAllDevices by remember { mutableStateOf(false) }
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
                val nameCandidates = listOfNotNull(scanName, record?.deviceName)
                    .mapNotNull { it.trim().takeIf(String::isNotBlank) }

                val bestName = nameCandidates.firstOrNull().orEmpty()
                val advertisesLegacy =
                    record?.serviceUuids?.any {
                        it.uuid == AnytimeConstants.SERVICE_LEGACY_CT2
                    } == true
                val familyEntry = AnytimeConstants.resolveFamily(bestName)
                val nameLooksCt14 = familyEntry.family == AnytimeConstants.Family.CT2
                val isLikelyCt14 = advertisesLegacy || nameLooksCt14

                if (!showAllDevices && !isLikelyCt14) return@startScan

                if (devices.none { it.address.equals(address, ignoreCase = true) }) {
                    devices = devices + Ct14ScanCandidate(
                        address = address,
                        displayName = bestName,
                        isLikelyCt14 = isLikelyCt14,
                        advertisesLegacyService = advertisesLegacy,
                        familyEntry = familyEntry,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ui.horizontalPadding,
                end = ui.horizontalPadding,
                top = ui.spacerMedium,
                bottom = ui.spacerLarge,
            ),
            verticalArrangement = Arrangement.spacedBy(ui.spacerMedium),
        ) {
            item {
                tk.glucodata.ui.CgmReadinessSetupBanner(onOpenReadiness = onNavigateToReadiness)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.ct14_searching_sensors),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { showAllDevices = !showAllDevices }) {
                        Text(
                            if (showAllDevices) stringResource(R.string.show_sensors_only)
                            else stringResource(R.string.see_all_devices)
                        )
                    }
                }
            }
            items(devices) { device ->
                if (!showAllDevices && !device.isLikelyCt14) return@items
                val title = device.displayName.ifBlank { stringResource(R.string.unknown) }
                val supporting = when {
                    device.advertisesLegacyService ->
                        stringResource(R.string.ct14_detected_label, device.address)
                    device.isLikelyCt14 ->
                        "${device.familyEntry.family.displayName} · ${device.address}"
                    else -> stringResource(R.string.ct14_selectable_unrecognized, device.address)
                }
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(supporting) },
                    leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                    modifier = Modifier.clickable { onDeviceSelected(device) }
                )
                HorizontalDivider()
            }

            if (!scanPermissionGranted || !bluetoothEnabled || scanError != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.ct14_no_sensors_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
