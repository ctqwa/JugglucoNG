package tk.glucodata.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import tk.glucodata.Natives
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.res.stringResource
import tk.glucodata.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var logType by remember { mutableStateOf(LogType.TRACE) }
    var isEnabled by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("Loading...") }
    // Sync initial state with Natives
    LaunchedEffect(Unit) {
        isEnabled = if (logType == LogType.TRACE) Natives.islogging() else Natives.islogcat()
    }

    // Polling Loop
    LaunchedEffect(logType) {
        // Update enabled state when type changes
        isEnabled = if (logType == LogType.TRACE) Natives.islogging() else Natives.islogcat()
        
        withContext(Dispatchers.IO) {
            while (isActive) {
                val fileName = if (logType == LogType.TRACE) "logs/trace.log" else "logs/logcat.txt"
                val file = File(context.filesDir, fileName)
                
                val content = if (file.exists()) {
                    // Read last 50KB to avoid memory issues
                    val fileSize = file.length()
                    if (fileSize > 50 * 1024) {
                        try {
                            // Dumb tail implementation: read random access or just skip
                            // For simplicity in Kotlin IO:
                            file.inputStream().use { stream ->
                                stream.skip(fileSize - 50 * 1024)
                                "[...truncated...]\n" + stream.bufferedReader().readText()
                            }
                        } catch (e: Exception) {
                            "Error reading file: ${e.message}"
                        }
                    } else {
                        file.readText()
                    }
                } else {
                    if (isEnabled) "Log enabled. Waiting for entries..." else "Log unchecked. Enable to start logging."
                }

                withContext(Dispatchers.Main) {
                    logContent = content
                }
                
                delay(1000) // Poll every second
            }
        }
    }

    // Save File Launcher
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { targetUri ->
            val fileName = if (logType == LogType.TRACE) "logs/trace.log" else "logs/logcat.txt"
            val sourceFile = File(context.filesDir, fileName)
            if (sourceFile.exists()) {
                try {
                    context.contentResolver.openOutputStream(targetUri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    logContent += "\n[Error saving file: ${e.message}]"
                }
            }
        }
    }

// ...

// ...

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_logs_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (logType == LogType.TRACE) Natives.zeroLog() else Natives.zeroLogcat()
                        // Force refresh
                        logContent = "Cleared."
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear))
                    }
                    IconButton(onClick = {
                        val name = if (logType == LogType.TRACE) "trace.log" else "logcat.txt"
                        saveLauncher.launch(name)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = logType == LogType.TRACE, onClick = { logType = LogType.TRACE })
                    Text(stringResource(R.string.trace_log))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = logType == LogType.LOGCAT, onClick = { logType = LogType.LOGCAT })
                    Text(stringResource(R.string.logcat))
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { 
                        isEnabled = it
                        if (logType == LogType.TRACE) {
                            Natives.dolog(it)
                        } else {
                            Natives.dologcat(it)
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Console View
            val scrollState = rememberScrollState()
            
            // Auto-scroll logic: Check if we are at bottom *before* content update
            // We use a side-effect to track "stickiness"
            var isAtBottom by remember { mutableStateOf(true) }

            // Monitor scroll state to detecting manual "unsticking"
            LaunchedEffect(scrollState.value, scrollState.maxValue) {
                // If the user scrolls up (value < maxValue), they "unstick"
                // strict equality might be flaky with floats, so allow small error or just check directly
                isAtBottom = (scrollState.maxValue - scrollState.value) < 50 // 50px tolerance
            }
            
            LaunchedEffect(logContent) {
                 if (isAtBottom) {
                     scrollState.scrollTo(scrollState.maxValue)
                 }
            }

            // Prepare some localized strings for logic blocks (though log content itself is dynamic)
            val logEnabledWaiting = stringResource(R.string.log_enabled_waiting)
            val logUnchecked = stringResource(R.string.log_unchecked)
            val errorReading = stringResource(R.string.error_reading_file)
            val errorSaving = stringResource(R.string.error_saving_file)

            // ... log reading logic might need to use context.getString() if inside a coroutine, 
            // but for UI text that is static we use stringResource. 
            // Ideally we pass these strings INTO the reading function or handle basic UI strings here.
            // The polling logic below constructs strings dynamically. 
            // Since `stringResource` is compostable, we can't use it inside `LaunchedEffect` block directly.
            // We should use context.getString() there.
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .drawWithContent {
                        drawContent()
                        val containerHeight = size.height
                        val totalContentHeight = scrollState.maxValue + containerHeight
                        
                        if (totalContentHeight > containerHeight) {
                            val thumbHeight = (containerHeight * containerHeight / totalContentHeight).coerceAtLeast(20.dp.toPx())
                            val scrollRange = totalContentHeight - containerHeight // == maxValue
                            val thumbRange = containerHeight - thumbHeight
                            val thumbOffset = if (scrollRange > 0) (scrollState.value / scrollRange) * thumbRange else 0f
                            
                            drawRect(
                                color = Color.White.copy(alpha = 0.3f),
                                topLeft = Offset(size.width - 4.dp.toPx(), thumbOffset),
                                size = Size(4.dp.toPx(), thumbHeight)
                            )
                        }
                    }
            ) {
                Text(
                    text = logContent,
                    color = MaterialTheme.colorScheme.onSurface, 
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }
        }
    }
}


enum class LogType {
    TRACE, LOGCAT
}
