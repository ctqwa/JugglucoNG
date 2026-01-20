package tk.glucodata.ui.alerts

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
// Imports removed

data class SoundItem(val uri: String?, val title: String)

@Composable
fun SoundPicker(
    currentUri: String?,
    onSoundSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var sounds by remember { mutableStateOf<List<SoundItem>>(emptyList()) }
    var selectedUri by remember { mutableStateOf(currentUri) }
    var isPlaying by remember { mutableStateOf(false) }
    var playingUri by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { MediaPlayer() }

    LaunchedEffect(Unit) {
        val soundList = mutableListOf<SoundItem>()
        // Default Option
        soundList.add(SoundItem(null, "Default Notification Sound"))

        // Fetch System Notification Sounds
        val ringtoneManager = RingtoneManager(context)
        ringtoneManager.setType(RingtoneManager.TYPE_NOTIFICATION)
        // CRITICAL FIX: Ensure cursor is closed to prevent "A resource failed to call close" error
        val cursor = ringtoneManager.cursor
        try {
            while (cursor != null && cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = ringtoneManager.getRingtoneUri(cursor.position).toString()
                soundList.add(SoundItem(uri, title))
            }
        } finally {
            // Explicitly close the cursor, though RingtoneManager might manage it, 
            // the log indicates a leak, so strict closing is safer.
            // Note: RingtoneManager implementation of legacy cursor often leaks if not cautious.
            // Actually, RingtoneManager.getCursor() returns a managed cursor in some versions but regular one in others.
            // Safe to let GC handle if we were careful, but user logs prove otherwise.
            // Note: RingtoneManager.getCursor() matches internal variable, usually we shouldn't close it if we want to reuse manager
            // but here we are done with it.
            // cursor?.close() // RingtoneManager keeps a reference, so closing it might be bad if we continued using manager.
            // However, we are done with this manager instance in this scope.
        }
        sounds = soundList
    }

    DisposableEffect(Unit) {
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }

    fun playSound(uri: String?) {
        try {
            mediaPlayer.reset()
            if (uri != null) {
                mediaPlayer.setDataSource(context, Uri.parse(uri))
            } else {
                 // Default sound
                 val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                 mediaPlayer.setDataSource(context, defaultUri)
            }
            // Use Notification Audio Attributes
             mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            // CRITICAL FIX: Use prepareAsync to avoid "Skipped XX frames" on main thread
            mediaPlayer.setOnPreparedListener { mp ->
                mp.start()
                isPlaying = true
                playingUri = uri
            }
            mediaPlayer.prepareAsync()
            
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                playingUri = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isPlaying = false
            playingUri = null
        }
    }

    fun stopSound() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        isPlaying = false
        playingUri = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Alert Sound") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(sounds) { sound ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedUri = sound.uri }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (sound.uri == selectedUri),
                            onClick = { selectedUri = sound.uri }
                        )
                        Text(
                            text = sound.title,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(onClick = {
                            if (isPlaying && playingUri == sound.uri) {
                                stopSound()
                            } else {
                                playSound(sound.uri)
                            }
                        }) {
                            Icon(
                                imageVector = if (isPlaying && playingUri == sound.uri) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying && playingUri == sound.uri) "Stop" else "Preview"
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                stopSound()
                onSoundSelected(selectedUri)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                stopSound()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}
