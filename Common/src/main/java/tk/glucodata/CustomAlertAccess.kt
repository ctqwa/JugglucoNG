package tk.glucodata

import android.content.Context

object CustomAlertAccess {
    private const val CLASS_NAME = "tk.glucodata.logic.CustomAlertManager"

    private val holder by lazy { runCatching { Class.forName(CLASS_NAME) }.getOrNull() }
    private val instance by lazy { runCatching { holder?.getField("INSTANCE")?.get(null) }.getOrNull() }
    private val checkAndTriggerMethod by lazy {
        runCatching {
            holder?.getMethod(
                "checkAndTrigger",
                Context::class.java,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            )
        }.getOrNull()
    }

    @JvmStatic
    fun checkAndTrigger(context: Context, glucose: Float, rate: Float, timestampMillis: Long) {
        runCatching { checkAndTriggerMethod?.invoke(instance, context, glucose, rate, timestampMillis) }
    }
}
