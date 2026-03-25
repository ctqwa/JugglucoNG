package tk.glucodata.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tk.glucodata.Applic
import tk.glucodata.BatteryTrace
import tk.glucodata.Natives
import tk.glucodata.UiRefreshBus
import java.util.concurrent.ConcurrentHashMap
import java.util.LinkedHashSet

/**
 * Singleton that synchronizes native glucose history with the Room database.
 *
 * Multi-sensor: Iterates ALL active sensors via [Natives.activeSensors] and syncs
 * each one independently using [Natives.getGlucoseHistoryForSensor]. Each reading
 * is tagged with its sensor serial so data from different sensors coexists in the
 * same Room table without conflict.
 *
 * NO MORE clearAllTables() — sensor switching is handled by querying the appropriate
 * sensorSerial, not by destroying and rebuilding the database.
 */
object HistorySync {

    private const val TAG = "HistorySync"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val historyRepository = HistoryRepository(Applic.app)

    // Track if we've done the initial full sync this session
    @Volatile
    private var initialSyncDone = false

    // Per-sensor backfill stability tracking.
    // Key = sensor serial, Value = (lastReadingCount, stableCount).
    private val sensorStability = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val sensorLastSyncTimeMs = ConcurrentHashMap<String, Long>()
    private val sensorSyncInProgress = ConcurrentHashMap.newKeySet<String>()
    private val sensorRecentSyncInProgress = ConcurrentHashMap.newKeySet<String>()
    private val sensorLastRecentSyncBucketMs = ConcurrentHashMap<String, Long>()
    private const val STABLE_THRESHOLD = 2
    private const val STABLE_GROWTH_TOLERANCE = 2

    private val syncGate = Mutex()
    private val syncRequestLock = Any()
    @Volatile
    private var syncInProgress = false
    @Volatile
    private var pendingForceFull = false

    // Throttle: keep active-dashboard sync responsive enough for reconnect/backfill,
    // but still avoid hammering native history on every 3-second UI tick.
    @Volatile
    private var lastSyncTimeMs = 0L
    private const val MIN_FULL_SYNC_INTERVAL_MS = 5_000L
    private const val MIN_INCR_SYNC_INTERVAL_MS = 3_000L

    // Incremental overlap: keep a generous window so reconnect/vendor repair work
    // does not leave permanent holes, while still avoiding all-history rescans.
    private const val INCREMENTAL_OVERLAP_MS = 6 * 60 * 60 * 1000L
    private const val RECENT_SYNC_BUCKET_MS = 60_000L
    private const val RECENT_SYNC_DEFAULT_LOOKBACK_MS = 5 * 60 * 1000L
    private const val RECENT_SYNC_MAX_LOOKBACK_MS = 30 * 60 * 1000L

    /**
     * Sync data from native layer to Room for ALL active sensors.
     *
     * Adaptive sync strategy per sensor:
     * - First call per session: Full sync (all data from time 0) for every sensor
     * - Subsequent calls: Keep doing full syncs per sensor until BLE backfill stabilizes
     *   (same reading count for [STABLE_THRESHOLD] consecutive full syncs)
     * - After backfill stabilizes: Switch to incremental sync (only last 5 min overlap)
     *
     * The DAO uses IGNORE on the (timestamp, sensorSerial) unique index so duplicates
     * from full syncs are handled efficiently.
     */
    @JvmOverloads
    fun syncFromNative(forceFull: Boolean = false) {
        BatteryTrace.bump(
            key = "history.sync.all.request",
            logEvery = 20L,
            detail = if (forceFull) "forceFull=true" else null
        )
        queueSync(forceFull, bypassThrottle = false)
    }

