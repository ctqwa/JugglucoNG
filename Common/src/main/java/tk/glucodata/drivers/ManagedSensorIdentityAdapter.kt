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

    /**
     * Optional driver-owned callback dataptr resolution.
     *
     * Return null to let shared code use its legacy native-stream lookup.
     * Return 0 when the managed callback should be restored without a native
     * stream dataptr and the driver owns any native-shell interaction itself.
     */
    fun resolveCallbackDataptr(sensorId: String?): Long? = null

    fun hasPersistedManagedRecord(sensorId: String?): Boolean = false

    fun persistedSensorIds(context: Context): List<String> = emptyList()

    fun createManagedCallback(context: Context, sensorId: String, dataptr: Long): SuperGattCallback? = null

    fun removePersistedSensor(context: Context, sensorId: String?) {}

    fun resolveNativeHistorySensorNames(sensorId: String?): List<String> = emptyList()

    fun isExternallyManagedBleSensor(sensorId: String?): Boolean = false

    fun shouldUseNativeHistorySync(sensorId: String?): Boolean? = null
}
