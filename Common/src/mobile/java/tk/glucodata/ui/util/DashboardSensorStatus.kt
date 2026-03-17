package tk.glucodata.ui.util

import tk.glucodata.SensorBluetooth

private fun getDriverDashboardStatus(serial: String): String? {
    return try {
        (SensorBluetooth.mygatts().firstOrNull { it.SerialNumber == serial } as? tk.glucodata.drivers.aidex.AiDexDriver)
            ?.getDetailedBleStatus()
            ?.takeIf {
                it.isNotBlank() &&
                    !it.equals("Connected", ignoreCase = true) &&
                    !it.equals("Disconnected", ignoreCase = true) &&
                    !it.equals("Receiving", ignoreCase = true) &&
                    !it.equals("Broadcast", ignoreCase = true) &&
                    !it.equals("Broadcast Mode", ignoreCase = true)
            }
    } catch (_: Throwable) {
        null
    }
}

fun resolveDashboardSensorStatus(
    serial: String,
    sensorKind: Int,
    startMsec: Long,
    nativeStatus: String
): String {
    val warmupStatus = getLegacyWarmupStatus(tk.glucodata.Applic.app, sensorKind, startMsec, nativeStatus)
    return warmupStatus ?: nativeStatus.ifBlank { getDriverDashboardStatus(serial).orEmpty() }
}

fun resolveDashboardSensorStatus(serial: String, nativeStatus: String): String {
    return nativeStatus.ifBlank { getDriverDashboardStatus(serial).orEmpty() }
}
