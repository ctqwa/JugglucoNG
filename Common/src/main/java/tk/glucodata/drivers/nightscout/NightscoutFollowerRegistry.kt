package tk.glucodata.drivers.nightscout

import android.content.Context
import java.security.MessageDigest
import java.util.Locale
import tk.glucodata.ManagedCurrentSensor
import tk.glucodata.SensorBluetooth
import tk.glucodata.SensorIdentity
import tk.glucodata.SuperGattCallback
import tk.glucodata.drivers.ManagedBluetoothSensorDriver
import tk.glucodata.drivers.ManagedSensorUiSignals

object NightscoutFollowerRegistry {
    private const val PREFS_NAME = "tk.glucodata_preferences"
    private const val PREF_ENABLED = "nightscout_follower_enabled"
    private const val PREF_URL = "nightscout_follower_url"
    private const val PREF_SECRET = "nightscout_follower_secret"
    const val SENSOR_PREFIX = "NSF-"

    data class Config(
        val enabled: Boolean,
        val url: String,
        val secret: String,
    ) {
        val sensorId: String get() = deriveSensorId(url)
        val isUsable: Boolean get() = enabled && url.isNotBlank()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun normalizeUrl(url: String?): String =
        url?.trim()
            ?.removeSuffix("/")
            ?.takeIf { it.isNotEmpty() }
            ?.let { raw ->
                if (raw.startsWith("http://", ignoreCase = true) ||
                    raw.startsWith("https://", ignoreCase = true)
                ) {
                    raw
                } else {
                    "https://$raw"
                }
            }
            .orEmpty()

    fun deriveSensorId(url: String?): String {
        val normalized = normalizeUrl(url)
        if (normalized.isEmpty()) return SENSOR_PREFIX + "UNCONFIGURED"
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(normalized.lowercase(Locale.US).toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02X".format(Locale.US, it) }
        return SENSOR_PREFIX + digest
    }

    fun loadConfig(context: Context): Config =
        Config(
            enabled = prefs(context).getBoolean(PREF_ENABLED, false),
            url = normalizeUrl(prefs(context).getString(PREF_URL, null)),
            secret = prefs(context).getString(PREF_SECRET, null).orEmpty(),
        )

    fun saveConfig(context: Context, enabled: Boolean, url: String?, secret: String?) {
        prefs(context).edit()
            .putBoolean(PREF_ENABLED, enabled)
            .putString(PREF_URL, normalizeUrl(url).takeIf { it.isNotEmpty() })
            .putString(PREF_SECRET, secret?.trim()?.takeIf { it.isNotEmpty() })
            .apply()
        ManagedSensorUiSignals.markDeviceListDirty()
    }

    fun persistedSensorIds(context: Context): List<String> =
        loadConfig(context).takeIf { it.isUsable }?.let { listOf(it.sensorId) }.orEmpty()

    fun createRestoredCallback(context: Context, sensorId: String, dataptr: Long): SuperGattCallback? {
        val config = loadConfig(context)
        if (!config.isUsable || !matchesSensorId(sensorId, config.sensorId)) return null
        return NightscoutFollowerManager(
            serial = config.sensorId,
            url = config.url,
            secret = config.secret,
            dataptr = dataptr,
        )
    }

    fun enableFollowerSensor(context: Context, url: String?, secret: String?, connectNow: Boolean = true): String? {
        val normalizedUrl = normalizeUrl(url)
        if (normalizedUrl.isEmpty()) return null
        saveConfig(context, enabled = true, url = normalizedUrl, secret = secret)
        val sensorId = deriveSensorId(normalizedUrl)
        if (connectNow) {
            connectSensor(context, sensorId)
        }
        return sensorId
    }

    fun disableFollowerSensor(context: Context) {
        val sensorId = loadConfig(context).sensorId
        ManagedCurrentSensor.clearIfMatches(sensorId)
        saveConfig(context, enabled = false, url = loadConfig(context).url, secret = loadConfig(context).secret)
        SensorBluetooth.mygatts()
            .firstOrNull { SensorIdentity.matches(it.SerialNumber, sensorId) }
            ?.let { callback ->
                if (callback is ManagedBluetoothSensorDriver) {
                    callback.terminateManagedSensor(wipeData = false)
                }
                SensorBluetooth.sensorEnded(callback.SerialNumber)
            }
    }

    fun connectSensor(context: Context, sensorId: String) {
        val blue = SensorBluetooth.blueone ?: return
        val existing = SensorBluetooth.gattcallbacks.firstOrNull { callback ->
            SensorIdentity.matches(callback.SerialNumber, sensorId) ||
                ((callback as? ManagedBluetoothSensorDriver)?.matchesManagedSensorId(sensorId) == true)
        }
        val callback = existing ?: createRestoredCallback(context, sensorId, 0L)?.also {
            SensorBluetooth.gattcallbacks.add(it)
            tk.glucodata.Natives.setmaxsensors(SensorBluetooth.gattcallbacks.size)
        } ?: return
        SensorBluetooth.ensureCurrentSensorSelection()
        if (SensorBluetooth.blueone === blue) {
            callback.connectDevice(0)
        }
        ManagedSensorUiSignals.markDeviceListDirty()
    }

    fun matchesSensorId(candidate: String?, expected: String?): Boolean {
        val left = candidate?.trim().orEmpty()
        val right = expected?.trim().orEmpty()
        return left.isNotEmpty() && right.isNotEmpty() && left.equals(right, ignoreCase = true)
    }
}
