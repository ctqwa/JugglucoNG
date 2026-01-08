package tk.glucodata.ui.viewmodel

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
    val startMs: Long,
    val officialEndMs: Long,
    val customCalEnabled: Boolean,
    val customCalIndex: Int,
    val customCalAutoReset: Boolean,
    val detailedStatus: String = ""
)

class SensorViewModel : ViewModel() {
    private val _sensors = MutableStateFlow<List<SensorInfo>>(emptyList())
    val sensors = _sensors.asStateFlow()

    // Polling job - only active when screen is visible
    private var pollingJob: Job? = null

    init {
        // Initial refresh only - no constant polling from init!
        refreshSensors()
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
                refreshSensors()
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

    fun refreshSensors() {
        viewModelScope.launch {
            SensorBluetooth.updateDevices()
            val gatts = SensorBluetooth.mygatts() ?: ArrayList()
            val sensorList = gatts.map { gatt ->
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

                // Get detailed status from native code which has accurate logic
                val nativeStatus = Natives.getsensortext(gatt.dataptr) ?: ""
                val bleStatus = gatt.constatstatusstr ?: ""
                
                // Calculate streaming status first - needed for status resolution
                // Consider "receiving" or "Receiving" in status as streaming
                val isActivelyReceiving = nativeStatus.contains("Receiving", ignoreCase = true) ||
                    nativeStatus.contains("eceiv", ignoreCase = true) ||
                    gatt.streamingEnabled()
                
                
                // Map common BLE status codes to user-friendly messages
                fun mapBleStatus(status: String): String = when {
                    status == "Status=22" -> "Bluetooth off"
                    status == "Status=133" -> "Connection failed"
                    status.startsWith("Status=") -> status // Keep other Status= codes as-is
                    else -> status
                }
                
                // Determine final display status with smart priority
                val finalStatus = when {
                    // Priority 1: Meaningful native sensor states (errors, warming up, receiving history)
                    nativeStatus.isNotEmpty() && 
                    (nativeStatus.contains("Error", ignoreCase = true) ||
                     nativeStatus.contains("Ended", ignoreCase = true) ||
                     nativeStatus.contains("Warming", ignoreCase = true) ||
                     nativeStatus.contains("Receiving", ignoreCase = true)) -> nativeStatus
                    
                    // Priority 2: ANY BLE status issues (Status=X codes, BT off, searching, loss of signal)
                    // These indicate real connection problems that should always show
                    bleStatus.isNotEmpty() &&
                    (bleStatus.startsWith("Status=") ||
                     bleStatus.contains("Bluetooth off", ignoreCase = true) ||
                     bleStatus.contains("search", ignoreCase = true) ||
                     bleStatus.contains("Loss of signal", ignoreCase = true)) -> bleStatus
                    
                    // Priority 3: Show "Connected" when actively receiving AND no BLE problems
                    // Only trust streaming flag if BLE status is normal (empty or just "Disconnected")
                    isActivelyReceiving && 
                    (bleStatus.isEmpty() || bleStatus == "Disconnected") -> "Connected"
                    
                    // Fallback: Show disconnected
                    else -> "Disconnected"
                }
                
                // Apply user-friendly mapping to final status
                val displayStatus = mapBleStatus(finalStatus)


                SensorInfo(
                    serial = gatt.SerialNumber ?: "Unknown",
                    deviceAddress = gatt.mActiveDeviceAddress ?: "Unknown",
                    // connectionStatus: Only show REAL BLE status codes (Status=X), not app messages
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
                    startMs = startMs,
                    officialEndMs = officialEndMs,
                    customCalEnabled = customEnabled,
                    customCalIndex = customIndex,
                    customCalAutoReset = customAutoReset,
                    detailedStatus = displayStatus
                )


            }
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

    // "Disconnect" in UI now maps to "Terminate" (finishSensor) as requested.
    fun terminateSensor(serial: String, wipeData: Boolean = false) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            if (wipeData) {
                Natives.siWipeDataOnly(gatt.dataptr)
            }
            gatt.finishSensor()
            SensorBluetooth.sensorEnded(serial)
            refreshSensors()
        }
    }

    fun forgetSensor(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            gatt.searchforDeviceAddress()
            gatt.close()
            SensorBluetooth.startscan()
            refreshSensors()
        }
    }

    fun resetSensor(serial: String) {
         val gatts = SensorBluetooth.mygatts()
         val gatt = gatts.find { it.SerialNumber == serial }
         if (gatt != null) {
             Natives.setResetSibionics2(gatt.dataptr, true)
         }
    }

    fun clearCalibration(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            Natives.siClearCalibration(gatt.dataptr)
        }
    }

    fun clearAll(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            Natives.siClearAll(gatt.dataptr)
        }
    }

    fun setCalibrationMode(serial: String, mode: Int) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            Natives.setViewMode(gatt.dataptr, mode)
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

    fun reconnectSensor(serial: String, wipeData: Boolean = false) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            viewModelScope.launch {
                if (wipeData) {
                    Natives.siWipeDataOnly(gatt.dataptr)
                }
                gatt.setPause(true)
                gatt.disconnect()
                kotlinx.coroutines.delay(2000) // Ensure full disconnect
                Natives.resetbluetooth(gatt.dataptr)
                gatt.setPause(false)
                gatt.connectDevice(200)
                refreshSensors()
            }
        }
    }

    fun wipeSensorData(serial: String) {
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            Natives.siWipeDataOnly(gatt.dataptr)
            refreshSensors()
        }
    }

    fun disconnectSensor(serial: String) {
        android.util.Log.d("SensorViewModel", "disconnectSensor called for: $serial")
        val gatts = SensorBluetooth.mygatts()
        val gatt = gatts.find { it.SerialNumber == serial }
        if (gatt != null) {
            viewModelScope.launch {
                android.util.Log.d("SensorViewModel", "Found gatt, using reconnect's proven sequence")
                gatt.setPause(true)
                gatt.disconnect()
                kotlinx.coroutines.delay(2000) // Ensure full disconnect
                Natives.resetbluetooth(gatt.dataptr)
                // DON'T reconnect - just refresh to show disconnected state
                refreshSensors()
            }
        } else {
            android.util.Log.d("SensorViewModel", "Gatt not found for serial: $serial")
        }
    }
}