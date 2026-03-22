package tk.glucodata

object HistorySyncAccess {
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
    private val aidexSourceValue by lazy {
        runCatching {
            repositoryHolder?.getField("GLUCODATA_SOURCE_AIDEX")?.getInt(null)
        }.getOrNull() ?: DEFAULT_AIDEX_SOURCE
    }

    @JvmStatic
    @JvmOverloads
    fun syncSensorFromNative(serial: String?, forceFull: Boolean = false) {
        if (serial.isNullOrBlank()) return
        runCatching { syncSensorMethod?.invoke(syncInstance, serial, forceFull) }
    }

    @JvmStatic
    fun forceFullSyncForSensor(serial: String?) {
        if (serial.isNullOrBlank()) return
        runCatching { forceFullSensorMethod?.invoke(syncInstance, serial) }
    }

    @JvmStatic
    fun resetBackfillFlag() {
        runCatching { resetBackfillMethod?.invoke(null) }
    }

    @JvmStatic
    fun storeAidexReadingAsync(timestamp: Long, valueMmol: Float) {
        runCatching { storeReadingMethod?.invoke(null, timestamp, valueMmol, aidexSourceValue) }
    }
}
