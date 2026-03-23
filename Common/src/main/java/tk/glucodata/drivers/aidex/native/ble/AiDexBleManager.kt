// JugglucoNG — AiDex Native Kotlin Driver
// AiDexBleManager.kt — Per-sensor BLE connection manager extending SuperGattCallback
//
// Replaces the vendor native lib (libblecomm-lib.so) for AiDex sensors.
// Uses AiDexKeyExchange for crypto, AiDexCommandBuilder for F002 commands,
// and AiDexParser for parsing responses.
//
// Integration: Drop-in replacement for the vendor-mode path in AiDexSensor.kt.
// SensorBluetooth still manages scanning and the gattcallbacks list.

package tk.glucodata.drivers.aidex.native.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import tk.glucodata.Applic
import tk.glucodata.BatteryTrace
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.SuperGattCallback
import tk.glucodata.UiRefreshBus
import tk.glucodata.drivers.aidex.AiDexDriver
import tk.glucodata.drivers.aidex.CalibrationRecord as SharedCalibrationRecord
import tk.glucodata.drivers.aidex.native.crypto.Crc16CcittFalse
import tk.glucodata.drivers.aidex.native.data.*
import tk.glucodata.drivers.aidex.native.protocol.*
import java.util.ArrayDeque
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Per-sensor BLE connection manager for AiDex sensors.
 *
 * Extends [SuperGattCallback] so it plugs into JugglucoNG's existing
 * [SensorBluetooth.gattcallbacks] list and multi-sensor management.
 *
 * Lifecycle:
 *   1. SensorBluetooth creates this and adds to gattcallbacks
 *   2. SensorBluetooth calls connectDevice() when device is found
 *   3. GATT connects → discover services → CCCD chain → key exchange → streaming
 *   4. F003 notifications deliver live glucose via handleGlucoseResult()
 *   5. History/calibration are fetched after streaming starts
 *   6. On disconnect, reconnect strategy manages retry timing
 *
 * @param serial Sensor serial number (bare, without "X-" prefix)
 * @param dataptr Native data pointer from Natives.getdataptr(serial)
 * @param sensorGen Sensor generation identifier
 */
