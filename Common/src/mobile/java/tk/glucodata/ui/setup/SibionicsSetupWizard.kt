package tk.glucodata.ui.setup

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import tk.glucodata.ui.components.StyledSwitch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.Natives
import tk.glucodata.ui.util.BleDeviceScanner

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SibionicsType(val displayNameRes: Int, val subtype: Int) {
    EU(R.string.eusibionics, 0),
    HEMATONIX(R.string.hematonix, 1),
    CHINESE(R.string.chsibionics, 2),
    SIBIONICS2(R.string.sibionics2, 3)
}

enum class SibionicsSetupStep {
    SELECT_TYPE,
    SCAN_SENSOR,
    SCAN_TRANSMITTER,
    CONNECTING
}

// Helper to decode QR from URI
suspend fun decodeBitmapQr(context: android.content.Context, uri: android.net.Uri): String? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return@withContext null

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            android.util.Log.e("QrDecode", "Error decoding QR", e)
            null
        }
    }
}

/**
 * Manual entry dialog for Sibionics SENSOR codes.
 * User enters Batch (10) and Serial (21) from the label.
 */
@Composable
fun ManualSensorEntryDialog(
    type: SibionicsType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var batch by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var batchValid by remember { mutableStateOf(false) }
    var serialValid by remember { mutableStateOf(false) }

    // Set examples based on sensor type
    val (batchExample, serialExample) = if (type == SibionicsType.SIBIONICS2) {
        "LT46250671C" to "P2250671014ATR89"
    } else {
        "LT41250946H" to "250946432T452CBZ43"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Sensor Codes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Enter the codes printed on the sensor label",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = batch,
                    onValueChange = {
                        batch = it.uppercase().filter { c -> c.isLetterOrDigit() }
                        batchValid = batch.length in 8..16
                    },
                    label = { Text("Batch Code (10)") },
                    placeholder = { Text(batchExample, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    supportingText = { Text("First code on label (8-16 chars), e.g. $batchExample") },
                    singleLine = true,
                    isError = batch.isNotEmpty() && !batchValid,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serial,
                    onValueChange = {
                        serial = it.uppercase().filter { c -> c.isLetterOrDigit() }
                        serialValid = serial.length in 10..20
                    },
                    label = { Text("Serial Number") },
                    placeholder = { Text(serialExample, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    supportingText = { Text("Second code (10-20 chars), e.g. $serialExample") },
                    singleLine = true,
                    isError = serial.isNotEmpty() && !serialValid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(batch + serial) },
                enabled = batchValid && serialValid
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Manual entry dialog for Sibionics 2 TRANSMITTER code.
 * User enters the transmitter name (last 10 chars of QR).
 */
@Composable
fun ManualTransmitterEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val isValid = code.length in 8..12

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Transmitter Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Enter the transmitter ID from the label",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                    label = { Text("Transmitter ID") },
                    placeholder = { Text("TW6CCWHS9L7D", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    supportingText = { Text("8-12 characters from transmitter label") },
                    singleLine = true,
                    isError = code.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(code) },
                enabled = isValid
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


// Helper to construct a "Fake QR" string that satisfies native requirements
// Returns null if input is invalid (prevents native crashes)
fun constructFakeSibionicsQr(input: String, targetLength: Int = 59): String? {
    val trimmedInput = input.trim()

    // VALIDATION: Reject obviously invalid input
    if (trimmedInput.isBlank() || trimmedInput.length < 3) {
        android.util.Log.w("SibionicsSetup", "Input too short: $trimmedInput")
        return null
    }

    // Reject input containing suspicious patterns that suggest malformed data
    // (slashes indicate a full QR URL that wasn't properly parsed)
    if (trimmedInput.count { it == '/' } > 2) {
        android.util.Log.w("SibionicsSetup", "Input contains too many slashes (malformed QR?): $trimmedInput")
        return null
    }

    val magicCode = "0697283164"

    // If input is already a full QR (contains magic code and is long), validate structure
    // FORCE-RECONSTRUCTION: We intentionally bypass this 'fast path' to ensure we strip 
    // any potential invisible characters or trailing garbage that might offset the native
    // parser's fixed-length window.
    // The native parser (namefromSIgegs) relies on strict offsets from the end of the string.
    /*
    if (trimmedInput.length >= 30 && trimmedInput.contains(magicCode)) {
        // Additional check: must not have garbage appended
        if (trimmedInput.length > 80) {
            android.util.Log.w("SibionicsSetup", "QR string too long (corrupted?): len=${trimmedInput.length}")
            return null
        }
        return trimmedInput
    }
    */

    // Extract just the alphanumeric code part if user pasted a partial URL
    val codeToUse = if (trimmedInput.contains("/")) {
        // Try to extract just the code part after last slash
        val lastSlashIdx = trimmedInput.lastIndexOf("/")
        val extracted = trimmedInput.substring(lastSlashIdx + 1).filter { it.isLetterOrDigit() }
        if (extracted.length >= 3) extracted else trimmedInput.filter { it.isLetterOrDigit() }
    } else {
        // Clean input: only keep alphanumeric
        trimmedInput.filter { it.isLetterOrDigit() }
    }

    if (codeToUse.isBlank()) {
        android.util.Log.w("SibionicsSetup", "No valid code extracted from input")
        return null
    }

    // CASE 1: Sensor QR (Sibionics sensor)
    // IMPORTANT: Do NOT truncate batch+serial.
    // Native code relies on full payload length.
    if (targetLength >= 65) {
        // Native code (namefromSIgegs) extracts the last 16 characters treating the LAST character as excluded.
        // i.e., it takes [len-17, len-1).
        // If we append "?", it takes [..., ?). So it includes the char before ?.
        // If we DON'T append "?", it takes [..., lastChar). So it *drops* the last character of the input.
        // Wait, if input is ...ATR89.
        // With "?": [ATR89, ?). Result: ...ATR89. (User reported this as wrong).
        // Without "?": [ATR8, 9). Result: ...ATR8. (This matches camera scan).
        
        val codePart = codeToUse  // batch + serial, full length

        val minPrefixLen = 53 - magicCode.length
        val prefixPadding =
            if (minPrefixLen > 0) "0".repeat(minPrefixLen) else ""

        return magicCode + prefixPadding + codePart
    }

    // CASE 2: Sibionics 2 Transmitter (Target 59)
    val namePart = if (codeToUse.length > 10) {
        codeToUse.takeLast(10)
    } else {
        codeToUse.padEnd(10, '0')
    }

    val paddingLen = targetLength - magicCode.length - namePart.length
    if (paddingLen < 0) return magicCode + namePart

    val padding = "0".repeat(paddingLen)
    return magicCode + padding + namePart
}

/**
 * Modern Material 3 Expressive wizard for setting up Sibionics sensors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SibionicsSetupWizard(
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(SibionicsSetupStep.SELECT_TYPE) }
    var selectedType by remember { mutableStateOf(SibionicsType.EU) }
    var sensorPtr by remember { mutableStateOf(0L) }
    var sensorName by remember { mutableStateOf("") }
    var resetTransmitter by remember { mutableStateOf(false) } // Default false as requested

    // Register callbacks with MainActivity
    DisposableEffect(Unit) {
        val sensorCallback = object : tk.glucodata.MainActivity.SensorScanCallback {
            override fun onResult(name: String?, ptr: Long, libreType: Int) {
                if (name == null || ptr == 0L) {
                    tk.glucodata.Applic.Toaster("Sensor initialization failed")
                    return
                }

                sensorName = name
                sensorPtr = ptr

                Natives.setSensorptrSiSubtype(ptr, selectedType.subtype)

                currentStep =
                    if (selectedType == SibionicsType.SIBIONICS2)
                        SibionicsSetupStep.SCAN_TRANSMITTER
                    else
                        SibionicsSetupStep.CONNECTING
            }
        }

        val transmitterCallback = object : tk.glucodata.MainActivity.TransmitterScanCallback {
            override fun onResult(success: Boolean) {
                if (success) {
                    currentStep = SibionicsSetupStep.CONNECTING
                    finishSetup(onComplete, sensorPtr, resetTransmitter)
                }
            }
        }

        tk.glucodata.MainActivity.onSensorScanResult = sensorCallback
        tk.glucodata.MainActivity.onTransmitterScanResult = transmitterCallback

        onDispose {
            tk.glucodata.MainActivity.onSensorScanResult = null
            tk.glucodata.MainActivity.onTransmitterScanResult = null
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Sibionics Setup") },
                windowInsets = TopAppBarDefaults.windowInsets, // Ensure status bar padding
                navigationIcon = {
                    IconButton(onClick = {
                        tk.glucodata.MainActivity.onSensorScanResult = null
                        tk.glucodata.MainActivity.onTransmitterScanResult = null
                        onDismiss()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            label = "WizardStep"
        ) { step ->
            when (step) {
                SibionicsSetupStep.SELECT_TYPE -> SelectTypeStep(
                    selectedType = selectedType,
                    onTypeSelected = { type ->
                        selectedType = type
                    },
                    onNext = {
                        // Always go to SCAN_SENSOR to ensure sensor structure is created.
                        // Subtype will be applied in SCAN_SENSOR callback.
                        currentStep = SibionicsSetupStep.SCAN_SENSOR
                    },
                    onBack = {
                        onDismiss()
                    }
                )

                SibionicsSetupStep.SCAN_SENSOR -> ScanSensorStep(
                    selectedType = selectedType,
                    onScanClick = {
                        tk.glucodata.MainActivity.launchQrScan(
                            tk.glucodata.MainActivity.REQUEST_BARCODE
                        )
                    },
                    onManualEntry = { code ->
                        val fakeQr = constructFakeSibionicsQr(code, targetLength = 70)
                        if (fakeQr == null) {
                            tk.glucodata.Applic.Toaster("Invalid code format. Please try again.")
                            return@ScanSensorStep
                        }

                        tk.glucodata.MainActivity.thisone?.runOnUiThread {
                            tk.glucodata.PhotoScan.connectSensor(
                                fakeQr,
                                tk.glucodata.MainActivity.thisone,
                                tk.glucodata.MainActivity.REQUEST_BARCODE,
                                0L
                            )
                        }
                    }
                )

                SibionicsSetupStep.SCAN_TRANSMITTER -> ScanTransmitterStep(
                    resetEnabled = resetTransmitter,
                    onResetChanged = { resetTransmitter = it },
                    onScanClick = {
                        if (sensorPtr == 0L) {
                            tk.glucodata.Applic.Toaster("Sensor not initialized. Use 'Scan Sensor' first.")
                            currentStep = SibionicsSetupStep.SCAN_SENSOR
                            return@ScanTransmitterStep
                        }
                        
                        // Apply reset setting before scan
                        Natives.setSensorptrResetSibionics2(sensorPtr, resetTransmitter)

                        tk.glucodata.PhotoScan.scanner(
                            tk.glucodata.MainActivity.thisone,
                            tk.glucodata.MainActivity.REQUEST_BARCODE_SIB2,
                            sensorPtr
                        )
                    },
                    onBack = { currentStep = SibionicsSetupStep.SELECT_TYPE },
                    onDeviceFound = { name ->
                        if (sensorPtr == 0L) {
                             tk.glucodata.Applic.Toaster("Error: Sensor lost. Please restart setup.")
                             currentStep = SibionicsSetupStep.SCAN_SENSOR
                             return@ScanTransmitterStep
                        }
                        
                        val fakeQr = constructFakeSibionicsQr(name, targetLength = 59)
                        if (fakeQr == null) {
                            tk.glucodata.Applic.Toaster("Invalid device name: $name")
                            return@ScanTransmitterStep
                        }

                        Natives.setSensorptrResetSibionics2(sensorPtr, resetTransmitter)
                        val success = Natives.siSensorptrTransmitterScan(sensorPtr, fakeQr)
                        if (success) {
                            currentStep = SibionicsSetupStep.CONNECTING
                            finishSetup(onComplete, sensorPtr, resetTransmitter)
                        } else {
                            tk.glucodata.Applic.Toaster("Failed to connect to $name")
                        }
                    },
                    onManualEntry = { code ->
                        if (sensorPtr == 0L) {
                             tk.glucodata.Applic.Toaster("Sensor not initialized. Use 'Scan Sensor' first.")
                             currentStep = SibionicsSetupStep.SCAN_SENSOR
                             return@ScanTransmitterStep
                        }
                        
                        val fakeQr = constructFakeSibionicsQr(code, targetLength = 59)
                        if (fakeQr == null) {
                            tk.glucodata.Applic.Toaster("Invalid code format. Please try again.")
                            return@ScanTransmitterStep
                        }

                        Natives.setSensorptrResetSibionics2(sensorPtr, resetTransmitter)
                        val success = Natives.siSensorptrTransmitterScan(sensorPtr, fakeQr)
                        if (success) {
                            currentStep = SibionicsSetupStep.CONNECTING
                            finishSetup(onComplete, sensorPtr, resetTransmitter)
                        } else {
                            tk.glucodata.Applic.Toaster("Failed to connect to $code")
                        }
                    }
                )

                SibionicsSetupStep.CONNECTING -> ConnectingStep()
            }
        }
    }
}

@Composable
fun ScanSensorStep(
    selectedType: SibionicsType,
    onScanClick: () -> Unit,
    onManualEntry: (String) -> Unit
) {
    var showManualEntry by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = decodeBitmapQr(context, uri)
                if (decoded != null) {
                    onManualEntry(decoded)
                } else {
                    tk.glucodata.Applic.Toaster("No QR code found in image")
                }
            }
        }
    }

    if (showManualEntry) {
        ManualSensorEntryDialog(
            type = selectedType,
            onDismiss = { showManualEntry = false },
            onConfirm = { code ->
                showManualEntry = false
                onManualEntry(code)
            }
        )
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top 
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp)) 
            Surface(
                modifier = Modifier.size(120.dp).padding(bottom = 24.dp), 
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.padding(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text(
                text = "Scan QR Code",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Scan the QR code on your generic Sibionics sensor box.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        item {
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR Code")
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Add Gallery Button
            OutlinedButton(
                onClick = { 
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    ) 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Select from Gallery")
            }

            // Skip Button for Sibionics 2 - allows bypassing sensor scan since it uses Bluetooth connection
            // We pass a randomly generated valid-looking sensor code.
            if (SibionicsType.SIBIONICS2 == selectedType) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        // random 16-char alphanumeric serial
                        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                        val randomSerial = (1..16).map { chars.random() }.joinToString("")
                        // Append dummy char because native parser drops the last character
                        val randomCode = "FAKEBATCH$" + randomSerial + "X" 
                        onManualEntry(randomCode)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                   Text("Skip (Use Fake Sensor)") 
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { showManualEntry = true }
            ) {
                Text("Enter Code Manually")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectTypeStep(
    selectedType: SibionicsType,
    onTypeSelected: (SibionicsType) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Header with generous spacing
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select Sensor Type",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose your Sibionics sensor variant",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Selection Cards (M3 Expressive - no dividers, surface tonality)
        Column(
            modifier = Modifier
                .weight(1f)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Generous spacing instead of dividers
        ) {
            SibionicsType.values().forEach { type ->
                val isSelected = (type == selectedType)
                
                // Animate container color
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) 
                        MaterialTheme.colorScheme.primaryContainer
                    else 
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = androidx.compose.animation.core.tween(250),
                    label = "containerColor"
                )
                
                // Animate border
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) 
                        MaterialTheme.colorScheme.primary
                    else 
                        Color.Transparent,
                    animationSpec = androidx.compose.animation.core.tween(250),
                    label = "borderColor"
                )
                
                // Selection Card (entire card is the selection target)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            onClick = { onTypeSelected(type) },
                            role = Role.RadioButton
                        ),
                    shape = MaterialTheme.shapes.large,
                    color = containerColor,
                    border = if (isSelected) 
                        androidx.compose.foundation.BorderStroke(2.dp, borderColor)
                    else null,
                    tonalElevation = if (isSelected) 0.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Content
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(type.displayNameRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Selection indicator (subtle checkmark, not radio button)
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Full-width Next button (M3 Expressive - prominent primary action)
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.labelLarge
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Secondary cancel action
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
fun ScanTransmitterStep(
    resetEnabled: Boolean,
    onResetChanged: (Boolean) -> Unit,
    onScanClick: () -> Unit,
    onBack: () -> Unit,
    onDeviceFound: (String) -> Unit,
    onManualEntry: (String) -> Unit
) {
    var isScanning by remember { mutableStateOf(true) } // Active by default
    var foundDevices by remember { mutableStateOf(setOf<String>()) }
    var showManualEntry by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = decodeBitmapQr(context, uri)
                if (decoded != null) {
                    onManualEntry(decoded)
                } else {
                    tk.glucodata.Applic.Toaster("No QR code found in image")
                }
            }
        }
    }

    // Auto-stop scanning after 10s is handled by the flow, but we track UI state
    LaunchedEffect(isScanning) {
        if (isScanning) {
            try {
                BleDeviceScanner.scanForSibionics().collect { name ->
                    if (name !in foundDevices) {
                        foundDevices = foundDevices + name
                    }
                }
            } finally {
                isScanning = false
            }
        }
    }

    if (showManualEntry) {
        ManualTransmitterEntryDialog(
             onDismiss = { showManualEntry = false },
             onConfirm = { code ->
                 showManualEntry = false
                 onManualEntry(code)
             }
        )
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top 
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                modifier = Modifier.size(120.dp).padding(bottom = 24.dp), 
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.padding(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "Scan Transmitter",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
             Text(
                text = "Select a nearby device or scan the QR code.",
                style = MaterialTheme.typography.bodyLarge,
                 textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
        
        item {
            // Reset Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onResetChanged(!resetEnabled) }
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.resetname),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Clear previous state",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                StyledSwitch(
                    checked = resetEnabled,
                    onCheckedChange = null // Handled by Row click
                )
            }
            
            Spacer(Modifier.height(24.dp))

            // Bluetooth Scanning Section (Moved to Top)
            OutlinedButton(
                onClick = { isScanning = !isScanning },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning for devices...")
                } else {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search Bluetooth")
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (foundDevices.isNotEmpty()) {
            item {
                Text(
                    "Found Devices:", 
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(8.dp))
            }
            items(foundDevices.toList()) { device ->
                ListItem(
                    headlineContent = { Text(device) },
                    leadingContent = { Icon(Icons.Default.Bluetooth, null) },
                    modifier = Modifier
                        .clickable { onDeviceFound(device) }
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }
        } else if (isScanning) {
             item {
                Text(
                    "Looking for nearby transmitters...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
             }
        }

        item {
            Text(
               text = "Or scan QR code",
               style = MaterialTheme.typography.labelLarge,
               color = MaterialTheme.colorScheme.primary,
               modifier = Modifier.fillMaxWidth(),
               textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
    
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan Transmitter")
            }
            
            Spacer(Modifier.height(8.dp))

            // Add Gallery Button (Transmitter)
            OutlinedButton(
                onClick = { 
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    ) 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Select from Gallery")
            }

            Spacer(Modifier.height(8.dp))
            
            TextButton(
                onClick = { showManualEntry = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                 Text("Enter Code Manually")
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ConnectingStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
             modifier = Modifier.size(64.dp),
             strokeWidth = 6.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Connecting to Sensor...",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please wait while we establish connection.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun finishSetup(onComplete: () -> Unit, sensorPtr: Long, reset: Boolean) {
    if (sensorPtr != 0L && reset) {
         // This might be redundant if already called in onScanClick, but safe
         Natives.setSensorptrResetSibionics2(sensorPtr, true)
    }

    if (Natives.getusebluetooth()) {
        tk.glucodata.SensorBluetooth.updateDevices()
        tk.glucodata.SuperGattCallback.glucosealarms.setLossAlarm()
    } else {
        Natives.updateUsedSensors()
    }
    tk.glucodata.Applic.wakemirrors()
    tk.glucodata.MainActivity.onSensorScanResult = null
    tk.glucodata.MainActivity.onTransmitterScanResult = null
    onComplete()
}
