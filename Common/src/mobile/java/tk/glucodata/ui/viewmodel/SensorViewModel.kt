package tk.glucodata.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tk.glucodata.SensorBluetooth
import tk.glucodata.SuperGattCallback
import tk.glucodata.Natives
import tk.glucodata.bluediag
import kotlin.math.abs

/**
 * Color palette for sensors - M3 Expressive style.
 * Colors are assigned deterministically based on sensor serial hash.
 */
object SensorColors {
    // Muted, expressive palette that works in both light and dark modes
    private val palette = listOf(
        Color(0xFF6750A4), // Primary purple
        Color(0xFF00796B), // Teal
        Color(0xFF5C6BC0), // Indigo
        Color(0xFFD81B60), // Pink
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFF4511E), // Deep orange
        Color(0xFF8E24AA), // Purple
    )
    
    fun getColor(serial: String): Color {
        val index = abs(serial.hashCode()) % palette.size
        return palette[index]
    }
    
    fun getColorIndex(serial: String): Int {
        return abs(serial.hashCode()) % palette.size
    }
}

data class SensorInfo(
    val serial: String,
    val deviceAddress: String,
    val connectionStatus: String,
    val starttime: String,
    val streaming: Boolean,
    val rssi: Int,
    val dataptr: Long,
    val officialEnd: String,
    val expectedEnd: String,
    val viewMode: Int,
    val autoResetDays: Int,
    val isSibionics: Boolean,
    val isSibionics2: Boolean,
    val isAidex: Boolean,
    val startMs: Long,
    val officialEndMs: Long,
    val expectedEndMs: Long,
    val customCalEnabled: Boolean,
    val customCalIndex: Int,
    val customCalAutoReset: Boolean,
    val detailedStatus: String = "",
    val isActive: Boolean = false,  // True if this is the primary data source
    val broadcastOnlyConnection: Boolean = false,  // AiDex: use BLE advertisements only
    val isVendorPaired: Boolean = false,  // AiDex: has saved vendor pairing keys
    val vendorCalibrations: List<VendorCalibrationInfo> = emptyList(),  // AiDex: calibration records from sensor
    val isVendorConnected: Boolean = false,  // AiDex: vendor BLE stack actively connected
    val batteryMillivolts: Int = 0,  // AiDex: sensor battery voltage in mV (0 = not yet received)
    val isSensorExpired: Boolean = false,  // AiDex: sensor has reported itself as expired
    // Edit 58a/58b/58c: Parsed sensor metadata from vendor protocol
    val sensorRemainingHours: Int = -1,  // AiDex: hours remaining (-1 = unknown)
    val sensorAgeHours: Int = -1,  // AiDex: sensor age in hours (-1 = unknown)
    val vendorFirmware: String = "",  // AiDex: firmware version from GET_DEVICE_INFO
    val vendorHardware: String = "",  // AiDex: hardware version from GET_DEVICE_INFO
    val vendorModel: String = "",  // AiDex: model name from GET_DEVICE_INFO (e.g. "GX-01S")
    // Edit 59: Reset compensation state
    val resetCompensationActive: Boolean = false,  // AiDex: whether initialization bias compensation is active
    val resetCompensationStatus: String = ""  // AiDex: human-readable compensation status (e.g. "Phase 1: ×1.176 (23h left)")
) {
    /** Get the assigned color for this sensor */
    val color: Color get() = SensorColors.getColor(serial)
}

/** UI-friendly calibration record from the AiDex sensor */
data class VendorCalibrationInfo(
    val index: Int,
    val referenceGlucoseMgDl: Int,
    val timeOffsetMinutes: Int,
    val timestampMs: Long,
    val cf: Float,
    val offset: Float,
    val isValid: Boolean
)


class SensorViewModel : ViewModel() {
    private val _sensors = MutableStateFlow<List<SensorInfo>>(emptyList())
    val sensors = _sensors.asStateFlow()

    // Polling job - only active when screen is visible
    private var pollingJob: Job? = null

    init {
        // Initial refresh with device sync — only time we need to call updateDevices() automatically
        refreshSensorsWithDeviceSync()
    }

