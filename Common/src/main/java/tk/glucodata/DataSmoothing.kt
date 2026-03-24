package tk.glucodata

import android.content.Context

object DataSmoothing {
    private const val PREFS_NAME = "tk.glucodata_preferences"
    private const val MINUTES_KEY = "dashboard_chart_smoothing_minutes"
    private const val GRAPH_ONLY_KEY = "dashboard_data_smoothing_graph_only"
    private const val COLLAPSE_CHUNKS_KEY = "dashboard_data_smoothing_collapse_chunks"

    private val allowedMinutes = intArrayOf(0, 2, 3, 4, 5, 7, 10, 15, 20)

    @JvmStatic
    fun allowedMinutes(): IntArray = allowedMinutes.copyOf()

    @JvmStatic
    fun sanitizeMinutes(minutes: Int): Int {
        return if (allowedMinutes.contains(minutes)) minutes else 0
    }

    @JvmStatic
    fun getMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sanitizeMinutes(prefs.getInt(MINUTES_KEY, 0))
    }

    @JvmStatic
    fun isGraphOnly(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(GRAPH_ONLY_KEY, true)
    }

    @JvmStatic
    fun collapseChunks(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(COLLAPSE_CHUNKS_KEY, false)
    }

    @JvmStatic
    fun setMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(MINUTES_KEY, sanitizeMinutes(minutes))
            .apply()
    }

    @JvmStatic
    fun setGraphOnly(context: Context, graphOnly: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(GRAPH_ONLY_KEY, graphOnly)
            .apply()
    }

    @JvmStatic
    fun setCollapseChunks(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(COLLAPSE_CHUNKS_KEY, enabled)
            .apply()
    }

    @JvmStatic
    fun smoothNativePoints(
        points: List<GlucosePoint>?,
        smoothingMinutes: Int,
        collapseChunks: Boolean
    ): List<GlucosePoint> {
        if (points.isNullOrEmpty()) {
            return emptyList()
        }
        val sanitizedMinutes = sanitizeMinutes(smoothingMinutes)
        if (sanitizedMinutes <= 0 || points.size < 3) {
            return points
        }

        val halfWindowMs = (sanitizedMinutes * 60_000L) / 2L
        if (halfWindowMs <= 0L) {
            return points
        }

        val smoothedAuto = smoothSeries(points, halfWindowMs, useRawValue = false)
        val smoothedRaw = smoothSeries(points, halfWindowMs, useRawValue = true)
        val smoothed = ArrayList<GlucosePoint>(points.size)
        points.indices.forEach { index ->
            val source = points[index]
            val point = GlucosePoint(source.timestamp, smoothedAuto[index], smoothedRaw[index])
            point.color = source.color
            smoothed.add(point)
        }

        return if (collapseChunks) {
            collapsePointList(smoothed, sanitizedMinutes)
        } else {
            smoothed
        }
    }

    private fun smoothSeries(
        points: List<GlucosePoint>,
        halfWindowMs: Long,
        useRawValue: Boolean
    ): FloatArray {
        val size = points.size
        val prefixSums = DoubleArray(size + 1)
        val prefixCounts = IntArray(size + 1)

        for (index in 0 until size) {
            val point = points[index]
            val value = if (useRawValue) point.rawValue else point.value
            val valid = value.isFinite() && value >= 0.1f
            prefixSums[index + 1] = prefixSums[index] + if (valid) value.toDouble() else 0.0
            prefixCounts[index + 1] = prefixCounts[index] + if (valid) 1 else 0
        }

        val result = FloatArray(size)
        var windowStart = 0
        var windowEndExclusive = 0

        for (index in 0 until size) {
            val point = points[index]
            val original = if (useRawValue) point.rawValue else point.value
            if (!original.isFinite() || original < 0.1f) {
                result[index] = original
                continue
            }

            val minTime = point.timestamp - halfWindowMs
            val maxTime = point.timestamp + halfWindowMs

            while (windowStart < size && points[windowStart].timestamp < minTime) {
                windowStart++
            }
            while (windowEndExclusive < size && points[windowEndExclusive].timestamp <= maxTime) {
                windowEndExclusive++
            }

            val count = prefixCounts[windowEndExclusive] - prefixCounts[windowStart]
            result[index] = if (count > 0) {
                ((prefixSums[windowEndExclusive] - prefixSums[windowStart]) / count).toFloat()
            } else {
                original
            }
        }

        return result
    }

    private fun collapsePointList(points: List<GlucosePoint>, smoothingMinutes: Int): List<GlucosePoint> {
        if (points.size < 3 || smoothingMinutes <= 0) {
            return points
        }

        val bucketDurationMs = smoothingMinutes * 60_000L
        val firstTimestamp = points.firstOrNull()?.timestamp ?: return points
        val collapsed = ArrayList<GlucosePoint>()
        var activeBucket = Long.MIN_VALUE
        var pending: GlucosePoint? = null

        for (point in points) {
            val bucket = ((point.timestamp - firstTimestamp).coerceAtLeast(0L)) / bucketDurationMs
            if (bucket != activeBucket) {
                pending?.let(collapsed::add)
                activeBucket = bucket
            }
            pending = point
        }

        pending?.let(collapsed::add)
        return if (collapsed.isEmpty()) points else collapsed
    }
}
