package tk.glucodata.drivers

import android.content.Context
import tk.glucodata.SuperGattCallback

/**
 * Vendor-specific identity adapter for managed BLE sensors.
 *
 * Shared code should not know vendor id formats, provisional ids, or native aliases.
 * It should ask these adapters instead.
 */
interface ManagedSensorIdentityAdapter {

    fun matchesCallbackId(callbackId: String?, sensorId: String): Boolean = false

    fun resolveCanonicalSensorId(sensorId: String?): String? = null

    fun resolveNativeSensorName(sensorId: String?): String? = null

    fun hasPersistedManagedRecord(sensorId: String?): Boolean = false

    fun persistedSensorIds(context: Context): List<String> = emptyList()

    fun createManagedCallback(context: Context, sensorId: String, dataptr: Long): SuperGattCallback? = null

    fun removePersistedSensor(context: Context, sensorId: String?) {}

    fun resolveNativeHistorySensorNames(sensorId: String?): List<String> = emptyList()

    fun isExternallyManagedBleSensor(sensorId: String?): Boolean = false

    fun shouldUseNativeHistorySync(sensorId: String?): Boolean? = null
}
