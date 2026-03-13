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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import tk.glucodata.Log
import tk.glucodata.SuperGattCallback
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
    private val mainHandler = Handler(Looper.getMainLooper())

    // -- GATT Queue --
    private sealed class GattOp {
        var retryCount: Int = 0
        data class Write(val charUuid: UUID, val data: ByteArray) : GattOp()
        data class Read(val charUuid: UUID) : GattOp()
    }

    private val gattQueue = ArrayDeque<GattOp>()
    @Volatile private var gattOpActive = false
    @Volatile private var queuePausedForBonding = false

    // -- CCCD State --
    private var servicesReady = false
    private var cccdQueue = ArrayDeque<UUID>() // Characteristics to enable notifications on
    private var cccdWriteInProgress = false
    private var cccdChainComplete = false

    // -- Key Exchange State --
    private var challengeWritten = false
    private var bondDataRead = false
    private var keyExchangePendingBond = false

    // -- History State --
    @Volatile private var historyDownloading = false
    private var historyRawNextIndex = 0
    private var historyBriefNextIndex = 0
    private var historyNewestOffset = 0

    // -- F003 Live Data --
    private var lastGlucoseTimeMs: Long = 0L
    private var lastOffsetMinutes: Int = 0

    // -- AiDexDriver State --
    @Volatile private var _batteryMillivolts: Int = 0
    @Volatile private var _sensorExpired: Boolean = false
    @Volatile private var _wearDays: Int = 14  // default 14-day sensor
    @Volatile private var _firmwareVersion: String = ""
    @Volatile private var _hardwareVersion: String = ""
    @Volatile private var _modelName: String = ""
    @Volatile private var _calibrationRecords: List<SharedCalibrationRecord> = emptyList()
    @Volatile private var _viewModeInternal: Int = 0
    @Volatile private var _resetCompensationEnabled: Boolean = false

    // -- Listeners --
    /** Called when a live glucose reading is parsed from F003. */
    var onGlucoseReading: ((GlucoseReading) -> Unit)? = null

    /** Called when calibrated history entries are parsed from 0x23. */
    var onCalibratedHistory: ((List<CalibratedHistoryEntry>) -> Unit)? = null

    /** Called when ADC history entries are parsed from 0x24. */
    var onAdcHistory: ((List<AdcHistoryEntry>) -> Unit)? = null

    /** Called when calibration records are parsed from 0x27. */
    var onCalibrationRecords: ((List<CalibrationRecord>) -> Unit)? = null

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

    // =========================================================================
    // GATT Callbacks
    // =========================================================================

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            val now = System.currentTimeMillis()
            connectTime = now
            constatstatusstr = "Connected"
            setPhase(Phase.DISCOVERING_SERVICES)

            // Reset per-connection state
            keyExchange.reset()
            challengeWritten = false
            bondDataRead = false
            servicesReady = false
            cccdChainComplete = false
            keyExchangePendingBond = false
            postCccdStreamingPending = false
            gattQueue.clear()
            gattOpActive = false
            queuePausedForBonding = false
            cccdQueue.clear()
            cccdWriteInProgress = false

            Log.i(TAG, "Connected to ${gatt.device?.address}. Requesting MTU 512...")
            gatt.requestMtu(512)

            // Schedule service discovery after MTU exchange
            mainHandler.postDelayed({
                if (mBluetoothGatt != null && !servicesReady) {
                    Log.i(TAG, "Discovering services...")
                    mBluetoothGatt?.discoverServices()
                    scheduleDiscoveryRetries(gatt)
                }
            }, MTU_DELAY_MS)

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "Disconnected. status=$status")
            constatstatusstr = "Disconnected"
            connectTime = 0L
            setPhase(Phase.IDLE)

            // Clear GATT state
            gattQueue.clear()
            gattOpActive = false
            queuePausedForBonding = false
            servicesReady = false
            cccdChainComplete = false
            keyExchangePendingBond = false
            postCccdStreamingPending = false
            historyDownloading = false

            // Handle specific failure cases
            when (status) {
                5 -> { // GATT_INSUFFICIENT_AUTHENTICATION
                    val delay = reconnect.nextAuthFailureDelayMs()
                    if (delay != null) {
                        Log.i(TAG, "Auth failure — reconnecting in ${delay}ms (attempt ${reconnect.authFailureCount})")
                        close()
                        handler.postDelayed({ connectDevice(0) }, delay)
                    } else {
                        Log.w(TAG, "Auth failures exhausted — broadcast-only mode")
                        close()
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

            // Schedule reconnect
            if (!reconnect.isBroadcastOnlyMode) {
                val delay = reconnect.nextReconnectDelayMs()
                Log.i(TAG, "Scheduling reconnect in ${delay}ms (attempt ${reconnect.softAttempts})")
                close()
                handler.postDelayed({ connectDevice(0) }, delay)
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)

        if (gatt !== mBluetoothGatt) {
            Log.w(TAG, "onServicesDiscovered: stale callback, ignoring")
            return
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "onServicesDiscovered: failed status=$status")
            return
        }

        val service = gatt.getService(SERVICE_F000)
        if (service == null) {
            Log.e(TAG, "onServicesDiscovered: SERVICE_F000 (0x181F) not found!")
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

            // Post-key-exchange CCCD re-registration complete?
            if (postCccdStreamingPending) {
                Log.i(TAG, "Post-key-exchange CCCD re-registration complete. Entering streaming...")
                enterStreamingPhase()
                return
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
                gattOpActive = false
                drainGattQueue()
            }
        }

        if (uuid == CHAR_F002 && data != null && data.size == 17) {
            // BOND data read response
            handleBondData(data, gatt)
        }
    }

    override fun bonded() {
        // Note: SensorBluetooth calls bonded() on EVERY bond state change
        // (BONDING, BONDED, NONE, ERROR), not just BOND_BONDED.
        val gatt = mBluetoothGatt ?: return
        val bondState = gatt.device.bondState
        Log.i(TAG, "bonded() callback: bondState=$bondState")

        if (bondState == BluetoothDevice.BOND_BONDED) {
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
            }
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            Log.d(TAG, "bonded() callback: BOND_BONDING — waiting for BOND_BONDED")
        } else {
            Log.w(TAG, "bonded() callback: unexpected bond state $bondState")
        }
    }

    // =========================================================================
    // CCCD Chain
    // =========================================================================

    private fun writeNextCccd(gatt: BluetoothGatt) {
        if (cccdWriteInProgress) return
        val charUuid = cccdQueue.pollFirst() ?: return

        val service = gatt.getService(SERVICE_F000)
        val characteristic = service?.getCharacteristic(charUuid)
        if (characteristic == null) {
            Log.w(TAG, "writeNextCccd: characteristic $charUuid not found, skipping")
            writeNextCccd(gatt) // Try next
            return
        }

        cccdWriteInProgress = true
        // All AiDex characteristics (F001, F002, F003) use NOTIFY, not INDICATE.
        // F001 props=0x18 (WRITE + NOTIFY), F002 props=WRITE+NOTIFY, F003 props=0x10 (NOTIFY).
        // Writing indication (02 00) to a NOTIFY-only CCCD fails with status=3.
        enableNotification(gatt, characteristic)
    }

    // =========================================================================
    // Key Exchange
    // =========================================================================

    private fun startKeyExchange(gatt: BluetoothGatt) {
        setPhase(Phase.KEY_EXCHANGE)

        // Step 1: Write SN challenge to F001
        val challenge = keyExchange.getChallenge()
        Log.i(TAG, "Key exchange: writing challenge to F001 (${AiDexParser.hexString(challenge)})")

        val service = gatt.getService(SERVICE_F000) ?: return
        val f001 = service.getCharacteristic(CHAR_F001) ?: return

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
        Log.i(TAG, "Key exchange complete — re-registering CCCDs then entering streaming phase")

        val gatt = mBluetoothGatt
        if (gatt == null) {
            Log.e(TAG, "onKeyExchangeComplete: no active GATT!")
            return
        }

        val service = gatt.getService(SERVICE_F000)
        if (service == null) {
            Log.e(TAG, "onKeyExchangeComplete: SERVICE_F000 not found!")
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

        // After CCCDs are re-registered, enter streaming and send commands.
        // We override the normal post-CCCD behavior by setting a flag.
        postCccdStreamingPending = true

        Log.i(TAG, "Re-registering F003 + F002 CCCDs...")
        writeNextCccd(gatt)
    }

    /** Set when CCCDs are being re-registered after key exchange. */
    @Volatile private var postCccdStreamingPending = false

    /**
     * Enter streaming phase and send initial commands.
     * Called after post-key-exchange CCCD re-registration completes.
     */
    private fun enterStreamingPhase() {
        postCccdStreamingPending = false
        setPhase(Phase.STREAMING)
        reconnect.onConnectionSuccess()
        constatstatusstr = "Streaming"
        Log.i(TAG, "Streaming phase entered. Requesting device info + history...")

        // Request device info and history range
        requestDeviceInfo()
        handler.postDelayed({ requestHistoryRange() }, 300L)
    }

    // =========================================================================
    // F003 Data Handling
    // =========================================================================

    private fun handleF003(encryptedData: ByteArray) {
        val now = System.currentTimeMillis()
        Log.i(TAG, "handleF003: len=${encryptedData.size}, raw=${AiDexParser.hexString(encryptedData.copyOfRange(0, minOf(encryptedData.size, 8)))}")

        // Status/keepalive frames (5 bytes) — ignore
        val frameType = AiDexParser.classifyFrame(encryptedData)
        if (frameType == AiDexParser.FrameType.STATUS) {
            Log.d(TAG, "F003: Status frame (5 bytes), ignoring")
            return
        }

        if (frameType != AiDexParser.FrameType.DATA) {
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
            Log.w(TAG, "F003: Failed to parse decrypted data frame")
            return
        }

        // Validate CRC-16 embedded in frame (bytes 15-16)
        val frameCrc = Crc16CcittFalse.checksum(decrypted.copyOfRange(0, 15))
        if (frameCrc != frame.crc16) {
            Log.w(TAG, "F003: CRC-16 mismatch (expected=0x${"%04X".format(frameCrc)}, got=0x${"%04X".format(frame.crc16)})")
            return
        }

        Log.i(TAG, "F003: glucose=${frame.glucoseMgDl} mg/dL, i1=${frame.i1}, i2=${frame.i2}, " +
                "opcode=0x${"%02X".format(frame.opcode)}, valid=${frame.isValid}")

        if (!frame.isValid) {
            Log.w(TAG, "F003: Invalid reading (sentinel or out of range)")
            // handleGlucoseResult() with 0 will set charcha[1] for failure tracking
            handleGlucoseResult(0L, now)
            return
        }

        // Update timestamps
        lastGlucoseTimeMs = now

        // Compute all three chart values
        val autoValue = frame.glucoseMgDl
        val rawValue = frame.i1 * 10f
        val sensorGlucose = frame.i1 * 18.0182f

        // Build GlucoseReading
        val reading = GlucoseReading(
            timestamp = now,
            sensorSerial = tk.glucodata.drivers.aidex.native.crypto.SerialCrypto.stripPrefix(SerialNumber),
            autoValue = autoValue,
            rawValue = rawValue,
            sensorGlucose = sensorGlucose,
            rawI1 = frame.i1,
            rawI2 = frame.i2,
            timeOffsetMinutes = lastOffsetMinutes,
        )
        onGlucoseReading?.invoke(reading)

        // Deliver to SuperGattCallback's standard glucose pipeline
        // Pack: mgdl in lower 32 bits, rate in bits 32-47, alarm in bits 48-55
        val mgdlInt = autoValue.toInt().coerceIn(0, 0xFFFF) * 10 // scaled to match native format
        val packedResult = mgdlInt.toLong() and 0xFFFFFFFFL
        handleGlucoseResult(packedResult, now)
    }

    // =========================================================================
    // F002 Command Responses
    // =========================================================================

    private fun handleF002Response(data: ByteArray, gatt: BluetoothGatt) {
        if (data.isEmpty()) return
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

        // Validate CRC-16 on decrypted response (log warning but don't reject —
        // some response types may not have a CRC trailer)
        if (plaintext.size >= 3 && !Crc16CcittFalse.validateResponse(plaintext)) {
            Log.d(TAG, "F002: CRC-16 mismatch (len=${plaintext.size}, may not have CRC trailer)")
        }

        val opcode = plaintext[0].toInt() and 0xFF
        Log.d(TAG, "F002 response: opcode=0x${"%02X".format(opcode)}, len=${plaintext.size}")

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
            else -> Log.d(TAG, "F002: Unknown opcode 0x${"%02X".format(opcode)}")
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

        if (data.size < 9) return
        val statusByte = data[1].toInt() and 0xFF
        Log.i(TAG, "Device info response: status=0x${"%02X".format(statusByte)}, len=${data.size}")

        // Parse device metadata (bytes 3-16)
        if (data.size >= 17) {
            try {
                val fwMajor = data[3].toInt() and 0xFF
                val fwMinor = data[4].toInt() and 0xFF
                val hwMajor = data[5].toInt() and 0xFF
                val hwMinor = data[6].toInt() and 0xFF
                _firmwareVersion = "$fwMajor.$fwMinor"
                _hardwareVersion = "$hwMajor.$hwMinor"

                // Model string: bytes 9..16, null-terminated ASCII
                if (data.size >= 17) {
                    val modelBytes = data.copyOfRange(9, 17)
                    val nullIdx = modelBytes.indexOf(0.toByte())
                    val modelStr = if (nullIdx >= 0) String(modelBytes, 0, nullIdx, Charsets.US_ASCII)
                    else String(modelBytes, Charsets.US_ASCII)
                    if (modelStr.isNotBlank()) {
                        _modelName = modelStr.trim()
                        applyWearProfileFromModel(_modelName)
                    }
                }
                Log.i(TAG, "Device info: fw=$_firmwareVersion hw=$_hardwareVersion model=$_modelName")
            } catch (t: Throwable) {
                Log.e(TAG, "Device info parse failed: ${t.message}")
            }
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
        }
    }

    /**
     * Handle GET_BROADCAST_DATA (0x11) response — live glucose from query.
     */
    private fun handleBroadcastDataResponse(data: ByteArray) {
        if (data.size < 4) return
        Log.d(TAG, "Broadcast data response: len=${data.size}")
        // This provides current glucose similar to F003, but via command/response.
        // F003 notifications already deliver live glucose, so this is mostly
        // used during initial pairing flow. Parse if needed in the future.
    }

    private fun handleHistoryRangeResponse(data: ByteArray) {
        if (data.size < 8) return
        // data[2..3] = briefStart (0x24), data[4..5] = rawStart (0x23), data[6..7] = newest offset
        val briefStart = u16LE(data, 2)
        val rawStart = u16LE(data, 4)
        val newest = u16LE(data, 6)
        historyNewestOffset = newest
        Log.i(TAG, "History range: briefStart=$briefStart, rawStart=$rawStart, newest=$newest")

        // Start history download from where we left off
        if (historyRawNextIndex < rawStart) historyRawNextIndex = rawStart
        if (historyBriefNextIndex < briefStart) historyBriefNextIndex = briefStart

        // Fetch calibrated history first
        if (historyRawNextIndex <= newest) {
            requestHistoryPage(AiDexOpcodes.GET_HISTORIES_RAW, historyRawNextIndex)
        }
    }

    private fun handleHistoryRawResponse(data: ByteArray) {
        if (data.size < 4) return
        // data[1] = status, data[2..] = payload
        val payload = data.copyOfRange(2, data.size)
        val entries = AiDexParser.parseHistoryResponse(payload)
        if (entries.isNotEmpty()) {
            Log.i(TAG, "History raw (0x23): ${entries.size} entries, offsets ${entries.first().timeOffsetMinutes}..${entries.last().timeOffsetMinutes}")
            onCalibratedHistory?.invoke(entries)
            historyRawNextIndex = entries.last().timeOffsetMinutes + 1

            // Fetch next page if more data available
            if (historyRawNextIndex <= historyNewestOffset) {
                handler.postDelayed({
                    requestHistoryPage(AiDexOpcodes.GET_HISTORIES_RAW, historyRawNextIndex)
                }, HISTORY_REQUEST_DELAY_MS)
            } else {
                // Raw done — start brief history
                if (historyBriefNextIndex <= historyNewestOffset) {
                    handler.postDelayed({
                        requestHistoryPage(AiDexOpcodes.GET_HISTORIES, historyBriefNextIndex)
                    }, HISTORY_REQUEST_DELAY_MS)
                } else {
                    historyDownloading = false
                    Log.i(TAG, "History download complete")
                }
            }
        }
    }

    private fun handleHistoryBriefResponse(data: ByteArray) {
        if (data.size < 7) return
        val payload = data.copyOfRange(2, data.size)
        val entries = AiDexParser.parseBriefHistoryResponse(payload)
        if (entries.isNotEmpty()) {
            Log.i(TAG, "History brief (0x24): ${entries.size} entries, offsets ${entries.first().timeOffsetMinutes}..${entries.last().timeOffsetMinutes}")
            onAdcHistory?.invoke(entries)
            historyBriefNextIndex = entries.last().timeOffsetMinutes + 1

            // Fetch next page
            if (historyBriefNextIndex <= historyNewestOffset) {
                handler.postDelayed({
                    requestHistoryPage(AiDexOpcodes.GET_HISTORIES, historyBriefNextIndex)
                }, HISTORY_REQUEST_DELAY_MS)
            } else {
                historyDownloading = false
                Log.i(TAG, "History download complete")
            }
        }
    }

    private fun handleCalibrationAck(data: ByteArray) {
        if (data.size < 2) return
        val statusByte = data[1].toInt() and 0xFF
        Log.i(TAG, "Calibration ACK: status=0x${"%02X".format(statusByte)}")
    }

    private fun handleCalibrationRangeResponse(data: ByteArray) {
        if (data.size < 6) return
        val startIndex = u16LE(data, 2)
        val endIndex = u16LE(data, 4)
        Log.i(TAG, "Calibration range: start=$startIndex, end=$endIndex")

        // Fetch calibration records
        if (startIndex <= endIndex) {
            requestCalibration(startIndex)
        }
    }

    private fun handleCalibrationResponse(data: ByteArray) {
        if (data.size < 10) return
        val payload = data.copyOfRange(2, data.size)
        val records = AiDexParser.parseCalibrationResponse(payload)
        if (records.isNotEmpty()) {
            Log.i(TAG, "Calibration records: ${records.size} entries")
            onCalibrationRecords?.invoke(records)
        }
    }

    private fun handleNewSensorAck(data: ByteArray) {
        if (data.size < 2) return
        val statusByte = data[1].toInt() and 0xFF
        Log.i(TAG, "New sensor ACK: status=0x${"%02X".format(statusByte)}")
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
        }
    }

    private fun requestCalibration(index: Int) {
        val cmd = commandBuilder.getCalibration(index) ?: return
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
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
                }
                if (!ok) {
                    handleWriteFailure(next, gatt)
                }
            }
            is GattOp.Read -> {
                val service = gatt.getService(SERVICE_F000)
                val characteristic = service?.getCharacteristic(next.charUuid)
                if (characteristic == null) {
                    Log.w(TAG, "GATT read: characteristic ${next.charUuid} not found, skipping")
                    drainGattQueue()
                    return
                }
                val ok = gatt.readCharacteristic(characteristic)
                Log.d(TAG, "GATT read [${next.charUuid}]: ok=$ok")
                gattOpActive = ok
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
    // Service Discovery Retry
    // =========================================================================

    private fun scheduleDiscoveryRetries(gatt: BluetoothGatt) {
        for (attempt in 1..DISCOVERY_MAX_RETRIES) {
            mainHandler.postDelayed({
                if (mBluetoothGatt != null && !servicesReady) {
                    Log.i(TAG, "Service discovery retry $attempt/$DISCOVERY_MAX_RETRIES")
                    mBluetoothGatt?.discoverServices()
                }
            }, DISCOVERY_RETRY_DELAY_MS * attempt)
        }
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
            else -> 14
        }
        Log.i(TAG, "Wear profile: model=$modelName days=$_wearDays")
    }

    // =========================================================================
    // AiDexDriver Interface Implementation
    // =========================================================================

    override fun getDetailedBleStatus(): String = when (phase) {
        Phase.IDLE -> "Disconnected"
        Phase.GATT_CONNECTING -> "Connecting..."
        Phase.DISCOVERING_SERVICES -> "Discovering services..."
        Phase.CCCD_CHAIN -> "Configuring notifications..."
        Phase.KEY_EXCHANGE -> "Key exchange..."
        Phase.STREAMING -> if (reconnect.isBroadcastOnlyMode) "Broadcast Mode" else "Connected"
    }

    override val isPaused: Boolean get() = stop

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
        keyExchange.reset()
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
        Log.i(TAG, "softDisconnect: non-destructive disconnect for $SerialNumber")
        try { mBluetoothGatt?.disconnect() } catch (_: Throwable) {}
        setPhase(Phase.IDLE)
    }

    override fun manualReconnectNow() {
        Log.i(TAG, "manualReconnectNow: forcing reconnect for $SerialNumber")
        reconnect.reset()
        stop = false
        connectDevice(0L)
        AiDexDriver.deviceListDirty = true
    }

    override fun setBroadcastOnlyConnection(enabled: Boolean) {
        Log.i(TAG, "setBroadcastOnlyConnection($enabled) for $SerialNumber")
        reconnect.isBroadcastOnlyMode = enabled
        if (enabled) {
            // Disconnect GATT, rely on advertisements only
            softDisconnect()
        } else {
            // Resume active GATT connection
            manualReconnectNow()
        }
    }

    override fun resetSensor(): Boolean {
        Log.i(TAG, "resetSensor: sending reset (0xF0) for $SerialNumber")
        val cmd = commandBuilder.resetSensor() ?: run {
            Log.e(TAG, "resetSensor: session key not available")
            return false
        }
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
        return true
    }

    override fun startNewSensor(): Boolean {
        Log.i(TAG, "startNewSensor: activating sensor $SerialNumber")
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
        if (lastOffsetMinutes <= 0) {
            Log.e(TAG, "calibrateSensor: no offset available yet")
            return false
        }
        sendCalibration(lastOffsetMinutes, glucoseMgDl)
        return true
    }

    override fun unpairSensor(): Boolean {
        Log.i(TAG, "unpairSensor: sending deleteBond (0xF2) for $SerialNumber")
        val cmd = commandBuilder.deleteBond() ?: run {
            Log.e(TAG, "unpairSensor: session key not available")
            return false
        }
        enqueueGattOp(GattOp.Write(CHAR_F002, cmd))
        // Also remove Android-level bond
        try {
            val device = mBluetoothGatt?.device
            if (device?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                val removeBond = device.javaClass.getMethod("removeBond")
                removeBond.invoke(device)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "unpairSensor: removeBond failed: ${t.message}")
        }
        keyExchange.reset()
        return true
    }

    override fun rePairSensor() {
        Log.i(TAG, "rePairSensor: resetting key exchange and reconnecting for $SerialNumber")
        keyExchange.reset()
        softDisconnect()
        handler.postDelayed({
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
    // Cleanup
    // =========================================================================

    /**
     * Release all resources. Call when sensor is removed from gattcallbacks list.
     */
    override fun destroy() {
        stop = true
        close()
        handler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
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
