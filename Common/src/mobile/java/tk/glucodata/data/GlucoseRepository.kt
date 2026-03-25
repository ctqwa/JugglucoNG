package tk.glucodata.data

import tk.glucodata.Natives
import tk.glucodata.ui.GlucosePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.util.Log
import android.os.SystemClock
import tk.glucodata.Applic
import tk.glucodata.BatteryTrace
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Repository that bridges native glucose data with the independent Room history database.
 * New readings are stored in Room for long-term history while still using native data for
 * real-time display and calibration.
 *
 * Multi-sensor: All queries filter by the main sensor serial (lastsensorname()) so the
 * dashboard/chart shows only the currently selected sensor. The underlying Room table
 * stores data from ALL sensors via the sensorSerial column.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseRepository {
    
    private val historyRepository = HistoryRepository(Applic.app)
    
    /**
     * Reactive sensor serial. Updated by [refreshSensorSerial] (called from
     * DashboardViewModel.onResume / refreshData). Flows that depend on the
     * current sensor use [flatMapLatest] on this so they automatically
     * re-subscribe when the main sensor changes.
     */
    private val _currentSerial = MutableStateFlow(
        Natives.lastsensorname()?.takeIf { it.isNotBlank() } ?: ""
    )
    val currentSerial = _currentSerial.asStateFlow()
    
    /**
     * Call this whenever the main sensor may have changed (resume, sensor switch, etc.).
     * If the serial actually changed, all flows keyed on [_currentSerial] will restart.
     */
    fun refreshSensorSerial(preferredSerial: String? = null) {
        val current = _currentSerial.value
        val resolved = preferredSerial?.takeIf { it.isNotBlank() }
            ?: Natives.lastsensorname()?.takeIf { it.isNotBlank() }
            ?: current.takeIf { it.isNotBlank() }
            ?: Natives.activeSensors()?.firstOrNull { !it.isNullOrBlank() }
            ?: ""

        if (resolved != current) {
            Log.d(TAG, "Sensor serial changed: '$current' → '$resolved'")
            _currentSerial.value = resolved
        }
    }
    
    companion object {
        private const val TAG = "GlucoseRepo"
        private const val ONE_SHOT_SYNC_MIN_INTERVAL_MS = 5_000L
    }

    @Volatile
    private var lastOneShotSyncStartedAtMs = 0L

    /**
     * Get the current reading for the main sensor.
     * OBSERVES the Room Database (Single Source of Truth) filtered by main sensor serial.
     * Live updates are pushed into Room from real sensor events; targeted native recovery
     * remains explicit in higher layers (startup/reconnect/manual repair) instead of being
     * hidden inside the current-reading subscription.
     *
     * Reactive: Uses [flatMapLatest] on [_currentSerial] so when the main sensor
     * changes, the flow automatically re-subscribes to the new sensor's data.
     */
    fun getCurrentReading(): Flow<GlucosePoint?> = channelFlow {
        _currentSerial.collectLatest { serial ->
            if (serial.isEmpty()) {
                Log.w(TAG, "getCurrentReading: no main sensor serial, returning empty flow")
                send(null)
                return@collectLatest
            }

            historyRepository.getLatestReadingFlowForSensor(serial).collect { point ->
                val unit = Natives.getunit()
                val isMmol = (unit == 1)

                if (point != null) {
                    // Apply Unit Conversion for Display
                    val displayValue = if (isMmol) point.value / 18.0182f else point.value
                    val displayRaw = if (isMmol) point.rawValue / 18.0182f else point.rawValue

                    val finalPoint = GlucosePoint(
                        value = displayValue,
                        time = point.time, // Already formatted HH:mm
                        timestamp = point.timestamp,
                        rawValue = displayRaw,
                        rate = point.rate
                    )
                    send(finalPoint)
                } else {
                    send(null)
                }
            }
        }
    }

    suspend fun syncLatestNativeReadingOnce() {
        val nowMs = SystemClock.elapsedRealtime()
        synchronized(this) {
            if ((nowMs - lastOneShotSyncStartedAtMs) < ONE_SHOT_SYNC_MIN_INTERVAL_MS) {
                Log.d(TAG, "syncLatestNativeReadingOnce skipped — last run was ${(nowMs - lastOneShotSyncStartedAtMs)}ms ago")
                return
            }
            lastOneShotSyncStartedAtMs = nowMs
        }
        BatteryTrace.bump("glucose.native.one_shot_sync", logEvery = 20L)
        pollNativeAndStore()
    }

    /** Timestamp (ms) of the last reading we stored, to avoid redundant writes. */
    private var lastStoredTimestampMs = 0L

    /**
     * Poll the main sensor's latest data and store it in Room, tagged with sensor serial.
     *
     * IMPORTANT: We use ONLY [Natives.getGlucoseHistory] here, which reads from
     * the user-selected main sensor ([infoblockptr()->current] in C++).
     * The reading is tagged with [Natives.lastsensorname] to identify which sensor
     * produced it.
     */
    private suspend fun pollNativeAndStore() {
        try {
            BatteryTrace.bump("glucose.native.poll_once", logEvery = 20L)
            // Get the main sensor serial for tagging
            val sensorSerial = Natives.lastsensorname() ?: "unknown"
            if (sensorSerial.isBlank()) return
            
            // Fetch the last 2 minutes of main-sensor history.
            // getGlucoseHistory uses infoblockptr()->current — main sensor only.
            val nowSec = System.currentTimeMillis() / 1000L
            val rawHistory = Natives.getGlucoseHistory(nowSec - 120)
            if (rawHistory == null || rawHistory.size < 3) return

            val lastIdx = rawHistory.size - 3
            val timeSec = rawHistory[lastIdx]
            val timestampMs = timeSec * 1000L

            // Skip if we already stored this exact reading
            if (timestampMs == lastStoredTimestampMs) return

            val valueMgdl = rawHistory[lastIdx + 1] / 10f
            val rawValueMgdl = rawHistory[lastIdx + 2] / 10f
            if (valueMgdl <= 0f && rawValueMgdl <= 0f) return

            var rate = 0f
            if (rawHistory.size >= 6) {
                val prevIdx = rawHistory.size - 6
                val prevTimeSec = rawHistory[prevIdx]
                val prevValueMgdl = rawHistory[prevIdx + 1] / 10f
                val dtMin = (timeSec - prevTimeSec) / 60f
                if (dtMin > 0f && prevValueMgdl > 0f) {
                    rate = (valueMgdl - prevValueMgdl) / dtMin
                }
            }

            historyRepository.storeReading(
                timestamp = timestampMs,
                value = valueMgdl,
                rawValue = rawValueMgdl,
                rate = rate,
                sensorSerial = sensorSerial
            )
            lastStoredTimestampMs = timestampMs
        } catch (e: Exception) {
            Log.e(TAG, "Error polling native glucose: ${e.message}")
        }
    }

    /**
     * Get ALL history from the Room database for the main sensor.
     * No time limit - fetches everything available for the main sensor.
     */
    suspend fun getAllHistory(): List<GlucosePoint> {
        val unit = Natives.getunit()
        val isMmol = (unit == 1)
        
        // Ensure backfill is done
        // Fetch history for main sensor only — never fall back to all-sensors
        val serial = Natives.lastsensorname() ?: ""
        if (serial.isEmpty()) {
            Log.w(TAG, "getAllHistory: no main sensor serial, returning empty list")
            return emptyList()
        }
        historyRepository.ensureBackfilled(serial)
        val rawHistory = historyRepository.getHistoryForSensor(serial, 0L)
        
        // Convert to display unit
        return rawHistory.map { p ->
             val v = if (isMmol) p.value / 18.0182f else p.value
             val r = if (isMmol) p.rawValue / 18.0182f else p.rawValue
             GlucosePoint(v, p.time, p.timestamp, r, p.rate)
        }
    }

    /**
     * Get history since startTime, converting if needed.
     * Filters by main sensor serial.
     */
    suspend fun getHistory(startTime: Long, isMmol: Boolean): List<GlucosePoint> {
        val serial = Natives.lastsensorname() ?: ""
        if (serial.isEmpty()) {
            Log.w(TAG, "getHistory: no main sensor serial, returning empty list")
            return emptyList()
        }
        historyRepository.ensureBackfilled(serial)
        val raw = historyRepository.getHistoryForSensor(serial, startTime)
        return if (isMmol) {
            raw.map { p ->
                val v = p.value / 18.0182f
                val r = p.rawValue / 18.0182f
                GlucosePoint(v, p.time, p.timestamp, r, p.rate)
            }
        } else raw
    }

    /**
     * Get history as a Flow for reactive updates.
     * Filters by main sensor serial. Reactive to sensor changes via [_currentSerial].
     */
    fun getHistoryFlow(startTime: Long = 0L, isMmol: Boolean): Flow<List<GlucosePoint>> {
        return _currentSerial.flatMapLatest { serial ->
            if (serial.isNotEmpty()) {
                channelFlow {
                    launch {
                        historyRepository.ensureBackfilled(serial)
                    }
                    historyRepository.getHistoryFlowForSensor(serial, startTime).collect { points ->
                        send(points)
                    }
                }
            } else {
                Log.w(TAG, "getHistoryFlow: no main sensor serial, returning empty flow")
                flowOf(emptyList())
            }
        }.map { list ->
            list.map { p ->
                 val v = if (isMmol) p.value / 18.0182f else p.value
                 val r = if (isMmol) p.rawValue / 18.0182f else p.rawValue
                 GlucosePoint(v, p.time, p.timestamp, r, p.rate)
            }
        }
    }

    /**
     * Get history as a Flow in RAW mg/dL (no conversion).
     * Filters by main sensor serial. Reactive to sensor changes via [_currentSerial].
     */
    fun getHistoryFlowRaw(startTime: Long = 0L): Flow<List<GlucosePoint>> {
        return _currentSerial.flatMapLatest { serial ->
            if (serial.isNotEmpty()) {
                channelFlow {
                    launch {
                        historyRepository.ensureBackfilled(serial)
                    }
                    historyRepository.getHistoryFlowForSensor(serial, startTime).collect { points ->
                        send(points)
                    }
                }
            } else {
                Log.w(TAG, "getHistoryFlowRaw: no main sensor serial, returning empty flow")
                flowOf(emptyList())
            }
        }
    }

    /**
     * Legacy synchronous method - fetches ALL history from native layer.
     * Used for initial load and when Room hasn't been populated yet.
     */
    fun getHistory(): List<GlucosePoint> {
        return kotlinx.coroutines.runBlocking {
            val serial = Natives.lastsensorname() ?: ""
            if (serial.isBlank()) return@runBlocking emptyList()

            historyRepository.ensureBackfilled(serial)
            val rawHistory = historyRepository.getHistoryForSensor(serial, 0L)
            val isMmol = (Natives.getunit() == 1)
            rawHistory.map { p ->
                val v = if (isMmol) p.value / 18.0182f else p.value
                val r = if (isMmol) p.rawValue / 18.0182f else p.rawValue
                GlucosePoint(v, p.time, p.timestamp, r, p.rate)
            }
        }
    }

    fun getUnit(): String {
        return when (Natives.getunit()) {
            1 -> "mmol/L"
            2 -> "mg/dL"
            else -> "mmol/L"
        }
    }

    /**
     * Efficiently get recent history from Native layer for Widgets/Glance.
     * Fetches only the requested duration to minimize memory usage in AppWidget process.
     */
    fun getRecentHistory(durationMs: Long = 24 * 60 * 60 * 1000L): List<GlucosePoint> {
        return kotlinx.coroutines.runBlocking {
            try {
                val serial = Natives.lastsensorname() ?: ""
                if (serial.isBlank()) return@runBlocking emptyList()

                historyRepository.ensureBackfilled(serial)
                val startMs = (System.currentTimeMillis() - durationMs).coerceAtLeast(0L)
                val rawHistory = historyRepository.getHistoryForSensor(serial, startMs)
                val isMmol = (Natives.getunit() == 1)
                rawHistory.map { p ->
                    val v = if (isMmol) p.value / 18.0182f else p.value
                    val r = if (isMmol) p.rawValue / 18.0182f else p.rawValue
                    GlucosePoint(v, p.time, p.timestamp, r, p.rate)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching recent history", e)
                emptyList()
            }
        }.sortedBy { it.timestamp }
    }
}
