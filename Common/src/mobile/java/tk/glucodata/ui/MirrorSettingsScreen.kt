package tk.glucodata.ui

import android.text.Html
import android.text.format.DateFormat
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import tk.glucodata.R
import tk.glucodata.Natives
import tk.glucodata.QRmake
import tk.glucodata.TurnServer
import java.util.Date
import java.util.Calendar
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

@Composable
fun QRCodeImage(content: String, size: Int = 500) {
    if (content.isEmpty()) return
    val bitmap = remember(content) {
        try {
            com.journeyapps.barcodescanner.BarcodeEncoder().encodeBitmap(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        } catch (e: Exception) {
            null
        }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(250.dp)
        )
    }
}

@Composable
fun AutoQRDialog(onDismiss: () -> Unit, act: tk.glucodata.MainActivity) {
    var qrContent by remember { mutableStateOf<String?>(null) }
    var qrTitle by remember { mutableStateOf("") }

    if (qrContent != null) {
        AlertDialog(
            onDismissRequest = { qrContent = null },
            title = { Text(qrTitle) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    QRCodeImage(qrContent!!)
                }
            },
            confirmButton = {
                TextButton(onClick = { qrContent = null }) { Text(stringResource(R.string.close)) }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.auto_qr)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.sendto), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { 
                            try {
                                val idx = Natives.makeHomeSender()
                                if (idx >= 0) {
                                    qrContent = Natives.getbackJson(idx)
                                    qrTitle = act.getString(R.string.home_net_sender)
                                } else {
                                     android.widget.Toast.makeText(act, act.getString(R.string.mirror_error_with_code, idx), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: UnsatisfiedLinkError) {
                                android.widget.Toast.makeText(act, act.getString(R.string.not_implemented_natively), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(stringResource(R.string.homenet)) }
                        
                        Button(onClick = { 
                            try {
                                val idx = Natives.makeICESender()
                                if (idx >= 0) {
                                    qrContent = Natives.getbackJson(idx)
                                    qrTitle = act.getString(R.string.internet_sender)
                                } else {
                                     android.widget.Toast.makeText(act, act.getString(R.string.mirror_error_with_code, idx), android.widget.Toast.LENGTH_SHORT).show()
                                }
                             } catch (e: UnsatisfiedLinkError) {
                                android.widget.Toast.makeText(act, act.getString(R.string.not_implemented_natively), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(stringResource(R.string.internet)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.receivefrom), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { 
                            try {
                                val idx = Natives.makeHomeReceiver()
                                if (idx >= 0) {
                                    qrContent = Natives.getbackJson(idx)
                                    qrTitle = act.getString(R.string.home_net_receiver)
                                } else {
                                     android.widget.Toast.makeText(act, act.getString(R.string.mirror_error_with_code, idx), android.widget.Toast.LENGTH_SHORT).show()
                                }
                             } catch (e: UnsatisfiedLinkError) {
                                android.widget.Toast.makeText(act, act.getString(R.string.not_implemented_natively), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(stringResource(R.string.homenet)) }
                        
                        Button(onClick = { 
                            try {
                                val idx = Natives.makeICEReceiver()
                                if (idx >= 0) {
                                    qrContent = Natives.getbackJson(idx)
                                    qrTitle = act.getString(R.string.internet_receiver)
                                } else {
                                     android.widget.Toast.makeText(act, act.getString(R.string.mirror_error_with_code, idx), android.widget.Toast.LENGTH_SHORT).show()
                                }
                             } catch (e: UnsatisfiedLinkError) {
                                android.widget.Toast.makeText(act, act.getString(R.string.not_implemented_natively), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(stringResource(R.string.internet)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
            dismissButton = {
                TextButton(onClick = { /* Help action */ }) { Text(stringResource(R.string.help)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var triggerRefresh by remember { mutableStateOf(0) }
    var mirrors by remember { mutableStateOf(emptyList<MirrorItemData>()) }
    var showAutoQR by remember { mutableStateOf(false) }

    LaunchedEffect(triggerRefresh) {
        mirrors = getMirrorsList()
    }

    if (showAutoQR) {
        (context as? tk.glucodata.MainActivity)?.let { AutoQRDialog(onDismiss = { showAutoQR = false }, act = it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mirror_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                         (context as? tk.glucodata.MainActivity)?.let { TurnServer.show(it, it.findViewById(android.R.id.content)) }
                     }) {
                         Text(stringResource(R.string.turnserver))
                     }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate("settings/mirror/edit/-1")
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_connection))
            }
        },
        bottomBar = {
             BottomAppBar {
                 IconButton(onClick = { 
                      tk.glucodata.help.help(context.getString(R.string.mirror_settings_help_text), context as android.app.Activity) 
                  }) {
                     Icon(Icons.Filled.Help, contentDescription = stringResource(R.string.help))
                  }
                  Spacer(Modifier.weight(1f))
                  TextButton(onClick = { tk.glucodata.Applic.wakemirrors(); android.widget.Toast.makeText(context, context.getString(R.string.syncing), android.widget.Toast.LENGTH_SHORT).show() }) {
                      Icon(Icons.Filled.Sync, contentDescription = null)
                      Spacer(Modifier.width(4.dp))
                      Text(stringResource(R.string.sync))
                  }
                  TextButton(onClick = { Natives.resetnetwork(); tk.glucodata.Applic.wakemirrors(); android.widget.Toast.makeText(context, context.getString(R.string.reinit_progress), android.widget.Toast.LENGTH_SHORT).show() }) {
                      Icon(Icons.Filled.Refresh, contentDescription = null)
                      Spacer(Modifier.width(4.dp))
                      Text(stringResource(R.string.reinit))
                  }
                  TextButton(onClick = { showAutoQR = true }) {
                      // AutoQR Icon? Using generic Code/QrCode or just text
                      Text(stringResource(R.string.auto_qr))
                  }
              }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mirrors) { mirror ->
                MirrorItemRow(mirror, navController) {
                    triggerRefresh++
                }
            }
        }
    }
}

data class MirrorItemData(
    val index: Int,
    val label: String?,
    val names: Array<String>?,
    val port: String?,
    val isDeactivated: Boolean,
    val status: String
)

@Composable
fun MirrorItemRow(mirror: MirrorItemData, navController: NavController, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var showStatusDialog by remember { mutableStateOf(false) }
    var qrContent by remember { mutableStateOf<String?>(null) }

    if (qrContent != null) {
        AlertDialog(
            onDismissRequest = { qrContent = null },
             title = { Text(if (!mirror.label.isNullOrEmpty()) mirror.label else stringResource(R.string.connection_number, mirror.index)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    QRCodeImage(qrContent!!)
                }
            },
            confirmButton = {
                TextButton(onClick = { qrContent = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showStatusDialog = true },
        colors = CardDefaults.cardColors(
            containerColor = if (mirror.isDeactivated) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.6f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (!mirror.label.isNullOrEmpty()) mirror.label else mirror.names?.firstOrNull() ?: context.getString(R.string.connection_number, mirror.index),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (mirror.isDeactivated) Color.Gray else Color.Unspecified
                )
                Spacer(Modifier.weight(1f))
                if (!mirror.port.isNullOrEmpty()) {
                    Text(mirror.port, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            if (!mirror.isDeactivated) {
                 AndroidView<TextView>(factory = { ctx ->
                     TextView(ctx).apply {
                         textSize = 12f
                         setTextColor(android.graphics.Color.WHITE)
                     }
                 }, update = {
                      it.text = Html.fromHtml(mirror.status, Html.FROM_HTML_MODE_LEGACY)
                 })
            } else {
                Text(stringResource(R.string.deactivated), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showStatusDialog) {
        MirrorStatusDialog(
            mirror = mirror,
            onDismiss = { showStatusDialog = false },
            onModify = {
                showStatusDialog = false
                navController.navigate("settings/mirror/edit/${mirror.index}")
            },
            onToggleOff = {
                 Natives.setHostDeactivated(mirror.index, !mirror.isDeactivated)
                 onRefresh()
            },
            onQR = {
                 val json = Natives.getbackJson(mirror.index)
                 qrContent = json
                 showStatusDialog = false
            }
        )
    }
}

@Composable
fun MirrorStatusDialog(
    mirror: MirrorItemData,
    onDismiss: () -> Unit,
    onModify: () -> Unit,
    onToggleOff: () -> Unit,
    onQR: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_number, mirror.index)) },
        text = {
            Column {
                 AndroidView<TextView>(factory = { ctx ->
                     TextView(ctx).apply {
                         textSize = 14f
                         setTextColor(android.graphics.Color.DKGRAY) 
                     }
                 }, update = {
                      it.text = Html.fromHtml(mirror.status, Html.FROM_HTML_MODE_LEGACY)
                 })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onModify) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onQR) { Text(stringResource(R.string.qr)) }
                TextButton(onClick = onToggleOff) { 
                    Text(if (mirror.isDeactivated) stringResource(R.string.enable) else stringResource(R.string.disable)) 
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorEditScreen(navController: NavController, pos: Int) {
    val context = LocalContext.current
    val isNew = pos == -1
    
    // --- STATE INITIALIZATION ---
    var isICE by remember { mutableStateOf(if (!isNew) Natives.getICEside(pos) else true) } 
    
    // Header
    var port by remember { mutableStateOf(if (!isNew) Natives.getbackuphostport(pos) ?: "8795" else "8795") }
    var hasHostname by remember { mutableStateOf(if (!isNew) Natives.getbackupHasHostname(pos) else false) } 
    var detect by remember { mutableStateOf(if (!isNew) Natives.detectIP(pos) else false) }

    // ICE Specific
    var iceLabel by remember { mutableStateOf(if (!isNew) Natives.getbackuplabel(pos) ?: "" else "") }
    var iceSide by remember { mutableIntStateOf(if (!isNew && Natives.getICEside(pos)) 1 else 0) } 

    // Host Row
    var hostname by remember { mutableStateOf(if (!isNew) Natives.getbackupIPs(pos)?.firstOrNull() ?: "" else "") }

    // Test Row
    var testIP by remember { mutableStateOf(if (!isNew) Natives.getbackuptestip(pos) else false) }
    var testLabel by remember { mutableStateOf(if (!isNew) Natives.getbackupTestLabel(pos) else false) }

    // Mode
    var mode by remember { mutableIntStateOf(
        if (isNew) 2 else {
            val p = Natives.getbackuphostpassive(pos)
            val a = Natives.getbackupActiveOnly(pos)
            if (p) 0 else if (a) 1 else 2
        }
    ) }

    // Send/Receive
    var sendAmounts by remember { mutableStateOf(if (!isNew) Natives.getbackuphostnums(pos) else false) }
    var sendScans by remember { mutableStateOf(if (!isNew) Natives.getbackuphostscans(pos) else false) }
    var sendStream by remember { mutableStateOf(if (!isNew) Natives.getbackuphoststream(pos) else false) }
    var receiveFrom by remember { mutableStateOf(if (!isNew) (Natives.getbackuphostreceive(pos) and 2) != 0 else false) } 

    // Time
    var startMode by remember { mutableIntStateOf(0) }
    var customDate by remember { mutableLongStateOf(Natives.getstarttime()) } 

    var password by remember { mutableStateOf(if (!isNew) Natives.getbackuppassword(pos) ?: "" else "") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isDeleted by remember { mutableStateOf(false) }

    // Auto-save logic
    DisposableEffect(Unit) {
        onDispose {
            if (!isDeleted) {
                // Natives.changebackuphost expects a non-empty array if hashostname is true
                // and blindly accesses index 0. We must safeguard against empty arrays.
                val names = if (hostname.isNotEmpty()) arrayOf(hostname) else arrayOf("")
                val activeOnly = mode == 1
                val passiveOnly = mode == 0
                val sideBool = iceSide == 1
                
                val finalStartTime = when(startMode) {
                    0 -> Natives.getstarttime() 
                    1 -> System.currentTimeMillis()
                    else -> customDate
                }

                Natives.changebackuphost(
                    if (isNew) -1 else pos,
                    names,
                    names.size,
                    detect,
                    port,
                    sendAmounts,
                    sendStream,
                    sendScans,
                    false, 
                    receiveFrom,
                    activeOnly,
                    passiveOnly,
                    password,
                    finalStartTime,
                    iceLabel,
                    testIP,
                    hasHostname, 
                    iceLabel.takeIf { isICE },
                    sideBool
                )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) stringResource(R.string.add_connection) else stringResource(R.string.edit_connection)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // Help
                    IconButton(onClick = {
                         tk.glucodata.help.help(context.getString(R.string.mirror_connection_settings_help_text), context as android.app.Activity) 
                    }) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.help))
                    }
                    
                    // Delete (only if not new, or maybe allow deleting new? new hasn't been saved yet so deleting it just means popping)
                    // Actually if it's new, "Delete" is effectively "Cancel". But usually Delete is for existing items.
                    // If it is new, we can just pop without saving by setting isDeleted=true.
                    IconButton(onClick = {
                        if (!isNew) {
                            Natives.deletebackuphost(pos) 
                        }
                        isDeleted = true
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- ROW 1: Port | Hostname Check | Detect ---
            if (!isICE) { // Only show if ICE is OFF? Screen shots logic
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text(stringResource(R.string.port)) },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasHostname, onCheckedChange = { hasHostname = it })
                        Text(stringResource(R.string.hostname))
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = detect, onCheckedChange = { detect = it })
                        Text(stringResource(R.string.detect))
                    }
                }
            }

            // --- ROW 2: ICE Checkbox ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isICE, onCheckedChange = { isICE = it })
                Text(stringResource(R.string.ICE))
                if (isICE) {
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        value = iceLabel,
                        onValueChange = { iceLabel = it },
                        label = { Text(stringResource(R.string.icelabel)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = iceSide == 0, onClick = { iceSide = 0 })
                    Text(stringResource(R.string.zero))
                    RadioButton(selected = iceSide == 1, onClick = { iceSide = 1 })
                    Text(stringResource(R.string.one))
                }
            }

            // --- ROW 3: Hostname/IP Field ---
            if (!isICE && hasHostname) { // Show Hostname only if Checkbox checked? Or always for Host?
                // Screenshot 2 (Hostname ON) shows Hostname field.
                // Screenshot 1 (Hostname OFF) shows Test IP (no host field in middle).
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { hostname = it },
                    label = { Text(stringResource(R.string.hostname_ip)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // --- ROW 4: Test IP | Test Label ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                 if (!isICE && !hasHostname) { // Test IP only when Hostname OFF?
                     Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = testIP, onCheckedChange = { testIP = it })
                        Text(stringResource(R.string.testip))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = testLabel, onCheckedChange = { testLabel = it })
                    Text(stringResource(R.string.testlabel))
                }
                
                // Master Label (User) ?
                if (!isICE && hasHostname) {
                    OutlinedTextField(
                        value = iceLabel, // Reusing icelabel for master/user?
                        onValueChange = { iceLabel = it },
                        label = { Text(stringResource(R.string.master)) },
                        modifier = Modifier.width(120.dp),
                         singleLine = true
                    )
                }
            }
            // --- ROW 5: Mode ---
            if (!isICE) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == 0, onClick = { mode = 0 })
                        Text(stringResource(R.string.passiveonly))
                    } 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == 1, onClick = { mode = 1 })
                        Text(stringResource(R.string.activeonly))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == 2, onClick = { mode = 2 })
                        Text(stringResource(R.string.both))
                    }
                }
            }

            // --- ROW 6: Send/Receive ---
            Text(stringResource(R.string.send_receive), style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = receiveFrom, onCheckedChange = { receiveFrom = it })
                        Text(stringResource(R.string.receivefrom))
                    }
                    Text(stringResource(R.string.send_to_colon), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start=12.dp, top=4.dp))
                }
                Column {
                   Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sendAmounts, onCheckedChange = { sendAmounts = it })
                        Text(stringResource(R.string.amountsname))
                   } 
                   Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sendScans, onCheckedChange = { sendScans = it })
                        Text(stringResource(R.string.scansname))
                   }
                   Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sendStream, onCheckedChange = { sendStream = it })
                        Text(stringResource(R.string.streamname))
                   }
                }
            }
            
            HorizontalDivider()

            // --- ROW 7: Time ---
            if (!isICE && hasHostname) {
                Text(stringResource(R.string.data_present_until), style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = startMode == 0, onClick = { startMode = 0 })
                        Text(stringResource(R.string.start))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = startMode == 1, onClick = { startMode = 1 })
                        Text(stringResource(R.string.now))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = startMode == 2, onClick = { startMode = 2 })
                        val dateStr = if (startMode == 2) DateFormat.format("yyyy-MM-dd", customDate).toString() else context.getString(R.string.date)
                        Text(dateStr)
                    }
                }
            }

            // --- ROW 8: Password ---
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password))
                    }
                }
            )

        }
    }
}

fun getMirrorsList(): List<MirrorItemData> {
    val mirrors = mutableListOf<MirrorItemData>()
    for (i in 0 until 64) {
        val names = Natives.getbackupIPs(i)
        if (names != null) {
            val label = Natives.getbackuplabel(i)
            val port = Natives.getbackuphostport(i)
            val deactivated = Natives.getHostDeactivated(i)
            val status = Natives.mirrorStatus(i) ?: ""
            mirrors.add(MirrorItemData(i, label, names, port, deactivated, status))
        }
    }
    return mirrors
}
