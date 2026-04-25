package tk.glucodata

import android.util.Log
import androidx.annotation.Keep

@Keep
object HistorySyncAccess {
    private const val TAG = "HistorySyncAccess"
    private const val SYNC_CLASS_NAME = "tk.glucodata.data.HistorySync"
    private const val REPOSITORY_CLASS_NAME = "tk.glucodata.data.HistoryRepository"
    private const val DEFAULT_AIDEX_SOURCE = 4

    private val syncHolder by lazy { runCatching { Class.forName(SYNC_CLASS_NAME) }.getOrNull() }
    private val syncInstance by lazy { runCatching { syncHolder?.getField("INSTANCE")?.get(null) }.getOrNull() }
    private val syncSensorMethod by lazy {
        runCatching {
            syncHolder?.getMethod("syncSensorFromNative", String::class.java, Boolean::class.javaPrimitiveType)
        }.getOrNull()
    }
    private val syncRecentSensorMethod by lazy {
        runCatching {
            syncHolder?.getMethod("syncRecentSensorFromNative", String::class.java, Long::class.javaPrimitiveType)
        }.getOrNull()
    }
    private val forceFullSensorMethod by lazy {
        runCatching { syncHolder?.getMethod("forceFullSyncForSensor", String::class.java) }.getOrNull()
    }
    private val mergeFullSensorMethod by lazy {
        runCatching { syncHolder?.getMethod("mergeFullSyncForSensor", String::class.java) }.getOrNull()
    }

