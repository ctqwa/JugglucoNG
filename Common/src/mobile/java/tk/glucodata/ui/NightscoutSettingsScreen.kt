package tk.glucodata.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.drivers.nightscout.NightscoutFollowerRegistry
import tk.glucodata.ui.components.CardPosition
import tk.glucodata.ui.components.MasterSwitchCard
import tk.glucodata.ui.components.SettingsSwitchItem
import tk.glucodata.ui.components.cardShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightscoutSettingsScreen(navController: NavController) {
    val context = LocalContext.current

    var url by rememberSaveable { mutableStateOf(Natives.getnightuploadurl() ?: "") }
    var secret by rememberSaveable { mutableStateOf(Natives.getnightuploadsecret() ?: "") }
    var isActive by rememberSaveable { mutableStateOf(Natives.getuseuploader()) }
    var sendTreatments by rememberSaveable { mutableStateOf(Natives.getpostTreatments()) }
    var isV3 by rememberSaveable { mutableStateOf(Natives.getnightscoutV3()) }
    var followServer by rememberSaveable { mutableStateOf(NightscoutFollowerRegistry.loadConfig(context).enabled) }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    var lastResponseCode by rememberSaveable { mutableStateOf(0) }
    var lastAttemptTime by rememberSaveable { mutableStateOf(0L) }
    var lastSuccessTime by rememberSaveable { mutableStateOf(0L) }
    var retryMinutes by rememberSaveable { mutableStateOf(0) }
    var uploaderRunning by rememberSaveable { mutableStateOf(false) }

    fun persistSettings() {
        Natives.setNightUploader(url.trim(), secret.trim(), isActive, isV3)
        Natives.setpostTreatments(sendTreatments)
        NightscoutFollowerRegistry.saveConfig(context, followServer, url, secret)
    }

    fun refreshStatus() {
        lastResponseCode = Natives.getnightscoutlastresponsecode()
        lastAttemptTime = Natives.getnightscoutlastattempttime()
        lastSuccessTime = Natives.getnightscoutlastsuccesstime()
        retryMinutes = Natives.getnightscoutretryminutes()
        uploaderRunning = Natives.getnightscoutuploaderrunning()
    }

    LaunchedEffect(isActive) {
        while (true) {
            refreshStatus()
            delay(if (isActive) 5_000L else 15_000L)
        }
    }

    DisposableEffect(Unit) {
        refreshStatus()
        onDispose { persistSettings() }
    }

    fun formatStatusTime(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return context.getString(R.string.nightscout_status_never)
        val formatted = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT,
            java.util.Locale.getDefault()
        ).format(java.util.Date(epochSeconds * 1000L))
        return formatted
    }

    val responseSummary = when {
        !isActive -> context.getString(R.string.nightscout_status_paused)
        lastResponseCode == 0 && lastAttemptTime <= 0L -> context.getString(R.string.nightscout_status_waiting)
        lastResponseCode == -2 -> context.getString(R.string.nightscout_status_response_invalid_url)
        lastResponseCode in 200..299 -> context.getString(R.string.nightscout_status_response_ok, lastResponseCode)
        lastResponseCode == 404 -> context.getString(R.string.nightscout_status_response_404)
        lastResponseCode == 413 -> context.getString(R.string.nightscout_status_response_413)
        lastResponseCode > 0 -> context.getString(R.string.nightscout_status_response_error, lastResponseCode)
        else -> context.getString(R.string.nightscout_status_waiting)
    }
    val uploaderSummary = when {
        !isActive -> context.getString(R.string.nightscout_status_paused)
        uploaderRunning -> context.getString(R.string.nightscout_status_running)
        retryMinutes > 0 -> context.getString(R.string.nightscout_status_retry_in, retryMinutes)
        else -> context.getString(R.string.nightscout_status_waiting)
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nightscout_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        val childAlpha = if (isActive) 1f else 0.6f

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("nightscout_master") {
                MasterSwitchCard(
                    title = stringResource(R.string.active),
                    subtitle = if (isActive) stringResource(R.string.nightscout_upload_active) else stringResource(R.string.nightscout_upload_paused),
                    checked = isActive,
                    onCheckedChange = { enabled ->
                        isActive = enabled
                        persistSettings()
                    },
                    icon = Icons.Filled.CloudUpload
                )
            }

            item("nightscout_connection_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(childAlpha),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = cardShape(CardPosition.SINGLE),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            enabled = isActive,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.nightscout_url_label)) },
                            placeholder = { Text(stringResource(R.string.nightscout_url_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                        )

                        OutlinedTextField(
                            value = secret,
                            onValueChange = { secret = it },
                            enabled = isActive,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.api_secret_label)) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { persistSettings() }),
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item("nightscout_status_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = cardShape(CardPosition.SINGLE),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.status),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = uploaderSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = responseSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.nightscout_status_last_attempt,
                                formatStatusTime(lastAttemptTime)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.nightscout_status_last_success,
                                formatStatusTime(lastSuccessTime)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item("nightscout_options_group") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(childAlpha),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.sendamounts),
                        subtitle = stringResource(R.string.nightscout_send_amounts_desc),
                        checked = sendTreatments,
                        onCheckedChange = { sendTreatments = it },
                        icon = Icons.Default.Medication,
                        iconTint = MaterialTheme.colorScheme.primary,
                        enabled = isActive,
                        position = CardPosition.TOP
                    )

                    SettingsSwitchItem(
                        title = stringResource(R.string.nightscout_use_v3_api),
                        subtitle = stringResource(R.string.experimental),
                        checked = isV3,
                        onCheckedChange = { isV3 = it },
                        icon = Icons.Default.Science,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        enabled = isActive,
                        position = CardPosition.MIDDLE
                    )

                    SettingsSwitchItem(
                        title = stringResource(R.string.nightscout_follow_title),
                        subtitle = stringResource(R.string.nightscout_follow_desc),
                        checked = followServer,
                        onCheckedChange = { enabled ->
                            if (enabled && NightscoutFollowerRegistry.normalizeUrl(url).isBlank()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.nightscout_follow_url_required),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@SettingsSwitchItem
                            }
                            followServer = enabled
                            persistSettings()
                            if (enabled) {
                                NightscoutFollowerRegistry.enableFollowerSensor(context, url, secret)
                            } else {
                                NightscoutFollowerRegistry.disableFollowerSensor(context)
                            }
                        },
                        icon = Icons.Default.Link,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        enabled = true,
                        position = CardPosition.BOTTOM
                    )
                }
            }

            item("nightscout_send_now") {
                Button(
                    onClick = {
                        persistSettings()
                        Natives.wakeuploader()
                        refreshStatus()
                        Toast.makeText(context, context.getString(R.string.sending_now), Toast.LENGTH_SHORT).show()
                    },
                    enabled = isActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .alpha(childAlpha),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.sendnow))
                }
            }

            item("nightscout_resend_reset") {
                OutlinedButton(
                    onClick = {
                        persistSettings()
                        Natives.resetuploader()
                        refreshStatus()
                        Toast.makeText(context, context.getString(R.string.resend_triggered), Toast.LENGTH_SHORT).show()
                    },
                    enabled = isActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .alpha(childAlpha)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.resend_data_reset))
                }
            }
        }
    }
}
