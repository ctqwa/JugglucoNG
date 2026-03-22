package tk.glucodata

import tk.glucodata.ui.DisplayValueResolver
import tk.glucodata.ui.DisplayValues

object CurrentDisplaySource {
    private const val DEFAULT_HISTORY_WINDOW_MS = 15 * 60 * 1000L
    private const val MATCH_WINDOW_MS = 60 * 1000L

    data class Snapshot(
        val timeMillis: Long,
        val rate: Float,
        val sensorId: String?,
        val sensorGen: Int,
        val index: Int,
        val viewMode: Int,
        val source: String,
        val displayValues: DisplayValues
    ) {
        val primaryValue: Float get() = displayValues.primaryValue
        val primaryStr: String get() = displayValues.primaryStr
        val secondaryStr: String? get() = displayValues.secondaryStr
        val tertiaryStr: String? get() = displayValues.tertiaryStr
        val fullFormatted: String get() = displayValues.fullFormatted
    }

    @JvmStatic
    @JvmOverloads
    fun resolveCurrent(
        maxAgeMillis: Long = Notify.glucosetimeout,
        preferredSensorId: String? = null,
        historyWindowMs: Long = DEFAULT_HISTORY_WINDOW_MS
    ): Snapshot? {
        val resolvedSensorId = NotificationHistorySource.resolveSensorSerial(preferredSensorId)
        val current = CurrentGlucoseSource.getFresh(maxAgeMillis)
            ?.takeIf { matchesSensor(it.sensorId, resolvedSensorId) }
        val isMmol = Applic.unit == 1
        val now = System.currentTimeMillis()
        val recentPoints = try {
            NotificationHistorySource.getDisplayHistory(now - historyWindowMs, isMmol, resolvedSensorId)
        } catch (_: Throwable) {
            emptyList()
        }
        val viewMode = resolveSensorViewMode(resolvedSensorId)
        return resolveFromLive(
            liveValueText = current?.valueText,
            liveNumericValue = current?.numericValue ?: Float.NaN,
            rate = current?.rate ?: Float.NaN,
            targetTimeMillis = current?.timeMillis ?: recentPoints.lastOrNull()?.timestamp ?: 0L,
            sensorId = resolvedSensorId,
            sensorGen = current?.sensorGen ?: 0,
            index = current?.index ?: 0,
            source = current?.source ?: if (recentPoints.isNotEmpty()) "history" else "none",
            recentPoints = recentPoints,
            viewMode = viewMode,
            isMmol = isMmol
        )
    }

    @JvmStatic
    fun getFreshNotGlucose(maxAgeMillis: Long): notGlucose? {
        val snapshot = resolveCurrent(maxAgeMillis) ?: return null
        return notGlucose(snapshot.timeMillis, snapshot.primaryStr, snapshot.rate, snapshot.sensorGen)
    }

    @JvmStatic
    fun getFreshNotGlucose(): notGlucose? = getFreshNotGlucose(Notify.glucosetimeout)