@SuppressLint("MissingPermission")
class AiDexBleManager(
    serial: String,
    dataptr: Long,
    sensorGen: Int,
) : SuperGattCallback(serial, dataptr, sensorGen), SensorBleController, AiDexDriver {

    companion object {
        private const val TAG = "AiDexBleManager"

        // -- BLE UUIDs --
        val SERVICE_F000: UUID = UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb")
        val CHAR_F001: UUID = UUID.fromString("0000f001-0000-1000-8000-00805f9b34fb")
        val CHAR_F002: UUID = UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb")
        val CHAR_F003: UUID = UUID.fromString("0000f003-0000-1000-8000-00805f9b34fb")

        // Standard BLE: Device Information Service (0x180A)
        val SERVICE_DIS: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val CHAR_MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val CHAR_SOFTWARE_REV: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
        val CHAR_MANUFACTURER: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")

        // Standard BLE: CGM Session Start Time (0x2AAA) under CGM service (0x181F = SERVICE_F000)
        val CHAR_CGM_SESSION_START: UUID = UUID.fromString("00002aaa-0000-1000-8000-00805f9b34fb")
        val CHAR_CGM_SESSION_RUN: UUID = UUID.fromString("00002aab-0000-1000-8000-00805f9b34fb")

        // -- GATT Queue --
        private const val GATT_OP_MAX_RETRIES = 10
        private const val NO_RESPONSE_ADVANCE_MS = 35L
        private const val BONDING_PAUSE_TIMEOUT_MS = 15_000L
        private const val GATT_WRITE_RETRY_DELAY_MS = 200L

        // -- Timeouts --
        private const val MTU_DELAY_MS = 200L
        private const val DISCOVERY_RETRY_DELAY_MS = 1_500L
        private const val DISCOVERY_MAX_RETRIES = 2
        private const val HISTORY_PAGE_TIMEOUT_MS = 25_000L
        private const val HISTORY_REQUEST_DELAY_MS = 80L
        private const val KEY_EXCHANGE_TIMEOUT_MS = 35_000L
        private const val GATT_OP_TIMEOUT_MS = 15_000L    // Watchdog for stuck GATT operations
        private const val GATT_OP_WATCHDOG_RETRIES = 2  // Max retries on watchdog timeout before dropping op
        private const val EXPECTED_LIVE_INTERVAL_MS = 60_000L
        private const val EXPECTED_LIVE_GRACE_MS = 20_000L
        private const val NO_STREAM_WATCHDOG_MS = EXPECTED_LIVE_INTERVAL_MS + EXPECTED_LIVE_GRACE_MS
        private const val INITIAL_HISTORY_REQUEST_DELAY_MS = 65_000L
        private const val POST_KEY_CCCD_REFRESH_DELAY_MS = 250L
        private const val STREAMING_METADATA_REQUEST_DELAY_MS = 400L

        // -- History Storage --
        private const val MIN_VALID_GLUCOSE_MGDL = 20
        private const val MAX_VALID_GLUCOSE_MGDL = 500
        private const val MAX_OFFSET_DAYS = 30
        private const val WARMUP_DURATION_MS = 7L * 60_000L  // 7 minutes (matches Kotlin AiDex warmup gate)
        private const val FIRST_VALID_READING_WAIT_MAX_MS = 60L * 60_000L  // Total wait window for the first usable reading
        private const val POST_RESET_WAITING_STATUS = "Waiting for first valid reading"
        private const val BROADCAST_FALLBACK_LIVE_TIMEOUT_MS = 90_000L
        private const val LIVE_HISTORY_CONTINUITY_BUCKET_MS = 60_000L
        private const val LIVE_HISTORY_CONTINUITY_MAX_MISSING_BUCKETS = 10
        private const val BROADCAST_ASSIST_SCAN_DELAY_MS = 15_000L
        private const val BROADCAST_ASSIST_SCAN_WINDOW_MS = 12_000L
        private const val BROADCAST_ASSIST_SETUP_STALL_MS = 45_000L
        private const val HISTORY_SYNC_BATCH_SIZE = 2000  // progressive sync to Room every N entries

        // -- Broadcast Scan --
        private const val BROADCAST_SCAN_WINDOW_MS = 12_000L  // Healthy steady-state scan window
        private const val BROADCAST_RECOVERY_SCAN_WINDOW_MS = 30_000L  // Larger window while recovering broadcasts
        private const val BROADCAST_SCAN_INTERVAL_MS = 60_000L  // Time between scans
        private const val BROADCAST_MIN_STORE_INTERVAL_MS = 50_000L  // Min interval between stored broadcast readings
        private const val BROADCAST_DUPLICATE_SUPPRESS_MS = 70_000L
    }

    // -- Protocol Objects --
    private val keyExchange = AiDexKeyExchange(serial)
    private val commandBuilder = AiDexCommandBuilder(keyExchange)

    // -- Reconnect Strategy --
    val reconnect = AiDexReconnect()

    // -- Connection Phase Tracking --
    enum class Phase {
        IDLE,
        GATT_CONNECTING,
        DISCOVERING_SERVICES,
        CCCD_CHAIN,
        KEY_EXCHANGE,
        STREAMING,
    }

    @Volatile var phase: Phase = Phase.IDLE
        private set

    // -- Handler --
    private val handlerThread = HandlerThread("AiDex-$serial").also { it.start() }
    private val handler = Handler(handlerThread.looper)

    // -- GATT Queue --
    private sealed class GattOp {
        var retryCount: Int = 0
        data class Write(val charUuid: UUID, val data: ByteArray) : GattOp()
        data class Read(val charUuid: UUID, val serviceUuid: UUID = SERVICE_F000) : GattOp()
    }

    private val gattQueue = ArrayDeque<GattOp>()
    @Volatile private var gattOpActive = false
    @Volatile private var queuePausedForBonding = false
    private var currentGattOp: GattOp? = null  // Tracks active op for watchdog retry

    /** Watchdog fires when a GATT operation callback never arrives within GATT_OP_TIMEOUT_MS. */
    private val gattOpWatchdog = Runnable {
        if (!gattOpActive) return@Runnable
        val op = currentGattOp
        Log.e(TAG, "GATT operation watchdog FIRED — no callback received in ${GATT_OP_TIMEOUT_MS}ms for $op")
        gattOpActive = false
        currentGattOp = null
        if (op != null && op.retryCount < GATT_OP_WATCHDOG_RETRIES) {
            op.retryCount++
            Log.w(TAG, "GATT watchdog: retrying (attempt ${op.retryCount}/$GATT_OP_WATCHDOG_RETRIES)")
            gattQueue.addFirst(op)
        } else {
            Log.e(TAG, "GATT watchdog: retries exhausted or no op — dropping")
        }
        drainGattQueue()
    }

    // -- CCCD State --
    private var servicesReady = false
    private var cccdQueue = ArrayDeque<UUID>() // Characteristics to enable notifications on
    private var cccdWriteInProgress = false
    private var cccdChainComplete = false

    // -- Key Exchange State --
    private var challengeWritten = false
    private var bondDataRead = false
    private var keyExchangePendingBond = false
    private var bondStateAtConnection: Int = BluetoothDevice.BOND_NONE
    private var bondBecameBondedThisConnection = false

    private enum class PostCccdFollowUp {
        NONE,
        ENTER_STREAMING,
        RESUME_STREAMING,
    }

    @Volatile private var postCccdFollowUp = PostCccdFollowUp.NONE
    private var streamingStartedAtMs: Long = 0L
    private var noStreamRecoveryAttempted = false
    private var postBondLiveRefreshAttempted = false
    private var pendingInitialHistoryRequest = false
    private var pendingStreamingMetadataRead = false
    private var pendingStreamingMetadataReason: String? = null
    private var lastF002FrameTimeMs: Long = 0L

    /** Watchdog: force-disconnect if key exchange doesn't complete within timeout. */
    private val keyExchangeWatchdog = Runnable {
        if (phase == Phase.KEY_EXCHANGE) {
            Log.e(TAG, "Key exchange watchdog FIRED — timeout after ${KEY_EXCHANGE_TIMEOUT_MS}ms. Force-disconnecting.")
            constatstatusstr = "Key exchange timeout"
            try { mBluetoothGatt?.disconnect() } catch (_: Throwable) {}
        }
    }

    /** Watchdog: re-arm live notifications once, then reconnect if F003 never appears. */
    private val noStreamWatchdog = Runnable {
        val gatt = mBluetoothGatt ?: return@Runnable
        if (phase != Phase.STREAMING) return@Runnable
        if (streamingStartedAtMs <= 0L || lastF003FrameTimeMs >= streamingStartedAtMs) return@Runnable
        val now = System.currentTimeMillis()

        if (hasRecentBroadcastData(now)) {
            Log.i(TAG, "No direct F003 yet, but broadcast fallback is healthy — keeping session alive")
            scheduleNoStreamWatchdog(EXPECTED_LIVE_INTERVAL_MS + EXPECTED_LIVE_GRACE_MS)
            return@Runnable
        }

        if (historyDownloading) {
            Log.i(TAG, "No F003 yet, but history download is still running — extending no-stream watchdog")
            scheduleNoStreamWatchdog()
            return@Runnable
        }

        if (!noStreamRecoveryAttempted) {
            noStreamRecoveryAttempted = true
            Log.w(TAG, "No F003 within ${NO_STREAM_WATCHDOG_MS / 1000}s of streaming start — refreshing F003/F002 CCCDs once")
            refreshLiveCccds(PostCccdFollowUp.RESUME_STREAMING, "no-stream-watchdog")
            return@Runnable
        }

        val delay = reconnect.nextReconnectDelayMs()
        Log.w(TAG, "No F003 after live CCCD refresh — reconnecting in ${delay}ms")
        constatstatusstr = "Reconnecting"
        setPhase(Phase.IDLE)
        close()
        handler.postDelayed({ connectDevice(0) }, delay)
    }

    private val delayedInitialHistoryRequest = Runnable {
        pendingInitialHistoryRequest = false
        if (phase != Phase.STREAMING || mBluetoothGatt == null) return@Runnable
        Log.i(TAG, "Initial history request starting after live-stream settle")
        requestHistoryRange()
    }

    private val delayedStreamingMetadataRequest = Runnable {
        val reason = pendingStreamingMetadataReason ?: "scheduled"
        pendingStreamingMetadataReason = null
        requestStreamingMetadataIfNeeded(reason)
    }

    // -- SharedPreferences for per-sensor state persistence --
    private val prefs by lazy {
        Applic.app.getSharedPreferences("AiDexNativePrefs", Context.MODE_PRIVATE)
    }

    private fun prefKey(name: String): String = "${name}_${SerialNumber}"

    private fun readIntPref(name: String, default: Int): Int {
        val key = prefKey(name)
        return if (prefs.contains(key)) prefs.getInt(key, default) else default
    }

    private fun writeIntPref(name: String, value: Int) {
        prefs.edit().putInt(prefKey(name), value).apply()
    }

    private fun readBoolPref(name: String, default: Boolean): Boolean {
        val key = prefKey(name)
        return if (prefs.contains(key)) prefs.getBoolean(key, default) else default
    }

    private fun writeBoolPref(name: String, value: Boolean) {
        prefs.edit().putBoolean(prefKey(name), value).apply()
    }

    private fun readLongPref(name: String, default: Long): Long {
        val key = prefKey(name)
        return if (prefs.contains(key)) prefs.getLong(key, default) else default
    }

    private fun writeLongPref(name: String, value: Long) {
        prefs.edit().putLong(prefKey(name), value).apply()
    }

    // -- History State --
    @Volatile private var historyDownloading = false
    private var historyRawNextIndex = 0
    private var historyBriefNextIndex = 0
    private var historyNewestOffset = 0
    private var historyStoredCount = 0  // entries stored via aidexProcessData this download
    private var historyDownloadStartIndex = 0  // snapshot of starting index for progress display
    private var historyPhase: HistoryPhase = HistoryPhase.IDLE

    // -- Post-Reset Activation Flag --
    // Set true by resetSensor(). When the post-reset reconnect reads CGM Session
    // Start Time and finds all-zeros (sensor uninitialized), automatically sends
    // SET_NEW_SENSOR (0x20) to re-activate. Persisted to SharedPreferences so it
    // survives driver instance recreation.
    @Volatile private var needsPostResetActivation: Boolean = false
    @Volatile private var postResetWarmupExtensionActive: Boolean = false
    @Volatile private var firstValidReadingAnchorMs: Long = 0L
    @Volatile private var hasAuthoritativeSessionStart: Boolean = false
    @Volatile private var autoActivationAttemptedThisConnection: Boolean = false

    init {
        // Restore persisted history offsets so reconnects only download new data.
        // Matches the vendor driver's Edit 47 approach (SharedPreferences persistence).
        historyRawNextIndex = readIntPref("historyRawNextIndex", 0)
        historyBriefNextIndex = readIntPref("historyBriefNextIndex", 0)
        if (historyRawNextIndex > 0 || historyBriefNextIndex > 0) {
            Log.i(TAG, "Restored history offsets: raw=$historyRawNextIndex, brief=$historyBriefNextIndex")
        }
        // Restore post-reset activation flag — survives driver instance recreation
        // (e.g., finish/unfinish sensor cycle that creates a new AiDexBleManager)
        needsPostResetActivation = readBoolPref("needsPostResetActivation", false)
        if (needsPostResetActivation) {
            Log.i(TAG, "Restored needsPostResetActivation=true from prefs — will auto-activate on next connect")
        }
        postResetWarmupExtensionActive = readBoolPref("postResetWarmupExtensionActive", false)
        firstValidReadingAnchorMs = readLongPref("firstValidReadingAnchorMs", 0L)
    }

    private enum class HistoryPhase {
        IDLE,
        DOWNLOADING_CALIBRATED,  // 0x23 (calibrated glucose)
        DOWNLOADING_RAW,         // 0x24 (ADC/raw data)
    }

    /** Watchdog fires when a history page response never arrives. */
    private val historyPageWatchdog = Runnable {
        if (historyDownloading) {
            Log.e(TAG, "History page watchdog FIRED — no response in ${HISTORY_PAGE_TIMEOUT_MS}ms (phase=$historyPhase)")
            historyDownloading = false
            historyPhase = HistoryPhase.IDLE
            // Persist current offsets so next reconnect resumes where we stopped
            writeIntPref("historyRawNextIndex", historyRawNextIndex)
            writeIntPref("historyBriefNextIndex", historyBriefNextIndex)
            Log.i(TAG, "History download aborted. Will resume on next connection from raw=$historyRawNextIndex, brief=$historyBriefNextIndex")
        }
    }

    // -- History Merge Cache --
    // 0x23 calibrated glucose values, keyed by offset minute.
    // Populated during 0x23 download, consumed during 0x24 download.
    // The vendor driver does the same: caches raw ADC by offset, then
    // merges with calibrated glucose when storing.
    // Our wire format is swapped: 0x23 = calibrated, 0x24 = raw ADC.
    private val calibratedGlucoseCache = HashMap<Int, Int>()
    // Fallback glucose for 0x24 entries without an exact 0x23 offset match.
    // Carried across pages so edge-of-page entries still get a valid glucose.
    private var lastCalibratedGlucoseFallback: Int? = null

    // -- F003 Live Data --
    private var lastGlucoseTimeMs: Long = 0L
    private var lastF003FrameTimeMs: Long = 0L
    private var lastOffsetMinutes: Int = 0
    private var lastLiveReadingObservedTimeMs: Long = 0L
    private var lastLiveContinuitySyncBucket: Long = -1L

    // -- Calibration State --
    /** End index of sensor's calibration range (from GET_CALIBRATION_RANGE). */
    private var calibrationRangeEndIndex: Int = 0
    /** Whether a calibration download is in progress. */
    private var calibrationDownloading: Boolean = false

    // -- Device Info (0x21) --
    // 0x21 always fails with status=0x01 on tested sensors.
    // All info comes from DIS (0x180A) + CGM Session Start Time (0x2AAA) instead.
    // Kept as single-shot attempt (no retries) for future sensors that may support it.
    @Volatile private var deviceInfoComplete = false

    // -- Reconnection Prevention Flags --
    // Matches vendor driver's layered defense against unwanted reconnection.
    // _isPaused blocks external reconnection triggers (LossOfSensorAlarm, reconnectall).
    // isUnpaired is a persistent flag for UI status display.
    @Volatile private var _isPaused: Boolean = false
    @Volatile private var isUnpaired: Boolean = false

    // -- Live Offset Cutoff (History Dedup) --
    // Tracks the highest offset stored by live F003 readings this session.
    // History entries at or above this offset are skipped because the live
    // pipeline already stored them. Matches vendor driver's
    // vendorHistoryAutoUpdateCutoff mechanism.
    @Volatile private var liveOffsetCutoff: Int = 0

    // -- History Catch-up Broadcast --
    // Tracks the newest valid entry stored during history download for the
    // catch-up broadcast in onHistoryDownloadComplete().
    private var lastHistoryNewestGlucose: Float = 0f
    private var lastHistoryNewestOffset: Int = 0

    // -- Reset Reconnect Flag --
    // Set true BEFORE sending reset command. When disconnect arrives with this
    // flag set, the driver removes the stale BLE bond, clears key exchange,
    // waits for the sensor to reboot, and auto-reconnects.
    @Volatile private var pendingResetReconnect: Boolean = false

    // -- Unpair Disconnect Flag --
    // Set true by unpairSensor(). The DELETE_BOND command is sent first; when the
    // response arrives (or disconnect occurs), this flag triggers bond removal +
    // soft disconnect instead of normal reconnect.
    @Volatile private var pendingUnpairDisconnect: Boolean = false

    // -- AiDexDriver State --
    @Volatile private var _batteryMillivolts: Int = 0
    @Volatile private var _sensorExpired: Boolean = false
    @Volatile private var _wearDays: Int = 15  // default 15-day sensor (AIDEX_SENSOR_MAX_DAYS)
    @Volatile private var _firmwareVersion: String = ""
    @Volatile private var _hardwareVersion: String = ""
    @Volatile private var _modelName: String = ""
    @Volatile private var _calibrationRecords: List<SharedCalibrationRecord> = emptyList()
    @Volatile private var _viewModeInternal: Int = 0
    @Volatile private var _resetCompensationEnabled: Boolean = false

    // -- Broadcast Scan State --
    @Volatile private var broadcastScanActive: Boolean = false
    @Volatile private var broadcastScanContinuousMode: Boolean = false
    private var broadcastScanCallback: ScanCallback? = null
    private var broadcastScanner: android.bluetooth.le.BluetoothLeScanner? = null
    @Volatile private var lastBroadcastGlucose: Float = 0f
    @Volatile private var lastBroadcastTime: Long = 0L
    @Volatile private var lastBroadcastStoredTime: Long = 0L
    @Volatile private var lastBroadcastOffsetSeen: Long = -1L
    @Volatile private var lastBroadcastTrendSeen: Int = Int.MIN_VALUE
    @Volatile private var lastBroadcastGlucoseSeen: Int = Int.MIN_VALUE
    @Volatile private var lastBroadcastOffsetSeenAtMs: Long = 0L
    @Volatile private var broadcastScanMisses: Int = 0

    // -- Transient Status --
    /** Temporary status message (e.g., calibration result) that auto-clears after 5 seconds. */
    @Volatile private var transientStatusMessage: String? = null
    private val broadcastAssistRunnable: Runnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (stop || reconnect.isBroadcastOnlyMode || hasRecentLiveData(now)) return
            AiDexRuntimePolicy.initialAssistDelayMs(
                nowMs = now,
                phaseStreaming = phase == Phase.STREAMING,
                pendingInitialHistoryRequest = pendingInitialHistoryRequest,
                historyDownloading = historyDownloading,
                streamingStartedAtMs = streamingStartedAtMs,
                initialHistoryRequestDelayMs = INITIAL_HISTORY_REQUEST_DELAY_MS,
            )?.let { remainingMs ->
                Log.d(
                    TAG,
                    "Waiting for initial history/live handoff — delaying assist scan for ${remainingMs / 1000}s"
                )
                handler.postDelayed(this, remainingMs)
                return
            }
            val anchor = effectiveWarmupAnchorMs()
            if (anchor > 0L && now >= anchor) {
                val ageMs = now - anchor
                val hasValidReadingSinceAnchor = lastGlucoseTimeMs >= anchor && lastGlucoseTimeMs > 0L
                if (!hasValidReadingSinceAnchor && ageMs < FIRST_VALID_READING_WAIT_MAX_MS) {
                    Log.i(
                        TAG,
                        "Waiting for first valid reading (${firstValidReadingWaitStatus(now) ?: "warming"}) — " +
                            "starting assist scan for broadcast fallback"
                    )
                }
            }
            if (phase == Phase.DISCOVERING_SERVICES || phase == Phase.CCCD_CHAIN || phase == Phase.KEY_EXCHANGE) {
                val setupAge = if (connectTime > 0L) now - connectTime else 0L
                if (setupAge in 1 until BROADCAST_ASSIST_SETUP_STALL_MS) {
                    Log.d(TAG, "Waiting for first valid reading (${firstValidReadingWaitStatus(now) ?: "setup"}) — delaying assist scan until setup completes")
                    handler.postDelayed(this, BROADCAST_ASSIST_SCAN_DELAY_MS)
                    return
                }
            }
            Log.i(TAG, "Waiting for first valid reading (${firstValidReadingWaitStatus(now) ?: "no-anchor"}) — starting assist scan")
            startBroadcastScan("assist-no-data", continuous = false)
        }
    }
    private val transientStatusClearRunnable = Runnable {
        transientStatusMessage = null
        AiDexDriver.deviceListDirty = true
    }

    private fun showTransientStatus(message: String, durationMs: Long = 5000L) {
        transientStatusMessage = message
        AiDexDriver.deviceListDirty = true
        handler.removeCallbacks(transientStatusClearRunnable)
        handler.postDelayed(transientStatusClearRunnable, durationMs)
    }

    private fun armFirstValidReadingWait(anchorMs: Long, reason: String) {
        val normalizedAnchor = anchorMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        firstValidReadingAnchorMs = normalizedAnchor
        writeLongPref("firstValidReadingAnchorMs", normalizedAnchor)
        Log.i(TAG, "First-valid-reading wait armed at $normalizedAnchor ($reason)")
        UiRefreshBus.requestStatusRefresh()
        handler.removeCallbacks(broadcastAssistRunnable)
        handler.postDelayed(broadcastAssistRunnable, BROADCAST_ASSIST_SCAN_DELAY_MS)
    }

    private fun clearFirstValidReadingWait(reason: String) {
        if (firstValidReadingAnchorMs == 0L && !postResetWarmupExtensionActive) return
        firstValidReadingAnchorMs = 0L
        writeLongPref("firstValidReadingAnchorMs", 0L)
        if (postResetWarmupExtensionActive) {
            postResetWarmupExtensionActive = false
            writeBoolPref("postResetWarmupExtensionActive", false)
        }
        if (constatstatusstr == POST_RESET_WAITING_STATUS) {
            constatstatusstr = ""
        }
        Log.i(TAG, "First-valid-reading wait cleared ($reason)")
        UiRefreshBus.requestStatusRefresh()
    }

    private fun noteValidReadingAvailable(timestampMs: Long, reason: String) {
        if (timestampMs > 0L && timestampMs > lastGlucoseTimeMs) {
            lastGlucoseTimeMs = timestampMs
        }
        handler.removeCallbacks(broadcastAssistRunnable)
        clearFirstValidReadingWait(reason)
        if (phase == Phase.STREAMING && streamingStartedAtMs > 0L && lastF003FrameTimeMs < streamingStartedAtMs) {
            scheduleNoStreamWatchdog()
        }
    }

    private fun maybeRequestHistoryContinuitySyncAfterLive(now: Long, source: String) {
        val previousReadingMs = lastLiveReadingObservedTimeMs
        lastLiveReadingObservedTimeMs = now
        if (previousReadingMs <= 0L || historyDownloading) {
            return
        }
        val continuityDecision = tk.glucodata.LiveContinuityPolicy.decideContinuitySync(
                previousReadingMs,
                now,
                LIVE_HISTORY_CONTINUITY_BUCKET_MS,
                LIVE_HISTORY_CONTINUITY_MAX_MISSING_BUCKETS,
            )
        if (!continuityDecision.shouldRequestContinuitySync) {
            return
        }

        if (lastLiveContinuitySyncBucket >= continuityDecision.currentBucket) {
            return
        }
        lastLiveContinuitySyncBucket = continuityDecision.currentBucket
        Log.i(
            TAG,
            "Detected $source continuity gap: missing ${continuityDecision.missingBuckets} minute bucket(s) between " +
                "${continuityDecision.previousBucket} and ${continuityDecision.currentBucket} — requesting incremental history continuity sync"
        )
        tk.glucodata.HistorySyncAccess.syncSensorFromNative(SerialNumber)
    }

    private fun effectiveWarmupAnchorMs(): Long {
        if (hasAuthoritativeSessionStart && sensorstartmsec > 0L) return sensorstartmsec
        if (firstValidReadingAnchorMs > 0L) return firstValidReadingAnchorMs
        return sensorstartmsec.takeIf { it > 0L } ?: 0L
    }

    private fun hasRecentBroadcastData(now: Long = System.currentTimeMillis()): Boolean {
        return lastBroadcastTime > 0L && (now - lastBroadcastTime) < BROADCAST_FALLBACK_LIVE_TIMEOUT_MS
    }

    private fun shouldContinueAssistScanning(now: Long = System.currentTimeMillis()): Boolean {
        return AiDexRuntimePolicy.shouldContinueAssistScanning(
            stop = stop,
            broadcastOnlyMode = reconnect.isBroadcastOnlyMode,
            phaseStreaming = phase == Phase.STREAMING,
            hasRecentLiveData = hasRecentLiveData(now),
            pendingInitialHistoryRequest = pendingInitialHistoryRequest,
            historyDownloading = historyDownloading,
            anchorMs = effectiveWarmupAnchorMs(),
            nowMs = now,
            lastGlucoseTimeMs = lastGlucoseTimeMs,
            firstValidReadingWaitMaxMs = FIRST_VALID_READING_WAIT_MAX_MS,
        )
    }

    private fun firstValidReadingWaitStatus(now: Long = System.currentTimeMillis()): String? {
        return AiDexRuntimePolicy.firstValidReadingWaitStatus(
            anchorMs = effectiveWarmupAnchorMs(),
            nowMs = now,
            warmupDurationMs = WARMUP_DURATION_MS,
            firstValidReadingWaitMaxMs = FIRST_VALID_READING_WAIT_MAX_MS,
        )
    }

    // -- Listeners --
    /** Called when a live glucose reading is parsed from F003. */
    var onGlucoseReading: ((GlucoseReading) -> Unit)? = null

    /** Called when calibrated history entries are parsed from 0x23. */
    var onCalibratedHistory: ((List<CalibratedHistoryEntry>) -> Unit)? = null

    /** Called when ADC history entries are parsed from 0x24. */
    var onAdcHistory: ((List<AdcHistoryEntry>) -> Unit)? = null

    /** Called when calibration records are parsed from 0x27. */
    var onCalibrationRecords: ((List<CalibrationRecord>) -> Unit)? = null

    /** Called when calibration result is received from SET_CALIBRATION (0x25).
     *  Parameters: (success: Boolean, message: String) */
    var onCalibrationResult: ((Boolean, String) -> Unit)? = null

    /** Called when sensor info is received (activation date, etc.) */
    var onSensorInfo: ((SensorInfo) -> Unit)? = null

    /** Called on phase changes for UI status updates. */
    var onPhaseChange: ((Phase) -> Unit)? = null

    // =========================================================================
    // SuperGattCallback Overrides
    // =========================================================================

    override fun matchDeviceName(deviceName: String?, address: String?): Boolean {
        if (deviceName == null) return false
        val bareSerial = tk.glucodata.drivers.aidex.native.crypto.SerialCrypto.stripPrefix(SerialNumber)
        return deviceName.contains(bareSerial) || deviceName.contains(SerialNumber)
    }

    override fun getService(): UUID = SERVICE_F000

    /**
     * Guard against double-connect and unwanted reconnection.
     *
     * Multiple paths call connectDevice():
     *   1. SensorBluetooth init (initializeBluetooth, possiblybluetooth→connectDevices)
     *   2. LossOfSensorAlarm → reconnectall() → reconnect() → connectDevice(0)
     *   3. Bluetooth STATE_ON → connectToAllActiveDevices(500)
     *   4. othersworking() → shouldreconnect() → reconnect()
     *
     * The base SuperGattCallback.reconnect() does NOT check the `stop` flag
     * before calling connectDevice(). We must guard here.
     *
     * Guards (matching vendor driver's connectDevice() at line 5595):
     *   - isPaused / isUnpaired: set by unpairSensor(), softDisconnect()
     *   - isBroadcastOnlyMode: set after auth failure exhaustion
     *   - phase != IDLE: already actively connected/connecting
     *   - mBluetoothGatt != null: GATT handle exists
     */
    override fun connectDevice(delayMillis: Long): Boolean {
        // Guard: paused, unpaired, or broadcast-only — refuse connection
        if (isPaused || isUnpaired) {
            Log.d(TAG, "connectDevice: skip — isPaused=$isPaused isUnpaired=$isUnpaired")
            return true  // Return true so SensorBluetooth doesn't start a scan
        }
        if (reconnect.isBroadcastOnlyMode) {
            Log.d(TAG, "connectDevice: skip — broadcast-only mode")
            return false  // Return false: we don't want GATT, but scanning is ok
        }
        if (phase != Phase.IDLE) {
            Log.d(TAG, "connectDevice: skip — already in phase $phase")
            return true
        }
        if (mBluetoothGatt != null) {
            Log.d(TAG, "connectDevice: skip — GATT already exists")
            return true
        }
        return super.connectDevice(delayMillis)
    }

    // =========================================================================
    // GATT Callbacks
    // =========================================================================

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            val now = System.currentTimeMillis()
            connectTime = now
            constatstatusstr = "Connected"
            _isPaused = false  // Clear paused flag — connection is active
            liveOffsetCutoff = 0  // Reset live offset cutoff for this connection session
            setPhase(Phase.DISCOVERING_SERVICES)

            // Reset per-connection state
            bondStateAtConnection = gatt.device?.bondState ?: BluetoothDevice.BOND_NONE
            bondBecameBondedThisConnection = false
            keyExchange.reset()
            challengeWritten = false
            bondDataRead = false
            servicesReady = false
            cccdChainComplete = false
            keyExchangePendingBond = false
            postCccdFollowUp = PostCccdFollowUp.NONE
            gattQueue.clear()
            gattOpActive = false
            queuePausedForBonding = false
            currentGattOp = null
            cccdQueue.clear()
            cccdWriteInProgress = false
            cccdRetryCount = 0
            deviceInfoComplete = false
            autoActivationAttemptedThisConnection = false
            hasAuthoritativeSessionStart = false
            streamingStartedAtMs = 0L
            lastF003FrameTimeMs = 0L
            lastLiveReadingObservedTimeMs = 0L
            lastLiveContinuitySyncBucket = -1L
            noStreamRecoveryAttempted = false
            postBondLiveRefreshAttempted = false
            pendingInitialHistoryRequest = false
            pendingStreamingMetadataRead = false
            pendingStreamingMetadataReason = null
            lastF002FrameTimeMs = 0L
            handler.removeCallbacks(noStreamWatchdog)
            handler.removeCallbacks(delayedInitialHistoryRequest)
            handler.removeCallbacks(delayedStreamingMetadataRequest)
            lastBroadcastOffsetSeen = -1L
            lastBroadcastTrendSeen = Int.MIN_VALUE
            lastBroadcastGlucoseSeen = Int.MIN_VALUE
            lastBroadcastOffsetSeenAtMs = 0L

            Log.i(TAG, "Connected to ${gatt.device?.address}. Requesting MTU 512...")
            gatt.requestMtu(512)
            handler.removeCallbacks(broadcastAssistRunnable)
            handler.postDelayed(broadcastAssistRunnable, BROADCAST_ASSIST_SCAN_DELAY_MS)

            // Schedule service discovery after MTU exchange
            handler.postDelayed({
                if (mBluetoothGatt != null && !servicesReady) {
                    Log.i(TAG, "Discovering services...")
                    mBluetoothGatt?.discoverServices()
                    scheduleDiscoveryRetries(gatt)
                }
            }, MTU_DELAY_MS)

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "Disconnected. status=$status")
            lastLiveReadingObservedTimeMs = 0L
            lastLiveContinuitySyncBucket = -1L
            constatstatusstr = "Disconnected"
            connectTime = 0L
            setPhase(Phase.IDLE)

            // Cancel ALL pending handler callbacks from the old connection.
            // Key exchange delays, CCCD retries, post-bond config, history timeouts, etc.
            // must not fire on a stale or new connection.
            handler.removeCallbacksAndMessages(null)

            // Clear GATT state
            gattQueue.clear()
            gattOpActive = false
            queuePausedForBonding = false
            currentGattOp = null
            servicesReady = false
            cccdChainComplete = false
            keyExchangePendingBond = false
            postCccdFollowUp = PostCccdFollowUp.NONE
            historyDownloading = false
            cccdRetryCount = 0
            discoveryRetryAttempt = 0
            streamingStartedAtMs = 0L
            lastF003FrameTimeMs = 0L
            noStreamRecoveryAttempted = false
            postBondLiveRefreshAttempted = false
            pendingInitialHistoryRequest = false
            pendingStreamingMetadataRead = false
            pendingStreamingMetadataReason = null
            lastF002FrameTimeMs = 0L
            lastBroadcastOffsetSeen = -1L
            lastBroadcastTrendSeen = Int.MIN_VALUE
            lastBroadcastGlucoseSeen = Int.MIN_VALUE
            lastBroadcastOffsetSeenAtMs = 0L

            // Clear per-connection caches and state (bugs #6, #7, #12, #13)
            calibratedGlucoseCache.clear()
            lastCalibratedGlucoseFallback = null
            lastOffsetMinutes = 0
            liveOffsetCutoff = 0
            lastHistoryNewestGlucose = 0f
            lastHistoryNewestOffset = 0
            deviceInfoComplete = false

            // Handle specific failure cases
            when (status) {
                5 -> { // GATT_INSUFFICIENT_AUTHENTICATION
                    val delay = reconnect.nextAuthFailureDelayMs()
                    if (delay != null) {
                        Log.i(TAG, "Auth failure — reconnecting in ${delay}ms (attempt ${reconnect.authFailureCount})")
                        close()
                        handler.postDelayed({ connectDevice(0) }, delay)
                    } else {
                        Log.w(TAG, "Auth failures exhausted — broadcast-only fallback")
                        close()
                        constatstatusstr = "Pairing failed — Broadcast Only"
                        reconnect.isBroadcastOnlyMode = true
                        stop = false
                        handler.post { startBroadcastScan("auth-failure-fallback") }
                        UiRefreshBus.requestStatusRefresh()
                    }
                    return
                }
                19 -> { // GATT_CONN_TERMINATE_PEER_USER — normal disconnect from sensor
                    Log.i(TAG, "Sensor terminated connection (normal)")
                }
                else -> {
                    if (status != 0) {
                        Log.w(TAG, "Unexpected disconnect status=$status")
                    }
                }
            }

            // Schedule reconnect — but NOT if paused (stop=true) or broadcast-only
            if (pendingUnpairDisconnect) {
                // Unpair command was sent but sensor disconnected before (or right after)
                // the response. Perform deferred cleanup now.
                pendingUnpairDisconnect = false
                Log.i(TAG, "Disconnect during unpair — performing deferred cleanup")
                val device = mBluetoothGatt?.device
                try {
                    if (device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                        val removeBond = device.javaClass.getMethod("removeBond")
                        removeBond.invoke(device)
                        Log.i(TAG, "unpairSensor: BLE bond removed (from disconnect handler)")
                    }
                } catch (_: Throwable) {}
                keyExchange.reset()
                close()
                constatstatusstr = "Unpaired — Broadcast Only"
                // Transition to broadcast-only mode so user keeps getting data
                reconnect.isBroadcastOnlyMode = true
                stop = false
                UiRefreshBus.requestStatusRefresh()
                handler.post { startBroadcastScan("post-unpair-disconnect") }
            } else if (pendingResetReconnect) {
                // Post-reset reconnect: sensor cleared its bond table + storage.
                // We must: remove stale BLE bond, clear key exchange, wait for
                // sensor reboot, then auto-reconnect fresh.
                pendingResetReconnect = false
                Log.i(TAG, "===== POST-RESET RECONNECT — removing bond, will reconnect in 5s =====")

                // Clear crypto state — will re-derive on next connection
                keyExchange.reset()

                // Capture device ref before closing GATT
                val device = mBluetoothGatt?.device

                // Remove stale Android BLE bond via reflection
                try {
                    if (device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                        val removeBond = device.javaClass.getMethod("removeBond")
                        removeBond.invoke(device)
                        Log.i(TAG, "Post-reset: BLE bond removed")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Post-reset: removeBond failed: ${t.message}")
                }

                close()

                // Reset reconnect counters so the fresh connect uses clean backoff
                reconnect.reset()

                // Delay 5 seconds for sensor to finish rebooting, then reconnect
                handler.postDelayed({
                    Log.i(TAG, "Post-reset: attempting auto-reconnect after 5s delay")
                    stop = false
                    connectDevice(0)
                }, 5_000L)
            } else if (stop) {
                Log.i(TAG, "Paused (stop=true) — not scheduling reconnect")
                close()
            } else if (!reconnect.isBroadcastOnlyMode) {
                val delay = reconnect.nextReconnectDelayMs()
                Log.i(TAG, "Scheduling reconnect in ${delay}ms (attempt ${reconnect.softAttempts})")
                close()
                handler.postDelayed({ connectDevice(0) }, delay)
            } else {
                Log.i(TAG, "Broadcast-only mode — starting broadcast scan instead of reconnect")
                close()
                UiRefreshBus.requestStatusRefresh()
                handler.postDelayed({ startBroadcastScan("post-disconnect") }, 2_000L)
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)

        if (gatt !== mBluetoothGatt) {
            Log.w(TAG, "onServicesDiscovered: stale callback, ignoring")
            return
        }
        if (servicesReady || phase != Phase.DISCOVERING_SERVICES) {
            Log.i(TAG, "onServicesDiscovered: duplicate callback in phase=$phase servicesReady=$servicesReady — ignoring")
            return
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "onServicesDiscovered: failed status=$status — triggering recovery")
            recoverFromServiceDiscoveryFailure()
            return
        }

        val service = gatt.getService(SERVICE_F000)
        if (service == null) {
            Log.e(TAG, "onServicesDiscovered: SERVICE_F000 (0x181F) not found! Triggering recovery")
            recoverFromServiceDiscoveryFailure()
            return
        }

        servicesReady = true
        Log.i(TAG, "Services discovered. Starting CCCD chain...")
        setPhase(Phase.CCCD_CHAIN)

        // Build CCCD queue: F003 first (data), then F002 (commands), then F001 (auth)
        cccdQueue.clear()
        cccdQueue.add(CHAR_F003)
        cccdQueue.add(CHAR_F002)
        cccdQueue.add(CHAR_F001)
        cccdWriteInProgress = false

        writeNextCccd(gatt)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (gatt !== mBluetoothGatt) {
            Log.w(TAG, "onDescriptorWrite: stale callback, ignoring")
            return
        }

        val charUuid = descriptor.characteristic.uuid

        if (status == 0x05 || status == 0x03) {
            // 0x05 = GATT_INSUFFICIENT_AUTHENTICATION — bonding in progress
            // 0x03 = GATT_WRITE_NOT_PERMITTED — may also indicate bonding needed
            Log.i(TAG, "onDescriptorWrite: CCCD $charUuid auth/perm fail (status=$status) — re-queuing for retry after bond")
            // Re-queue this characteristic for retry after bonding
            cccdQueue.addFirst(charUuid)
            cccdWriteInProgress = false
            // Wait for bonded() callback to resume
            return
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "onDescriptorWrite: CCCD $charUuid failed status=$status")
            cccdWriteInProgress = false
            // Try next anyway
        } else {
            Log.i(TAG, "onDescriptorWrite: CCCD $charUuid enabled successfully")
            cccdWriteInProgress = false
        }

        // Continue CCCD chain
        if (cccdQueue.isNotEmpty()) {
            writeNextCccd(gatt)
        } else {
            cccdChainComplete = true

            when (postCccdFollowUp) {
                PostCccdFollowUp.ENTER_STREAMING -> {
                    postCccdFollowUp = PostCccdFollowUp.NONE
                    Log.i(TAG, "Post-key-exchange CCCD re-registration complete. Entering streaming...")
                    enterStreamingPhase(requestHistory = true)
                    return
                }
                PostCccdFollowUp.RESUME_STREAMING -> {
                    postCccdFollowUp = PostCccdFollowUp.NONE
                    Log.i(TAG, "Live CCCD refresh complete. Waiting for F003 stream...")
                    resumeStreamingAfterLiveCccdRefresh()
                    return
                }
                PostCccdFollowUp.NONE -> Unit
            }

            // Initial CCCD chain complete — check bond state before starting key exchange
            val bondState = gatt.device.bondState
            Log.i(TAG, "All CCCDs enabled. Bond state: $bondState")

            when (bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    // Already bonded (reconnect case, or bonding finished during CCCD chain).
                    // Small delay to let encryption fully settle.
                    Log.i(TAG, "Already bonded. Starting key exchange after 500ms settle delay...")
                    handler.postDelayed({ startKeyExchange(gatt) }, 500L)
                }
                BluetoothDevice.BOND_BONDING -> {
                    // Bonding in progress — defer key exchange to bonded() callback.
                    // The sensor ignores writes on an unencrypted link.
                    Log.i(TAG, "Bonding in progress. Deferring key exchange until BOND_BONDED...")
                    keyExchangePendingBond = true
                }
                else -> {
                    // BOND_NONE — no bonding happened (unusual for AiDex).
                    // Try key exchange anyway; the CCCD write itself may trigger bonding later.
                    Log.w(TAG, "Bond state is BOND_NONE after CCCD chain — starting key exchange anyway")
                    startKeyExchange(gatt)
                }
            }
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val uuid = characteristic.uuid
        val data = characteristic.value ?: return
        if (data.isEmpty()) return

        Log.i(TAG, "onCharacteristicChanged: uuid=$uuid len=${data.size} hex=${AiDexParser.hexString(data.copyOfRange(0, minOf(data.size, 8)))}")

        when (uuid) {
            CHAR_F003 -> handleF003(data)
            CHAR_F001 -> handleF001Response(data, gatt)
            CHAR_F002 -> handleF002Response(data, gatt)
            else -> Log.w(TAG, "onCharacteristicChanged: unexpected uuid=$uuid")
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        Log.d(TAG, "onCharacteristicWrite: uuid=${characteristic.uuid} status=$status")
        if (gattOpActive) {
            handler.post {
                handler.removeCallbacks(gattOpWatchdog)
                currentGattOp = null
                gattOpActive = false
                drainGattQueue()
            }
        }
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        super.onCharacteristicRead(gatt, characteristic, status)
        val uuid = characteristic.uuid
        val data = characteristic.value

        if (gattOpActive) {
            handler.post {
                handler.removeCallbacks(gattOpWatchdog)
                currentGattOp = null
                gattOpActive = false
                drainGattQueue()
            }
        }

        if (status != BluetoothGatt.GATT_SUCCESS || data == null) {
            Log.w(TAG, "onCharacteristicRead: uuid=$uuid status=$status data=${data?.size}")
            return
        }

        when (uuid) {
            CHAR_F002 -> {
                if (!bondDataRead && phase == Phase.KEY_EXCHANGE && data.size == 17) {
                    handleBondData(data, gatt)
                } else {
                    handleF002Response(data, gatt)
                }
            }
            // Device Information Service reads
            CHAR_MODEL_NUMBER -> {
                _modelName = String(data, Charsets.UTF_8).trim('\u0000', ' ')
                Log.i(TAG, "DIS Model Number: $_modelName")
                applyWearProfileFromModel(_modelName)
            }
            CHAR_SOFTWARE_REV -> {
                _firmwareVersion = String(data, Charsets.UTF_8).trim('\u0000', ' ')
                Log.i(TAG, "DIS Software Revision: $_firmwareVersion")
            }
            CHAR_MANUFACTURER -> {
                val manufacturer = String(data, Charsets.UTF_8).trim('\u0000', ' ')
                Log.i(TAG, "DIS Manufacturer Name: $manufacturer")
            }
            // CGM Session Start Time (0x2AAA) — parse activation date + compute wear days
            CHAR_CGM_SESSION_START -> handleCGMSessionStartTime(data)
            // CGM Session Run Time (0x2AAB) — how long sensor has been running
            CHAR_CGM_SESSION_RUN -> {
                if (data.size >= 2) {
                    val minutes = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                    Log.i(TAG, "CGM Session Run Time: ${minutes}min = ${minutes / 60}h = ~${minutes / 1440} days")
                }
            }
            else -> Log.d(TAG, "onCharacteristicRead: unknown uuid=$uuid len=${data.size}")
        }
    }

    /**
     * Parse CGM Session Start Time (0x2AAA):
     * Bytes: year(u16LE), month, day, hour, minute, second, timezone(s8), DST(u8)
     * Sets sensorstartmsec and computes actual wear days from the sensor.
     */
    private fun handleCGMSessionStartTime(data: ByteArray) {
        if (data.size < 7) {
            Log.w(TAG, "CGM Session Start Time: too short (${data.size} bytes)")
            return
        }
        val year = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        val month = data[2].toInt() and 0xFF
        val day = data[3].toInt() and 0xFF
        val hour = data[4].toInt() and 0xFF
        val minute = data[5].toInt() and 0xFF
        val second = data[6].toInt() and 0xFF

        var tzOffsetSeconds = 0
        if (data.size >= 9) {
            val tz = data[7].toInt()  // signed, 15-minute increments
            val dst = data[8].toInt() and 0xFF
            tzOffsetSeconds = tz * 15 * 60
            if (dst == 4) tzOffsetSeconds += 3600  // DST=4 means +1h
            Log.i(TAG, "CGM Session Start: $year-$month-$day $hour:$minute:$second TZ=${tz * 15}min DST=$dst")
        } else {
            Log.i(TAG, "CGM Session Start: $year-$month-$day $hour:$minute:$second (no TZ)")
        }

        // Detect uninitialized sensor (all-zeros start time after reset)
        if (year == 0 && month == 0 && day == 0 && hour == 0 && minute == 0 && second == 0) {
            Log.i(TAG, "CGM Session Start: all zeros — sensor is uninitialized")
            hasAuthoritativeSessionStart = false
            armFirstValidReadingWait(System.currentTimeMillis(), "zero-session-start")
            if (!autoActivationAttemptedThisConnection) {
                autoActivationAttemptedThisConnection = true
                if (needsPostResetActivation) {
                    needsPostResetActivation = false
                    writeBoolPref("needsPostResetActivation", false)
                    Log.i(TAG, "Post-reset: auto-activating sensor with SET_NEW_SENSOR (0x20)")
                } else {
                    Log.i(TAG, "Fresh sensor: auto-activating sensor with SET_NEW_SENSOR (0x20)")
                }
                startNewSensor()
                // Re-read session start time after a delay so we pick up the new activation time
                handler.postDelayed({ readCGMSessionCharacteristics() }, 2_000L)
            } else {
                Log.w(TAG, "CGM Session Start still zero after activation attempt — waiting for first valid reading")
            }
            return
        }

        // Construct timestamp
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        // The time in the characteristic is in the sensor's local timezone
        val startMs = cal.timeInMillis - (tzOffsetSeconds * 1000L)

        if (startMs > 0 && startMs < System.currentTimeMillis() + 86400_000L) {
            hasAuthoritativeSessionStart = true
            val ageMs = System.currentTimeMillis() - startMs
            val ageDays = ageMs.toDouble() / 86400_000.0
            val remainDays = _wearDays - ageDays
            Log.i(TAG, "CGM Session Start parsed: startMs=$startMs, age=${String.format("%.1f", ageDays)} days, remaining=${String.format("%.1f", remainDays)} days")

            // Push wear days to C++ layer BEFORE start time — aidexSetStartTime
            // uses info->days to compute wearduration2, so days must be set first
            try {
                Natives.aidexSetWearDays(dataptr, _wearDays)
                Log.i(TAG, "aidexSetWearDays: days=$_wearDays (from CGM Session Start)")
            } catch (_: Throwable) {}

            // Update sensorstartmsec if not already set or if this is more accurate
            if (sensorstartmsec <= 0L || kotlin.math.abs(sensorstartmsec - startMs) > 60_000L) {
                Log.i(TAG, "Updating sensorstartmsec from CGM Session Start: $sensorstartmsec → $startMs")
                sensorstartmsec = startMs
                Natives.aidexSetStartTime(dataptr, startMs)
            }
            if (lastGlucoseTimeMs < startMs) {
                armFirstValidReadingWait(startMs, "authoritative-session-start")
            }

            // Update expiry
            val expiryMs = startMs + (_wearDays.toLong() * 24 * 3600_000L)
            _sensorExpired = System.currentTimeMillis() > expiryMs
        }
    }

    override fun bonded() {
        // Note: SensorBluetooth calls bonded() on EVERY bond state change
        // (BONDING, BONDED, NONE, ERROR), not just BOND_BONDED.
        val gatt = mBluetoothGatt ?: return
        val bondState = gatt.device.bondState
        Log.i(TAG, "bonded() callback: bondState=$bondState")

        if (bondState == BluetoothDevice.BOND_BONDED) {
            if (bondStateAtConnection != BluetoothDevice.BOND_BONDED) {
                bondBecameBondedThisConnection = true
            }
            reconnect.onBondSuccess()

            // Resume GATT queue if it was paused for bonding
            if (queuePausedForBonding) {
                queuePausedForBonding = false
                handler.post { drainGattQueue() }
            }

            // Resume CCCD chain if it was interrupted by auth failure
            if (cccdQueue.isNotEmpty()) {
                handler.post { writeNextCccd(gatt) }
            }

            // Start deferred key exchange if CCCDs completed while bonding
            if (keyExchangePendingBond && cccdChainComplete) {
                keyExchangePendingBond = false
                // 500ms delay to let encryption fully settle after bonding,
                // matching vendor driver's approach (AiDexSensor.kt line 6013)
                Log.i(TAG, "Bond complete. Starting deferred key exchange after 500ms settle delay...")
                handler.postDelayed({ startKeyExchange(gatt) }, 500L)
            } else if (
                phase == Phase.STREAMING &&
                keyExchange.isComplete &&
                !historyDownloading &&
                !gattOpActive &&
                cccdQueue.isEmpty() &&
                !postBondLiveRefreshAttempted &&
                streamingStartedAtMs > 0L &&
                lastF003FrameTimeMs < streamingStartedAtMs
            ) {
                postBondLiveRefreshAttempted = true
                handler.postDelayed({
                    if (
                        mBluetoothGatt === gatt &&
                        phase == Phase.STREAMING &&
                        !historyDownloading &&
                        !gattOpActive &&
                        cccdQueue.isEmpty() &&
                        streamingStartedAtMs > 0L &&
                        lastF003FrameTimeMs < streamingStartedAtMs
                    ) {
                        Log.i(TAG, "Bond completed after streaming start but no F003 arrived — refreshing live CCCDs once")
                        refreshLiveCccds(PostCccdFollowUp.RESUME_STREAMING, "post-bond-no-f003")
                    }
                }, 800L)
            }
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            Log.d(TAG, "bonded() callback: BOND_BONDING — waiting for BOND_BONDED")
        } else if (bondState == BluetoothDevice.BOND_NONE) {
            // User cancelled pairing dialog or bonding failed
            Log.w(TAG, "bonded() callback: BOND_NONE — pairing cancelled/failed")
            val delay = reconnect.nextAuthFailureDelayMs()
            if (delay == null) {
                // Exhausted auth retries — stop trying
                Log.w(TAG, "Pairing cancelled — max auth failures reached, stopping reconnect")
                softDisconnect()
                constatstatusstr = "Pairing cancelled — tap to retry"
            } else {
                Log.i(TAG, "Pairing cancelled — attempt ${reconnect.authFailureCount}/${reconnect.maxAuthFailures}, next retry in ${delay}ms")
                // Don't disconnect here — the GATT disconnect callback will handle reconnect with backoff
            }
        } else {
            Log.w(TAG, "bonded() callback: unexpected bond state $bondState")
        }
    }

    // =========================================================================
    // CCCD Chain
    // =========================================================================

    private var cccdRetryCount = 0
    private val CCCD_MAX_RETRIES = 5
    private val CCCD_RETRY_DELAY_MS = 300L

    private fun writeNextCccd(gatt: BluetoothGatt) {
        if (cccdWriteInProgress) return
        val charUuid = cccdQueue.peekFirst() ?: return

        val service = gatt.getService(SERVICE_F000)
        val characteristic = service?.getCharacteristic(charUuid)
        if (characteristic == null) {
            Log.w(TAG, "writeNextCccd: characteristic $charUuid not found, skipping")
            cccdQueue.pollFirst()
            writeNextCccd(gatt) // Try next
            return
        }

        cccdWriteInProgress = true
        // All AiDex characteristics (F001, F002, F003) use NOTIFY, not INDICATE.
        // F001 props=0x18 (WRITE + NOTIFY), F002 props=WRITE+NOTIFY, F003 props=0x10 (NOTIFY).
        // Writing indication (02 00) to a NOTIFY-only CCCD fails with status=3.
        val ok = enableNotification(gatt, characteristic)
        if (!ok) {
            cccdWriteInProgress = false
            cccdRetryCount++
            if (cccdRetryCount >= CCCD_MAX_RETRIES) {
                Log.e(TAG, "writeNextCccd: CCCD $charUuid failed after $cccdRetryCount retries — skipping")
                cccdRetryCount = 0
                cccdQueue.pollFirst()
                if (cccdQueue.isEmpty()) {
                    cccdChainComplete = true
                    when (postCccdFollowUp) {
                        PostCccdFollowUp.ENTER_STREAMING -> {
                            postCccdFollowUp = PostCccdFollowUp.NONE
                            Log.w(TAG, "Post-key-exchange CCCD re-registration had failures. Entering streaming anyway...")
                            enterStreamingPhase(requestHistory = true)
                        }
                        PostCccdFollowUp.RESUME_STREAMING -> {
                            postCccdFollowUp = PostCccdFollowUp.NONE
                            Log.w(TAG, "Live CCCD refresh had failures. Resuming streaming wait anyway...")
                            resumeStreamingAfterLiveCccdRefresh()
                        }
                        PostCccdFollowUp.NONE -> Unit
                    }
                } else {
                    writeNextCccd(gatt)
                }
            } else {
                Log.w(TAG, "writeNextCccd: CCCD $charUuid write failed — retry $cccdRetryCount/$CCCD_MAX_RETRIES in ${CCCD_RETRY_DELAY_MS}ms")
                handler.postDelayed({
                    if (mBluetoothGatt != null) writeNextCccd(gatt)
                }, CCCD_RETRY_DELAY_MS)
            }
        } else {
            // Success — remove from queue, reset retry count. onDescriptorWrite will advance chain.
            cccdQueue.pollFirst()
            cccdRetryCount = 0
        }
    }

    // =========================================================================
    // Key Exchange
    // =========================================================================

    private fun startKeyExchange(gatt: BluetoothGatt) {
        setPhase(Phase.KEY_EXCHANGE)

        // Start watchdog timer — force disconnect if key exchange doesn't complete
        handler.removeCallbacks(keyExchangeWatchdog)
        handler.postDelayed(keyExchangeWatchdog, KEY_EXCHANGE_TIMEOUT_MS)

        // Step 1: Write SN challenge to F001
        val challenge = keyExchange.getChallenge()
        Log.i(TAG, "Key exchange: writing challenge to F001 (${AiDexParser.hexString(challenge)})")

        val service = gatt.getService(SERVICE_F000)
        val f001 = service?.getCharacteristic(CHAR_F001)
        if (service == null || f001 == null) {
            Log.e(TAG, "startKeyExchange: SERVICE_F000 or F001 not found — cannot proceed. Triggering recovery.")
            handler.removeCallbacks(keyExchangeWatchdog)
            recoverFromServiceDiscoveryFailure()
            return
        }

        f001.value = challenge
        f001.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(f001)
        challengeWritten = true
    }

    /**
     * Handle F001 notification — contains the PAIR key.
     */
    private fun handleF001Response(data: ByteArray, gatt: BluetoothGatt) {
        if (data.size < 16) {
            Log.w(TAG, "F001 response too short (${data.size} bytes)")
            return
        }

        if (!challengeWritten) {
            Log.w(TAG, "F001 response received but challenge not written yet — ignoring")
            return
        }

        if (keyExchange.pairKey != null) {
            Log.d(TAG, "F001 response: PAIR key already set, ignoring duplicate")
            return
        }

        // Extract PAIR key (first 16 bytes of notification)
        val pairKeyData = data.copyOfRange(0, 16)
        keyExchange.onPairKeyReceived(pairKeyData)
        Log.i(TAG, "Key exchange: PAIR key received (${AiDexParser.hexString(pairKeyData)})")

        // Step 3: Read BOND data from F002
        readBondData(gatt)
    }

    /**
     * Read BOND data from F002 characteristic.
     */
    private fun readBondData(gatt: BluetoothGatt) {
        Log.i(TAG, "Key exchange: reading BOND data from F002")
        enqueueGattOp(GattOp.Read(CHAR_F002))
    }

    /**
     * Handle BOND data read from F002 (17 bytes).
     */
    private fun handleBondData(data: ByteArray, gatt: BluetoothGatt) {
        if (bondDataRead) return
        bondDataRead = true

        Log.i(TAG, "Key exchange: BOND data received (${data.size} bytes)")

        if (!keyExchange.decryptBond(data)) {
            Log.e(TAG, "Key exchange: BOND decryption/CRC failed!")
            // Retry entire key exchange on next connection
            close()
            reconnect.nextReconnectDelayMs()
            handler.postDelayed({ connectDevice(0) }, reconnect.adaptiveDelayMs())
            return
        }

        Log.i(TAG, "Key exchange: Session key established!")

        // Step 5: Send post-BOND config
        sendPostBondConfig(gatt)
    }

    /**
     * Send post-BOND config (plaintext 10 C1 F3, encrypted).
     */
    private fun sendPostBondConfig(gatt: BluetoothGatt) {
        val configData = keyExchange.getPostBondConfig()
        if (configData == null) {
            Log.e(TAG, "Key exchange: failed to encrypt post-BOND config")
            return
        }

        Log.i(TAG, "Key exchange: writing post-BOND config to F001")
        enqueueGattOp(GattOp.Write(CHAR_F001, configData))

        // Transition to streaming after config is sent
        handler.postDelayed({
            onKeyExchangeComplete()
        }, 500L)
    }

    /**
     * Called when key exchange is fully complete. Start receiving data.
     *
     * Re-registers F003 and F002 CCCDs before sending commands.
     * On first connection, SMP bonding is triggered by the F001 CCCD write,
     * but F003 and F002 CCCDs were written BEFORE bonding started. The sensor
     * invalidates pre-bond CCCDs when the security level changes, so notifications
     * never arrive. Re-writing CCCDs after key exchange (post-bond) fixes this.
     * On reconnections where the device is already bonded, this is a harmless no-op.
     */
    private fun onKeyExchangeComplete() {
        val shouldRefreshLiveCccds = AiDexStreamingPolicy.shouldRefreshLiveCccdsAfterKeyExchange(
            bondStateAtConnection = bondStateAtConnection,
            bondBecameBondedThisConnection = bondBecameBondedThisConnection,
        )
        if (!shouldRefreshLiveCccds) {
            Log.i(TAG, "Key exchange complete — already-bonded reconnect, entering streaming without CCCD re-registration")
            handler.removeCallbacks(keyExchangeWatchdog)
            enterStreamingPhase(requestHistory = true)
            return
        }
        Log.i(TAG, "Key exchange complete — settling briefly before CCCD re-registration")
        handler.postDelayed({
            if (mBluetoothGatt != null && phase == Phase.KEY_EXCHANGE) {
                refreshLiveCccds(PostCccdFollowUp.ENTER_STREAMING, "post-key-exchange")
            }
        }, POST_KEY_CCCD_REFRESH_DELAY_MS)
    }

    private fun refreshLiveCccds(followUp: PostCccdFollowUp, reason: String) {
        val gatt = mBluetoothGatt
        if (gatt == null) {
            Log.e(TAG, "refreshLiveCccds($reason): no active GATT!")
            return
        }

        val service = gatt.getService(SERVICE_F000)
        if (service == null) {
            Log.e(TAG, "refreshLiveCccds($reason): SERVICE_F000 not found!")
            return
        }

        // Re-register F003 (glucose data) and F002 (command responses) CCCDs.
        // F001 was written during/after bonding so it's already valid.
        // Use the CCCD chain mechanism for serialized writes.
        cccdQueue.clear()
        cccdQueue.add(CHAR_F003)
        cccdQueue.add(CHAR_F002)
        cccdWriteInProgress = false
        cccdChainComplete = false

        // After CCCDs are re-registered, either enter streaming (first connect)
        // or just resume waiting for the live stream (recovery path).
        postCccdFollowUp = followUp

        Log.i(TAG, "Re-registering F003 + F002 CCCDs ($reason)...")
        writeNextCccd(gatt)
    }

    /**
     * Enter streaming phase and send initial commands.
     * Called after post-key-exchange CCCD re-registration completes.
     */
    private fun enterStreamingPhase(requestHistory: Boolean) {
        handler.removeCallbacks(keyExchangeWatchdog)  // Cancel watchdog — key exchange succeeded
        setPhase(Phase.STREAMING)
        reconnect.onConnectionSuccess()
        constatstatusstr = "Connected"
        streamingStartedAtMs = System.currentTimeMillis()
        noStreamRecoveryAttempted = false
        scheduleNoStreamWatchdog()
        pendingInitialHistoryRequest = false
        pendingStreamingMetadataRead = true
        handler.removeCallbacks(delayedInitialHistoryRequest)
        Log.i(
            TAG,
            "Streaming phase entered." +
                if (requestHistory) {
                    " Waiting for first F003 before history (${INITIAL_HISTORY_REQUEST_DELAY_MS / 1000}s max)"
                } else {
                    " Waiting for first live data"
                } +
                "..."
        )

        if (requestHistory) {
            pendingInitialHistoryRequest = true
            handler.postDelayed(delayedInitialHistoryRequest, INITIAL_HISTORY_REQUEST_DELAY_MS)
        }
    }

    private fun resumeStreamingAfterLiveCccdRefresh() {
        if (phase != Phase.STREAMING) {
            setPhase(Phase.STREAMING)
        }
        scheduleNoStreamWatchdog()
    }

    private fun scheduleNoStreamWatchdog(delayMs: Long = NO_STREAM_WATCHDOG_MS) {
        handler.removeCallbacks(noStreamWatchdog)
        if (phase == Phase.STREAMING && streamingStartedAtMs > 0L && lastF003FrameTimeMs < streamingStartedAtMs) {
            val now = System.currentTimeMillis()
            val latestKnownReadingMs = lastGlucoseTimeMs
            val effectiveDelay = AiDexStreamingPolicy.resolveNoStreamWatchdogDelayMs(
                defaultDelayMs = delayMs,
                nowMs = now,
                latestKnownReadingMs = latestKnownReadingMs,
                expectedLiveIntervalMs = EXPECTED_LIVE_INTERVAL_MS,
                expectedLiveGraceMs = EXPECTED_LIVE_GRACE_MS,
            )
            if (latestKnownReadingMs > 0L && effectiveDelay > delayMs + 1_000L) {
                Log.i(
                    TAG,
                    "No-stream watchdog extended to ${effectiveDelay / 1000}s because latest known reading is " +
                        "${(now - latestKnownReadingMs).coerceAtLeast(0L) / 1000}s old"
                )
            }
            handler.postDelayed(noStreamWatchdog, effectiveDelay)
        }
    }

    private fun handleParsedLiveReading(
        now: Long,
        source: String,
        autoValue: Float,
        trustedTimeOffsetMinutes: Int?,
        rawValue: Float?,
        sensorGlucose: Float?,
        rawI1: Float?,
        rawI2: Float?,
    ) {
        trustedTimeOffsetMinutes?.let { trustedOffset ->
            lastOffsetMinutes = trustedOffset
            if (trustedOffset > liveOffsetCutoff) {
                liveOffsetCutoff = trustedOffset
            }
        }
        val shouldStartHistoryNow = AiDexRuntimePolicy.shouldStartHistoryImmediately(
            pendingInitialHistoryRequest = pendingInitialHistoryRequest,
            historyDownloading = historyDownloading,
        )
        noteValidReadingAvailable(now, "valid-$source")
        if (shouldStartHistoryNow) {
            pendingInitialHistoryRequest = false
            handler.removeCallbacks(delayedInitialHistoryRequest)
            Log.i(TAG, "First live reading arrived from $source — starting history now")
            requestHistoryRange()
        } else if (!historyDownloading) {
            scheduleStreamingMetadataRead("first-live-$source")
        }

        if (broadcastScanActive && !broadcastScanContinuousMode) {
            stopBroadcastScan("live-reading", found = true)
        }

        val bareSerial = tk.glucodata.drivers.aidex.native.crypto.SerialCrypto.stripPrefix(SerialNumber)
        val reading = GlucoseReading(
            timestamp = now,
            sensorSerial = bareSerial,
            autoValue = autoValue,
            rawValue = rawValue,
            sensorGlucose = sensorGlucose,
            rawI1 = rawI1,
            rawI2 = rawI2,
            timeOffsetMinutes = trustedTimeOffsetMinutes ?: 0,
        )
        onGlucoseReading?.invoke(reading)

        ensureSensorStartTime(now, trustedTimeOffsetMinutes)

        if (dataptr != 0L) {
            try {
                val res = Natives.aidexProcessData(
                    dataptr,
                    byteArrayOf(0),
                    now,
                    autoValue,
                    rawValue ?: 0f,
                    1.0f
                )
                handleGlucoseResult(res, now)
                if (constatstatusstr == "Connected") {
                    constatstatusstr = ""
                }
                maybeRequestHistoryContinuitySyncAfterLive(now, source)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "F003($source): Native library mismatch: $e")
                val mgdlInt = autoValue.toInt().coerceIn(0, 0xFFFF) * 10
                handleGlucoseResult(mgdlInt.toLong() and 0xFFFFFFFFL, now)
                maybeRequestHistoryContinuitySyncAfterLive(now, "$source-fallback")
            } catch (e: Throwable) {
                Log.e(TAG, "F003($source): aidexProcessData failed: $e")
                val mgdlInt = autoValue.toInt().coerceIn(0, 0xFFFF) * 10
                handleGlucoseResult(mgdlInt.toLong() and 0xFFFFFFFFL, now)
                maybeRequestHistoryContinuitySyncAfterLive(now, "$source-fallback")
            }
        } else {
            Log.w(TAG, "F003($source): dataptr is 0 — cannot store reading")
            val mgdlInt = autoValue.toInt().coerceIn(0, 0xFFFF) * 10
            handleGlucoseResult(mgdlInt.toLong() and 0xFFFFFFFFL, now)
            maybeRequestHistoryContinuitySyncAfterLive(now, "$source-no-dataptr")
        }
    }

    private fun resolveTrustedLiveOffsetMinutes(frame: GlucoseFrame, now: Long): Pair<Int?, String?> {
        val wireOffset = frame.timeOffsetMinutes
        val maxOffsetMinutes = MAX_OFFSET_DAYS * 24L * 60L

        fun trust(offset: Int, reason: String? = null): Pair<Int?, String?> = offset to reason
        fun untrusted(reason: String): Pair<Int?, String?> = null to reason

        if (wireOffset <= 0) {
            return untrusted("wire offset=$wireOffset is not positive")
        }
        if (wireOffset.toLong() > maxOffsetMinutes) {
            // 1.8.1 has been observed to produce correct glucose/raw with garbage timing bytes.
            // Accept the reading, but do not let that field rewrite start time or dedupe state.
            val derivedFromSession = deriveOffsetFromAuthoritativeSession(now)
            if (derivedFromSession != null) {
                return trust(
                    derivedFromSession,
                    "wire offset=$wireOffset invalid; derived live offset=$derivedFromSession from authoritative session"
                )
            }
            val derivedFromHistory = deriveOffsetFromKnownHistory(now)
            if (derivedFromHistory != null) {
                return trust(
                    derivedFromHistory,
                    "wire offset=$wireOffset invalid; derived live offset=$derivedFromHistory from history cadence"
                )
            }
            return untrusted("wire offset=$wireOffset exceeds ${MAX_OFFSET_DAYS}d limit")
        }

        if (historyNewestOffset > 0 && wireOffset > historyNewestOffset + 2) {
            val derivedFromSession = deriveOffsetFromAuthoritativeSession(now)
            if (derivedFromSession != null) {
                return trust(
                    derivedFromSession,
                    "wire offset=$wireOffset ahead of history newest=$historyNewestOffset; using session-derived offset=$derivedFromSession"
                )
            }
            val derivedFromHistory = deriveOffsetFromKnownHistory(now)
            if (derivedFromHistory != null) {
                return trust(
                    derivedFromHistory,
                    "wire offset=$wireOffset ahead of history newest=$historyNewestOffset; using history-derived offset=$derivedFromHistory"
                )
            }
            return untrusted("wire offset=$wireOffset ahead of known history newest=$historyNewestOffset")
        }

        val startMs = sensorstartmsec.takeIf { it > 0L } ?: 0L
        if (startMs > 0L) {
            val timestampMs = startMs + wireOffset.toLong() * 60_000L
            if (timestampMs > now + 5L * 60_000L) {
                val derivedFromSession = deriveOffsetFromAuthoritativeSession(now)
                if (derivedFromSession != null) {
                    return trust(
                        derivedFromSession,
                        "wire offset=$wireOffset points into the future; using session-derived offset=$derivedFromSession"
                    )
                }
                return untrusted("wire offset=$wireOffset points into the future")
            }
            val oldestAllowed = now - (MAX_OFFSET_DAYS * 24L * 60L * 60_000L)
            if (timestampMs < oldestAllowed) {
                return untrusted("wire offset=$wireOffset points older than ${MAX_OFFSET_DAYS}d")
            }
        }

        return trust(wireOffset)
    }

    private fun deriveOffsetFromAuthoritativeSession(now: Long): Int? {
        if (!hasAuthoritativeSessionStart || sensorstartmsec <= 0L || now <= sensorstartmsec) return null
        val derived = ((now - sensorstartmsec + 30_000L) / 60_000L).toInt()
        return derived.takeIf { it > 0 && it.toLong() <= (MAX_OFFSET_DAYS * 24L * 60L) }
    }

    private fun deriveOffsetFromKnownHistory(now: Long): Int? {
        val baseOffset = maxOf(lastOffsetMinutes, historyNewestOffset, lastHistoryNewestOffset)
        if (baseOffset <= 0) return null
        val latestKnownTimeMs = lastGlucoseTimeMs.takeIf { it > 0L } ?: return baseOffset
        val advanceMinutes = ((now - latestKnownTimeMs + 30_000L) / 60_000L).coerceAtLeast(0L).toInt()
        val derived = baseOffset + advanceMinutes
        return derived.takeIf { it > 0 && it.toLong() <= (MAX_OFFSET_DAYS * 24L * 60L) }
    }

    private fun requestStreamingMetadataIfNeeded(reason: String) {
        if (!pendingStreamingMetadataRead) return
        pendingStreamingMetadataRead = false
        pendingStreamingMetadataReason = null
        Log.i(TAG, "Requesting streaming metadata ($reason)")
        // Keep these reads out of the first live-arm window. The clean 1.8.1
        // trace reaches F003 without front-loading DIS/2AAA reads here, and
        // our driver only needs this metadata after live data or history is
        // already flowing.
        readDeviceInformationService()
        readCGMSessionCharacteristics()
    }

    private fun scheduleStreamingMetadataRead(reason: String) {
        if (!pendingStreamingMetadataRead) return
        pendingStreamingMetadataReason = reason
        handler.removeCallbacks(delayedStreamingMetadataRequest)
        handler.postDelayed(delayedStreamingMetadataRequest, STREAMING_METADATA_REQUEST_DELAY_MS)
    }

    /**
     * Read Device Information Service (0x180A) characteristics:
     * Model Number (0x2A24), Software Revision (0x2A28), Manufacturer Name (0x2A29).
     */
    private fun readDeviceInformationService() {
        enqueueGattOp(GattOp.Read(CHAR_MODEL_NUMBER, SERVICE_DIS))
        enqueueGattOp(GattOp.Read(CHAR_SOFTWARE_REV, SERVICE_DIS))
        enqueueGattOp(GattOp.Read(CHAR_MANUFACTURER, SERVICE_DIS))
    }

    /**
     * Read standard CGM characteristics under service 0x181F (same as SERVICE_F000):
     * CGM Session Start Time (0x2AAA) — sensor activation date + timezone.
     * CGM Session Run Time (0x2AAB) — how long the sensor has been running.
     */
    private fun readCGMSessionCharacteristics() {
        enqueueGattOp(GattOp.Read(CHAR_CGM_SESSION_START, SERVICE_F000))
        enqueueGattOp(GattOp.Read(CHAR_CGM_SESSION_RUN, SERVICE_F000))
    }

    // =========================================================================
    // F003 Data Handling
    // =========================================================================

    private fun handleF003(encryptedData: ByteArray) {
        val now = System.currentTimeMillis()
        lastF003FrameTimeMs = now
        handler.removeCallbacks(noStreamWatchdog)
        Log.i(TAG, "handleF003: len=${encryptedData.size}, raw=${AiDexParser.hexString(encryptedData.copyOfRange(0, minOf(encryptedData.size, 8)))}")

        // Status/keepalive frames (5 bytes) — decrypt and extract battery voltage
        val frameType = AiDexParser.classifyFrame(encryptedData)
        if (frameType == AiDexParser.FrameType.STATUS) {
            handleStatusFrame(encryptedData)
            return
        }

        if (frameType != AiDexParser.FrameType.DATA) {
            // 13-byte F003 frames appear after calibration commands (opcode 0x0A in logs).
            // Decrypt and log them — they may be calibration-related notifications.
            if (encryptedData.size == 13) {
                handleCalibrationNotificationFrame(encryptedData)
                return
            }
            Log.w(TAG, "F003: Unknown frame size ${encryptedData.size}")
            return
        }

        // Decrypt
        val decrypted = keyExchange.decrypt(encryptedData)
        if (decrypted == null) {
            Log.w(TAG, "F003: Cannot decrypt — session key not available")
            return
        }

        // Parse
        val frame = AiDexParser.parseDataFrame(decrypted)
        if (frame == null) {
            Log.w(TAG, "F003: Failed to parse decrypted data frame (${AiDexParser.hexString(decrypted.copyOfRange(0, minOf(decrypted.size, 16)))})")
            return
        }

        Log.i(
            TAG,
            "F003 parsed: offset=${frame.timeOffsetMinutes}min glucose=${frame.glucoseMgDl} mg/dL " +
                "packed=${frame.rawGlucosePacked} i1=${frame.i1} i2=${frame.i2} " +
                "opcode=0x${"%02X".format(frame.opcode)} valid=${frame.isValid}"
        )

        // Validate CRC-16 embedded in frame (bytes 15-16)
        val frameCrc = Crc16CcittFalse.checksum(decrypted.copyOfRange(0, 15))
        if (frameCrc != frame.crc16) {
            Log.w(TAG, "F003: CRC-16 mismatch (expected=0x${"%04X".format(frameCrc)}, got=0x${"%04X".format(frame.crc16)})")
            return
        }

        if (!frame.isValid) {
            Log.w(TAG, "F003: Invalid reading (sentinel or out of range)")
            firstValidReadingWaitStatus(now)?.let {
                Log.i(TAG, "F003: still waiting for first valid reading ($it)")
            }
            // handleGlucoseResult() with 0 will set charcha[1] for failure tracking
            handleGlucoseResult(0L, now)
            if (firstValidReadingAnchorMs > 0L || postResetWarmupExtensionActive) {
                val warmupAnchor = effectiveWarmupAnchorMs()
                val warmupElapsed = warmupAnchor > 0L && (now - warmupAnchor) >= WARMUP_DURATION_MS
                if (warmupElapsed && constatstatusstr != POST_RESET_WAITING_STATUS) {
                    constatstatusstr = POST_RESET_WAITING_STATUS
                }
                UiRefreshBus.requestStatusRefresh()
            }
            return
        }

        val (trustedOffsetMinutes, offsetResolutionNote) = resolveTrustedLiveOffsetMinutes(frame, now)
        offsetResolutionNote?.let {
            Log.w(
                TAG,
                "F003 timing: $it " +
                    "(wireOffset=${frame.timeOffsetMinutes} glucose=${frame.glucoseMgDl} i1=${frame.i1} i2=${frame.i2})"
            )
        }

        handleParsedLiveReading(
            now = now,
            source = "native",
            autoValue = frame.glucoseMgDl,
            trustedTimeOffsetMinutes = trustedOffsetMinutes,
            rawValue = frame.i1 * 10f,
            sensorGlucose = frame.i1 * 18.0182f,
            rawI1 = frame.i1,
            rawI2 = frame.i2,
        )
    }

    /**
     * Handle 5-byte F003 status/keepalive frame.
     * Decrypts and attempts to extract battery voltage.
     *
     * These frames arrive every ~4 minutes between glucose data frames.
     * Format (after decryption) is not fully documented. We decrypt and log
     * the plaintext for analysis, and attempt to extract battery voltage
     * from bytes 1-2 as u16 LE millivolts (matching the vendor driver's
     * 2-byte LE battery format from AUTO_UPDATE_BATTERY_VOLTAGE).
     */
    private fun handleStatusFrame(encryptedData: ByteArray) {
        val decrypted = keyExchange.decrypt(encryptedData)
        if (decrypted == null) {
            Log.d(TAG, "F003: Status frame — cannot decrypt (no session key)")
            return
        }

        Log.d(TAG, "F003: Status frame decrypted: ${AiDexParser.hexString(decrypted)}")

        // Attempt battery voltage extraction: bytes 1-2 as u16 LE millivolts.
        // Vendor driver reports typical range ~1530-1560 mV.
        // Accept any value in 500-3500 mV range as plausible.
        if (decrypted.size >= 3) {
            val candidate = (decrypted[1].toInt() and 0xFF) or ((decrypted[2].toInt() and 0xFF) shl 8)
            if (candidate in 500..3500) {
                _batteryMillivolts = candidate
                Log.i(TAG, "F003: Battery voltage: ${candidate} mV (${String.format("%.3f", candidate / 1000.0)} V)")
            }
        }
    }

    /**
     * Handle a 13-byte F003 frame — observed after calibration commands.
     *
     * The log shows frames like `0A EC 33 F1 EE 18 7D D2 ...` (opcode 0x0A) arriving
     * immediately after SET_CALIBRATION (0x25) is acknowledged. These may be
     * calibration-related notifications from the sensor confirming internal state changes.
     *
     * We decrypt, log, and attempt to parse any useful information.
     */
    private fun handleCalibrationNotificationFrame(encryptedData: ByteArray) {
        val decrypted = keyExchange.decrypt(encryptedData)
        if (decrypted == null) {
            Log.d(TAG, "F003: 13-byte frame — cannot decrypt (no session key)")
            return
        }

        val opcode = decrypted[0].toInt() and 0xFF
        Log.i(TAG, "F003: 13-byte notification frame: opcode=0x${"%02X".format(opcode)}, " +
                "decrypted=${AiDexParser.hexString(decrypted)}")

        // Opcode 0x0A is the only 13-byte F003 opcode we've observed in logs.
        // It appears to be a calibration state update notification.
        // For now, log the payload. If the frame contains calibration data in a known
        // format, we can parse it in the future.
        when (opcode) {
            0x0A -> {
                Log.i(TAG, "F003: Calibration notification (opcode 0x0A, ${decrypted.size} bytes)")
                // The sensor is confirming it updated its internal calibration state.
                // We already auto-refresh calibration records after a successful SET_CALIBRATION ACK,
                // so no additional action is needed here.
            }
            else -> {
                Log.d(TAG, "F003: Unknown 13-byte frame opcode 0x${"%02X".format(opcode)}")
            }
        }
    }

    // =========================================================================
    // F002 Command Responses
    // =========================================================================

    private fun handleF002Response(data: ByteArray, gatt: BluetoothGatt) {
        if (data.isEmpty()) return
        lastF002FrameTimeMs = System.currentTimeMillis()
        Log.i(TAG, "handleF002Response: len=${data.size}, raw=${AiDexParser.hexString(data.copyOfRange(0, minOf(data.size, 16)))}")

        // Decrypt if session key is available
        val plaintext = if (keyExchange.isComplete) {
            keyExchange.decrypt(data)
        } else {
            data
        }
        if (plaintext == null) {
            Log.w(TAG, "F002: Cannot decrypt response")
            return
        }

        // Validate CRC-16 on decrypted response.
        // Known data opcodes (0x21-0x24, 0x26-0x27) always have CRC trailers —
        // reject on mismatch to prevent processing corrupt data.
        // Control/ACK opcodes (0x20, 0x25, 0xF0, 0xF2, 0xF3, 0x11) may lack CRC.
        val crcValid = plaintext.size < 3 || Crc16CcittFalse.validateResponse(plaintext)

        val opcode = plaintext[0].toInt() and 0xFF
        Log.d(TAG, "F002 response: opcode=0x${"%02X".format(opcode)}, len=${plaintext.size}, crc=$crcValid")

        // For data-carrying opcodes, reject on CRC failure
        if (!crcValid && opcode in intArrayOf(0x21, 0x22, 0x23, 0x24, 0x26, 0x27)) {
            Log.e(TAG, "F002: CRC-16 FAILED for data opcode 0x${"%02X".format(opcode)} — rejecting corrupt response")
            return
        }

        when (opcode) {
            0x21 -> handleDeviceInfoResponse(plaintext)
            0x22 -> handleHistoryRangeResponse(plaintext)
            0x23 -> handleHistoryRawResponse(plaintext)
            0x24 -> handleHistoryBriefResponse(plaintext)
            0x25 -> handleCalibrationAck(plaintext)
            0x26 -> handleCalibrationRangeResponse(plaintext)
            0x27 -> handleCalibrationResponse(plaintext)
            0x11 -> handleBroadcastDataResponse(plaintext)
            0x20 -> handleNewSensorAck(plaintext)
            0xF3 -> handleClearStorageResponse(plaintext)
            0xF0 -> handleResetResponse(plaintext)
            0xF2 -> handleDeleteBondResponse(plaintext)
            else -> {
                // AUTO_UPDATE_CALIBRATION detection: sensor pushes unsolicited calibration
                // data with an unknown opcode. Heuristic: valid CRC, size >= 12 (opcode +
                // status + startIndex_u16 + at least 1×8-byte calibration record), and the
                // opcode doesn't match any known command.
                if (plaintext.size >= 12 && Crc16CcittFalse.validateResponse(plaintext)) {
                    Log.i(TAG, "F002: Unsolicited push (opcode=0x${"%02X".format(opcode)}): " +
                            "attempting AUTO_UPDATE_CALIBRATION parse")
                    handleAutoUpdateCalibration(plaintext)
                } else {
                    Log.d(TAG, "F002: Unknown opcode 0x${"%02X".format(opcode)}")
                }
            }
        }
    }

    // -- Response Handlers --

    private fun handleDeviceInfoResponse(data: ByteArray) {
        // Wire opcode 0x21 returns combined device info + start time.
        // Real capture format (26 bytes total):
        //   data[0]     = 0x21 (opcode)
        //   data[1..2]  = status (2 bytes, e.g. 00 00)
        //   data[3]     = fw_major
        //   data[4]     = fw_minor
        //   data[5]     = hw_major
        //   data[6]     = hw_minor
        //   data[7..8]  = sensor_type (u16 LE)
        //   data[9..16] = model name (8 bytes ASCII, null-terminated)
        //   data[17..18]= year (u16 LE)
        //   data[19]    = month (1-12)
        //   data[20]    = day
        //   data[21]    = hour
        //   data[22]    = minute
        //   data[23]    = second
        //   data[24]    = timezone (signed, 15-min quarters)
        //   data[25]    = DST offset (15-min quarters)
        //
        // Confirmed from real sensor X-2222267V4E capture:
        //   DeviceInfo: 00 00 01 07 01 03 0F 00 47 58 2D 30 31 53 00 00
        //   StartTime:  EA 07 02 1C 13 25 05 14 00

        if (data.size < 3) {
            Log.d(TAG, "Device info 0x21: too short (${data.size} bytes) — ignoring, using DIS+2AAA")
            return
        }
        val statusByte = data[1].toInt() and 0xFF
        Log.i(TAG, "Device info response: status=0x${"%02X".format(statusByte)}, len=${data.size}")

        // If status != 0 or too short — not supported by this sensor, no retries.
        if (data.size < 17 || statusByte != 0) {
            Log.d(TAG, "Device info 0x21: status=0x${"%02X".format(statusByte)}, len=${data.size} — not supported, using DIS+2AAA")
            return
        }

        // Parse device metadata (bytes 3-16)
        try {
            val fwMajor = data[3].toInt() and 0xFF
            val fwMinor = data[4].toInt() and 0xFF
            val hwMajor = data[5].toInt() and 0xFF
            val hwMinor = data[6].toInt() and 0xFF
            _firmwareVersion = "$fwMajor.$fwMinor"
            _hardwareVersion = "$hwMajor.$hwMinor"

            // Model string: bytes 9..16, null-terminated ASCII
            val modelBytes = data.copyOfRange(9, 17)
            val nullIdx = modelBytes.indexOf(0.toByte())
            val modelStr = if (nullIdx >= 0) String(modelBytes, 0, nullIdx, Charsets.US_ASCII)
            else String(modelBytes, Charsets.US_ASCII)
            if (modelStr.isNotBlank()) {
                _modelName = modelStr.trim()
                applyWearProfileFromModel(_modelName)
            }
            Log.i(TAG, "Device info: fw=$_firmwareVersion hw=$_hardwareVersion model=$_modelName")
        } catch (t: Throwable) {
            Log.e(TAG, "Device info parse failed: ${t.message}")
        }

        // Parse start time (bytes 17-25)
        if (data.size >= 24) {
            try {
                val year = u16LE(data, 17)
                val month = data[19].toInt() and 0xFF
                val day = data[20].toInt() and 0xFF
                val hour = data[21].toInt() and 0xFF
                val minute = data[22].toInt() and 0xFF
                val second = data[23].toInt() and 0xFF
                val tzQuarters = if (data.size >= 25) data[24].toInt() else 0  // signed
                val dstQuarters = if (data.size >= 26) data[25].toInt() and 0xFF else 0

                val isAllZeros = (year == 0 && month == 0 && day == 0 && hour == 0 && minute == 0 && second == 0)
                if (isAllZeros) {
                    Log.w(TAG, "Start time: all zeros — sensor not activated")
                } else if (year in 2020..2040 && month in 1..12 && day in 1..31) {
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    cal.set(year, month - 1, day, hour, minute, second)
                    cal.set(Calendar.MILLISECOND, 0)
                    val tzOffsetMs = (tzQuarters + dstQuarters) * 15L * 60_000L
                    val startUtcMs = cal.timeInMillis - tzOffsetMs

                    sensorstartmsec = startUtcMs
                    val now = System.currentTimeMillis()
                    lastOffsetMinutes = ((now - startUtcMs) / 60_000L).toInt()

                    // Persist wear days BEFORE start time — aidexSetStartTime
                    // uses info->days to compute wearduration2
                    if (dataptr != 0L) {
                        try {
                            Natives.aidexSetWearDays(dataptr, _wearDays)
                        } catch (_: Throwable) {}
                        try {
                            Natives.aidexSetStartTime(dataptr, startUtcMs)
                        } catch (_: Throwable) {}
                    }

                    // Compute expiry
                    val expiryMs = startUtcMs + (_wearDays.toLong() * 24 * 3600_000L)
                    _sensorExpired = now > expiryMs

                    Log.i(TAG, "Start time: $year-${"%02d".format(month)}-${"%02d".format(day)} " +
                            "${"%02d".format(hour)}:${"%02d".format(minute)}:${"%02d".format(second)} " +
                            "tz=${tzQuarters}q dst=${dstQuarters}q → startMs=$startUtcMs offset=${lastOffsetMinutes}min")
                } else {
                    Log.w(TAG, "Start time: date out of range $year-$month-$day")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Start time parse failed: ${t.message}")
            }
        } else {
            // Got metadata but no start time — will use 2AAA or infer from offset
            Log.d(TAG, "Device info 0x21: metadata OK but no start time (len=${data.size}) — using 2AAA")
        }

        // If we got here, we have everything
        deviceInfoComplete = true
    }

    /** Optional opcode 0x11 control response. Ignore unless it becomes protocol-relevant. */
    private fun handleBroadcastDataResponse(data: ByteArray) {
        Log.d(TAG, "Ignoring optional F002 opcode 0x11 len=${data.size}")
    }

    private fun handleHistoryRangeResponse(data: ByteArray) {
        if (data.size < 8) return
        // data[2..3] = briefStart (0x24), data[4..5] = rawStart (0x23), data[6..7] = newest offset
        val briefStart = u16LE(data, 2)
        val rawStart = u16LE(data, 4)
        val newest = u16LE(data, 6)
        historyNewestOffset = newest
        historyStoredCount = 0
        historyDownloading = true
        historyDownloadStartIndex = rawStart  // snapshot for progress display
        historyPhase = HistoryPhase.DOWNLOADING_CALIBRATED
        Log.i(TAG, "History range: briefStart=$briefStart, rawStart=$rawStart, newest=$newest")

        val downloadPlan = AiDexHistoryPolicy.planInitialDownload(
            briefStart = briefStart,
            rawStart = rawStart,
            newest = newest,
            persistedRawNextIndex = historyRawNextIndex,
            persistedBriefNextIndex = historyBriefNextIndex,
        )
        historyRawNextIndex = downloadPlan.rawNextIndex
        historyBriefNextIndex = downloadPlan.briefNextIndex
        historyDownloadStartIndex = downloadPlan.downloadStartIndex

        if (downloadPlan.action == AiDexHistoryPolicy.InitialAction.COMPLETE_EMPTY) {
            Log.i(TAG, "History range is empty — completing history download immediately")
            onHistoryDownloadComplete()
            return
        }

        // Snapshot liveOffsetCutoff for history dedup.
        // If live F003 readings have been stored this session, only skip the exact
        // live-backed minute already written by the live pipeline. Reconnect history
        // pages can legitimately contain newer missing minutes in the same page.
        if (liveOffsetCutoff == 0 && newest > 0) {
            // No live readings yet this session — set cutoff to newest so we don't
            // skip anything during initial history catch-up.
            // (liveOffsetCutoff stays 0 — storeHistoryEntries guards on > 0)
            Log.d(TAG, "History dedup: no live readings yet, liveOffsetCutoff stays 0 (no filtering)")
        } else if (liveOffsetCutoff > 0) {
            Log.i(TAG, "History dedup: liveOffsetCutoff=$liveOffsetCutoff (only the exact live-backed offset will be skipped)")
        }

        if (liveOffsetCutoff == 0 && lastGlucoseTimeMs > 0L && newest > 0) {
            liveOffsetCutoff = newest
            Log.i(TAG, "History dedup: snapped liveOffsetCutoff to newest history offset $newest because live data already arrived this session")
        }

        // Update sensorstartmsec from the newest offset.
        // This is critical: SuperGattCallback constructor may have set sensorstartmsec to "now"
        // (via Natives.getSensorStartmsec for a newly-registered sensor), but the sensor has
        // been running for days. ensureSensorStartTime will override if >10min off.
        if (newest > 0) {
            lastOffsetMinutes = newest
            ensureSensorStartTime(System.currentTimeMillis())
        }

        Log.i(TAG, "History download: starting from raw=$historyRawNextIndex, brief=$historyBriefNextIndex (sensor range: $rawStart..$newest)")

        when (downloadPlan.action) {
            AiDexHistoryPolicy.InitialAction.REQUEST_RAW -> {
                requestHistoryPage(AiDexOpcodes.GET_HISTORIES_RAW, historyRawNextIndex)
            }
            AiDexHistoryPolicy.InitialAction.REQUEST_BRIEF -> {
                Log.i(TAG, "0x23 already up-to-date (rawNext=$historyRawNextIndex > newest=$newest)")
                historyPhase = HistoryPhase.DOWNLOADING_RAW
                requestHistoryPage(AiDexOpcodes.GET_HISTORIES, historyBriefNextIndex)
            }
            AiDexHistoryPolicy.InitialAction.COMPLETE_ALREADY_CAUGHT_UP -> {
                Log.i(TAG, "0x23 and 0x24 already up-to-date (rawNext=$historyRawNextIndex, briefNext=$historyBriefNextIndex, newest=$newest)")
                onHistoryDownloadComplete()
            }
            AiDexHistoryPolicy.InitialAction.COMPLETE_EMPTY -> {
                onHistoryDownloadComplete()
            }
        }
    }

    private fun handleHistoryRawResponse(data: ByteArray) {
        if (data.size < 4) return
        handler.removeCallbacks(historyPageWatchdog)  // Response arrived — cancel page timeout
        // data[1] = status, data[2..] = payload
        val payload = data.copyOfRange(2, data.size)
        val entries = AiDexParser.parseHistoryResponse(payload)
        if (entries.isEmpty()) {
            Log.i(TAG, "History raw (0x23): empty page at offset=$historyRawNextIndex newest=$historyNewestOffset")
            historyPhase = HistoryPhase.DOWNLOADING_RAW
            if (historyBriefNextIndex <= historyNewestOffset) {
                handler.postDelayed({
                    requestHistoryPage(AiDexOpcodes.GET_HISTORIES, historyBriefNextIndex)
                }, HISTORY_REQUEST_DELAY_MS)
            } else {
                onHistoryDownloadComplete()
            }
            return
        }
        if (entries.isNotEmpty()) {
            Log.i(TAG, "History raw (0x23): ${entries.size} entries, offsets ${entries.first().timeOffsetMinutes}..${entries.last().timeOffsetMinutes}")
            onCalibratedHistory?.invoke(entries)

            // Cache 0x23 calibrated glucose by offset using extracted helper.
            // DO NOT store yet — wait for 0x24 to provide raw ADC data,
            // then store BOTH together in a single aidexProcessData call.
            val (cached, skipped) = HistoryMerge.cacheCalibratedEntries(entries, calibratedGlucoseCache)
            Log.i(TAG, "0x23: cached $cached, skipped $skipped (cache size=${calibratedGlucoseCache.size})")

            historyRawNextIndex = entries.last().timeOffsetMinutes + 1
            writeIntPref("historyRawNextIndex", historyRawNextIndex)

            // Fetch next page if more data available
            if (historyRawNextIndex <= historyNewestOffset) {
                handler.postDelayed({
                    requestHistoryPage(AiDexOpcodes.GET_HISTORIES_RAW, historyRawNextIndex)
                }, HISTORY_REQUEST_DELAY_MS)
            } else {
                // 0x23 done — start 0x24 (brief/ADC history)
                Log.i(TAG, "0x23 complete. ${calibratedGlucoseCache.size} entries cached. Starting 0x24...")
                historyPhase = HistoryPhase.DOWNLOADING_RAW
                if (historyBriefNextIndex <= historyNewestOffset) {
                    handler.postDelayed({
                        requestHistoryPage(AiDexOpcodes.GET_HISTORIES, historyBriefNextIndex)
                    }, HISTORY_REQUEST_DELAY_MS)
                } else {
                    onHistoryDownloadComplete()
                }
            }
        }
    }

    private fun handleHistoryBriefResponse(data: ByteArray) {
        if (data.size < 7) return
        handler.removeCallbacks(historyPageWatchdog)  // Response arrived — cancel page timeout
        val payload = data.copyOfRange(2, data.size)
        val entries = AiDexParser.parseBriefHistoryResponse(payload)
        if (entries.isEmpty()) {
            Log.i(TAG, "History brief (0x24): empty page at offset=$historyBriefNextIndex newest=$historyNewestOffset")
            onHistoryDownloadComplete()
            return
        }
        if (entries.isNotEmpty()) {
            Log.i(TAG, "History brief (0x24): ${entries.size} entries, offsets ${entries.first().timeOffsetMinutes}..${entries.last().timeOffsetMinutes}")
            onAdcHistory?.invoke(entries)

            // Merge 0x24 raw ADC data with cached 0x23 calibrated glucose using extracted helper.
            val mergeResult = HistoryMerge.mergeHistoryEntries(entries, calibratedGlucoseCache, lastCalibratedGlucoseFallback)
            storeHistoryEntries(mergeResult.entries)
            // Persist the last known glucose for the next page
            if (mergeResult.lastKnownGlucose != null) lastCalibratedGlucoseFallback = mergeResult.lastKnownGlucose

            Log.i(TAG, "0x24: merged=${mergeResult.mergedCount} fallback=${mergeResult.fallbackCount} noGlucose=${mergeResult.noGlucoseCount} (cache remaining=${calibratedGlucoseCache.size})")

            historyBriefNextIndex = entries.last().timeOffsetMinutes + 1
            writeIntPref("historyBriefNextIndex", historyBriefNextIndex)

            // Fetch next page
            if (historyBriefNextIndex <= historyNewestOffset) {
                handler.postDelayed({
                    requestHistoryPage(AiDexOpcodes.GET_HISTORIES, historyBriefNextIndex)
                }, HISTORY_REQUEST_DELAY_MS)
            } else {
                onHistoryDownloadComplete()
            }
        }
    }

    private fun handleCalibrationAck(data: ByteArray) {
        if (data.size < 2) {
            Log.w(TAG, "Calibration ACK too short: ${data.size} bytes")
            onCalibrationResult?.invoke(false, "Invalid calibration response from sensor")
            return
        }
        val statusByte = data[1].toInt() and 0xFF
        Log.i(TAG, "Calibration ACK: status=0x${"%02X".format(statusByte)}")

        if (statusByte == 0x01) {
            // Success — sensor accepted the calibration
            onCalibrationResult?.invoke(true, "Calibration accepted by sensor")
            showTransientStatus("Calibration accepted")
            // Auto-refresh calibration records to include the new one
            handler.postDelayed({
                val cmd = commandBuilder.getCalibrationRange()
                if (cmd != null) {
                    Log.i(TAG, "Auto-refreshing calibration records after successful calibration")
                    enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
                }
            }, 200L)
        } else {
            onCalibrationResult?.invoke(
                false,
                "Sensor rejected calibration (status: 0x${"%02X".format(statusByte)})"
            )
            showTransientStatus("Calibration failed")
        }
    }

    private fun handleCalibrationRangeResponse(data: ByteArray) {
        if (data.size < 6) return
        val startIndex = u16LE(data, 2)
        val endIndex = u16LE(data, 4)
        Log.i(TAG, "Calibration range: start=$startIndex, end=$endIndex")

        calibrationRangeEndIndex = endIndex

        if (endIndex <= 0 || startIndex > endIndex) {
            Log.i(TAG, "No calibration records available (start=$startIndex, end=$endIndex)")
            return
        }

        // Fetch all calibration records, starting from startIndex, chaining through endIndex
        calibrationDownloading = true
        requestCalibrationPaginated(startIndex, endIndex)
    }

    private fun handleCalibrationResponse(data: ByteArray) {
        if (data.size < 10) return
        // Strip opcode (1 byte) + status (1 byte) from front and CRC-16 (2 bytes) from end
        val crcEnd = if (data.size >= 4) data.size - 2 else data.size
        val payload = data.copyOfRange(2, crcEnd)
        Log.d(TAG, "Calibration payload: ${payload.size} bytes, hex=${AiDexParser.hexString(payload)}")
        val records = AiDexParser.parseCalibrationResponse(payload)
        if (records.isNotEmpty()) {
            Log.i(TAG, "Calibration records: ${records.size} entries")
            onCalibrationRecords?.invoke(records)

            // Merge new records into existing list (dedup by index), then convert
            // native CalibrationRecord → shared CalibrationRecord for UI.
            mergeCalibrationRecords(records)
        }
    }

    /**
     * Merge new native calibration records into the shared calibration record list,
     * deduplicating by index. Converts native → shared type and sorts by index.
     */
    private fun mergeCalibrationRecords(newRecords: List<CalibrationRecord>) {
        val existingByIndex = _calibrationRecords.associateBy { it.index }.toMutableMap()

        for (rec in newRecords) {
            val timestampMs = if (sensorstartmsec > 0L)
                sensorstartmsec + rec.timeOffsetMinutes.toLong() * 60_000L
            else 0L
            val valid = rec.timeOffsetMinutes > 0 &&
                    rec.timeOffsetMinutes.toLong() <= (MAX_OFFSET_DAYS * 24L * 60L) &&
                    rec.referenceGlucoseMgDl in MIN_VALID_GLUCOSE_MGDL..MAX_VALID_GLUCOSE_MGDL
            existingByIndex[rec.index] = SharedCalibrationRecord(
                index = rec.index,
                timeOffsetMinutes = rec.timeOffsetMinutes,
                referenceGlucoseMgDl = rec.referenceGlucoseMgDl,
                cf = rec.calibrationFactor,
                offset = rec.calibrationOffset,
                isValid = valid,
                timestampMs = timestampMs,
            )

            Log.d(TAG, "CALIBRATION: index=${rec.index} glucose=${rec.referenceGlucoseMgDl}mg/dL " +
                    "offset=${rec.timeOffsetMinutes}min cf=${String.format("%.2f", rec.calibrationFactor)} " +
                    "calOffset=${String.format("%.2f", rec.calibrationOffset)} valid=$valid")
        }

        _calibrationRecords = existingByIndex.values.sortedBy { it.index }
        Log.i(TAG, "Stored ${_calibrationRecords.size} calibration records for UI")
    }

    /**
     * Handle AUTO_UPDATE_CALIBRATION — unsolicited calibration push from sensor.
     *
     * The sensor pushes calibration data when its internal calibration state changes.
     * Same payload format as GET_CALIBRATION response: [opcode, status, startIndex_u16LE,
     * N×8-byte calibration records, CRC-16 trailer].
     */
    private fun handleAutoUpdateCalibration(data: ByteArray) {
        if (data.size < 12) return

        // Strip opcode + status (2 bytes) from front, CRC-16 (2 bytes) from end
        val payloadEnd = data.size - 2
        if (payloadEnd <= 2) {
            Log.d(TAG, "AUTO_UPDATE_CALIBRATION: no payload")
            return
        }
        val payload = data.copyOfRange(2, payloadEnd)

        val records = AiDexParser.parseCalibrationResponse(payload)
        if (records.isEmpty()) {
            Log.d(TAG, "AUTO_UPDATE_CALIBRATION: no records parsed from ${payload.size} bytes")
            return
        }

        Log.i(TAG, "AUTO_UPDATE_CALIBRATION: received ${records.size} record(s)")
        onCalibrationRecords?.invoke(records)
        mergeCalibrationRecords(records)
    }

    private fun handleNewSensorAck(data: ByteArray) {
        if (data.size < 2) return
        val statusByte = data[1].toInt() and 0xFF
        Log.i(TAG, "New sensor ACK: status=0x${"%02X".format(statusByte)}")
        if (statusByte == 0x00) {
            armFirstValidReadingWait(System.currentTimeMillis(), "new-sensor-ack")
            handler.postDelayed({ readCGMSessionCharacteristics() }, 1_000L)
        }
    }

    /**
     * Handle CLEAR_STORAGE (0xF3) response.
     * On success (status=0x00), send RESET (0xF0) as the second step of the reset sequence.
     */
    private fun handleClearStorageResponse(data: ByteArray) {
        val status = if (data.size >= 2) data[1].toInt() and 0xFF else 0xFF
        Log.i(TAG, "CLEAR_STORAGE response: status=0x${"%02X".format(status)}")
        if (pendingResetReconnect && status == 0x00) {
            // Step 2: Now send RESET (0xF0)
            Log.i(TAG, "CLEAR_STORAGE done — sending RESET (0xF0)")
            val cmd = commandBuilder.reset()
            if (cmd != null) {
                enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
            } else {
                Log.e(TAG, "Cannot send RESET: session key unavailable")
            }
        } else if (status != 0x00) {
            Log.e(TAG, "CLEAR_STORAGE failed — not arming extended post-reset warmup")
            pendingResetReconnect = false
            needsPostResetActivation = false
            postResetWarmupExtensionActive = false
            writeBoolPref("needsPostResetActivation", false)
            writeBoolPref("postResetWarmupExtensionActive", false)
        }
    }

    /**
     * Handle RESET (0xF0) response.
     * The sensor will disconnect shortly after this. The disconnect handler
     * checks pendingResetReconnect to perform bond removal + delayed reconnect.
     */
    private fun handleResetResponse(data: ByteArray) {
        val status = if (data.size >= 2) data[1].toInt() and 0xFF else 0xFF
        Log.i(TAG, "RESET response: status=0x${"%02X".format(status)} — sensor will disconnect shortly")
        postResetWarmupExtensionActive = (status == 0x00)
        writeBoolPref("postResetWarmupExtensionActive", postResetWarmupExtensionActive)
    }

    /**
     * Handle DELETE_BOND (0xF2) response.
     * If pendingUnpairDisconnect is set, perform the deferred bond removal + disconnect.
     */
    private fun handleDeleteBondResponse(data: ByteArray) {
        val status = if (data.size >= 2) data[1].toInt() and 0xFF else 0xFF
        Log.i(TAG, "DELETE_BOND response: status=0x${"%02X".format(status)}")
        if (pendingUnpairDisconnect) {
            pendingUnpairDisconnect = false
            Log.i(TAG, "DELETE_BOND delivered — performing deferred unpair cleanup")
            // Remove Android-level bond
            try {
                val device = mBluetoothGatt?.device
                if (device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                    val removeBond = device.javaClass.getMethod("removeBond")
                    removeBond.invoke(device)
                    Log.i(TAG, "unpairSensor: BLE bond removed")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "unpairSensor: removeBond failed: ${t.message}")
            }
            keyExchange.reset()
            softDisconnect()
            constatstatusstr = "Unpaired — Broadcast Only"
            // Transition to broadcast-only mode so user keeps getting data
            reconnect.isBroadcastOnlyMode = true
            stop = false
            UiRefreshBus.requestStatusRefresh()
            handler.post { startBroadcastScan("post-unpair") }
        }
    }

    // =========================================================================
    // F002 Command Sending
    // =========================================================================

    private fun requestDeviceInfo() {
        val cmd = commandBuilder.getDeviceInfo() ?: return
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
    }

    private fun requestHistoryRange() {
        val cmd = commandBuilder.getHistoryRange() ?: return
        pendingInitialHistoryRequest = false
        handler.removeCallbacks(delayedInitialHistoryRequest)
        historyDownloading = true
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
    }

    private fun requestHistoryPage(opcode: Int, offset: Int) {
        val cmd = when (opcode) {
            AiDexOpcodes.GET_HISTORIES_RAW -> commandBuilder.getHistoriesRaw(offset)
            AiDexOpcodes.GET_HISTORIES -> commandBuilder.getHistories(offset)
            else -> return
        }
        if (cmd != null) {
            enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
            // Start page timeout — if no response arrives, abort history download
            handler.removeCallbacks(historyPageWatchdog)
            handler.postDelayed(historyPageWatchdog, HISTORY_PAGE_TIMEOUT_MS)
        }
    }

    private fun requestCalibration(index: Int) {
        val cmd = commandBuilder.getCalibration(index) ?: return
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
    }

    /**
     * Fetch calibration records one index at a time from [currentIndex] through [lastIndex],
     * with 100ms delay between requests (matching Android vendor driver's Thread.sleep(100)).
     */
    private fun requestCalibrationPaginated(currentIndex: Int, lastIndex: Int) {
        requestCalibration(currentIndex)

        // The response handler (handleCalibrationResponse) will parse and store records.
        // Chain to next index after a delay. We track progress via calibrationRangeEndIndex.
        // Note: The F002 response dispatch will call handleCalibrationResponse which
        // stores records. We chain the next request after a delay here.
        val nextIndex = currentIndex + 1
        if (nextIndex <= lastIndex) {
            handler.postDelayed({
                requestCalibrationPaginated(nextIndex, lastIndex)
            }, 100L)
        } else {
            // Last request sent — mark download complete after a brief delay
            // to let the response arrive and be processed.
            handler.postDelayed({
                calibrationDownloading = false
                Log.i(TAG, "Calibration download complete: ${_calibrationRecords.size} records stored")
            }, 500L)
        }
    }

    // -- Public Command Methods --

    /**
     * Send a calibration reference value to the sensor.
     *
     * @param offsetMinutes time offset in minutes from sensor start
     * @param glucoseMgDl reference blood glucose in mg/dL
     */
    fun sendCalibration(offsetMinutes: Int, glucoseMgDl: Int) {
        val cmd = commandBuilder.setCalibration(offsetMinutes, glucoseMgDl) ?: run {
            Log.e(TAG, "Cannot send calibration — session key not available")
            return
        }
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
    }

    /**
     * Activate a new sensor.
     */
    fun activateSensor(
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int, second: Int,
        tzQuarters: Int, dstQuarters: Int
    ) {
        val cmd = commandBuilder.setNewSensor(year, month, day, hour, minute, second, tzQuarters, dstQuarters) ?: run {
            Log.e(TAG, "Cannot activate sensor — session key not available")
            return
        }
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
    }

    /**
     * Request history backfill (both calibrated and raw).
     */
    fun requestHistoryBackfill() {
        if (historyDownloading) return
        requestHistoryRange()
    }

    // =========================================================================
    // GATT Queue
    // =========================================================================

    private fun enqueueGattOp(op: GattOp) {
        handler.post {
            gattQueue.add(op)
            if (!gattOpActive) {
                drainGattQueue()
            }
        }
    }

    private fun drainGattQueue() {
        if (gattOpActive) return
        if (queuePausedForBonding) {
            Log.d(TAG, "GATT queue: paused for bonding")
            return
        }
        if (gattQueue.isEmpty()) return

        // Cancel any prior watchdog before starting a new op
        handler.removeCallbacks(gattOpWatchdog)
        currentGattOp = null

        val next = gattQueue.removeFirst()
        val gatt = mBluetoothGatt
        if (gatt == null) {
            Log.w(TAG, "GATT queue: no active GATT, dropping ${gattQueue.size + 1} ops")
            gattQueue.clear()
            gattOpActive = false
            return
        }

        when (next) {
            is GattOp.Write -> {
                val service = gatt.getService(SERVICE_F000)
                val characteristic = service?.getCharacteristic(next.charUuid)
                if (characteristic == null) {
                    Log.w(TAG, "GATT write: characteristic ${next.charUuid} not found, skipping")
                    drainGattQueue()
                    return
                }
                characteristic.value = next.data
                val writeType = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                characteristic.writeType = writeType
                val ok = gatt.writeCharacteristic(characteristic)
                Log.d(TAG, "GATT write [${next.charUuid}]: ok=$ok, queueRemaining=${gattQueue.size}")

                if (ok && writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                    gattOpActive = false
                    handler.postDelayed({
                        if (!gattOpActive) drainGattQueue()
                    }, NO_RESPONSE_ADVANCE_MS)
                } else {
                    gattOpActive = ok
                    if (ok) {
                        currentGattOp = next
                        handler.postDelayed(gattOpWatchdog, GATT_OP_TIMEOUT_MS)
                    }
                }
                if (!ok) {
                    handleWriteFailure(next, gatt)
                }
            }
            is GattOp.Read -> {
                val service = gatt.getService(next.serviceUuid)
                val characteristic = service?.getCharacteristic(next.charUuid)
                if (characteristic == null) {
                    Log.w(TAG, "GATT read: characteristic ${next.charUuid} not found in service ${next.serviceUuid}, skipping")
                    drainGattQueue()
                    return
                }
                val ok = gatt.readCharacteristic(characteristic)
                Log.d(TAG, "GATT read [${next.charUuid}]: ok=$ok")
                gattOpActive = ok
                if (ok) {
                    currentGattOp = next
                    handler.postDelayed(gattOpWatchdog, GATT_OP_TIMEOUT_MS)
                }
                if (!ok) {
                    handleReadFailure(next, gatt)
                }
            }
        }
    }

    private fun handleWriteFailure(op: GattOp.Write, gatt: BluetoothGatt) {
        val bondState = gatt.device?.bondState ?: BluetoothDevice.BOND_NONE
        if (bondState == BluetoothDevice.BOND_BONDING) {
            Log.i(TAG, "GATT write failed during BONDING — pausing queue")
            queuePausedForBonding = true
            gattQueue.addFirst(op) // Re-queue without incrementing retry
            handler.postDelayed({
                if (queuePausedForBonding) {
                    Log.w(TAG, "Bonding pause timeout (${BONDING_PAUSE_TIMEOUT_MS}ms) — resuming")
                    queuePausedForBonding = false
                    drainGattQueue()
                }
            }, BONDING_PAUSE_TIMEOUT_MS)
            return
        }
        op.retryCount++
        if (op.retryCount >= GATT_OP_MAX_RETRIES) {
            Log.e(TAG, "GATT write failed after ${op.retryCount} retries — dropping and reconnecting")
            gattQueue.clear()
            gattOpActive = false
            handler.post {
                close()
                connectDevice(reconnect.adaptiveDelayMs())
            }
        } else {
            gattQueue.addFirst(op)
            handler.postDelayed({ drainGattQueue() }, GATT_WRITE_RETRY_DELAY_MS)
        }
    }

    private fun handleReadFailure(op: GattOp.Read, gatt: BluetoothGatt) {
        op.retryCount++
        if (op.retryCount >= GATT_OP_MAX_RETRIES) {
            Log.e(TAG, "GATT read failed after ${op.retryCount} retries — dropping")
            gattOpActive = false
            drainGattQueue()
        } else {
            gattQueue.addFirst(op)
            handler.postDelayed({ drainGattQueue() }, GATT_WRITE_RETRY_DELAY_MS)
        }
    }

    // =========================================================================
    // Service Discovery Retry + Watchdog
    // =========================================================================

    private var discoveryRetryAttempt = 0

    private fun scheduleDiscoveryRetries(gatt: BluetoothGatt) {
        discoveryRetryAttempt = 0
        scheduleNextDiscoveryRetry(gatt)
    }

    private fun scheduleNextDiscoveryRetry(gatt: BluetoothGatt) {
        discoveryRetryAttempt++
        if (discoveryRetryAttempt > DISCOVERY_MAX_RETRIES) {
            // All retries exhausted — disconnect and reconnect cleanly
            Log.w(TAG, "Service discovery retries exhausted ($DISCOVERY_MAX_RETRIES). Disconnecting and scheduling reconnect.")
            recoverFromServiceDiscoveryFailure()
            return
        }
        handler.postDelayed({
            if (mBluetoothGatt != null && !servicesReady) {
                Log.i(TAG, "Service discovery retry $discoveryRetryAttempt/$DISCOVERY_MAX_RETRIES")
                mBluetoothGatt?.discoverServices()
                // Schedule next retry (or the final recovery)
                scheduleNextDiscoveryRetry(gatt)
            }
        }, DISCOVERY_RETRY_DELAY_MS)
    }

    /**
     * Called when service discovery fails after all retries.
     * Disconnects, closes, and schedules a reconnect — preventing the zombie state
     * where the driver has a GATT object but services never became ready.
     */
    private fun recoverFromServiceDiscoveryFailure() {
        Log.w(TAG, "recoverFromServiceDiscoveryFailure: closing GATT and scheduling reconnect")
        constatstatusstr = "Reconnecting"
        setPhase(Phase.IDLE)
        // Cancel all pending retry/watchdog callbacks on worker handler
        // (discovery retries, key exchange watchdog, etc. are all on handler)
        // Close the GATT cleanly
        close()
        // Schedule reconnect on the worker handler
        val delay = reconnect.nextReconnectDelayMs()
        Log.i(TAG, "Scheduling reconnect after service discovery failure in ${delay}ms")
        handler.postDelayed({ connectDevice(0) }, delay)
    }

    // =========================================================================
    // Data Storage Helpers
    // =========================================================================

    // HistoryStoreEntry is now in data/HistoryMerge.kt for testability

    /**
     * Ensure sensorstartmsec is correct before storing data.
     *
     * Matches the vendor driver's updateStartTimeFromOffset() logic:
     * - If lastOffsetMinutes is available, compute inferredStart = now - offset * 60_000
     * - Override sensorstartmsec if it's 0 OR if it's >10 minutes off from the inferred value
     *   (handles the case where SuperGattCallback constructor set it to "now" via
     *   Natives.getSensorStartmsec for a newly-registered sensor, even though the sensor
     *   has been running for days)
     * - Last resort: use current time
     */
    private fun ensureSensorStartTime(now: Long, trustedOffsetMinutes: Int? = null) {
        val effectiveOffsetMinutes = trustedOffsetMinutes ?: lastOffsetMinutes.takeIf { it > 0 }
        if (effectiveOffsetMinutes != null && effectiveOffsetMinutes > 0) {
            val inferredStart = now - (effectiveOffsetMinutes.toLong() * 60_000L)
            if (sensorstartmsec == 0L || kotlin.math.abs(sensorstartmsec - inferredStart) > (10L * 60_000L)) {
                sensorstartmsec = inferredStart
                Log.i(TAG, "Updated sensorstartmsec from offset: ${effectiveOffsetMinutes}min → $inferredStart")
                if (dataptr != 0L) {
                    // Push wear days BEFORE start time — aidexSetStartTime
                    // uses info->days to compute wearduration2
                    try {
                        Natives.aidexSetWearDays(dataptr, _wearDays)
                    } catch (_: Throwable) {}
                    try {
                        Natives.aidexSetStartTime(dataptr, sensorstartmsec)
                    } catch (_: Throwable) {}
                }
            }
        } else if (sensorstartmsec == 0L) {
            sensorstartmsec = now
            Log.i(TAG, "Fallback sensorstartmsec = now ($now)")
            if (dataptr != 0L) {
                try {
                    Natives.aidexSetWearDays(dataptr, _wearDays)
                } catch (_: Throwable) {}
                try {
                    Natives.aidexSetStartTime(dataptr, sensorstartmsec)
                } catch (_: Throwable) {}
            }
        }

        // Update local expiry state whenever start time is set
        if (sensorstartmsec > 0L) {
            val expiryMs = sensorstartmsec + (_wearDays.toLong() * 24 * 3600_000L)
            _sensorExpired = now > expiryMs
        }
    }

    /**
     * Store a batch of history entries to the native C++ layer via aidexProcessData.
     *
     * Matches the vendor driver's storeHistoryRecord() logic:
     * - Requires sensorstartmsec > 0 (timestamp = sensorstartmsec + offset * 60_000)
     * - Filters: invalid entries, ADC saturation (≥1023), out of range, warmup period, future
     * - Calls aidexProcessData with ONLY glucose/raw, NO handleGlucoseResult (no notifications)
     * - Does progressive HistorySync every HISTORY_SYNC_BATCH_SIZE entries
     */
    private fun storeHistoryEntries(entries: List<HistoryStoreEntry>) {
        // Ensure start time is available before computing timestamps.
        // History download can begin before 0x21 succeeds or before any F003 arrives.
        if (sensorstartmsec <= 0L) {
            ensureSensorStartTime(System.currentTimeMillis())
        }
        if (sensorstartmsec <= 0L) {
            Log.w(TAG, "storeHistoryEntries: sensorstartmsec still not set after ensureSensorStartTime — skipping ${entries.size} entries")
            return
        }
        if (dataptr == 0L) {
            Log.w(TAG, "storeHistoryEntries: dataptr is 0 — cannot store")
            return
        }

        val now = System.currentTimeMillis()
        var stored = 0
        var newestStoredTimeMs = 0L

        for (entry in entries) {
            // Filter invalid
            if (!entry.isValid) continue
            if (entry.offsetMinutes <= 0) continue
            if (entry.offsetMinutes.toLong() > MAX_OFFSET_DAYS * 24L * 60L) continue

            // Skip entries beyond the sensor's reported newest offset — these contain
            // uninitialized/corrupt data from the sensor's ring buffer write head.
            if (historyNewestOffset > 0 && entry.offsetMinutes > historyNewestOffset) continue

            // Only skip the exact live-backed minute that was already stored by
            // direct F003 before history started. On reconnect, newer history
            // offsets in the same page are often legitimate backfill rows.
            if (AiDexHistoryPolicy.shouldSkipHistoryEntryForLiveDedupe(
                    entryOffsetMinutes = entry.offsetMinutes,
                    liveOffsetCutoff = liveOffsetCutoff,
                )
            ) continue

            // Filter ADC saturation sentinel (≥1023 for calibrated, but raw can be higher)
            val glucoseInt = entry.glucoseMgDl.toInt()
            if (glucoseInt >= 1023 && entry.glucoseMgDl > 0f) continue

            // Filter out-of-range calibrated values (matches vendor driver's MIN_VALID..MAX_VALID check)
            if (glucoseInt !in MIN_VALID_GLUCOSE_MGDL..MAX_VALID_GLUCOSE_MGDL) continue

            // Compute timestamp
            val historicalTimeMs = sensorstartmsec + (entry.offsetMinutes.toLong() * 60_000L)

            // No warmup gate — valid readings during warmup are stored and displayed.
            // The isValid/MIN_VALID check above already filters out garbage warmup data
            // (e.g., glucose=15 mg/dL). Valid-looking readings (>= MIN_VALID) are stored
            // even during the first 7 minutes so the user sees data as soon as possible.

            // Future-timestamp guard (2 minutes tolerance)
            if (historicalTimeMs > now + 120_000L) continue

            // Store via JNI — lightweight path, no handleGlucoseResult
            try {
                val rawForStore = if (entry.rawMgDl.isFinite() && entry.rawMgDl > 0f) entry.rawMgDl else 0f
                Natives.aidexProcessData(
                    dataptr, byteArrayOf(0), historicalTimeMs,
                    entry.glucoseMgDl, rawForStore, 1.0f
                )
                stored++
                historyStoredCount++
                if (historicalTimeMs > newestStoredTimeMs) {
                    newestStoredTimeMs = historicalTimeMs
                }
                if (entry.offsetMinutes > lastHistoryNewestOffset) {
                    lastHistoryNewestOffset = entry.offsetMinutes
                    lastHistoryNewestGlucose = entry.glucoseMgDl
                }
            } catch (t: Throwable) {
                Log.e(TAG, "storeHistoryEntries: aidexProcessData failed: $t")
                break  // Don't keep hammering a broken JNI
            }

            // Progressive sync to Room DB every HISTORY_SYNC_BATCH_SIZE entries.
            // Uses forceFullSyncForSensor (DELETE + re-insert) to bypass the
            // 30-second throttle in syncFromNative(), matching vendor driver Edit 73.
            // This allows the chart to show data incrementally during large downloads
            // instead of waiting until the entire history is fetched.
            if (historyStoredCount % HISTORY_SYNC_BATCH_SIZE == 0) {
                try {
                    tk.glucodata.HistorySyncAccess.forceFullSyncForSensor(SerialNumber)
                    Log.i(TAG, "Progressive sync at $historyStoredCount entries")
                } catch (_: Throwable) {}
            }
        }

        if (stored > 0) {
            noteValidReadingAvailable(newestStoredTimeMs, "valid-history")
            Log.i(TAG, "storeHistoryEntries: stored $stored/${entries.size} entries (total=$historyStoredCount)")
        }
    }

    /**
     * Called when all history pages (both raw and brief) have been downloaded.
     */
    private fun onHistoryDownloadComplete() {
        handler.removeCallbacks(historyPageWatchdog)  // History done — cancel any pending page timeout
        historyDownloading = false
        historyPhase = HistoryPhase.IDLE

        // Log any remaining cached 0x23 entries that had no matching 0x24
        if (calibratedGlucoseCache.isNotEmpty()) {
            Log.i(TAG, "History complete: ${calibratedGlucoseCache.size} cached 0x23 entries had no matching 0x24")
            calibratedGlucoseCache.clear()
        }
        lastCalibratedGlucoseFallback = null

        Log.i(TAG, "History download complete. Total entries stored: $historyStoredCount")

        // Final sync to Room DB
        if (historyStoredCount > 0) {
            try {
                tk.glucodata.HistorySyncAccess.forceFullSyncForSensor(SerialNumber)
            } catch (t: Throwable) {
                Log.e(TAG, "HistorySync.forceFullSyncForSensor failed: $t")
                // Fallback
                try {
                    tk.glucodata.HistorySyncAccess.syncSensorFromNative(SerialNumber, forceFull = true)
                } catch (_: Throwable) {}
            }
        }

        // Catch-up broadcast: notify xDrip/Watchdrip of the newest history record
        // so the stream resumes immediately without waiting for the next F003 (~60s).
        // Uses manual pack (not aidexProcessData) to avoid double-writing data
        // that was already stored by storeHistoryEntries().
        if (AiDexHistoryPolicy.shouldEmitCatchUpBroadcast(
                lastHistoryNewestGlucose = lastHistoryNewestGlucose,
                lastHistoryNewestOffset = lastHistoryNewestOffset,
                liveOffsetCutoff = liveOffsetCutoff,
            )
        ) {
            val mgdlInt = lastHistoryNewestGlucose.toInt().coerceIn(0, 0xFFFF) * 10
            val catchUpTimestamp = sensorstartmsec + (lastHistoryNewestOffset.toLong() * 60_000L)
            handleGlucoseResult(mgdlInt.toLong() and 0xFFFFFFFFL, catchUpTimestamp)
            Log.i(TAG, "History catch-up broadcast: glucose=$lastHistoryNewestGlucose offset=$lastHistoryNewestOffset")
        }
        lastHistoryNewestGlucose = 0f
        lastHistoryNewestOffset = 0

        // Request calibration range
        handler.postDelayed({
            val cmd = commandBuilder.getCalibrationRange()
            if (cmd != null) {
                enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
            }
            scheduleStreamingMetadataRead("post-history")
        }, 200L)
    }

    // =========================================================================
    // Phase Management
    // =========================================================================

    private fun setPhase(newPhase: Phase) {
        if (phase == newPhase) return
        phase = newPhase
        Log.i(TAG, "Phase: $newPhase")
        onPhaseChange?.invoke(newPhase)
    }

    private fun applyWearProfileFromModel(modelName: String) {
        val normalized = modelName.trim().uppercase(java.util.Locale.US)
        _wearDays = when {
            normalized.startsWith("GX-01S") -> 15
            else -> 15
        }
        Log.i(TAG, "Wear profile: model=$modelName days=$_wearDays")
    }

    // =========================================================================
    // AiDexDriver Interface Implementation
    // =========================================================================

    override fun getDetailedBleStatus(): String {
        val now = System.currentTimeMillis()

        fun connectedWarmupStatus(connectionPart: String): String? {
            return AiDexRuntimePolicy.connectedWarmupStatus(
                connectionPart = connectionPart,
                anchorMs = effectiveWarmupAnchorMs(),
                nowMs = now,
                lastGlucoseTimeMs = lastGlucoseTimeMs,
                warmupDurationMs = WARMUP_DURATION_MS,
                firstValidReadingWaitMaxMs = FIRST_VALID_READING_WAIT_MAX_MS,
                firstValidReadingWaitActive = firstValidReadingAnchorMs > 0L,
            )
        }

        return when (phase) {
            Phase.IDLE -> {
                if (reconnect.isBroadcastOnlyMode) {
                    if (broadcastScanActive) "Scanning for broadcasts..."
                    else if (lastBroadcastTime > 0 && (now - lastBroadcastTime) < 5 * 60_000L)
                        "Broadcast Mode — Receiving"
                    else "Broadcast Mode"
                }
                else if (isUnpaired) "Unpaired — tap Pair to reconnect"
                else if (stop) "Paused"
                else constatstatusstr ?: "Disconnected"
            }
            Phase.GATT_CONNECTING -> "Connecting..."
            Phase.DISCOVERING_SERVICES -> {
                val bondState = mBluetoothGatt?.device?.bondState
                    ?: android.bluetooth.BluetoothDevice.BOND_NONE
                when (bondState) {
                    android.bluetooth.BluetoothDevice.BOND_BONDING -> "Bonding..."
                    android.bluetooth.BluetoothDevice.BOND_NONE -> "Pairing..."
                    else -> "Discovering services..."
                }
            }
            Phase.CCCD_CHAIN -> "Configuring notifications..."
            Phase.KEY_EXCHANGE -> "Key exchange..."
            Phase.STREAMING -> {
                if (reconnect.isBroadcastOnlyMode) return "Broadcast Mode"

                // Transient status (calibration result, etc.) takes priority
                transientStatusMessage?.let { return it }

                // History download in progress — show phase and progress
                if (historyDownloading) {
                    val toDownload = historyNewestOffset - historyDownloadStartIndex
                    return when (historyPhase) {
                        HistoryPhase.DOWNLOADING_CALIBRATED -> {
                            val cached = calibratedGlucoseCache.size
                            if (toDownload > 0 && cached > 0)
                                "Fetching history... $cached/$toDownload"
                            else
                                "Fetching history..."
                        }
                        HistoryPhase.DOWNLOADING_RAW -> {
                            if (toDownload > 0 && historyStoredCount > 0)
                                "Storing history... $historyStoredCount/$toDownload"
                            else
                                "Storing history..."
                        }
                        HistoryPhase.IDLE -> "Fetching history..."
                    }
                }

                connectedWarmupStatus("Connected")?.let { return it }

                // Normal connected state
                "Connected"
            }
        }
    }

    override val isPaused: Boolean get() = _isPaused || stop

    override val broadcastOnlyConnection: Boolean get() = reconnect.isBroadcastOnlyMode

    override fun isVendorPaired(): Boolean = keyExchange.isComplete

    override fun isVendorConnected(): Boolean = phase == Phase.STREAMING && mBluetoothGatt != null

    override fun getCalibrationRecords(): List<SharedCalibrationRecord> =
        _calibrationRecords.sortedByDescending { it.index }

    override fun getBatteryMillivolts(): Int = _batteryMillivolts

    override fun isSensorExpired(): Boolean = _sensorExpired

    override fun getSensorRemainingHours(): Int {
        if (sensorstartmsec <= 0L) return -1
        val elapsedMs = System.currentTimeMillis() - sensorstartmsec
        val totalMs = _wearDays.toLong() * 24 * 60 * 60 * 1000
        val remainingMs = totalMs - elapsedMs
        return if (remainingMs <= 0) 0 else (remainingMs / (60 * 60 * 1000)).toInt()
    }

    override fun getSensorAgeHours(): Int {
        if (sensorstartmsec <= 0L) return -1
        val elapsedMs = System.currentTimeMillis() - sensorstartmsec
        return (elapsedMs / (60 * 60 * 1000)).toInt()
    }

    override var vendorFirmwareVersion: String
        get() = _firmwareVersion
        set(value) { _firmwareVersion = value }

    override var vendorHardwareVersion: String
        get() = _hardwareVersion
        set(value) { _hardwareVersion = value }

    override var vendorModelName: String
        get() = _modelName
        set(value) { _modelName = value }

    override fun forgetVendor() {
        Log.i(TAG, "forgetVendor: tearing down native driver for $SerialNumber")
        stop = true
        cancelBroadcastScan()
        keyExchange.reset()
        // Notify C++ layer that this sensor is being removed — prevents zombie resurrection
        try { finishSensor() } catch (_: Throwable) {}
        // Capture device reference BEFORE nullifying gatt
        val device = mBluetoothGatt?.device
        // Disconnect and close GATT
        try { mBluetoothGatt?.disconnect() } catch (_: Throwable) {}
        try { mBluetoothGatt?.close() } catch (_: Throwable) {}
        mBluetoothGatt = null
        // Remove BLE bond via reflection
        try {
            if (device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                val removeBond = device.javaClass.getMethod("removeBond")
                removeBond.invoke(device)
                Log.i(TAG, "forgetVendor: BLE bond removed")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "forgetVendor: removeBond failed: ${t.message}")
        }
        setPhase(Phase.IDLE)
        AiDexDriver.deviceListDirty = true
    }

    override fun softDisconnect() {
        Log.i(TAG, "softDisconnect: pausing sensor $SerialNumber")
        _isPaused = true  // Block external reconnection triggers (LossOfSensorAlarm, reconnectall)
        stop = true  // Prevent auto-reconnect from disconnect handler
        cancelBroadcastScan()  // Stop active BLE scanner and cancel scheduled scans
        handler.removeCallbacksAndMessages(null)   // Cancel pending reconnects/timeouts
        close()  // SuperGattCallback.close() does disconnect + close + nulls mBluetoothGatt
        setPhase(Phase.IDLE)
        // NOTE: callers set constatstatusstr after calling softDisconnect()
        // (e.g., "Paused", "Unpaired", "Broadcast Only", "Pairing cancelled")
    }

    override fun manualReconnectNow() {
        Log.i(TAG, "manualReconnectNow: forcing reconnect for $SerialNumber")
        cancelBroadcastScan()
        _isPaused = false   // Clear paused flag — user explicitly wants reconnection
        isUnpaired = false // Clear unpaired flag — user explicitly wants reconnection
        reconnect.reset()  // Clears isBroadcastOnlyMode + authFailureCount
        stop = false
        connectDevice(0L)
        AiDexDriver.deviceListDirty = true
    }

    override fun setBroadcastOnlyConnection(enabled: Boolean) {
        Log.i(TAG, "setBroadcastOnlyConnection($enabled) for $SerialNumber")
        reconnect.isBroadcastOnlyMode = enabled
        if (enabled) {
            // Disconnect GATT, start broadcast scanning
            softDisconnect()
            constatstatusstr = "Broadcast Only"
            stop = false
            UiRefreshBus.requestStatusRefresh()
            // Start broadcast scan loop
            handler.post { startBroadcastScan("broadcast-mode-enabled") }
        } else {
            // Stop broadcast scanning, resume active GATT connection
            cancelBroadcastScan()
            manualReconnectNow()
        }
    }

    override fun resetSensor(): Boolean {
        Log.i(TAG, "resetSensor: CLEAR_STORAGE (0xF3) then RESET (0xF0) for $SerialNumber")

        // Step 0: Build CLEAR_STORAGE command (requires session key)
        val cmd = commandBuilder.clearStorage() ?: run {
            Log.e(TAG, "resetSensor: session key not available")
            return false
        }

        // Reset history indices — new sensor means history starts from scratch
        historyRawNextIndex = 0
        historyBriefNextIndex = 0
        writeIntPref("historyRawNextIndex", 0)
        writeIntPref("historyBriefNextIndex", 0)
        liveOffsetCutoff = 0
        Log.i(TAG, "resetSensor: history indices reset to 0")

        // Set flags BEFORE sending commands — the disconnect handler checks pendingResetReconnect,
        // and handleCGMSessionStartTime checks needsPostResetActivation on the next connection
        pendingResetReconnect = true
        needsPostResetActivation = true
        writeBoolPref("needsPostResetActivation", true)

        // Step 1: Send CLEAR_STORAGE (0xF3)
        // Step 2 (RESET 0xF0) is sent from handleClearStorageResponse() when
        // the sensor acknowledges the clear.
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
        return true
    }

    override fun startNewSensor(): Boolean {
        Log.i(TAG, "startNewSensor: activating sensor $SerialNumber")
        autoActivationAttemptedThisConnection = true
        hasAuthoritativeSessionStart = false
        armFirstValidReadingWait(System.currentTimeMillis(), "start-new-sensor")

        // Reset history indices — new sensor means history starts from scratch
        historyRawNextIndex = 0
        historyBriefNextIndex = 0
        writeIntPref("historyRawNextIndex", 0)
        writeIntPref("historyBriefNextIndex", 0)
        liveOffsetCutoff = 0
        calibratedGlucoseCache.clear()
        lastCalibratedGlucoseFallback = null
        Log.i(TAG, "startNewSensor: history indices and caches reset")

        val cal = Calendar.getInstance(TimeZone.getDefault())
        val tz = TimeZone.getDefault()
        val tzOffsetMs = tz.getOffset(cal.timeInMillis)
        val tzQuarters = (tzOffsetMs / (15 * 60 * 1000))
        val dstMs = tz.getDSTSavings()
        val dstQuarters = if (tz.inDaylightTime(cal.time)) (dstMs / (15 * 60 * 1000)) else 0

        activateSensor(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            second = cal.get(Calendar.SECOND),
            tzQuarters = tzQuarters,
            dstQuarters = dstQuarters,
        )
        return true
    }

    override fun calibrateSensor(glucoseMgDl: Int): Boolean {
        Log.i(TAG, "calibrateSensor($glucoseMgDl mg/dL) for $SerialNumber")

        // Guard: session key must be available (encryption required)
        if (!keyExchange.isComplete) {
            Log.e(TAG, "calibrateSensor: session key not available (handshake not complete)")
            onCalibrationResult?.invoke(false, "Cannot calibrate: not connected to sensor")
            return false
        }

        // Guard: must not be in warmup
        val warmupAnchor = effectiveWarmupAnchorMs()
        if (warmupAnchor > 0L) {
            val warmupEndMs = warmupAnchor + WARMUP_DURATION_MS
            val now = System.currentTimeMillis()
            if (now < warmupEndMs) {
                val remainingSec = ((warmupEndMs - now) / 1000).toInt()
                Log.e(TAG, "calibrateSensor: sensor is warming up (${remainingSec}s remaining)")
                onCalibrationResult?.invoke(false, "Cannot calibrate: sensor warming up ($remainingSec seconds remaining)")
                return false
            }
        }

        // Guard: must have current offset
        if (lastOffsetMinutes <= 0) {
            Log.e(TAG, "calibrateSensor: no offset available yet")
            onCalibrationResult?.invoke(false, "Cannot calibrate: no sensor offset available yet")
            return false
        }

        // Guard: glucose must be in valid range
        if (glucoseMgDl < 30 || glucoseMgDl > 500) {
            Log.e(TAG, "calibrateSensor: glucose $glucoseMgDl out of range (30-500)")
            onCalibrationResult?.invoke(false, "Cannot calibrate: glucose value $glucoseMgDl out of range (30-500 mg/dL)")
            return false
        }

        sendCalibration(lastOffsetMinutes, glucoseMgDl)
        showTransientStatus("Calibrating...", 10_000L)  // Show until ACK arrives (or 10s timeout)
        return true
    }

    override fun unpairSensor(): Boolean {
        Log.i(TAG, "unpairSensor: sending deleteBond (0xF2) for $SerialNumber")
        isUnpaired = true  // Block reconnection permanently until re-pair
        val cmd = commandBuilder.deleteBond() ?: run {
            Log.e(TAG, "unpairSensor: session key not available — disconnecting without 0xF2")
            // Even without session key, disconnect and remove bond
            try {
                val device = mBluetoothGatt?.device
                if (device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                    val removeBond = device.javaClass.getMethod("removeBond")
                    removeBond.invoke(device)
                }
            } catch (_: Throwable) {}
            keyExchange.reset()
            softDisconnect()
            constatstatusstr = "Unpaired — Broadcast Only"
            // Transition to broadcast-only mode so user keeps getting data
            reconnect.isBroadcastOnlyMode = true
            stop = false
            UiRefreshBus.requestStatusRefresh()
            handler.post { startBroadcastScan("post-unpair") }
            return true
        }
        // Set flag BEFORE sending — the response handler (or disconnect handler)
        // will perform bond removal + disconnect after the command is delivered.
        pendingUnpairDisconnect = true
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
        constatstatusstr = "Unpairing..."
        UiRefreshBus.requestStatusRefresh()
        return true
    }

    override fun rePairSensor() {
        Log.i(TAG, "rePairSensor: resetting key exchange and reconnecting for $SerialNumber")
        keyExchange.reset()
        softDisconnect()
        constatstatusstr = "Re-pairing..."
        UiRefreshBus.requestStatusRefresh()
        handler.postDelayed({
            _isPaused = false   // Clear paused flag — user explicitly wants re-pair
            isUnpaired = false // Clear unpaired flag — user explicitly wants re-pair
            reconnect.reset()  // Clear broadcast-only fallback/auth-failure state before pairing again
            stop = false
            connectDevice(500L)
        }, 1000L)
    }

    override fun sendMaintenanceCommand(opCode: Int): Boolean {
        Log.i(TAG, "sendMaintenanceCommand(0x${"%02X".format(opCode)}) for $SerialNumber")
        val cmd = commandBuilder.buildEncrypted(opCode) ?: run {
            Log.e(TAG, "sendMaintenanceCommand: session key not available")
            return false
        }
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
        return true
    }

    override val resetCompensationEnabled: Boolean get() = _resetCompensationEnabled

    override fun enableResetCompensation() {
        Log.i(TAG, "enableResetCompensation for $SerialNumber")
        _resetCompensationEnabled = true
    }

    override fun disableResetCompensation() {
        Log.i(TAG, "disableResetCompensation for $SerialNumber")
        _resetCompensationEnabled = false
    }

    override fun getCompensationStatusText(): String {
        return if (_resetCompensationEnabled) "Enabled (native driver)" else ""
    }

    override var viewMode: Int
        get() = _viewModeInternal
        set(value) { _viewModeInternal = value }

    // =========================================================================
    // Broadcast Scanning
    // =========================================================================

    /**
     * Runnable that starts a broadcast scan. Posted with delay for periodic scanning.
     */
    private val broadcastScanRunnable = Runnable { startBroadcastScan("scheduled") }

    /**
     * Runnable that stops a broadcast scan after the scan window expires.
     */
    private val broadcastScanStopRunnable = Runnable {
        stopBroadcastScan("timeout", found = false)
    }

    /**
     * Start a BLE scan for broadcast advertisements from this sensor.
     * Used in broadcast-only mode (after unpair, or manual broadcast mode).
     *
     * Ported from AiDexSensor.kt:startBroadcastScan().
     */
    @SuppressLint("MissingPermission")
    private fun hasRecentLiveData(now: Long = System.currentTimeMillis()): Boolean {
        return (lastGlucoseTimeMs > 0L && (now - lastGlucoseTimeMs) < BROADCAST_FALLBACK_LIVE_TIMEOUT_MS) ||
            hasRecentBroadcastData(now)
    }

    private fun startBroadcastScan(reason: String, continuous: Boolean = reconnect.isBroadcastOnlyMode) {
        if (broadcastScanActive) return

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return

        val scanner = adapter.bluetoothLeScanner ?: return
        broadcastScanner = scanner

        if (broadcastScanCallback == null) {
            broadcastScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val address = device.address
                    val targetAddress = mActiveDeviceAddress

                    // Filter to our sensor's address only
                    if (targetAddress != null && address != targetAddress) return

                    val scanRecord = result.scanRecord?.bytes ?: return
                    parseScanRecord(scanRecord, result.rssi)
                }

                override fun onBatchScanResults(results: List<ScanResult>) {
                    results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "Broadcast scan failed: $errorCode")
                    stopBroadcastScan("scan-failed-$errorCode", found = false)
                }
            }
        }

        // Build filters: device address if known, otherwise open scan
        val filterBuilder = ScanFilter.Builder()
        val targetAddr = mActiveDeviceAddress
        if (targetAddr != null) {
            filterBuilder.setDeviceAddress(targetAddr)
        }
        val filters = arrayListOf(filterBuilder.build())

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner.startScan(filters, settings, broadcastScanCallback)
            broadcastScanActive = true
            broadcastScanContinuousMode = continuous
            handler.removeCallbacks(broadcastScanStopRunnable)
            val windowMs = currentBroadcastScanWindowMs(continuous)
            handler.postDelayed(broadcastScanStopRunnable, windowMs)
            Log.i(TAG, "Broadcast scan started ($reason)")
            BatteryTrace.bump(
                key = "aidex.broadcast_scan.start",
                logEvery = 10L,
                detail = "reason=$reason continuous=$continuous windowMs=$windowMs misses=$broadcastScanMisses"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast scan start failed: ${e.message}")
        }
    }

    /**
     * Stop the active broadcast scan and optionally schedule the next one.
     */
    @SuppressLint("MissingPermission")
    private fun stopBroadcastScan(reason: String, found: Boolean) {
        if (broadcastScanActive) {
            try {
                broadcastScanner?.stopScan(broadcastScanCallback)
            } catch (_: Throwable) {}
            broadcastScanActive = false
            Log.d(TAG, "Broadcast scan stopped ($reason, found=$found)")
            BatteryTrace.bump(
                key = "aidex.broadcast_scan.stop",
                logEvery = 10L,
                detail = "reason=$reason found=$found misses=$broadcastScanMisses"
            )
        }

        broadcastScanMisses = if (found) 0 else (broadcastScanMisses + 1)

        // Schedule next scan if still in broadcast-only mode
        if (broadcastScanContinuousMode && reconnect.isBroadcastOnlyMode && !stop) {
            scheduleBroadcastScan("post-$reason")
        } else if (!broadcastScanContinuousMode && !found && shouldContinueAssistScanning()) {
            handler.removeCallbacks(broadcastAssistRunnable)
            handler.postDelayed(broadcastAssistRunnable, BROADCAST_ASSIST_SCAN_DELAY_MS)
            Log.i(TAG, "Broadcast assist scan rescheduled after $reason (${firstValidReadingWaitStatus() ?: "waiting"})")
        }
    }

    /**
     * Cancel all broadcast scan scheduling.
     */
    private fun cancelBroadcastScan() {
        handler.removeCallbacks(broadcastScanRunnable)
        handler.removeCallbacks(broadcastScanStopRunnable)
        if (broadcastScanActive) {
            try {
                broadcastScanner?.stopScan(broadcastScanCallback)
            } catch (_: Throwable) {}
            broadcastScanActive = false
        }
        broadcastScanContinuousMode = false
    }

    /**
     * Schedule the next broadcast scan with appropriate delay.
     */
    private fun scheduleBroadcastScan(reason: String) {
        if (!reconnect.isBroadcastOnlyMode || stop) return

        var delay = BROADCAST_SCAN_INTERVAL_MS
        // Retry aggressively (15s) for a few attempts if we're missing broadcasts
        if (delay > 15_000L && broadcastScanMisses in 1..5) {
            delay = 15_000L
        }

        handler.removeCallbacks(broadcastScanRunnable)
        handler.postDelayed(broadcastScanRunnable, delay)
        Log.d(TAG, "Broadcast scan scheduled in ${delay / 1000}s ($reason, misses=$broadcastScanMisses)")
    }

    private fun currentBroadcastScanWindowMs(continuous: Boolean, now: Long = System.currentTimeMillis()): Long {
        if (!continuous) return BROADCAST_ASSIST_SCAN_WINDOW_MS
        return if (broadcastScanMisses > 0 || !hasRecentLiveData(now)) {
            BROADCAST_RECOVERY_SCAN_WINDOW_MS
        } else {
            BROADCAST_SCAN_WINDOW_MS
        }
    }

    /**
     * Parse scan record from BLE advertisement to extract manufacturer data.
     * AiDex broadcast format (in Manufacturer Specific Data, type 0xFF):
     *   bytes 0..3 : u32 LE timeOffsetMinutes
     *   byte 4     : i8 trend
     *   bytes 5..6 : packed glucose mg/dL (10-bit: lo | (carry & 0x03) << 8)
     *
     * Ported from AiDexSensor.kt:onScanRecord() + parseBroadcastData().
     */
    private fun parseScanRecord(scanRecord: ByteArray, rssi: Int) {
        var offset = 0
        while (offset < scanRecord.size - 2) {
            val len = scanRecord[offset].toInt() and 0xFF
            if (len == 0) break
            val type = scanRecord[offset + 1].toInt() and 0xFF

            if (type == 0xFF) {  // Manufacturer Specific Data
                if (offset + 3 < scanRecord.size) {
                    val dataLen = len - 3
                    if (dataLen >= 6 && offset + 4 + dataLen <= scanRecord.size) {
                        val data = ByteArray(dataLen)
                        System.arraycopy(scanRecord, offset + 4, data, 0, dataLen)
                        handleBroadcastPayload(data)
                    }
                }
            }
            offset += len + 1
        }
    }

    /**
     * Parse and store broadcast glucose payload.
     */
    private fun handleBroadcastPayload(data: ByteArray) {
        if (data.size < 7) return

        val now = System.currentTimeMillis()

        val offsetMinutes = u32LE(data, 0) and 0xFFFF_FFFFL
        val trend = data[4].toInt()
        val lo = data[5].toInt() and 0xFF
        val carry = data[6].toInt() and 0xFF
        val glucoseMgDlInt = lo or ((carry and 0x03) shl 8)

        Log.i(
            TAG,
            "BROADCAST sample: offset=${offsetMinutes}min glucose=$glucoseMgDlInt mg/dL trend=$trend " +
                "inRange=${glucoseMgDlInt in MIN_VALID_GLUCOSE_MGDL..MAX_VALID_GLUCOSE_MGDL}"
        )
        if (glucoseMgDlInt !in MIN_VALID_GLUCOSE_MGDL..MAX_VALID_GLUCOSE_MGDL) {
            return
        }

        lastBroadcastGlucose = glucoseMgDlInt.toFloat()
        lastBroadcastTime = now

        if (
            offsetMinutes == lastBroadcastOffsetSeen &&
            glucoseMgDlInt == lastBroadcastGlucoseSeen &&
            trend == lastBroadcastTrendSeen &&
            (now - lastBroadcastOffsetSeenAtMs) < BROADCAST_DUPLICATE_SUPPRESS_MS
        ) {
            Log.d(
                TAG,
                "BROADCAST duplicate suppressed: offset=${offsetMinutes}min glucose=$glucoseMgDlInt trend=$trend " +
                    "ageMs=${now - lastBroadcastOffsetSeenAtMs}"
            )
            return
        }
        lastBroadcastOffsetSeen = offsetMinutes
        lastBroadcastGlucoseSeen = glucoseMgDlInt
        lastBroadcastTrendSeen = trend
        lastBroadcastOffsetSeenAtMs = now

        // Update offset tracking
        lastOffsetMinutes = offsetMinutes.toInt()
        ensureSensorStartTime(now)

        val fallbackActive = reconnect.isBroadcastOnlyMode || !hasRecentLiveData(now)
        if (!fallbackActive) {
            return
        }

        // Update notification status
        if (reconnect.isBroadcastOnlyMode) {
            constatstatusstr = "Receiving"
        }

        // Dedup: don't store if too soon after last stored broadcast
        if (lastBroadcastStoredTime != 0L && (now - lastBroadcastStoredTime) < BROADCAST_MIN_STORE_INTERVAL_MS) {
            return
        }

        // Store via JNI
        if (dataptr != 0L) {
            try {
                val res = Natives.aidexProcessData(
                    dataptr, byteArrayOf(0), now,
                    glucoseMgDlInt.toFloat(), 0f, 1.0f
                )
                handleGlucoseResult(res, now)
                noteValidReadingAvailable(now, "valid-broadcast")
                lastBroadcastStoredTime = now
                maybeRequestHistoryContinuitySyncAfterLive(now, "broadcast")
            } catch (e: Throwable) {
                Log.e(TAG, "Broadcast: aidexProcessData failed: $e")
                val mgdlPacked = (glucoseMgDlInt * 10).toLong() and 0xFFFFFFFFL
                handleGlucoseResult(mgdlPacked, now)
                noteValidReadingAvailable(now, "valid-broadcast-fallback")
                maybeRequestHistoryContinuitySyncAfterLive(now, "broadcast-fallback")
            }
        } else {
            val mgdlPacked = (glucoseMgDlInt * 10).toLong() and 0xFFFFFFFFL
            handleGlucoseResult(mgdlPacked, now)
            noteValidReadingAvailable(now, "valid-broadcast-no-dataptr")
            maybeRequestHistoryContinuitySyncAfterLive(now, "broadcast-no-dataptr")
        }

        // Stop current scan, schedule next
        stopBroadcastScan("broadcast-received", found = true)
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    /**
     * Release all resources. Call when sensor is removed from gattcallbacks list.
     */
    override fun destroy() {
        stop = true
        cancelBroadcastScan()
        close()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }

    // broadcastOnlyConnection is implemented as a property (line ~984) via AiDexDriver interface

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun u16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32LE(data: ByteArray, offset: Int): Long {
        return (data[offset].toInt() and 0xFF).toLong() or
                ((data[offset + 1].toInt() and 0xFF).toLong() shl 8) or
                ((data[offset + 2].toInt() and 0xFF).toLong() shl 16) or
                ((data[offset + 3].toInt() and 0xFF).toLong() shl 24)
    }
}
