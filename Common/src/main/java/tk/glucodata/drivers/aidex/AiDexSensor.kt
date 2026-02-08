package tk.glucodata.drivers.aidex

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
// import android.util.Log
import tk.glucodata.Log
import tk.glucodata.SuperGattCallback
import tk.glucodata.Natives
import com.microtechmd.blecomm.parser.AidexXParser
import com.microtechmd.blecomm.BleAdapter
import com.microtechmd.blecomm.BlecommLoader
import com.microtechmd.blecomm.BluetoothDeviceStore
import com.microtechmd.blecomm.constant.AidexXOperation
import com.microtechmd.blecomm.controller.AidexXController
import com.microtechmd.blecomm.controller.BleController
import com.microtechmd.blecomm.controller.BleControllerInfo
import com.microtechmd.blecomm.entity.BleMessage
import java.lang.reflect.Method
import java.util.Calendar
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import tk.glucodata.data.HistoryRepository
import com.microtechmd.blecomm.entity.AidexXDatetimeEntity
import com.microtechmd.blecomm.entity.NewSensorEntity

/**
 * AiDex/LinX Sensor Driver.
 *
 * Handles:
 * 1. Scanning (filtering by Manufacturer Data or Service UUID).
 * 2. Connection & Handshake (Proprietary "B6" protocol).
 * 3. Decryption (AES-128 CFB with Dynamic IV).
 * 4. Official parser first, deterministic fallback when needed.
 */
class AiDexSensor(context: Context, serial: String, dataptr: Long) : SuperGattCallback(serial, dataptr, 0) {

    companion object {
        private const val TAG = "AiDexSensor"
        
        // --- PROPRIETARY SERVICES & CHARACTERISTICS ---
        // Note: Characteristics are embedded in the Standard CGM Service (0x181F)
        val SERVICE_F000: UUID = UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb")
        val CHAR_F001: UUID    = UUID.fromString("0000F001-0000-1000-8000-00805f9b34fb") // Write/Indicate (Commands)
        val CHAR_F002: UUID    = UUID.fromString("0000F002-0000-1000-8000-00805f9b34fb") // Write/Notify (Auth?)
        val CHAR_F003: UUID    = UUID.fromString("0000F003-0000-1000-8000-00805f9b34fb") // Notify (Data Stream)

        // --- PRIVATE CONFIGURATION SERVICE (FF30) ---
        // Used for New Sensor, Reset, Shelf Mode, etc.
        val SERVICE_FF30: UUID = UUID.fromString("0000FF30-0000-1000-8000-00805f9b34fb")
        val CHAR_FF31: UUID    = UUID.fromString("0000FF31-0000-1000-8000-00805f9b34fb") // Notify (Response)
        val CHAR_FF32: UUID    = UUID.fromString("0000FF32-0000-1000-8000-00805f9b34fb") // Write (Command)
        
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // --- KEYS ---
        // "AC4C8ECDD8761B512EEB95D707942912" -> Hex bytes
        private val MASTER_KEY = byteArrayOf(
            0xAC.toByte(), 0x4C.toByte(), 0x8E.toByte(), 0xCD.toByte(),
            0xD8.toByte(), 0x76.toByte(), 0x1B.toByte(), 0x51.toByte(),
            0x2E.toByte(), 0xEB.toByte(), 0x95.toByte(), 0xD7.toByte(),
            0x07.toByte(), 0x94.toByte(), 0x29.toByte(), 0x12.toByte()
        )

        private val handshakeHandler = Handler(Looper.getMainLooper())

        // Official handshake sequences observed in LinX 1.7.25 logs.
        // Unbonded/short flow (A8/AF IV path).
        private val OFFICIAL_HANDSHAKE_UNBONDED_STEPS = arrayOf(
            "BE67CDEE",
            "BF67FCDD",
            "9A8660",
            "A9B666",
            "A825A3EEC8",
            "AF24A34F7E",
        )

        // Bonded/long flow (history-prefetch + stream enable).
        // Derived from aidex_output_timed.txt.
        private val OFFICIAL_HANDSHAKE_BONDED_STEPS = arrayOf(
            "B0C58080",
            "B1C5B1B3",
            "94242D",
            "A7142B",
            "A689ECCD88",
            "A600ECCD29",
            "A6FFED133A",
            "A676ED139B",
            "A6EDEAE52F",
            "A18DEC99C1",
            "A1BCEC3DF7",
            "A163EC24F2",
            "A112EC4CC9",
            "A1C1ED1999",
            "A1F0EDBDAF",
            "A1A7ED9538",
            "A156ED6518",
            "A105ED8943",
            "A134ED2D75",
            "A1DBEA4605",
            "A18AEAC838",
            "A1B9EA0E68",
        )

        // Post-pairing flow (observed right after SMP pairing).
        // Derived from btsnoop_hci_timed.log + aidex_output_timed.txt.
        private const val OFFICIAL_PAIRED_F001 = "91C5470280BB4C3D8FA8EDB1B06A0F06"
        private val OFFICIAL_PAIRED_F002_STEPS = arrayOf(
            "FBF050",
            "CA8276",
            "969BEADB",
            "979BDBE8",
            "B27A47",
        )

        // Legacy handshake sequence from older probes
        private val LEGACY_HANDSHAKE_STEPS = arrayOf(
            "55FB0631",
            "54FB3702",
            "711AAB",
            "422AAD",
            "43BA4C847E",
            "44C14CB72F",
            "802454",
            "81FB486A48",
            "826674",
            "B4482C",
            "B597303367",
            "B60A0C",
        )

        private const val HANDSHAKE_STEP_DELAY_MS = 120L
        private const val OFFICIAL_BONDED_STEP_DELAY_MS = 200L
        private const val HANDSHAKE_READ_DELAY_MS = 120L
        private const val HANDSHAKE_TIMEOUT_MS = 4000L
        private const val HANDSHAKE_MAX_RETRIES = 3
        private const val HANDSHAKE_READ_MAX = 3
        private const val HANDSHAKE_PRIME_TIMEOUT_MS = 500L
        private const val BOND_WAIT_MS = 4000L
        private const val LEGACY_HANDSHAKE_STEP_DELAY_MS = 1200L
        private const val LEGACY_HANDSHAKE_READ_DELAY_MS = 1200L

        private const val BROADCAST_STALE_MS = 5 * 60_000L
        private const val BROADCAST_SCAN_WINDOW_MS = 30_000L
        private const val BROADCAST_SCAN_BASE_INTERVAL_AUTO_MS = 60_000L
        private const val BROADCAST_SCAN_BASE_INTERVAL_CONNECTED_MS = 60_000L
        private const val BROADCAST_SCAN_MAX_INTERVAL_MS = 300_000L
        private const val BROADCAST_MIN_STORE_INTERVAL_MS = 50_000L

        private const val BROADCAST_REFERENCE_MS = 5 * 60_000L
    }
    // --- STATE ---
    private data class HandshakeStep(
        val label: String,
        val uuid: UUID,
        val data: ByteArray,
        val expectResponseOps: Set<Int> = emptySet()
    )
    private enum class OfficialFlowStage {
        BOOTSTRAP,
        STREAMING
    }

    private var handshakeStep = 0
    private var handshakePlan: List<HandshakeStep> = emptyList()
    private var handshakePlanLabel: String = "none"
    private var officialFlowStage: OfficialFlowStage = OfficialFlowStage.BOOTSTRAP
    private var dynamicIV: ByteArray? = null
    private var bondRequested = false
    private var bondWaitUntilMs = 0L
    private var justBondedThisSession = false
    private var handshakePrimingInProgress = false
    private var pendingHandshakePrimingTimeout: Runnable? = null
    private var useOfficialHandshake = false
    private var useBitReverse: Boolean? = null
    @Volatile private var connectInProgress = false
    private val broadcastEnabled = true
    private var broadcastDisabledLogged = false
    private val rawBroadcastFallbackEnabled = true

    private var lastRawMgDl: Float = 0f
    private var lastRawTime: Long = 0L
    private var lastAutoMgDl: Float = 0f
    private var lastAutoTime: Long = 0L

    private var pendingHandshakeRead: Runnable? = null
    private var pendingHandshakeTimeout: Runnable? = null
    private var expectedF002ResponseOps: Set<Int> = emptySet()
    private var waitAnyF002Response = false
    private var handshakeRetries: Int = 0
    private var handshakeReadAttempts: Int = 0
    private val pendingIVCandidates = ArrayList<ByteArray>(4)
    private var vendorParserUnavailableLogged = false
    private val vendorProbeFailures = HashSet<String>()
    private var ivLockKey: String? = null
    private var ivLockCount: Int = 0
    private var ivLocked = false
    private var ivLockedFromBroadcast = false

    // --- BROADCAST SCAN STATE ---
    private val scanHandler = Handler(Looper.getMainLooper())

    // init block removed for debugging

    private var broadcastScanner: BluetoothLeScanner? = null
    private var broadcastScanCallback: ScanCallback? = null
    internal var broadcastScanActive = false
    private var broadcastScanMisses = 0
    private var broadcastScanStartedAtElapsed = 0L
    private var connectedAddress: String? = null
    private var broadcastWakeLock: PowerManager.WakeLock? = null
    private var broadcastSeenInSession = false
    private var sessionStartMs: Long = System.currentTimeMillis()
    
    // --- BROADCAST STATE ---
    private var lastBroadcastGlucose: Float = 0f
    private var lastBroadcastTime: Long = 0L
    private var lastBroadcastOffsetMinutes: Long = 0L
    private var lastBroadcastStoredOffsetMinutes: Long = 0L
    private var lastRawBroadcastOffsetMinutes: Long = 0L

    private var viewModeInternal: Int = 0
    
    // Connection option: use broadcast scanning instead of GATT (separate from viewMode)
    var broadcastOnlyConnection: Boolean = false
        private set

    // --- VENDOR (blecomm-lib) STATE ---
    private val vendorBleEnabled = true
    private var vendorAdapter: VendorBleAdapter? = null
    private var vendorController: AidexXController? = null
    private var lastScanRecordBytes: ByteArray? = null
    private var vendorStarted = false
    private var vendorRegistered = false
    private var vendorLibAvailable = false
    private var vendorLibLogged = false
    private var vendorGattConnected = false
    private var vendorGattNotified = false
    private var vendorConnectPending = false
    @Volatile private var vendorNativeReady = false         // true after onConnectSuccess() succeeds without SIGSEGV
    private var vendorConnectSuccessCrashCount = 0          // consecutive crash count for crash-loop protection
    private val VENDOR_MAX_CRASH_RETRIES = 3                // stop calling onConnectSuccess after N consecutive failures
    @Volatile private var vendorExecuteConnectReceived = false  // true after native lib called executeConnect() — MUST be true before onConnectSuccess()
    private var lastVendorMgDl: Float = 0f
    private var lastVendorTime: Long = 0L
    private var lastVendorOffsetMinutes: Int = 0
    private val vendorWriteQueue = ArrayDeque<VendorWrite>()
    private var vendorWriteActive = false
    private var vendorLongConnectTriggered = false

    var viewMode: Int
        get() = viewModeInternal
        set(value) {
            if (viewModeInternal == value) return
            val previous = viewModeInternal
            viewModeInternal = value
            if (previous == 0 && value != 0) {
                // Allow combined/raw modes to consume the most recent broadcast once.
                lastBroadcastStoredOffsetMinutes = 0L
            }
            Log.i(TAG, "ViewMode changed: $previous -> $value")
            if (dataptr != 0L) Natives.setViewMode(dataptr, value)
            applyViewMode("viewMode", connectIfNeeded = true)
        }
    
    /** Enable/disable broadcast-only connection mode (no GATT, just BLE advertisements) */
    fun setBroadcastOnlyConnection(enabled: Boolean) {
        if (broadcastOnlyConnection == enabled) return
        broadcastOnlyConnection = enabled
        writeBoolPref("broadcastOnlyConnection", enabled)
        Log.i(TAG, "BroadcastOnlyConnection changed: $enabled")
        
        // Update notification status
        constatstatusstr = if (enabled) "Broadcast Mode" else "Disconnected"
        
        applyViewMode("broadcastOnly", connectIfNeeded = true)
    }
    
    /** Get detailed BLE status for UI display */
    fun getDetailedBleStatus(): String {
        val now = System.currentTimeMillis()
        
        if (broadcastOnlyConnection) {
            // Broadcast-only mode status
            return when {
                broadcastScanActive -> "Scanning..."
                lastBroadcastTime > 0 && (now - lastBroadcastTime) < 120_000 -> {
                    val agoSec = (now - lastBroadcastTime) / 1000
                    "Last broadcast ${agoSec}s ago"
                }
                lastBroadcastTime > 0 -> {
                    val agoMin = (now - lastBroadcastTime) / 60_000
                    "Last broadcast ${agoMin}m ago"
                }
                else -> "Waiting for broadcast..."
            }
        } else {
            // GATT mode status
            val gattStatus = mBluetoothGatt != null
            val rawStatus = constatstatusstr ?: ""
            return when {
                gattStatus && rawStatus.isEmpty() -> "Connected"
                rawStatus.startsWith("Status=") -> rawStatus
                rawStatus.isNotEmpty() -> rawStatus
                else -> "Disconnected"
            }
        }
    }
    
    private val appContext = context.applicationContext

    private val prefs by lazy { 
        appContext.getSharedPreferences("AiDexSensorPrefs", Context.MODE_PRIVATE)
    }

    private fun prefKey(name: String): String = "${name}_${SerialNumber}"

    private fun readFloatPref(name: String, default: Float): Float {
        val key = prefKey(name)
        return if (prefs.contains(key)) prefs.getFloat(key, default) else prefs.getFloat(name, default)
    }

    private fun readLongPref(name: String, default: Long): Long {
        val key = prefKey(name)
        return if (prefs.contains(key)) prefs.getLong(key, default) else prefs.getLong(name, default)
    }

    private fun readIntPref(name: String, default: Int): Int {
        val key = prefKey(name)
        return if (prefs.contains(key)) prefs.getInt(key, default) else prefs.getInt(name, default)
    }

    private fun writeFloatPref(name: String, value: Float) {
        prefs.edit()
            .putFloat(prefKey(name), value)
            .putFloat(name, value)
            .apply()
    }

    private fun writeLongPref(name: String, value: Long) {
        prefs.edit()
            .putLong(prefKey(name), value)
            .putLong(name, value)
            .apply()
    }

    private fun writeIntPref(name: String, value: Int) {
        prefs.edit()
            .putInt(prefKey(name), value)
            .putInt(name, value)
            .apply()
    }

    private fun readBoolPref(name: String, default: Boolean): Boolean {
        val key = prefKey(name)
        return if (prefs.contains(key)) prefs.getBoolean(key, default) else prefs.getBoolean(name, default)
    }

    private fun writeBoolPref(name: String, value: Boolean) {
        prefs.edit()
            .putBoolean(prefKey(name), value)
            .putBoolean(name, value)
            .apply()
    }

    private fun readStringPref(name: String, default: String?): String? {
        val key = prefKey(name)
        return if (prefs.contains(key)) prefs.getString(key, default) else prefs.getString(name, default)
    }

    private fun writeStringPref(name: String, value: String?) {
        prefs.edit()
            .putString(prefKey(name), value)
            .putString(name, value)
            .apply()
    }

    init {
        lastBroadcastGlucose = readFloatPref("lastBroadcastGlucose", 0f)
        lastBroadcastTime = readLongPref("lastBroadcastTime", 0L)
        lastBroadcastOffsetMinutes = readLongPref("lastBroadcastOffsetMinutes", 0L)
        lastRawMgDl = readFloatPref("lastRawMgDl", 0f)
        lastRawTime = readLongPref("lastRawTime", 0L)
        lastAutoMgDl = readFloatPref("lastAutoMgDl", 0f)
        lastAutoTime = readLongPref("lastAutoTime", 0L)
        sessionStartMs = System.currentTimeMillis()
        broadcastSeenInSession = false
        val savedReverse = readIntPref("useBitReverse", -1)
        useBitReverse = when (savedReverse) {
            1 -> true
            0 -> false
            else -> null
        }
        // Load connection option (separate from viewMode)
        broadcastOnlyConnection = readBoolPref("broadcastOnlyConnection", false)
        viewModeInternal = if (dataptr != 0L) Natives.getViewMode(dataptr) else 0
        
        // Set initial status for broadcast mode (prevents 'Searching for sensors' from SensorBluetooth)
        if (broadcastOnlyConnection) {
            constatstatusstr = "Broadcast Mode"
        }
        
        Log.i(TAG, "Loaded: LastBroadcast=$lastBroadcastGlucose, BitReverse=$useBitReverse, ViewMode=$viewModeInternal, BroadcastOnly=$broadcastOnlyConnection")
        applyViewMode("init", connectIfNeeded = false)
        scheduleBroadcastScan("init", forceImmediate = true)
    }

    private fun isAutoOnlyMode(): Boolean = viewModeInternal == 0
    private fun isRawOnlyMode(): Boolean = viewModeInternal == 1
    private fun isBroadcastOnlyMode(): Boolean = broadcastOnlyConnection
    private fun wantsAuto(): Boolean = viewModeInternal == 0 || viewModeInternal == 2
    private fun wantsRaw(): Boolean = viewModeInternal == 1 || viewModeInternal == 3
    private fun wantsBroadcastScan(): Boolean {
        // Mode 4 (Broadcast Only) always wants broadcast scan
        if (isBroadcastOnlyMode()) return broadcastEnabled
        
        if (!broadcastEnabled) return false
        if (!(wantsAuto() || (wantsRaw() && rawBroadcastFallbackEnabled))) return false

        val now = System.currentTimeMillis()
        if (dynamicIV == null) return true
        if (lastRawTime == 0L) return true
        return (now - lastRawTime) > BROADCAST_REFERENCE_MS
    }


    private fun scanTargetAddress(): String? = connectedAddress ?: mActiveDeviceAddress

    private fun broadcastScanBaseIntervalMs(): Long {
        return if (isAutoOnlyMode()) {
            BROADCAST_SCAN_BASE_INTERVAL_AUTO_MS
        } else {
            BROADCAST_SCAN_BASE_INTERVAL_CONNECTED_MS
        }
    }

    private fun applyViewMode(reason: String, connectIfNeeded: Boolean) {
        // Mode policy:
        // - Auto (0): official handshake + broadcast/IV-lock (vendor parser optional).
        // - Raw (1): legacy handshake.
        // - Auto+Raw (2, 3): combined modes.
        // - Broadcast Only (4): NO connection, just BLE advertisement scanning for stability.
        useOfficialHandshake = wantsAuto()

        if (wantsAuto() || wantsRaw()) {
            ensureVendorParserLoaded("mode-$reason")
        }

        if (vendorBleEnabled && wantsAuto()) {
            ensureVendorStarted("mode-$reason")
        } else {
            stopVendor("mode-$reason")
        }

        // Broadcast Only mode: disconnect GATT so sensor resumes advertising
        if (isBroadcastOnlyMode()) {
            Log.i(TAG, "Broadcast Only mode: disconnecting GATT so sensor can advertise")
            disconnect()
        } else if (connectIfNeeded && (wantsRaw() || wantsAuto())) {
            ensureConnected("mode-$reason")
        }

        if (wantsBroadcastScan()) {
            scheduleBroadcastScan("mode-$reason", forceImmediate = true)
        }
    }

