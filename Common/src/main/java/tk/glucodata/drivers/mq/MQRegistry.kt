// MQRegistry.kt — SharedPreferences-backed persistence for MQ/Glutec sensors.
//
// Each record is a "|"-joined triplet (sensorId | address | displayName).
// Per-sensor config (protocol type, deviation, K/B/multiplier, warmup start...)
// lives under separate keyed prefixes so the schema can evolve without
// rewriting the SensorRecord set.

package tk.glucodata.drivers.mq

import android.content.Context
import android.content.SharedPreferences
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.SensorBluetooth
import tk.glucodata.SensorIdentity
import tk.glucodata.SuperGattCallback
import tk.glucodata.drivers.ManagedBluetoothSensorDriver

object MQRegistry {
    private const val TAG = MQConstants.TAG
    private const val PREFS_NAME = "tk.glucodata_preferences"

    data class SensorRecord(
        val sensorId: String,
        val address: String,
        val displayName: String,
    ) {
        fun matchesId(id: String?): Boolean =
            MQConstants.matchesCanonicalOrKnownNativeAlias(sensorId, id)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private inline fun editBlocking(
        context: Context,
        block: SharedPreferences.Editor.() -> Unit,
    ) {
        val editor = prefs(context).edit()
        editor.block()
        editor.commit()
    }

    private fun parseRecord(entry: String?): SensorRecord? {
        if (entry.isNullOrBlank()) return null
        val parts = entry.split('|')
        if (parts.isEmpty()) return null
        val sensorId = MQConstants.canonicalSensorId(parts[0])
        if (sensorId.isEmpty()) return null
        val address = parts.getOrNull(1)?.trim().orEmpty()
        val displayName = parts.getOrNull(2)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: MQConstants.DEFAULT_DISPLAY_NAME
        return SensorRecord(sensorId, address, displayName)
    }

    private fun encode(r: SensorRecord): String =
        "${r.sensorId}|${r.address}|${r.displayName}"

    private fun readAll(context: Context): LinkedHashSet<SensorRecord> {
        val stored = try {
            prefs(context).getStringSet(MQConstants.PREF_SENSORS_KEY, emptySet())
        } catch (t: Throwable) {
            Log.stack(TAG, "readAll", t); emptySet()
        }.orEmpty()
        val out = LinkedHashSet<SensorRecord>()
        for (entry in stored) parseRecord(entry)?.let(out::add)
        return out
    }

    private fun writeAll(context: Context, records: Collection<SensorRecord>) {
        val encoded = LinkedHashSet<String>()
        records.forEach { encoded.add(encode(it)) }
        prefs(context).edit()
            .putStringSet(MQConstants.PREF_SENSORS_KEY, encoded)
            .commit()
        SensorIdentity.invalidateCaches()
    }

    @JvmStatic
    fun findRecord(context: Context?, sensorId: String?): SensorRecord? {
        if (context == null || sensorId.isNullOrBlank()) return null
        return readAll(context).firstOrNull { it.matchesId(sensorId) }
    }

    @JvmStatic
    fun resolveCanonicalSensorId(context: Context?, sensorId: String?): String? =
        findRecord(context, sensorId)?.sensorId

    @JvmStatic
    fun persistedRecords(context: Context?): List<SensorRecord> {
        if (context == null) return emptyList()
        return readAll(context).toList()
    }

    @JvmStatic
    fun createRestoredCallback(context: Context?, sensorId: String, dataptr: Long): SuperGattCallback? {
        if (context == null) return null
        val record = findRecord(context, sensorId) ?: return null
        val cb = MQBleManager(record.sensorId, dataptr)
        cb.mActiveDeviceAddress = record.address.takeIf { it.isNotBlank() }
        cb.restoreFromPersistence(context)
        return cb
    }

    @JvmStatic
    fun addSensor(
        context: Context,
        displayName: String?,
        address: String?,
        qrCodeContent: String? = null,
        connectNow: Boolean = true,
        bootstrapConfig: MQBootstrapConfig? = null,
    ): String? {
        val normalizedAddress = address?.trim().orEmpty()
        val normalizedQr = qrCodeContent?.trim().orEmpty()
        if (normalizedAddress.isEmpty() && normalizedQr.isEmpty()) {
            Log.w(TAG, "addSensor: missing address and QR code")
            return null
        }
        val safeName = displayName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: MQConstants.DEFAULT_DISPLAY_NAME
        val sensorId = MQConstants.deriveInitialSensorId(safeName, normalizedAddress, normalizedQr)

        val updated = LinkedHashSet<SensorRecord>()
        readAll(context).forEach { existing ->
            val sameAddress = normalizedAddress.isNotEmpty() &&
                normalizedAddress.equals(existing.address, ignoreCase = true)
            if (existing.sensorId != sensorId && !sameAddress) updated.add(existing)
        }
        updated.add(SensorRecord(sensorId, normalizedAddress, safeName))
        writeAll(context, updated)

        val editor = prefs(context).edit()
        if (normalizedQr.isNotEmpty()) {
            editor.putString("${MQConstants.PREF_QR_CONTENT_PREFIX}$sensorId", normalizedQr)
        }
        editor.commit()

        if (bootstrapConfig != null) {
            applyBootstrapConfig(context, sensorId, bootstrapConfig)
        }

        if (connectNow) {
            connectSensor(context, sensorId)
        }
        return sensorId
    }

    @JvmStatic
    fun connectSensor(context: Context, sensorId: String) {
        val blue = SensorBluetooth.blueone ?: return
        val record = findRecord(context, sensorId) ?: return
        val existing = SensorBluetooth.gattcallbacks.firstOrNull { cb ->
            SensorIdentity.matches(cb.SerialNumber, sensorId) ||
                ((cb as? ManagedBluetoothSensorDriver)?.matchesManagedSensorId(sensorId) == true)
        }
        val callback = (existing as? MQBleManager) ?: MQBleManager(record.sensorId, 0L).also {
            SensorBluetooth.gattcallbacks.add(it)
            Natives.setmaxsensors(SensorBluetooth.gattcallbacks.size)
        }
        callback.mActiveDeviceAddress = record.address.takeIf { it.isNotBlank() }
        callback.restoreFromPersistence(context)
        if (SensorBluetooth.blueone === blue) {
            callback.connectDevice(0)
        }
    }

    @JvmStatic
    fun removeSensor(context: Context?, sensorId: String?) {
        if (context == null || sensorId.isNullOrBlank()) return
        val record = findRecord(context, sensorId)
        val canonical = record?.sensorId ?: MQConstants.canonicalSensorId(sensorId)
        val updated = LinkedHashSet<SensorRecord>()
        readAll(context).forEach { existing ->
            if (!existing.matchesId(sensorId) && !existing.matchesId(canonical)) updated.add(existing)
        }
        writeAll(context, updated)
        if (canonical.isBlank()) return
        prefs(context).edit()
            .remove("${MQConstants.PREF_PROTOCOL_TYPE_PREFIX}$canonical")
            .remove("${MQConstants.PREF_DEVIATION_PREFIX}$canonical")
            .remove("${MQConstants.PREF_TRANSMITTER10_PREFIX}$canonical")
            .remove("${MQConstants.PREF_WARMUP_STARTED_AT_PREFIX}$canonical")
            .remove("${MQConstants.PREF_SENSOR_START_AT_PREFIX}$canonical")
            .remove("${MQConstants.PREF_K_VALUE_PREFIX}$canonical")
            .remove("${MQConstants.PREF_B_VALUE_PREFIX}$canonical")
            .remove("${MQConstants.PREF_SENSITIVITY_PREFIX}$canonical")
            .remove("${MQConstants.PREF_ALGORITHM_VERSION_PREFIX}$canonical")
            .remove("${MQConstants.PREF_MULTIPLIER_PREFIX}$canonical")
            .remove("${MQConstants.PREF_PACKAGES_PREFIX}$canonical")
            .remove("${MQConstants.PREF_LAST_PROCESSED_PREFIX}$canonical")
            .remove("${MQConstants.PREF_LAST_PACKET_INDEX_PREFIX}$canonical")
            .remove("${MQConstants.PREF_SNAPSHOT_ID_PREFIX}$canonical")
            .remove("${MQConstants.PREF_LOCAL_RESET_PENDING_PREFIX}$canonical")
            .remove("${MQConstants.PREF_QR_CONTENT_PREFIX}$canonical")
            .commit()
    }

    // ---- Per-sensor config accessors (used by MQBleManager) ----

    @JvmStatic
    fun loadProtocolType(context: Context, sensorId: String): Int =
        prefs(context).getInt("${MQConstants.PREF_PROTOCOL_TYPE_PREFIX}$sensorId",
            MQConstants.SERVER_DEFAULT_PROTOCOL_TYPE)

    @JvmStatic
    fun saveProtocolType(context: Context, sensorId: String, value: Int) {
        editBlocking(context) {
            putInt("${MQConstants.PREF_PROTOCOL_TYPE_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadDeviation(context: Context, sensorId: String): Int =
        prefs(context).getInt("${MQConstants.PREF_DEVIATION_PREFIX}$sensorId",
            MQConstants.SERVER_DEFAULT_DEVIATION)

    @JvmStatic
    fun saveDeviation(context: Context, sensorId: String, value: Int) {
        editBlocking(context) {
            putInt("${MQConstants.PREF_DEVIATION_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadTransmitter10(context: Context, sensorId: String): Int =
        prefs(context).getInt("${MQConstants.PREF_TRANSMITTER10_PREFIX}$sensorId",
            MQConstants.SERVER_DEFAULT_TRANSMITTER10)

    @JvmStatic
    fun saveTransmitter10(context: Context, sensorId: String, value: Int) {
        editBlocking(context) {
            putInt("${MQConstants.PREF_TRANSMITTER10_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadWarmupStartedAt(context: Context, sensorId: String): Long =
        prefs(context).getLong("${MQConstants.PREF_WARMUP_STARTED_AT_PREFIX}$sensorId", 0L)

    @JvmStatic
    fun saveWarmupStartedAt(context: Context, sensorId: String, timestamp: Long) {
        editBlocking(context) {
            putLong("${MQConstants.PREF_WARMUP_STARTED_AT_PREFIX}$sensorId", timestamp)
        }
    }

    @JvmStatic
    fun loadSensorStartAt(context: Context, sensorId: String): Long =
        prefs(context).getLong("${MQConstants.PREF_SENSOR_START_AT_PREFIX}$sensorId", 0L)

    @JvmStatic
    fun saveSensorStartAt(context: Context, sensorId: String, timestamp: Long) {
        editBlocking(context) {
            putLong("${MQConstants.PREF_SENSOR_START_AT_PREFIX}$sensorId", timestamp)
        }
    }

    @JvmStatic
    fun loadKValue(context: Context, sensorId: String): Float =
        prefs(context).getFloat("${MQConstants.PREF_K_VALUE_PREFIX}$sensorId", 0f)

    @JvmStatic
    fun saveKValue(context: Context, sensorId: String, value: Float) {
        editBlocking(context) {
            putFloat("${MQConstants.PREF_K_VALUE_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadBValue(context: Context, sensorId: String): Float =
        prefs(context).getFloat("${MQConstants.PREF_B_VALUE_PREFIX}$sensorId", 0f)

    @JvmStatic
    fun saveBValue(context: Context, sensorId: String, value: Float) {
        editBlocking(context) {
            putFloat("${MQConstants.PREF_B_VALUE_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadSensitivity(context: Context, sensorId: String): Float =
        prefs(context).getFloat("${MQConstants.PREF_SENSITIVITY_PREFIX}$sensorId", 0f)

    @JvmStatic
    fun saveSensitivity(context: Context, sensorId: String, value: Float) {
        editBlocking(context) {
            putFloat("${MQConstants.PREF_SENSITIVITY_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadAlgorithmVersion(context: Context, sensorId: String): Int =
        prefs(context).getInt("${MQConstants.PREF_ALGORITHM_VERSION_PREFIX}$sensorId",
            MQConstants.ALGO_DEFAULT_VERSION)

    @JvmStatic
    fun saveAlgorithmVersion(context: Context, sensorId: String, value: Int) {
        editBlocking(context) {
            putInt("${MQConstants.PREF_ALGORITHM_VERSION_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadMultiplier(context: Context, sensorId: String): Float =
        prefs(context).getFloat("${MQConstants.PREF_MULTIPLIER_PREFIX}$sensorId",
            MQConstants.ALGO_DEFAULT_MULTIPLIER.toFloat())

    @JvmStatic
    fun saveMultiplier(context: Context, sensorId: String, value: Float) {
        editBlocking(context) {
            putFloat("${MQConstants.PREF_MULTIPLIER_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadPackages(context: Context, sensorId: String): Int =
        prefs(context).getInt("${MQConstants.PREF_PACKAGES_PREFIX}$sensorId",
            MQConstants.ALGO_DEFAULT_PACKAGES)

    @JvmStatic
    fun savePackages(context: Context, sensorId: String, value: Int) {
        editBlocking(context) {
            putInt("${MQConstants.PREF_PACKAGES_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadLastProcessed(context: Context, sensorId: String): Float =
        prefs(context).getFloat("${MQConstants.PREF_LAST_PROCESSED_PREFIX}$sensorId", 0f)

    @JvmStatic
    fun saveLastProcessed(context: Context, sensorId: String, value: Float) {
        editBlocking(context) {
            putFloat("${MQConstants.PREF_LAST_PROCESSED_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadLastPacketIndex(context: Context, sensorId: String): Int =
        prefs(context).getInt("${MQConstants.PREF_LAST_PACKET_INDEX_PREFIX}$sensorId", -1)

    @JvmStatic
    fun saveLastPacketIndex(context: Context, sensorId: String, value: Int) {
        editBlocking(context) {
            putInt("${MQConstants.PREF_LAST_PACKET_INDEX_PREFIX}$sensorId", value)
        }
    }

    @JvmStatic
    fun loadSnapshotId(context: Context, sensorId: String): String? =
        prefs(context).getString("${MQConstants.PREF_SNAPSHOT_ID_PREFIX}$sensorId", null)

    @JvmStatic
    fun saveSnapshotId(context: Context, sensorId: String, value: String?) {
        editBlocking(context) {
            putString(
                "${MQConstants.PREF_SNAPSHOT_ID_PREFIX}$sensorId",
                value?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    @JvmStatic
    fun loadLocalResetPending(context: Context, sensorId: String): Boolean =
        prefs(context).getBoolean("${MQConstants.PREF_LOCAL_RESET_PENDING_PREFIX}$sensorId", false)

    @JvmStatic
    fun saveLocalResetPending(context: Context, sensorId: String, pending: Boolean) {
        editBlocking(context) {
            putBoolean("${MQConstants.PREF_LOCAL_RESET_PENDING_PREFIX}$sensorId", pending)
        }
    }

    @JvmStatic
    fun clearRuntimeState(
        context: Context,
        sensorId: String,
        markLocalResetPending: Boolean = false,
    ) {
        editBlocking(context) {
            putLong("${MQConstants.PREF_WARMUP_STARTED_AT_PREFIX}$sensorId", 0L)
            putLong("${MQConstants.PREF_SENSOR_START_AT_PREFIX}$sensorId", 0L)
            putFloat("${MQConstants.PREF_K_VALUE_PREFIX}$sensorId", 0f)
            putFloat("${MQConstants.PREF_B_VALUE_PREFIX}$sensorId", 0f)
            putFloat("${MQConstants.PREF_LAST_PROCESSED_PREFIX}$sensorId", 0f)
            putInt("${MQConstants.PREF_LAST_PACKET_INDEX_PREFIX}$sensorId", -1)
            remove("${MQConstants.PREF_SNAPSHOT_ID_PREFIX}$sensorId")
            putBoolean("${MQConstants.PREF_LOCAL_RESET_PENDING_PREFIX}$sensorId", markLocalResetPending)
        }
    }

    @JvmStatic
    fun loadQrContent(context: Context, sensorId: String): String? =
        prefs(context).getString("${MQConstants.PREF_QR_CONTENT_PREFIX}$sensorId", null)

    @JvmStatic
    fun saveQrContent(context: Context, sensorId: String, qrCode: String?) {
        editBlocking(context) {
            putString(
                "${MQConstants.PREF_QR_CONTENT_PREFIX}$sensorId",
                qrCode?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    @JvmStatic
    fun loadAuthToken(context: Context): String? =
        prefs(context).getString(MQConstants.PREF_AUTH_TOKEN_KEY, null)

    @JvmStatic
    fun saveAuthToken(context: Context, token: String?) {
        editBlocking(context) {
            putString(MQConstants.PREF_AUTH_TOKEN_KEY, token?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    @JvmStatic
    fun loadAuthPhone(context: Context): String? =
        prefs(context).getString(MQConstants.PREF_AUTH_PHONE_KEY, null)

    @JvmStatic
    fun saveAuthPhone(context: Context, phone: String?) {
        editBlocking(context) {
            putString(MQConstants.PREF_AUTH_PHONE_KEY, phone?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    @JvmStatic
    fun loadAuthPassword(context: Context): String? =
        prefs(context).getString(MQConstants.PREF_AUTH_PASSWORD_KEY, null)

    @JvmStatic
    fun saveAuthPassword(context: Context, password: String?) {
        editBlocking(context) {
            putString(MQConstants.PREF_AUTH_PASSWORD_KEY, password?.takeIf { !it.isNullOrBlank() })
        }
    }

    @JvmStatic
    fun saveAuthCredentials(context: Context, phone: String?, password: String?) {
        editBlocking(context) {
            putString(MQConstants.PREF_AUTH_PHONE_KEY, phone?.trim()?.takeIf { it.isNotEmpty() })
            putString(MQConstants.PREF_AUTH_PASSWORD_KEY, password?.takeIf { !it.isNullOrBlank() })
        }
    }

    @JvmStatic
    fun loadAuthCredentials(context: Context): MQAuthCredentials? {
        val phone = loadAuthPhone(context)?.trim().orEmpty()
        val password = loadAuthPassword(context).orEmpty()
        if (phone.isEmpty() || password.isBlank()) return null
        return MQAuthCredentials(account = phone, password = password)
    }

    @JvmStatic
    fun loadApiBaseUrl(context: Context): String =
        MQConstants.normalizeVendorBaseUrl(
            prefs(context).getString(MQConstants.PREF_API_BASE_URL_KEY, null)
        ) ?: MQConstants.VENDOR_BASE_URL

    @JvmStatic
    fun saveApiBaseUrl(context: Context, baseUrl: String?) {
        editBlocking(context) {
            putString(
                MQConstants.PREF_API_BASE_URL_KEY,
                MQConstants.normalizeVendorBaseUrl(baseUrl),
            )
        }
    }

    @JvmStatic
    fun applyBootstrapConfig(context: Context, sensorId: String, config: MQBootstrapConfig) {
        editBlocking(context) {
            config.protocolType?.let { putInt("${MQConstants.PREF_PROTOCOL_TYPE_PREFIX}$sensorId", it) }
            config.deviation?.let { putInt("${MQConstants.PREF_DEVIATION_PREFIX}$sensorId", it) }
            config.transmitter10?.let { putInt("${MQConstants.PREF_TRANSMITTER10_PREFIX}$sensorId", it) }
            config.sensitivity?.let { putFloat("${MQConstants.PREF_SENSITIVITY_PREFIX}$sensorId", it) }
            config.algorithmVersion?.let { putInt("${MQConstants.PREF_ALGORITHM_VERSION_PREFIX}$sensorId", it) }
            config.packages?.let { putInt("${MQConstants.PREF_PACKAGES_PREFIX}$sensorId", it) }
            config.multiplier?.let { putFloat("${MQConstants.PREF_MULTIPLIER_PREFIX}$sensorId", it) }
            config.snapshotId?.let {
                putString("${MQConstants.PREF_SNAPSHOT_ID_PREFIX}$sensorId", it.trim().takeIf(String::isNotEmpty))
            }
            config.sensorStartAtMs?.takeIf { it > 0L }?.let {
                putLong("${MQConstants.PREF_SENSOR_START_AT_PREFIX}$sensorId", it)
            }
            config.restoredKValue?.let { putFloat("${MQConstants.PREF_K_VALUE_PREFIX}$sensorId", it) }
            config.restoredBValue?.let { putFloat("${MQConstants.PREF_B_VALUE_PREFIX}$sensorId", it) }
            config.restoredLastProcessed?.let { putFloat("${MQConstants.PREF_LAST_PROCESSED_PREFIX}$sensorId", it) }
            config.restoredPacketIndex?.let { putInt("${MQConstants.PREF_LAST_PACKET_INDEX_PREFIX}$sensorId", it) }
        }
        (SensorBluetooth.gattcallbacks.firstOrNull { cb ->
            SensorIdentity.matches(cb.SerialNumber, sensorId) ||
                ((cb as? ManagedBluetoothSensorDriver)?.matchesManagedSensorId(sensorId) == true)
        } as? MQBleManager)?.restoreFromPersistence(context)
    }
}
