package tk.glucodata

import kotlin.math.abs

object TrendAccess {
    private const val CLASS_NAME = "tk.glucodata.logic.TrendEngine"

    private val holder by lazy { runCatching { Class.forName(CLASS_NAME) }.getOrNull() }
    private val instance by lazy { runCatching { holder?.getField("INSTANCE")?.get(null) }.getOrNull() }
    private val calculateTrendMethod by lazy {
        runCatching {
            holder?.getMethod("calculateTrend", List::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        }.getOrNull()
    }
    private val velocityMethod by lazy {
        runCatching { Class.forName("tk.glucodata.logic.TrendEngine\$TrendResult").getMethod("getVelocity") }.getOrNull()
    }

    @JvmStatic
    fun calculateVelocity(points: List<GlucosePoint>, useRaw: Boolean, isMmol: Boolean): Float {
        val reflected = runCatching {
            val result = calculateTrendMethod?.invoke(instance, points, useRaw, isMmol)
            velocityMethod?.invoke(result) as? Float
        }.getOrNull()
        if (reflected != null && reflected.isFinite()) {
            return reflected
        }
        return fallbackVelocity(points, useRaw)
    }

    private fun fallbackVelocity(points: List<GlucosePoint>, useRaw: Boolean): Float {
        if (points.size < 2) return 0f
        val last = points.last()
        val previous = points.dropLast(1).lastOrNull { candidate ->
            val value = if (useRaw) candidate.rawValue else candidate.value
            value.isFinite() && value > 0f
        } ?: return 0f
        val lastValue = if (useRaw) last.rawValue else last.value
        val prevValue = if (useRaw) previous.rawValue else previous.value
        if (!lastValue.isFinite() || !prevValue.isFinite() || lastValue <= 0f || prevValue <= 0f) {
            return 0f
        }
        val minutes = (last.timestamp - previous.timestamp) / 60000f
        if (!minutes.isFinite() || abs(minutes) < 0.1f) {
            return 0f
        }
        return (lastValue - prevValue) / minutes
    }
}
