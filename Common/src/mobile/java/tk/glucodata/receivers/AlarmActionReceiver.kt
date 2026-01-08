package tk.glucodata.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tk.glucodata.Notify

class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> {
                Notify.stopalarm()
            }
            ACTION_SNOOZE -> {
                Notify.stopalarm()
                // TODO: Implement actual snooze logic (timer to re-trigger)
                // For now, it just stops the sound/vibration like dismiss
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "tk.glucodata.ACTION_SNOOZE"
        const val ACTION_DISMISS = "tk.glucodata.ACTION_DISMISS"
    }
}