    @JvmStatic
    @JvmOverloads
    fun syncSensorFromNative(serial: String, forceFull: Boolean = false) {
        if (serial.isBlank()) return

        val now = System.currentTimeMillis()
        val (_, stableCount) = sensorStability[serial] ?: Pair(-1, 0)
        val sensorStable = stableCount >= STABLE_THRESHOLD
        val minInterval = if (sensorStable && initialSyncDone) MIN_INCR_SYNC_INTERVAL_MS else MIN_FULL_SYNC_INTERVAL_MS
        val lastSensorSync = sensorLastSyncTimeMs[serial] ?: 0L

        if (!forceFull && (now - lastSensorSync) < minInterval) {
            return
        }
        if (!sensorSyncInProgress.add(serial)) {
            return
        }

        BatteryTrace.bump(
            key = "history.sync.sensor.request",
            logEvery = 20L,
            detail = "serial=$serial forceFull=$forceFull"
        )

        scope.launch {
            try {
                syncGate.withLock {
                    doSyncSensor(serial, forceFull)
                    sensorLastSyncTimeMs[serial] = System.currentTimeMillis()
                    if (!initialSyncDone) {
                        initialSyncDone = true
                    }
                }
            } finally {
                sensorSyncInProgress.remove(serial)
            }
        }
    }

    @JvmStatic
    fun syncRecentSensorFromNative(serial: String, anchorTimeMs: Long) {
        if (serial.isBlank() || anchorTimeMs <= 0L) return

        val currentBucket = (anchorTimeMs / RECENT_SYNC_BUCKET_MS) * RECENT_SYNC_BUCKET_MS
        val previousBucket = sensorLastRecentSyncBucketMs[serial]
        if (previousBucket != null && previousBucket >= currentBucket) {
            return
        }
        if (!sensorRecentSyncInProgress.add(serial)) {
            return
        }

        BatteryTrace.bump(
            key = "history.sync.sensor.live.request",
            logEvery = 20L,
            detail = "serial=$serial bucket=$currentBucket"
        )

        scope.launch {
            try {
                syncGate.withLock {
                    val startMs = resolveRecentSyncStartMs(currentBucket, previousBucket)
                    val readings = doSyncSensorWindow(serial, startMs / 1000L)
                    if (readings > 0) {
                        sensorLastRecentSyncBucketMs[serial] = currentBucket
                    }
                }
            } finally {
                sensorRecentSyncInProgress.remove(serial)
            }
        }
    }

    private fun resolveRecentSyncStartMs(currentBucket: Long, previousBucket: Long?): Long {
        val defaultStart = (currentBucket - RECENT_SYNC_DEFAULT_LOOKBACK_MS).coerceAtLeast(0L)
        if (previousBucket == null || previousBucket <= 0L) {
            return defaultStart
        }
        val earliestNeeded = (previousBucket - RECENT_SYNC_BUCKET_MS).coerceAtLeast(0L)
        val boundedEarliest = earliestNeeded.coerceAtLeast((currentBucket - RECENT_SYNC_MAX_LOOKBACK_MS).coerceAtLeast(0L))
        return minOf(defaultStart, boundedEarliest)
    }

    private fun queueSync(forceFull: Boolean, bypassThrottle: Boolean) {
        val now = System.currentTimeMillis()

        synchronized(syncRequestLock) {
            if (syncInProgress) {
                if (forceFull) {
                    pendingForceFull = true
                }
                return
            }

            // Determine if ALL sensors have stabilized for throttle interval selection
            val allStable = sensorStability.values.all { (_, stable) -> stable >= STABLE_THRESHOLD }
            val minInterval = if (allStable && initialSyncDone) MIN_INCR_SYNC_INTERVAL_MS else MIN_FULL_SYNC_INTERVAL_MS

            // Throttle: skip if called too frequently (unless it's the first sync, forced,
            // or a queued rerun that arrived while the previous sync was still running).
            if (!bypassThrottle && !forceFull && initialSyncDone && (now - lastSyncTimeMs) < minInterval) {
                return
            }

            syncInProgress = true
        }

        scope.launch {
            try {
                doSyncAllSensors(forceFull)
            } finally {
                var rerunForceFull = false
                synchronized(syncRequestLock) {
                    syncInProgress = false
                    rerunForceFull = pendingForceFull
                    pendingForceFull = false
                }
                if (rerunForceFull) {
                    queueSync(rerunForceFull, bypassThrottle = true)
                }
            }
        }
    }

