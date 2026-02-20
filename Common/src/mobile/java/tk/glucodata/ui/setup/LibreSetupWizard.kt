package tk.glucodata.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tk.glucodata.Natives
import tk.glucodata.R

/**
 * Edit 63d / 67c: Multi-step Libre setup wizard.
 * Step 0: NFC scan to activate the sensor (primary action).
 * Step 1: LibreView credentials (optional — provides Account ID for Libre 3).
 *
 * The wizard also serves as the entry point for LibreView configuration,
 * accessible from the sensor bottom sheet / empty state / dashboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibreSetupWizard(
    onDismiss: () -> Unit,
    onScanNfc: () -> Unit
) {
    val ui = rememberWizardUiMetrics()
    var currentStep by remember { mutableIntStateOf(0) }

    // LibreView state — load from Natives
    var email by remember { mutableStateOf(Natives.getlibreemail() ?: "") }
    var password by remember { mutableStateOf(Natives.getlibrepass() ?: "") }
    var isActive by remember { mutableStateOf(Natives.getuselibreview()) }
    var isRussia by remember { mutableStateOf(Natives.getLibreCountry() == 4) }
    var showPassword by remember { mutableStateOf(false) }
    val accountId = remember { Natives.getlibreAccountIDnumber() }
    var accountIdFetched by remember { mutableStateOf(accountId > 0L) }
    var statusText by remember { mutableStateOf("") }
    var isFetchingAccountId by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Save credentials to native on step transition or dismiss
    fun saveCredentials() {
        Natives.setlibreemail(email)
        Natives.setlibrepass(password)
        Natives.setuselibreview(isActive)
        Natives.setLibreCountry(if (isRussia) 4 else 0)
    }

    DisposableEffect(Unit) {
        onDispose { saveCredentials() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.libre_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 0) currentStep-- else onDismiss()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Step indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1f) / 2f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ui.horizontalPadding, vertical = ui.spacerSmall),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            when (currentStep) {
                0 -> NfcScanStep(
                    ui = ui,
                    onScanNfc = {
                        saveCredentials()
                        onScanNfc()
                    },
                    onSetupLibreView = {
                        currentStep = 1
                    }
                )
                1 -> LibreViewStep(
                    ui = ui,
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    isActive = isActive,
                    onActiveChange = { isActive = it },
                    isRussia = isRussia,
                    onRussiaChange = { isRussia = it },
                    showPassword = showPassword,
                    onShowPasswordChange = { showPassword = it },
                    accountIdFetched = accountIdFetched,
                    statusText = statusText,
                    isFetching = isFetchingAccountId,
                    onGetAccountId = {
                        saveCredentials()
                        Natives.askServerforAccountID()
                        statusText = "Requesting Account ID..."
                        isFetchingAccountId = true
                        // Edit 64a: Poll every 500ms for up to 30s instead of blind 5s delay.
                        // The C++ thread downloads config + authenticates asynchronously.
                        // We watch for either: Account ID obtained, or status changed to a
                        // terminal value (error or success).
                        coroutineScope.launch {
                            val initialStatus = statusText
                            var elapsed = 0
                            while (elapsed < 30_000) {
                                delay(500)
                                elapsed += 500
                                val currentStatus = tk.glucodata.Libreview.getStatus()
                                val gotId = Natives.getlibreAccountIDnumber() > 0L
                                if (gotId) {
                                    statusText = currentStatus
                                    accountIdFetched = true
                                    break
                                }
                                // Update status text so user sees progress (e.g. "libreconfig", error messages)
                                if (currentStatus.isNotEmpty()) {
                                    statusText = currentStatus
                                }
                                // If status changed to a terminal error, stop polling
                                val isTerminal = currentStatus.contains("failed", ignoreCase = true) ||
                                    currentStatus.contains("error", ignoreCase = true) ||
                                    currentStatus.contains("locked", ignoreCase = true) ||
                                    currentStatus.contains("no credentials", ignoreCase = true) ||
                                    currentStatus.contains("ResponseCode", ignoreCase = true)
                                if (isTerminal) break
                            }
                            if (!accountIdFetched) {
                                val finalStatus = tk.glucodata.Libreview.getStatus()
                                statusText = if (finalStatus.isNotEmpty()) finalStatus else "Timed out — check credentials and try again"
                            }
                            isFetchingAccountId = false
                        }
                    },
                    onDone = {
                        saveCredentials()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun LibreViewStep(
    ui: WizardUiMetrics,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    isRussia: Boolean,
    onRussiaChange: (Boolean) -> Unit,
    showPassword: Boolean,
    onShowPasswordChange: (Boolean) -> Unit,
    accountIdFetched: Boolean,
    statusText: String,
    isFetching: Boolean,
    onGetAccountId: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ui.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(ui.spacerMedium)
    ) {
        // Header
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(if (ui.compact) 60.dp else 72.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (ui.compact) 14.dp else 16.dp)
            )
        }

        Text(
            text = stringResource(R.string.libre_setup_step_libreview),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.libre_setup_step_libreview_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(ui.spacerSmall))

        // Active toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.libreview_active)) },
            supportingContent = { Text(stringResource(R.string.libreview_active_desc)) },
            trailingContent = {
                Switch(checked = isActive, onCheckedChange = onActiveChange)
            }
        )

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.libreview_email)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.libreview_password)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { onShowPasswordChange(!showPassword) }) {
                    Icon(imageVector = image, contentDescription = null)
                }
            }
        )

        // Russia toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.libreview_russia)) },
            trailingContent = {
                Switch(checked = isRussia, onCheckedChange = onRussiaChange)
            }
        )

        // Get Account ID button
        val hasCredentials = email.isNotBlank() && password.isNotBlank()
        OutlinedButton(
            onClick = onGetAccountId,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasCredentials && !isFetching
        ) {
            if (isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.libreview_get_account_id))
        }

        // Status / Account ID feedback
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        if (accountIdFetched) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Account ID obtained",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom button
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.libre_setup_done))
        }
    }
}

@Composable
private fun NfcScanStep(
    ui: WizardUiMetrics,
    onScanNfc: () -> Unit,
    onSetupLibreView: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ui.horizontalPadding)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(ui.heroSize)
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ui.heroInnerPadding)
            )
        }

        Spacer(modifier = Modifier.height(ui.spacerLarge))

        Text(
            text = stringResource(R.string.libre_setup_step_scan),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(ui.spacerSmall))

        Text(
            text = stringResource(R.string.libre_nfc_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(ui.spacerLarge + ui.spacerMedium))

        Button(
            onClick = onScanNfc,
            modifier = Modifier
                .fillMaxWidth()
                .height(ui.buttonHeight)
        ) {
            Icon(Icons.Default.Nfc, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.scan_libre_sensor))
        }

        Spacer(modifier = Modifier.height(ui.spacerMedium))

        // Edit 67c: LibreView setup is optional step 2 — accessible via text button
        TextButton(
            onClick = onSetupLibreView,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.libre_setup_step_libreview))
        }
    }
}
