@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui

import android.content.Context
import android.text.Html
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.components.*
import tk.glucodata.ui.util.ConnectedButtonGroup
import tk.glucodata.util.DiscoveredMirror
import tk.glucodata.util.MDnsManager

// ── QR Code ──────────────────────────────────────────────────────────────────

@Composable
fun QRCodeImage(content: String, size: Int = 500) {
    if (content.isEmpty()) return
    val bitmap = remember(content) {
        try { com.journeyapps.barcodescanner.BarcodeEncoder().encodeBitmap(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size) }
        catch (_: Exception) { null }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(240.dp))
    }
}

fun injectMirrorJson(jsonstr: String, context: Context): Boolean {
    try {
        val jsonClean = if (jsonstr.endsWith(" MirrorJuggluco")) jsonstr.dropLast(15) else jsonstr
        val json = JSONObject(jsonClean)
        val namesArray = json.getJSONArray("names")
        val names = Array(namesArray.length()) { i -> namesArray.getString(i) }
        val pos = Natives.changebackuphost(
            -1, names, json.getInt("nr"),
            json.optBoolean("detect", false), json.getString("port"),
            json.optBoolean("nums", false), json.optBoolean("stream", false),
            json.optBoolean("scans", false), false, json.optBoolean("receive", false),
            json.optBoolean("activeonly", false), json.optBoolean("passiveonly", false),
            if (json.isNull("pass")) null else json.getString("pass"), 0L,
            if (json.isNull("label")) null else json.getString("label"),
            json.optBoolean("testip", false), json.optBoolean("hasname", false),
            null, false
        )
        if (pos < 0) { Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show(); return false }
        Toast.makeText(context, context.getString(R.string.mirrorscansucces), Toast.LENGTH_SHORT).show()
        tk.glucodata.Applic.wakemirrors()
        return true
    } catch (_: Exception) {
        Toast.makeText(context, "Invalid QR data", Toast.LENGTH_SHORT).show()
        return false
    }
}

// ── Main Screen ──────────────────────────────────────────────────────────────

