package tk.glucodata.drivers.icanhealth

import android.content.Context
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.SensorBluetooth
import tk.glucodata.SuperGattCallback
import tk.glucodata.drivers.ManagedBluetoothSensorDriver
import tk.glucodata.drivers.ManagedSensorIdentityAdapter

object ICanHealthManagedSensorIdentityAdapter : ManagedSensorIdentityAdapter {

    override fun matchesCallbackId(callbackId: String?, sensorId: String): Boolean =
        run {
            val normalizedCallbackId = callbackId?.trim().takeIf { !it.isNullOrEmpty() } ?: return@run false
            if (normalizedCallbackId.equals(sensorId, ignoreCase = true) ||
                ICanHealthConstants.matchesCanonicalOrKnownNativeAlias(normalizedCallbackId, sensorId)
            ) {
                return@run true
            }
            SensorBluetooth.mygatts().any { callback ->
                val managed = callback as? ManagedBluetoothSensorDriver ?: return@any false
                managed.matchesManagedSensorId(normalizedCallbackId) &&
                    managed.matchesManagedSensorId(sensorId)
            }
        }

    override fun resolveCanonicalSensorId(sensorId: String?): String? {
        val raw = sensorId?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
        SensorBluetooth.mygatts()
            .firstOrNull { callback ->
                val managed = callback as? ManagedBluetoothSensorDriver ?: return@firstOrNull false
                managed.matchesManagedSensorId(raw)
            }
            ?.SerialNumber
            ?.takeIf { it.isNotBlank() }
            ?.let(ICanHealthConstants::canonicalSensorId)
            ?.takeIf { it.isNotEmpty() && !ICanHealthConstants.isProvisionalSensorId(it) }
            ?.let { return it }

        if (ICanHealthConstants.isProvisionalSensorId(raw)) {
            return raw
        }
        val normalized = ICanHealthConstants.canonicalSensorId(raw)
        if (normalized.isEmpty()) {
            return null
        }
        if (ICanHealthConstants.nativeShortSensorAlias(normalized) != null) {
            return normalized
        }

        SensorBluetooth.mygatts()
            .firstOrNull { callback ->
                val callbackId = callback.SerialNumber ?: return@firstOrNull false
                (callback as? ManagedBluetoothSensorDriver)?.matchesManagedSensorId(normalized) == true ||
                    ICanHealthConstants.matchesCanonicalOrKnownNativeAlias(callbackId, normalized)
            }
            ?.SerialNumber
            ?.takeIf { it.isNotBlank() }
            ?.let(ICanHealthConstants::canonicalSensorId)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        runCatching { ICanHealthRegistry.resolveCanonicalSensorId(Applic.app, normalized) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        runCatching { Natives.resolveFullSensorName(normalized) }
            .getOrNull()
            ?.trim()
            ?.takeIf { ICanHealthConstants.isLikelyPersistedSensorName(it) }
            ?.let(ICanHealthConstants::canonicalSensorId)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return null
    }

    override fun resolveNativeSensorName(sensorId: String?): String? {
        val raw = sensorId?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
        val canonical = resolveCanonicalSensorId(raw) ?: raw
        val record = ICanHealthRegistry.findRecord(Applic.app, canonical)

        if (record != null) {
            Natives.activeSensors()
                ?.firstOrNull { record.matchesId(it) }
                ?.takeIf { !it.isNullOrBlank() }
                ?.let { return it }

            val driver = SensorBluetooth.mygatts()
                .firstOrNull { callback ->
                    val callbackId = callback.SerialNumber ?: return@firstOrNull false
                    (callback as? ManagedBluetoothSensorDriver)?.matchesManagedSensorId(canonical) == true ||
                        ICanHealthConstants.matchesCanonicalOrKnownNativeAlias(callbackId, canonical)
                } as? ManagedBluetoothSensorDriver

            if (driver?.hasNativeSensorBacking() == true) {
                runCatching { Natives.lastsensorname() }
                    .getOrNull()
                    ?.takeIf { record.matchesId(it) }
                    ?.let { return it }
            }
        }

        runCatching { Natives.resolveFullSensorName(canonical) }
            .getOrNull()
            ?.trim()
            ?.takeIf { ICanHealthConstants.matchesCanonicalOrKnownNativeAlias(it, canonical) }
            ?.let { return it }

        return canonical.takeIf { ICanHealthConstants.isLikelyPersistedSensorName(it) }
    }

    override fun hasPersistedManagedRecord(sensorId: String?): Boolean {
        val normalized = sensorId?.trim().takeIf { !it.isNullOrEmpty() } ?: return false
        return ICanHealthRegistry.findRecord(Applic.app, normalized) != null
    }

    override fun resolveCallbackDataptr(sensorId: String?): Long? =
        resolveCanonicalSensorId(sensorId)?.let { 0L }

    override fun persistedSensorIds(context: Context): List<String> =
        ICanHealthRegistry.persistedRecords(context).map { it.sensorId }

    override fun createManagedCallback(context: Context, sensorId: String, dataptr: Long): SuperGattCallback? {
        ICanHealthRegistry.createRestoredCallback(context, sensorId, dataptr)?.let { return it }

        val canonical = resolveCanonicalSensorId(sensorId)
            ?.takeIf { it.isNotBlank() && !ICanHealthConstants.isProvisionalSensorId(it) }
            ?: return null
        return ICanHealthBleManager(canonical, dataptr)
    }

    override fun removePersistedSensor(context: Context, sensorId: String?) {
        ICanHealthRegistry.removeSensor(context, sensorId)
    }

    override fun isExternallyManagedBleSensor(sensorId: String?): Boolean =
        resolveCanonicalSensorId(sensorId) != null

    override fun usesNativeDirectStreamShell(sensorId: String?): Boolean =
        resolveCanonicalSensorId(sensorId) != null

    override fun hasNativeSensorBacking(sensorId: String?): Boolean? {
        val canonical = resolveCanonicalSensorId(sensorId) ?: return null
        val record = ICanHealthRegistry.findRecord(Applic.app, canonical) ?: return null
        val driver = SensorBluetooth.mygatts()
            .asSequence()
            .mapNotNull { it as? ManagedBluetoothSensorDriver }
            .firstOrNull { it.matchesManagedSensorId(record.sensorId) }
        return driver?.hasNativeSensorBacking() ?: true
    }

    override fun shouldUseNativeHistorySync(sensorId: String?): Boolean? {
        val canonical = resolveCanonicalSensorId(sensorId) ?: return null
        val hasPersistedRecord = ICanHealthRegistry.findRecord(Applic.app, canonical) != null
        return !hasPersistedRecord
    }
}
