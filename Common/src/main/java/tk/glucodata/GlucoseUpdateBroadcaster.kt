package tk.glucodata

import android.content.Context
import android.content.Intent
import android.os.SystemClock

object GlucoseUpdateBroadcaster {
    const val ACTION_GLUCOSE_UPDATE = "tk.glucodata.action.GLUCOSE_UPDATE"

    private const val LOG_ID = "GlucoseUpdateBroadcast"
    private const val MIN_BROADCAST_INTERVAL_MS = 1_000L
    private val lock = Any()
    private var lastBroadcastElapsedRealtime = 0L

    @JvmStatic
    @JvmOverloads
    fun send(context: Context? = null) {
        val appContext = (context?.applicationContext ?: Applic.app) ?: return
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (lastBroadcastElapsedRealtime > 0L &&
                now - lastBroadcastElapsedRealtime < MIN_BROADCAST_INTERVAL_MS
            ) {
                return
            }
            lastBroadcastElapsedRealtime = now
        }

        try {
            appContext.sendBroadcast(
                Intent(ACTION_GLUCOSE_UPDATE).setPackage(appContext.packageName)
            )
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "send", th)
        }
    }
}
