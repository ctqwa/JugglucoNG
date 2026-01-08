package tk.glucodata.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import tk.glucodata.Notify
import tk.glucodata.Natives
import tk.glucodata.Applic

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        turnScreenOnAndKeyguard()

        val intent = intent
        val glucoseValExtra = intent.getStringExtra("EXTRA_GLUCOSE_VAL") ?: "---"
        val glucoseUnit = intent.getStringExtra("EXTRA_GLUCOSE_UNIT") ?: if(Applic.unit == 1) "mmol/L" else "mg/dL"
        val rate = intent.getFloatExtra("EXTRA_RATE", Float.NaN)
        val alarmType = intent.getStringExtra("EXTRA_ALARM_TYPE") ?: "ALARM"
        
        // Parse Glucose Value and Message separately if stuck together
        val (parsedValue, parsedMessage) = parseGlucoseString(glucoseValExtra, glucoseUnit)

        // Arrow Logic
        val arrow = if (!rate.isNaN()) {
             getArrow(rate)
        } else {
             intent.getStringExtra("EXTRA_ARROW") ?: ""
        }

        setContent {
            MaterialTheme {
                // Force Dark Mode feel (or actually use Dark Theme if app supports it, currently using hardcoded dark variants in screen)
                AlarmScreen(
                    glucoseValue = parsedValue,
                    glucoseUnit = glucoseUnit,
                    arrow = arrow,
                    alarmType = alarmType,
                    message = parsedMessage,
                    onSnooze = {
                        Notify.stopalarm()
                        finish()
                    },
                    onDismiss = {
                        Notify.stopalarm()
                        finish()
                    }
                )
            }
        }
    }
    
    private fun parseGlucoseString(input: String, unit: String): Pair<String, String> {
        // Input might be "2.8 mmol/L Low Glucose!" or "2.8"
        // Try to extract the number at the start (supports dot or comma)
        val numberRegex = Regex("^(\\d+([.,]\\d+)?)")
        val match = numberRegex.find(input)
        
        return if (match != null) {
            val value = match.value
            // Remove value and unit from message
            var message = input.substring(match.range.last + 1).trim()
            message = message.replace(unit, "", ignoreCase = true).trim()
            value to message
        } else {
            // Fallback
            input to ""
        }
    }

    private fun getArrow(rate: Float): String {
        return when {
            rate >= 2.0f -> "↑↑" // Double Up
            rate >= 1.0f -> "↑"  // Up
            rate >= 0.5f -> "↗"  // 45 Up
            rate > -0.5f -> "→"  // Flat
            rate > -1.0f -> "↘"  // 45 Down
            rate > -2.0f -> "↓"  // Down
            else -> "↓↓"         // Double Down
        }
    }

    private fun turnScreenOnAndKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }
    }

    companion object {
        fun createIntent(context: Context, glucoseVal: String, alarmType: String, arrow: String): Intent {
            return Intent(context, AlarmActivity::class.java).apply {
                putExtra("EXTRA_GLUCOSE_VAL", glucoseVal)
                putExtra("EXTRA_ALARM_TYPE", alarmType)
                  putExtra("EXTRA_ARROW", arrow)
                // Add flags to clear top/new task if needed
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}
