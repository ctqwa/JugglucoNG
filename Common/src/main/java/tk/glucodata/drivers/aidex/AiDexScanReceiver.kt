package tk.glucodata.drivers.aidex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import tk.glucodata.Log
import tk.glucodata.SensorBluetooth

/**
 * Receiver for AiDex broadcast scan wake-up alarms.
 * This wakes up the CPU to ensure the scan cycle continues even in deep sleep.
 */
class AiDexScanReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AiDexScanReceiver"
        const val ACTION_AIDEX_SCAN = "tk.glucodata.drivers.aidex.ACTION_AIDEX_SCAN"
        const val EXTRA_SERIAL = "serial"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AIDEX_SCAN) return
        
        val serial = intent.getStringExtra(EXTRA_SERIAL) ?: return
        Log.d(TAG, "OnReceive: scan alarm for $serial")

        // Brief wake lock to ensure we handle the alarm before CPU returns to sleep
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AiDexSensor:ReceiverWakeup")
        wl?.acquire(10_000L) // 10s should be plenty to start the scan

        // Find the sensor in the global list
        val sensor = SensorBluetooth.gattcallbacks.find { 
            it is AiDexSensor && it.SerialNumber == serial 
        } as? AiDexSensor

        if (sensor != null) {
            if (sensor.broadcastScanActive) {
                Log.d(TAG, "Scan already active for $serial, skipping trigger.")
            } else {
                sensor.startBroadcastScan("alarm")
            }
        } else {
            Log.w(TAG, "Sensor $serial not found in callbacks")
        }
    }
}