    private fun ensureVendorParserLoaded(reason: String) {
        vendorLibAvailable = BlecommLoader.ensureLoaded()
        if (!vendorLibAvailable && !vendorLibLogged) {
            vendorLibLogged = true
            Log.e(TAG, "Vendor parser lib not available ($reason).")
        }
        if (vendorLibAvailable) {
            vendorParserUnavailableLogged = false
        }
    }

    private data class VendorWrite(val uuid: Int, val data: ByteArray)

    private fun ensureVendorStarted(reason: String) {
        if (!vendorBleEnabled) {
            ensureVendorParserLoaded("vendor-ble-disabled-$reason")
            return
        }
        if (vendorStarted && vendorController != null) return

        vendorLibAvailable = BlecommLoader.ensureLoaded()
        if (!vendorLibAvailable && !vendorLibLogged) {
            vendorLibLogged = true
            Log.e(TAG, "Vendor lib not available; vendor mode disabled.")
        }
        if (!vendorLibAvailable) {
            return
        }
        val adapter = vendorAdapter ?: VendorBleAdapter(appContext).also {
            vendorAdapter = it
        }
        if (!vendorStarted) {
            try {
                BleController.setBleAdapter(adapter)
                BleController.setDiscoveredCallback { info -> onVendorDiscovered(info) }
                vendorStarted = true
                vendorParserUnavailableLogged = false
            } catch (t: Throwable) {
                Log.e(TAG, "Vendor init failed: ${t.message}")
                return // cannot proceed without adapter registration
            }
            try {
                BleController.startScan()
            } catch (_: Throwable) {
            }
            Log.i(TAG, "Vendor started ($reason)")
        }

        // Proactive initialization if we have a connected address but no controller yet.
        if (vendorController == null) {
            val addr = connectedAddress ?: mActiveDeviceAddress
            if (addr != null && SerialNumber.isNotEmpty()) {
                Log.i(TAG, "Proactively initializing vendor controller for $addr")
                // type=1, address, name, sn, rssi=-60, params=lastScanRecordBytes or loaded
                var scannedBytes = lastScanRecordBytes
                if (scannedBytes == null) {
                    scannedBytes = loadVendorParams()
                    if (scannedBytes != null) {
                        Log.i(TAG, "Vendor init: Loaded cached params (${scannedBytes.size} bytes)")
                        lastScanRecordBytes = scannedBytes
                    }
                }
                val finalBytes = scannedBytes ?: ByteArray(0)

                if (finalBytes.isEmpty()) {
                     Log.w(TAG, "Vendor init warning: Using empty scan record bytes. Device usage might fail.")
                }
                val info = BleControllerInfo(1, addr, "AiDEX $SerialNumber", SerialNumber, -60, finalBytes)
                // Ensure device is recorded in adapter store for the library's internal mapping
                mActiveBluetoothDevice?.let { adapter.recordDevice(it) }
                onVendorDiscovered(info)
            }
        }
    }

    private fun stopVendor(reason: String) {
        if (!vendorBleEnabled) return
        if (!vendorStarted) return
        vendorStarted = false
        try {
            BleController.stopScan()
        } catch (_: Throwable) {
        }
        try {
            vendorController?.disconnect()
        } catch (_: Throwable) {
        }
        try {
            vendorController?.unregister()
        } catch (_: Throwable) {
        }
        vendorRegistered = false
        vendorGattConnected = false
        vendorServicesReady = false
        vendorGattNotified = false
        vendorConnectPending = false
        vendorNativeReady = false
        vendorExecuteConnectReceived = false
        vendorWriteQueue.clear()
        vendorWriteActive = false
        vendorLongConnectTriggered = false
        Log.i(TAG, "Vendor stopped ($reason)")

    }

    private var disregardDisconnectsUntil: Long = 0

    override fun disconnect() {
        if (System.currentTimeMillis() < disregardDisconnectsUntil) {
            Log.w(TAG, "Ignoring disconnect() request (Debounce active)")
            return
        }
        super.disconnect()
    }

    override fun close() {
        stopVendor("close")
        super.close()
    }

    /**
     * Sends a maintenance command to the AiDex sensor via the vendor library.
     * @param opCode 1=Reset, 2=ShelfMode, 3=DeleteBond, 4=ClearStorage
     */
    /**
     * Sends a maintenance command to the AiDex sensor via the vendor library.
     * @param opCode 1=Reset, 2=ShelfMode, 3=DeleteBond, 4=ClearStorage
     */
    fun sendMaintenanceCommand(opCode: Int): Boolean {
        return executeVendorCommand("maintenance", opCode) { controller ->
             when (opCode) {
                1 -> controller.reset()
                2 -> controller.shelfMode()
                3 -> controller.deleteBond()
                4 -> controller.clearStorage()
                else -> -1
            }
        }
    }
    
    // Old startNewSensor and resetSensor removed in favor of robust implementation at end of file.

    private fun executeVendorCommand(label: String, opCode: Int, action: (AidexXController) -> Int): Boolean {
        if (!vendorBleEnabled) {
             Log.w(TAG, "$label failed: Vendor BLE not enabled")
             return false
        }
        ensureVendorStarted(label)

        // Wait for controller to be ready (async init race)
        var attempts = 0
        while (vendorController == null && attempts < 5) {
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
            }
            attempts++
        }

        val controller = vendorController
        if (controller == null) {
            Log.w(TAG, "$label failed: Vendor controller not ready after wait")
            return false
        }

        // Critical: Ensure vendor GATT connection and notifications before sending command
        if (!ensureVendorGattReady()) {
            Log.w(TAG, "$label failed: Could not establish vendor GATT connection")
            return false
        }

        // Wait for AES initialization (the vendor native lib needs to complete the F002 handshake)
        val aesBeforeCmd = try { controller.isInitialized } catch (_: Throwable) { false }
        if (!aesBeforeCmd) {
            Log.i(TAG, "$label: AES not yet initialized, waiting for F002 handshake...")
            var aesWait = 0
            val maxAesWait = 50 // 10 seconds (50 * 200ms)
            var aesReady = false
            while (aesWait < maxAesWait) {
                try { Thread.sleep(200) } catch (_: InterruptedException) {}
                aesWait++
                aesReady = try { controller.isInitialized } catch (_: Throwable) { false }
                if (aesReady) break
            }
            if (!aesReady) {
                Log.w(TAG, "$label failed: AES handshake did not complete after ${aesWait * 200}ms")
                return false
            }
            Log.i(TAG, "$label: AES initialized after ${aesWait * 200}ms")
        } else {
            Log.i(TAG, "$label: AES already initialized")
        }

        // Trigger vendor long-connect mode if not already done
        if (vendorGattNotified && !vendorLongConnectTriggered) {
            try {
                startVendorLongConnect(label)
                vendorLongConnectTriggered = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start vendor long-connect: ${t.message}")
            }
        }

        return try {
            val writeQueueBefore = vendorWriteQueue.size
            val result = action(controller)
            Log.i(TAG, "Command $label (op=$opCode) invoked, native result=$result (0x${Integer.toHexString(result)})")
            
            // Wait for the vendor library to trigger executeWrite callbacks
            Thread.sleep(800)
            
            val writeQueueAfter = vendorWriteQueue.size
            val writesProduced = writeQueueBefore != writeQueueAfter || vendorWriteActive
            Log.i(TAG, "Command $label: writeQueueBefore=$writeQueueBefore writeQueueAfter=$writeQueueAfter writeActive=$vendorWriteActive writesProduced=$writesProduced")
            
            // The result from native methods like reset() is the opcode echoed back (e.g. 3840=0xF00).
            // This does NOT mean the command was successfully sent over BLE.
            // The real indicator is whether executeWrite/executeWriteCharacteristic was called.
            if (result < 0) {
                Log.w(TAG, "Command $label: native returned error $result")
                false
            } else {
                Log.i(TAG, "Command $label: native accepted (result=$result). BLE transmission pending via vendor callbacks.")
                true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to send command $label: ${t.message}")
            false
        }
    }

    /**
     * Ensures the vendor GATT connection is established and notifications are enabled.
     * Returns true if ready for command transmission, false otherwise.
     */
    private fun ensureVendorGattReady(): Boolean {
        // If already connected and notified (set by onDescriptorWrite(F003)), we're ready
        if (vendorGattConnected && vendorGattNotified) {
            Log.d(TAG, "ensureVendorGattReady: already ready (connected=$vendorGattConnected notified=$vendorGattNotified)")
            return true
        }

        // Check if GATT exists but flags aren't set yet
        val gatt = mBluetoothGatt
        if (gatt != null) {
            // GATT exists but vendorGattNotified is false — means onDescriptorWrite(F003) hasn't
            // fired yet (CCCDs not written, or service discovery still pending).
            // Do NOT force the flags — that was the root cause of the AES-never-initializes bug.
            // Instead, wait for the proper notification setup to complete.
            Log.i(TAG, "ensureVendorGattReady: GATT exists but not fully ready (connected=$vendorGattConnected notified=$vendorGattNotified). Waiting...")
            var waitAttempts = 0
            val maxWait = 50 // 10 seconds (50 * 200ms)
            while (!vendorGattNotified && waitAttempts < maxWait) {
                try { Thread.sleep(200) } catch (_: InterruptedException) {}
                waitAttempts++
            }
            if (vendorGattNotified) {
                vendorGattConnected = true
                Log.i(TAG, "ensureVendorGattReady: notifications became ready after ${waitAttempts * 200}ms")
                return true
            }
            Log.w(TAG, "ensureVendorGattReady: timeout waiting for notifications (GATT exists but CCCDs not written)")
            return false
        }

        // No GATT — need to connect
        val addr = connectedAddress ?: mActiveDeviceAddress
        if (addr == null) {
            Log.w(TAG, "ensureVendorGattReady: no known device address")
            return false
        }
        Log.i(TAG, "ensureVendorGattReady: triggering GATT connection for maintenance command")
        if (!connectDevice(0)) {
            Log.w(TAG, "ensureVendorGattReady: failed to trigger connection")
            return false
        }

        // Wait for GATT connection + notification setup (vendorGattNotified is set by onDescriptorWrite(F003))
        var waitAttempts = 0
        val maxWait = 75 // 15 seconds (75 * 200ms) — includes service discovery + CCCD writes
        while (!vendorGattNotified && waitAttempts < maxWait) {
            try { Thread.sleep(200) } catch (_: InterruptedException) {}
            waitAttempts++
        }
        if (!vendorGattNotified) {
            Log.w(TAG, "ensureVendorGattReady: timeout waiting for GATT+notifications after ${waitAttempts * 200}ms")
            return false
        }

        vendorGattConnected = true
        Log.i(TAG, "ensureVendorGattReady: ready after new connection (${waitAttempts * 200}ms)")
        return true
    }



    private var lastDiscoveredInfo: BleControllerInfo? = null
    private var vendorServicesReady = false