    /**
     * Start real-time polling when Sensors screen becomes visible.
     * Call this from LaunchedEffect in SensorScreen.
     */
    fun startPolling() {
        // Cancel any existing job first
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                // Edit 62c: Only do expensive device sync when something actually changed
                if (tk.glucodata.drivers.aidex.AiDexSensor.deviceListDirty) {
                    tk.glucodata.drivers.aidex.AiDexSensor.deviceListDirty = false
                    refreshSensorsWithDeviceSync()
                } else {
                    refreshSensors()
                }
                kotlinx.coroutines.delay(2000) // 2 second refresh for real-time feel
            }
        }
    }

    /**
     * Stop polling when leaving Sensors screen.
     * Call this from DisposableEffect onDispose in SensorScreen.
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Set the main active sensor (for notifications/xDrip).
     * Multi-sensor: No longer clears the Room DB. The new sensor's data is already
     * in Room (synced via HistorySync). The dashboard/chart will automatically
     * filter by the new main sensor serial. We just trigger a sync to ensure
     * the latest data is available.
     */
    fun setMain(serial: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Natives.setcurrentsensor(serial)
                // Ensure this sensor's data is synced into Room (non-destructive)
                tk.glucodata.data.HistorySync.forceFullSyncForSensor(serial)
                refreshSensorsWithDeviceSync()
            } catch (e: Exception) {
                android.util.Log.e("SensorVM", "Failed to set main sensor: ${e.message}")
            }
        }
    }

    fun refreshSensors() {
        refreshSensorsInternal(syncDeviceList = false)
    }

    /**
     * Edit 60c: Full refresh that also calls SensorBluetooth.updateDevices() to sync the
     * device list with native state. This is expensive (touches SharedPrefs, native sensors,
     * generates ~16 log lines per call) so it should only be called on actual state changes
     * (init, set main, remove sensor, etc.), NOT on the 2-second polling loop.
     */
    private fun refreshSensorsWithDeviceSync() {
        refreshSensorsInternal(syncDeviceList = true)
    }

    private fun refreshSensorsInternal(syncDeviceList: Boolean) {
        viewModelScope.launch {
            if (syncDeviceList) {
                SensorBluetooth.updateDevices()
            }
            val gatts = SensorBluetooth.mygatts() ?: ArrayList()
            
            // Get the currently active sensor (primary data source)
            val activeSensors = try { Natives.activeSensors() } catch (_: Exception) { null }
            val activeSensorSerial = try {
                var sName = Natives.lastsensorname()
                
                // Conflict Resolution: If last sensor is gone but we have valid active ones,
                // switch focus to the valid one.
                // Edit 57a: Actually persist the correction via setcurrentsensor() so that
                // Notify.java stops calling getdataptr() on the finished sensor. Previously
                // this only updated the local variable but never wrote back to native state,
                // so lastsensorname() kept returning the stale serial on every notification.
                if (activeSensors != null && activeSensors.isNotEmpty()) {
                    if (sName.isNullOrEmpty() || !activeSensors.contains(sName)) {
                         val next = activeSensors[0]
                         try {
                             Natives.setcurrentsensor(next)
                             android.util.Log.i("SensorVM", "Edit 57a: Corrected lastsensorname from '$sName' to '$next'")
                         } catch (t: Throwable) {
                             android.util.Log.e("SensorVM", "Edit 57a: setcurrentsensor failed: ${t.message}")
                         }
                         sName = next
                    }
                } else if (!sName.isNullOrEmpty()) {
                    // No active sensors at all but lastsensorname still set — clear it
                    try {
                        Natives.setcurrentsensor("")
                        android.util.Log.i("SensorVM", "Edit 57a: Cleared stale lastsensorname '$sName' (no active sensors)")
                    } catch (_: Throwable) {}
                    sName = ""
                }
                sName
            } catch (e: Exception) {
                null
            }

            // Edit 56c: Build a set of active sensor serials for filtering.
            // Legacy sensors not in activeSensors() are finished — exclude them from the UI.
            // AiDex sensors (X- prefix) are managed via SharedPreferences, not activeSensors().
            val activeSet = activeSensors?.toHashSet() ?: HashSet()

            val sensorList = gatts.mapNotNull { gatt ->
                try {
                    // Edit 56c: Skip finished legacy sensors (not in activeSensors list).
                    // AiDex sensors bypass this check since they're tracked in SharedPreferences.
                    val serial = gatt.SerialNumber ?: ""
                    if (gatt !is tk.glucodata.drivers.aidex.AiDexSensor && serial.isNotEmpty() && !activeSet.contains(serial)) {
                        android.util.Log.d("SensorVM", "Edit 56c: Filtering out finished sensor $serial from UI")
                        return@mapNotNull null
                    }
                    
                    // KOTLIN SENSOR SUPPORT (AiDex)
                    if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
                         // Map pure Kotlin fields to SensorInfo
                         val meaningfulStatus = gatt.getDetailedBleStatus()
                         val rawBleStatus = gatt.constatstatusstr ?: ""
                         // Only show BLE status if it's a raw Status= code (not our custom strings)
                         val bleStatusForDisplay = if (rawBleStatus.startsWith("Status=")) rawBleStatus else ""
                         
                         val aiDexDataptr = gatt.dataptr
                         val aiDexPaused = try { gatt.isPaused } catch (_: Throwable) { false }
                         val aiDexStreaming = !aiDexPaused
                         val startMs = if (aiDexDataptr != 0L) Natives.getSensorStartmsec(aiDexDataptr) else 0L
                         val officialEndMs = if (aiDexDataptr != 0L) Natives.getSensorEndTime(aiDexDataptr, true) else 0L
                         val expectedEndMs = if (aiDexDataptr != 0L) Natives.getSensorEndTime(aiDexDataptr, false) else 0L
                         val currentViewMode = if (aiDexDataptr != 0L) Natives.getViewMode(aiDexDataptr) else 0
                         
                         // Get calibration records from the sensor
                         val calRecords = try {
                             gatt.getCalibrationRecords().map { rec ->
                                 VendorCalibrationInfo(
                                     index = rec.index,
                                     referenceGlucoseMgDl = rec.referenceGlucoseMgDl,
                                     timeOffsetMinutes = rec.timeOffsetMinutes,
                                     timestampMs = rec.timestampMs,
                                     cf = rec.cf,
                                     offset = rec.offset,
                                     isValid = rec.isValid
                                 )
                             }
                         } catch (_: Throwable) { emptyList() }

                         // Check if vendor BLE stack is actively connected
                         val vendorConnected = try { gatt.isVendorConnected() } catch (_: Throwable) { false }
                         
                         SensorInfo(
                            serial = gatt.SerialNumber ?: "AiDex",
                            deviceAddress = gatt.mActiveDeviceAddress ?: "Unknown",
                            connectionStatus = bleStatusForDisplay,  // Raw BLE status (Status=X) or empty
                            starttime = if (startMs > 0) tk.glucodata.bluediag.datestr(startMs) else "",
                            streaming = aiDexStreaming,
                            rssi = gatt.readrssi,
                            dataptr = aiDexDataptr, 
                            officialEnd = if (officialEndMs > 0) tk.glucodata.bluediag.datestr(officialEndMs) else "",
                            expectedEnd = if (expectedEndMs > 0) tk.glucodata.bluediag.datestr(expectedEndMs) else "",
                            viewMode = currentViewMode, 
                            autoResetDays = 0,
                            isSibionics = false,
                            isSibionics2 = false,
                            isAidex = true,
                            startMs = startMs,
                            officialEndMs = officialEndMs,
                            expectedEndMs = expectedEndMs,
                            customCalEnabled = false,
                            customCalIndex = 0,
                            customCalAutoReset = false,
                            detailedStatus = meaningfulStatus,  // Meaningful status (Broadcast Mode, Scanning, etc.)
                            isActive = (activeSensorSerial == gatt.SerialNumber),
                            broadcastOnlyConnection = gatt.broadcastOnlyConnection,
                            isVendorPaired = gatt.isVendorPaired(),
                            vendorCalibrations = calRecords,
                            isVendorConnected = vendorConnected,
                            batteryMillivolts = try { gatt.getBatteryMillivolts() } catch (_: Throwable) { 0 },
                            isSensorExpired = try { gatt.isSensorExpired() } catch (_: Throwable) { false },
                            sensorRemainingHours = try { gatt.getSensorRemainingHours() } catch (_: Throwable) { -1 },
                            sensorAgeHours = try { gatt.getSensorAgeHours() } catch (_: Throwable) { -1 },
                            vendorFirmware = try { gatt.vendorFirmwareVersion } catch (_: Throwable) { "" },
                            vendorHardware = try { gatt.vendorHardwareVersion } catch (_: Throwable) { "" },
                            vendorModel = try { gatt.vendorModelName } catch (_: Throwable) { "" },
                            // Edit 59: Reset compensation state
                            resetCompensationActive = try { gatt.resetCompensationEnabled } catch (_: Throwable) { false },
                            resetCompensationStatus = try { gatt.getCompensationStatusText() } catch (_: Throwable) { "" }
                        )
                    } else {
                        // LEGACY NATIVE SENSOR LOGIC
                        val officialEndMs = Natives.getSensorEndTime(gatt.dataptr, true)
                        val expectedEndMs = Natives.getSensorEndTime(gatt.dataptr, false)
                        val startMs = Natives.getSensorStartmsec(gatt.dataptr)
                        val currentViewMode = Natives.getViewMode(gatt.dataptr)
                        var autoResetDays = Natives.getAutoResetDays(gatt.dataptr)
                        val isSi2 = Natives.isSibionics2(gatt.dataptr)
                        val isSi = Natives.isSibionics(gatt.dataptr)
                        // If 0 (Fresh), force to 21 (Default ON)
                        if (isSi2 && autoResetDays == 0) {
                            Natives.setAutoResetDays(gatt.dataptr, 21)
                            autoResetDays = 21
                        }
    
                        // Get custom calibration settings
                        val customSettings = Natives.getCustomCalibrationSettings(gatt.dataptr)
                        val customEnabled = (customSettings and 1L) != 0L
                        val customAutoReset = (customSettings and 2L) != 0L
                        val customIndex = ((customSettings ushr 8) and 0xFF).toInt()
    
                        // Get detailed status from native code
                        val nativeStatus = Natives.getsensortext(gatt.dataptr) ?: ""
                        val bleStatus = gatt.constatstatusstr ?: ""
                        
                        // Edit 85: Check `stop` (paused) state via SensorBluetooth helper.
                        // SuperGattCallback.stop is protected, not accessible from this package,
                        // so we use SensorBluetooth.isSensorPaused() as a same-package bridge.
                        // When paused (stop=true), the sensor is NOT actively receiving regardless
                        // of what the native streamingIsEnabled flag says (it lags).
                        val isPaused = SensorBluetooth.isSensorPaused(gatt)
                        val isActivelyReceiving = !isPaused && (nativeStatus.isNotEmpty() || gatt.streamingEnabled())
                        
                        fun mapBleStatus(status: String): String = when {
                            status == "Status=22" -> tk.glucodata.Applic.app.getString(tk.glucodata.R.string.status_bluetooth_off)
                            status == "Status=133" -> tk.glucodata.Applic.app.getString(tk.glucodata.R.string.status_connection_failed)
                            status.startsWith("Status=") -> status 
                            else -> status
                        }
                        
                        val finalStatus = when {
                            nativeStatus.isNotEmpty() -> nativeStatus
                            bleStatus.isNotEmpty() && (bleStatus.startsWith("Status=") || bleStatus.contains("Bluetooth off", ignoreCase = true) || bleStatus.contains("search", ignoreCase = true) || bleStatus.contains("Loss of signal", ignoreCase = true)) -> bleStatus
                            isActivelyReceiving && (bleStatus.isEmpty() || bleStatus == "Disconnected") -> tk.glucodata.Applic.app.getString(tk.glucodata.R.string.status_connected)
                            else -> tk.glucodata.Applic.app.getString(tk.glucodata.R.string.status_disconnected)
                        }
                        
                        val displayStatus = mapBleStatus(finalStatus)
                        val sensorSerial = gatt.SerialNumber ?: "Unknown"
                        val isActiveSensor = activeSensorSerial != null && sensorSerial == activeSensorSerial
    
                        SensorInfo(
                            serial = sensorSerial,
                            deviceAddress = gatt.mActiveDeviceAddress ?: "Unknown",
                            connectionStatus = if (bleStatus.startsWith("Status=")) mapBleStatus(bleStatus) else "",
                            starttime = if (startMs > 0) tk.glucodata.bluediag.datestr(startMs) else "",
                            streaming = isActivelyReceiving,
                            rssi = gatt.readrssi,
                            dataptr = gatt.dataptr,
                            officialEnd = if(officialEndMs > 0) tk.glucodata.bluediag.datestr(officialEndMs) else "",
                            expectedEnd = if(expectedEndMs > 0) tk.glucodata.bluediag.datestr(expectedEndMs) else "",
                            viewMode = currentViewMode,
                            autoResetDays = autoResetDays,
                            isSibionics = isSi,
                            isSibionics2 = isSi2,
                            isAidex = false,
                            startMs = startMs,
                            officialEndMs = officialEndMs,
                            expectedEndMs = expectedEndMs,
                            customCalEnabled = customEnabled,
                            customCalIndex = customIndex,
                            customCalAutoReset = customAutoReset,
                            detailedStatus = displayStatus,
                            isActive = isActiveSensor,
                            broadcastOnlyConnection = false
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SensorViewModel", "Error loading sensor ${gatt.SerialNumber}", e)
                    SensorInfo(
                        serial = gatt.SerialNumber ?: "Error",
                        deviceAddress = gatt.mActiveDeviceAddress ?: "Unknown",
                        connectionStatus = "Load Error",
                        starttime = "",
                        streaming = false,
                        rssi = 0,
                        dataptr = gatt.dataptr,
                        officialEnd = "",
                        expectedEnd = "",
                        viewMode = 0,
                        autoResetDays = 0,
                        isSibionics = false,
                        isSibionics2 = false,
                        isAidex = false,
                        startMs = 0L,
                        officialEndMs = 0L,
                        expectedEndMs = 0L,
                        customCalEnabled = false,
                        customCalIndex = 0,
                        customCalAutoReset = false,
                        detailedStatus = "Error: ${e.message}",
                        isActive = false,
                        broadcastOnlyConnection = false
                    )
                }
            }
            // Edit 86: Preserve natural order from mygatts() (native insertion order).
            // The main sensor is visually distinguished by its isActive styling —
            // no need to sort it to the top. Sorting on every refresh caused the
            // list to jump when the user manually switched the main sensor.
            _sensors.value = sensorList
        }
    }

    fun setAutoResetDays(serial: String, days: Int) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            Natives.setAutoResetDays(gatt.dataptr, days)
            refreshSensors()
        }
    }

    private fun clearSibionicsTransmitterBinding(gatt: SuperGattCallback, reason: String) {
        val dataptr = gatt.dataptr
        if (dataptr == 0L) return
        try {
            if (!Natives.isSibionics(dataptr)) return
            Natives.siClearTransmitterBinding(dataptr)
            try {
                gatt.setDeviceAddress(null)
            } catch (_: Throwable) {}
            android.util.Log.i("SensorVM", "Cleared Sibionics transmitter binding for ${gatt.SerialNumber} ($reason)")
        } catch (t: Throwable) {
            android.util.Log.e("SensorVM", "clearSibionicsTransmitterBinding($reason) failed: ${t.message}")
        }
    }

    private fun wipeSibionicsDataIfNeeded(gatt: SuperGattCallback, reason: String) {
        val dataptr = gatt.dataptr
        if (dataptr == 0L) return
        try {
            if (!Natives.isSibionics(dataptr)) {
                android.util.Log.w(
                    "SensorVM",
                    "wipeSibionicsDataIfNeeded($reason): ${gatt.SerialNumber} is not Sibionics, skipping siWipeDataOnly"
                )
                return
            }
            Natives.siWipeDataOnly(dataptr)
            clearSibionicsTransmitterBinding(gatt, reason)
        } catch (t: Throwable) {
            android.util.Log.e("SensorVM", "wipeSibionicsDataIfNeeded($reason) failed: ${t.message}")
        }
    }

    private fun forceDeleteSensorDirectory(serial: String) {
        if (serial.isEmpty()) {
            android.util.Log.w("SensorViewModel", "forceDeleteSensorDirectory called with empty serial")
            return
        }
        try {
            val sensorsDir = java.io.File(tk.glucodata.Applic.app.filesDir, "sensors")
            val sensorDir = java.io.File(sensorsDir, serial)
            if (sensorDir.exists()) {
                val success = sensorDir.deleteRecursively()
                android.util.Log.i("SensorViewModel", "Force deleting sensor dir $serial: $success")
            }
        } catch (e: Exception) {
            android.util.Log.e("SensorViewModel", "Failed to force delete sensor dir $serial", e)
        }
    }

    // Edit 76: Remove an AiDex sensor from SharedPrefs (reverted to Edit 56a inline implementation).
    private fun removeAiDexFromPrefs(serial: String) {
        try {
            val prefs = tk.glucodata.Applic.app.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
            val sensors = prefs.getStringSet("aidex_sensors", HashSet()) ?: return
            val updated = HashSet(sensors)
            val removed = updated.removeAll { it.startsWith("$serial|") || it == serial }
            if (removed) {
                prefs.edit().putStringSet("aidex_sensors", updated).commit()
                android.util.Log.i("SensorVM", "Edit 56a: Removed $serial from aidex_sensors prefs")
            }
        } catch (t: Throwable) {
            android.util.Log.e("SensorVM", "Edit 56a: removeAiDexFromPrefs failed: ${t.message}")
        }
    }

    // Edit 56b: When terminating/forgetting the current lastsensorname, update it to
    // the next active sensor. Otherwise Notify.java keeps calling getdataptr() on the
    // finished sensor, triggering calibration algorithm re-initialization on every
    // notification cycle (observed: 33 newAlgContext calls for finished 432T452CBZ4).
    // Multi-sensor: No longer calls forceFullSync()/clearAllTables(). Data from the
    // departed sensor stays in Room (harmless — just won't be queried on the dashboard
    // since the dashboard filters by main sensor serial). The next sensor's data is
    // already synced.
    private fun switchAwayFromSensor(serial: String) {
        try {
            val current = Natives.lastsensorname()
            if (current == serial) {
                val active = Natives.activeSensors()
                val next = active?.firstOrNull { it != serial }
                if (next != null) {
                    Natives.setcurrentsensor(next)
                    android.util.Log.i("SensorVM", "Edit 56b: Switched lastsensorname from $serial to $next")
                    // Ensure the new main sensor's data is up-to-date in Room
                    try {
                        tk.glucodata.data.HistorySync.forceFullSyncForSensor(next)
                    } catch (t2: Throwable) {
                        android.util.Log.e("SensorVM", "switchAwayFromSensor sync failed: ${t2.message}")
                    }
                } else {
                    // No other active sensor — set to empty to stop Notify from hitting getdataptr
                    Natives.setcurrentsensor("")
                    android.util.Log.i("SensorVM", "Edit 56b: Cleared lastsensorname (was $serial, no other active)")
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("SensorVM", "Edit 56b: switchAwayFromSensor failed: ${t.message}")
        }
    }

    // "Disconnect" in UI now maps to "Terminate" (finishSensor) as requested.
    // Edit 39c: Guard ALL JNI calls with dataptr != 0 check. For AiDex sensors,
    // route through forgetVendor() which handles vendor stack, BLE bond, and key cleanup
    // without touching libg.so native code (which crashes with SIGSEGV on null dataptr).
    // Edit 54a: For both AiDex and legacy, stop BLE processing BEFORE finishSensor()
    // to prevent race where incoming BLE notification resets finished=0 via processchanged().
    // Sequence: setPause(true) → disconnect() → finishSensor() → sensorEnded().
    fun terminateSensor(serial: String, wipeData: Boolean = false) {
        // Edit 56b: Switch lastsensorname away BEFORE teardown to prevent Notify.java
        // from calling getdataptr on the finished sensor during the teardown window
        switchAwayFromSensor(serial)
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            try {
                if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
                    // AiDex: never call Sibionics wipe JNI here.
                    // If wipeData=true, forceDeleteSensorDirectory() below handles local AiDex files.
                    if (wipeData) {
                        android.util.Log.i("SensorVM", "terminateSensor AiDex wipeData requested: using AiDex-local cleanup only")
                    }
                    try { gatt.forgetVendor() } catch (t: Throwable) {
                        android.util.Log.e("SensorVM", "terminateSensor AiDex forgetVendor failed: ${t.message}")
                    }
                    // Edit 54a: Set finished=1 in native so bluetoothactive() skips this sensor
                    if (gatt.dataptr != 0L) {
                        try { gatt.finishSensor() } catch (t: Throwable) {
                            android.util.Log.e("SensorVM", "terminateSensor AiDex finishSensor failed: ${t.message}")
                        }
                    }
                    try { gatt.close() } catch (t: Throwable) {
                        android.util.Log.e("SensorVM", "terminateSensor AiDex close failed: ${t.message}")
                    }
                    // Edit 56a: Remove from SharedPreferences BEFORE sensorEnded to prevent
                    // updateDevicers() from re-adding it to gattcallbacks
                    removeAiDexFromPrefs(serial)
                    SensorBluetooth.sensorEnded(serial)
                } else {
                    // Legacy sensors: native finishSensor path
                    // Edit 54a: Stop BLE processing first to prevent race
                    gatt.setPause(true)
                    gatt.disconnect()
                    if (wipeData && gatt.dataptr != 0L) {
                        wipeSibionicsDataIfNeeded(gatt, "terminate/wipe")
                    }
                    gatt.finishSensor()
                    SensorBluetooth.sensorEnded(serial)
                }
            } catch (t: Throwable) {
                android.util.Log.e("SensorViewModel", "terminateSensor($serial) crashed: ${t.message}", t)
                // Still try to clean up
                try { SensorBluetooth.sensorEnded(serial) } catch (_: Throwable) {}
            }
        }

        // Force delete AFTER stopping everything and native wipe, to ensure no recreating happens
        if (wipeData) {
            forceDeleteSensorDirectory(serial)
        }

        refreshSensors()
    }

    // Edit 54b: Also mark sensor finished in native so bluetoothactive() won't return it
    // and cause re-creation in updateDevicers(). Stop BLE processing first to prevent race.
    fun forgetSensor(serial: String) {
        // Edit 56b: Switch lastsensorname away first
        switchAwayFromSensor(serial)
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            // Edit 38e: Wrap in try/catch to prevent crashes when GATT or vendor state
            // is already torn down. The forgetVendor→stopVendor→close chain can crash if
            // called from the UI thread while native lib is mid-operation.
            try {
                // AiDex: stop vendor stack, remove BLE bond, wipe saved AES keys
                if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
                    gatt.forgetVendor()
                }
            } catch (t: Throwable) {
                android.util.Log.e("SensorViewModel", "forgetSensor($serial) forgetVendor crashed: ${t.message}", t)
            }
            // Stop BLE processing and mark finished before removing from Java lists
            try {
                gatt.setPause(true)
                gatt.disconnect()
                if (gatt.dataptr != 0L) {
                    gatt.finishSensor()
                }
            } catch (t: Throwable) {
                android.util.Log.e("SensorViewModel", "forgetSensor($serial) finishSensor crashed: ${t.message}", t)
            }
            if (gatt !is tk.glucodata.drivers.aidex.AiDexSensor) {
                clearSibionicsTransmitterBinding(gatt, "forget")
            }
            try {
                gatt.close()
            } catch (t: Throwable) {
                android.util.Log.e("SensorViewModel", "forgetSensor($serial) close crashed: ${t.message}", t)
            }
        }
        // Edit 56a: Remove from SharedPreferences BEFORE sensorEnded
        removeAiDexFromPrefs(serial)
        // Properly notify system that sensor is ended/removed from list
        try { SensorBluetooth.sensorEnded(serial) } catch (_: Throwable) {}
        // Restart scanning so the system can find new sensors
        try { SensorBluetooth.startscan() } catch (_: Throwable) {}
        refreshSensors()
    }

    fun resetSensor(serial: String, enableBiasCompensation: Boolean = false) {
         val gatts = SensorBluetooth.mygatts()
         val gatt = gatts.find { it.SerialNumber == serial }
         if (gatt != null) {
             if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
                 // Route AiDex to multi-strategy reset (runs on IO thread)
                 resetAiDexSensor(serial, enableBiasCompensation)
             } else {
                 Natives.setResetSibionics2(gatt.dataptr, true)
             }
         }
    }

    fun clearCalibration(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null && gatt.dataptr != 0L) {
            try { Natives.siClearCalibration(gatt.dataptr) } catch (_: Throwable) {}
        }
    }

    fun localReplay(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null && gatt.dataptr != 0L) {
            try { Natives.siLocalReplay(gatt.dataptr) } catch (_: Throwable) {}
            // Force chart refresh — localReplay modifies polls[] in mmap,
            // Room DB needs sync to reflect the recalibrated values.
            // Multi-sensor: sync only this sensor, not destructive clearAllTables
            try { tk.glucodata.data.HistorySync.forceFullSyncForSensor(serial) } catch (_: Throwable) {}
            refreshSensors()
        }
    }

    fun clearAll(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null && gatt.dataptr != 0L) {
            try { Natives.siClearAll(gatt.dataptr) } catch (_: Throwable) {}
        }
    }

    fun setCalibrationMode(serial: String, mode: Int) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
                gatt.viewMode = mode
            }
            Natives.setViewMode(gatt.dataptr, mode)
            refreshSensors()
        }
    }

    fun setBroadcastOnlyConnection(serial: String, enabled: Boolean) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            gatt.setBroadcastOnlyConnection(enabled)
            refreshSensors()
        }
    }

    fun updateCustomCalibration(serial: String, enabled: Boolean, index: Int, autoReset: Boolean) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            Natives.setCustomCalibrationSettings(gatt.dataptr, enabled, index, autoReset)
            refreshSensors()
        }
    }

    /**
     * Disable custom calibration and replay the algorithm in native mode.
     * Called when the master switch is turned OFF — restores standard Juggluco behavior.
     */
    fun disableCustomCalAndReplay(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null && gatt.dataptr != 0L) {
            val wasCustomEnabled = try {
                (Natives.getCustomCalibrationSettings(gatt.dataptr) and 1L) != 0L
            } catch (_: Throwable) {
                // Keep old behavior if settings read fails.
                true
            }
            // Disable custom calibration.
            Natives.setCustomCalibrationSettings(gatt.dataptr, false, 0, false)
            // Only restore/replay when we actually transition Advanced ON -> OFF.
            // This avoids a second fallback replay after a successful restore, which
            // can drift away from the true native state.
            if (wasCustomEnabled) {
                // Try to restore original polls+binState from backup (preserves manufacturer values).
                // Falls back to legacy replay if no backup exists (e.g. first install).
                val restored = try { Natives.siRestoreOriginalPolls(gatt.dataptr) } catch (_: Throwable) { false }
                if (!restored) {
                    android.util.Log.d("SensorVM", "disableCustomCal: no backup, falling back to legacy replay")
                    try { Natives.siLocalReplay(gatt.dataptr) } catch (_: Throwable) {}
                }
            } else {
                android.util.Log.d("SensorVM", "disableCustomCal: already in native mode, skipping restore/replay")
            }
            try { tk.glucodata.data.HistorySync.forceFullSyncForSensor(serial) } catch (_: Throwable) {}
            refreshSensors()
        }
    }

    // Edit 39d: AiDex-safe reconnect. For AiDex, restart vendor stack instead of
    // calling native resetbluetooth (SIGSEGV risk). For legacy sensors, use proven sequence.
    fun reconnectSensor(serial: String, wipeData: Boolean = false) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            viewModelScope.launch {
                if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
                    // AiDex reconnect must be non-destructive: keep pairing keys/local sensor state.
                    // Using forgetVendor() here wipes pairing + removes from prefs, which can leave
                    // the sensor dead until a full app reset/re-add.
                    if (wipeData) {
                        android.util.Log.w("SensorVM", "AiDex reconnect requested with wipeData=true; running safe reconnect without destructive wipe")
                    }
                    if (gatt.dataptr != 0L) {
                        try { Natives.unfinishSensor(gatt.dataptr) } catch (t: Throwable) {
                            android.util.Log.e("SensorVM", "reconnectSensor AiDex unfinishSensor: ${t.message}")
                        }
                    }
                    try { gatt.softDisconnect() } catch (t: Throwable) {
                        android.util.Log.e("SensorVM", "reconnectSensor AiDex softDisconnect: ${t.message}")
                    }
                    kotlinx.coroutines.delay(500)
                    // Reconnect: connectDevice will defer to vendor stack flow
                    gatt.connectDevice(200)
                } else {
                    if (wipeData && gatt.dataptr != 0L) {
                        wipeSibionicsDataIfNeeded(gatt, "reconnect/wipe")
                    }
                    gatt.setPause(true)
                    gatt.disconnect()
                    kotlinx.coroutines.delay(500)
                    if (gatt.dataptr != 0L) {
                        try { Natives.resetbluetooth(gatt.dataptr) } catch (_: Throwable) {}
                    }
                    gatt.setPause(false)
                    gatt.connectDevice(200)
                }
                refreshSensors()
            }
        }
    }

    fun wipeSensorData(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null && gatt.dataptr != 0L) {
            try {
                wipeSibionicsDataIfNeeded(gatt, "wipeData")
            } catch (t: Throwable) {
                android.util.Log.e("SensorVM", "wipeSensorData: ${t.message}")
            }
            // Edit 84: Only delete the sensor directory for AiDex sensors.
            // Legacy/Sibionics sensors don't use a per-sensor directory — their data
            // lives in the mmap'd native file, which siWipeDataOnly already handles.
            if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
                forceDeleteSensorDirectory(serial)
            }
            refreshSensors()
        }
    }

    // Edit 39d: AiDex-safe disconnect. For AiDex, soft-stop the vendor stack and disconnect GATT
    // WITHOUT calling forgetVendor() (which destroys bond + keys, making reconnect impossible).
    // Preserve bond + keys so the Play button can reconnect later.
    // Edit 84: Legacy sensors restored to dev-latest behavior — non-destructive disconnect
    // (setPause + disconnect + resetbluetooth). Does NOT call finishSensor/sensorEnded, so
    // the sensor stays in the list and can be reconnected with reconnectSensor().
    // The old Edit 54c code called finishSensor+sensorEnded which permanently terminated
    // the sensor — this was the "pause button disconnected Sibionics" bug.
    fun disconnectSensor(serial: String) {
        android.util.Log.d("SensorViewModel", "disconnectSensor called for: $serial")
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
                    // AiDex disconnect must stay non-destructive so Play can reconnect.
                    android.util.Log.d("SensorViewModel", "AiDex disconnect: soft-stopping vendor + GATT (non-destructive)")
                    try { gatt.softDisconnect() } catch (t: Throwable) {
                        android.util.Log.e("SensorVM", "disconnectSensor AiDex softDisconnect: ${t.message}")
                    }
                } else {
                    // Edit 84: Legacy sensors — non-destructive disconnect (dev-latest behavior).
                    // Does NOT call finishSensor/sensorEnded — sensor stays in the list.
                    // Use reconnectSensor() to bring it back.
                    android.util.Log.d("SensorViewModel", "Legacy disconnect: setPause + disconnect + resetbluetooth (non-destructive)")
                    gatt.setPause(true)
                    gatt.disconnect()
                    kotlinx.coroutines.delay(500)
                    if (gatt.dataptr != 0L) {
                        try { Natives.resetbluetooth(gatt.dataptr) } catch (_: Throwable) {}
                    }
                    // DON'T call finishSensor/sensorEnded — just refresh to show disconnected state
                }
                refreshSensors()
            }
        } else {
            android.util.Log.d("SensorViewModel", "Gatt not found for serial: $serial")
        }
    }

    fun sendAiDexMaintenanceCommand(serial: String, opCode: Int) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val success = gatt.sendMaintenanceCommand(opCode)
                if (success) {
                    refreshSensors()
                }
            }
        }
    }

    /**
     * Multi-strategy AiDex sensor reset: vendor native lib -> FF32 direct write -> BLE bond removal.
     * Must run on IO dispatcher (uses Thread.sleep internally).
     */
    fun resetAiDexSensor(serial: String, enableBiasCompensation: Boolean = false) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Edit 59d: Enable/disable bias compensation before reset
                if (enableBiasCompensation) {
                    gatt.enableResetCompensation()
                } else {
                    gatt.disableResetCompensation()
                }
                val success = gatt.resetSensor()
                android.util.Log.i("SensorVM", "AiDex resetSensor result: $success, biasCompensation=$enableBiasCompensation")
                refreshSensors()
            }
        }
    }

    /**
     * Disable AiDex initialization bias compensation for a sensor.
     */
    fun disableAiDexBiasCompensation(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            gatt.disableResetCompensation()
            viewModelScope.launch { refreshSensors() }
        }
    }

    /**
     * Edit 77: Manually enable AiDex initialization bias compensation for a sensor.
     * Use case: app was reinstalled while sensor was already in its post-reset compensation
     * window, so the persisted compensation state was lost. This lets the user re-enable it.
     */
    fun enableAiDexBiasCompensation(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            gatt.enableResetCompensation()
            viewModelScope.launch { refreshSensors() }
        }
    }

    /**
     * Multi-strategy AiDex start new sensor: vendor native lib -> FF32 direct write -> full reset fallback.
     * Must run on IO dispatcher (uses Thread.sleep internally).
     */
    fun startNewAiDexSensor(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val success = gatt.startNewSensor()
                android.util.Log.i("SensorVM", "AiDex startNewSensor result: $success")
                refreshSensors()
            }
        }
    }

    /**
     * Send a manual calibration (finger-stick blood glucose) to the AiDex sensor.
     * @param glucoseMgDl glucose in mg/dL (integer)
     */
    fun calibrateAiDexSensor(serial: String, glucoseMgDl: Int) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val success = gatt.calibrateSensor(glucoseMgDl)
                android.util.Log.i("SensorVM", "AiDex calibrateSensor($glucoseMgDl mg/dL) result: $success")
            }
        }
    }

    /**
     * Unpair from the AiDex sensor: delete bond on sensor side, clear saved keys.
     */
    fun unpairAiDexSensor(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val success = gatt.unpairSensor()
                android.util.Log.i("SensorVM", "AiDex unpairSensor result: $success")
                refreshSensors()
            }
        }
    }

    /**
     * Re-pair with the AiDex sensor: clear keys and restart vendor stack for fresh pairing.
     */
    fun rePairAiDexSensor(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt is tk.glucodata.drivers.aidex.AiDexSensor) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                gatt.rePairSensor()
                android.util.Log.i("SensorVM", "AiDex rePairSensor initiated")
                refreshSensors()
            }
        }
    }
}
