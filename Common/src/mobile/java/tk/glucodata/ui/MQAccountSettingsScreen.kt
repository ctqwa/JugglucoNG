@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.R
import tk.glucodata.drivers.mq.MQAuthCredentials
import tk.glucodata.drivers.mq.MQBootstrapFailure
import tk.glucodata.drivers.mq.MQCloudClient
import tk.glucodata.drivers.mq.MQIncomingFriendRequest
import tk.glucodata.drivers.mq.MQRegistry
import tk.glucodata.drivers.mq.MQVerifyCodeAction
import tk.glucodata.ui.components.MasterSwitchCard
import tk.glucodata.ui.components.SectionLabel

@Composable
fun MQAccountSettingsScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialAccountState = remember { MQRegistry.loadAccountState(context) }

    var phone by rememberSaveable { mutableStateOf(initialAccountState.phone) }
    var password by rememberSaveable { mutableStateOf(initialAccountState.password) }
    var apiBaseUrl by rememberSaveable { mutableStateOf(initialAccountState.apiBaseUrl) }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    var cloudSyncEnabled by rememberSaveable {
        mutableStateOf(MQRegistry.isCloudSyncEnabled(context))
    }
    var followerEnabled by rememberSaveable {
        mutableStateOf(MQRegistry.isFollowerEnabled(context))
    }
    var followerAccount by rememberSaveable {
        mutableStateOf(MQRegistry.loadFollowerAccount(context))
    }
    var authToken by rememberSaveable { mutableStateOf(initialAccountState.authToken.orEmpty()) }
    var statusText by rememberSaveable { mutableStateOf("") }
    var busyAction by remember { mutableStateOf<String?>(null) }
    var incomingRequests by remember { mutableStateOf<List<MQIncomingFriendRequest>>(emptyList()) }
    var requestsLoaded by remember { mutableStateOf(false) }

    val hasCredentials = phone.isNotBlank() && password.isNotBlank()
    val hasVerificationCode = verificationCode.isNotBlank()
    val hasAuthToken = authToken.isNotBlank()
    val isBusy = busyAction != null
    val signInLabel = stringResource(R.string.mq_account_sign_in_action)
    val checkSessionLabel = stringResource(R.string.mq_account_check_session_action)
    val signOutLabel = stringResource(R.string.mq_account_sign_out_action)
    val requestLoginCodeLabel = stringResource(R.string.mq_request_login_code_action)
    val requestSignupCodeLabel = stringResource(R.string.mq_request_signup_code_action)
    val requestResetCodeLabel = stringResource(R.string.mq_request_reset_code_action)
    val signInWithCodeLabel = stringResource(R.string.mq_account_sign_in_with_code_action)
    val createAccountLabel = stringResource(R.string.mq_account_register_action)
    val resetPasswordLabel = stringResource(R.string.mq_account_reset_password_action)
    val refreshLabel = stringResource(R.string.refresh)
    val approveRequestLabel = stringResource(R.string.mq_friend_request_approve_action)
    val rejectRequestLabel = stringResource(R.string.mq_friend_request_reject_action)
    val followerTitle = stringResource(R.string.mq_follower_title)
    val statusSummary = when {
        hasAuthToken -> stringResource(R.string.mq_account_status_signed_in)
        hasCredentials -> stringResource(R.string.mq_account_status_saved)
        else -> stringResource(R.string.mq_account_status_missing)
    }
    val secondarySummary = buildList {
        phone.trim().takeIf { it.isNotEmpty() }?.let(::add)
        add(
            if (cloudSyncEnabled) {
                stringResource(R.string.mq_sync_enabled_summary)
            } else {
                stringResource(R.string.mq_sync_disabled_summary)
            }
        )
        add(
            if (followerEnabled) {
                stringResource(R.string.mq_follower_enabled_summary)
            } else {
                stringResource(R.string.mq_follower_disabled_summary)
            }
        )
    }.joinToString(" · ")

    fun persistAccountState() {
        MQRegistry.saveAccountState(
            context = context,
            phone = phone,
            password = password,
            apiBaseUrl = apiBaseUrl,
        )
        MQRegistry.saveCloudSyncEnabled(context, cloudSyncEnabled)
        MQRegistry.saveFollowerAccount(context, followerAccount)
        MQRegistry.saveFollowerEnabled(context, followerEnabled)
        authToken = MQRegistry.loadAccountState(context).authToken.orEmpty()
    }

    fun setResultMessage(message: String?, fallbackRes: Int) {
        statusText = message?.takeIf { it.isNotBlank() } ?: context.getString(fallbackRes)
    }

    suspend fun runCloudAction(
        actionLabel: String,
        block: suspend () -> Unit,
    ) {
        busyAction = actionLabel
        try {
            persistAccountState()
            block()
            } finally {
                busyAction = null
            }
    }

    suspend fun ensureCloudToken(): String? {
        MQRegistry.loadAccountState(context).authToken?.takeIf { it.isNotBlank() }?.let { return it }
        val credentials = MQRegistry.loadAuthCredentials(context) ?: return null
        val result = withContext(Dispatchers.IO) {
            MQCloudClient.signInWithPassword(
                context = context,
                credentials = credentials,
            )
        }
        if (result.success) {
            MQRegistry.saveAuthToken(context, result.token)
            authToken = result.token.orEmpty()
            return result.token
        }
        return null
    }

    suspend fun refreshIncomingRequests() {
        val currentUser = phone.trim()
        if (currentUser.isEmpty()) {
            incomingRequests = emptyList()
            requestsLoaded = false
            return
        }
        val token = ensureCloudToken()
        if (token.isNullOrBlank()) {
            incomingRequests = emptyList()
            requestsLoaded = false
            return
        }
        val (requests, result) = withContext(Dispatchers.IO) {
            MQCloudClient.fetchIncomingFriendRequests(
                context = context,
                authToken = token,
                userAccount = currentUser,
            )
        }
        incomingRequests = requests
        requestsLoaded = true
        if (result.failure != MQBootstrapFailure.NONE && result.message?.isNotBlank() == true) {
            statusText = result.message
        }
    }

    suspend fun setFollowerEnabled(enabled: Boolean) {
        if (!enabled) {
            followerEnabled = false
            MQRegistry.disableFollowerSensors(context)
            withContext(Dispatchers.IO) { tk.glucodata.SensorBluetooth.updateDevices() }
            setResultMessage(
                context.getString(R.string.mq_follower_disabled_message),
                R.string.mq_follower_disabled_message,
            )
            return
        }

        val targetAccount = followerAccount.trim()
        if (targetAccount.isEmpty()) {
            followerEnabled = false
            setResultMessage(
                context.getString(R.string.mq_follower_requires_target),
                R.string.mq_follower_requires_target,
            )
            return
        }

        val currentUser = phone.trim()
        if (currentUser.isEmpty()) {
            followerEnabled = false
            setResultMessage(
                context.getString(R.string.mq_follower_requires_login),
                R.string.mq_follower_requires_login,
            )
            return
        }

        val token = ensureCloudToken()
        if (token.isNullOrBlank()) {
            followerEnabled = false
            setResultMessage(
                context.getString(R.string.mq_follower_requires_login),
                R.string.mq_follower_requires_login,
            )
            return
        }

        val lookup = withContext(Dispatchers.IO) {
            MQCloudClient.findFriendUser(
                context = context,
                authToken = token,
                userAccount = currentUser,
                friendAccount = targetAccount,
            )
        }
        if (!lookup.exists && lookup.failure != MQBootstrapFailure.NONE) {
            followerEnabled = false
            setResultMessage(lookup.message, R.string.mq_follower_account_not_found)
            return
        }
        if (!lookup.exists) {
            followerEnabled = false
            setResultMessage(
                context.getString(R.string.mq_follower_account_not_found),
                R.string.mq_follower_account_not_found,
            )
            return
        }

        val statusMessage = if (lookup.isFriend || lookup.friendshipState == 2) {
            MQRegistry.enableFollowerSensor(context, targetAccount, connectNow = true)
            if (lookup.isFriend) {
                context.getString(R.string.mq_follower_enabled_message)
            } else {
                context.getString(R.string.mq_follower_request_sent)
            }
        } else {
            val request = withContext(Dispatchers.IO) {
                MQCloudClient.addFriend(
                    context = context,
                    authToken = token,
                    userAccount = currentUser,
                    friendAccount = targetAccount,
                    permission = 2,
                    remark = "",
                    type = 2,
                )
            }
            if (!request.success) {
                followerEnabled = false
                setResultMessage(request.message, R.string.mq_follower_request_failed)
                return
            }
            MQRegistry.enableFollowerSensor(context, targetAccount, connectNow = true)
            context.getString(R.string.mq_follower_request_sent)
        }

        followerEnabled = true
        withContext(Dispatchers.IO) { tk.glucodata.SensorBluetooth.updateDevices() }
        setResultMessage(statusMessage, R.string.mq_follower_enabled_message)
    }

    LaunchedEffect(authToken, phone) {
        if (authToken.isNotBlank() && phone.isNotBlank()) {
            refreshIncomingRequests()
        } else {
            incomingRequests = emptyList()
            requestsLoaded = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { persistAccountState() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mq_account_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MasterSwitchCard(
                title = stringResource(R.string.mq_sync_title),
                subtitle = stringResource(R.string.mq_sync_desc),
                checked = cloudSyncEnabled,
                onCheckedChange = { enabled ->
                    cloudSyncEnabled = enabled
                    MQRegistry.saveCloudSyncEnabled(context, enabled)
                },
                icon = Icons.Default.Cloud,
            )

            MasterSwitchCard(
                title = stringResource(R.string.mq_follower_title),
                subtitle = stringResource(R.string.mq_follower_desc),
                checked = followerEnabled,
                onCheckedChange = { enabled ->
                    coroutineScope.launch {
                        runCloudAction(followerTitle) {
                            setFollowerEnabled(enabled)
                        }
                    }
                },
                icon = Icons.Default.Cloud,
            )

            MQAccountStatusCard(
                summary = statusSummary,
                secondarySummary = secondarySummary,
                statusText = statusText,
                busyAction = busyAction,
            )

            SectionLabel(
                text = stringResource(R.string.mq_account_title),
                topPadding = 0.dp,
            )

            MQAccountFields(
                phone = phone,
                onPhoneChange = { phone = it },
                password = password,
                onPasswordChange = { password = it },
                apiBaseUrl = apiBaseUrl,
                onApiBaseUrlChange = { apiBaseUrl = it },
                supportingText = stringResource(R.string.mq_account_desc),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(
                text = stringResource(R.string.mq_follower_title),
                topPadding = 8.dp,
            )

            OutlinedTextField(
                value = followerAccount,
                onValueChange = { followerAccount = it.trim() },
                label = { Text(stringResource(R.string.mq_follower_account_label)) },
                supportingText = { Text(stringResource(R.string.mq_follower_account_desc)) },
                singleLine = true,
                enabled = !followerEnabled && !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        runCloudAction(signInLabel) {
                            val result = withContext(Dispatchers.IO) {
                                MQCloudClient.signInWithPassword(
                                    context = context,
                                    credentials = MQAuthCredentials(
                                        account = phone.trim(),
                                        password = password,
                                    ),
                                )
                            }
                            if (result.success) {
                                MQRegistry.saveAuthToken(context, result.token)
                                authToken = result.token.orEmpty()
                                refreshIncomingRequests()
                            }
                            setResultMessage(result.message, R.string.mq_account_sign_in_action)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasCredentials && !isBusy,
            ) {
                Text(signInLabel)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            runCloudAction(checkSessionLabel) {
                                val result = withContext(Dispatchers.IO) {
                                    MQCloudClient.checkToken(
                                        context = context,
                                        authToken = authToken,
                                    )
                                }
                                setResultMessage(
                                    result.message,
                                    if (result.isValid) {
                                        R.string.mq_account_session_valid
                                    } else {
                                        R.string.mq_account_session_invalid
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = hasAuthToken && !isBusy,
                ) {
                    Text(checkSessionLabel)
                }
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            runCloudAction(signOutLabel) {
                                val result = withContext(Dispatchers.IO) {
                                    if (hasAuthToken && phone.isNotBlank()) {
                                        MQCloudClient.logout(
                                            context = context,
                                            phone = phone.trim(),
                                            authToken = authToken,
                                        )
                                    } else {
                                        tk.glucodata.drivers.mq.MQCloudActionResult(success = true)
                                    }
                                }
                                MQRegistry.clearAuthToken(context)
                                authToken = ""
                                incomingRequests = emptyList()
                                requestsLoaded = false
                                setResultMessage(result.message, R.string.mq_account_sign_out_action)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = (hasAuthToken || hasCredentials) && !isBusy,
                ) {
                    Text(signOutLabel)
                }
            }

            SectionLabel(
                text = stringResource(R.string.mq_friend_requests_title),
                topPadding = 8.dp,
            )

            Text(
                text = stringResource(R.string.mq_friend_requests_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        runCloudAction(refreshLabel) {
                            refreshIncomingRequests()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phone.isNotBlank() && !isBusy,
            ) {
                Text(refreshLabel)
            }

            when {
                requestsLoaded && incomingRequests.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.mq_friend_requests_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                incomingRequests.isNotEmpty() -> {
                    incomingRequests.forEach { request ->
                        MQIncomingRequestCard(
                            request = request,
                            enabled = !isBusy,
                            onApprove = {
                                coroutineScope.launch {
                                    runCloudAction(approveRequestLabel) {
                                        val token = ensureCloudToken()
                                        if (token.isNullOrBlank()) {
                                            setResultMessage(
                                                context.getString(R.string.mq_follower_requires_login),
                                                R.string.mq_follower_requires_login,
                                            )
                                            return@runCloudAction
                                        }
                                        val result = withContext(Dispatchers.IO) {
                                            MQCloudClient.reviewFriendRequest(
                                                context = context,
                                                authToken = token,
                                                userAccount = phone.trim(),
                                                applyFriendId = request.requestId,
                                                approved = true,
                                                permission = 2,
                                                aliasName = request.name?.takeIf { it.isNotBlank() }
                                                    ?: request.phone,
                                            )
                                        }
                                        setResultMessage(
                                            result.message,
                                            R.string.mq_friend_request_approved_message,
                                        )
                                        refreshIncomingRequests()
                                    }
                                }
                            },
                            onReject = {
                                coroutineScope.launch {
                                    runCloudAction(rejectRequestLabel) {
                                        val token = ensureCloudToken()
                                        if (token.isNullOrBlank()) {
                                            setResultMessage(
                                                context.getString(R.string.mq_follower_requires_login),
                                                R.string.mq_follower_requires_login,
                                            )
                                            return@runCloudAction
                                        }
                                        val result = withContext(Dispatchers.IO) {
                                            MQCloudClient.reviewFriendRequest(
                                                context = context,
                                                authToken = token,
                                                userAccount = phone.trim(),
                                                applyFriendId = request.requestId,
                                                approved = false,
                                                permission = 2,
                                                aliasName = request.name?.takeIf { it.isNotBlank() }
                                                    ?: request.phone,
                                            )
                                        }
                                        setResultMessage(
                                            result.message,
                                            R.string.mq_friend_request_rejected_message,
                                        )
                                        refreshIncomingRequests()
                                    }
                                }
                            },
                        )
                    }
                }
            }

            SectionLabel(
                text = stringResource(R.string.mq_sms_section_title),
                topPadding = 8.dp,
            )

            OutlinedTextField(
                value = verificationCode,
                onValueChange = { verificationCode = it.filter { c -> c.isLetterOrDigit() } },
                label = { Text(stringResource(R.string.mq_sms_code_label)) },
                supportingText = { Text(stringResource(R.string.mq_sms_code_desc)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        runCloudAction(requestLoginCodeLabel) {
                            val result = withContext(Dispatchers.IO) {
                                MQCloudClient.requestVerifyCode(
                                    context = context,
                                    phone = phone.trim(),
                                    action = MQVerifyCodeAction.LOGIN,
                                )
                            }
                            setResultMessage(result.message, R.string.mq_request_login_code_action)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phone.isNotBlank() && !isBusy,
            ) {
                Text(requestLoginCodeLabel)
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        runCloudAction(requestSignupCodeLabel) {
                            val result = withContext(Dispatchers.IO) {
                                MQCloudClient.requestVerifyCode(
                                    context = context,
                                    phone = phone.trim(),
                                    action = MQVerifyCodeAction.REGISTER,
                                )
                            }
                            setResultMessage(result.message, R.string.mq_request_signup_code_action)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phone.isNotBlank() && !isBusy,
            ) {
                Text(requestSignupCodeLabel)
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        runCloudAction(requestResetCodeLabel) {
                            val result = withContext(Dispatchers.IO) {
                                MQCloudClient.requestVerifyCode(
                                    context = context,
                                    phone = phone.trim(),
                                    action = MQVerifyCodeAction.RESET_PASSWORD,
                                )
                            }
                            setResultMessage(result.message, R.string.mq_request_reset_code_action)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phone.isNotBlank() && !isBusy,
            ) {
                Text(requestResetCodeLabel)
            }

            FilledTonalButton(
                onClick = {
                    coroutineScope.launch {
                        runCloudAction(signInWithCodeLabel) {
                            val result = withContext(Dispatchers.IO) {
                                MQCloudClient.signInWithCode(
                                    context = context,
                                    phone = phone.trim(),
                                    captcha = verificationCode.trim(),
                                )
                            }
                            if (result.success) {
                                MQRegistry.saveAuthToken(context, result.token)
                                authToken = result.token.orEmpty()
                                refreshIncomingRequests()
                            }
                            setResultMessage(result.message, R.string.mq_account_sign_in_with_code_action)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phone.isNotBlank() && hasVerificationCode && !isBusy,
            ) {
                Text(signInWithCodeLabel)
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        runCloudAction(createAccountLabel) {
                            val result = withContext(Dispatchers.IO) {
                                MQCloudClient.registerByCode(
                                    context = context,
                                    phone = phone.trim(),
                                    captcha = verificationCode.trim(),
                                    password = password,
                                )
                            }
                            if (result.success) {
                                MQRegistry.saveAuthToken(context, result.token)
                                authToken = result.token.orEmpty()
                                refreshIncomingRequests()
                            }
                            setResultMessage(result.message, R.string.mq_account_register_action)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phone.isNotBlank() && hasVerificationCode && password.isNotBlank() && !isBusy,
            ) {
                Text(createAccountLabel)
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        runCloudAction(resetPasswordLabel) {
                            val result = withContext(Dispatchers.IO) {
                                MQCloudClient.resetPasswordByCode(
                                    context = context,
                                    phone = phone.trim(),
                                    captcha = verificationCode.trim(),
                                    newPassword = password,
                                )
                            }
                            setResultMessage(result.message, R.string.mq_account_reset_password_action)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phone.isNotBlank() && hasVerificationCode && password.isNotBlank() && !isBusy,
            ) {
                Text(resetPasswordLabel)
            }
        }
    }
}

@Composable
private fun MQAccountStatusCard(
    summary: String,
    secondarySummary: String,
    statusText: String,
    busyAction: String?,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = secondarySummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busyAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = busyAction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MQIncomingRequestCard(
    request: MQIncomingFriendRequest,
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = request.name?.takeIf { it.isNotBlank() } ?: request.phone,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (request.name?.isNotBlank() == true && request.phone.isNotBlank()) {
                Text(
                    text = request.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            request.remark?.takeIf { it.isNotBlank() }?.let { remark ->
                Text(
                    text = stringResource(R.string.mq_friend_request_remark_format, remark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.mq_friend_request_reject_action))
                }
                FilledTonalButton(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.mq_friend_request_approve_action))
                }
            }
        }
    }
}
