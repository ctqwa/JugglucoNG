package tk.glucodata

import kotlin.math.roundToInt
import tk.glucodata.ui.DisplayValueResolver
import tk.glucodata.ui.DisplayValues

object CurrentDisplaySource {
    private const val DEFAULT_HISTORY_WINDOW_MS = DisplayTrendSource.TREND_WINDOW_MS
    private const val LIVE_CONTEXT_WINDOW_MS = 2 * 60 * 1000L
    private const val MATCH_WINDOW_MS = 60 * 1000L
    private const val MGDL_PER_MMOLL = 18.0182f

    data class Snapshot(
        val timeMillis: Long,
        val rate: Float,
        val sensorId: String?,
        val sensorGen: Int,
        val index: Int,
        val viewMode: Int,
        val source: String,
        val autoValue: Float,
        val rawValue: Float,
        val sharedDisplayValue: Float,
        val sharedMgdl: Int,
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
        val resolvedSensorId = preferredSensorId ?: SensorIdentity.resolveMainSensor()
        val current = CurrentGlucoseSource.getFresh(maxAgeMillis, resolvedSensorId)
        val isMmol = Applic.unit == 1
        val smoothingMinutes = DataSmoothing.getMinutes(Applic.app)
        val smoothAllData = smoothingMinutes > 0 && !DataSmoothing.isGraphOnly(Applic.app)
        val collapseChunks = smoothAllData && DataSmoothing.collapseChunks(Applic.app)
        val now = System.currentTimeMillis()
        val liveHistoryWindowMs = historyWindowMs.coerceAtLeast(LIVE_CONTEXT_WINDOW_MS)
        val historyStart = when {
            current != null && current.timeMillis > 0L -> (current.timeMillis - liveHistoryWindowMs).coerceAtLeast(0L)
            else -> now - historyWindowMs
        }
        val recentPoints = try {
            NotificationHistorySource.getDisplayHistory(historyStart, isMmol, resolvedSensorId)
        } catch (_: Throwable) {
            emptyList()
        }
        val viewMode = resolveSensorViewMode(resolvedSensorId)
        val processedPoints = if (smoothAllData) {
            DataSmoothing.smoothNativePoints(
                recentPoints,
                smoothingMinutes,
                collapseChunks
            )
        } else {
            recentPoints
        }
        val initialSnapshot = resolveFromLive(
            liveValueText = current?.valueText,
            liveNumericValue = current?.numericValue ?: Float.NaN,
            liveRawValue = current?.rawNumericValue ?: Float.NaN,
            rate = current?.rate ?: Float.NaN,
            targetTimeMillis = if (collapseChunks) {
                processedPoints.lastOrNull()?.timestamp ?: current?.timeMillis ?: 0L
            } else {
                current?.timeMillis ?: processedPoints.lastOrNull()?.timestamp ?: 0L
            },
            sensorId = resolvedSensorId,
            sensorGen = current?.sensorGen ?: 0,
            index = current?.index ?: 0,
            source = current?.source ?: if (processedPoints.isNotEmpty()) "history" else "none",
            recentPoints = processedPoints,
            viewMode = viewMode,
            isMmol = isMmol
        )
        if (initialSnapshot == null) {
            return null
        }
        val trendPoints = DisplayTrendSource.augmentHistory(
            historyPoints = processedPoints,
            current = initialSnapshot,
            activeSensorSerial = resolvedSensorId,
            startTimeMs = historyStart
        )
        val canonicalRate = DisplayTrendSource.resolveArrowRate(
            recentPoints = trendPoints,
            current = initialSnapshot,
            viewMode = viewMode,
            isMmol = isMmol,
            fallbackRate = current?.rate ?: Float.NaN
        )
        return initialSnapshot.copy(rate = canonicalRate)
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
        liveRawValue: Float,
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
        val exactMatch = findExactPoint(recentPoints, targetTimeMillis)
        val match = exactMatch ?: recentPoints.lastOrNull()
        val isRawMode = isRawPrimary(viewMode)
        val liveValue = liveNumericValue.takeIf { it.isFinite() && it > 0.1f }

        var autoValue = match?.value?.takeIf { it.isFinite() && it > 0.1f } ?: Float.NaN
        var rawValue = match?.rawValue?.takeIf { it.isFinite() && it > 0.1f } ?: Float.NaN

        val canUseLiveAsLaneFallback = exactMatch == null

        if (canUseLiveAsLaneFallback && !autoValue.isFinite() && !isRawMode && liveValue != null) {
            autoValue = liveValue
        }
        if (canUseLiveAsLaneFallback && !rawValue.isFinite()) {
            rawValue = when {
                liveRawValue.isFinite() && liveRawValue > 0.1f -> liveRawValue
                isRawMode && liveValue != null -> liveValue
                else -> rawValue
            }
        }

        val resolvedExactPoint = exactMatch?.let { point ->
            mergeExactPointWithLive(
                point = point,
                liveAutoValue = liveValue,
                liveRawValue = liveRawValue
            )
        }

        if (resolvedExactPoint != null) {
            autoValue = resolvedExactPoint.value.takeIf { it.isFinite() && it > 0.1f } ?: autoValue
            rawValue = resolvedExactPoint.rawValue.takeIf { it.isFinite() && it > 0.1f } ?: rawValue
        }

        val displayValues = resolvedExactPoint?.let {
            resolveDisplayValuesForPoint(
                point = it,
                viewMode = viewMode,
                isMmol = isMmol,
                sensorId = sensorId
            )
        } ?: run {
            val hideInitialWhenCalibrated = shouldHideInitialWhenCalibrated()
            val calibratedValue = resolveCalibratedValue(
                liveValue = liveValue,
                autoValue = autoValue,
                rawValue = rawValue,
                sensorId = sensorId,
                viewMode = viewMode,
                targetTimeMillis = targetTimeMillis,
                allowLiveFallback = exactMatch == null
            )

            DisplayValueResolver.resolve(
                autoValue = autoValue,
                rawValue = rawValue,
                viewMode = viewMode,
                isMmol = isMmol,
                unitLabel = "",
                calibratedValue = calibratedValue,
                hideInitialWhenCalibrated = calibratedValue != null && hideInitialWhenCalibrated
            )
        }

        val resolvedTime = when {
            targetTimeMillis > 0L -> targetTimeMillis
            match != null -> match.timestamp
            else -> 0L
        }
        if (resolvedTime <= 0L || !displayValues.primaryValue.isFinite() || displayValues.primaryValue <= 0f) {
            return null
        }

        val sharedMgdl = resolveSharedMgdl(
            sensorId = sensorId,
            autoValue = autoValue,
            rawValue = rawValue,
            targetTimeMillis = resolvedTime,
            isMmol = isMmol
        )
        val sharedDisplayValue = if (sharedMgdl > 0) {
            if (isMmol) sharedMgdl / MGDL_PER_MMOLL else sharedMgdl.toFloat()
        } else {
            0f
        }

        return Snapshot(
            timeMillis = resolvedTime,
            rate = rate,
            sensorId = sensorId,
            sensorGen = sensorGen,
            index = index,
            viewMode = viewMode,
            source = source,
            autoValue = autoValue,
            rawValue = rawValue,
            sharedDisplayValue = sharedDisplayValue,
            sharedMgdl = sharedMgdl,
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
        allowLiveFallback: Boolean
    ): Float? {
        val isRawMode = isRawPrimary(viewMode)
        if (!CalibrationAccess.hasActiveCalibration(isRawMode, sensorId)) {
            if (allowLiveFallback && liveValue != null && CalibrationAccess.hasActiveCalibration(isRawMode, null)) {
                val fallbackCalibrated = CalibrationAccess.getCalibratedValue(
                    liveValue,
                    targetTimeMillis,
                    isRawMode,
                    false,
                    null
                )
                return fallbackCalibrated.takeIf { it.isFinite() && it > 0.1f } ?: liveValue
            }
            return null
        }
        val baseValue = (if (isRawMode) rawValue else autoValue).takeIf { it.isFinite() && it > 0.1f }
            ?: autoValue.takeIf { it.isFinite() && it > 0.1f }
            ?: rawValue.takeIf { it.isFinite() && it > 0.1f }
            ?: liveValue?.takeIf { allowLiveFallback && it.isFinite() && it > 0.1f }
            ?: return null

        val calibratedValue = CalibrationAccess.getCalibratedValue(
            baseValue,
            targetTimeMillis,
            isRawMode,
            false,
            sensorId
        )
        return calibratedValue.takeIf { it.isFinite() && it > 0.1f }
            ?: liveValue?.takeIf { allowLiveFallback && it.isFinite() && it > 0.1f }
    }

    private fun resolveDisplayValuesForPoint(
        point: GlucosePoint,
        viewMode: Int,
        isMmol: Boolean,
        sensorId: String?
    ): DisplayValues {
        val isRawMode = isRawPrimary(viewMode)
        val calibratedValue = if (CalibrationAccess.hasActiveCalibration(isRawMode, sensorId)) {
            val baseValue = if (isRawMode) point.rawValue else point.value
            if (baseValue.isFinite() && baseValue > 0.1f) {
                CalibrationAccess.getCalibratedValue(
                    baseValue,
                    point.timestamp,
                    isRawMode,
                    false,
                    sensorId
                ).takeIf { it.isFinite() && it > 0.1f }
            } else {
                null
            }
        } else {
            null
        }
        return DisplayValueResolver.resolve(
            autoValue = point.value,
            rawValue = point.rawValue,
            viewMode = viewMode,
            isMmol = isMmol,
            unitLabel = "",
            calibratedValue = calibratedValue,
            hideInitialWhenCalibrated = calibratedValue != null && shouldHideInitialWhenCalibrated()
        )
    }

    private fun mergeExactPointWithLive(
        point: GlucosePoint,
        liveAutoValue: Float?,
        liveRawValue: Float
    ): GlucosePoint {
        val mergedAuto = if (point.value.isFinite() && point.value > 0.1f) {
            point.value
        } else {
            liveAutoValue?.takeIf { it.isFinite() && it > 0.1f } ?: point.value
        }
        val mergedRaw = if (point.rawValue.isFinite() && point.rawValue > 0.1f) {
            point.rawValue
        } else {
            liveRawValue.takeIf { it.isFinite() && it > 0.1f } ?: point.rawValue
        }
        if (mergedAuto == point.value && mergedRaw == point.rawValue) {
            return point
        }
        return GlucosePoint(point.timestamp, mergedAuto, mergedRaw)
    }

    private fun shouldHideInitialWhenCalibrated(): Boolean {
        return CalibrationAccess.shouldHideInitialWhenCalibrated()
    }

    private fun isRawPrimary(viewMode: Int): Boolean = viewMode == 1 || viewMode == 3

    private fun matchesSensor(candidate: String?, expected: String?): Boolean {
        if (expected.isNullOrBlank()) {
            return true
        }
        return SensorIdentity.matches(candidate, expected)
    }

    private fun findExactPoint(points: List<GlucosePoint>, targetTimeMillis: Long): GlucosePoint? {
        if (points.isEmpty()) {
            return null
        }
        if (targetTimeMillis <= 0L) {
            return points.lastOrNull()
        }
        return points.lastOrNull { kotlin.math.abs(it.timestamp - targetTimeMillis) <= MATCH_WINDOW_MS }
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

    private fun resolveSharedMgdl(
        sensorId: String?,
        autoValue: Float,
        rawValue: Float,
        targetTimeMillis: Long,
        isMmol: Boolean
    ): Int {
        val calibratedAuto = calibrateForShare(sensorId, autoValue, targetTimeMillis, false)
        if (calibratedAuto > 0f) {
            return displayToMgdl(calibratedAuto, isMmol)
        }

        val calibratedRaw = calibrateForShare(sensorId, rawValue, targetTimeMillis, true)
        if (calibratedRaw > 0f) {
            return displayToMgdl(calibratedRaw, isMmol)
        }

        val nativeAutoMgdl = resolveNativeAutoMgdl(sensorId, isMmol)
        if (nativeAutoMgdl > 0) {
            return nativeAutoMgdl
        }

        val autoMgdl = displayToMgdl(autoValue, isMmol)
        if (autoMgdl > 0) {
            return autoMgdl
        }

        val rawMgdl = displayToMgdl(rawValue, isMmol)
        return rawMgdl.coerceAtLeast(0)
    }

    private fun calibrateForShare(
        sensorId: String?,
        baseValue: Float,
        targetTimeMillis: Long,
        isRawMode: Boolean
    ): Float {
        if (!baseValue.isFinite() || baseValue <= 0f) {
            return 0f
        }
        if (!CalibrationAccess.hasActiveCalibration(isRawMode, sensorId)) {
            return 0f
        }
        val calibrated = CalibrationAccess.getCalibratedValue(
            baseValue,
            targetTimeMillis,
            isRawMode,
            false,
            sensorId
        )
        return calibrated.takeIf { it.isFinite() && it > 0f } ?: 0f
    }

    private fun resolveNativeAutoMgdl(sensorId: String?, isMmol: Boolean): Int {
        val latest = try {
            Natives.lastglucose()
        } catch (_: Throwable) {
            null
        } ?: return 0
        if (!SensorIdentity.matches(latest.sensorid, sensorId)) {
            return 0
        }
        val latestValue = GlucoseValueParser.parseFirst(latest.value)
            ?.takeIf { it.isFinite() && it > 0f }
            ?: return 0
        return displayToMgdl(latestValue, isMmol)
    }

    private fun displayToMgdl(value: Float, isMmol: Boolean): Int {
        if (!value.isFinite() || value <= 0f) {
            return 0
        }
        return (if (isMmol) value * MGDL_PER_MMOLL else value).roundToInt()
    }
}
