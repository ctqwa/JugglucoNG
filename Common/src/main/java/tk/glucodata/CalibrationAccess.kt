package tk.glucodata

object CalibrationAccess {
    private const val CLASS_NAME = "tk.glucodata.data.calibration.CalibrationManager"

    private val holder by lazy { runCatching { Class.forName(CLASS_NAME) }.getOrNull() }
    private val instance by lazy { runCatching { holder?.getField("INSTANCE")?.get(null) }.getOrNull() }
    private val hasActiveCalibrationMethod by lazy {
        runCatching {
            holder?.getMethod("hasActiveCalibration", Boolean::class.javaPrimitiveType, String::class.java)
        }.getOrNull()
    }
    private val getCalibratedValueMethod by lazy {
        runCatching {
            holder?.getMethod(
                "getCalibratedValue",
                Float::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java
            )
        }.getOrNull()
    }
    private val shouldHideInitialMethod by lazy {
        runCatching { holder?.getMethod("shouldHideInitialWhenCalibrated") }.getOrNull()
    }
    private val shouldOverwriteSensorValuesMethod by lazy {
        runCatching { holder?.getMethod("shouldOverwriteSensorValues") }.getOrNull()
    }
    private val getRevisionMethod by lazy {
        runCatching { holder?.getMethod("getRevision") }.getOrNull()
    }

    @JvmStatic
    fun hasActiveCalibration(isRawMode: Boolean, sensorId: String? = null): Boolean {
        return runCatching {
            hasActiveCalibrationMethod?.invoke(instance, isRawMode, sensorId) as? Boolean
        }.getOrNull() ?: false
    }

    @JvmStatic
    @JvmOverloads
    fun getCalibratedValue(
        value: Float,
        timestamp: Long,
        isRawMode: Boolean,
        emitDiagnostics: Boolean = false,
        sensorIdOverride: String? = null
    ): Float {
        return runCatching {
            getCalibratedValueMethod?.invoke(
                instance,
                value,
                timestamp,
                isRawMode,
                emitDiagnostics,
                sensorIdOverride
            ) as? Float
        }.getOrNull() ?: value
    }

    @JvmStatic
    fun shouldHideInitialWhenCalibrated(): Boolean {
        return runCatching {
            shouldHideInitialMethod?.invoke(instance) as? Boolean
        }.getOrNull() ?: false
    }

    @JvmStatic
    fun shouldOverwriteSensorValues(): Boolean {
        return runCatching {
            shouldOverwriteSensorValuesMethod?.invoke(instance) as? Boolean
        }.getOrNull() ?: false
    }

    @JvmStatic
    fun getRevision(): Long {
        return runCatching {
            when (val value = getRevisionMethod?.invoke(instance)) {
                is Long -> value
                is Number -> value.toLong()
                else -> null
            }
        }.getOrNull() ?: 0L
    }
}
