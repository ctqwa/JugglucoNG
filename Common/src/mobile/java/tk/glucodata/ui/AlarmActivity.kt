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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import tk.glucodata.Notify
import tk.glucodata.Natives
import tk.glucodata.Applic

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        turnScreenOnAndKeyguard()

        val intent = intent
        // Force comma separator for display (User Request)
        // Parse float value first for color (needs dot)
        val rawValue = intent.getStringExtra("EXTRA_GLUCOSE_VAL") ?: "---"
        
        // Ensure rate is passed correctly
        val rate = intent.getFloatExtra("EXTRA_RATE", Float.NaN)
        val alarmType = intent.getStringExtra("EXTRA_ALARM_TYPE") ?: "ALARM"
        
        // Parse Glucose Value and Message
        // Parse Glucose Value and Message
        val (parsedValueRaw, parsedMessage) = parseGlucoseString(rawValue, "")
        
        // Force comma for display
        val parsedValue = parsedValueRaw.replace(".", ",")

        val isMmol = Applic.unit == 1
        // Parse float value for Color determination
        val floatValue = try {
            // Take only the first part before any slash (in case of "3,9 / 2,7")
            val firstPart = parsedValue.split("/")[0].trim()
            firstPart.replace(",", ".").toFloat()
        } catch (e: Exception) { 0f }
        
        val glucoseColor = tk.glucodata.NotificationChartDrawer.getGlucoseColor(this, floatValue, isMmol)
        
        // Use the Drawer to get the exact font/look
        val glucoseBitmap = tk.glucodata.NotificationChartDrawer.drawGlucoseText(
            this,
            parsedValue,
            glucoseColor,
            4.0f, // Reduced Scale (Secondary Focus)
            300 // Light weight
        ).asImageBitmap()

        // 2. Generate Arrow Bitmap (REUSING Notification Logic)
        val arrowBitmap = if (!rate.isNaN()) {
             // Use the Drawer check for arrow
             tk.glucodata.NotificationChartDrawer.drawArrow(
                 this,
                 rate, 
                 isMmol, 
                 glucoseColor,
                 3.0f // Standard Scale
             )?.asImageBitmap()
        } else {
             null
        }

        setContent {
            MaterialTheme {
                AlarmScreen(
                    glucoseBitmap = glucoseBitmap,
                    arrowBitmap = arrowBitmap,
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
        // Regex to find value (e.g. "4.1" or "3,9 / 2,7") anywhere
        // Matches: Num, optional decimals, optional " / " + Num + decimals
        val numberRegex = Regex("(\\d+([.,]\\d+)?(\\s*[/]\\s*\\d+([.,]\\d+)?)?)")
        val match = numberRegex.find(input)
        
        return if (match != null) {
            val value = match.value
            
            // Remove the value from the string to get the message part
            // e.g. "Forecast Low 4.1 mmol/L" -> "Forecast Low  mmol/L"
            var message = input.removeRange(match.range)
            
            // Remove units
            val unitsToRemove = listOf(
                unit, 
                tk.glucodata.Notify.unitlabel, 
                "mmol/L", 
                "mg/dL",
                "mmol/l",
                "mg/dl"
            )
            
            for (u in unitsToRemove) {
                if (!u.isNullOrEmpty()) {
                    message = message.replace(u, "", ignoreCase = true)
                }
            }
            
            // Cleanup: "Forecast Low  " -> "Forecast Low"
            // Also remove any leftover / or dots if they were part of unit separators?
            // Just trim for now.
            message = message.replace(Regex("\\s+"), " ").trim()
            
            value to message
        } else {
            // Fallback: If no number found, treat whole string as message? 
            // Or if design requires a number, maybe "---" and whole string as message.
            "---" to input
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
            // val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            // keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            )
        }
    }

    companion object {
        fun createIntent(context: Context, glucoseVal: String, alarmType: String, rate: Float): Intent {
            return Intent(context, AlarmActivity::class.java).apply {
                putExtra("EXTRA_GLUCOSE_VAL", glucoseVal)
                putExtra("EXTRA_ALARM_TYPE", alarmType)
                putExtra("EXTRA_RATE", rate)
                // Add flags to clear top/new task if needed
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}
