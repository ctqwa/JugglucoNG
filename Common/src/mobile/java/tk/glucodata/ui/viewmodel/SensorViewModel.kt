package tk.glucodata.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isSibionics2: Boolean,
    val startMs: Long,
    val officialEndMs: Long,
    val customCalEnabled: Boolean,
    val customCalIndex: Int,
    val customCalAutoReset: Boolean
)

class SensorViewModel : ViewModel() {
    private val _sensors = MutableStateFlow<List<SensorInfo>>(emptyList())
    val sensors = _sensors.asStateFlow()

    init {
        refreshSensors()
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

                SensorInfo(
                    serial = gatt.SerialNumber ?: "Unknown",
                    deviceAddress = gatt.mActiveDeviceAddress ?: "Unknown",
                    connectionStatus = gatt.constatstatusstr ?: "Disconnected",
                    starttime = if (startMs > 0) tk.glucodata.bluediag.datestr(startMs) else "",
                    streaming = gatt.streamingEnabled(),
                    rssi = gatt.readrssi,
                    dataptr = gatt.dataptr,
                    officialEnd = if(officialEndMs > 0) tk.glucodata.bluediag.datestr(officialEndMs) else "",
                    expectedEnd = if(expectedEndMs > 0) tk.glucodata.bluediag.datestr(expectedEndMs) else "",
                    viewMode = currentViewMode,
                    autoResetDays = autoResetDays,
                    isSibionics2 = isSi2,
                    startMs = startMs,
                    officialEndMs = officialEndMs,
                    customCalEnabled = customEnabled,
                    customCalIndex = customIndex,
                    customCalAutoReset = customAutoReset
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
                gatt.disconnect()
                kotlinx.coroutines.delay(2000) // Ensure full disconnect
                Natives.resetbluetooth(gatt.dataptr)
                gatt.connectDevice(200)
                refreshSensors()
            }
        }
    }
}

//fun reconnectrestartSensor(serial: String, wipeData: Boolean = false, context: Context) {
//    val gatts = SensorBluetooth.mygatts()
//    val gatt = gatts.find { it.SerialNumber == serial }
//    if (gatt != null) {
//        if (wipeData) {
//            Natives.siWipeDataOnly(gatt.dataptr)
//        }
//        Natives.resetbluetooth(gatt.dataptr)
//        gatt.disconnect()
//        gatt.connectDevice(100)
//        refreshSensors()
//
//        // --- Restart the app ---
//        val pm = context.packageManager
//        val intent = pm.getLaunchIntentForPackage(context.packageName)
//        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
//
//        context.startActivity(intent)
//
//        // Needed for a real full restart
//        exitProcess(0)
//    }
//}