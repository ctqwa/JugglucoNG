package tk.glucodata.drivers.nightscout

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.R
import tk.glucodata.SensorIdentity
import tk.glucodata.SuperGattCallback
import tk.glucodata.UiRefreshBus
import tk.glucodata.drivers.ManagedBluetoothSensorDriver
import tk.glucodata.drivers.ManagedSensorCurrentSnapshot
import tk.glucodata.drivers.ManagedSensorUiFamily
import tk.glucodata.drivers.ManagedSensorUiSnapshot
import tk.glucodata.drivers.VirtualGlucoseSensorBridge

class NightscoutFollowerManager(
    serial: String,
    private val url: String,
    private val secret: String,
    dataptr: Long,
) : SuperGattCallback(serial, dataptr, SENSOR_GEN), ManagedBluetoothSensorDriver {

    companion object {
        private const val TAG = "NightscoutFollower"
        private const val SENSOR_GEN = 0
        private const val POLL_INTERVAL_MS = 60_000L
        private const val RETRY_INTERVAL_MS = 30_000L
        private const val HISTORY_COUNT = 288
        private const val MMOL_TO_MGDL = 18.0182f
    }

    private enum class Phase {
        IDLE,
        SYNCING,
        FOLLOWING,
    }

    private val handlerThread = HandlerThread("NightscoutFollower-$serial").also { it.start() }
    private val handler = Handler(handlerThread.looper)
    private val pollRunnable = Runnable { refresh("poll") }

    @Volatile private var phase: Phase = Phase.IDLE
    @Volatile private var status: String = localizedString(R.string.nightscout_follow_status_idle, "Nightscout follower idle")
    @Volatile private var lastImportedHistoryTailMs: Long = 0L
    @Volatile private var latestReadingTimeMs: Long = 0L
    @Volatile private var latestReadingMgdl: Float = Float.NaN
    @Volatile private var latestRateMgdlPerMin: Float = 0f

    init {
        mActiveDeviceAddress = url
    }

    override var viewMode: Int = 0

    override fun canConnectWithoutDataptr(): Boolean = true

    override fun hasNativeSensorBacking(): Boolean = false

    override fun shouldUseNativeHistorySync(): Boolean = false

    override fun managesLiveRoomStorage(): Boolean = true

    override fun shouldUseSharedCurrentSensorHandoffOnTerminate(): Boolean = false

    override fun isManagedOutsideNativeActiveSet(): Boolean = true

    override fun shouldShowSearchingStatusWhenIdle(): Boolean = false

    override fun matchesManagedSensorId(sensorId: String?): Boolean =
        NightscoutFollowerRegistry.matchesSensorId(SerialNumber, sensorId)

    override fun mygetDeviceName(): String = localizedString(R.string.nightscout_follow_title, "Nightscout follower")

    override fun getDetailedBleStatus(): String = status

    override fun getManagedCurrentSnapshot(maxAgeMillis: Long): ManagedSensorCurrentSnapshot? {
        val timestampMs = latestReadingTimeMs
        val glucoseMgdl = latestReadingMgdl
        if (timestampMs <= 0L || !glucoseMgdl.isFinite() || glucoseMgdl <= 0f) return null
        if (kotlin.math.abs(System.currentTimeMillis() - timestampMs) > maxAgeMillis) return null
        val glucoseDisplay = if (Applic.unit == 1) glucoseMgdl / MMOL_TO_MGDL else glucoseMgdl
        val rateDisplay = if (Applic.unit == 1) latestRateMgdlPerMin / MMOL_TO_MGDL else latestRateMgdlPerMin
        return ManagedSensorCurrentSnapshot(
            timeMillis = timestampMs,
            glucoseValue = glucoseDisplay,
            rawGlucoseValue = Float.NaN,
            rate = rateDisplay,
            sensorGen = SENSOR_GEN,
        )
    }

    override fun getManagedUiSnapshot(activeSensorId: String?): ManagedSensorUiSnapshot =
        ManagedSensorUiSnapshot(
            serial = SerialNumber,
            displayName = localizedString(R.string.nightscout_follow_title, "Nightscout follower"),
            deviceAddress = url,
            uiFamily = ManagedSensorUiFamily.GENERIC,
            connectionStatus = when (phase) {
                Phase.FOLLOWING -> localizedString(R.string.nightscout_follow_status_following, "Following Nightscout")
                Phase.SYNCING -> localizedString(R.string.nightscout_follow_status_syncing, "Refreshing Nightscout")
                Phase.IDLE -> localizedString(R.string.nightscout_follow_title, "Nightscout follower")
            },
            detailedStatus = status,
            subtitleStatus = status,
            showConnectionStatusInDetails = true,
            isUiEnabled = true,
            isActive = SensorIdentity.matches(activeSensorId, SerialNumber),
            dataptr = 0L,
            viewMode = viewMode,
            supportsDisplayModes = false,
            supportsManualCalibration = false,
            supportsHardwareReset = false,
            isVendorConnected = phase == Phase.FOLLOWING,
            vendorModel = localizedString(R.string.nightscout_follow_title, "Nightscout follower"),
        )

    override fun connectDevice(delayMillis: Long): Boolean {
        stop = false
        scheduleRefresh(delayMillis.coerceAtLeast(0L))
        return true
    }

    override fun close() {
        handler.removeCallbacksAndMessages(null)
        runCatching { handlerThread.quitSafely() }
        super.close()
    }

    override fun softDisconnect() {
        stop = true
        handler.removeCallbacksAndMessages(null)
        setStatus(Phase.IDLE, localizedString(R.string.nightscout_follow_status_paused, "Nightscout follower paused"))
    }

    override fun softReconnect() {
        stop = false
        scheduleRefresh(0L)
    }

    override fun terminateManagedSensor(wipeData: Boolean) {
        stop = true
        handler.removeCallbacksAndMessages(null)
        if (wipeData) {
            Applic.app?.let { NightscoutFollowerRegistry.disableFollowerSensor(it) }
        }
    }

    override fun removeManagedPersistence(context: Context) {
        NightscoutFollowerRegistry.disableFollowerSensor(context)
    }

    private fun localizedString(resId: Int, fallback: String): String =
        Applic.app?.getString(resId) ?: fallback

    private fun setStatus(phase: Phase, status: String) {
        this.phase = phase
        this.status = status
        UiRefreshBus.requestStatusRefresh()
    }

    private fun scheduleRefresh(delayMillis: Long) {
        handler.removeCallbacks(pollRunnable)
        if (!stop) {
            handler.postDelayed(pollRunnable, delayMillis)
        }
    }

    private fun refresh(reason: String) {
        if (stop) return
        if (url.isBlank()) {
            setStatus(Phase.IDLE, localizedString(R.string.nightscout_follow_status_config_needed, "Enter Nightscout URL"))
            return
        }
        setStatus(Phase.SYNCING, localizedString(R.string.nightscout_follow_status_syncing, "Refreshing Nightscout"))
        try {
            val readings = fetchReadings()
            if (readings.isEmpty()) {
                setStatus(Phase.IDLE, localizedString(R.string.nightscout_follow_status_no_readings, "No Nightscout readings yet"))
                scheduleRefresh(POLL_INTERVAL_MS)
                return
            }
            importHistory(readings)
            publishLatest(readings)
            setStatus(Phase.FOLLOWING, localizedString(R.string.nightscout_follow_status_following, "Following Nightscout"))
            Log.i(
                TAG,
                String.format(
                    Locale.US,
                    "Nightscout follower refreshed (%s): %s points latest=%.1f",
                    reason,
                    readings.size,
                    readings.last().glucoseMgdl,
                ),
            )
            UiRefreshBus.requestDataRefresh()
            scheduleRefresh(POLL_INTERVAL_MS)
        } catch (t: Throwable) {
            Log.stack(TAG, "refresh($reason)", t)
            setStatus(Phase.IDLE, localizedString(R.string.nightscout_follow_status_sync_failed, "Nightscout sync failed"))
            scheduleRefresh(RETRY_INTERVAL_MS)
        }
    }

    private fun importHistory(readings: List<VirtualGlucoseSensorBridge.Reading>) {
        val tailMs = readings.maxOfOrNull { it.timestampMs } ?: 0L
        if (tailMs > 0L && tailMs <= lastImportedHistoryTailMs) return
        VirtualGlucoseSensorBridge.importHistory(
            sensorSerial = SerialNumber,
            readings = readings,
            logLabel = "Nightscout follower",
        )
        if (tailMs > 0L) {
            lastImportedHistoryTailMs = tailMs
        }
    }

    private fun publishLatest(readings: List<VirtualGlucoseSensorBridge.Reading>) {
        val latest = readings.lastOrNull() ?: return
        val previous = readings.dropLast(1).lastOrNull()
        val rate = if (previous != null && latest.timestampMs > previous.timestampMs) {
            val minutes = (latest.timestampMs - previous.timestampMs) / 60000f
            if (minutes > 0f) (latest.glucoseMgdl - previous.glucoseMgdl) / minutes else 0f
        } else {
            0f
        }
        latestReadingTimeMs = latest.timestampMs
        latestReadingMgdl = latest.glucoseMgdl
        latestRateMgdlPerMin = rate
        VirtualGlucoseSensorBridge.publishCurrent(
            sensorSerial = SerialNumber,
            reading = latest.copy(rate = rate),
            sensorGen = SENSOR_GEN,
            logLabel = "Nightscout follower",
        )
    }

    private fun fetchReadings(): List<VirtualGlucoseSensorBridge.Reading> {
        val endpoint = "${NightscoutFollowerRegistry.normalizeUrl(url)}/api/v1/entries/sgv.json?count=$HISTORY_COUNT"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "JugglucoNG Nightscout follower")
            applyAuth(secret)
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("Nightscout HTTP $code: ${body.take(160)}")
        }
        val array = JSONArray(body)
        val readings = ArrayList<VirtualGlucoseSensorBridge.Reading>(array.length())
        for (index in 0 until array.length()) {
            parseEntry(array.optJSONObject(index))?.let(readings::add)
        }
        return readings
            .distinctBy { it.timestampMs }
            .sortedBy { it.timestampMs }
    }

    private fun HttpURLConnection.applyAuth(secret: String) {
        val trimmed = secret.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            setRequestProperty("Authorization", trimmed)
            return
        }
        if (trimmed.startsWith("token=", ignoreCase = true)) {
            setRequestProperty("Authorization", "Bearer ${trimmed.substringAfter('=')}")
            return
        }
        setRequestProperty("api-secret", if (trimmed.matches(Regex("^[0-9a-fA-F]{40}$"))) trimmed else sha1(trimmed))
    }

    private fun parseEntry(entry: JSONObject?): VirtualGlucoseSensorBridge.Reading? {
        entry ?: return null
        val mgdl = entry.optDouble("sgv", Double.NaN)
            .takeIf { it.isFinite() && it > 0.0 }
            ?: entry.optDouble("mbg", Double.NaN).takeIf { it.isFinite() && it > 0.0 }
            ?: return null
        val timestampMs = when {
            entry.has("date") -> entry.optLong("date", 0L)
            entry.has("mills") -> entry.optLong("mills", 0L)
            else -> 0L
        }.takeIf { it > 0L } ?: return null
        return VirtualGlucoseSensorBridge.Reading(
            timestampMs = timestampMs,
            glucoseMgdl = mgdl.toFloat(),
        )
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.US, it) }
}
