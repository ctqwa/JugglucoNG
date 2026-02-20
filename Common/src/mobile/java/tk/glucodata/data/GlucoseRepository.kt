package tk.glucodata.data

import tk.glucodata.Natives
import tk.glucodata.ui.GlucosePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import tk.glucodata.Applic
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
    private val _currentSerial = MutableStateFlow(Natives.lastsensorname() ?: "")
    val currentSerial = _currentSerial.asStateFlow()
    
    /**
     * Call this whenever the main sensor may have changed (resume, sensor switch, etc.).
     * If the serial actually changed, all flows keyed on [_currentSerial] will restart.
     */
    fun refreshSensorSerial() {
        val fresh = Natives.lastsensorname() ?: ""
        if (fresh != _currentSerial.value) {
            Log.d(TAG, "Sensor serial changed: '${_currentSerial.value}' → '$fresh'")
            _currentSerial.value = fresh
        }
    }
    
    companion object {
        private const val TAG = "GlucoseRepo"
    }

    /**
     * Get the current reading for the main sensor.
     * OBSERVES the Room Database (Single Source of Truth) filtered by main sensor serial.
     * Also runs a background Poller to fetch Native data and write it to the DB.
     * This unifies Native (Polled) and AiDex (Pushed) data streams.
     *
     * Reactive: Uses [flatMapLatest] on [_currentSerial] so when the main sensor
     * changes, the flow automatically re-subscribes to the new sensor's data.
     */
    fun getCurrentReading(): Flow<GlucosePoint?> = channelFlow {
        // Ensure backfill is done on first access
        historyRepository.ensureBackfilled()

        // 1. Launch Background Poller for Native Data
        launch {
            while (isActive) {
                pollNativeAndStore()
                delay(3000)
            }
        }

        // 2. Observe Database for main sensor updates — reactive to sensor changes
        _currentSerial.flatMapLatest { serial ->
            if (serial.isNotEmpty()) {
                historyRepository.getLatestReadingFlowForSensor(serial)
            } else {
                Log.w(TAG, "getCurrentReading: no main sensor serial, returning empty flow")
                flowOf(null)
            }
        }.collect { point ->
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
            // Get the main sensor serial for tagging
            val sensorSerial = Natives.lastsensorname() ?: "unknown"
            
            // Fetch the last 2 minutes of main-sensor history.
            // getGlucoseHistory uses infoblockptr()->current — main sensor only.
            val nowSec = System.currentTimeMillis() / 1000L
            val rawHistory = Natives.getGlucoseHistory(nowSec - 120)
            if (rawHistory == null || rawHistory.size < 3) return

            // Find the LAST (most recent) triplet
            val lastIdx = rawHistory.size - 3
            val timeSec = rawHistory[lastIdx]
            val timestampMs = timeSec * 1000L

            // Skip if we already stored this exact reading
            if (timestampMs == lastStoredTimestampMs) return
            
            val valueMgdl = rawHistory[lastIdx + 1] / 10f   // mg/dL * 10 → mg/dL
            val rawValueMgdl = rawHistory[lastIdx + 2] / 10f // mg/dL * 10 → mg/dL

            if (valueMgdl <= 0 && rawValueMgdl <= 0) return

            // Compute approximate rate from the last two entries if available
            var rate = 0f
            if (rawHistory.size >= 6) {
                val prevIdx = rawHistory.size - 6
                val prevTimeSec = rawHistory[prevIdx]
                val prevValue = rawHistory[prevIdx + 1] / 10f
                val dtMin = (timeSec - prevTimeSec) / 60f
                if (dtMin > 0 && prevValue > 0) {
                    rate = (valueMgdl - prevValue) / dtMin  // mg/dL per minute
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
        historyRepository.ensureBackfilled()
        
        // Fetch history for main sensor only — never fall back to all-sensors
        val serial = Natives.lastsensorname() ?: ""
        if (serial.isEmpty()) {
            Log.w(TAG, "getAllHistory: no main sensor serial, returning empty list")
            return emptyList()
        }
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
                historyRepository.getHistoryFlowForSensor(serial, startTime)
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
                historyRepository.getHistoryFlowForSensor(serial, startTime)
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
        val history = mutableListOf<GlucosePoint>()
        
        try {
            // Fetch from the very beginning (startSec = 0 means all data)
            val startSec = 0L
            
            val unit = Natives.getunit()
            val isMmol = (unit == 1)
            
            val rawHistory = Natives.getGlucoseHistory(startSec)
            if (rawHistory != null) {
                Log.d(TAG, "getGlucoseHistory returned ${rawHistory.size / 3} points (ALL history)")
                try {
                    for (i in rawHistory.indices step 3) {
                        if (i + 2 >= rawHistory.size) break
                        val timeSec = rawHistory[i]
                        val valueAutoRaw = rawHistory[i+1]
                        val valueRawRaw = rawHistory[i+2]
                        
                        var value = valueAutoRaw / 10f // mg/dL
                        var valueRaw = valueRawRaw / 10f // mg/dL
                        
                        if (isMmol) {
                            value = value / 18.0182f
                            valueRaw = valueRaw / 18.0182f
                        }
                        
                        val timeMs = timeSec * 1000L
                        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timeMs))
                        history.add(GlucosePoint(value, timeStr, timeMs, valueRaw, 0f))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing history", e)
                }
            } else {
                Log.d(TAG, "getGlucoseHistory returned null. ViewMode might be changing or no data.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching history", e)
        }
        
        return history.sortedBy { it.timestamp }
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
        val history = mutableListOf<GlucosePoint>()
        try {
            val nowSec = System.currentTimeMillis() / 1000L
            val startSec = (nowSec - (durationMs / 1000)).coerceAtLeast(0)
            
            val unit = Natives.getunit()
            val isMmol = (unit == 1)
            
            val rawHistory = Natives.getGlucoseHistory(startSec)
            if (rawHistory != null) {
                try {
                    for (i in rawHistory.indices step 3) {
                        if (i + 2 >= rawHistory.size) break
                        val timeSec = rawHistory[i]
                        val valueAutoRaw = rawHistory[i+1]
                        val valueRawRaw = rawHistory[i+2]
                        
                        var value = valueAutoRaw / 10f // mg/dL
                        var valueRaw = valueRawRaw / 10f // mg/dL
                        
                        if (isMmol) {
                            value = value / 18.0182f
                            valueRaw = valueRaw / 18.0182f
                        }
                        
                        val timeMs = timeSec * 1000L
                        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timeMs))
                        history.add(GlucosePoint(value, timeStr, timeMs, valueRaw, 0f))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing history in getRecentHistory", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recent history", e)
        }
        return history.sortedBy { it.timestamp }
    }
}