    private fun onVendorDiscovered(info: BleControllerInfo) {
        if (!vendorBleEnabled) return
        if (!wantsAuto()) return
        if (!vendorLibAvailable) return

        // Cache valid info for later use (e.g. clean start retry)
        if (!info.sn.isNullOrBlank() && info.params != null) {
            lastDiscoveredInfo = info
            // Persist params for Clean Start scenarios (app restart)
            writeStringPref("lastVendorAddress", info.address)
            writeStringPref("lastVendorParams", bytesToHex(info.params))
        }

        val target = scanTargetAddress()
        val matches = when {
            target != null -> info.address == target
            !info.sn.isNullOrBlank() -> info.sn.contains(SerialNumber, ignoreCase = true)
            !info.name.isNullOrBlank() -> info.name.contains(SerialNumber, ignoreCase = true)
            else -> false
        }
        if (!matches) return

        if (vendorController == null) {
            vendorController = try {
                AidexXController.getInstance(info)
            } catch (t: Throwable) {
                Log.e(TAG, "Vendor controller init failed: ${t.message}")
                null
            }
        }
        val controller = vendorController ?: return
        try {
            controller.setMac(info.address)
            if (!info.sn.isNullOrBlank()) {
                controller.setSn(info.sn)
            }
            if (!info.name.isNullOrBlank()) {
                controller.setName(info.name)
            }
        } catch (_: Throwable) {
        }
        if (!vendorRegistered) {
            vendorRegistered = true
            try {
                controller.setMessageCallback { op, success, data ->
                    handleVendorReceive(op, success, data)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Vendor setMessageCallback failed: ${t.message}")
            }
            try {
                controller.register()
            } catch (_: Throwable) {
            }
        }
        val paramsHex = info.params?.let { bytesToHex(it) }
        Log.i(
            TAG,
            "Vendor discovered: ${info.address} sn=${info.sn} name=${info.name} params=${paramsHex ?: "null"}"
        )
        if (mBluetoothGatt != null) {
            startVendorLongConnect("discover")
        }
    }


    private fun handleVendorReceive(operation: Int, success: Boolean, data: ByteArray?) {
        if (!vendorBleEnabled) return
        if (!vendorLibAvailable) return
        Log.i(TAG, "Vendor MessageCallback: op=$operation success=$success data=${data?.let { bytesToHex(it) } ?: "null"} (${data?.size ?: 0} bytes)")
        val payload: ByteArray?
        val resCode: Int
        if (operation in 1..3 || data == null || data.isEmpty()) {
            payload = data
            resCode = 1
        } else {
            resCode = data[0].toInt() and 0xFF
            payload = if (data.size > 1) data.copyOfRange(1, data.size) else ByteArray(0)
        }
        val message = BleMessage(operation, success, payload, resCode, BleMessage.MessageType.NORMAL)
        handleVendorMessage(message)
    }

    private fun handleVendorMessage(message: BleMessage) {
        if (!vendorBleEnabled) return
        if (!vendorLibAvailable) return
        val data = message.data ?: return
        val now = System.currentTimeMillis()
        Log.i(TAG, "Vendor message: op=${message.operation} success=${message.isSuccess} resCode=${message.resCode} data=${bytesToHex(data)}")
        when (message.operation) {
            AidexXOperation.AUTO_UPDATE_FULL_HISTORY -> {
                if (message.isSuccess) handleVendorInstant(data, now, "auto")
            }
            AidexXOperation.GET_BROADCAST_DATA -> {
                if (message.isSuccess) handleVendorBroadcast(data, now, "vendor-gatt")
            }
            AidexXOperation.RESET -> {
                Log.i(TAG, "Vendor RESET response: success=${message.isSuccess} resCode=${message.resCode}")
            }
            AidexXOperation.SET_NEW_SENSOR -> {
                Log.i(TAG, "Vendor NEW_SENSOR response: success=${message.isSuccess} resCode=${message.resCode}")
            }
            AidexXOperation.CLEAR_STORAGE -> {
                Log.i(TAG, "Vendor CLEAR_STORAGE response: success=${message.isSuccess} resCode=${message.resCode}")
            }
            AidexXOperation.GET_HISTORIES -> {
                Log.i(TAG, "Vendor GET_HISTORIES response: success=${message.isSuccess} data=${bytesToHex(data)} (${data.size} bytes)")
                if (message.isSuccess && data.isNotEmpty()) {
                    // Try to parse each history record from the payload.
                    // The vendor parser expects individual record payloads.
                    try {
                        val entity = parseVendorInstantPayload(data, now)
                        if (entity != null) {
                            Log.i(TAG, "GET_HISTORIES: parsed glucose=${entity.glucoseMgDl} mg/dL offset=${entity.timeOffsetMinutes}min")
                            storeAutoFromSource(entity.glucoseMgDl, entity.timeOffsetMinutes, now, "vendor-history", fromVendor = true)
                        } else {
                            Log.d(TAG, "GET_HISTORIES: native parser returned null for payload")
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "GET_HISTORIES: parse failed: ${t.message}")
                    }
                }
            }
            else -> {
                Log.d(TAG, "Vendor unhandled op=${message.operation} success=${message.isSuccess}")
            }
        }
    }

    private fun handleVendorInstant(payload: ByteArray, now: Long, source: String) {
        if (!vendorBleEnabled) return
        val entity = parseVendorInstantPayload(payload, now) ?: return
        storeAutoFromSource(entity.glucoseMgDl, entity.timeOffsetMinutes, now, source, fromVendor = true)
    }

    private fun handleVendorBroadcast(payload: ByteArray, now: Long, source: String) {
        if (!vendorBleEnabled) return
        val entity = parseVendorBroadcastPayload(payload, now) ?: return
        storeAutoFromSource(entity.glucoseMgDl, entity.timeOffsetMinutes, now, source, fromVendor = true)
    }

    /**
     * Safely call vendorAdapter.onConnectSuccess() with crash-loop protection.
     * 
     * CRITICAL: The native Ble::onConnectSuccess() in libblecomm-lib.so SIGSEGV-crashes if called
     * before the native state machine is ready. The native lib must have called executeConnect()
     * first — this sets up an internal connection context struct. Without it, onConnectSuccess()
     * at offset +100 dereferences a null pointer → SIGSEGV → process kill → crash loop.
     *
     * Guards (in order):
     * 1. vendorExecuteConnectReceived — native lib must have called executeConnect() first
     * 2. vendorRegistered — controller.register() must have linked controller to Ble singleton
     * 3. Crash-loop count — backs off after VENDOR_MAX_CRASH_RETRIES consecutive failures
     * 4. try/catch — catches JNI exceptions (but NOT native SIGSEGV)
     * 5. Sets vendorNativeReady=true on success (gates onReceiveData forwarding)
     */
    private fun safeCallOnConnectSuccess(caller: String) {
        val adapter = vendorAdapter
        if (adapter == null) {
            Log.w(TAG, "safeCallOnConnectSuccess($caller): vendorAdapter is null, skipping.")
            return
        }
        if (!vendorExecuteConnectReceived) {
            Log.w(TAG, "safeCallOnConnectSuccess($caller): BLOCKED — native lib has not called executeConnect() yet. " +
                    "Internal Ble connection context is not allocated. Calling onConnectSuccess() would SIGSEGV.")
            return
        }
        if (!vendorRegistered) {
            Log.w(TAG, "safeCallOnConnectSuccess($caller): BLOCKED — controller not registered. " +
                    "Ble singleton has no registered controller to dispatch to.")
            return
        }
        if (vendorConnectSuccessCrashCount >= VENDOR_MAX_CRASH_RETRIES) {
            Log.e(TAG, "safeCallOnConnectSuccess($caller): BLOCKED — $vendorConnectSuccessCrashCount consecutive failures. " +
                    "Native Ble object appears corrupt. Will retry on next fresh connection.")
            return
        }
        try {
            Log.i(TAG, "safeCallOnConnectSuccess($caller): Calling vendorAdapter.onConnectSuccess()...")
            adapter.onConnectSuccess()
            vendorNativeReady = true
            vendorConnectSuccessCrashCount = 0 // reset on success
            Log.i(TAG, "safeCallOnConnectSuccess($caller): onConnectSuccess() returned OK. vendorNativeReady=true")
        } catch (t: Throwable) {
            vendorConnectSuccessCrashCount++
            vendorNativeReady = false
            Log.e(TAG, "safeCallOnConnectSuccess($caller): onConnectSuccess() THREW (crash #$vendorConnectSuccessCrashCount): ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun startVendorLongConnect(reason: String) {
        if (!vendorBleEnabled) return
        if (!vendorLibAvailable) return
        if (!vendorGattNotified) return
        val controller = vendorController ?: return
        try {
            controller.setDynamicMode(1)
            controller.setAutoUpdate()
            controller.getBroadcastDataWithLog()
            Log.i(TAG, "Vendor long-connect started ($reason)")
        } catch (t: Throwable) {
            Log.e(TAG, "Vendor long-connect failed: ${t.message}")
        }
    }

    private fun enqueueVendorWrite(uuid: Int, data: ByteArray) {
        vendorWriteQueue.add(VendorWrite(uuid, data))
        if (!vendorWriteActive) {
            sendNextVendorWrite()
        }
    }

    private fun sendNextVendorWrite() {
        if (vendorWriteActive) return
        if (vendorWriteQueue.isEmpty()) return
        val next = vendorWriteQueue.removeFirst()
        val gatt = mBluetoothGatt
        if (gatt == null) {
            vendorWriteQueue.addFirst(next)
            return
        }
        val characteristic = vendorAdapter?.getCharacteristic(next.uuid)
        if (characteristic == null) {
            Log.w(TAG, "Vendor write: characteristic 0x${String.format("%04X", next.uuid)} not found")
            return
        }
        characteristic.value = next.data
        characteristic.writeType = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val ok = gatt.writeCharacteristic(characteristic)
        Log.i(TAG, "Vendor write [0x${Integer.toHexString(next.uuid)}] data=${bytesToHex(next.data)} ok=$ok (queueRemaining=${vendorWriteQueue.size})")
        vendorWriteActive = ok
        if (!ok) {
            vendorWriteQueue.addFirst(next)
            handshakeHandler.postDelayed({ sendNextVendorWrite() }, 200L)
        }
    }

    private fun vendorRead(uuid: Int) {
        val gatt = mBluetoothGatt ?: return
        val characteristic = vendorAdapter?.getCharacteristic(uuid) ?: return
        gatt.readCharacteristic(characteristic)
    }

    private fun vendorWrite(uuid: Int, data: ByteArray) {
        enqueueVendorWrite(uuid, data)
    }

    private fun uuidToShort(uuid: UUID): Int {
        val str = uuid.toString()
        return if (str.length >= 8) {
            str.substring(4, 8).toInt(16)
        } else {
            0
        }
    }

    private inner class VendorBleAdapter(private val context: Context) : BleAdapter() {
        private val deviceStore = BluetoothDeviceStore()
        private val characteristics = HashMap<Int, BluetoothGattCharacteristic>()

        init {
            instance = this
        }

        fun recordDevice(device: BluetoothDevice?) {
            if (device != null) {
                deviceStore.add(device)
            }
        }

        fun refreshCharacteristics(gatt: BluetoothGatt) {
            characteristics.clear()
            val serviceF000 = gatt.getService(SERVICE_F000)
            if (serviceF000 != null) {
                for (characteristic in serviceF000.characteristics) {
                    val id = uuidToShort(characteristic.uuid)
                    characteristics[id] = characteristic
                }
            }
            
            val serviceFF30 = gatt.getService(SERVICE_FF30)
            if (serviceFF30 != null) {
                for (characteristic in serviceFF30.characteristics) {
                    val id = uuidToShort(characteristic.uuid)
                    characteristics[id] = characteristic
                }
            }
        }

        fun getCharacteristic(id: Int): BluetoothGattCharacteristic? {
            val c = characteristics[id]
            if (c == null) Log.v(TAG, "Vendor getCharacteristic(0x${Integer.toHexString(id)}) -> NULL (map size=${characteristics.size})")
            return c
        }

        fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
            val id = uuidToShort(characteristic.uuid)
            Log.d(TAG, "Vendor RX [0x${Integer.toHexString(id)}]: ${bytesToHex(data)}")
            if (!vendorNativeReady) {
                Log.d(TAG, "Vendor RX [0x${Integer.toHexString(id)}]: skipped, native not ready")
                return
            }
            try {
                onReceiveData(id, data)
            } catch (t: Throwable) {
                Log.e(TAG, "Vendor RX [0x${Integer.toHexString(id)}]: onReceiveData crashed: ${t.message}")
            }
        }

        override fun executeConnect(str: String) {
            Log.i(TAG, "executeConnect: Native lib requested connect to $str")
            connectedAddress = str
            mActiveDeviceAddress = str
            deviceStore.getDeviceMap()[str]?.let { mActiveBluetoothDevice = it }
            vendorConnectPending = true
            vendorExecuteConnectReceived = true  // Native lib has set up its internal connection state machine
            val existing = mBluetoothGatt
            if (existing != null && existing.device?.address == str) {
                // GATT already exists for this device.
                // If services+notifications are ready, signal success immediately.
                if (vendorServicesReady && vendorGattNotified) {
                    Log.i(TAG, "executeConnect: GATT+notifications ready for $str, signaling onConnectSuccess immediately.")
                    disregardDisconnectsUntil = System.currentTimeMillis() + 5000L
                    safeCallOnConnectSuccess("executeConnect-immediate")
                } else {
                    Log.i(TAG, "executeConnect: GATT exists for $str but not fully ready (services=$vendorServicesReady notified=$vendorGattNotified). Waiting for setup.")
                    // onConnectSuccess will be called when onDescriptorWrite(F003) fires
                }
                return
            }
            if (connectInProgress) {
                Log.d(TAG, "executeConnect: Connection already in progress for $str.")
                return
            }
            val scheduled = connectDevice(0)
            if (!scheduled) {
                vendorConnectPending = false
                try {
                    onConnectFailure()
                } catch (t: Throwable) {
                    Log.e(TAG, "executeConnect: onConnectFailure() crashed: ${t.message}")
                }
            }
        }

        override fun executeDisconnect() {
            vendorConnectPending = false
            disconnect()
        }

        override fun executeReadCharacteristic(i: Int) {
            vendorRead(i)
        }

        override fun executeStartScan() {
            startBroadcastScan("vendor-start")
        }

        override fun executeStopScan() {
            stopBroadcastScan("vendor-stop", found = false)
        }

        override fun executeWrite(bArr: ByteArray) {
            try {
                val uuid = getCharacteristicUUID()
                vendorWrite(uuid, bArr)
                checkAndBond()
            } catch (t: Throwable) {
                Log.e(TAG, "executeWrite: crashed: ${t.message}")
            }
        }

        override fun executeWriteCharacteristic(i: Int, bArr: ByteArray) {


            vendorWrite(i, bArr)
            checkAndBond()
        }

        private fun checkAndBond() {
            // User tip: "Upon connection... send one command and immediately request bond."
            // If the vendor library is writing (handshake), and we are not bonded, trigger it now.
            val device = mBluetoothGatt?.device ?: return
            val state = device.bondState
            if (state == BluetoothDevice.BOND_NONE) {
                Log.i(TAG, "Vendor write detected. Requesting immediate bond (User Tip Strategy).")
                device.createBond()
            } else {
                 Log.d(TAG, "Vendor write detected, but already bonded ($state). Skipping bond request.")
            }
        }

        override fun getDeviceStore(): BluetoothDeviceStore = deviceStore

        override fun isReadyToConnect(str: String): Boolean {
            return !connectInProgress && (mBluetoothGatt == null || mBluetoothGatt?.device?.address == str)
        }

        override fun setDiscoverCallback() {
            BleController.setDiscoveredCallback { info -> onVendorDiscovered(info) }
        }

        override fun startBtScan(isPeriodic: Boolean) {
            startBroadcastScan("vendor-startBt")
        }

        override fun stopBtScan(isPeriodic: Boolean) {
            stopBroadcastScan("vendor-stopBt", found = false)
        }
    }

    private fun ensureConnected(reason: String) {
        if (mBluetoothGatt != null) return
        Log.i(TAG, "Ensuring GATT connection ($reason)")
        connectDevice(0)
    }

    override fun matchDeviceName(deviceName: String?, address: String?): Boolean {
        if (deviceName == null) return false
        return deviceName.contains(SerialNumber, ignoreCase = true)
    }

    // Scanning is handled by SensorBluetooth / Wizard.

    // --- CONNECTION ---

    override fun connectDevice(delayMillis: Long): Boolean {
        // In Broadcast Only mode, skip GATT connection entirely
        if (isBroadcastOnlyMode()) {
            Log.d(TAG, "connectDevice: skip in Broadcast Only mode")
            return false
        }
        if (connectInProgress || mBluetoothGatt != null) {
            Log.d(TAG, "connectDevice: skip (inProgress=$connectInProgress, gatt=${mBluetoothGatt != null})")
            return false
        }
        connectInProgress = true
        val scheduled = super.connectDevice(delayMillis)
        if (!scheduled) {
            connectInProgress = false
        }
        return scheduled
    }

    override fun reconnect(now: Long): Boolean {
        scheduleBroadcastScan("reconnect", forceImmediate = true)
        // In Broadcast Only mode, don't GATT reconnect - just scan for broadcasts
        if (isBroadcastOnlyMode()) {
            Log.d(TAG, "reconnect: skip GATT in Broadcast Only mode, broadcast scan scheduled")
            return true
        }
        return super.reconnect(now)
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        connectInProgress = false
        
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            val now = System.currentTimeMillis()
            connectTime = now
            constatstatusstr = "Connected"
            
            // Leave start time unset until we have a valid offset from broadcast/history.
            
            Log.i(TAG, "Connected to ${gatt.device.address}. Requesting MTU 512...")

            handshakeStep = 0
            handshakePlan = emptyList()
            handshakePlanLabel = "none"
            officialFlowStage = OfficialFlowStage.BOOTSTRAP
            expectedF002ResponseOps = emptySet()
            waitAnyF002Response = false
            dynamicIV = null
            ivLocked = false
            ivLockedFromBroadcast = false
            bondRequested = false
            bondWaitUntilMs = 0L
            justBondedThisSession = false
            handshakePrimingInProgress = false
            pendingHandshakePrimingTimeout = null
            cancelHandshakeTimers()
            connectedAddress = gatt.device.address
            sessionStartMs = now
            broadcastSeenInSession = lastBroadcastTime >= (sessionStartMs - BROADCAST_REFERENCE_MS)
            if (wantsBroadcastScan()) {
                scheduleBroadcastScan("connect", forceImmediate = true)
            }

            if (dataptr != 0L) {
                Natives.setDeviceAddress(dataptr, gatt.device.address)
            }
        // onConnectSuccess moved to onServicesDiscovered to ensure characteristics are ready.
        if (vendorBleEnabled && vendorStarted && vendorRegistered && vendorController != null && vendorConnectPending) {
            vendorGattConnected = true
        }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                gatt.requestMtu(512)
            }
            // Some devices need MTU change to finish before discovery
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                gatt.discoverServices()
            }, 600)
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            connectTime = 0L
            constatstatusstr = "Disconnected"
            Log.i(TAG, "Disconnected.")
            cancelHandshakeTimers()
            connectedAddress = null
            bondRequested = false
            bondWaitUntilMs = 0L
            justBondedThisSession = false
            handshakePrimingInProgress = false
            pendingHandshakePrimingTimeout = null
            waitAnyF002Response = false
            vendorWriteQueue.clear()
            vendorWriteActive = false
            vendorGattConnected = false
            vendorGattNotified = false
            vendorConnectPending = false
            vendorNativeReady = false
            vendorExecuteConnectReceived = false
            vendorConnectSuccessCrashCount = 0  // reset crash counter on fresh disconnect
            notifyVendorDisconnected(status)
            close()
        }
    }

    private fun notifyVendorDisconnected(status: Int) {
        if (!vendorBleEnabled) return
        if (!vendorStarted || !vendorRegistered || vendorController == null) return
        if (!vendorGattConnected) return
        try {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                vendorAdapter?.onDisconnected()
            } else {
                vendorAdapter?.onConnectFailure()
            }
        } catch (_: Throwable) {
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.i(TAG, "MTU Changed to $mtu (Status: $status)")
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            vendorServicesReady = true
            if (vendorBleEnabled) {
                Log.i(TAG, "Services Discovered. Refreshing Vendor Characteristics.")
                try {
                    vendorAdapter?.refreshCharacteristics(gatt)

                    if (vendorLibAvailable) {
                        try {
                            // 1. Ensure Vendor is Registered (e.g. Clean Start or Reconnect without Scan)
                            if (!vendorRegistered) {
                                val address = gatt?.device?.address
                                if (address != null) {
                                    val cached = lastDiscoveredInfo
                                    val info = if (cached != null && cached.address == address) {
                                        Log.w(TAG, "Clean Start: Registering vendor with cached info for $address")
                                        cached
                                    } else {
                                        val savedAddress = readStringPref("lastVendorAddress", null)
                                        val savedParams = readStringPref("lastVendorParams", null)
                                        if (savedAddress == address && !savedParams.isNullOrEmpty()) {
                                            Log.w(TAG, "Clean Start: Registering vendor with persisted params for $address")
                                            val loaded = BleControllerInfo()
                                            loaded.address = address
                                            loaded.name = gatt.device.name ?: "AiDEX"
                                            loaded.params = try { hexStringToByteArray(savedParams) } catch (e: Exception) { null }
                                            loaded.sn = SerialNumber
                                            loaded
                                        } else {
                                            Log.w(TAG, "Clean Start: Registering vendor with basic info (no params) for $address")
                                            val basic = BleControllerInfo()
                                            basic.address = address
                                            basic.name = gatt.device.name ?: "AiDEX"
                                            basic
                                        }
                                    }
                                    onVendorDiscovered(info)
                                }
                            }

                            // 2. Do NOT signal onConnectSuccess here.
                            // It will be signaled from onDescriptorWrite(F003) after all
                            // CCCDs are written, ensuring the native lib starts its handshake
                            // only when we're ready to forward BLE data.
                            Log.i(TAG, "onServicesDiscovered: Vendor registered=$vendorRegistered. onConnectSuccess deferred to onDescriptorWrite(F003).")

                        } catch (e: Exception) {
                             Log.e(TAG, "Clean Start Logic Failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Vendor refreshCharacteristics failed: ${e.message}")
                }
            }

            if (wantsAuto() || wantsRaw()) {
                if (vendorBleEnabled) {
                     Log.i(TAG, "Services Discovered. Enabling vendor notifications before delegating.")
                     enableVendorNotifications(gatt)
                     Log.i(TAG, "Services Discovered. Skipping internal handshake (delegating to Vendor Lib).")
                } else {
                     Log.i(TAG, "Services Discovered. Starting Handshake.")
                     startHandshake(gatt)
                }
            }
        } else {
            Log.e(TAG, "Service Discovery Failed: $status")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableVendorNotifications(gatt: BluetoothGatt) {
        val sF000 = gatt.getService(SERVICE_F000)
        val cF001 = sF000?.getCharacteristic(CHAR_F001)
        if (cF001 != null) {
            setCharacteristicNotification(gatt, cF001, true)
        } else {
            Log.e(TAG, "F001 not found!")
        }

        // Also enable FF31 notifications on the FF30 vendor private service
        // so we receive responses for reset/newSensor/shelfMode commands sent via FF32.
        val sFF30 = gatt.getService(SERVICE_FF30)
        val cFF31 = sFF30?.getCharacteristic(CHAR_FF31)
        if (cFF31 != null) {
            // Queue after F001 CCCD write completes (descriptor writes are serialized by Android BLE stack)
            scanHandler.postDelayed({
                try {
                    setCharacteristicNotification(gatt, cFF31, true)
                    Log.i(TAG, "FF31 notifications enabled on FF30 service")
                } catch (t: Throwable) {
                    Log.w(TAG, "FF31 notification enable failed: ${t.message}")
                }
            }, 500L)
        } else {
            Log.w(TAG, "FF31 not found (FF30 service may not be present on this firmware)")
        }
    }

    // --- BROADCAST SCANNING ---

    private val broadcastScanRunnable = Runnable { startBroadcastScan("scheduled") }
    private val broadcastScanStopRunnable = Runnable { stopBroadcastScan("timeout", found = false) }

    private fun computeNextScanDelayMs(): Long {
        if (lastBroadcastTime == 0L) return 0L
        return broadcastScanBaseIntervalMs()
            .coerceAtMost(BROADCAST_SCAN_MAX_INTERVAL_MS)
    }

    private fun scheduleBroadcastScan(reason: String, forceImmediate: Boolean = false) {
        if (!broadcastEnabled) return
        if (!wantsBroadcastScan()) return
        if (broadcastScanActive) {
            val age = android.os.SystemClock.elapsedRealtime() - broadcastScanStartedAtElapsed
            if (broadcastScanStartedAtElapsed > 0L && age > (BROADCAST_SCAN_WINDOW_MS + 2_000L)) {
                Log.w(TAG, "Broadcast scan stuck (${age}ms). Forcing reschedule.")
                broadcastScanActive = false
            } else {
                return
            }
        }

        var delay = if (forceImmediate) 0L else computeNextScanDelayMs()

        // Fix for "Lost" Data Gaps:
        // If we are consistently missing broadcasts (e.g. out of phase with sensor interval),
        // retry aggressively (15s) for a few attempts to break the pattern and catch the window.
        if (!forceImmediate && delay > 15_000L && broadcastScanMisses in 1..5) {
            delay = 15_000L
        }
        
        // Use Handler for short delays or when system is likely awake
        scanHandler.removeCallbacks(broadcastScanRunnable)
        scanHandler.postDelayed(broadcastScanRunnable, delay)

        // Additionally schedule an AlarmManager wake-up for longer delays to ensure we wake from deep sleep.
        // Short delays (<10s) usually mean the system is already awake or just about to be.
        if (delay > 10_000L) {
            scheduleScanAlarm(delay)
        }

        Log.d(TAG, "Broadcast scan scheduled in ${delay / 1000}s (reason=$reason, miss=$broadcastScanMisses)")
    }

    @SuppressLint("MissingPermission")
    internal fun startBroadcastScan(reason: String) {
        val isVendorScan = reason == "vendor-start"
        if (!broadcastEnabled && !isVendorScan) return
        if (broadcastScanActive || (!isVendorScan && !wantsBroadcastScan())) return

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return

        val scanner = adapter.bluetoothLeScanner ?: return
        broadcastScanner = scanner
        acquireBroadcastWakeLock()

        if (broadcastScanCallback == null) {
            broadcastScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val address = device.address
                    val targetAddress = scanTargetAddress()

                    if (targetAddress != null) {
                        if (address != targetAddress) return
                    } else {
                        // Avoid latching onto the first 0x0059 advertiser (e.g. Bubble mini).
                        val name = device.name
                        val matches = if (name != null) {
                            matchDeviceName(name, address)
                        } else {
                            val advName = result.scanRecord?.bytes?.let { extractLocalName(it) }
                            advName?.contains(SerialNumber, ignoreCase = true) == true
                        }
                        if (!matches) return
                    }

                    connectedAddress = address
                    mActiveDeviceAddress = address
                    mActiveBluetoothDevice = device
                    handleScanResult(result)
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

        // Build filters: device address if known, otherwise open scan (name check happens in callback)
        val filterBuilder = ScanFilter.Builder()
        val targetAddr = scanTargetAddress()
        if (targetAddr != null) {
            filterBuilder.setDeviceAddress(targetAddr)
        }
        val filters = arrayListOf(filterBuilder.build())
        
        // Use LOW_POWER mode to reduce scan frequency and logcat spam
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner.startScan(filters, settings, broadcastScanCallback)
            broadcastScanActive = true
            broadcastScanStartedAtElapsed = android.os.SystemClock.elapsedRealtime()
            scanHandler.removeCallbacks(broadcastScanStopRunnable)
            scanHandler.postDelayed(broadcastScanStopRunnable, BROADCAST_SCAN_WINDOW_MS)
            // Don't update constatstatusstr here - it's transient and makes notification static
            // The UI uses getDetailedBleStatus() for real-time status display instead
            Log.d(TAG, "Broadcast scan started ($reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast scan start failed: ${e.message}")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val bytes = result.scanRecord?.bytes ?: return
        if (!java.util.Arrays.equals(lastScanRecordBytes, bytes)) {
            lastScanRecordBytes = bytes
            storeVendorParams(bytes)
        }
        if (vendorBleEnabled && vendorStarted) {
            try {
                vendorAdapter?.recordDevice(result.device)
                vendorAdapter?.onAdvertiseWithAndroidRawBytes(
                    result.device.address,
                    result.rssi,
                    bytes
                )
            } catch (_: Throwable) {
            }
        }
        // Critical Fix: If we find the device but the vendor lib isn't registered yet (fresh start),
        // we must trigger discovery to initialize the controller and set vendorRegistered = true.
        if (vendorBleEnabled && !vendorRegistered && wantsAuto()) {
             val device = result.device
             val name = device.name ?: "AiDEX"
             if (SerialNumber.isNotEmpty()) {
                 val info = BleControllerInfo(
                     1, 
                     device.address, 
                     name, 
                     SerialNumber, 
                     result.rssi, 
                     bytes
                 )
                 Log.i(TAG, "Scan Result: Triggering delayed onVendorDiscovered for ${device.address}")
                 onVendorDiscovered(info)
             }
        }

        onScanRecord(bytes)
    }

    @SuppressLint("MissingPermission")
    private fun stopBroadcastScan(reason: String, found: Boolean) {
        if (!broadcastEnabled) return
        if (reason == "disconnect" || reason.startsWith("mode-raw")) {
            scanHandler.removeCallbacks(broadcastScanRunnable)
            scanHandler.removeCallbacks(broadcastScanStopRunnable)
            cancelScanAlarm()
        }
        if (!broadcastScanActive) {
            if (reason != "disconnect" && wantsBroadcastScan()) {
                broadcastScanMisses = if (found) 0 else (broadcastScanMisses + 1)
                scheduleBroadcastScan("post-$reason-inactive")
            }
            return
        }

        try {
            broadcastScanner?.stopScan(broadcastScanCallback)
        } catch (_: Exception) {
        }
        broadcastScanActive = false
        broadcastScanStartedAtElapsed = 0L
        releaseBroadcastWakeLock()
        scanHandler.removeCallbacks(broadcastScanStopRunnable)

        if (reason != "disconnect" && wantsBroadcastScan()) {
            broadcastScanMisses = if (found) 0 else (broadcastScanMisses + 1)
            scheduleBroadcastScan("post-$reason")
            Log.d(TAG, "Broadcast scan stopped ($reason, found=$found, miss=$broadcastScanMisses)")
        }
    }

    private fun scheduleScanAlarm(delayMs: Long) {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, AiDexScanReceiver::class.java).apply {
            action = AiDexScanReceiver.ACTION_AIDEX_SCAN
            putExtra(AiDexScanReceiver.EXTRA_SERIAL, SerialNumber)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            SerialNumber.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d(TAG, "Scan alarm set for $SerialNumber in ${delayMs / 1000}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set scan alarm: ${e.message}")
        }
    }

    private fun cancelScanAlarm() {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, AiDexScanReceiver::class.java).apply {
            action = AiDexScanReceiver.ACTION_AIDEX_SCAN
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            SerialNumber.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Scan alarm cancelled for $SerialNumber")
        }
    }

    private fun acquireBroadcastWakeLock() {
        if (broadcastWakeLock?.isHeld == true) return
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AiDexSensor:BroadcastScan")
        wl.setReferenceCounted(false)
        wl.acquire(BROADCAST_SCAN_WINDOW_MS + 2_000L)
        broadcastWakeLock = wl
    }

    private fun releaseBroadcastWakeLock() {
        try {
            broadcastWakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {
        } finally {
            broadcastWakeLock = null
        }
    }

    private fun onBroadcastUpdated() {
        if (!broadcastEnabled) return
        broadcastScanMisses = 0
        if (broadcastScanActive) {
            stopBroadcastScan("broadcast", found = true)
        } else if (wantsBroadcastScan()) {
            scheduleBroadcastScan("broadcast")
        }
    }

    private fun updateStartTimeFromOffset(offsetMinutes: Long, now: Long) {
        if (offsetMinutes <= 0L || offsetMinutes > 60L * 24L * 14L) return
        val inferredStart = now - (offsetMinutes * 60_000L)
        if (sensorstartmsec == 0L || kotlin.math.abs(sensorstartmsec - inferredStart) > (10L * 60_000L)) {
            sensorstartmsec = inferredStart
            if (dataptr != 0L) {
                try {
                    Natives.aidexSetStartTime(dataptr, sensorstartmsec)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun extractLocalName(scanRecord: ByteArray): String? {
        var offset = 0
        while (offset < scanRecord.size - 1) {
            val len = scanRecord[offset].toInt() and 0xFF
            if (len == 0) break
            val type = scanRecord[offset + 1].toInt() and 0xFF
            if (type == 0x09 || type == 0x08) { // Complete / Shortened local name
                val start = offset + 2
                val endExclusive = (start + len - 1).coerceAtMost(scanRecord.size)
                if (endExclusive > start) {
                    return try {
                        String(scanRecord, start, endExclusive - start, Charsets.UTF_8)
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            offset += len + 1
        }
        return null
    }

    override fun onScanRecord(scanRecord: ByteArray) {
        if (!broadcastEnabled) {
            if (!broadcastDisabledLogged) {
                broadcastDisabledLogged = true
                Log.i(TAG, "Broadcast parsing disabled for AiDex.")
            }
            return
        }
        try {
            // Parse Manufacturer Data (ID: 0x59 = 89 for AiDex/MicroTech)
            // Manual parsing of raw bytes: Len, Type, ID_LO, ID_HI, Data...
            var offset = 0
            while (offset < scanRecord.size - 2) {
                val len = scanRecord[offset].toInt() and 0xFF
                if (len == 0) break
                val type = scanRecord[offset + 1].toInt() and 0xFF
                
                if (type == 0xFF) { // Manufacturer Specific Data
                    // Relaxed check: Allow any manufacturer ID if the payload looks like a broadcast packet.
                    // Previously checked for 0x59 (AiDex/MicroTech), but variants (Linx, etc.) use others.
                    // Format: [Len] [0xFF] [ID_LO] [ID_HI] [Data...]
                    if (offset + 3 < scanRecord.size) {
                         // We rely on parseBroadcastData to validate the payload structure/content.
                         // This allows broad compat for variants that wrap the same glucose payload in different headers.
                        val dataLen = len - 3 
                        if (offset + 4 + dataLen <= scanRecord.size) {
                             val data = ByteArray(dataLen)
                             System.arraycopy(scanRecord, offset + 4, data, 0, dataLen)
                             val updated = parseBroadcastData(data)
                             if (updated) onBroadcastUpdated()
                        }
                    }
                }
                offset += len + 1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing scan record: ${e.message}")
        }
    }

    private fun parseBroadcastData(data: ByteArray): Boolean {
         if (!broadcastEnabled) return false
         if (data.size < 6) return false

         val now = System.currentTimeMillis()
         if (wantsAuto()) {
             val vendor = parseVendorBroadcastPayload(data, now)
             if (vendor != null) {
                 if (!shouldAcceptOffset(vendor.timeOffsetMinutes.toLong(), now, "vendor-broadcast")) {
                     return false
                 }
                 lastBroadcastGlucose = vendor.glucoseMgDl
                 lastBroadcastTime = now
                 lastBroadcastOffsetMinutes = vendor.timeOffsetMinutes.toLong()
                 if (now >= sessionStartMs) {
                     broadcastSeenInSession = true
                 }
                 writeFloatPref("lastBroadcastGlucose", lastBroadcastGlucose)
                 writeLongPref("lastBroadcastTime", lastBroadcastTime)
                 writeLongPref("lastBroadcastOffsetMinutes", lastBroadcastOffsetMinutes)
                 updateStartTimeFromOffset(lastBroadcastOffsetMinutes, now)
                 storeAutoFromSource(vendor.glucoseMgDl, vendor.timeOffsetMinutes, now, "vendor-broadcast", fromVendor = true)
                 onBroadcastUpdated()
                 return true
             }
         }

         // LinX (official) broadcast format (Manufacturer 0x0059, 20-byte payload):
         // bytes 0..3 : u32 little-endian timeOffsetMinutes
         // byte 4     : i8 trend
         // byte 5     : u8 glucose mg/dL
         val offsetMinutes = ((data[0].toLong() and 0xFF) or
             ((data[1].toLong() and 0xFF) shl 8) or
             ((data[2].toLong() and 0xFF) shl 16) or
             ((data[3].toLong() and 0xFF) shl 24)) and 0xFFFF_FFFFL
         val trend = data[4].toInt() // signed
         val glucoseMgDlInt = data[5].toInt() and 0xFF

         if (glucoseMgDlInt !in 30..500) return false
         if (!shouldAcceptOffset(offsetMinutes, now, "manual-broadcast")) return false

        lastBroadcastGlucose = glucoseMgDlInt.toFloat()
        lastBroadcastTime = now
        lastBroadcastOffsetMinutes = offsetMinutes
        if (now >= sessionStartMs) {
            broadcastSeenInSession = true
        }

         writeFloatPref("lastBroadcastGlucose", lastBroadcastGlucose)
         writeLongPref("lastBroadcastTime", lastBroadcastTime)
         writeLongPref("lastBroadcastOffsetMinutes", lastBroadcastOffsetMinutes)

         // Use offsetMinutes to establish an approximate start time; this improves native ID alignment.
         updateStartTimeFromOffset(offsetMinutes, now)

        Log.d(TAG, "AIDEX-BCAST: off=${offsetMinutes}m trend=$trend glucose=$glucoseMgDlInt mg/dL")
        
        // Update notification status when receiving broadcast data
        if (broadcastOnlyConnection) {
            constatstatusstr = "Receiving"
        }
        
        storeBroadcastIfNeeded(lastBroadcastGlucose, offsetMinutes, now)
        storeRawFromBroadcastIfNeeded(lastBroadcastGlucose, offsetMinutes, now)
        return true
    }

    private fun storeBroadcastIfNeeded(glucoseMgDl: Float, offsetMinutes: Long, timeMs: Long) {
        if (!broadcastEnabled) return
        // Mode 4 (Broadcast Only) stores broadcast data as Auto readings since that's its only source
        if (!wantsAuto() && !isBroadcastOnlyMode()) return

        if (lastVendorTime != 0L && (timeMs - lastVendorTime) < BROADCAST_MIN_STORE_INTERVAL_MS) return

        // Dedupe on offset rather than wall-clock to prevent "repeat" values looking like new readings.
        if (lastBroadcastStoredOffsetMinutes != 0L && offsetMinutes < lastBroadcastStoredOffsetMinutes) {
            Log.i(TAG, "Broadcast offset went backwards ($lastBroadcastStoredOffsetMinutes -> $offsetMinutes). Resetting dedupe.")
            lastBroadcastStoredOffsetMinutes = 0L
        }

        val stored = storeAutoFromSource(
            glucoseMgDl,
            offsetMinutes.toInt(),
            timeMs,
            "broadcast",
            fromVendor = false
        )
        if (stored) {
            lastBroadcastStoredOffsetMinutes = offsetMinutes
        }
    }

    private fun storeRawFromBroadcastIfNeeded(glucoseMgDl: Float, offsetMinutes: Long, timeMs: Long) {
        if (!broadcastEnabled) return
        if (!rawBroadcastFallbackEnabled) return
        if (!wantsRaw()) return
        if (glucoseMgDl !in 30f..500f) return
        if (!shouldAcceptOffset(offsetMinutes, timeMs, "raw-broadcast")) return

        if (lastRawTime != 0L && (timeMs - lastRawTime) < BROADCAST_MIN_STORE_INTERVAL_MS) return

        if (lastRawBroadcastOffsetMinutes != 0L && offsetMinutes < lastRawBroadcastOffsetMinutes) {
            Log.i(TAG, "Raw-broadcast offset went backwards ($lastRawBroadcastOffsetMinutes -> $offsetMinutes). Resetting dedupe.")
            lastRawBroadcastOffsetMinutes = 0L
        }

        if (offsetMinutes <= lastRawBroadcastOffsetMinutes) return

        Log.i(TAG, ">>> SUCCESS AIDEX: RawFromBroadcast=$glucoseMgDl mg/dL")
        storeAidexReading(byteArrayOf(0), timeMs, glucoseMgDl, 0f)
        lastRawMgDl = glucoseMgDl
        lastRawTime = timeMs
        writeFloatPref("lastRawMgDl", lastRawMgDl)
        writeLongPref("lastRawTime", lastRawTime)
        lastRawBroadcastOffsetMinutes = offsetMinutes
    }

    private fun shouldAcceptOffset(offsetMinutes: Long, now: Long, source: String): Boolean {
        if (offsetMinutes <= 0L || offsetMinutes > 60L * 24L * 14L) return false
        if (lastBroadcastOffsetMinutes > 0L && offsetMinutes + 2 < lastBroadcastOffsetMinutes) {
            Log.w(
                TAG,
                "Broadcast offset went backwards ($lastBroadcastOffsetMinutes -> $offsetMinutes); ignoring ($source)"
            )
            return false
        }
        if (now < sessionStartMs) return false
        return true
    }

    @SuppressLint("MissingPermission")
    private fun setCharacteristicNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, enabled: Boolean) {
        gatt.setCharacteristicNotification(characteristic, enabled)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            val value = if (!enabled) {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            } else {
                val props = characteristic.properties
                val supportsIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                val supportsNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0

                // AiDex/LinX: we must receive responses on F002.
                // Some firmware uses notifications, others indications. If both are supported, enable both (0x03).
                if (characteristic.uuid == CHAR_F002) {
                    // Prefer NOTIFY for F002 (matches advertised properties on most LinX firmwares).
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else if (supportsIndicate) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else if (supportsNotify) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
            }
            descriptor.value = value
            Log.d(TAG, "CCCD ${characteristic.uuid}: props=0x${String.format("%02X", characteristic.properties)} value=${bytesToHex(value)}")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startHandshake(gatt: BluetoothGatt) {
         val modeLabel = if (useOfficialHandshake) "official" else "legacy"
         handshakePlan = if (useOfficialHandshake) {
             buildOfficialHandshakePlan(gatt)
         } else {
             buildLegacyHandshakePlan()
         }
         if (useOfficialHandshake) {
             officialFlowStage = OfficialFlowStage.BOOTSTRAP
         }
         val bondState = gatt.device.bondState
         if (useOfficialHandshake && bondState == BluetoothDevice.BOND_NONE && !bondRequested) {
             val ok = gatt.device.createBond()
             bondRequested = true
             bondWaitUntilMs = System.currentTimeMillis() + BOND_WAIT_MS
             Log.i(TAG, "Bond requested (ok=$ok). Waiting up to ${BOND_WAIT_MS}ms before handshake.")
         } else if (bondState != BluetoothDevice.BOND_NONE) {
             bondWaitUntilMs = 0L
         }
         Log.i(
             TAG,
             "Starting Handshake Setup (F001 -> F002 -> F003) mode=$modeLabel plan=$handshakePlanLabel steps=${handshakePlan.size} bond=$bondState"
         )
         handshakeStep = 1
         handshakeRetries = 0
         expectedF002ResponseOps = emptySet()
         pendingIVCandidates.clear()
         val sF000 = gatt.getService(SERVICE_F000)
         
         val cF001 = sF000?.getCharacteristic(CHAR_F001)
         if (cF001 != null) {
             setCharacteristicNotification(gatt, cF001, true)
         } else {
             Log.e(TAG, "F001 not found!")
         }
         
         // RE-ENGINEERING FIX: Restore IV candidates from persistence on handshake start
         if (pendingIVCandidates.isEmpty()) {
             try {
                val sharePref = appContext.getSharedPreferences("Juggluco", Context.MODE_PRIVATE)
                val savedIvCandidates = sharePref.getString("aidex_iv_candidates", "")
                if (!savedIvCandidates.isNullOrEmpty()) {
                    savedIvCandidates.split(",").forEach { hex ->
                        if (hex.isNotEmpty()) {
                            val iv = hexBytes(hex)
                            if (iv.size == 16) {
                                pendingIVCandidates.add(iv)
                            }
                        }
                    }
                    Log.i(TAG, "Restored ${pendingIVCandidates.size} IV candidates from persistence (startHandshake).")
                }
             } catch (e: Exception) {
                Log.e(TAG, "Failed to restore IV candidates: ${e.message}")
             }
         }
    }

    private fun cancelHandshakeTimers() {
        pendingHandshakeRead?.let { handshakeHandler.removeCallbacks(it) }
        pendingHandshakeTimeout?.let { handshakeHandler.removeCallbacks(it) }
        pendingHandshakePrimingTimeout?.let { handshakeHandler.removeCallbacks(it) }
        pendingHandshakeRead = null
        pendingHandshakeTimeout = null
        pendingHandshakePrimingTimeout = null
        handshakeReadAttempts = 0
        handshakePrimingInProgress = false
    }

    private fun isHandshakeActive(): Boolean {
        return handshakeStep in 1..handshakePlan.size
    }

    private fun markHandshakeComplete(reason: String) {
        if (!isHandshakeActive()) return
        cancelHandshakeTimers()
        expectedF002ResponseOps = emptySet()
        handshakeStep = handshakePlan.size + 1
        if (useOfficialHandshake) {
            officialFlowStage = OfficialFlowStage.STREAMING
        }
        Log.i(TAG, "Handshake Complete ($reason) plan=$handshakePlanLabel")
    }

    private fun beginHandshakeAfterBondWait(gatt: BluetoothGatt) {
        bondWaitUntilMs = 0L
        if (bondRequested && gatt.device.bondState == BluetoothDevice.BOND_BONDED) {
            justBondedThisSession = true
        } else {
            justBondedThisSession = false
        }
        bondRequested = false

        if (useOfficialHandshake) {
            handshakePlan = buildOfficialHandshakePlan(gatt)
        }
        handshakeStep = 1
        handshakeRetries = 0
        expectedF002ResponseOps = emptySet()
        pendingIVCandidates.clear()
        Log.i(TAG, "Bond wait complete. Starting handshake plan=$handshakePlanLabel bond=${gatt.device.bondState}")
        beginHandshakeWithPriming(gatt)
    }

    private fun beginHandshakeWithPriming(gatt: BluetoothGatt) {

        if (useOfficialHandshake) {
            val sF000 = gatt.getService(SERVICE_F000)
            val cF002 = sF000?.getCharacteristic(CHAR_F002)
            if (cF002 != null && (cF002.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                handshakePrimingInProgress = true
                pendingHandshakePrimingTimeout?.let { handshakeHandler.removeCallbacks(it) }
                val ok = gatt.readCharacteristic(cF002)
                Log.i(TAG, "Handshake priming read F002 enqueued=$ok")
                if (ok) {
                    pendingHandshakePrimingTimeout = Runnable {
                        if (!handshakePrimingInProgress) return@Runnable
                        handshakePrimingInProgress = false
                        Log.w(TAG, "Handshake priming read timed out; proceeding.")
                        performHandshakeStep(gatt)
                    }
                    handshakeHandler.postDelayed(pendingHandshakePrimingTimeout!!, HANDSHAKE_PRIME_TIMEOUT_MS)
                    return
                }
                handshakePrimingInProgress = false
            }
        }
        performHandshakeStep(gatt)
    }

    private fun hexBytes(input: String): ByteArray {
        return hexStringToByteArray(input.replace(" ", ""))
    }

    private fun buildOfficialHandshakePlan(gatt: BluetoothGatt): List<HandshakeStep> {
        val bondState = gatt.device.bondState
        // 2026-02-05: Use bonded sequence when already bonded, unbonded when not.
        // Analysis of aidex_output_timed.txt shows:
        // - Bonded (12:14): B0/B1/94/A7/A6/A1... → sensor responds with history data
        // - New pair (12:26): F001 + FB/CA/96/97/B2 → sensor responds with session setup
        // The vendor library handles decryption internally - we just need to use the right
        // handshake sequence to get the sensor talking and pass data to vendor library.
        return if (bondState == BluetoothDevice.BOND_BONDED) {
            Log.i(TAG, "buildOfficialHandshakePlan: using BONDED sequence (B0/B1/94...)")
            buildOfficialBondedPlan()
        } else {
            Log.i(TAG, "buildOfficialHandshakePlan: using unbonded sequence (bond=$bondState)")
            buildOfficialUnbondedPlan()
        }
    }

    private fun buildOfficialBondedPlan(): List<HandshakeStep> {
        handshakePlanLabel = "official-bonded"
        val steps = ArrayList<HandshakeStep>()
        
        // 2026-02-05: Always start with F001 challenge to "wake up" the sensor.
        // Even in bonded mode, the sensor seems more responsive after an F001 write.
        steps.add(
            HandshakeStep(
                "${handshakePlanLabel}:F001-wake",
                CHAR_F001,
                hexBytes(OFFICIAL_PAIRED_F001),
                emptySet()
            )
        )

        OFFICIAL_HANDSHAKE_BONDED_STEPS.forEach { hex ->
            steps.add(HandshakeStep("${handshakePlanLabel}:${hex}", CHAR_F002, hexBytes(hex), emptySet()))
        }
        return steps
    }

    private fun buildOfficialUnbondedPlan(): List<HandshakeStep> {
        handshakePlanLabel = "official-unbonded"
        val steps = ArrayList<HandshakeStep>()
        
        // 2026-02-05: Add F001 challenge first to "wake up" the sensor session.
        // Without this, the sensor may ignore all F002 commands and return static responses.
        // The official app sends F001 before F002 in most flows.
        steps.add(
            HandshakeStep(
                "${handshakePlanLabel}:F001-challenge",
                CHAR_F001,
                hexBytes(OFFICIAL_PAIRED_F001),  // Use the known F001 challenge
                emptySet()
            )
        )
        
        // 2026-02-05: Use OFFICIAL_PAIRED_F002_STEPS (FB/CA/96/97/B2) instead of 
        // OFFICIAL_HANDSHAKE_UNBONDED_STEPS (BE/BF/9A...).
        // Analysis of aidex_output_timed.txt shows that after F001 challenge (91 C5 47...),
        // the official app sends FB F0 50, CA 82 76, 96 9B EA DB, 97 9B DB E8, B2 7A 47.
        // NOT the BE/BF/9A/A9/A8/AF sequence which appears to be for a different flow.
        OFFICIAL_PAIRED_F002_STEPS.forEach { hex ->
            val bytes = hexBytes(hex)
            val op = bytes.firstOrNull()?.toInt()?.and(0xFF) ?: -1
            val expectOps = when (op) {
                0xFB, 0xCA -> setOf(op)  // These should return the session/IV info
                else -> emptySet()
            }
            steps.add(HandshakeStep("${handshakePlanLabel}:${hex}", CHAR_F002, bytes, expectOps))
        }
        
        return steps
    }

    private fun buildOfficialPairedPlan(): List<HandshakeStep> {
        handshakePlanLabel = "official-paired"
        val steps = ArrayList<HandshakeStep>()

        steps.add(
            HandshakeStep(
                "${handshakePlanLabel}:$OFFICIAL_PAIRED_F001",
                CHAR_F001,
                hexBytes(OFFICIAL_PAIRED_F001),
                emptySet()
            )
        )

        OFFICIAL_PAIRED_F002_STEPS.forEach { hex ->
            val bytes = hexBytes(hex)
            steps.add(HandshakeStep("${handshakePlanLabel}:$hex", CHAR_F002, bytes, emptySet()))
        }

        return steps
    }

    private fun buildLegacyHandshakePlan(): List<HandshakeStep> {
        handshakePlanLabel = "legacy"
        return LEGACY_HANDSHAKE_STEPS.map { hex ->
            val bytes = hexBytes(hex)
            val op = bytes.firstOrNull()?.toInt()?.and(0xFF) ?: -1
            val expectOps = when (op) {
                0xB4, 0xB5 -> setOf(op)
                else -> emptySet()
            }
            HandshakeStep("${handshakePlanLabel}:${hex}", CHAR_F002, bytes, expectOps)
        }
    }

    private fun handshakeStepDelayMs(): Long {
        return if (handshakePlanLabel == "official-bonded" || handshakePlanLabel == "official-paired") {
            OFFICIAL_BONDED_STEP_DELAY_MS
        } else if (useOfficialHandshake) {
            HANDSHAKE_STEP_DELAY_MS
        } else {
            LEGACY_HANDSHAKE_STEP_DELAY_MS
        }
    }

    private fun addIvCandidatesFrom(data: ByteArray) {
        if (data.isEmpty()) return
        fun addCandidate(candidate: ByteArray) {
            if (candidate.size != 16) return
            pendingIVCandidates.add(candidate)
            try {
                // Some firmwares may transmit an IV block that is AES-ECB transformed.
                val ecb = Cipher.getInstance("AES/ECB/NoPadding")
                val keySpec = SecretKeySpec(MASTER_KEY, "AES")
                ecb.init(Cipher.DECRYPT_MODE, keySpec)
                pendingIVCandidates.add(ecb.doFinal(candidate))
                ecb.init(Cipher.ENCRYPT_MODE, keySpec)
                pendingIVCandidates.add(ecb.doFinal(candidate))
            } catch (_: Throwable) {
            }
        }

        if (data.size >= 16) {
            addCandidate(data.copyOfRange(0, 16))
        }
        if (data.size >= 17) {
            addCandidate(data.copyOfRange(1, 17))
        }
        if (data.size >= 18) {
            addCandidate(data.copyOfRange(2, 18))
        }
        if (pendingIVCandidates.size > 24) {
            pendingIVCandidates.subList(0, pendingIVCandidates.size - 24).clear()
        }
        
        // RE-ENGINEERING FIX: Persist IV candidates whenever they are updated.
        try {
            val sharePref = appContext.getSharedPreferences("Juggluco", Context.MODE_PRIVATE)
            val candidatesStr = pendingIVCandidates.joinToString(",") { bytesToHex(it) }
            sharePref.edit().putString("aidex_iv_candidates", candidatesStr).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist IV candidates: ${e.message}")
        }
    }
    
    override fun getService(): UUID? {
        return SERVICE_F000
    }

    private fun rawFromHeader6ED9(bytes: ByteArray): Float? {
        return rawFromHeader6ED9At(bytes, 0)
    }

    private fun rawFromHeader6ED9At(bytes: ByteArray, offset: Int): Float? {
        if (bytes.size < offset + 4) return null
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        if (b0 != 0x6E || b1 != 0xD9) return null
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        val combined = (b2 shl 8) or b3
        val mgDl = combined / 512.0f
        return if (mgDl in 30f..500f) mgDl else null
    }

    private fun rawFromHeader6D56(bytes: ByteArray): Float? {
        return rawFromHeader6D56At(bytes, 0)
    }

    private fun rawFromHeader6D56At(bytes: ByteArray, offset: Int): Float? {
        // LinX 1.7.25 (official) decrypted payload observed:
        // 6D 56 ... glucose mg/dL at byte[12]
        if (bytes.size < offset + 13) return null
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        if (b0 != 0x6D || b1 != 0x56) return null
        val mgDl = (bytes[offset + 12].toInt() and 0xFF).toFloat()
        return if (mgDl in 30f..500f) mgDl else null
    }

    private data class VendorDecodeResult(
        val glucoseMgDl: Float,
        val timeOffsetMinutes: Int,
        val trend: Int
    )

    private fun tryVendorInstantParse(label: String, payload: ByteArray, now: Long): VendorDecodeResult? {
        if (payload.isEmpty()) return null
        val loaded = try {
            AidexXParser.isLoaded()
        } catch (t: Throwable) {
            if (!vendorParserUnavailableLogged) {
                vendorParserUnavailableLogged = true
                Log.e(TAG, "AidexXParser init failed: ${t.message}")
            }
            return null
        }
        if (!loaded) {
            if (!vendorParserUnavailableLogged) {
                vendorParserUnavailableLogged = true
                Log.e(TAG, "AidexXParser not loaded (blecomm-lib missing or failed).")
            }
            return null
        }

        val entity = try {
            AidexXParser.getAidexXInstantHistory(payload) as? com.microtechmd.blecomm.parser.AidexXInstantHistoryEntity
        } catch (t: Throwable) {
            if (vendorProbeFailures.add(label)) {
                Log.d(TAG, "AidexXParser failed ($label): ${t.message}")
            }
            return null
        } ?: return null

        val history = entity.history ?: return null
        if (history.isValid == 0) return null
        val glucose = history.glucose
        if (glucose !in 30..500) return null

        val abstractOffset = entity.abstractEntity?.timeOffset ?: 0
        val offsetMinutes = if (history.timeOffset != 0) {
            history.timeOffset
        } else {
            abstractOffset
        }
        if (offsetMinutes <= 0 || offsetMinutes > 14 * 24 * 60) {
            if (vendorProbeFailures.add("${label}-offset")) {
                Log.d(TAG, "Vendor parser offset out of range ($label): $offsetMinutes")
            }
            return null
        }

        val trend = entity.abstractEntity?.trend ?: 0
        Log.i(TAG, "AIDEX-DEC: Vendor parser OK ($label) mg=$glucose offset=$offsetMinutes trend=$trend")
        return VendorDecodeResult(glucose.toFloat(), offsetMinutes, trend)
    }

    private fun parseVendorInstantPayload(payload: ByteArray, now: Long): VendorDecodeResult? {
        if (payload.isEmpty()) return null
        if (!vendorLibAvailable) return null

        // 17-byte packets are standard data frames
        if (payload.size != 17) {
             // 5-byte packets are likely status/keepalive
             return null 
        }

        val loaded = try {
            AidexXParser.isLoaded()
        } catch (t: Throwable) {
            if (!vendorParserUnavailableLogged) {
                vendorParserUnavailableLogged = true
                Log.e(TAG, "AidexXParser init failed: ${t.message}")
            }
            return null
        }
        if (!loaded) {
            if (!vendorParserUnavailableLogged) {
                vendorParserUnavailableLogged = true
                Log.e(TAG, "AidexXParser not loaded (blecomm-lib missing or failed).")
            }
            return null
        }

        val entity = try {
            AidexXParser.getAidexXInstantHistory(payload) as? com.microtechmd.blecomm.parser.AidexXInstantHistoryEntity
        } catch (t: Throwable) {
            Log.e(TAG, "AidexXParser failed: ${t.message}")
            return null
        } ?: return null

        val history = entity.history ?: return null
        val glucose = history.glucose
        
        // Removed incorrect Sanitizer. 
        // If the parser returns a value, we trust it for now as per correct protocol handling.
        
        // Debug logging 
        // Log.i(TAG, "Vendor entity: valid=${history.isValid} glucose=$glucose timeOffset=${history.timeOffset}")
        
        if (history.isValid == 0) return null
        if (glucose !in 30..500) return null

        val abstractOffset = entity.abstractEntity?.timeOffset ?: 0
        val offsetMinutes = if (history.timeOffset != 0) {
            history.timeOffset
        } else {
            abstractOffset
        }
        if (offsetMinutes > 0) {
            updateStartTimeFromOffset(offsetMinutes.toLong(), now)
        }

        val trend = entity.abstractEntity?.trend ?: 0
        return VendorDecodeResult(glucose.toFloat(), offsetMinutes, trend)
    }




    private fun parseVendorBroadcastPayload(payload: ByteArray, now: Long): VendorDecodeResult? {
        if (payload.isEmpty()) return null
        if (!vendorLibAvailable) return null
        val loaded = try {
            AidexXParser.isLoaded()
        } catch (t: Throwable) {
            if (!vendorParserUnavailableLogged) {
                vendorParserUnavailableLogged = true
                Log.e(TAG, "AidexXParser init failed: ${t.message}")
            }
            return null
        }
        if (!loaded) {
            if (!vendorParserUnavailableLogged) {
                vendorParserUnavailableLogged = true
                Log.e(TAG, "AidexXParser not loaded (blecomm-lib missing or failed).")
            }
            return null
        }

        val entity = try {
            AidexXParser.getBroadcast(payload) as? com.microtechmd.blecomm.parser.AidexXBroadcastEntity
        } catch (t: Throwable) {
            Log.e(TAG, "AidexXParser broadcast failed: ${t.message}")
            return null
        } ?: return null

        val history = entity.history?.firstOrNull() ?: return null
        
        if (history.isValid == 0) return null
        val glucose = history.glucose
        if (glucose !in 30..500) return null

        val abstractOffset = entity.abstractEntity?.timeOffset ?: 0
        val offsetMinutes = if (history.timeOffset != 0) {
            history.timeOffset
        } else {
            abstractOffset
        }
        if (offsetMinutes > 0) {
            updateStartTimeFromOffset(offsetMinutes.toLong(), now)
        }

        val trend = entity.abstractEntity?.trend ?: 0
        return VendorDecodeResult(glucose.toFloat(), offsetMinutes, trend)
    }

    private fun storeAutoFromSource(
        glucoseMgDl: Float,
        offsetMinutes: Int,
        now: Long,
        source: String,
        fromVendor: Boolean
    ): Boolean {
        // Mode 4 (Broadcast Only) uses this path to store readings since it has no other source
        if (!wantsAuto() && !isBroadcastOnlyMode()) return false
        if (glucoseMgDl !in 30f..500f) return false
        if (offsetMinutes <= 0 || offsetMinutes > 14 * 24 * 60) return false

        if (fromVendor && lastVendorOffsetMinutes != 0 && offsetMinutes < lastVendorOffsetMinutes) {
            Log.i(TAG, "Vendor offset went backwards ($lastVendorOffsetMinutes -> $offsetMinutes). Resetting dedupe.")
            lastVendorOffsetMinutes = 0
        }

        val lastOffset = maxOf(
            lastVendorOffsetMinutes,
            lastBroadcastStoredOffsetMinutes.toInt()
        )
        if (offsetMinutes <= lastOffset) return false
        if (lastAutoTime != 0L && (now - lastAutoTime) < BROADCAST_MIN_STORE_INTERVAL_MS) return false

        updateStartTimeFromOffset(offsetMinutes.toLong(), now)

        val secondary = if (viewModeInternal == 2) {
            recentRawSecondary(now)
        } else {
            0f
        }

        Log.i(TAG, ">>> SUCCESS AIDEX: Auto($source)=$glucoseMgDl mg/dL")
        storeAidexReading(byteArrayOf(0), now, glucoseMgDl, secondary)
        lastAutoMgDl = glucoseMgDl
        lastAutoTime = now
        writeFloatPref("lastAutoMgDl", lastAutoMgDl)
        writeLongPref("lastAutoTime", lastAutoTime)

        if (fromVendor) {
            lastVendorMgDl = glucoseMgDl
            lastVendorTime = now
            lastVendorOffsetMinutes = offsetMinutes
        }

        return true
    }

    private fun recentRawSecondary(now: Long): Float {
        if (lastRawTime == 0L) return 0f
        if ((now - lastRawTime) > 2 * 60_000L) return 0f
        return lastRawMgDl
    }

    private fun recentVendorSecondary(now: Long): Float {
        if (lastVendorTime == 0L) return 0f
        if ((now - lastVendorTime) > 2 * 60_000L) return 0f
        return lastVendorMgDl
    }

    private data class LegacyDecodeResult(
        val mgDl: Float,
        val source: String,
        val selectedBytes: ByteArray
    )

    private fun isVendorProbePlausible(glucoseMgDl: Float, now: Long): Boolean {
        // RE-ENGINEERING FIX: If the Vendor Library explicitly validated this packet, trust it.
        // We do this FIRST to override standard deviation checks.
        if (vendorBleEnabled) {
            // Log.v(TAG, "Vendor probe accepted (trusted source): $glucoseMgDl")
            return true
        }

        if (glucoseMgDl !in 30f..500f) return false

        val referenceAge = now - lastRawTime
        if (lastRawTime > 0L && referenceAge in 0..(10 * 60_000L)) {
            val diff = kotlin.math.abs(glucoseMgDl - lastRawMgDl)
            val maxDiff = maxAllowedDiff(referenceAge)
            if (diff > maxDiff) {
                Log.w(TAG, "Vendor probe rejected (diff=$diff mg/dL, max=$maxDiff, last=$lastRawMgDl)")
                return false
            }
        }

        if (lastBroadcastTime > 0L) {
            val ageMs = now - lastBroadcastTime
            if (ageMs in 0..BROADCAST_REFERENCE_MS) {
                val diff = kotlin.math.abs(glucoseMgDl - lastBroadcastGlucose)
                val maxDiff = maxAllowedDiff(ageMs)
                if (diff > maxDiff) {
                    Log.w(TAG, "Vendor probe rejected vs broadcast (diff=$diff mg/dL, max=$maxDiff, brd=$lastBroadcastGlucose)")
                    return false
                }
            }
                }


        return true
    }



    private fun getBroadcastReference(now: Long): Pair<Float, Long>? {
        if (!broadcastEnabled) return null
        if (!broadcastSeenInSession) return null
        if (lastBroadcastGlucose <= 0f || lastBroadcastTime == 0L) return null
        val ageMs = now - lastBroadcastTime
        if (ageMs > BROADCAST_REFERENCE_MS) return null
        return Pair(lastBroadcastGlucose, ageMs)
    }

    private data class IvLockMatch(
        val iv: ByteArray,
        val candidate: DecodeCandidate,
        val selectedBytes: ByteArray,
        val sourceLabel: String
    )

    private fun tryLockIvWithBroadcast(
        encryptedData: ByteArray,
        now: Long
    ): IvLockMatch? {
        val ref = getBroadcastReference(now) ?: return null
        val (broadcastGlucose, ageMs) = ref
        val maxDiff = maxAllowedDiff(ageMs)

        if (pendingIVCandidates.isEmpty()) return null

        val skips = intArrayOf(1, 0)
        var bestMatch: IvLockMatch? = null
        var bestDiff = Float.MAX_VALUE

        for (candidateIv in pendingIVCandidates) {
            for (skip in skips) {
                val decrypted = decryptPayload(encryptedData, candidateIv, skip) ?: continue
                try {
                    val decryptedReversed = reverseBitsCopy(decrypted)
                    val allCandidates = collectCandidates(decrypted, decryptedReversed, skip)
                    val candidates = if (useOfficialHandshake) {
                        allCandidates.filter { it.source.startsWith("header") }
                    } else {
                        allCandidates
                    }
                    if (candidates.isEmpty()) continue

                    val bestCandidate = candidates.minBy { kotlin.math.abs(it.mgDl - broadcastGlucose) }
                    val diff = kotlin.math.abs(bestCandidate.mgDl - broadcastGlucose)
                    if (diff > maxDiff) continue

                    if (diff < bestDiff) {
                        val selectedBytes = if (bestCandidate.reversed) decryptedReversed else decrypted
                        val label = "skip$skip-${bestCandidate.source}"
                        bestMatch = IvLockMatch(candidateIv, bestCandidate, selectedBytes, label)
                        bestDiff = diff
                    }
                } catch (_: Throwable) {
                }
            }
        }

        return bestMatch
    }

    private data class DecodeCandidate(
        val mgDl: Float,
        val reversed: Boolean,
        val source: String
    )

    private fun decryptPayload(encryptedData: ByteArray, iv: ByteArray, skip: Int): ByteArray? {
        if (skip < 0 || skip >= encryptedData.size) return null
        val slice = encryptedData.copyOfRange(skip, encryptedData.size)
        if (slice.size < 16) return null
        return try {
            decrypt(slice, iv)
        } catch (_: Throwable) {
            null
        }
    }

    private fun collectCandidates(
        decrypted: ByteArray,
        decryptedReversed: ByteArray,
        skip: Int
    ): List<DecodeCandidate> {
        val candidates = ArrayList<DecodeCandidate>(8)

        for (offset in 0..2) {
            val header6D56 = rawFromHeader6D56At(decrypted, offset)
            if (header6D56 != null) {
                candidates.add(DecodeCandidate(header6D56, false, "header6D56@$offset/s$skip"))
            }
            val headerPlain = rawFromHeader6ED9At(decrypted, offset)
            if (headerPlain != null) {
                candidates.add(DecodeCandidate(headerPlain, false, "header6ED9@$offset/s$skip"))
            }
            val headerReversed = rawFromHeader6ED9At(decryptedReversed, offset)
            if (headerReversed != null) {
                candidates.add(DecodeCandidate(headerReversed, true, "header6ED9@$offset/s$skip"))
            }
        }

        // Legacy fallback candidates.
        if (decrypted.size >= 6) {
            val mgDl = (decrypted[5].toInt() and 0xFF).toFloat()
            if (mgDl in 30f..500f) {
                candidates.add(DecodeCandidate(mgDl, false, "plain.b5/s$skip"))
            }
        }
        if (decryptedReversed.size >= 6) {
            val mgDl = (decryptedReversed[5].toInt() and 0xFF).toFloat()
            if (mgDl in 30f..500f) {
                candidates.add(DecodeCandidate(mgDl, true, "rev.b5/s$skip"))
            }
        }
        return candidates
    }

    private fun maxAllowedDiff(ageMs: Long): Float {
        val ageMin = ageMs / 60000f
        val max = 30f + (ageMin * 5f)
        return max.coerceIn(30f, 80f)
    }

    private fun selectCandidate(
        candidates: List<DecodeCandidate>,
        now: Long,
        reverseHint: Boolean
    ): DecodeCandidate? {
        if (candidates.isEmpty()) return null
        val filtered = if (useOfficialHandshake || ivLocked) {
            candidates.filter { it.source.startsWith("header") }
        } else {
            candidates
        }
        if (filtered.isEmpty()) return null
        val hasHeader6D56 = filtered.any { it.source.startsWith("header6D56") }
        val hasHeader6ED9 = filtered.any { it.source.startsWith("header6ED9") }
        val referenceAge = now - lastRawTime
        val hasReference = lastRawTime > 0L && referenceAge in 0..(10 * 60_000L)

        // LinX official frames: prefer the known header mapping.
        var chosen: DecodeCandidate? = null
        if (hasHeader6D56) {
            chosen = filtered.first { it.source.startsWith("header6D56") }
        } else if (hasReference) {
            val best = filtered.minBy { kotlin.math.abs(it.mgDl - lastRawMgDl) }
            val diff = kotlin.math.abs(best.mgDl - lastRawMgDl)
            val maxDiff = maxAllowedDiff(referenceAge)
            if (diff > maxDiff) {
                Log.w(TAG, "F003: Candidate rejected (diff=$diff mg/dL, max=$maxDiff, last=$lastRawMgDl)")
                return null
            }
            chosen = best
        } else if (hasHeader6ED9) {
            chosen = filtered.first { it.source.startsWith("header6ED9") }
        } else {
            chosen = filtered.firstOrNull { it.reversed == reverseHint } ?: filtered.first()
        }

        if (chosen == null) return null

        if (ivLockedFromBroadcast && (chosen.source == "plain.b5" || chosen.source == "rev.b5")) {
            val ref = getBroadcastReference(now)
            if (ref != null) {
                val (brd, ageMs) = ref
                val diff = kotlin.math.abs(chosen.mgDl - brd)
                val maxDiff = maxAllowedDiff(ageMs)
                if (diff > maxDiff) {
                    Log.w(TAG, "F003: Fallback candidate rejected vs broadcast (diff=$diff mg/dL, max=$maxDiff, brd=$brd)")
                    return null
                }
            } else if (!hasReference) {
                Log.w(TAG, "F003: Fallback candidate rejected (no broadcast or recent reference)")
                return null
            }
        }

        return chosen
    }

    private fun decodeLegacyF003(encryptedData: ByteArray, now: Long): LegacyDecodeResult? {
        if (encryptedData.size < 17) {
            Log.d(TAG, "F003 short frame (${encryptedData.size} bytes). Ignored.")
            return null
        }

        if (dynamicIV == null) {
            if (pendingIVCandidates.isNotEmpty()) {
                val lockMatch = tryLockIvWithBroadcast(encryptedData, now)
                if (lockMatch != null) {
                    val ivHex = bytesToHex(lockMatch.iv)
                    val key = "$ivHex:${lockMatch.sourceLabel}:${lockMatch.candidate.reversed}"
                    if (key == ivLockKey) {
                        ivLockCount += 1
                    } else {
                        ivLockKey = key
                        ivLockCount = 1
                    }

                    Log.i(
                        TAG,
                        "IV lock candidate ${ivLockCount}/2 (key=$key, mg=${lockMatch.candidate.mgDl})"
                    )

                    if (ivLockCount >= 2) {
                        dynamicIV = lockMatch.iv
                        pendingIVCandidates.clear()
                        ivLockCount = 0
                        ivLockKey = null
                        useBitReverse = lockMatch.candidate.reversed
                        writeIntPref("useBitReverse", if (useBitReverse == true) 1 else 0)
                        ivLocked = true
                        ivLockedFromBroadcast = true
                        Log.i(TAG, "IV locked from broadcast (source=${lockMatch.sourceLabel})")

                        lastRawMgDl = lockMatch.candidate.mgDl
                        lastRawTime = now
                        writeFloatPref("lastRawMgDl", lastRawMgDl)
                        writeLongPref("lastRawTime", lastRawTime)
                        return LegacyDecodeResult(lockMatch.candidate.mgDl, "ivlock", lockMatch.selectedBytes)
                    }

                    // Don't emit a reading until IV is locked; rely on broadcast instead.
                    return null
                }

                var resolved: ByteArray? = null
                var resolvedVendor: VendorDecodeResult? = null
                val skips = intArrayOf(1, 0)
                for (candidate in pendingIVCandidates) {
                    for (skip in skips) {
                        val decrypted = decryptPayload(encryptedData, candidate, skip) ?: continue
                        try {
                            val decryptedReversed = reverseBitsCopy(decrypted)
                            if (!useOfficialHandshake) {
                                val vendorCandidate = tryVendorInstantParse("f003-ivcand", decrypted, now)
                                    ?: tryVendorInstantParse("f003-ivcand-rev", decryptedReversed, now)
                                if (vendorCandidate != null && isVendorProbePlausible(vendorCandidate.glucoseMgDl, now)) {
                                    resolved = candidate
                                    resolvedVendor = vendorCandidate
                                    break
                                }
                            }
                            val headerOk =
                                (rawFromHeader6D56At(decrypted, 0) != null ||
                                    rawFromHeader6D56At(decrypted, 1) != null ||
                                    rawFromHeader6D56At(decrypted, 2) != null ||
                                    rawFromHeader6D56At(decryptedReversed, 0) != null ||
                                    rawFromHeader6D56At(decryptedReversed, 1) != null ||
                                    rawFromHeader6D56At(decryptedReversed, 2) != null ||
                                    rawFromHeader6ED9At(decrypted, 0) != null ||
                                    rawFromHeader6ED9At(decrypted, 1) != null ||
                                    rawFromHeader6ED9At(decrypted, 2) != null ||
                                    rawFromHeader6ED9At(decryptedReversed, 0) != null ||
                                    rawFromHeader6ED9At(decryptedReversed, 1) != null ||
                                    rawFromHeader6ED9At(decryptedReversed, 2) != null)
                            if (headerOk) {
                                resolved = candidate
                                break
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    if (resolved != null) break
                }
                if (resolved != null) {
                    dynamicIV = resolved
                    pendingIVCandidates.clear()
                    Log.i(TAG, "IV resolved from candidate list; continuing decode.")
                    ivLocked = true
                    ivLockedFromBroadcast = false
                    if (resolvedVendor != null) {
                        lastRawMgDl = resolvedVendor.glucoseMgDl
                        lastRawTime = now
                        writeFloatPref("lastRawMgDl", lastRawMgDl)
                        writeLongPref("lastRawTime", lastRawTime)
                        return LegacyDecodeResult(resolvedVendor.glucoseMgDl, "vendor-ivcand", byteArrayOf(0))
                    }
                } else {
                    Log.w(TAG, "RX [F003]: CANNOT DECRYPT - no valid IV candidate matched header (6D56/6ED9)")
                    return null
                }
            } else {
                Log.w(TAG, "RX [F003]: CANNOT DECRYPT - dynamicIV is NULL")
                return null
            }
        }

        try {
            val skips = intArrayOf(1, 0)
            var bestCandidates: List<DecodeCandidate> = emptyList()
            var bestDecrypted: ByteArray? = null
            var bestReversed: ByteArray? = null

            for (skip in skips) {
                val decrypted = decryptPayload(encryptedData, dynamicIV!!, skip) ?: continue
                val decryptedReversed = reverseBitsCopy(decrypted)
                val candidates = collectCandidates(decrypted, decryptedReversed, skip)
                if (candidates.isNotEmpty()) {
                    bestCandidates = candidates
                    bestDecrypted = decrypted
                    bestReversed = decryptedReversed
                    // Prefer the first skip that yields header candidates.
                    if (candidates.any { it.source.startsWith("header") }) break
                }
            }

            if (bestDecrypted == null || bestReversed == null) return null

            val vendorDecrypted = tryVendorInstantParse("f003-dec", bestDecrypted, now)
                ?: tryVendorInstantParse("f003-dec-rev", bestReversed, now)
            if (vendorDecrypted != null && isVendorProbePlausible(vendorDecrypted.glucoseMgDl, now)) {
                lastRawMgDl = vendorDecrypted.glucoseMgDl
                lastRawTime = now
                writeFloatPref("lastRawMgDl", lastRawMgDl)
                writeLongPref("lastRawTime", lastRawTime)
                return LegacyDecodeResult(vendorDecrypted.glucoseMgDl, "vendor-dec", byteArrayOf(0))
            }

            val candidates = bestCandidates
            if (useOfficialHandshake &&
                candidates.none { it.source.startsWith("header6D56") || it.source.startsWith("header6ED9") }
            ) {
                Log.w(TAG, "F003: Unexpected decrypted header; dropping frame (no 6D56/6ED9 match)")
                return null
            }
            val reverseHint = (bestDecrypted[1].toInt() and 0x80) != 0
            val decoded = selectCandidate(candidates, now, reverseHint)
            if (decoded == null) {
                Log.w(TAG, "F003: No deterministic decode match")
                return null
            }

            val bestVal = decoded.mgDl
            val usedReverse = decoded.reversed
            val selectedBytes = if (usedReverse) bestReversed else bestDecrypted

            if (useBitReverse != usedReverse) {
                useBitReverse = usedReverse
                writeIntPref("useBitReverse", if (useBitReverse == true) 1 else 0)
                Log.i(TAG, "AIDEX-DEC: Using bit-reverse=$usedReverse")
            }

            when {
                decoded.source.startsWith("header6D56") -> {
                    Log.i(TAG, "AIDEX-DEC: Header 6D56 mg=$bestVal mg/dL (${decoded.source})")
                }
                decoded.source.startsWith("header6ED9") -> {
                    Log.i(TAG, "AIDEX-DEC: Header 6ED9 mg=$bestVal mg/dL (${decoded.source})")
                }
                else -> {
                    Log.d(TAG, "AIDEX-DEC: ${decoded.source} mg=$bestVal mg/dL")
                }
            }

            // Raw reference for future candidate selection.
            lastRawMgDl = bestVal
            lastRawTime = now
            writeFloatPref("lastRawMgDl", lastRawMgDl)
            writeLongPref("lastRawTime", lastRawTime)

            return LegacyDecodeResult(bestVal, decoded.source, selectedBytes)
        } catch (e: Exception) {
            Log.stack(TAG, "Decryption/Parse Error", e)
            return null
        }
    }

    private fun handleF003Data(encryptedData: ByteArray) {
        if (!wantsRaw() && !wantsAuto()) return
        val now = System.currentTimeMillis()

        // --- NEW PROTOCOL HANDLING ---
        // 5-byte packets are status/keepalive (ignore for now)
        if (encryptedData.size == 5) {
             Log.d(TAG, "AIDEX-F003: Status packet (5 bytes) ignored.")
             return
        }

        // 17-byte packets are data
        if (encryptedData.size == 17) {
            var packetToParse = encryptedData
            var decrypted = false
            
            if (dynamicIV != null) {
                try {
                    val dec = decrypt(encryptedData, dynamicIV!!)
                    packetToParse = dec
                    decrypted = true
                     Log.d(TAG, "AIDEX-F003: Decrypted successfully.")
                } catch (e: Exception) {
                    Log.w(TAG, "AIDEX-F003: Decrypt failed: ${e.message}")
                }
            } else {
                 Log.w(TAG, "AIDEX-F003: dynamicIV is NULL. Parsing ciphertext (likely to fail/garbage).")
            }

            // Try native parser with (hopefully) plaintext
            val vendorDirect = parseVendorInstantPayload(packetToParse, now)
            if (vendorDirect != null) {
                // SANITY CHECK: If values are extreme and we didn't decrypt, it's likely garbage.
                if (!decrypted && (vendorDirect.glucoseMgDl > 400 || vendorDirect.glucoseMgDl < 40)) {
                     Log.w(TAG, "AIDEX-F003: Native parse result ${vendorDirect.glucoseMgDl} suspicious (Ciphertext?). Ignoring.")
                } else {
                    markHandshakeComplete("vendor-f003-native")
                    lastRawMgDl = vendorDirect.glucoseMgDl
                    lastRawTime = now
                    writeFloatPref("lastRawMgDl", lastRawMgDl)
                    writeLongPref("lastRawTime", lastRawTime)
    
                    val secondary = if (viewModeInternal == 3) {
                        recentVendorSecondary(now)
                    } else {
                        0f
                    }
                    Log.i(TAG, ">>> SUCCESS AIDEX (Native): Raw=${vendorDirect.glucoseMgDl} mg/dL")
                    storeAidexReading(byteArrayOf(0), now, vendorDirect.glucoseMgDl, secondary)
    
                    if (wantsAuto()) {
                        lastAutoMgDl = vendorDirect.glucoseMgDl
                        lastAutoTime = now
                        writeFloatPref("lastAutoMgDl", lastAutoMgDl)
                        writeLongPref("lastAutoTime", lastAutoTime)
                    }
                    
                    checkAndRequestHistory()
                    return
                }
            } else {
                Log.w(TAG, "AIDEX-F003: Native parse failed for 17-byte packet. Trying legacy fallback...")
            }
        }
        
        // --- LEGACY FALLBACK ---
        val legacy = decodeLegacyF003(encryptedData, now) ?: return
        markHandshakeComplete("f003-legacy")

        val secondary = if (viewModeInternal == 3) {
            recentVendorSecondary(now)
        } else {
            0f
        }

        Log.i(TAG, ">>> SUCCESS AIDEX (Legacy): Raw=${legacy.mgDl} mg/dL")
        storeAidexReading(legacy.selectedBytes, now, legacy.mgDl, secondary)

        if (wantsAuto()) {
            lastAutoMgDl = legacy.mgDl
            lastAutoTime = now
            writeFloatPref("lastAutoMgDl", lastAutoMgDl)
            writeLongPref("lastAutoTime", lastAutoTime)
        }
        
        // Trigger history backfill if needed
        checkAndRequestHistory()
    }

    private fun checkAndRequestHistory() {
         if (System.currentTimeMillis() - lastHistoryRequestTime > 300_000L) { // 5 mins
            lastHistoryRequestTime = System.currentTimeMillis()
            executeVendorCommand("get-history", AidexXOperation.GET_HISTORIES) { controller ->
                controller.getHistories(0)
            }
        }
    }


    private fun getFreshBroadcastForStore(now: Long): Float? {
        if (!broadcastEnabled) return null
        if (!broadcastSeenInSession) return null
        if (lastBroadcastGlucose <= 0f || lastBroadcastTime == 0L) {
            Log.w(TAG, "Broadcast missing. Skipping F003 store.")
            scheduleBroadcastScan("no-broadcast", forceImmediate = true)
            return null
        }
        val ageMs = now - lastBroadcastTime
        if (ageMs > BROADCAST_REFERENCE_MS) {
            Log.w(TAG, "Broadcast stale (${ageMs / 1000}s). Skipping F003 store.")
            scheduleBroadcastScan("broadcast-stale", forceImmediate = true)
            return null
        }
        return lastBroadcastGlucose
    }

    private fun storeAidexReading(bytes: ByteArray, timeMs: Long, autoGlucose: Float, rawGlucose: Float) {
        if (dataptr == 0L) {
            Log.w(TAG, "No dataptr; skipping store.")
            return
        }
        if (sensorstartmsec == 0L) {
            if (lastBroadcastOffsetMinutes > 0L) {
                updateStartTimeFromOffset(lastBroadcastOffsetMinutes, timeMs)
            } else {
                sensorstartmsec = timeMs
                try {
                    Natives.aidexSetStartTime(dataptr, sensorstartmsec)
                } catch (_: Throwable) {
                }
            }
        }
        try {
            val res = Natives.aidexProcessData(dataptr, bytes, timeMs, autoGlucose, rawGlucose, 1.0f)
            handleGlucoseResult(res, timeMs)

            // Trigger sync to update Compose chart
            tk.glucodata.data.HistorySync.syncFromNative()
            
            // --- HISTORY BACKFILL ---
            // If we are getting data but history is missing, request it.
            // Limited to once per connection or if gap detected?
            // For now, request on every F003 to ensure we don't miss anything, 
            // but controller.getHistories(0) might be heavy.
            // Better: request if we haven't requested recently.
            if (System.currentTimeMillis() - lastHistoryRequestTime > 300_000L) { // 5 mins
                lastHistoryRequestTime = System.currentTimeMillis()
                Log.i(TAG, "Requesting History Backfill...")
                executeVendorCommand("get-history", 1) { controller ->
                    controller.getHistories(0) // 0 = all? or offset?
                    // controller.getRawHistories(0) // Alternative?
                }
            }

        } catch (e: UnsatisfiedLinkError) {
             Log.e(TAG, "Native library mismatch/missing: $e")
        } catch (e: Throwable) {
             Log.e(TAG, "Error in native processing: $e")
        }
    }

    private var lastHistoryRequestTime = 0L






    // --- HANDSHAKE LOGIC ---

    @SuppressLint("MissingPermission")
    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return

        // Chain the descriptor writes (F001 -> F002 -> F003)
        if (descriptor.characteristic.uuid == CHAR_F001) {
             val sF000 = gatt.getService(SERVICE_F000)
             val cF002 = sF000?.getCharacteristic(CHAR_F002)
             if (cF002 != null) setCharacteristicNotification(gatt, cF002, true)
        } 
        else if (descriptor.characteristic.uuid == CHAR_F002) {
             val sF000 = gatt.getService(SERVICE_F000)
             val cF003 = sF000?.getCharacteristic(CHAR_F003)
             if (cF003 != null) setCharacteristicNotification(gatt, cF003, true)
        }
        else if (descriptor.characteristic.uuid == CHAR_F003) {
            if (vendorBleEnabled) {
                vendorGattNotified = true
                Log.d(TAG, "onDescriptorWrite: Forced vendorGattNotified=true")
            }
            if (vendorGattNotified) {
                Log.i(TAG, "All CCCDs written. Vendor notifications enabled. executeConnectReceived=$vendorExecuteConnectReceived")
                // Signal onConnectSuccess to the vendor native lib — BUT ONLY if the native
                // lib has called executeConnect() first (setting up internal connection context).
                // Without executeConnect(), the native Ble::onConnectSuccess() dereferences a null
                // internal pointer at offset +100 → SIGSEGV → process kill → crash loop.
                //
                // If executeConnect hasn't been called yet, we trigger startVendorLongConnect()
                // to prompt the native lib to initiate the connection sequence.
                if (vendorBleEnabled && vendorRegistered) {
                    scanHandler.post {
                        disregardDisconnectsUntil = System.currentTimeMillis() + 8000L
                        if (vendorExecuteConnectReceived) {
                            // Native lib has set up its connection context — safe to signal success
                            Log.i(TAG, "Signaling onConnectSuccess to vendor adapter (all CCCDs ready, executeConnect received).")
                            vendorConnectPending = true
                            safeCallOnConnectSuccess("onDescriptorWrite-ready")
                        } else {
                            // Native lib hasn't called executeConnect() yet.
                            // DO NOT call onConnectSuccess — it will SIGSEGV.
                            // Instead, trigger long-connect to prompt native to start its flow.
                            Log.i(TAG, "CCCDs ready but executeConnect not received yet. Triggering long-connect to prompt native lib.")
                        }
                        // Always trigger long-connect to ensure native lib starts its
                        // data streaming / auto-update / handshake sequence.
                        scanHandler.postDelayed({
                            startVendorLongConnect("post-cccd")
                        }, 1000L)
                    }
                } else {
                    Log.w(TAG, "Vendor not ready for onConnectSuccess: enabled=$vendorBleEnabled registered=$vendorRegistered pending=$vendorConnectPending")
                }
                return
            }

            if (wantsRaw() || wantsAuto()) {
                val mode = if (useOfficialHandshake) "official" else "legacy"
                Log.i(TAG, "All Notifications Enabled. Starting $mode handshake.")
                val now = System.currentTimeMillis()
                if (useOfficialHandshake && bondWaitUntilMs > now) {
                    val delay = bondWaitUntilMs - now
                    handshakeHandler.postDelayed({ beginHandshakeAfterBondWait(gatt) }, delay)
                } else {
                    beginHandshakeWithPriming(gatt)
                }
            }
        }
    }

    // --- HANDSHAKE ---

    @SuppressLint("MissingPermission")
    private fun performHandshakeStep(gatt: BluetoothGatt) {
        val sF000 = gatt.getService(SERVICE_F000) ?: return
        val steps = handshakePlan
        val stepDelay = handshakeStepDelayMs()
        val readDelay = if (useOfficialHandshake) HANDSHAKE_READ_DELAY_MS else LEGACY_HANDSHAKE_READ_DELAY_MS

        if (handshakeStep <= 0) {
            handshakeStep = 1
            handshakeRetries = 0
        }

        if (steps.isEmpty()) {
            Log.e(TAG, "Handshake aborted: no plan (mode=${if (useOfficialHandshake) "official" else "legacy"})")
            return
        }

        if (handshakeStep > steps.size) {
            Log.i(TAG, "Handshake Completed. Listening for F003. (plan=$handshakePlanLabel)")
            return
        }

        val step = steps[handshakeStep - 1]
        val cmd = step.data
        val cmdOp = if (cmd.isNotEmpty()) cmd[0].toInt() and 0xFF else -1
        val needsResponse = step.expectResponseOps.isNotEmpty()

        cancelHandshakeTimers()

        val characteristic = when (step.uuid) {
            CHAR_F001 -> sF000.getCharacteristic(CHAR_F001)
            CHAR_F002 -> sF000.getCharacteristic(CHAR_F002)
            CHAR_F003 -> sF000.getCharacteristic(CHAR_F003)
            else -> sF000.getCharacteristic(step.uuid)
        } ?: run {
            Log.e(TAG, "Handshake step $handshakeStep missing characteristic ${step.uuid}")
            return
        }

        // Force WRITE_TYPE_NO_RESPONSE for F001/F002 as per protocol analysis
        // The official app uses Write Command (No Auth/No Response) for these handshakes.
        val forceNoResponse = (step.uuid == CHAR_F001 || step.uuid == CHAR_F002)
        characteristic.writeType = if (forceNoResponse || (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        if (gatt != mBluetoothGatt) {
            Log.w(TAG, "performHandshakeStep: aborting (gatt instance mismatch or null)")
            return
        }

        val success = gatt.writeCharacteristic(characteristic)
        Log.d(
            TAG,
            "Handshake Step $handshakeStep (${step.label}): Writing ${bytesToHex(cmd)} to ${step.uuid} " +
                "(props=0x${String.format("%02X", characteristic.properties)} writeType=${characteristic.writeType}, enqueued=$success)"
        )
        if (!success) {
            // Android GATT allows only one in-flight operation; retry the same step shortly.
            if (mBluetoothGatt != null) {
                handshakeHandler.postDelayed({ performHandshakeStep(gatt) }, 250L)
            } else {
                Log.e(TAG, "Handshake aborted: disconnected during write attempt")
            }
            return
        }

        if (!needsResponse) {
            // Advancement now happens in onCharacteristicWrite callback for better pacing.
            return
        }

        expectedF002ResponseOps = step.expectResponseOps
        handshakeReadAttempts = 0

        if (step.uuid == CHAR_F002) {
            // Some Android stacks/firmware do not emit F002 notifications reliably.
            // F002 is readable (props include READ on many devices), so schedule repeated reads as a fallback.
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                val stepAtSchedule = handshakeStep
                val expectedAtSchedule = expectedF002ResponseOps
                pendingHandshakeRead = object : Runnable {
                    override fun run() {
                        if (handshakeStep != stepAtSchedule || expectedF002ResponseOps != expectedAtSchedule) return
                        val ok = gatt.readCharacteristic(characteristic)
                        handshakeReadAttempts++
                        Log.d(
                            TAG,
                            "Handshake Step $handshakeStep: READ F002 fallback enqueued=$ok (try=$handshakeReadAttempts)"
                        )
                        if (handshakeReadAttempts < HANDSHAKE_READ_MAX) {
                            handshakeHandler.postDelayed(this, readDelay)
                        }
                    }
                }
                handshakeHandler.postDelayed(pendingHandshakeRead!!, readDelay)
            }
        }

        val stepAtSchedule = handshakeStep
        val expectedAtSchedule = expectedF002ResponseOps
        pendingHandshakeTimeout = Runnable {
            if (handshakeStep == stepAtSchedule && expectedF002ResponseOps == expectedAtSchedule) {
                handshakeRetries++
                if (handshakeRetries <= HANDSHAKE_MAX_RETRIES) {
                    Log.w(TAG, "Handshake step $handshakeStep timed out. Retrying (${handshakeRetries}/$HANDSHAKE_MAX_RETRIES).")
                    performHandshakeStep(gatt)
                    return@Runnable
                }
                if (!useOfficialHandshake &&
                    expectedAtSchedule.any { it == 0xB4 || it == 0xB5 }
                ) {
                    Log.w(TAG, "Legacy handshake timed out; auto-advancing (step=$handshakeStep, plan=$handshakePlanLabel).")
                    handshakeStep++
                    expectedF002ResponseOps = emptySet()
                    performHandshakeStep(gatt)
                    return@Runnable
                }
                if (useOfficialHandshake &&
                    handshakePlanLabel == "official-unbonded" &&
                    dynamicIV == null &&
                    pendingIVCandidates.isEmpty()
                ) {
                    // 2026-02-05: Bonded plan doesn't return IV, try legacy instead.
                    Log.w(TAG, "Official unbonded handshake timed out; switching to legacy plan.")
                    handshakePlan = buildLegacyHandshakePlan()
                    useOfficialHandshake = false
                    handshakeStep = 1
                    expectedF002ResponseOps = emptySet()
                    performHandshakeStep(gatt)
                    return@Runnable
                }
                if (useOfficialHandshake &&
                    handshakePlanLabel == "official-paired" &&
                    dynamicIV == null &&
                    pendingIVCandidates.isEmpty()
                ) {
                    // 2026-02-05: Bonded plan doesn't return IV, try legacy instead.
                    Log.w(TAG, "Official paired handshake timed out; switching to legacy plan.")
                    handshakePlan = buildLegacyHandshakePlan()
                    useOfficialHandshake = false
                    handshakeStep = 1
                    expectedF002ResponseOps = emptySet()
                    performHandshakeStep(gatt)
                    return@Runnable
                }
                Log.e(TAG, "Handshake step $handshakeStep timed out after $HANDSHAKE_MAX_RETRIES retries (plan=$handshakePlanLabel).")
                expectedF002ResponseOps = emptySet()
            }
        }
        handshakeHandler.postDelayed(pendingHandshakeTimeout!!, HANDSHAKE_TIMEOUT_MS)
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return
        val data = characteristic.value ?: return

        // 2026-02-08: If Vendor Lib is active, it handles its own reads/notify.
        // Doing anything here (like passing it back to vendorAdapter or processing it ourselves)
        // can cause "Double Packet" errors in the native stack, leading to disconnects.
        if (vendorBleEnabled) {
             Log.d(TAG, "Ignored onCharacteristicRead [${characteristic.uuid}] (Vendor managing)")
             return
        }

        if (vendorBleEnabled && vendorGattNotified) {
            vendorAdapter?.handleCharacteristicChanged(characteristic, data)
        }
        if ((wantsRaw() || wantsAuto()) && characteristic.uuid == CHAR_F002) {
            Log.i(TAG, "READ [F002]: ${bytesToHex(data)}")
            handleF002Response(gatt, data)
            if (handshakePrimingInProgress) {
                handshakePrimingInProgress = false
                pendingHandshakePrimingTimeout?.let { handshakeHandler.removeCallbacks(it) }
                pendingHandshakePrimingTimeout = null
                Log.i(TAG, "Handshake priming read complete. Starting handshake.")
                performHandshakeStep(gatt)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val uuid = characteristic.uuid
        val data = characteristic.value

        if (data == null || data.isEmpty()) return

        // --- FORWARD ALL CHARACTERISTIC DATA TO VENDOR NATIVE LIB ---
        // The vendor native lib needs to receive F001/F002/F003 data via onReceiveData()
        // to complete its internal AES handshake and process commands.
        // Without this, the native lib never initializes AES and commands like reset() are no-ops.
        // Guard with vendorNativeReady: only forward data after onConnectSuccess() has succeeded.
        // Calling onReceiveData() before native Ble is initialized causes SIGSEGV in Ble::onReceiveData().
        if (vendorBleEnabled && vendorGattNotified && vendorNativeReady && vendorAdapter != null) {
            try {
                val shortUuid = uuidToShort(uuid)
                vendorAdapter!!.onReceiveData(shortUuid, data)
                Log.i(TAG, "Vendor RX fwd [0x${Integer.toHexString(shortUuid)}]: ${bytesToHex(data)}")
            } catch (t: Throwable) {
                Log.w(TAG, "Vendor RX fwd failed: ${t.message}")
            }
        }

        // --- IV SNOOPING ---
        // Also capture IV from F002 responses for our own legacy decrypt path.
        // Snoop known IV response opcodes (A8, B4, B5, F0) from F002 data.
        if (uuid == CHAR_F002 && data.size >= 18) {
            val opcode = data[0].toInt() and 0xFF
            if (opcode == 0xF0 || opcode == 0xB4 || opcode == 0xB5 || opcode == 0xA8) {
                val ivOffset = if (opcode == 0xB5) 1 else 2
                if (data.size >= ivOffset + 16) {
                    val iv = ByteArray(16)
                    System.arraycopy(data, ivOffset, iv, 0, 16)
                    dynamicIV = iv
                    Log.i(TAG, "IV SNOOP: Captured dynamicIV from F002 opcode 0x${"%02X".format(opcode)}: ${iv.joinToString("") { "%02X".format(it) }}")
                }
            }
        }

        if (uuid == CHAR_F003) {
            Log.d(TAG, "RX [F003]: ${bytesToHex(data)}")
            // Always process F003 locally to ensure we get glucose readings
            // even if the vendor lib is slow or disconnects.
            if (wantsRaw() || wantsAuto()) {
                handleF003Data(data)
            }
            return
        }

        if (uuid == CHAR_F001) {
            Log.i(TAG, "RX [F001]: ${bytesToHex(data)}")
            if (data.size >= 16) {
                addIvCandidatesFrom(data)
                Log.i(TAG, "F001 response added as IV candidates: ${pendingIVCandidates.size} total")
            }
            handleF001Response(data)
        }

        if (uuid == CHAR_F002) {
             handleF002Response(gatt, data)
        }

        // FF31: Vendor private service response channel (responses to commands sent via FF32)
        if (uuid == CHAR_FF31) {
            Log.i(TAG, "RX [FF31]: ${bytesToHex(data)} (len=${data.size})")
            // Already forwarded to vendor native lib via onReceiveData above.
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        if (vendorWriteActive) {
            vendorWriteActive = false
            sendNextVendorWrite()
            // If we are in vendor mode, do not process manual handshake logic for these writes
            if (vendorBleEnabled) return
        }
        if (vendorBleEnabled) return // Catch-all: don't track manual handshake state if delegating
        val uuid = characteristic.uuid
        if (useOfficialHandshake && (uuid == CHAR_F001 || uuid == CHAR_F002)) {
            val expectedOp = if (expectedF002ResponseOps.isEmpty()) "none" else expectedF002ResponseOps.joinToString(",") { "0x%02X".format(it) }
            Log.d(
                TAG,
                "WRITE [$uuid]: status=$status value=${bytesToHex(characteristic.value ?: byteArrayOf())} (expectedOp=$expectedOp)"
            )
            
            if (status == BluetoothGatt.GATT_SUCCESS && expectedF002ResponseOps.isEmpty()) {
                // This step didn't expect a notification/read response, so advance handshake 
                // now that the write confirmation has been received.
                val nextDelay = if (uuid == CHAR_F002) 150L else 400L
                handshakeStep++
                handshakeHandler.postDelayed({
                    mBluetoothGatt?.let { performHandshakeStep(it) }
                }, nextDelay)
            }
        }
    }

    private fun handleF002Response(gatt: BluetoothGatt, data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val firstByte = data[0].toInt() and 0xFF
        Log.d(TAG, "RX [F002]: ${bytesToHex(data)} (step=$handshakeStep)")

        if (useOfficialHandshake) {
            // SNOOP: Attempt to capture IV from F002 response (Handshake Step 2/3)
            // This response contains the session Key (IV) mixed with our challenge.
            if (dynamicIV == null && data.size >= 16) {
                Log.d(TAG, "SNOOP: Capturing IV candidate from F002 (len=${data.size})")
                addIvCandidatesFrom(data)
            } else {
                 if (dynamicIV != null) Log.v(TAG, "SNOOP: IV already captured.")
                 if (data.size < 16) Log.w(TAG, "SNOOP: F002 data too short for IV (${data.size})")
            }

            // If vendor enabled, STOP HERE. Do not advance handshake or write data.
            // The native lib handles the response; we only wanted to snoop the IV.
            if (vendorBleEnabled) {
                Log.v(TAG, "SNOOP: Vendor active, ignoring F002 logic after snoop.")
                return
            }

            val expected = expectedF002ResponseOps
            if (expected.isEmpty()) {
                return
            }
            if (firstByte in expected) {
                if (firstByte == 0xA8) {
                    // LinX 1.7.25: IV is embedded in the A8 response as:
                    // A8 67 <16 bytes IV> <4 bytes tail>
                    if (data.size >= 18) {
                        dynamicIV = data.copyOfRange(2, 18)
                        pendingIVCandidates.clear()
                        Log.i(TAG, "IV captured from A8 response: ${bytesToHex(dynamicIV!!)}")
                    } else {
                        Log.w(TAG, "A8 response too short (${data.size}); ignoring")
                        return
                    }
                }

                cancelHandshakeTimers()
                handshakeRetries = 0
                expectedF002ResponseOps = emptySet()
                handshakeStep++
                handshakeHandler.postDelayed({ performHandshakeStep(gatt) }, handshakeStepDelayMs())
                return
            }

            // Some firmwares do not echo the opcode on A8/AF. Treat any 16+ byte response as a candidate IV block.
            if (expected.any { it == 0xA8 || it == 0xAF } && data.size >= 16) {
                addIvCandidatesFrom(data)
                Log.w(
                    TAG,
                    "A8/AF response missing header; cached ${pendingIVCandidates.size} IV candidates"
                )
                cancelHandshakeTimers()
                handshakeRetries = 0
                expectedF002ResponseOps = emptySet()
                handshakeStep++
                handshakeHandler.postDelayed({ performHandshakeStep(gatt) }, handshakeStepDelayMs())
                return
            }

            Log.d(TAG, "F002 response 0x%02X ignored (expecting $expected)".format(firstByte))
            return
        }

        var capturedIv = false
        if (firstByte == 0xB5 || firstByte == 0xB4) {
            if (data.size >= 17) {
                dynamicIV = data.copyOfRange(1, 17)
                capturedIv = true
                Log.d(TAG, "IV Captured from 0x%02X".format(firstByte))
            } else if (data.size == 16) {
                dynamicIV = data.copyOfRange(0, 16)
                capturedIv = true
                Log.d(TAG, "IV Captured from 0x%02X (16 bytes)".format(firstByte))
            } else {
                Log.w(TAG, "IV not found in 0x%02X response (len=%d)".format(firstByte, data.size))
            }
        } else if (handshakeStep >= 10 && data.size >= 16) {
            // Legacy stacks sometimes omit B4/B5 header; cache IV candidates for F003 validation.
            addIvCandidatesFrom(data)
            Log.w(TAG, "Legacy IV response missing header; cached ${pendingIVCandidates.size} IV candidates")
        }

        if (firstByte == 0xB4 || firstByte == 0xB5 || capturedIv) {
            cancelHandshakeTimers()
            if (handshakeStep < 1) handshakeStep = 1
            handshakeStep++
            performHandshakeStep(gatt)
            return
        }

        if (firstByte == 0xB6) {
            cancelHandshakeTimers()
            Log.i(TAG, "Handshake Complete. Stream Starting.")
            handshakeStep++
            return
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) +
                    Character.digit(s[i + 1], 16)).toByte()
        }
        return data
    }

    // --- RESPONSE HANDLERS ---
    
    private fun handleF001Response(data: ByteArray) {
        // F001 responses are typically confirmations or error codes for commands.
        // Step 1: 0xB0 response -> Key exchange / Challenge response?
        if (data.isEmpty()) return
        val op = data[0].toInt() and 0xFF
        Log.d(TAG, "handleF001Response: op=0x%02X len=%d".format(op, data.size))
        
        // If we are tracking handshake, we might want to log this.
    }

    // (Duplicate handleF003Data removed)

    // --- CRYPTO ---
    private fun reverseBits(b: Byte): Byte {
        var res = 0
        var x = b.toInt() and 0xFF
        for (i in 0 until 8) {
            res = (res shl 1) or (x and 1)
            x = x shr 1
        }
        return res.toByte()
    }

    private fun decrypt(data: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CFB/NoPadding")
        val keySpec = SecretKeySpec(MASTER_KEY, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    // --- UTILS ---
    // fun setViewMode(mode: Int) removed to avoid platform declaration clash with var viewMode

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    private fun reverseBitsCopy(bytes: ByteArray): ByteArray {
        val out = bytes.clone()
        for (i in out.indices) {
            out[i] = reverseBits(out[i])
        }
        return out
    }

    private fun storeVendorParams(bytes: ByteArray) {
        val hex = bytesToHex(bytes)
        prefs.edit().putString(prefKey("lastVendorParams"), hex).apply()
    }

    private fun loadVendorParams(): ByteArray? {
        val key = prefKey("lastVendorParams")
        val hex = prefs.getString(key, null) ?: return null
        return try { hexToBytes(hex) } catch (e: Exception) { null }
    }

    private fun hexToBytes(s: String): ByteArray {
        val clean = s.replace(" ", "")
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    // Added for Strict Bonding Sequence
    private var isBondingSequenceActive = false

    @SuppressLint("MissingPermission")
    private fun startBondingSequence(gatt: BluetoothGatt) {
        isBondingSequenceActive = true
        // 1. Construct the first command (Key Formation / Authentication Challenge)
        // Usually B0 or similar. We'll use the first step of the official plan.
        val bondingPlan = buildOfficialHandshakePlan(gatt) // Force "Bonded" plan structure just for the first cmd
        if (bondingPlan.isEmpty()) {
            Log.e(TAG, "Bonding Sequence Failed: No plan available.")
            return
        }
        val firstStep = bondingPlan[0]
        
        Log.i(TAG, "Bonding Sequence: Sending KEY COMMAND: ${bytesToHex(firstStep.data)}")
        
        // Write the command directly (bypass notification logic for now)
        val service = gatt.getService(SERVICE_F000)
        val charF002 = service?.getCharacteristic(CHAR_F002)
        if (charF002 != null) {
            charF002.value = firstStep.data
            charF002.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val success = gatt.writeCharacteristic(charF002)
            Log.i(TAG, "Bonding Sequence: Write Status = $success")
            
            // 2. IMMEDIATELY Request Bond
            Log.i(TAG, "Bonding Sequence: Requesting createBond() IMMEDIATELY.")
            gatt.device.createBond()
            
             // 3. Poll for Bond State
            bondRequested = true
            bondWaitUntilMs = System.currentTimeMillis() + BOND_WAIT_MS
            
            handshakeHandler.postDelayed(object : Runnable {
                override fun run() {
                    if (!isBondingSequenceActive) return
                    val state = gatt.device.bondState
                    if (state == BluetoothDevice.BOND_BONDED) {
                        Log.i(TAG, "Bonding Sequence: Bonded! Resuming handshake.")
                        isBondingSequenceActive = false
                        startHandshake(gatt)
                    } else if (System.currentTimeMillis() > bondWaitUntilMs) {
                         Log.w(TAG, "Bonding Sequence: Timeout waiting for bond. Resuming anyway.")
                         isBondingSequenceActive = false
                         startHandshake(gatt)
                    } else {
                        // Keep polling
                        handshakeHandler.postDelayed(this, 500)
                    }
                }
            }, 500)

        } else {
            Log.e(TAG, "Bonding Sequence Failed: F002 not found.")
        }
    }

    // --- ROBUST RESET IMPLEMENTATION ---

    /**
     * Encrypts a command payload using the Dynamic IV + Master Key.
     */
    private fun encryptCommand(cmd: ByteArray): ByteArray? {
        val iv = dynamicIV
        if (iv == null) {
            Log.e(TAG, "Cannot encrypt command: dynamicIV is null")
            return null
        }
        try {
            val cipher = Cipher.getInstance("AES/CFB/NoPadding")
            val keySpec = SecretKeySpec(MASTER_KEY, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            return cipher.doFinal(cmd)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            return null
        }
    }

    /**
     * Sends an encrypted command to F001.
     */
    private fun sendEncryptedCommand(cmd: ByteArray, opDescription: String): Boolean {
        val gatt = mBluetoothGatt
        if (gatt == null) {
            Log.w(TAG, "$opDescription failed: GATT not connected")
            return false
        }
        
        val encrypted = encryptCommand(cmd)
        if (encrypted == null) {
             Log.e(TAG, "$opDescription failed: Encryption error (missing IV?)")
             return false
        }

        val service = gatt.getService(SERVICE_F000)
        val charF001 = service?.getCharacteristic(CHAR_F001)
        if (charF001 == null) {
             Log.e(TAG, "$opDescription failed: F001 not found")
             return false
        }

        charF001.value = encrypted
        charF001.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        
        val success = gatt.writeCharacteristic(charF001)
        Log.i(TAG, "$opDescription sent: ${bytesToHex(encrypted)} (Raw: ${bytesToHex(cmd)}) Success=$success")
        return success
    }

    /**
     * Writes a raw command to FF32 on the FF30 vendor private service.
     * This is the direct path for sensor lifecycle commands (reset, new sensor, shelf mode).
     * Returns true if the write was enqueued successfully.
     */
    @SuppressLint("MissingPermission")
    private fun writeFF32Command(data: ByteArray, opDescription: String): Boolean {
        val gatt = mBluetoothGatt
        if (gatt == null) {
            Log.w(TAG, "$opDescription via FF32 failed: GATT not connected")
            return false
        }
        val service = gatt.getService(SERVICE_FF30)
        if (service == null) {
            Log.w(TAG, "$opDescription via FF32 failed: FF30 service not found")
            return false
        }
        val charFF32 = service.getCharacteristic(CHAR_FF32)
        if (charFF32 == null) {
            Log.w(TAG, "$opDescription via FF32 failed: FF32 characteristic not found")
            return false
        }
        charFF32.value = data
        // FF32 typically uses Write Command (no response) based on official app behavior
        charFF32.writeType = if ((charFF32.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val success = gatt.writeCharacteristic(charFF32)
        Log.i(TAG, "$opDescription via FF32: data=${bytesToHex(data)} writeType=${charFF32.writeType} success=$success")
        return success
    }

    /**
     * Removes the BLE bond for the connected sensor device via reflection.
     * This forces re-initialization (key exchange, bonding) on next connection.
     * Returns true if bond removal was invoked (may still be async).
     */
    @SuppressLint("MissingPermission")
    private fun removeSensorBond(): Boolean {
        val device = mBluetoothGatt?.device ?: mActiveBluetoothDevice
        if (device == null) {
            Log.w(TAG, "removeSensorBond: no device")
            return false
        }
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            Log.i(TAG, "removeSensorBond: already unbonded")
            return true
        }
        return try {
            val method: Method = device.javaClass.getMethod("removeBond")
            val result = method.invoke(device) as Boolean
            Log.i(TAG, "removeSensorBond: removeBond() returned $result")
            result
        } catch (e: Throwable) {
            Log.e(TAG, "removeSensorBond: reflection failed: ${e.message}")
            false
        }
    }

    /**
     * Resets the sensor (restarts lifecycle).
     * Multi-strategy approach:
     *   Strategy 1: Vendor native lib (controller.reset()) — requires AES init
     *   Strategy 2: Direct FF32 write on FF30 service
     *   Strategy 3: BLE bond removal + disconnect (forces re-init on reconnect)
     *
     * Each strategy is tried in order; any success short-circuits.
     * All attempts are logged for diagnostics.
     */
    fun resetSensor(): Boolean {
        Log.i(TAG, "=== RESET SENSOR: Multi-Strategy Reset ===")
        
        // --- Strategy 1: Vendor Native Lib ---
        // executeVendorCommand now properly waits for GATT + AES initialization
        // before executing the command, so we don't need to pre-check aesReady here.
        if (vendorBleEnabled && vendorController != null) {
            Log.i(TAG, "Reset Strategy 1: Vendor native lib")
            val success = executeVendorCommand("reset-sensor", AidexXOperation.RESET) { ctrl ->
                ctrl.clearStorage()
                ctrl.reset()
            }
            if (success) {
                Log.i(TAG, "Reset Strategy 1: SUCCESS — reset command accepted by vendor native lib")
                // Give time for the BLE write to actually transmit
                try { Thread.sleep(1000) } catch (_: InterruptedException) {}
                return true
            } else {
                Log.w(TAG, "Reset Strategy 1: FAILED — vendor native command not accepted (AES handshake may have failed)")
            }
        } else {
            Log.w(TAG, "Reset Strategy 1: SKIPPED — vendor BLE disabled or controller null")
        }

        // --- Strategy 2: Direct FF32 Write on FF30 Service ---
        // The FF30 service is the vendor private channel for maintenance commands.
        // We try writing the reset opcode directly.
        // Based on AidexXOperation: RESET=0xF00. The native lib translates this into
        // a wire-format command. Without knowing exact format, we try the opcode bytes.
        Log.i(TAG, "Reset Strategy 2: Direct FF32 write on FF30 service")
        
        // Try via vendor write queue (uses proper serialization)
        val vendorResetSent = try {
            val ff32Id = 0xFF32.toInt()
            // Attempt 2a: Single-byte reset command (common BLE pattern)
            vendorWrite(ff32Id, byteArrayOf(0x0F.toByte(), 0x00.toByte())) // 0xF00 as little-endian 2 bytes
            Thread.sleep(300)
            // Attempt 2b: Also try the full AidexXOperation.RESET value as big-endian
            vendorWrite(ff32Id, byteArrayOf(
                ((AidexXOperation.RESET shr 8) and 0xFF).toByte(),
                (AidexXOperation.RESET and 0xFF).toByte()
            ))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Reset Strategy 2 (vendor queue): failed: ${t.message}")
            false
        }

        // Also try direct GATT write bypassing vendor queue
        if (!vendorResetSent) {
            val directSuccess = writeFF32Command(
                byteArrayOf(0x0F.toByte(), 0x00.toByte()),
                "Reset"
            )
            if (directSuccess) {
                Log.i(TAG, "Reset Strategy 2: FF32 direct write sent")
                // Wait for potential response on FF31
                Thread.sleep(500)
            }
        }

        // --- Strategy 3: BLE Bond Removal + Disconnect ---
        // Removing the bond forces re-initialization on next connection.
        // The sensor will go back to unbonded state and allow fresh pairing.
        Log.i(TAG, "Reset Strategy 3: BLE bond removal + disconnect")
        val bondRemoved = removeSensorBond()
        if (bondRemoved) {
            Log.i(TAG, "Reset Strategy 3: Bond removed. Disconnecting GATT...")
            // Suppress disconnect debounce for this explicit user action
            disregardDisconnectsUntil = 0
            disconnect()
            // Clear handshake/session state
            dynamicIV = null
            handshakeStep = 0
            handshakePlan = emptyList()
            handshakePlanLabel = "none"
            ivLocked = false
            ivLockedFromBroadcast = false
            ivLockKey = null
            ivLockCount = 0
            vendorGattConnected = false
            vendorGattNotified = false
            vendorNativeReady = false
            vendorExecuteConnectReceived = false
            vendorConnectSuccessCrashCount = 0
            vendorLongConnectTriggered = false
            vendorWriteQueue.clear()
            vendorWriteActive = false
            Log.i(TAG, "Reset Strategy 3: Session state cleared. Sensor will re-initialize on next connection.")
            return true
        } else {
            Log.w(TAG, "Reset Strategy 3: Bond removal failed or device not bonded")
        }

        // If all strategies attempted but none confirmed success
        Log.w(TAG, "=== RESET SENSOR: All strategies attempted. Check logs for partial success. ===")
        return vendorResetSent // Return true if at least FF32 write was sent
    }

    /**
     * Starts a new sensor session.
     * Multi-strategy approach:
     *   Strategy 1: Vendor native lib (controller.newSensor(datetime))
     *   Strategy 2: Direct FF32 write with SET_NEW_SENSOR opcode
     *   Strategy 3: Reset + reconnect (effectively starts fresh)
     */
    fun startNewSensor(): Boolean {
        Log.i(TAG, "=== START NEW SENSOR: Multi-Strategy ===")
        
        // --- Strategy 1: Vendor Native Lib ---
        // executeVendorCommand now properly waits for GATT + AES initialization
        if (vendorBleEnabled && vendorController != null) {
            Log.i(TAG, "NewSensor Strategy 1: Vendor native lib")
            val success = executeVendorCommand("new-sensor", AidexXOperation.SET_NEW_SENSOR) { ctrl ->
                val datetime = AidexXDatetimeEntity(Calendar.getInstance())
                ctrl.newSensor(datetime)
            }
            if (success) {
                Log.i(TAG, "NewSensor Strategy 1: SUCCESS — command accepted by vendor native lib")
                // Give time for the BLE write to actually transmit
                try { Thread.sleep(1000) } catch (_: InterruptedException) {}
                // Clear session state for fresh start
                dynamicIV = null
                ivLocked = false
                ivLockedFromBroadcast = false
                ivLockKey = null
                ivLockCount = 0
                return true
            } else {
                Log.w(TAG, "NewSensor Strategy 1: FAILED — vendor native command not accepted")
            }
        } else {
            Log.w(TAG, "NewSensor Strategy 1: SKIPPED — vendor BLE disabled or controller null")
        }

        // --- Strategy 2: Direct FF32 Write ---
        Log.i(TAG, "NewSensor Strategy 2: Direct FF32 write with SET_NEW_SENSOR opcode")
        val opBytes = byteArrayOf(
            ((AidexXOperation.SET_NEW_SENSOR shr 8) and 0xFF).toByte(),
            (AidexXOperation.SET_NEW_SENSOR and 0xFF).toByte()
        )
        // Append current datetime as payload (year_hi, year_lo, month, day, hour, minute, second)
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val datetimePayload = byteArrayOf(
            ((year shr 8) and 0xFF).toByte(),
            (year and 0xFF).toByte(),
            (cal.get(Calendar.MONTH) + 1).toByte(),
            cal.get(Calendar.DAY_OF_MONTH).toByte(),
            cal.get(Calendar.HOUR_OF_DAY).toByte(),
            cal.get(Calendar.MINUTE).toByte(),
            cal.get(Calendar.SECOND).toByte()
        )
        val fullCommand = opBytes + datetimePayload
        
        val ff32Sent = writeFF32Command(fullCommand, "NewSensor")
        if (ff32Sent) {
            Log.i(TAG, "NewSensor Strategy 2: FF32 write sent. Waiting for response...")
            Thread.sleep(500)
            // Clear session state
            dynamicIV = null
            ivLocked = false
            ivLockedFromBroadcast = false
            ivLockKey = null
            ivLockCount = 0
            return true
        }

        // --- Strategy 3: Full Reset + Reconnect ---
        Log.i(TAG, "NewSensor Strategy 3: Falling back to full reset")
        return resetSensor()
    }
}
