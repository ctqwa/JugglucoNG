package tk.glucodata.drivers

import android.content.Context
import tk.glucodata.SensorIdentity
import tk.glucodata.SuperGattCallback
import tk.glucodata.drivers.aidex.AiDexManagedSensorIdentityAdapter
import tk.glucodata.drivers.icanhealth.ICanHealthManagedSensorIdentityAdapter

object ManagedSensorIdentityRegistry {
    val all: List<ManagedSensorIdentityAdapter> = listOf(
        AiDexManagedSensorIdentityAdapter,
        ICanHealthManagedSensorIdentityAdapter,
    )

    fun persistedSensorIds(context: Context): List<String> =
        all.asSequence()
            .flatMap { it.persistedSensorIds(context).asSequence() }
            .distinct()
            .toList()

    fun createManagedCallback(context: Context, sensorId: String, dataptr: Long): SuperGattCallback? =
        all.asSequence()
            .mapNotNull { it.createManagedCallback(context, sensorId, dataptr) }
            .firstOrNull()

    fun resolveManagedCallbackDataptr(sensorId: String?): Long? =
        all.asSequence()
            .mapNotNull { it.resolveCallbackDataptr(sensorId) }
            .firstOrNull()

    fun resolveManagedNativeSensorName(sensorId: String?): String? =
        all.asSequence()
            .mapNotNull { it.resolveNativeSensorName(sensorId) }
            .firstOrNull { it.isNotBlank() }

    fun isExternallyManagedBleSensor(sensorId: String?): Boolean =
        all.any { it.isExternallyManagedBleSensor(sensorId) }

    fun shouldUseNativeHistorySync(sensorId: String?): Boolean? =
        all.asSequence()
            .mapNotNull { it.shouldUseNativeHistorySync(sensorId) }
            .firstOrNull()

    fun removePersistedSensor(context: Context, sensorId: String?) {
        all.forEach { it.removePersistedSensor(context, sensorId) }
        SensorIdentity.invalidateCaches()
    }
}
