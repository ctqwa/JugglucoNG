// MQBleManager.kt — BLE manager for MQ/Glutec CGM (Nordic UART Service).
//
// Lifecycle:
//   1) connectDevice → GATT connect
//   2) onConnectionStateChange(CONNECTED) → discoverServices
//   3) onServicesDiscovered → find NUS, enable notifications on TX char
//   4) onCharacteristicChanged(TX) → MQParser.parse → dispatch by cmd
//      • 0x06 NOTIFY_BEGIN_WORK  → write confirmWithInit  (fresh session)
//      • 0x01 NOTIFY_WORKING     → write confirmWithoutInit (heartbeat)
//      • 0x04 NOTIFY_BG_DATA     → parse BG records, run vendor math, confirm
//
// The 0x04 records carry packet index + sample current, not final glucose.
// We only emit a glucose when we have enough state to reproduce the vendor
// calculation honestly; otherwise we keep the session alive but do not invent
// a reading.

package tk.glucodata.drivers.mq

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import java.util.Locale
import java.util.UUID
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.SuperGattCallback
import tk.glucodata.UiRefreshBus

@SuppressLint("MissingPermission")
class MQBleManager(
    serial: String,
    dataptr: Long,
) : SuperGattCallback(serial, dataptr, SENSOR_GEN), MQDriver {

    companion object {
        private const val TAG = MQConstants.TAG

        /** SuperGattCallback generation tag — keep at 0 (same as iCan/AiDex). */
        const val SENSOR_GEN = 0

        /** Reconnect active sessions aggressively instead of waiting for generic polling. */
        private const val ACTIVE_SESSION_RECONNECT_DELAY_MS = 2_000L

        /** No-data watchdog multiplier. Real cadence is profile-controlled. */
        private const val NO_DATA_WATCHDOG_MULTIPLIER = 4L

        /** Give the reset frame time to leave Android before we drop GATT. */
        private const val RESET_RECONNECT_DELAY_MS = 700L
    }

    enum class Phase { IDLE, CONNECTING, DISCOVERING, STREAMING }

    @Volatile var phase: Phase = Phase.IDLE
        private set

    private val handlerThread = HandlerThread("MQ-$serial").also { it.start() }
    private val handler = Handler(handlerThread.looper)

    private var nusService: BluetoothGattService? = null
    private var charTxNotify: BluetoothGattCharacteristic? = null
    private var charRxWrite: BluetoothGattCharacteristic? = null

    private val profile = MQProfileResolver.resolve()

    // Per-sensor config snapshot (populated in restoreFromPersistence).
    @Volatile private var protocolType: Int = MQConstants.SERVER_DEFAULT_PROTOCOL_TYPE
    @Volatile private var deviation: Int = MQConstants.SERVER_DEFAULT_DEVIATION
    @Volatile private var transmitter10: Int = MQConstants.SERVER_DEFAULT_TRANSMITTER10
    @Volatile private var sensitivitySeed: Float = 0f
    @Volatile private var algorithmVersion: Int = MQConstants.ALGO_DEFAULT_VERSION
    @Volatile private var kValue: Float = 0f
    @Volatile private var bValue: Float = 0f
    @Volatile private var multiplier: Float = MQConstants.ALGO_DEFAULT_MULTIPLIER.toFloat()
    @Volatile private var packages: Int = MQConstants.ALGO_DEFAULT_PACKAGES

    // CRC variant learned from the transmitter's first frame.
    //   0x0000 = Protocol01 (vanilla CRC16-Modbus)
    //   0x0100 = Protocol02 (Modbus ^ 0x0100 — observed on W25101399)
    // We seed it from the cached protocolType but override as soon as we see
    // the transmitter's first frame so we don't ship CRCs the firmware rejects.
    @Volatile private var crcXorOut: Int = 0
    @Volatile private var crcXorOutLearned: Boolean = false

    // Session state (derived while we run).
    @Volatile private var warmupStartedAtMs: Long = 0L
    @Volatile private var sensorStartAtMs: Long = 0L
    @Volatile private var packetCount: Int = 0
    @Volatile private var pendingReferenceBgTimes10Mmol: Double = 0.0
    @Volatile private var lastPacketIndex: Int = -1
    @Volatile private var lastRecordReceivedAtMs: Long = 0L
    @Volatile private var lastRawCurrent: Double = 0.0
    @Volatile private var lastProcessed: Double = 0.0
    @Volatile private var lastBatteryPercent: Int = -1
    @Volatile private var lastGlucoseAtMs: Long = 0L
    @Volatile private var lastGlucoseMgdlTimes10: Int = 0
    @Volatile private var vendorModelNameInternal: String = MQConstants.DEFAULT_DISPLAY_NAME
    @Volatile private var vendorFirmwareVersionInternal: String = ""
    @Volatile private var reconnectReason: String = ""
    @Volatile private var bootstrapFetchInFlight: Boolean = false
    @Volatile private var lastBootstrapAttemptAtMs: Long = 0L
    @Volatile private var lastBootstrapFailure: MQBootstrapFailure = MQBootstrapFailure.NONE
    @Volatile private var lastBootstrapMessage: String = ""
    @Volatile private var localResetPending: Boolean = false

    override var viewMode: Int = 0

    private val reconnectRunnable = Runnable {
        if (stop) return@Runnable
        Log.i(TAG, "Reconnect requested: $reconnectReason")
        connectDevice(0)
    }

    private val noDataWatchdog = Runnable {
        if (stop || phase != Phase.STREAMING) return@Runnable
        val lastReadingMs = lastGlucoseAtMs
        if (lastReadingMs <= 0L) return@Runnable
        val elapsedMs = System.currentTimeMillis() - lastReadingMs
        if (elapsedMs < noDataWatchdogMs()) {
            armNoDataWatchdog()
            return@Runnable
        }
        Log.w(TAG, "No MQ glucose for ${elapsedMs / 1000}s — forcing reconnect")
        mActiveBluetoothDevice = null
        runCatching { mBluetoothGatt?.disconnect() }
        scheduleReconnect("No-data watchdog", ACTIVE_SESSION_RECONNECT_DELAY_MS)
    }

    // ---- Persistence ----

    fun restoreFromPersistence(context: Context) {
        val id = SerialNumber ?: return
        protocolType = MQRegistry.loadProtocolType(context, id)
        deviation = MQRegistry.loadDeviation(context, id)
        transmitter10 = MQRegistry.loadTransmitter10(context, id)
        warmupStartedAtMs = MQRegistry.loadWarmupStartedAt(context, id)
        sensorStartAtMs = MQRegistry.loadSensorStartAt(context, id)
        sensitivitySeed = MQRegistry.loadSensitivity(context, id)
        algorithmVersion = MQRegistry.loadAlgorithmVersion(context, id)
        kValue = MQRegistry.loadKValue(context, id)
        bValue = MQRegistry.loadBValue(context, id)
        localResetPending = MQRegistry.loadLocalResetPending(context, id)
        if (!hasValidSlopeSeed(kValue.toDouble()) && kValue != 0f) {
            Log.w(TAG, "Discarding invalid persisted MQ K seed=$kValue for $id")
            kValue = 0f
            bValue = 0f
            MQRegistry.saveKValue(context, id, 0f)
            MQRegistry.saveBValue(context, id, 0f)
        }
        multiplier = MQRegistry.loadMultiplier(context, id)
        packages = MQRegistry.loadPackages(context, id)
        lastProcessed = MQRegistry.loadLastProcessed(context, id).toDouble().coerceAtLeast(0.0)
        lastPacketIndex = MQRegistry.loadLastPacketIndex(context, id)
        crcXorOut = if (protocolType == 2) 0x0100 else 0x0000
        if (hasUsableSlopeSeed()) {
            if (localResetPending) {
                clearLocalResetPending("restored-valid-k")
            }
            lastBootstrapFailure = MQBootstrapFailure.NONE
            lastBootstrapMessage = ""
        }
        if (!hasUsableSlopeSeed()) {
            maybeRefreshBootstrapAsync(context, "restore")
        }
    }

    private fun persistSensorStart(context: Context, timestamp: Long) {
        val id = SerialNumber ?: return
        MQRegistry.saveSensorStartAt(context, id, timestamp)
    }

    private fun persistWarmupStart(context: Context, timestamp: Long) {
        val id = SerialNumber ?: return
        MQRegistry.saveWarmupStartedAt(context, id, timestamp)
    }

    private fun noDataWatchdogMs(): Long =
        NO_DATA_WATCHDOG_MULTIPLIER * profile.readingIntervalMinutes * 60L * 1000L

    private fun armNoDataWatchdog() {
        handler.removeCallbacks(noDataWatchdog)
        if (lastGlucoseAtMs > 0L) {
            handler.postDelayed(noDataWatchdog, noDataWatchdogMs())
        }
    }

    private fun cancelReconnect() {
        handler.removeCallbacks(reconnectRunnable)
        reconnectReason = ""
    }

    private fun scheduleReconnect(reason: String, delayMs: Long = ACTIVE_SESSION_RECONNECT_DELAY_MS) {
        if (stop) return
        reconnectReason = reason
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun nativeCreationSensorName(sensorId: String): String =
        MQConstants.canonicalSensorId(sensorId)

    private fun resolveExistingNativeSensorName(sensorId: String): String? {
        val canonical = nativeCreationSensorName(sensorId)
        if (canonical.isBlank() || MQConstants.isProvisionalSensorId(canonical)) {
            return null
        }
        runCatching { Natives.activeSensors() }
            .getOrNull()
            ?.firstOrNull { MQConstants.matchesCanonicalOrKnownNativeAlias(it, canonical) }
            ?.takeIf { !it.isNullOrBlank() }
            ?.let { return it }
        runCatching { Natives.lastsensorname() }
            .getOrNull()
            ?.takeIf { MQConstants.matchesCanonicalOrKnownNativeAlias(it, canonical) }
            ?.let { return it }
        return null
    }

    private fun resolveNativeSensorPtr(sensorId: String): Long {
        if (sensorId.isBlank() || MQConstants.isProvisionalSensorId(sensorId)) {
            return 0L
        }
        if (dataptr != 0L) {
            runCatching { Natives.getsensorptr(dataptr) }
                .getOrNull()
                ?.takeIf { it != 0L }
                ?.let { return it }
        }
        val nativeName = resolveExistingNativeSensorName(sensorId) ?: return 0L
        return runCatching { Natives.str2sensorptr(nativeName) }.getOrDefault(0L)
    }

    private fun applyNativeSensorMetadata(nativeName: String = nativeCreationSensorName(SerialNumber)) {
        if (nativeName.isBlank()) return
        if (dataptr != 0L && !mActiveDeviceAddress.isNullOrEmpty()) {
            runCatching { Natives.setDeviceAddress(dataptr, mActiveDeviceAddress) }
                .onFailure { Log.stack(TAG, "applyNativeSensorMetadata(setDeviceAddress)", it) }
            runCatching { Natives.unfinishSensor(dataptr) }
                .onFailure { Log.stack(TAG, "applyNativeSensorMetadata(unfinishSensor)", it) }
        }
        val current = runCatching { Natives.lastsensorname() }.getOrNull()
        if (current.isNullOrBlank() || MQConstants.matchesCanonicalOrKnownNativeAlias(current, SerialNumber)) {
            runCatching { Natives.setcurrentsensor(nativeName) }
                .onFailure { Log.stack(TAG, "applyNativeSensorMetadata(setcurrentsensor)", it) }
        }
        if (sensorStartAtMs > 0L) {
            sensorstartmsec = sensorStartAtMs
        } else {
            val sensorPtr = resolveNativeSensorPtr(SerialNumber)
            if (sensorPtr != 0L) {
                sensorstartmsec = runCatching { Natives.getSensorStartmsecFromSensorptr(sensorPtr) }
                    .getOrDefault(sensorstartmsec)
            }
        }
    }

    private fun ensureNativeDataptr(sensorId: String) {
        val canonical = nativeCreationSensorName(sensorId)
        if (canonical.isBlank() || MQConstants.isProvisionalSensorId(canonical)) {
            return
        }
        val startSec = if (sensorStartAtMs > 0L) sensorStartAtMs / 1000L else 0L
        runCatching { Natives.ensureSensorShell(canonical, startSec) }
            .onFailure { Log.stack(TAG, "ensureNativeDataptr(ensureSensorShell)", it) }
        if (dataptr == 0L) {
            dataptr = runCatching { Natives.getdataptr(canonical) }.getOrDefault(0L)
        }
        applyNativeSensorMetadata(canonical)
    }

    private fun mirrorReadingIntoNative(sampleMs: Long, glucoseMgdl: Int) {
        if (sampleMs <= 0L || glucoseMgdl <= 0 || SerialNumber.isBlank()) {
            return
        }
        val nativeName = nativeCreationSensorName(SerialNumber)
        runCatching {
            ensureNativeDataptr(SerialNumber)
            // Native direct-stream storage multiplies the float by 10 internally.
            Natives.addGlucoseStream(sampleMs / 1000L, glucoseMgdl / 10f, nativeName)
            applyNativeSensorMetadata(nativeName)
            Natives.wakebackup()
        }.onFailure { Log.stack(TAG, "mirrorReadingIntoNative", it) }
    }

    // ---- ManagedBluetoothSensorDriver / MQDriver overrides ----

    override fun matchesManagedSensorId(sensorId: String?): Boolean =
        MQConstants.matchesCanonicalOrKnownNativeAlias(SerialNumber, sensorId)

    override fun hasNativeSensorBacking(): Boolean =
        SerialNumber.isNotBlank() && resolveExistingNativeSensorName(SerialNumber) != null
    override fun shouldUseNativeHistorySync(): Boolean = false

    override fun getDetailedBleStatus(): String = when (phase) {
        Phase.IDLE -> if (mActiveBluetoothDevice == null) "Searching" else "Idle"
        Phase.CONNECTING -> "Connecting"
        Phase.DISCOVERING -> "Discovering services"
        Phase.STREAMING -> when {
            lastGlucoseAtMs > 0L -> "Connected"
            bootstrapFetchInFlight -> "Refreshing MQ login/bootstrap"
            lastBootstrapFailure == MQBootstrapFailure.AUTH_EXPIRED -> "MQ login expired"
            lastBootstrapFailure == MQBootstrapFailure.SERVER -> "MQ bootstrap failed"
            lastRecordReceivedAtMs > 0L && !hasUsableSlopeSeed() -> "MQ restore session or calibrate"
            else -> "Warmup"
        }
    }

    override fun getStartTimeMs(): Long = sensorStartAtMs
    override fun getOfficialEndMs(): Long =
        if (sensorStartAtMs > 0L) sensorStartAtMs + profile.ratedLifetimeMs() else 0L
    override fun getExpectedEndMs(): Long = getOfficialEndMs()

    override fun isSensorExpired(): Boolean {
        val end = getOfficialEndMs()
        return end > 0L && System.currentTimeMillis() >= end
    }

    override fun getSensorRemainingHours(): Int {
        val end = getOfficialEndMs()
        if (end <= 0L) return -1
        val remaining = end - System.currentTimeMillis()
        return if (remaining <= 0L) 0 else ((remaining + 30 * 60 * 1000L) / (60L * 60 * 1000L)).toInt()
    }

    override fun getSensorAgeHours(): Int {
        if (sensorStartAtMs <= 0L) return -1
        val age = System.currentTimeMillis() - sensorStartAtMs
        if (age <= 0L) return 0
        return ((age + 30 * 60 * 1000L) / (60L * 60 * 1000L)).toInt()
    }

    override fun getReadingIntervalMinutes(): Int = profile.readingIntervalMinutes

    override fun calibrateSensor(glucoseMgDl: Int): Boolean {
        if (glucoseMgDl <= 0) return false
        pendingReferenceBgTimes10Mmol = (glucoseMgDl / 18.0182) * 10.0
        return true
    }

    override val vendorFirmwareVersion: String get() = vendorFirmwareVersionInternal
    override val vendorModelName: String get() = vendorModelNameInternal
    override val batteryMillivolts: Int
        get() = if (lastBatteryPercent < 0) 0 else lastBatteryPercent * 30 // crude %→mV for UI

    override fun getCurrentSnapshot(maxAgeMillis: Long): MQCurrentSnapshot? {
        if (lastGlucoseAtMs == 0L) return null
        val age = System.currentTimeMillis() - lastGlucoseAtMs
        if (age > maxAgeMillis) return null
        return MQCurrentSnapshot(
            timeMillis = lastGlucoseAtMs,
            glucoseValue = lastGlucoseMgdlTimes10 / 10f,
            rawValue = lastRawCurrent.toFloat(),
            rate = Float.NaN,
            sensorGen = sensorgen,
        )
    }

    override fun removeManagedPersistence(context: Context) {
        MQRegistry.removeSensor(context, SerialNumber)
    }

    override fun softDisconnect() {
        setPause(true)
        cancelReconnect()
        handler.removeCallbacks(noDataWatchdog)
        phase = Phase.IDLE
        runCatching { close() }
            .onFailure { Log.stack(TAG, "softDisconnect(close)", it) }
        mActiveBluetoothDevice = null
        UiRefreshBus.requestStatusRefresh()
    }

    override fun softReconnect() {
        setPause(false)
        cancelReconnect()
        handler.removeCallbacks(noDataWatchdog)
        if (dataptr != 0L) {
            runCatching { Natives.unfinishSensor(dataptr) }
                .onFailure { Log.stack(TAG, "softReconnect(unfinishSensor)", it) }
        }
        runCatching { close() }
            .onFailure { Log.stack(TAG, "softReconnect(close)", it) }
        phase = Phase.IDLE
        handler.postDelayed({
            if (!stop) connectDevice(0)
        }, 250L)
        UiRefreshBus.requestStatusRefresh()
    }

    override fun terminateManagedSensor(wipeData: Boolean) {
        setPause(true)
        cancelReconnect()
        handler.removeCallbacks(noDataWatchdog)
        phase = Phase.IDLE
        val sensorPtr = resolveNativeSensorPtr(SerialNumber)
        runCatching { mBluetoothGatt?.disconnect() }
            .onFailure { Log.stack(TAG, "terminateManagedSensor(disconnect)", it) }
        if (sensorPtr != 0L) {
            runCatching { Natives.finishfromSensorptr(sensorPtr) }
                .onFailure { Log.stack(TAG, "terminateManagedSensor(finishfromSensorptr)", it) }
        }
        dataptr = 0L
        runCatching { close() }
            .onFailure { Log.stack(TAG, "terminateManagedSensor(close)", it) }
    }

    override fun resetSensor(): Boolean {
        val frame = when {
            protocolType == 2 || crcXorOut == 0x0100 -> MagicAck.reset00.copyOf()
            else -> MQParser.buildConfirmReset(0)
        }
        if (!writeFrameNow(frame, "confirmReset")) {
            Log.w(TAG, "MQ reset rejected: GATT write path not ready")
            return false
        }
        markLocalResetPending()
        handler.postDelayed({
            if (!stop) {
                runCatching { mBluetoothGatt?.disconnect() }
            }
        }, RESET_RECONNECT_DELAY_MS)
        UiRefreshBus.requestStatusRefresh()
        return true
    }

    // ---- BLE lifecycle ----

    override fun getService(): UUID = MQConstants.NUS_SERVICE

    override fun matchDeviceName(deviceName: String?, address: String?): Boolean {
        val trimmed = deviceName?.trim().orEmpty()
        val knownAddress = mActiveDeviceAddress?.takeIf { it.isNotBlank() }
        if (knownAddress != null && address != null && address.equals(knownAddress, ignoreCase = true)) return true
        if (trimmed.isEmpty()) return false
        val advertisedCanonical = MQConstants.canonicalSensorId(trimmed)
        if (advertisedCanonical.equals(SerialNumber, ignoreCase = true)) return true
        return MQConstants.isMqDevice(trimmed)
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (stop) return
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.i(TAG, "Connected to ${gatt.device?.address}")
                cancelReconnect()
                handler.removeCallbacks(noDataWatchdog)
                mBluetoothGatt = gatt
                mActiveBluetoothDevice = gatt.device
                connectTime = System.currentTimeMillis()
                phase = Phase.DISCOVERING
                handler.postDelayed({
                    try {
                        if (!gatt.discoverServices()) {
                            Log.e(TAG, "discoverServices() returned false")
                        }
                    } catch (t: Throwable) {
                        Log.stack(TAG, "discoverServices", t)
                    }
                }, 250)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.i(TAG, "Disconnected (status=$status)")
                phase = Phase.IDLE
                charTxNotify = null
                charRxWrite = null
                nusService = null
                mActiveBluetoothDevice = null
                try { gatt.close() } catch (_: Throwable) {}
                mBluetoothGatt = null
                handler.removeCallbacksAndMessages(null)
                if (!stop) {
                    scheduleReconnect("GATT disconnected (status=$status)")
                }
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "Service discovery failed status=$status")
            runCatching { gatt.disconnect() }
            return
        }
        val service = gatt.getService(MQConstants.NUS_SERVICE)
        if (service == null) {
            Log.e(TAG, "NUS service not present on ${gatt.device?.address}")
            runCatching { gatt.disconnect() }
            return
        }
        nusService = service
        charTxNotify = service.getCharacteristic(MQConstants.NUS_TX_NOTIFY)
        charRxWrite = service.getCharacteristic(MQConstants.NUS_RX_WRITE)
        val tx = charTxNotify
        if (tx == null) {
            Log.e(TAG, "NUS TX characteristic missing")
            runCatching { gatt.disconnect() }
            return
        }
        if (!enableNotification(gatt, tx)) {
            Log.e(TAG, "Failed to enable TX notifications")
        } else {
            Log.i(TAG, "TX notifications enabled")
            phase = Phase.STREAMING
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (characteristic.uuid != MQConstants.NUS_TX_NOTIFY) return
        val data = characteristic.value ?: return
        val frame = MQParser.parse(data)
        if (frame == null) {
            logRejectedFrame(data)
            return
        }
        learnCrcVariant(frame)
        when (frame.cmd) {
            MQConstants.CMD_NOTIFY_BEGIN_WORK -> onBeginWork()
            MQConstants.CMD_NOTIFY_WORKING -> onHeartbeat()
            MQConstants.CMD_NOTIFY_BG_DATA -> onBgData(frame)
            MQConstants.CMD_NOTIFY_BG_COMPLETE -> {
                // Session-continuation marker from the transmitter after it
                // accepts our 0x02 ack. No response required.
            }
            else -> Log.d(TAG, "Unhandled cmd 0x${"%02X".format(frame.cmdUnsigned)}")
        }
    }

    /**
     * Infer the firmware's CRC variant from the first incoming frame.
     *
     * We recompute Modbus over the frame body and XOR the result with the
     * observed trailing bytes. If the XOR is 0x0000 the firmware is Protocol01;
     * if it's 0x0100 it's the Protocol02 variant observed on W25101399. Any
     * other value gets logged so we can add it to the table later.
     */
    private fun learnCrcVariant(frame: MQFrame) {
        if (crcXorOutLearned) return
        val body = frame.raw
        if (body.size < 2) return
        val computed = MQCrc16.compute(body, 0, body.size - 2)
        val observed = ((body[body.size - 2].toInt() and 0xFF) shl 8) or
            (body[body.size - 1].toInt() and 0xFF)
        val xorOut = (computed xor observed) and 0xFFFF
        crcXorOut = xorOut
        crcXorOutLearned = true
        Log.i(TAG, "Learned CRC variant: xorOut=0x%04X (computed=0x%04X observed=0x%04X)"
            .format(xorOut, computed, observed))
    }

    /**
     * Emit everything we know about a rejected frame so we can figure out
     * whether it's a header mismatch, CRC endianness flip, or an unknown
     * protocol variant. Prints hex, ASCII-ish view, and three CRC candidates.
     */
    private fun logRejectedFrame(data: ByteArray) {
        val hex = data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        val hdrOk = data.size >= 2 &&
            data[0] == MQConstants.HEADER0 && data[1] == MQConstants.HEADER1
        val cmd = if (data.size > MQConstants.OFFSET_CMD)
            "0x%02X".format(data[MQConstants.OFFSET_CMD].toInt() and 0xFF) else "?"
        val len = if (data.size > MQConstants.OFFSET_LEN)
            data[MQConstants.OFFSET_LEN].toInt() and 0xFF else -1
        val expectedSize = if (len >= 0) MQConstants.FRAME_OVERHEAD + len else -1

        // CRC candidates over all bytes except the last two.
        val computed = if (data.size >= 2) MQCrc16.compute(data, 0, data.size - 2) else 0
        val tailHi = if (data.size >= 2) data[data.size - 2].toInt() and 0xFF else 0
        val tailLo = if (data.size >= 2) data[data.size - 1].toInt() and 0xFF else 0
        val tailBE = (tailHi shl 8) or tailLo
        val tailLE = (tailLo shl 8) or tailHi

        Log.w(
            TAG,
            "Rejected frame (${data.size} B): [$hex] " +
                "hdrOk=$hdrOk cmd=$cmd len=$len expSize=$expectedSize " +
                "crcComputed=0x%04X tailBE=0x%04X tailLE=0x%04X".format(computed, tailBE, tailLE)
        )
    }

    // ---- Frame handlers ----

    /**
     * Protocol02 pre-baked ack bytes, reverse-engineered from the official
     * Glutec app's HCI btsnoop log on transmitter W25101399. These are NOT
     * computed CRCs — they're hardcoded byte sequences in the app's Protocol02
     * table. Our own CRC code will never reproduce them.
     *
     * In the log, the transmitter got stuck in a BEGIN_WORK loop for 103s while
     * the app cycled through Modbus and Modbus^0x0100 variants. The loop broke
     * only after a fresh BLE reconnection followed by ONE write of
     * {5A A5 08 01 00 4A 6F}, which caused the transmitter to advance to
     * heartbeat (0x01) within 1.8 seconds.
     */
    private object MagicAck {
        // confirmWithInit (cmd=0x08, payload=0x00) — the unlock ack.
        val withInit00: ByteArray = byteArrayOf(0x5A, -0x5B, 0x08, 0x01, 0x00, 0x4A, 0x6F)
        // bgDataConfirm (cmd=0x02, payload=0x00) — observed 21x in official log.
        val bgData00: ByteArray = byteArrayOf(0x5A, -0x5B, 0x02, 0x01, 0x00, 0x49, 0x7F)
        // confirmWithoutInit (cmd=0x03, payload=0x00) — observed (Modbus^0x0100 form).
        val withoutInit00: ByteArray = byteArrayOf(0x5A, -0x5B, 0x03, 0x01, 0x00, 0x50, -0x51)
        // confirmReset (cmd=0x11, payload=0x00) — fixed Protocol02 bytes from the official app.
        val reset00: ByteArray = byteArrayOf(0x5A, -0x5B, 0x11, 0x01, 0x00, -0x56, 0x07)
    }

    private data class AckCandidate(
        val label: String,
        val cmd: Byte,
        val payload: Byte,
        val useLearnedXor: Boolean,
    )

    @Volatile private var beginWorkRetryCount: Int = 0
    @Volatile private var beginWorkFirstSeenMs: Long = 0L
    /** After this many 0x06 pings without the transmitter advancing, drop the
     *  connection and let SuperGattCallback reconnect fresh. This is what the
     *  official app did in the wild — 103s of failures then a fresh connect
     *  unlocked the state machine. */
    private val MAX_BEGIN_WORK_RETRIES_BEFORE_RECONNECT = 5

    /**
     * Once the transmitter advances past 0x06 we freeze the ack convention
     * (payload byte + CRC xor) that got us there, and reuse it for all
     * subsequent acks (heartbeat/BG). The command byte still comes from the
     * per-frame mapping (0x03 for heartbeat, 0x02 for BG data).
     */
    @Volatile private var learnedAckPayload: Byte = 0x00
    @Volatile private var learnedAckUseLearnedXor: Boolean = true
    @Volatile private var ackConventionLocked: Boolean = false
    @Volatile private var lastBeginWorkCandidate: AckCandidate? = null

    private fun buildAck(c: AckCandidate): ByteArray {
        val xor = if (c.useLearnedXor) crcXorOut else 0x0000
        val frame = byteArrayOf(
            MQConstants.HEADER0,
            MQConstants.HEADER1,
            c.cmd,
            0x01, // LEN
            c.payload,
            0x00, 0x00, // CRC placeholder
        )
        return MQCrc16.stamp(frame, xor)
    }

    private fun onBeginWork() {
        val now = System.currentTimeMillis()
        if (sensorStartAtMs == 0L) {
            sensorStartAtMs = now
            sensorstartmsec = now
            warmupStartedAtMs = now
            packetCount = 0
            Applic.app?.let {
                persistSensorStart(it, now)
                persistWarmupStart(it, now)
            }
            ensureNativeDataptr(SerialNumber)
            Log.i(TAG, "Sensor begin work — session start marked")
        }
        Applic.app?.takeIf { !hasUsableSlopeSeed() }?.let {
            maybeRefreshBootstrapAsync(it, "begin-work")
        }
        if (beginWorkFirstSeenMs == 0L) beginWorkFirstSeenMs = now
        beginWorkRetryCount++

        // If we've sent several acks and the transmitter is still echoing 0x06,
        // the GATT state is wedged. Drop and reconnect — in the official app's
        // log this is exactly what broke the loop.
        if (beginWorkRetryCount > MAX_BEGIN_WORK_RETRIES_BEFORE_RECONNECT) {
            Log.w(TAG, "BEGIN_WORK looped ${beginWorkRetryCount}x — forcing GATT reconnect")
            beginWorkRetryCount = 0
            beginWorkFirstSeenMs = 0L
            runCatching { mBluetoothGatt?.disconnect() }
            return
        }

        // Send the Protocol02 magic ack — hardcoded bytes from the official app's
        // HCI log. This is what unlocks the transmitter on a fresh connection.
        val lastAck = AckCandidate("withInit/00/magic", MQConstants.CMD_WRITE_CONFIRM_WITH_INIT, 0x00, true)
        lastBeginWorkCandidate = lastAck
        Log.i(TAG, "BEGIN_WORK ack #$beginWorkRetryCount — Protocol02 magic (withInit/00)")
        writeFrame(MagicAck.withInit00.copyOf(), "beginWorkAck(magic)")
    }

    /**
     * Called when we observe the transmitter advance past BEGIN_WORK (i.e. it
     * sends a 0x01 heartbeat or 0x04 BG data). The last-tried candidate is
     * what worked — freeze its payload-byte and CRC-variant for all future
     * acks so we don't rotate away from a working shape.
     */
    private fun lockAckConventionIfNeeded() {
        if (ackConventionLocked) return
        val c = lastBeginWorkCandidate ?: return
        learnedAckPayload = c.payload
        learnedAckUseLearnedXor = c.useLearnedXor
        ackConventionLocked = true
        Log.i(
            TAG,
            "Locked ack convention: payload=0x%02X crcXorOut=0x%04X (from %s)".format(
                c.payload.toInt() and 0xFF,
                if (c.useLearnedXor) crcXorOut else 0x0000,
                c.label,
            ),
        )
    }

    private fun buildLockedAck(cmd: Byte, tag: String): ByteArray {
        val candidate = AckCandidate(tag, cmd, learnedAckPayload, learnedAckUseLearnedXor)
        return buildAck(candidate)
    }

    private fun onHeartbeat() {
        beginWorkRetryCount = 0
        beginWorkFirstSeenMs = 0L
        armNoDataWatchdog()
        // Protocol02 magic — computed CRC would be rejected (see HCI log analysis).
        writeFrame(MagicAck.withoutInit00.copyOf(), "confirmWithoutInit(magic)")
    }

    private fun persistAlgorithmState() {
        val context = Applic.app ?: return
        val id = SerialNumber ?: return
        MQRegistry.saveKValue(context, id, kValue)
        MQRegistry.saveBValue(context, id, bValue)
        MQRegistry.saveLastProcessed(context, id, lastProcessed.toFloat())
        MQRegistry.saveLastPacketIndex(context, id, lastPacketIndex)
    }

    private fun clearLocalResetPending(reason: String) {
        if (!localResetPending) return
        localResetPending = false
        val context = Applic.app ?: return
        MQRegistry.saveLocalResetPending(context, SerialNumber, false)
        Log.i(TAG, "Cleared local MQ reset pending ($reason)")
    }

    private fun markLocalResetPending() {
        localResetPending = true
        val context = Applic.app
        val id = SerialNumber ?: return
        clearVolatileSessionState()
        if (context != null) {
            MQRegistry.clearRuntimeState(
                context = context,
                sensorId = id,
                markLocalResetPending = true,
            )
        }
        lastBootstrapFailure = MQBootstrapFailure.NONE
        lastBootstrapMessage = ""
        Log.i(TAG, "Marked local MQ reset pending for $id")
    }

    private fun clearVolatileSessionState() {
        sensorStartAtMs = 0L
        sensorstartmsec = 0L
        warmupStartedAtMs = 0L
        packetCount = 0
        pendingReferenceBgTimes10Mmol = 0.0
        lastPacketIndex = -1
        lastRecordReceivedAtMs = 0L
        lastRawCurrent = 0.0
        lastProcessed = 0.0
        lastGlucoseAtMs = 0L
        lastGlucoseMgdlTimes10 = 0
        beginWorkRetryCount = 0
        beginWorkFirstSeenMs = 0L
        learnedAckPayload = 0x00
        learnedAckUseLearnedXor = true
        ackConventionLocked = false
        lastBeginWorkCandidate = null
        kValue = 0f
        bValue = 0f
    }

    private fun hasValidSlopeSeed(value: Double): Boolean =
        value > MQConstants.ALGO_MIN_VALID_K

    private fun hasUsableSlopeSeed(): Boolean =
        hasValidSlopeSeed(kValue.toDouble())

    private fun maybeRefreshBootstrapAsync(context: Context, reason: String) {
        val id = SerialNumber ?: return
        val now = System.currentTimeMillis()
        if (bootstrapFetchInFlight || now - lastBootstrapAttemptAtMs < MQConstants.VENDOR_BOOTSTRAP_RETRY_MS) {
            return
        }
        val qrCode = MQRegistry.loadQrContent(context, id)?.trim().orEmpty().takeIf { it.isNotEmpty() }
        val bleId = mActiveDeviceAddress?.takeIf { it.isNotBlank() }
            ?: MQRegistry.findRecord(context, id)?.address?.takeIf { it.isNotBlank() }
        if (qrCode == null && bleId == null) return
        bootstrapFetchInFlight = true
        lastBootstrapAttemptAtMs = now
        handler.post {
            try {
                val result = MQBootstrapClient.fetchBestEffort(
                    context = context,
                    bleId = bleId,
                    qrCode = qrCode,
                    authToken = MQRegistry.loadAuthToken(context),
                    credentials = MQRegistry.loadAuthCredentials(context),
                    allowContinueWearRestore = !localResetPending,
                )
                result.refreshedToken?.let { MQRegistry.saveAuthToken(context, it) }
                lastBootstrapFailure = result.failure
                lastBootstrapMessage = result.message.orEmpty()
                if (result.config != null) {
                    MQRegistry.applyBootstrapConfig(context, id, result.config)
                    restoreFromPersistence(context)
                    if (hasUsableSlopeSeed()) {
                        lastBootstrapFailure = MQBootstrapFailure.NONE
                        lastBootstrapMessage = ""
                    }
                    Log.i(
                        TAG,
                        "Applied MQ bootstrap ($reason): sensitivity=$sensitivitySeed k=$kValue b=$bValue packet=$lastPacketIndex algo=$algorithmVersion packages=$packages multiplier=$multiplier",
                    )
                } else {
                    Log.w(TAG, "MQ bootstrap unavailable for $id ($reason): ${result.message}")
                }
            } catch (t: Throwable) {
                Log.stack(TAG, "maybeRefreshBootstrapAsync($reason)", t)
            } finally {
                bootstrapFetchInFlight = false
            }
        }
    }

    private fun calculateInitTimeMinutes(sampleMs: Long): Double {
        val startAt = sensorStartAtMs.takeIf { it > 0L } ?: return MQConstants.ALGO_DEFAULT_INIT_TIME_MINUTES
        val elapsedMs = sampleMs - startAt
        return if (elapsedMs > 0L) {
            elapsedMs / 60_000.0
        } else {
            MQConstants.ALGO_DEFAULT_INIT_TIME_MINUTES
        }
    }

    private fun calculateVendorGlucose(rec: MQBgRecord, sampleMs: Long): MQAlgorithm.Result? {
        val reference = pendingReferenceBgTimes10Mmol.takeIf { it > 0.0 && rec.packetIndex > 8 } ?: 0.0
        val seedK = kValue.toDouble()
        if (!hasValidSlopeSeed(seedK) && reference <= 0.0) {
            return null
        }
        val initTimeMinutes = calculateInitTimeMinutes(sampleMs)
        val previousProcessed = lastProcessed
        val result = MQAlgorithm.calculateResult(
            algorithmVersion = algorithmVersion,
            initTimeMinutes = initTimeMinutes,
            packetIndex = rec.packetIndex.toDouble(),
            sampleCurrent = rec.sampleCurrent.toDouble(),
            previousReviseCurrent2 = previousProcessed,
            kValue = seedK,
            referenceBgTimes10Mmol = reference,
            bValue = bValue.toDouble(),
            packages = packages.toDouble(),
            multiplier = multiplier.toDouble(),
        )
        lastProcessed = result.reviseCurrent2
        if (hasValidSlopeSeed(result.kValue)) {
            kValue = result.kValue.toFloat()
        }
        bValue = result.bValue.toFloat()
        if (reference > 0.0) {
            pendingReferenceBgTimes10Mmol = 0.0
        }
        persistAlgorithmState()
        if (result.glucoseTimes10Mmol > 0 && hasValidSlopeSeed(result.kValue)) {
            clearLocalResetPending("live-calculated-k")
        }
        Log.d(
            TAG,
            String.format(
                Locale.US,
                "Calculated packet=%d initMin=%.1f current=%d prev=%.1f revise=%.1f k=%.2f b=%.2f mmol=%.1f",
                rec.packetIndex,
                initTimeMinutes,
                rec.sampleCurrent,
                previousProcessed,
                result.reviseCurrent2,
                result.kValue,
                result.bValue,
                result.glucoseMmol,
            ),
        )
        return if (result.glucoseTimes10Mmol > 0) result else null
    }

    private fun onBgData(frame: MQFrame) {
        beginWorkRetryCount = 0
        beginWorkFirstSeenMs = 0L
        val records = MQParser.parseBgRecords(frame)
        if (records.isEmpty()) {
            writeFrame(MagicAck.bgData00.copyOf(), "confirmBgData(empty,magic)")
            return
        }
        Applic.app?.takeIf { !hasUsableSlopeSeed() }?.let {
            maybeRefreshBootstrapAsync(it, "bg-data")
        }
        val nowMs = System.currentTimeMillis()
        if (sensorStartAtMs == 0L) {
            sensorStartAtMs = nowMs
            sensorstartmsec = nowMs
            warmupStartedAtMs = nowMs
            Applic.app?.let {
                persistSensorStart(it, nowMs)
                persistWarmupStart(it, nowMs)
            }
        }
        ensureNativeDataptr(SerialNumber)
        val intervalMs = profile.readingIntervalMinutes * 60L * 1000L
        val previousLastPacketIndex = lastPacketIndex
        var highestPacketIndexSeen = lastPacketIndex
        // The transmitter buffers the most recent record first; each subsequent
        // record is `intervalMs` older.
        for (rec in records) {
            if (previousLastPacketIndex >= 0 && rec.packetIndex <= previousLastPacketIndex) {
                Log.i(TAG, "Skipping already-seen MQ packet=${rec.packetIndex} (last=$previousLastPacketIndex)")
                continue
            }
            packetCount++
            highestPacketIndexSeen = maxOf(highestPacketIndexSeen, rec.packetIndex)
            lastRawCurrent = rec.sampleCurrent.toDouble()
            lastBatteryPercent = rec.batteryPercent

            val rb = rec.recordBytes

            val recHex = rb.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            val sampleMs = nowMs - rec.indexInPacket * intervalMs
            lastRecordReceivedAtMs = maxOf(lastRecordReceivedAtMs, sampleMs)
            Log.i(
                TAG,
                "BG record #${rec.indexInPacket}: [$recHex]  marker=0x%02X  packet=%d  current=%d  battery=%d%%".format(
                    rec.marker,
                    rec.packetIndex,
                    rec.sampleCurrent,
                    rec.batteryPercent,
                )
            )
            if (rec.marker != MQConstants.BG_RECORD_MARKER) {
                Log.w(TAG, "Ignoring MQ BG record with unexpected marker 0x%02X".format(rec.marker))
                continue
            }
            val result = calculateVendorGlucose(rec, sampleMs)
            if (result == null) {
                lastPacketIndex = highestPacketIndexSeen
                persistAlgorithmState()
                Log.w(
                    TAG,
                    "Skipping glucose emit for packet=${rec.packetIndex}: current=${rec.sampleCurrent} seedK=%.2f b=%.2f pendingRef=%.1f".format(
                        kValue,
                        bValue,
                        pendingReferenceBgTimes10Mmol,
                    ),
                )
                continue
            }
            if (sampleMs >= lastGlucoseAtMs) {
                lastGlucoseAtMs = sampleMs
                lastGlucoseMgdlTimes10 = result.mgdlTimes10
            }
            mirrorReadingIntoNative(sampleMs, result.mgdlTimes10 / 10)
            emitGlucose(result, sampleMs)
        }
        if (highestPacketIndexSeen != lastPacketIndex) {
            lastPacketIndex = highestPacketIndexSeen
            persistAlgorithmState()
        }
        armNoDataWatchdog()
        writeFrame(MagicAck.bgData00.copyOf(), "confirmBgData(magic)")
    }

    private fun emitGlucose(result: MQAlgorithm.Result, sampleMs: Long) {
        val alarm = 0L
        val rateShort = 0  // we don't compute trend rate yet
        val mgdlTimes10 = result.mgdlTimes10.toLong() and 0xFFFFFFFFL
        val res = (alarm shl 48) or ((rateShort.toLong() and 0xFFFF) shl 32) or mgdlTimes10
        try {
            handleGlucoseResult(res, sampleMs)
        } catch (t: Throwable) {
            Log.stack(TAG, "emitGlucose", t)
        }
    }

    /**
     * Write a framed packet to the RX characteristic. The official Glutec app
     * writes fast enough that the first confirm can collide with the CCCD-write
     * ack, so Android sometimes returns false on the first attempt. We pick the
     * best writeType for the characteristic's advertised properties, then retry
     * once after a short delay if the initial write is rejected.
     */
    private fun writeFrame(bytes: ByteArray, tag: String) {
        attemptWrite(bytes, tag, attempt = 1)
    }

    private fun writeFrameNow(bytes: ByteArray, tag: String): Boolean {
        val gatt = mBluetoothGatt ?: return false
        val ch = charRxWrite ?: return false
        return try {
            val props = ch.properties
            val supportsNoResp = props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val supportsResp = props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            ch.writeType = when {
                supportsNoResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                supportsResp -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            ch.value = bytes
            val ok = gatt.writeCharacteristic(ch)
            val hex = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            if (ok) {
                Log.d(TAG, "wrote $tag [$hex] (immediate writeType=${ch.writeType})")
            } else {
                Log.w(TAG, "writeCharacteristic($tag) returned false (immediate bytes=[$hex])")
            }
            ok
        } catch (t: Throwable) {
            Log.stack(TAG, "writeFrameNow($tag)", t)
            false
        }
    }

    private fun attemptWrite(bytes: ByteArray, tag: String, attempt: Int) {
        val gatt = mBluetoothGatt ?: return
        val ch = charRxWrite ?: run {
            Log.w(TAG, "writeFrame($tag): RX characteristic not ready (attempt=$attempt)")
            return
        }
        try {
            val props = ch.properties
            val supportsNoResp = props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val supportsResp = props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            ch.writeType = when {
                supportsNoResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                supportsResp -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // try anyway
            }
            ch.value = bytes
            val ok = gatt.writeCharacteristic(ch)
            val hex = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            if (!ok) {
                Log.w(
                    TAG,
                    "writeCharacteristic($tag) returned false " +
                        "(attempt=$attempt props=0x%02X writeType=%d bytes=[%s])"
                            .format(props, ch.writeType, hex),
                )
                if (attempt < 3) {
                    val delay = 150L * attempt
                    handler.postDelayed({ attemptWrite(bytes, tag, attempt + 1) }, delay)
                }
            } else {
                Log.d(TAG, "wrote $tag [$hex] (attempt=$attempt writeType=${ch.writeType})")
            }
        } catch (t: Throwable) {
            Log.stack(TAG, "writeFrame($tag)", t)
        }
    }

    override fun close() {
        phase = Phase.IDLE
        try { handlerThread.quitSafely() } catch (_: Throwable) {}
        super.close()
    }
}