    /**
     * Sync all active sensors. Iterates [Natives.activeSensors] and syncs each one
     * via [Natives.getGlucoseHistoryForSensor].
     */
    private suspend fun doSyncAllSensors(forceFull: Boolean) {
        syncGate.withLock {
            try {
                BatteryTrace.bump(
                    key = "history.sync.all.run",
                    logEvery = 20L,
                    detail = if (forceFull) "forceFull=true" else null
                )
                val sensorsToSync = linkedSetOfSensors(
                    Natives.activeSensors(),
                    Natives.lastsensorname()
                )
                if (sensorsToSync.isEmpty()) {
                    Log.w(TAG, "No active sensors and no main sensor — nothing to sync")
                    lastSyncTimeMs = 0L
                    return@withLock
                }

                for (serial in sensorsToSync) {
                    doSyncSensor(serial, forceFull)
                }

                lastSyncTimeMs = System.currentTimeMillis()
                if (!initialSyncDone) {
                    initialSyncDone = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing all sensors: ${e.message}")
            }
        }
    }

    private fun linkedSetOfSensors(activeSensors: Array<String?>?, mainSensor: String?): LinkedHashSet<String> {
        val result = LinkedHashSet<String>()
        activeSensors?.forEach { serial ->
            serial?.takeIf { it.isNotBlank() }?.let(result::add)
        }
        mainSensor?.takeIf { it.isNotBlank() }?.let(result::add)
        return result
    }

    /**
     * Sync a single sensor's native data into Room.
     */
    private suspend fun doSyncSensor(serial: String, forceFull: Boolean) {
        try {
            BatteryTrace.bump(
                key = "history.sync.sensor.run",
                logEvery = 20L,
                detail = "serial=$serial forceFull=$forceFull"
            )
            val (lastCount, stableCount) = sensorStability[serial] ?: Pair(-1, 0)
            val sensorStable = stableCount >= STABLE_THRESHOLD

            val startSec: Long
            val isFullSync: Boolean

            if (forceFull || !initialSyncDone || !sensorStable) {
                // Full sync: first sync, forced, or backfill still in progress for this sensor
                startSec = 0L
                isFullSync = true
            } else {
                // Backfill done for this sensor: incremental only
                val latestTs = historyRepository.getLatestTimestampForSensor(serial)
                val startMs = if (latestTs > INCREMENTAL_OVERLAP_MS) latestTs - INCREMENTAL_OVERLAP_MS else 0L
                startSec = startMs / 1000L
                isFullSync = false
            }

            val currentCount = doSyncSensorWindow(serial, startSec)
            if (currentCount > 0) {
                if (isFullSync) {
                    Log.i(TAG, "Full sync [$serial]: ${currentCount} readings (lastCount=$lastCount, stable=$stableCount)")
                } else if (currentCount > 10) {
                    Log.i(TAG, "Incremental sync [$serial]: ${currentCount} readings")
                }
            }

            // Track backfill progress per sensor
            if (isFullSync) {
                val growth = if (lastCount >= 0) currentCount - lastCount else Int.MAX_VALUE
                if (growth in 0..STABLE_GROWTH_TOLERANCE) {
                    val newStable = stableCount + 1
                    sensorStability[serial] = Pair(currentCount, newStable)
                    if (newStable >= STABLE_THRESHOLD) {
                        Log.i(TAG, "Backfill stabilized [$serial] after $newStable near-stable full syncs ($currentCount readings, growth=$growth)")
                    }
                } else {
                    // New data arrived — reset stability counter for this sensor
                    sensorStability[serial] = Pair(currentCount, 0)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing sensor $serial: ${e.message}")
        }
    }

    private suspend fun doSyncSensorWindow(serial: String, startSec: Long): Int {
        val rawHistory = loadNativeHistory(serial, startSec)
        if (rawHistory == null) {
            Log.d(TAG, "getGlucoseHistoryForSensor($serial, $startSec) returned null — skipping")
            return 0
        }

        val readings = mutableListOf<HistoryReading>()

        for (i in rawHistory.indices step 3) {
            if (i + 2 >= rawHistory.size) break

            val timeSec = rawHistory[i]
            val timeMs = timeSec * 1000L

            val valueAutoRaw = rawHistory[i + 1]
            val valueRawRaw = rawHistory[i + 2]

            val value = valueAutoRaw / 10f
            val rawValue = valueRawRaw / 10f

            if (value > 0 || rawValue > 0) {
                readings.add(
                    HistoryReading(
                        timestamp = timeMs,
                        sensorSerial = serial,
                        value = value,
                        rawValue = rawValue,
                        rate = null
                    )
                )
            }
        }

        if (readings.isNotEmpty()) {
            historyRepository.storeReadings(readings)
            UiRefreshBus.requestStatusRefresh()
        }
        return readings.size
    }

    private fun loadNativeHistory(serial: String, startSec: Long): LongArray? {
        val exact = try {
            Natives.getGlucoseHistoryForSensor(serial, startSec)
        } catch (_: Throwable) {
            null
        }
        if (exact != null) {
            return exact
        }
        if (serial.startsWith("X-") && serial.length > 2) {
            return try {
                Natives.getGlucoseHistoryForSensor(serial.substring(2), startSec)
            } catch (_: Throwable) {
                null
            }
        }
        return null
    }

    /**
     * Force a full resync of ALL active sensors.
     * Useful after vendor history download or sensor data changes.
     *
     * Multi-sensor: NO LONGER clears the entire Room DB. Instead, resets sync
     * state and does a full re-sync for every active sensor. Data from all sensors
     * coexists via the sensorSerial column. The DAO's IGNORE strategy handles
     * duplicates efficiently.
     */
    fun forceFullSync() {
        Log.i(TAG, "forceFullSync() called — resetting sync state for all sensors (NO clearAllTables)")
        lastSyncTimeMs = 0L
        initialSyncDone = false
        sensorStability.clear()
        sensorLastSyncTimeMs.clear()
        syncFromNative(forceFull = true)
    }

    /**
     * Force a full resync of a SPECIFIC sensor with DELETE-then-INSERT.
     *
     * After localReplay(), the native polls[] have been overwritten with
     * recalibrated values, but the Room DB still has the OLD values.
     * Since the DAO uses IGNORE on conflict (timestamp + sensorSerial),
     * a plain re-sync would silently skip the updated values.
     *
     * Fix: DELETE all Room rows for this sensor first, then re-insert from native.
     * This triggers Room's Flow observers so the chart redraws instantly.
     */
    fun forceFullSyncForSensor(serial: String) {
        Log.i(TAG, "forceFullSyncForSensor($serial) — deleting Room data then re-syncing")
        sensorStability.remove(serial)
        sensorLastSyncTimeMs.remove(serial)
        val legacyAlias = if (serial.startsWith("X-") && serial.length > 2) serial.substring(2) else null
        if (legacyAlias != null) {
            sensorStability.remove(legacyAlias)
            sensorLastSyncTimeMs.remove(legacyAlias)
        }
        lastSyncTimeMs = 0L  // Allow immediate sync
        scope.launch {
            syncGate.withLock {
                // Step 1: Delete existing Room data for this sensor
                try {
                    historyRepository.deleteForSensor(serial)
                    if (legacyAlias != null) {
                        historyRepository.deleteForSensor(legacyAlias)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting Room data for $serial before resync: ${e.message}")
                }
                // Step 2: Re-sync from native (all data for this sensor)
                doSyncSensor(serial, forceFull = true)
            }
        }
    }
}
