package tk.glucodata

import android.util.Log

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
    private val forceFullSensorMethod by lazy {
        runCatching { syncHolder?.getMethod("forceFullSyncForSensor", String::class.java) }.getOrNull()
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
    fun resetBackfillFlag() {
        runCatching { resetBackfillMethod?.invoke(null) }
    }

    @JvmStatic
    fun storeAidexReadingAsync(timestamp: Long, valueMmol: Float) {
        runCatching { storeReadingMethod?.invoke(null, timestamp, valueMmol, aidexSourceValue) }
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
        runCatching {
            storeReadingWithSerialMethod?.invoke(
                null,
                timestamp,
                valueMgdl,
                rawValueMgdl,
                rate,
                sensorSerial
            )
        }
    }
}