    private val repositoryHolder by lazy { runCatching { Class.forName(REPOSITORY_CLASS_NAME) }.getOrNull() }
    private val resetBackfillMethod by lazy {
        runCatching { repositoryHolder?.getMethod("resetBackfillFlag") }.getOrNull()
    }
    private val storeReadingMethod by lazy {
        runCatching {
            repositoryHolder?.getMethod(
                "storeReadingAsync",
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        }.getOrNull()
    }
    private val storeReadingWithSerialMethod by lazy {
        runCatching {
            repositoryHolder?.getMethod(
                "storeReadingAsync",
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                String::class.java
            )
        }.getOrNull()
    }
    private val storeHistoryBatchMethod by lazy {
        runCatching {
            repositoryHolder?.getMethod(
                "storeHistoryBatchAsync",
                String::class.java,
                LongArray::class.java,
                FloatArray::class.java,
                FloatArray::class.java
            )
        }.getOrNull()
    }
    private val storeHistoryBatchBlockingMethod by lazy {
        runCatching {
            repositoryHolder?.getMethod(
                "storeHistoryBatchBlocking",
                String::class.java,
                LongArray::class.java,
                FloatArray::class.java,
                FloatArray::class.java
            )
        }.getOrNull()
    }
    private val getLatestTimestampMethod by lazy {
        runCatching {
            repositoryHolder?.getMethod("getLatestTimestampForSensorBlocking", String::class.java)
        }.getOrNull()
    }
    private val aidexSourceValue by lazy {
        runCatching {
            repositoryHolder?.getField("GLUCODATA_SOURCE_AIDEX")?.getInt(null)
        }.getOrNull() ?: DEFAULT_AIDEX_SOURCE
    }

    @JvmStatic
    @JvmOverloads
    fun syncSensorFromNative(serial: String?, forceFull: Boolean = false) {
        if (serial.isNullOrBlank()) return
        val method = syncSensorMethod
        val instance = syncInstance
        if (method == null || instance == null) {
            Log.w(TAG, "syncSensorFromNative unavailable for serial=$serial forceFull=$forceFull")
            return
        }
        runCatching { method.invoke(instance, serial, forceFull) }
            .onFailure { Log.w(TAG, "syncSensorFromNative failed for serial=$serial forceFull=$forceFull", it) }
    }

    @JvmStatic
    fun syncRecentSensorFromNative(serial: String?, anchorTimeMs: Long) {
        if (serial.isNullOrBlank() || anchorTimeMs <= 0L) return
        val method = syncRecentSensorMethod
        val instance = syncInstance
        if (method == null || instance == null) {
            Log.w(TAG, "syncRecentSensorFromNative unavailable for serial=$serial anchor=$anchorTimeMs")
            return
        }
        runCatching { method.invoke(instance, serial, anchorTimeMs) }
            .onFailure { Log.w(TAG, "syncRecentSensorFromNative failed for serial=$serial anchor=$anchorTimeMs", it) }
    }

    @JvmStatic
    fun forceFullSyncForSensor(serial: String?) {
        if (serial.isNullOrBlank()) return
        val instance = syncInstance
        val forceMethod = forceFullSensorMethod
        if (instance != null && forceMethod != null) {
            val invoked = runCatching {
                forceMethod.invoke(instance, serial)
            }.onFailure {
                Log.w(TAG, "forceFullSyncForSensor invoke failed for serial=$serial; falling back to syncSensorFromNative(forceFull=true)", it)
            }.isSuccess
            if (invoked) {
                return
            }
        } else {
            Log.w(TAG, "forceFullSyncForSensor unavailable for serial=$serial; falling back to syncSensorFromNative(forceFull=true)")
        }
        syncSensorFromNative(serial, forceFull = true)
    }

    @JvmStatic
    fun mergeFullSyncForSensor(serial: String?) {
        if (serial.isNullOrBlank()) return
        val instance = syncInstance
        val mergeMethod = mergeFullSensorMethod
        if (instance != null && mergeMethod != null) {
            val invoked = runCatching {
                mergeMethod.invoke(instance, serial)
            }.onFailure {
                Log.w(
                    TAG,
                    "mergeFullSyncForSensor invoke failed for serial=$serial; falling back to syncSensorFromNative(forceFull=true)",
                    it
                )
            }.isSuccess
            if (invoked) {
                return
            }
        } else {
            Log.w(TAG, "mergeFullSyncForSensor unavailable for serial=$serial; falling back to syncSensorFromNative(forceFull=true)")
        }
        syncSensorFromNative(serial, forceFull = true)
    }

    @JvmStatic
    fun resetBackfillFlag() {
        val method = resetBackfillMethod
        if (method == null) {
            Log.w(TAG, "resetBackfillFlag unavailable")
            return
        }
        runCatching { method.invoke(null) }
            .onFailure { Log.w(TAG, "resetBackfillFlag failed", it) }
    }

    @JvmStatic
    fun storeAidexReadingAsync(timestamp: Long, valueMmol: Float) {
        val method = storeReadingMethod
        if (method == null) {
            Log.w(TAG, "storeAidexReadingAsync unavailable for timestamp=$timestamp")
            return
        }
        runCatching { method.invoke(null, timestamp, valueMmol, aidexSourceValue) }
            .onFailure { Log.w(TAG, "storeAidexReadingAsync failed for timestamp=$timestamp", it) }
    }

    @JvmStatic
    fun storeCurrentReadingAsync(
        timestamp: Long,
        valueMgdl: Float,
        rawValueMgdl: Float,
        rate: Float,
        sensorSerial: String?
    ) {
        if (timestamp <= 0L || sensorSerial.isNullOrBlank()) return
        val method = storeReadingWithSerialMethod
        if (method == null) {
            Log.w(TAG, "storeCurrentReadingAsync unavailable for serial=$sensorSerial timestamp=$timestamp")
            return
        }
        runCatching {
            method.invoke(
                null,
                timestamp,
                valueMgdl,
                rawValueMgdl,
                rate,
                sensorSerial
            )
        }.onFailure {
            Log.w(
                TAG,
                "storeCurrentReadingAsync failed for serial=$sensorSerial timestamp=$timestamp",
                it
            )
        }
    }

    @JvmStatic
    fun storeSensorHistoryBatchAsync(
        sensorSerial: String?,
        timestamps: LongArray,
        valuesMgdl: FloatArray,
        rawValuesMgdl: FloatArray
    ): Boolean {
        if (sensorSerial.isNullOrBlank()) return false
        if (timestamps.isEmpty()) return true
        val method = storeHistoryBatchMethod
        if (method == null) {
            Log.w(TAG, "storeSensorHistoryBatchAsync unavailable for serial=$sensorSerial")
            return false
        }
        return runCatching {
            method.invoke(
                null,
                sensorSerial,
                timestamps,
                valuesMgdl,
                rawValuesMgdl
            )
        }.onFailure {
            Log.w(
                TAG,
                "storeSensorHistoryBatchAsync failed for serial=$sensorSerial size=${timestamps.size}",
                it
            )
        }.isSuccess
    }

    @JvmStatic
    fun storeSensorHistoryBatchBlocking(
        sensorSerial: String?,
        timestamps: LongArray,
        valuesMgdl: FloatArray,
        rawValuesMgdl: FloatArray
    ): Boolean {
        if (sensorSerial.isNullOrBlank()) return false
        if (timestamps.isEmpty()) return true
        val method = storeHistoryBatchBlockingMethod
        if (method == null) {
            Log.w(TAG, "storeSensorHistoryBatchBlocking unavailable for serial=$sensorSerial; falling back to async")
            return storeSensorHistoryBatchAsync(sensorSerial, timestamps, valuesMgdl, rawValuesMgdl)
        }
        return runCatching {
            method.invoke(
                null,
                sensorSerial,
                timestamps,
                valuesMgdl,
                rawValuesMgdl
            ) as? Boolean ?: false
        }.onFailure {
            Log.w(
                TAG,
                "storeSensorHistoryBatchBlocking failed for serial=$sensorSerial size=${timestamps.size}",
                it
            )
        }.getOrDefault(false)
    }

    @JvmStatic
    fun getLatestTimestampForSensor(sensorSerial: String?): Long {
        if (sensorSerial.isNullOrBlank()) return 0L
        val method = getLatestTimestampMethod
        if (method == null) {
            Log.w(TAG, "getLatestTimestampForSensor unavailable for serial=$sensorSerial")
            return 0L
        }
        return runCatching {
            (method.invoke(null, sensorSerial) as? Long) ?: 0L
        }.onFailure {
            Log.w(TAG, "getLatestTimestampForSensor failed for serial=$sensorSerial", it)
        }.getOrDefault(0L)
    }

}
