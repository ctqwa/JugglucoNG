package tk.glucodata

import android.content.Context

/**
 * Logical current-sensor slot for managed sensors without native backing.
 *
 * Native `lastsensorname` must keep pointing at a real native sensor or be empty;
 * storing virtual ids there makes native history/status paths repeatedly try to
 * open non-existent sensor files.
 */
object ManagedCurrentSensor {
    private const val PREFS_NAME = "tk.glucodata_preferences"
    private const val KEY_CURRENT = "managed_current_sensor"

    @Volatile private var cached: String? = null

    private fun prefs() = Applic.app?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun get(): String? =
        prefs()?.getString(KEY_CURRENT, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: cached?.trim()?.takeIf { it.isNotEmpty() }

    @JvmStatic
    fun set(sensorId: String?) {
        val normalized = sensorId?.trim()?.takeIf { it.isNotEmpty() }
        cached = normalized
        prefs()?.edit()?.putString(KEY_CURRENT, normalized)?.apply()
    }

    @JvmStatic
    fun clear() {
        cached = null
        prefs()?.edit()?.remove(KEY_CURRENT)?.apply()
    }

    @JvmStatic
    fun clearIfMatches(sensorId: String?) {
        val current = get() ?: return
        if (SensorIdentity.matches(current, sensorId)) {
            clear()
        }
    }
}
