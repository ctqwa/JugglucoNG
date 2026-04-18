package tk.glucodata.drivers

import tk.glucodata.SensorBluetooth
import tk.glucodata.SensorIdentity

object ManagedSensorRuntime {
    @JvmStatic
    fun resolveDriver(sensorId: String?): ManagedBluetoothSensorDriver? {
        val resolvedSensorId = SensorIdentity.resolveAppSensorId(sensorId) ?: sensorId
        if (resolvedSensorId.isNullOrBlank()) {
            return null
        }
        return SensorBluetooth.mygatts()
            .asSequence()
            .mapNotNull { it as? ManagedBluetoothSensorDriver }
            .firstOrNull { driver ->
                val snapshot = runCatching { driver.getManagedUiSnapshot(resolvedSensorId) }.getOrNull()
                when {
                    snapshot != null -> SensorIdentity.matches(snapshot.serial, resolvedSensorId)
                    else -> runCatching { driver.matchesManagedSensorId(resolvedSensorId) }.getOrDefault(false)
                }
            }
    }

    @JvmStatic
    fun resolveUiSnapshot(sensorId: String?, activeSensorId: String? = null): ManagedSensorUiSnapshot? {
        val resolvedSensorId = SensorIdentity.resolveAppSensorId(sensorId) ?: sensorId
        if (resolvedSensorId.isNullOrBlank()) {
            return null
        }
        return SensorBluetooth.mygatts()
            .asSequence()
            .mapNotNull { it as? ManagedBluetoothSensorDriver }
            .mapNotNull { driver ->
                runCatching { driver.getManagedUiSnapshot(activeSensorId ?: resolvedSensorId) }.getOrNull()
            }
            .firstOrNull { snapshot -> SensorIdentity.matches(snapshot.serial, resolvedSensorId) }
    }

    @JvmStatic
    fun resolveCurrentSnapshot(sensorId: String?, maxAgeMillis: Long): ManagedSensorCurrentSnapshot? {
        val resolvedSensorId = SensorIdentity.resolveAppSensorId(sensorId) ?: sensorId
        if (resolvedSensorId.isNullOrBlank()) {
            return null
        }
        return resolveDriver(resolvedSensorId)
            ?.let { driver -> runCatching { driver.getManagedCurrentSnapshot(maxAgeMillis) }.getOrNull() }
    }
}