@Composable
fun MirrorSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var triggerRefresh by remember { mutableIntStateOf(0) }
    var mirrors by remember { mutableStateOf(emptyList<MirrorItemData>()) }
    var showMyQR by remember { mutableStateOf<String?>(null) }

    // mDNS
    val mdnsManager = remember { MDnsManager(context) }
    var isBroadcasting by remember { mutableStateOf(false) }
    var broadcastSenderIdx by remember { mutableIntStateOf(-1) } // index of the sender entry on master
    var discoveredMirrors by remember { mutableStateOf(emptyList<DiscoveredMirror>()) }

    // Pending states
    var scannedQrPayload by remember { mutableStateOf<String?>(null) }
    var pendingNearby by remember { mutableStateOf<DiscoveredMirror?>(null) }

    // Edit sheet state
    var editSheetPos by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(triggerRefresh) { mirrors = getMirrorsList() }

    DisposableEffect(Unit) {
        mdnsManager.discoverServices { mirror ->
            if (discoveredMirrors.none { it.ip == mirror.ip }) {
                discoveredMirrors = discoveredMirrors + mirror
            }
        }
        onDispose {
            mdnsManager.stopDiscovery()
            if (isBroadcasting) mdnsManager.unregisterService()
        }
    }

    // ZXing fallback
    val zxingLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { raw ->
            if (raw.contains("MirrorJuggluco") || raw.contains("\"port\"")) scannedQrPayload = raw
            else Toast.makeText(context, "Invalid QR Code", Toast.LENGTH_SHORT).show()
        }
    }
    val launchScanner: () -> Unit = {
        try {
            GmsBarcodeScanning.getClient(context).startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.let { raw ->
                        if (raw.contains("MirrorJuggluco") || raw.contains("\"port\"")) scannedQrPayload = raw
                        else Toast.makeText(context, "Invalid QR Code", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    zxingLauncher.launch(ScanOptions().apply { setPrompt("Scan Juggluco Mirror QR"); setBeepEnabled(false) })
                }
        } catch (_: Exception) {
            zxingLauncher.launch(ScanOptions().apply { setPrompt("Scan Juggluco Mirror QR"); setBeepEnabled(false) })
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────

    if (scannedQrPayload != null) {
        AlertDialog(
            onDismissRequest = { scannedQrPayload = null },
            icon = { Icon(Icons.Filled.Link, contentDescription = null) },
            title = { Text("Connect to this device?") },
            text = { Text("A Juggluco Mirror QR code was scanned. This will sync glucose data with the remote device.") },
            confirmButton = {
                Button(onClick = {
                    if (injectMirrorJson(scannedQrPayload!!, context)) triggerRefresh++
                    scannedQrPayload = null
                }) { Text("Connect") }
            },
            dismissButton = { OutlinedButton(onClick = { scannedQrPayload = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (pendingNearby != null) {
        val device = pendingNearby!!
        AlertDialog(
            onDismissRequest = { pendingNearby = null },
            icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
            title = { Text("Connect to ${device.name}?") },
            text = { Text("Found at ${device.ip}:${device.port}.\nThis will receive glucose data from \"${device.name}\".") },
            confirmButton = {
                Button(onClick = {
                    Natives.changebackuphost(
                        -1, arrayOf(device.ip), 1, false, device.port.toString(),
                        false, false, false, false, true,
                        false, true, "", System.currentTimeMillis(), device.name,
                        false, true, null, false
                    )
                    Toast.makeText(context, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                    tk.glucodata.Applic.wakemirrors()
                    triggerRefresh++
                    pendingNearby = null
                }) { Text("Connect") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingNearby = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showMyQR != null) {
        AlertDialog(
            onDismissRequest = { showMyQR = null },
            title = { Text(stringResource(R.string.auto_qr)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_with_follower), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    QRCodeImage(showMyQR!!)
                }
            },
            confirmButton = { Button(onClick = { showMyQR = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    // ── Content ──────────────────────────────────────────────────────────

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Natives.resetnetwork()
                        tk.glucodata.Applic.wakemirrors()
                        Toast.makeText(context, context.getString(R.string.reinit_progress), Toast.LENGTH_SHORT).show()
                        triggerRefresh++
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reconnect All")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── QR Row ───────────────────────────────────────────────
            item(key = "qr_section") {
                SectionLabel("Quick Pair", topPadding = 8.dp)
            }
            item(key = "qr_share") {
                SettingsItem(
                    title = "Share My QR",
                    subtitle = "Let another device scan to connect",
                    icon = Icons.Outlined.QrCode,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    position = CardPosition.TOP,
                    onClick = {
                        val idx = Natives.makeHomeSender()
                        if (idx >= 0) {
                            showMyQR = Natives.getbackJson(idx)
                            triggerRefresh++
                        } else {
                            Toast.makeText(context, context.getString(R.string.mirror_error_with_code, idx), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            item(key = "qr_scan") {
                SettingsItem(
                    title = "Scan QR Code",
                    subtitle = "Scan another device's QR to connect",
                    icon = Icons.Outlined.QrCodeScanner,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    position = CardPosition.BOTTOM,
                    onClick = { launchScanner() }
                )
            }

            // ── Local Network ────────────────────────────────────────
            item(key = "network_section") {
                SectionLabel("Local Network")
            }
            item(key = "broadcast") {
                SettingsSwitchItem(
                    title = "Broadcast on Network",
                    subtitle = "Let nearby devices discover this device",
                    icon = Icons.Filled.CellTower,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    checked = isBroadcasting,
                    position = CardPosition.SINGLE,
                    onCheckedChange = { checked ->
                        isBroadcasting = checked
                        if (checked) {
                            // Create a sender entry so master actually listens for connections
                            val idx = Natives.makeHomeSender()
                            if (idx >= 0) {
                                broadcastSenderIdx = idx
                                triggerRefresh++
                            }
                            mdnsManager.registerService(android.os.Build.MODEL ?: "Device")
                        } else {
                            mdnsManager.unregisterService()
                        }
                    }
                )
            }
            if (discoveredMirrors.isNotEmpty()) {
                items(discoveredMirrors, key = { it.ip }) { device ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { pendingNearby = device },
                        shape = cardShape(CardPosition.SINGLE),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.titleSmall)
                                Text("${device.ip}:${device.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // ── Relay ────────────────────────────────────────────────
            item(key = "relay_section") {
                SectionLabel("Relay")
            }
            item(key = "turn") {
                SettingsItem(
                    title = stringResource(R.string.turnserver),
                    subtitle = "TURN relay for remote connections",
                    icon = Icons.Filled.Cloud,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    showArrow = true,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/turnserver") }
                )
            }

            // ── Connections ──────────────────────────────────────────
            item(key = "connections_section") {
                Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp, start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Connections", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { editSheetPos = -1 }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_connection), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (mirrors.isEmpty()) {
                item(key = "empty_msg") {
                    Surface(Modifier.fillMaxWidth(), shape = cardShape(CardPosition.SINGLE), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text("No connections. Use Quick Pair or tap + to add.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                items(mirrors, key = { it.index }) { mirror ->
                    MirrorConnectionCard(
                        mirror = mirror,
                        onEdit = { editSheetPos = mirror.index },
                        onToggle = {
                            Natives.setHostDeactivated(mirror.index, !mirror.isDeactivated)
                            triggerRefresh++
                        },
                        onShowQR = { mirror.index },
                        onDelete = {
                            Natives.deletebackuphost(mirror.index)
                            triggerRefresh++
                        }
                    )
                }
            }
        }
    }

    // ── Edit Bottom Sheet ────────────────────────────────────────────────

    if (editSheetPos != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        MirrorEditSheet(
            pos = editSheetPos!!,
            sheetState = sheetState,
            onDismiss = { editSheetPos = null; triggerRefresh++ }
        )
    }
}

// ── Connection Card (expandable) ─────────────────────────────────────────────

@Composable
fun MirrorConnectionCard(
    mirror: MirrorItemData,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onShowQR: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var qrContent by remember { mutableStateOf<String?>(null) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    if (qrContent != null) {
        AlertDialog(
            onDismissRequest = { qrContent = null },
            title = { Text(mirror.label ?: context.getString(R.string.connection_number, mirror.index)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    QRCodeImage(qrContent!!)
                }
            },
            confirmButton = { Button(onClick = { qrContent = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete connection?") },
            text = { Text("\"${mirror.label ?: mirror.names?.firstOrNull() ?: "Connection"}\" will be removed permanently.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape(CardPosition.SINGLE),
        color = if (mirror.isDeactivated) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        mirror.label?.takeIf { it.isNotEmpty() } ?: mirror.names?.firstOrNull() ?: context.getString(R.string.connection_number, mirror.index),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (mirror.isDeactivated) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else Color.Unspecified
                    )
                    val sub = if (mirror.isDeactivated) stringResource(R.string.deactivated)
                    else if (!mirror.port.isNullOrEmpty()) ":${mirror.port}" else null
                    if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.graphicsLayer { rotationZ = chevronRotation })
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    if (!mirror.isDeactivated) {
                        AndroidView<TextView>(
                            factory = { ctx -> TextView(ctx).apply { textSize = 13f } },
                            update = { it.text = Html.fromHtml(mirror.status, Html.FROM_HTML_MODE_LEGACY) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(stringResource(R.string.delete))
                        }
                        TextButton(onClick = onToggle) {
                            Text(if (mirror.isDeactivated) stringResource(R.string.enable) else stringResource(R.string.disable))
                        }
                        TextButton(onClick = { qrContent = Natives.getbackJson(mirror.index) }) {
                            Text(stringResource(R.string.qr))
                        }
                        TextButton(onClick = onEdit) {
                            Text(stringResource(R.string.edit))
                        }
                    }
                }
            }
        }
    }
}

// ── Edit Sheet ───────────────────────────────────────────────────────────────

enum class ConnectionType { LOCAL, ICE, DIRECT }

@Composable
fun MirrorEditSheet(pos: Int, sheetState: SheetState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isNew = pos == -1

    var connectionType by remember { mutableStateOf(
        if (isNew) ConnectionType.LOCAL
        else if (Natives.getICEside(pos)) ConnectionType.ICE
        else if (Natives.getbackupHasHostname(pos)) ConnectionType.DIRECT
        else ConnectionType.LOCAL
    )}
    var port by remember { mutableStateOf(if (!isNew) Natives.getbackuphostport(pos) ?: "8795" else "8795") }
    var password by remember { mutableStateOf(if (!isNew) Natives.getbackuppassword(pos) ?: "" else "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var iceLabel by remember { mutableStateOf(if (!isNew) Natives.getbackuplabel(pos) ?: "" else "") }
    var hostname by remember { mutableStateOf(if (!isNew) Natives.getbackupIPs(pos)?.firstOrNull() ?: "" else "") }
    var isSending by remember { mutableStateOf(if (!isNew) Natives.getbackuphostnums(pos) else false) }
    var isReceiving by remember { mutableStateOf(if (!isNew) (Natives.getbackuphostreceive(pos) and 2) != 0 else true) }

    fun save() {
        val isICE = connectionType == ConnectionType.ICE
        val isDirect = connectionType == ConnectionType.DIRECT
        val isLocal = connectionType == ConnectionType.LOCAL
        val finalNames = if ((isDirect || isLocal) && hostname.isNotEmpty()) arrayOf(hostname) else arrayOf("")
        val finalPort = port.ifEmpty { "8795" }
        val finalIceLabel = if (isICE) iceLabel else ""

        Natives.changebackuphost(
            if (isNew) -1 else pos, finalNames, finalNames.size,
            isLocal, finalPort,
            isSending, isSending, isSending, false, isReceiving,
            false, false, password, System.currentTimeMillis(), finalIceLabel,
            isLocal && hostname.isEmpty(), isDirect || (!isICE && hostname.isNotEmpty()),
            finalIceLabel.takeIf { isICE }, isReceiving
        )
        tk.glucodata.Applic.wakemirrors()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (isNew) stringResource(R.string.add_connection) else stringResource(R.string.edit_connection),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            // Connection Type
            SectionLabel("Connection Type", topPadding = 0.dp, modifier = Modifier.padding(horizontal = 24.dp))
            ConnectedButtonGroup(
                options = ConnectionType.entries.toList(),
                selectedOption = connectionType,
                onOptionSelected = { connectionType = it },
                label = { option ->
                    Text(when (option) {
                        ConnectionType.LOCAL -> "Local"
                        ConnectionType.ICE -> "ICE"
                        ConnectionType.DIRECT -> "Direct IP"
                    })
                },
                itemHeight = 40.dp,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )

            // Type hint
            val typeHint = when (connectionType) {
                ConnectionType.LOCAL -> "Same Wi-Fi. IP auto-detected."
                ConnectionType.ICE -> "Across networks via STUN/TURN."
                ConnectionType.DIRECT -> "Specific IP/hostname."
            }
            Text(typeHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            // Fields
            SectionLabel("Details", modifier = Modifier.padding(horizontal = 24.dp))
            Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (connectionType) {
                    ConnectionType.LOCAL -> {
                        OutlinedTextField(
                            value = hostname, onValueChange = { hostname = it },
                            label = { Text("IP Address (optional)") },
                            supportingText = { Text("Leave blank to auto-detect") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                    ConnectionType.ICE -> {
                        OutlinedTextField(
                            value = iceLabel, onValueChange = { iceLabel = it },
                            label = { Text("ICE Label") },
                            supportingText = { Text("Must match on both devices") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                    ConnectionType.DIRECT -> {
                        OutlinedTextField(
                            value = hostname, onValueChange = { hostname = it },
                            label = { Text("Hostname / IP Address") },
                            supportingText = { Text("e.g. 192.168.1.100 or myserver.com") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                }
                OutlinedTextField(
                    value = port, onValueChange = { port = it },
                    label = { Text(stringResource(R.string.port)) },
                    supportingText = { Text("Default: 8795") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Role
            SectionLabel("Role", modifier = Modifier.padding(horizontal = 24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                SettingsSwitchItem(
                    title = "Receive Data", subtitle = "This device is a Follower",
                    checked = isReceiving, onCheckedChange = { isReceiving = it },
                    icon = Icons.Filled.Download, iconTint = MaterialTheme.colorScheme.tertiary,
                    position = CardPosition.TOP
                )
                SettingsSwitchItem(
                    title = "Send Data", subtitle = "This device is a Master",
                    checked = isSending, onCheckedChange = { isSending = it },
                    icon = Icons.Filled.Upload, iconTint = MaterialTheme.colorScheme.tertiary,
                    position = CardPosition.BOTTOM
                )
            }

            // Password
            SectionLabel("Security", modifier = Modifier.padding(horizontal = 24.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                supportingText = { Text("Must match on both devices") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null)
                    }
                }
            )

            // Save
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isNew) {
                    OutlinedButton(
                        onClick = { Natives.deletebackuphost(pos); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.delete)) }
                }
                Button(onClick = { save(); onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

// ── Data ──────────────────────────────────────────────────────────────────────

data class MirrorItemData(
    val index: Int, val label: String?, val names: Array<String>?,
    val port: String?, val isDeactivated: Boolean, val status: String
)

fun getMirrorsList(): List<MirrorItemData> {
    val mirrors = mutableListOf<MirrorItemData>()
    for (i in 0 until 64) {
        val names = Natives.getbackupIPs(i)
        if (names != null) {
            mirrors.add(MirrorItemData(i, Natives.getbackuplabel(i), names, Natives.getbackuphostport(i), Natives.getHostDeactivated(i), Natives.mirrorStatus(i) ?: ""))
        }
    }
    return mirrors
}