    @JvmStatic
    fun resolveFromLive(
        liveValueText: String?,
        liveNumericValue: Float,
        rate: Float,
        targetTimeMillis: Long,
        sensorId: String?,
        sensorGen: Int,
        index: Int,
        source: String,
        recentPoints: List<GlucosePoint>,
        viewMode: Int,
        isMmol: Boolean
    ): Snapshot? {
        val match = findBestPoint(recentPoints, targetTimeMillis)
        val isRawMode = isRawPrimary(viewMode)
        val liveValue = liveNumericValue.takeIf { it.isFinite() && it > 0.1f }

        var autoValue = match?.value?.takeIf { it.isFinite() && it > 0.1f } ?: Float.NaN
        var rawValue = match?.rawValue?.takeIf { it.isFinite() && it > 0.1f } ?: Float.NaN

        if (!autoValue.isFinite() && !isRawMode && liveValue != null) {
            autoValue = liveValue
        }
        if (!rawValue.isFinite() && isRawMode && liveValue != null) {
            rawValue = liveValue
        }

        val hideInitialWhenCalibrated = shouldHideInitialWhenCalibrated()
        val calibratedValue = resolveCalibratedValue(
            liveValue = liveValue,
            autoValue = autoValue,
            rawValue = rawValue,
            sensorId = sensorId,
            viewMode = viewMode,
            targetTimeMillis = targetTimeMillis,
            source = source,
            isMmol = isMmol
        )

        val displayValues = DisplayValueResolver.resolve(
            autoValue = autoValue,
            rawValue = rawValue,
            viewMode = viewMode,
            isMmol = isMmol,
            unitLabel = "",
            calibratedValue = calibratedValue,
            hideInitialWhenCalibrated = calibratedValue != null && hideInitialWhenCalibrated
        )

        val resolvedTime = when {
            targetTimeMillis > 0L -> targetTimeMillis
            match != null -> match.timestamp
            else -> 0L
        }
        if (resolvedTime <= 0L || !displayValues.primaryValue.isFinite() || displayValues.primaryValue <= 0f) {
            return null
        }

        return Snapshot(
            timeMillis = resolvedTime,
            rate = rate,
            sensorId = sensorId,
            sensorGen = sensorGen,
            index = index,
            viewMode = viewMode,
            source = source,
            displayValues = displayValues
        )
    }

    private fun resolveCalibratedValue(
        liveValue: Float?,
        autoValue: Float,
        rawValue: Float,
        sensorId: String?,
        viewMode: Int,
        targetTimeMillis: Long,
        source: String,
        isMmol: Boolean
    ): Float? {
        if (!NightscoutCalibration.hasCalibrationForViewMode(sensorId, viewMode)) {
            return null
        }
        if (source == "callback" && liveValue != null && liveValue.isFinite() && liveValue > 0.1f) {
            return liveValue
        }

        val isRawMode = isRawPrimary(viewMode)
        val baseValue = (if (isRawMode) rawValue else autoValue).takeIf { it.isFinite() && it > 0.1f }
            ?: autoValue.takeIf { it.isFinite() && it > 0.1f }
            ?: rawValue.takeIf { it.isFinite() && it > 0.1f }
            ?: return liveValue?.takeIf { it.isFinite() && it > 0.1f }

        val calibratedValue = CalibrationAccess.getCalibratedValue(
            baseValue,
            targetTimeMillis,
            isRawMode,
            false,
            sensorId
        )
        return calibratedValue.takeIf { it.isFinite() && it > 0.1f }
            ?: liveValue?.takeIf { it.isFinite() && it > 0.1f }
    }

    private fun shouldHideInitialWhenCalibrated(): Boolean {
        return CalibrationAccess.shouldHideInitialWhenCalibrated()
    }

    private fun isRawPrimary(viewMode: Int): Boolean = viewMode == 1 || viewMode == 3

    private fun matchesSensor(candidate: String?, expected: String?): Boolean {
        if (expected.isNullOrBlank() || candidate.isNullOrBlank()) {
            return true
        }
        return candidate == expected || candidate.contains(expected) || expected.contains(candidate)
    }

    private fun findBestPoint(points: List<GlucosePoint>, targetTimeMillis: Long): GlucosePoint? {
        if (points.isEmpty()) {
            return null
        }
        if (targetTimeMillis <= 0L) {
            return points.lastOrNull()
        }
        return points.lastOrNull { kotlin.math.abs(it.timestamp - targetTimeMillis) <= MATCH_WINDOW_MS }
            ?: points.lastOrNull()
    }

    private fun resolveSensorViewMode(sensorName: String?): Int {
        if (sensorName.isNullOrEmpty()) {
            return 0
        }
        return try {
            val snapshot = Natives.getSensorUiSnapshot(sensorName)
            if (snapshot != null && snapshot.size >= 2) snapshot[1].toInt() else 0
        } catch (_: Throwable) {
            0
        }
    }
}
