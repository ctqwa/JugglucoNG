// JugglucoNG — AiDex Driver Interface
//
// Shared interface for all AiDex driver implementations (vendor-lib AiDexSensor
// and native-Kotlin AiDexBleManager). SensorViewModel, ComposeHost, and other UI
// code type-check `is AiDexDriver` instead of `is AiDexSensor`, so both drivers
// work identically from the UI's perspective.
//
// Lives in tk.glucodata.drivers.aidex (Java-accessible package).

package tk.glucodata.drivers.aidex

/**
 * Calibration record from the AiDex sensor's on-board storage.
 *
 * Previously an inner class of AiDexSensor; moved here so both driver
 * implementations can return the same type.
 */
data class CalibrationRecord(
    val index: Int,
    val timeOffsetMinutes: Int,
    val referenceGlucoseMgDl: Int,
    val cf: Float,
    val offset: Float,
    val isValid: Boolean,
    /** Absolute timestamp: sensorstartmsec + timeOffsetMinutes * 60_000L */
    val timestampMs: Long,
)

/**
 * Interface that all AiDex BLE driver implementations must satisfy.
 *
 * Both [AiDexSensor] (vendor-lib driver) and
 * [tk.glucodata.drivers.aidex.native.ble.AiDexBleManager] (native Kotlin driver)
 * implement this interface.
 *
 * **UI code should check `gatt is AiDexDriver`** rather than `gatt is AiDexSensor`.
 */
interface AiDexDriver {

    // ── Status ──────────────────────────────────────────────────────────

    /** Human-readable BLE/protocol status for the sensor detail UI. */
    fun getDetailedBleStatus(): String

    /** Whether the driver is paused (not actively receiving data). */
    val isPaused: Boolean

    /** Whether only BLE advertisements are used (no GATT connection). */
    val broadcastOnlyConnection: Boolean

    /** Whether the sensor has saved vendor pairing keys. */
    fun isVendorPaired(): Boolean

    /** Whether the vendor BLE stack is actively connected right now. */
    fun isVendorConnected(): Boolean

    // ── Metadata ────────────────────────────────────────────────────────

    /** Calibration records stored on the sensor. Newest first. */
    fun getCalibrationRecords(): List<CalibrationRecord>

    /** Sensor battery voltage in millivolts (0 = not yet received). */
    fun getBatteryMillivolts(): Int

    /** Whether the sensor has reported itself as expired. */
    fun isSensorExpired(): Boolean

    /** Hours of sensor life remaining (-1 = unknown, 0 = expired). */
    fun getSensorRemainingHours(): Int

    /** Hours since sensor activation (-1 = unknown). */
    fun getSensorAgeHours(): Int

    /** Firmware version string from startup metadata / vendor device-info. */
    val vendorFirmwareVersion: String

    /** Hardware version string from startup metadata / vendor device-info. */
    val vendorHardwareVersion: String

    /** Model name from startup metadata / vendor device-info (e.g. "GX-01S"). */
    val vendorModelName: String

    // ── Lifecycle ───────────────────────────────────────────────────────

    /**
     * Full teardown: stop BLE, remove bond, wipe AES keys, remove from prefs.
     * Called from terminate/forget flows.
     */
    fun forgetVendor()

    /**
     * Non-destructive disconnect: stop vendor BLE but preserve pairing keys
     * so the sensor can reconnect later.
     */
    fun softDisconnect()

    /** Force an immediate reconnection attempt. */
    fun manualReconnectNow()

    /** Enable or disable broadcast-only (advertisement) mode. */
    fun setBroadcastOnlyConnection(enabled: Boolean)

    // ── Sensor Commands ─────────────────────────────────────────────────

    /** Send a hardware reset (0xF0) to the sensor. Returns true on success. */
    fun resetSensor(): Boolean

    /** Activate a new sensor (SET_NEW_SENSOR 0x20). Returns true on success. */
    fun startNewSensor(): Boolean

    /** Send a calibration value to the sensor. Returns true on success. */
    fun calibrateSensor(glucoseMgDl: Int): Boolean

    /** Remove vendor pairing (delete bond + keys). Returns true on success. */
    fun unpairSensor(): Boolean

    /** Initiate re-pairing from scratch. */
    fun rePairSensor()

    /** Send an arbitrary maintenance/diagnostic command. Returns true on success. */
    fun sendMaintenanceCommand(opCode: Int): Boolean

    // ── Bias Compensation ───────────────────────────────────────────────

    /** Whether post-reset initialization bias compensation is active. */
    val resetCompensationEnabled: Boolean

    /** Enable post-reset bias compensation. */
    fun enableResetCompensation()

    /** Disable post-reset bias compensation. */
    fun disableResetCompensation()

    /** Human-readable compensation status (e.g. "Phase 1: x1.176 (23h left)"). */
    fun getCompensationStatusText(): String

    // ── Data Mode ───────────────────────────────────────────────────────

    /** Current view/calibration mode (0=Auto, 1=Raw, 2=Auto+Raw, 3=Raw+Auto). */
    var viewMode: Int

    // ── Device List Dirty Flag ──────────────────────────────────────────

    companion object {
        /**
         * Set to true when the device list changes (sensor added/removed/reconnected).
         * SensorViewModel polls this to trigger a refresh.
         *
         * Both driver implementations should set this when their state changes.
         */
        @Volatile
        @JvmStatic
        var deviceListDirty = false
    }
}
