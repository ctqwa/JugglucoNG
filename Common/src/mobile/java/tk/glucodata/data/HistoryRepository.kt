package tk.glucodata.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.data.calibration.CalibrationManager
import tk.glucodata.ui.GlucosePoint

/**
 * Repository for managing the independent glucose history database.
 * Handles:
 * - Storing new readings from the native layer (tagged with sensor serial)
 * - Backfilling ALL existing history from ALL active sensors on first run
 * - Querying history for chart display (per-sensor or all)
 */
class HistoryRepository(context: Context = Applic.app) {
    
    private val dao = HistoryDatabase.getInstance(context).historyDao()
    
    companion object {
        private const val TAG = "HistoryRepo"
        
        @Volatile
        private var backfillCompleted = false
        
        /**
         * Reset the backfill flag so ensureBackfilled() re-runs.
         * Called after vendor history download completes — if ensureBackfilled() ran before
         * the download finished, it got 0 records and set backfillCompleted=true.
         * This allows it to re-run and pick up the newly downloaded data.
         */
        @JvmStatic
        fun resetBackfillFlag() {
            backfillCompleted = false
            Log.d(TAG, "backfillCompleted reset — ensureBackfilled() will re-run")
        }
        
        /**
         * Blocking version of getHistory for Java access.
         * This runs the suspend function on a blocking coroutine.
         * Should be called from a background thread.
         */
        @JvmStatic
        fun getHistoryBlocking(startTime: Long, isMmol: Boolean): List<GlucosePoint> {
            return kotlinx.coroutines.runBlocking {
                val raw = HistoryRepository().getHistory(startTime)
                if (isMmol) {
                    raw.map { p ->
                        val v = p.value / 18.0182f
                        val r = p.rawValue / 18.0182f
                        GlucosePoint(v, p.time, p.timestamp, r, p.rate)
                    }
                } else raw
            }
        }
        
        const val HISTORY_SOURCE_NATIVE = 1
        const val GLUCODATA_SOURCE_AIDEX = 4
        
        @JvmStatic
        fun storeReadingAsync(timestamp: Long, valueMmol: Float, source: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val valueMgDl = valueMmol * 18.0182f
                    // Use main sensor serial for source tagging
                    val serial = Natives.lastsensorname() ?: "unknown"
                    HistoryRepository().storeReading(
                        timestamp = timestamp,
                        value = valueMgDl,
                        rawValue = valueMgDl,
                        rate = 0f,
                        sensorSerial = serial
                    )
                    Log.d(TAG, "Stored reading: $valueMgDl mg/dL from source $source [$serial]")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to store reading", e)
                }
            }
        }
        
        /**
         * Blocking version for Notify.java that returns tk.glucodata.GlucosePoint.
         * Filters by main sensor serial.
         */
        @JvmStatic
        fun getHistoryForNotification(startTime: Long, isMmol: Boolean): List<tk.glucodata.GlucosePoint> {
            return kotlinx.coroutines.runBlocking {
                val serial = Natives.lastsensorname() ?: ""
                val repo = HistoryRepository()
                val uiPoints = if (serial.isNotEmpty()) {
                    repo.getHistoryForSensor(serial, startTime)
                } else {
                    Log.w(TAG, "getHistoryForNotification: no main sensor serial, returning empty list")
                    emptyList()
                }
                uiPoints.map { p ->
                    val v = if (isMmol) p.value / 18.0182f else p.value
                    val r = if (isMmol) p.rawValue / 18.0182f else p.rawValue
                    tk.glucodata.GlucosePoint(p.timestamp, v, r)
                }
            }
        }
        
        /**
         * Blocking version for Notify.java returning raw mg/dL.
         * Filters by main sensor serial.
         */
        @JvmStatic
        fun getHistoryRawForNotification(startTime: Long): List<tk.glucodata.GlucosePoint> {
            return kotlinx.coroutines.runBlocking {
                val serial = Natives.lastsensorname() ?: ""
                val repo = HistoryRepository()
                val uiPoints = if (serial.isNotEmpty()) {
                    repo.getHistoryForSensor(serial, startTime)
                } else {
                    Log.w(TAG, "getHistoryRawForNotification: no main sensor serial, returning empty list")
                    emptyList()
                }
                uiPoints.map { p ->
                    tk.glucodata.GlucosePoint(p.timestamp, p.value, p.rawValue)
                }
            }
        }

        /**
         * Async helper for Java callers (e.g. AiDexProbe) to store readings without blocking.
         * Launches a coroutine in IO scope.
         */
        @JvmStatic
        fun storeReadingAsync(timestamp: Long, value: Float, rawValue: Float, rate: Float) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                HistoryRepository().storeReading(timestamp, value, rawValue, rate)
            }
        }

        /**
         * Async helper that includes sensor serial. Preferred over the 4-arg variant.
         */
        @JvmStatic
        fun storeReadingAsync(timestamp: Long, value: Float, rawValue: Float, rate: Float, sensorSerial: String) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                HistoryRepository().storeReading(timestamp, value, rawValue, rate, sensorSerial)
            }
        }
    }
    
    /**
     * Store a new glucose reading in the history database.
     * Values should be in mg/dL (will be converted on display).
     * Uses main sensor serial if none specified.
     */
    suspend fun storeReading(timestamp: Long, value: Float, rawValue: Float, rate: Float, sensorSerial: String? = null) {
        // Don't store invalid readings
        if (value <= 0 && rawValue <= 0) return
        
        val serial = sensorSerial ?: Natives.lastsensorname() ?: "unknown"
        val storedValue = maybeProjectCalibratedValueForStorage(
            sensorSerial = serial,
            timestamp = timestamp,
            value = value,
            rawValue = rawValue
        )
        val reading = HistoryReading(
            timestamp = timestamp,
            sensorSerial = serial,
            value = storedValue,
            rawValue = rawValue,
            rate = rate
        )
        withContext(Dispatchers.IO) {
            try {
                dao.insert(reading)
            } catch (e: Exception) {
                Log.e(TAG, "Error storing reading", e)
            }
        }
    }

    private fun resolveSensorViewMode(sensorSerial: String): Int {
        return try {
            val dataPtr = Natives.getdataptr(sensorSerial)
            if (dataPtr == 0L) {
                0
            } else {
                try {
                    Natives.getViewMode(dataPtr)
                } finally {
                    Natives.freedataptr(dataPtr)
                }
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun maybeProjectCalibratedValueForStorage(
        sensorSerial: String,
        timestamp: Long,
        value: Float,
        rawValue: Float
    ): Float {
        if (!CalibrationManager.shouldOverwriteSensorValues()) return value

        val viewMode = resolveSensorViewMode(sensorSerial)
        val isRawMode = viewMode == 1 || viewMode == 3
        if (!CalibrationManager.hasActiveCalibration(isRawMode, sensorSerial)) return value

        val baseValue = if (isRawMode) rawValue else value
        if (!baseValue.isFinite() || baseValue <= 0f) return value

        val calibrated = CalibrationManager.getCalibratedValue(
            value = baseValue,
            timestamp = timestamp,
            isRawMode = isRawMode,
            sensorIdOverride = sensorSerial
        )
        return if (calibrated.isFinite() && calibrated > 0f) calibrated else value
    }
    
    /**
     * Store multiple readings at once (used for backfill).
     * Readings must already have sensorSerial set.
     */
    suspend fun storeReadings(readings: List<HistoryReading>) {
        if (readings.isEmpty()) return
        
        withContext(Dispatchers.IO) {
            try {
                dao.insertAll(readings)
                // Only log small batches (likely genuine new data, not re-syncs)
                if (readings.size <= 10) {
                    Log.d(TAG, "Stored ${readings.size} readings")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error storing readings batch", e)
            }
        }
    }

    // ── Per-sensor query methods (for dashboard, chart, current reading) ──
    
    /**
     * Get history for a specific sensor as a Flow (Raw mg/dL).
     */
    fun getHistoryFlowForSensor(serial: String, startTime: Long = 0L): kotlinx.coroutines.flow.Flow<List<GlucosePoint>> {
        return dao.getHistoryFlowForSensor(serial, startTime).map { readings ->
            readings.map { reading ->
                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(reading.timestamp))
                GlucosePoint(
                    value = reading.value,
                    time = timeStr,
                    timestamp = reading.timestamp,
                    rawValue = reading.rawValue,
                    rate = reading.rate
                )
            }.distinctBy { it.timestamp }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Stats-only flow optimized for large datasets:
     * - No per-point time formatting
     * - No extra sorting/distinct pass (DAO already returns ASC by timestamp)
     */
    fun getHistoryFlowForStatsSensor(
        serial: String,
        startTime: Long
    ): kotlinx.coroutines.flow.Flow<List<GlucosePoint>> {
        return dao.getHistoryFlowForSensor(serial, startTime).map { readings ->
            readings.map { reading ->
                GlucosePoint(
                    value = reading.value,
                    time = "",
                    timestamp = reading.timestamp,
                    rawValue = reading.rawValue,
                    rate = reading.rate
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get the latest reading for a specific sensor as a reactive Flow.
     */
    fun getLatestReadingFlowForSensor(serial: String): kotlinx.coroutines.flow.Flow<GlucosePoint?> {
        return dao.getLatestReadingFlowForSensor(serial).map { reading ->
            reading?.let {
                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(it.timestamp))
                GlucosePoint(
                    value = it.value,
                    time = timeStr,
                    timestamp = it.timestamp,
                    rawValue = it.rawValue,
                    rate = it.rate
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get history for a specific sensor (suspend, Raw mg/dL).
     */
    suspend fun getHistoryForSensor(serial: String, startTime: Long): List<GlucosePoint> {
        return withContext(Dispatchers.IO) {
            try {
                val readings = dao.getReadingsSinceForSensor(serial, startTime)
                readings.map { reading ->
                    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(reading.timestamp))
                    GlucosePoint(
                        value = reading.value,
                        time = timeStr,
                        timestamp = reading.timestamp,
                        rawValue = reading.rawValue,
                        rate = reading.rate
                    )
                }.distinctBy { it.timestamp }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting history for sensor $serial", e)
                emptyList()
            }
        }
    }

    /**
     * Get the timestamp of the latest stored reading for a specific sensor.
     * Returns 0 if no readings exist for that sensor.
     */
    suspend fun getLatestTimestampForSensor(serial: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                dao.getLatestReadingForSensor(serial)?.timestamp ?: 0L
            } catch (e: Exception) {
                Log.e(TAG, "Error getting latest timestamp for sensor $serial", e)
                0L
            }
        }
    }

    // ── All-sensor query methods (for export, legacy compatibility) ──
    
    /**
     * Get history as a Flow for reactive updates (Raw mg/dL, all sensors).
     */
    fun getHistoryFlow(startTime: Long = 0L): kotlinx.coroutines.flow.Flow<List<GlucosePoint>> {
        return dao.getHistoryFlow(startTime).map { readings ->
            readings.map { reading ->
                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(reading.timestamp))
                
                GlucosePoint(
                    value = reading.value,
                    time = timeStr,
                    timestamp = reading.timestamp,
                    rawValue = reading.rawValue,
                    rate = reading.rate
                )
            }.distinctBy { it.timestamp }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get the latest reading as a reactive Flow (any sensor).
     * Legacy: use getLatestReadingFlowForSensor() for per-sensor queries.
     */
    fun getLatestReadingFlow(): kotlinx.coroutines.flow.Flow<GlucosePoint?> {
        return dao.getLatestReadingFlow().map { reading ->
            reading?.let {
                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(it.timestamp))
                GlucosePoint(
                    value = it.value,
                    time = timeStr,
                    timestamp = it.timestamp,
                    rawValue = it.rawValue,
                    rate = it.rate
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get history for chart display (Raw mg/dL, all sensors).
     * @param startTime Start time in milliseconds (0 = all data)
     */
    suspend fun getHistory(startTime: Long): List<GlucosePoint> {
        return withContext(Dispatchers.IO) {
            try {
                val readings = dao.getReadingsSince(startTime)
                readings.map { reading ->
                    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(reading.timestamp))
                    
                    GlucosePoint(
                        value = reading.value,
                        time = timeStr,
                        timestamp = reading.timestamp,
                        rawValue = reading.rawValue,
                        rate = reading.rate
                    )
                }.distinctBy { it.timestamp }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting history", e)
                emptyList()
            }
        }
    }

    /**
     * Get history in raw mg/dL (no conversion, all sensors).
     */
    suspend fun getHistoryRaw(startTime: Long): List<GlucosePoint> {
        return withContext(Dispatchers.IO) {
            try {
                val readings = dao.getReadingsSince(startTime)
                readings.map { reading ->
                    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(reading.timestamp))
                    
                    GlucosePoint(
                        value = reading.value,
                        time = timeStr,
                        timestamp = reading.timestamp,
                        rawValue = reading.rawValue,
                        rate = reading.rate
                    )
                }.distinctBy { it.timestamp }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting raw history", e)
                emptyList()
            }
        }
    }

    
    /**
     * Get the count of stored readings (all sensors).
     */
    suspend fun getReadingCount(): Int {
        return withContext(Dispatchers.IO) {
            try {
                dao.getCount()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting count", e)
                0
            }
        }
    }
    
    /**
     * Get the timestamp of the latest stored reading (any sensor).
     * Returns 0 if no readings exist.
     */
    suspend fun getLatestTimestamp(): Long {
        return withContext(Dispatchers.IO) {
            try {
                dao.getLatestReading()?.timestamp ?: 0L
            } catch (e: Exception) {
                Log.e(TAG, "Error getting latest timestamp", e)
                0L
            }
        }
    }
    
    /**
     * Backfill ALL history from native layer on first run.
     * Multi-sensor: syncs ALL active sensors, not just the main one.
     * Only runs once per app session.
     */
    suspend fun ensureBackfilled() {
        if (backfillCompleted) return
        
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Session start - merging native history for ALL active sensors into database")
                backfillAllSensors()
                backfillCompleted = true
            } catch (e: Exception) {
                Log.e(TAG, "Error during backfill", e)
            }
        }
    }
    
    /**
     * Import ALL existing history from the native C++ layer for ALL active sensors.
     * Multi-sensor: iterates activeSensors() and uses getGlucoseHistoryForSensor().
     */
    private suspend fun backfillAllSensors() {
        try {
            val activeSensors = Natives.activeSensors()
            if (activeSensors == null || activeSensors.isEmpty()) {
                // Fallback: try main sensor via the old single-sensor path
                val mainSensor = Natives.lastsensorname()
                if (!mainSensor.isNullOrEmpty()) {
                    backfillSensor(mainSensor)
                } else {
                    Log.d(TAG, "No active sensors for backfill")
                }
                return
            }
            
            for (serial in activeSensors) {
                if (serial.isNullOrEmpty()) continue
                backfillSensor(serial)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error backfilling all sensors", e)
        }
    }

    /**
     * Backfill a single sensor's data from the native layer.
     */
    private suspend fun backfillSensor(serial: String) {
        try {
            val rawHistory = Natives.getGlucoseHistoryForSensor(serial, 0L)
            if (rawHistory == null) {
                Log.d(TAG, "Native history for $serial returned null")
                return
            }

            val readings = mutableListOf<HistoryReading>()
            for (i in rawHistory.indices step 3) {
                if (i + 2 >= rawHistory.size) break

                val timeSec = rawHistory[i]
                val valueAutoRaw = rawHistory[i + 1]
                val valueRawRaw = rawHistory[i + 2]

                // Values from native are in mg/dL * 10
                val value = valueAutoRaw / 10f
                val rawValue = valueRawRaw / 10f

                if (value > 0 || rawValue > 0) {
                    readings.add(HistoryReading(
                        timestamp = timeSec * 1000L,
                        sensorSerial = serial,
                        value = value,
                        rawValue = rawValue,
                        rate = 0f  // Rate not available from history
                    ))
                }
            }

            if (readings.isNotEmpty()) {
                dao.insertAll(readings)
                Log.d(TAG, "Backfilled ${readings.size} readings from native for sensor $serial")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error backfilling sensor $serial", e)
        }
    }

    /**
     * Delete all Room history for a specific sensor.
     * Used before re-syncing after localReplay — since the DAO uses IGNORE on
     * conflict, recalibrated values with unchanged timestamps would be silently
     * skipped. Deleting first forces a clean re-insert.
     */
    suspend fun deleteForSensor(serial: String) {
        withContext(Dispatchers.IO) {
            try {
                dao.deleteForSensor(serial)
                Log.d(TAG, "Deleted Room data for sensor $serial")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting data for sensor $serial", e)
            }
        }
    }

    suspend fun rewriteSensorValuesWithCalibration(
        sensorSerial: String,
        isRawMode: Boolean,
        startTimestamp: Long = 0L
    ): Int {
        if (sensorSerial.isBlank()) return 0
        if (!CalibrationManager.hasActiveCalibration(isRawMode, sensorSerial)) return 0
        val effectiveStartTimestamp = if (CalibrationManager.shouldLockPastHistory()) {
            startTimestamp.coerceAtLeast(0L)
        } else {
            0L
        }

        return withContext(Dispatchers.IO) {
            try {
                val readings = dao.getReadingsSinceForSensor(sensorSerial, effectiveStartTimestamp)
                var updated = 0
                readings.forEach { reading ->
                    val baseValue = if (isRawMode) reading.rawValue else reading.value
                    if (!baseValue.isFinite() || baseValue <= 0f) return@forEach
                    val calibrated = CalibrationManager.getCalibratedValue(
                        value = baseValue,
                        timestamp = reading.timestamp,
                        isRawMode = isRawMode,
                        sensorIdOverride = sensorSerial
                    )
                    if (!calibrated.isFinite() || calibrated <= 0f) return@forEach
                    if (kotlin.math.abs(calibrated - reading.value) < 0.01f) return@forEach
                    updated += dao.updateValueAtTime(
                        sensorSerial = sensorSerial,
                        timestamp = reading.timestamp,
                        value = calibrated
                    )
                }
                if (updated > 0) {
                    Log.d(
                        TAG,
                        "Rewrote $updated readings with calibrated values for $sensorSerial (start=$effectiveStartTimestamp)"
                    )
                }
                updated
            } catch (e: Exception) {
                Log.e(TAG, "Failed rewriting calibrated values for $sensorSerial", e)
                0
            }
        }
    }
}
