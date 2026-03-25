package tk.glucodata.data.calibration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tk.glucodata.Natives
import tk.glucodata.UiRefreshBus
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.pow

object CalibrationManager {
    private const val TAG = "CalibrationManager"
    private const val PREFS_NAME = "calibration_prefs"
    private const val KEY_ENABLED_RAW = "calibration_enabled_raw"
    private const val KEY_ENABLED_AUTO = "calibration_enabled_auto"
    private const val KEY_ALGORITHM_RAW = "calibration_algorithm_raw"
    private const val KEY_ALGORITHM_AUTO = "calibration_algorithm_auto"
    private const val KEY_HIDE_INITIAL_WHEN_CALIBRATED = "hide_initial_when_calibrated"
    private const val KEY_APPLY_TO_PAST = "calibration_apply_to_past"
    private const val KEY_LOCK_PAST_HISTORY = "calibration_lock_past_history"
    private const val KEY_OVERWRITE_SENSOR_VALUES = "calibration_overwrite_sensor_values"
    private const val KEY_VISUAL_CONTINUITY = "calibration_visual_continuity"
    private const val HOUR_MS = 3_600_000.0
    private const val PAST_BLEND_WINDOW_MS = 30L * 60L * 1000L

    enum class CalibrationAlgorithm(
        val storageValue: String,
        val title: String,
        val description: String
    ) {
        SANE_WEIGHTED_OLS(
            storageValue = "sane_weighted_ols",
            title = "Sane Weighted OLS",
            description = "Recency-weighted linear fit with slope guardrails"
        ),
        XDRIP_MEDIAN_SLOPE(
            storageValue = "xdrip_median_slope",
            title = "xDrip Median Slope",
            description = "Median pair-slope fit (Theil-Sen style), robust to outliers"
        ),
        TIME_WEIGHTED_ROBUST_REGRESSION(
            storageValue = "time_weighted_robust_regression",
            title = "Time-Weighted Robust Regression",
            description = "Huber-style weighted regression with temporal decay"
        ),
        ELASTIC_TIME_WEIGHTED_INTERPOLATION(
            storageValue = "elastic_time_weighted_interpolation",
            title = "Elastic Time-Weighted Point Interpolation",
            description = "Local anchor interpolation blended with global trend"
        ),
        ADAPTIVE_ENSEMBLE(
            storageValue = "adaptive_ensemble",
            title = "Adaptive Ensemble (Recommended)",
            description = "Blends robust, elastic and median-slope predictions"
        );

        companion object {
            fun fromStorage(value: String?): CalibrationAlgorithm {
                return values().firstOrNull { it.storageValue == value } ?: ADAPTIVE_ENSEMBLE
            }
        }
    }

    private data class CalPoint(
        val x: Double,
        val y: Double,
        val timestamp: Long
    )

    private data class LinearModel(
        val slope: Double,
        val intercept: Double
    ) {
        fun predict(x: Double): Double = slope * x + intercept
    }

    private data class AlgorithmComputation(
        val prediction: Double,
        val slope: Double? = null,
        val intercept: Double? = null,
        val offset: Double? = null,
        val anchorInfluence: Double? = null,
        val confidence: Double? = null,
        val note: String = ""
    )

    data class CalibrationDiagnostics(
        val algorithm: CalibrationAlgorithm = CalibrationAlgorithm.ADAPTIVE_ENSEMBLE,
        val pointCount: Int = 0,
        val slope: Float? = null,
        val intercept: Float? = null,
        val offset: Float? = null,
        val anchorInfluence: Float? = null,
        val confidence: Float? = null,
        val targetValue: Float = 0f,
        val targetTimestamp: Long = 0L,
        val note: String = "Waiting for the next calibrated reading",
        val updatedAt: Long = 0L
    )

    private data class CalibrationCacheKey(
        val isRawMode: Boolean,
        val sensorId: String,
        val algorithm: CalibrationAlgorithm,
        val timestamp: Long,
        val quantizedValue: Int,
        val revision: Long
    )
    
    private lateinit var database: CalibrationDatabase
    private lateinit var dao: CalibrationDao
    private lateinit var prefs: SharedPreferences
    
    // Reactive list of calibrations
    private val _calibrations = MutableStateFlow<List<CalibrationEntity>>(emptyList())
    val calibrations: StateFlow<List<CalibrationEntity>> = _calibrations
    
    // Per-mode enable/disable state
    private val _isEnabledForRaw = MutableStateFlow(true)
    val isEnabledForRaw: StateFlow<Boolean> = _isEnabledForRaw
    
    private val _isEnabledForAuto = MutableStateFlow(true)
    val isEnabledForAuto: StateFlow<Boolean> = _isEnabledForAuto

    private val _hideInitialWhenCalibrated = MutableStateFlow(false)
    val hideInitialWhenCalibrated: StateFlow<Boolean> = _hideInitialWhenCalibrated

    private val _applyToPast = MutableStateFlow(false)
    val applyToPast: StateFlow<Boolean> = _applyToPast

    private val _lockPastHistory = MutableStateFlow(false)
    val lockPastHistory: StateFlow<Boolean> = _lockPastHistory

    private val _overwriteSensorValues = MutableStateFlow(false)
    val overwriteSensorValues: StateFlow<Boolean> = _overwriteSensorValues

    private val _visualContinuity = MutableStateFlow(false)
    val visualContinuity: StateFlow<Boolean> = _visualContinuity

    // Per-mode algorithm selection
    private val _algorithmForRaw = MutableStateFlow(CalibrationAlgorithm.ADAPTIVE_ENSEMBLE)
    val algorithmForRaw: StateFlow<CalibrationAlgorithm> = _algorithmForRaw

    private val _algorithmForAuto = MutableStateFlow(CalibrationAlgorithm.ADAPTIVE_ENSEMBLE)
    val algorithmForAuto: StateFlow<CalibrationAlgorithm> = _algorithmForAuto

    private val _diagnosticsForRaw = MutableStateFlow(CalibrationDiagnostics())
    val diagnosticsForRaw: StateFlow<CalibrationDiagnostics> = _diagnosticsForRaw

    private val _diagnosticsForAuto = MutableStateFlow(CalibrationDiagnostics())
    val diagnosticsForAuto: StateFlow<CalibrationDiagnostics> = _diagnosticsForAuto

    private var lastDiagnosticsEmitRaw = 0L
    private var lastDiagnosticsEmitAuto = 0L

    @Volatile
    private var calibrationRevision = 0L
    @Volatile
    private var suppressMirrorSyncCount = 0
    private val calibrationCache = object : LinkedHashMap<CalibrationCacheKey, Float>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CalibrationCacheKey, Float>?): Boolean {
            return size > 4096
        }
    }

    fun init(context: Context) {
        database = CalibrationDatabase.getInstance(context)
        dao = database.calibrationDao()
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isEnabledForRaw.value = prefs.getBoolean(KEY_ENABLED_RAW, true)
        _isEnabledForAuto.value = prefs.getBoolean(KEY_ENABLED_AUTO, true)
        _hideInitialWhenCalibrated.value = prefs.getBoolean(KEY_HIDE_INITIAL_WHEN_CALIBRATED, false)
        _applyToPast.value = prefs.getBoolean(KEY_APPLY_TO_PAST, false)
        _lockPastHistory.value = prefs.getBoolean(KEY_LOCK_PAST_HISTORY, false)
        _overwriteSensorValues.value = prefs.getBoolean(KEY_OVERWRITE_SENSOR_VALUES, false)
        _visualContinuity.value = prefs.getBoolean(KEY_VISUAL_CONTINUITY, false)
        runCatching { Natives.setCalibratePast(_applyToPast.value) }
        _algorithmForRaw.value = CalibrationAlgorithm.fromStorage(
            prefs.getString(KEY_ALGORITHM_RAW, CalibrationAlgorithm.ADAPTIVE_ENSEMBLE.storageValue)
        )
        _algorithmForAuto.value = CalibrationAlgorithm.fromStorage(
            prefs.getString(KEY_ALGORITHM_AUTO, CalibrationAlgorithm.ADAPTIVE_ENSEMBLE.storageValue)
        )
    }

    private fun invalidateComputationCache(reason: String) {
        calibrationRevision++
        synchronized(calibrationCache) {
            calibrationCache.clear()
        }
        Log.d(TAG, "Calibration cache invalidated: $reason")
    }

    private fun normalizeSensorId(sensorId: String?): String {
        return sensorId?.trim()?.takeIf { it.isNotEmpty() } ?: ""
    }

    private inline fun <T> withoutMirrorSync(block: () -> T): T {
        suppressMirrorSyncCount++
        return try {
            block()
        } finally {
            suppressMirrorSyncCount--
        }
    }

    private fun shouldMirrorSync(): Boolean = suppressMirrorSyncCount <= 0

    private fun requestMirrorCalibrationSync(sensorId: String?) {
        if (!shouldMirrorSync()) return
        val normalized = normalizeSensorId(sensorId)
        if (normalized.isBlank()) return
        runCatching { Natives.requestMirrorCalibrationSync(normalized) }
            .onFailure { Log.w(TAG, "Failed requesting mirror calibration sync for $normalized", it) }
    }

    private fun requestMirrorCalibrationSyncForSensors(sensorIds: Iterable<String?>) {
        if (!shouldMirrorSync()) return
        val targets = linkedSetOf<String>()
        sensorIds.forEach { candidate ->
            normalizeSensorId(candidate).takeIf { it.isNotBlank() }?.let(targets::add)
        }
        normalizeSensorId(Natives.lastsensorname()).takeIf { it.isNotBlank() }?.let(targets::add)
        targets.forEach(::requestMirrorCalibrationSync)
    }

    private fun requestMirrorCalibrationSyncForCurrentOrKnownSensors() {
        requestMirrorCalibrationSyncForSensors(_calibrations.value.map { it.sensorId })
    }

    private fun sensorMatches(calibrationSensorId: String, sensorId: String): Boolean {
        if (calibrationSensorId.isBlank()) return true
        if (sensorId.isBlank()) return false
        return calibrationSensorId.trim().equals(sensorId, ignoreCase = true)
    }

    private fun getValidPoints(isRawMode: Boolean, sensorId: String): List<CalPoint> {
        val currentList = _calibrations.value
        return currentList
            .asSequence()
            .filter {
                it.isEnabled &&
                    it.isRawMode == isRawMode &&
                    sensorMatches(it.sensorId, sensorId)
            }
            .map { p ->
                CalPoint(
                    x = (if (isRawMode) p.sensorValueRaw else p.sensorValue).toDouble(),
                    y = p.userValue.toDouble(),
                    timestamp = p.timestamp
                )
            }
            .toList()
    }

    private fun getValidPointsForSensor(isRawMode: Boolean, sensorIdOverride: String?): List<CalPoint> {
        val sensorId = normalizeSensorId(sensorIdOverride ?: Natives.lastsensorname())
        return getValidPoints(isRawMode = isRawMode, sensorId = sensorId)
    }
    
    fun setEnabledForMode(isRawMode: Boolean, enabled: Boolean) {
        if (isRawMode) {
            if (_isEnabledForRaw.value == enabled) return
            _isEnabledForRaw.value = enabled
            if (::prefs.isInitialized) {
                prefs.edit().putBoolean(KEY_ENABLED_RAW, enabled).apply()
            }
        } else {
            if (_isEnabledForAuto.value == enabled) return
            _isEnabledForAuto.value = enabled
            if (::prefs.isInitialized) {
                prefs.edit().putBoolean(KEY_ENABLED_AUTO, enabled).apply()
            }
        }
        invalidateComputationCache("setEnabledForMode(${if (isRawMode) "raw" else "auto"})")
        UiRefreshBus.requestStatusRefresh()
        requestMirrorCalibrationSyncForCurrentOrKnownSensors()
        Log.i(TAG, "Calibration enabled for ${if (isRawMode) "Raw" else "Auto"}: $enabled")
    }
    
    fun isEnabledForMode(isRawMode: Boolean): Boolean {
        return if (isRawMode) _isEnabledForRaw.value else _isEnabledForAuto.value
    }

    fun setHideInitialWhenCalibrated(enabled: Boolean) {
        if (_hideInitialWhenCalibrated.value == enabled) return
        _hideInitialWhenCalibrated.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_HIDE_INITIAL_WHEN_CALIBRATED, enabled).apply()
        }
        UiRefreshBus.requestStatusRefresh()
        requestMirrorCalibrationSyncForCurrentOrKnownSensors()
        Log.i(TAG, "Hide initial when calibrated: $enabled")
    }

    fun shouldHideInitialWhenCalibrated(): Boolean = _hideInitialWhenCalibrated.value

    fun setApplyToPast(enabled: Boolean) {
        if (_applyToPast.value == enabled) return
        _applyToPast.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_APPLY_TO_PAST, enabled).apply()
        }
        runCatching { Natives.setCalibratePast(enabled) }
        invalidateComputationCache("setApplyToPast")
        UiRefreshBus.requestStatusRefresh()
        requestMirrorCalibrationSyncForCurrentOrKnownSensors()
        Log.i(TAG, "Apply calibration to past: $enabled")
    }

    fun shouldApplyToPast(): Boolean = _applyToPast.value

    fun setLockPastHistory(enabled: Boolean) {
        if (_lockPastHistory.value == enabled) return
        _lockPastHistory.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_LOCK_PAST_HISTORY, enabled).apply()
        }
        invalidateComputationCache("setLockPastHistory")
        UiRefreshBus.requestStatusRefresh()
        requestMirrorCalibrationSyncForCurrentOrKnownSensors()
        Log.i(TAG, "Lock past history calibration rewrite: $enabled")
    }

    fun shouldLockPastHistory(): Boolean = _lockPastHistory.value

    fun setOverwriteSensorValues(enabled: Boolean) {
        if (_overwriteSensorValues.value == enabled) return
        _overwriteSensorValues.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_OVERWRITE_SENSOR_VALUES, enabled).apply()
        }
        UiRefreshBus.requestStatusRefresh()
        requestMirrorCalibrationSyncForCurrentOrKnownSensors()
        Log.i(TAG, "Overwrite sensor values in history DB: $enabled")
    }

    fun shouldOverwriteSensorValues(): Boolean = _overwriteSensorValues.value

    fun setVisualContinuity(enabled: Boolean) {
        if (_visualContinuity.value == enabled) return
        _visualContinuity.value = enabled
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_VISUAL_CONTINUITY, enabled).apply()
        }
        UiRefreshBus.requestStatusRefresh()
        requestMirrorCalibrationSyncForCurrentOrKnownSensors()
        Log.i(TAG, "Visual continuity mode: $enabled")
    }

    fun shouldVisualContinuity(): Boolean = _visualContinuity.value

    fun setAlgorithmForMode(isRawMode: Boolean, algorithm: CalibrationAlgorithm) {
        if (isRawMode) {
            if (_algorithmForRaw.value == algorithm) return
            _algorithmForRaw.value = algorithm
            if (::prefs.isInitialized) {
                prefs.edit().putString(KEY_ALGORITHM_RAW, algorithm.storageValue).apply()
            }
        } else {
            if (_algorithmForAuto.value == algorithm) return
            _algorithmForAuto.value = algorithm
            if (::prefs.isInitialized) {
                prefs.edit().putString(KEY_ALGORITHM_AUTO, algorithm.storageValue).apply()
            }
        }
        invalidateComputationCache("setAlgorithmForMode(${if (isRawMode) "raw" else "auto"})")
        refreshDiagnosticsPreview(isRawMode = isRawMode, force = true)
        UiRefreshBus.requestStatusRefresh()
        requestMirrorCalibrationSyncForCurrentOrKnownSensors()
        Log.i(TAG, "Calibration algorithm for ${if (isRawMode) "Raw" else "Auto"}: ${algorithm.title}")
    }

    fun getAlgorithmForMode(isRawMode: Boolean): CalibrationAlgorithm {
        return if (isRawMode) _algorithmForRaw.value else _algorithmForAuto.value
    }

    data class CalibrationProfileImportResult(
        val sensorId: String,
        val imported: Int,
        val skipped: Int,
        val replaced: Int,
        val message: String
    )

    fun exportProfileForSensorAsJson(sensorId: String): String? {
        if (sensorId.isBlank()) return null

        val rows = _calibrations.value
            .filter { it.sensorId == sensorId || it.sensorId.isEmpty() }
            .sortedByDescending { it.timestamp }

        val root = JSONObject()
        root.put("version", 1)
        root.put("sensorId", sensorId)
        root.put("createdAt", System.currentTimeMillis())
        root.put("rawEnabled", _isEnabledForRaw.value)
        root.put("autoEnabled", _isEnabledForAuto.value)
        root.put("hideInitialWhenCalibrated", _hideInitialWhenCalibrated.value)
        root.put("applyToPast", _applyToPast.value)
        root.put("lockPastHistory", _lockPastHistory.value)
        root.put("overwriteSensorValues", _overwriteSensorValues.value)
        root.put("visualContinuity", _visualContinuity.value)
        root.put("rawAlgorithm", _algorithmForRaw.value.storageValue)
        root.put("autoAlgorithm", _algorithmForAuto.value.storageValue)

        val payload = JSONArray()
        rows.forEach { row ->
            val obj = JSONObject()
            obj.put("timestamp", row.timestamp)
            obj.put("sensorId", if (row.sensorId.isBlank()) sensorId else row.sensorId)
            obj.put("sensorValue", row.sensorValue.toDouble())
            obj.put("sensorValueRaw", row.sensorValueRaw.toDouble())
            obj.put("userValue", row.userValue.toDouble())
            obj.put("isEnabled", row.isEnabled)
            obj.put("isRawMode", row.isRawMode)
            payload.put(obj)
        }
        root.put("calibrations", payload)

        return root.toString(2)
    }

    suspend fun importProfileFromJson(
        json: String,
        replaceExisting: Boolean,
        overrideSensorId: String? = null
    ): CalibrationProfileImportResult {
        val root = JSONObject(json)
        val sourceSensorId = root.optString("sensorId", "").ifBlank { Natives.lastsensorname() ?: "" }
        val targetSensorId = overrideSensorId?.ifBlank { null } ?: sourceSensorId
        if (targetSensorId.isBlank()) {
            return CalibrationProfileImportResult(
                sensorId = "",
                imported = 0,
                skipped = 0,
                replaced = 0,
                message = "No target sensor found in profile"
            )
        }

        val rawEnabled = root.optBoolean("rawEnabled", _isEnabledForRaw.value)
        val autoEnabled = root.optBoolean("autoEnabled", _isEnabledForAuto.value)
        val hideInitialWhenCalibrated = root.optBoolean(
            "hideInitialWhenCalibrated",
            _hideInitialWhenCalibrated.value
        )
        val applyToPast = root.optBoolean("applyToPast", _applyToPast.value)
        val lockPastHistory = root.optBoolean("lockPastHistory", _lockPastHistory.value)
        val overwriteSensorValues = root.optBoolean(
            "overwriteSensorValues",
            _overwriteSensorValues.value
        )
        val visualContinuity = root.optBoolean("visualContinuity", _visualContinuity.value)
        val rawAlgorithm = CalibrationAlgorithm.fromStorage(root.optString("rawAlgorithm", ""))
        val autoAlgorithm = CalibrationAlgorithm.fromStorage(root.optString("autoAlgorithm", ""))

        val array = root.optJSONArray("calibrations") ?: JSONArray()
        val incoming = mutableListOf<CalibrationEntity>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val timestamp = obj.optLong("timestamp", 0L)
            if (timestamp <= 0L) continue

            incoming.add(
                CalibrationEntity(
                    id = 0,
                    timestamp = timestamp,
                    sensorId = targetSensorId,
                    sensorValue = obj.optDouble("sensorValue", 0.0).toFloat(),
                    sensorValueRaw = obj.optDouble("sensorValueRaw", 0.0).toFloat(),
                    userValue = obj.optDouble("userValue", 0.0).toFloat(),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    isRawMode = obj.optBoolean("isRawMode", false)
                )
            )
        }

        val replaced = if (replaceExisting) dao.deleteForSensor(targetSensorId) else 0

        val existing = if (replaceExisting) emptyList() else dao.getAllSync().filter { it.sensorId == targetSensorId }
        val deduped = mutableListOf<CalibrationEntity>()
        var skipped = 0
        incoming.forEach { row ->
            val duplicate = existing.any { old ->
                old.isRawMode == row.isRawMode &&
                    abs(old.timestamp - row.timestamp) <= 30_000L &&
                    abs(old.userValue - row.userValue) <= 0.05f &&
                    abs(old.sensorValue - row.sensorValue) <= 0.05f &&
                    abs(old.sensorValueRaw - row.sensorValueRaw) <= 0.05f
            } || deduped.any { added ->
                added.isRawMode == row.isRawMode &&
                    abs(added.timestamp - row.timestamp) <= 30_000L &&
                    abs(added.userValue - row.userValue) <= 0.05f &&
                    abs(added.sensorValue - row.sensorValue) <= 0.05f &&
                    abs(added.sensorValueRaw - row.sensorValueRaw) <= 0.05f
            }

            if (duplicate) {
                skipped++
            } else {
                deduped.add(row)
            }
        }

        if (deduped.isNotEmpty()) {
            dao.insertAll(deduped)
        }

        setEnabledForMode(isRawMode = true, enabled = rawEnabled)
        setEnabledForMode(isRawMode = false, enabled = autoEnabled)
        setHideInitialWhenCalibrated(hideInitialWhenCalibrated)
        setApplyToPast(applyToPast)
        setLockPastHistory(lockPastHistory)
        setOverwriteSensorValues(overwriteSensorValues)
        setVisualContinuity(visualContinuity)
        setAlgorithmForMode(isRawMode = true, algorithm = rawAlgorithm)
        setAlgorithmForMode(isRawMode = false, algorithm = autoAlgorithm)

        loadCalibrations()

        requestMirrorCalibrationSync(targetSensorId)

        return CalibrationProfileImportResult(
            sensorId = targetSensorId,
            imported = deduped.size,
            skipped = skipped,
            replaced = replaced,
            message = "Imported ${deduped.size} calibration(s) for $targetSensorId"
        )
    }
    
    suspend fun loadCalibrations() {
        if (::dao.isInitialized) {
            val list = withContext(Dispatchers.IO) { dao.getAllSync() }
            _calibrations.value = list
            invalidateComputationCache("loadCalibrations")
            UiRefreshBus.requestStatusRefresh()
        }
    }

    suspend fun addCalibration(timestamp: Long, sensorValue: Float, sensorValueRaw: Float, userValue: Float, sensorId: String? = null, isRawMode: Boolean = false) {
        val entity = CalibrationEntity(
            timestamp = timestamp,
            sensorId = sensorId ?: Natives.lastsensorname() ?: "",
            sensorValue = sensorValue,
            sensorValueRaw = sensorValueRaw,
            userValue = userValue,
            isRawMode = isRawMode
        )
        withContext(Dispatchers.IO) { dao.insert(entity) }
        loadCalibrations()
        refreshDiagnosticsPreview(isRawMode = isRawMode, force = true)
        requestMirrorCalibrationSync(entity.sensorId)
        Log.i(TAG, "Added calibration: auto=$sensorValue raw=$sensorValueRaw user=$userValue isRaw=$isRawMode at $timestamp")
    }

    suspend fun restoreCalibration(entity: CalibrationEntity) {
        withContext(Dispatchers.IO) { dao.insert(entity) }
        loadCalibrations()
        refreshDiagnosticsPreview(isRawMode = entity.isRawMode, force = true)
        requestMirrorCalibrationSync(entity.sensorId)
        Log.i(TAG, "Restored calibration: id=${entity.id}")
    }

    suspend fun deleteCalibration(entity: CalibrationEntity) {
        withContext(Dispatchers.IO) { dao.delete(entity) }
        loadCalibrations()
        refreshDiagnosticsPreview(isRawMode = entity.isRawMode, force = true)
        requestMirrorCalibrationSync(entity.sensorId)
        Log.i(TAG, "Deleted calibration: id=${entity.id}")
    }
    
    suspend fun updateCalibration(entity: CalibrationEntity) {
        withContext(Dispatchers.IO) { dao.update(entity) }
        loadCalibrations()
        refreshDiagnosticsPreview(isRawMode = entity.isRawMode, force = true)
        requestMirrorCalibrationSync(entity.sensorId)
    }
    
    suspend fun clearAll() {
        val affectedSensors = _calibrations.value.map { it.sensorId }
        withContext(Dispatchers.IO) { dao.deleteAll() }
        loadCalibrations()
        refreshDiagnosticsPreview(isRawMode = true, force = true)
        refreshDiagnosticsPreview(isRawMode = false, force = true)
        requestMirrorCalibrationSyncForSensors(affectedSensors)
        Log.i(TAG, "Cleared all calibrations")
    }

    suspend fun restoreAll(calibrations: List<CalibrationEntity>) {
        withContext(Dispatchers.IO) { dao.insertAll(calibrations) }
        loadCalibrations()
        refreshDiagnosticsPreview(isRawMode = true, force = true)
        refreshDiagnosticsPreview(isRawMode = false, force = true)
        requestMirrorCalibrationSyncForSensors(calibrations.map { it.sensorId })
        Log.i(TAG, "Restored ${calibrations.size} calibrations")
    }

    fun importProfileFromJsonBlocking(
        json: String,
        replaceExisting: Boolean,
        overrideSensorId: String? = null
    ): CalibrationProfileImportResult = runBlocking {
        importProfileFromJson(json, replaceExisting, overrideSensorId)
    }

    fun importMirrorProfileFromJsonBlocking(
        json: String,
        overrideSensorId: String? = null
    ): CalibrationProfileImportResult = withoutMirrorSync {
        runBlocking {
            importProfileFromJson(json, replaceExisting = true, overrideSensorId = overrideSensorId)
        }
    }

    /**
     * Apply calibration to a glucose value.
     * Mode-specific: only applies calibrations made in the same mode (Raw or Auto).
     * Uses the selected per-mode algorithm.
     */
    @JvmOverloads
    fun getCalibratedValue(
        value: Float,
        timestamp: Long,
        isRawMode: Boolean,
        emitDiagnostics: Boolean = false,
        sensorIdOverride: String? = null
    ): Float {
        if (!isEnabledForMode(isRawMode)) {
            return value
        }
        // Missing/invalid inputs must stay missing. Calibrating 0/NaN can produce
        // synthetic values that look real (e.g. in raw mode when raw is unavailable).
        if (!value.isFinite() || value <= 0f) {
            return value
        }

        val currentSensor = normalizeSensorId(sensorIdOverride ?: Natives.lastsensorname())
        val algorithm = getAlgorithmForMode(isRawMode)
        val cacheKey = CalibrationCacheKey(
            isRawMode = isRawMode,
            sensorId = currentSensor,
            algorithm = algorithm,
            timestamp = timestamp,
            quantizedValue = java.lang.Float.floatToRawIntBits(value),
            revision = calibrationRevision
        )

        synchronized(calibrationCache) {
            calibrationCache[cacheKey]
        }?.let { cached ->
            if (emitDiagnostics) {
                refreshDiagnosticsPreview(
                    isRawMode = isRawMode,
                    targetValue = value,
                    targetTimestamp = timestamp,
                    force = true
                )
            }
            return cached
        }

        val allPoints = getValidPointsForSensor(isRawMode = isRawMode, sensorIdOverride = currentSensor)
        if (allPoints.isEmpty()) {
            return value
        }
        val points = if (_lockPastHistory.value) {
            val earliestPoint = allPoints.minByOrNull { it.timestamp }
            allPoints
                .filter { it.timestamp <= timestamp }
                .ifEmpty {
                    if (_applyToPast.value && earliestPoint != null) {
                        listOf(earliestPoint)
                    } else {
                        emptyList()
                    }
                }
        } else {
            allPoints
        }
        if (points.isEmpty()) return value

        val computation = computeAlgorithm(
            algorithm = algorithm,
            targetValue = value.toDouble(),
            targetTimestamp = timestamp,
            points = points
        )

        if (emitDiagnostics) {
            emitDiagnostics(
                isRawMode = isRawMode,
                diagnostics = buildDiagnostics(
                    algorithm = algorithm,
                    pointCount = points.size,
                    computation = computation,
                    targetValue = value,
                    targetTimestamp = timestamp
                ),
                force = false
            )
        }

        val calibrated = sanitizeCalibratedValue(computation.prediction, value)
        val finalValue = if (_applyToPast.value) {
            calibrated
        } else {
            applyPastPolicy(
                originalValue = value,
                calibratedValue = calibrated,
                targetTimestamp = timestamp,
                points = points
            )
        }
        synchronized(calibrationCache) {
            calibrationCache[cacheKey] = finalValue
        }
        return finalValue
    }

    private fun applyPastPolicy(
        originalValue: Float,
        calibratedValue: Float,
        targetTimestamp: Long,
        points: List<CalPoint>
    ): Float {
        val firstCalibrationTs = points.minOfOrNull { it.timestamp } ?: return calibratedValue
        if (targetTimestamp >= firstCalibrationTs) return calibratedValue

        val blendStartTs = firstCalibrationTs - PAST_BLEND_WINDOW_MS
        if (targetTimestamp <= blendStartTs) return originalValue

        val blend = ((targetTimestamp - blendStartTs).toDouble() / PAST_BLEND_WINDOW_MS.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
        return originalValue + (calibratedValue - originalValue) * blend
    }

    private fun computeAlgorithm(
        algorithm: CalibrationAlgorithm,
        targetValue: Double,
        targetTimestamp: Long,
        points: List<CalPoint>
    ): AlgorithmComputation {
        if (points.size == 1) {
            val offset = points.first().y - points.first().x
            return AlgorithmComputation(
                prediction = targetValue + offset,
                offset = offset,
                anchorInfluence = 1.0,
                confidence = 1.0,
                note = "Single-point offset calibration"
            )
        }

        return when (algorithm) {
            CalibrationAlgorithm.SANE_WEIGHTED_OLS ->
                saneWeightedOls(targetValue, targetTimestamp, points)

            CalibrationAlgorithm.XDRIP_MEDIAN_SLOPE ->
                xdripMedianSlope(targetValue, targetTimestamp, points)

            CalibrationAlgorithm.TIME_WEIGHTED_ROBUST_REGRESSION ->
                timeWeightedRobustRegression(targetValue, targetTimestamp, points)

            CalibrationAlgorithm.ELASTIC_TIME_WEIGHTED_INTERPOLATION ->
                elasticTimeWeightedInterpolation(targetValue, targetTimestamp, points)

            CalibrationAlgorithm.ADAPTIVE_ENSEMBLE ->
                adaptiveEnsemble(targetValue, targetTimestamp, points)
        }
    }

    private fun buildDiagnostics(
        algorithm: CalibrationAlgorithm,
        pointCount: Int,
        computation: AlgorithmComputation,
        targetValue: Float,
        targetTimestamp: Long
    ): CalibrationDiagnostics {
        return CalibrationDiagnostics(
            algorithm = algorithm,
            pointCount = pointCount,
            slope = computation.slope?.toFloat(),
            intercept = computation.intercept?.toFloat(),
            offset = computation.offset?.toFloat(),
            anchorInfluence = computation.anchorInfluence?.toFloat(),
            confidence = computation.confidence?.toFloat(),
            targetValue = targetValue,
            targetTimestamp = targetTimestamp,
            note = computation.note
        )
    }

    fun refreshDiagnosticsPreview(
        isRawMode: Boolean,
        targetValue: Float? = null,
        targetTimestamp: Long? = null,
        force: Boolean = true
    ) {
        val sensorId = Natives.lastsensorname() ?: ""
        val points = getValidPoints(isRawMode = isRawMode, sensorId = sensorId)
        val algorithm = getAlgorithmForMode(isRawMode)

        if (!isEnabledForMode(isRawMode)) {
            emitDiagnostics(
                isRawMode = isRawMode,
                diagnostics = CalibrationDiagnostics(
                    algorithm = algorithm,
                    pointCount = points.size,
                    note = "Calibration disabled"
                ),
                force = force
            )
            return
        }

        if (points.isEmpty()) {
            emitDiagnostics(
                isRawMode = isRawMode,
                diagnostics = CalibrationDiagnostics(
                    algorithm = algorithm,
                    pointCount = 0,
                    note = "Add calibration points to see diagnostics"
                ),
                force = force
            )
            return
        }

        val latestPoint = points.maxByOrNull { it.timestamp } ?: points.last()
        val targetTs = targetTimestamp ?: latestPoint.timestamp
        val targetVal = targetValue?.toDouble() ?: latestPoint.x
        val computation = computeAlgorithm(
            algorithm = algorithm,
            targetValue = targetVal,
            targetTimestamp = targetTs,
            points = points
        )

        emitDiagnostics(
            isRawMode = isRawMode,
            diagnostics = buildDiagnostics(
                algorithm = algorithm,
                pointCount = points.size,
                computation = computation,
                targetValue = targetVal.toFloat(),
                targetTimestamp = targetTs
            ),
            force = force
        )
    }

    private fun sanitizeCalibratedValue(calibrated: Double, fallback: Float): Float {
        if (!calibrated.isFinite()) return fallback
        return calibrated.coerceIn(0.0, 1000.0).toFloat()
    }

    private fun emitDiagnostics(isRawMode: Boolean, diagnostics: CalibrationDiagnostics, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (isRawMode) {
            if (!force && now - lastDiagnosticsEmitRaw < 700L) return
            lastDiagnosticsEmitRaw = now
            _diagnosticsForRaw.value = diagnostics.copy(updatedAt = now)
        } else {
            if (!force && now - lastDiagnosticsEmitAuto < 700L) return
            lastDiagnosticsEmitAuto = now
            _diagnosticsForAuto.value = diagnostics.copy(updatedAt = now)
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    private fun temporalWeight(
        pointTimestamp: Long,
        targetTimestamp: Long,
        pastHalfLifeHours: Double,
        futureHalfLifeHours: Double
    ): Double {
        val deltaHours = (targetTimestamp - pointTimestamp) / HOUR_MS
        val ageHours = abs(deltaHours)
        val halfLife = if (deltaHours >= 0.0) pastHalfLifeHours else futureHalfLifeHours
        return 0.5.pow(ageHours / halfLife.coerceAtLeast(0.1))
    }

    private fun weightedOffset(points: List<CalPoint>, weights: List<Double>): Double {
        var sumW = 0.0
        var sumOffset = 0.0
        points.indices.forEach { idx ->
            val w = weights[idx].coerceAtLeast(0.0)
            sumW += w
            sumOffset += (points[idx].y - points[idx].x) * w
        }
        return if (sumW > 1e-9) sumOffset / sumW else points.map { it.y - it.x }.average()
    }

    private fun weightedLinearModel(
        points: List<CalPoint>,
        weights: List<Double>,
        slopeMin: Double,
        slopeMax: Double
    ): LinearModel? {
        if (points.size != weights.size || points.isEmpty()) return null

        var sumW = 0.0
        var sumWX = 0.0
        var sumWY = 0.0
        var sumWXY = 0.0
        var sumWX2 = 0.0

        points.indices.forEach { idx ->
            val w = weights[idx].coerceAtLeast(0.0)
            val x = points[idx].x
            val y = points[idx].y
            sumW += w
            sumWX += w * x
            sumWY += w * y
            sumWXY += w * x * y
            sumWX2 += w * x * x
        }

        if (sumW <= 1e-9) return null
        val denominator = sumW * sumWX2 - sumWX * sumWX
        if (abs(denominator) <= 1e-9) return null

        var slope = (sumW * sumWXY - sumWX * sumWY) / denominator
        slope = slope.coerceIn(slopeMin, slopeMax)
        val intercept = (sumWY - slope * sumWX) / sumW

        if (!slope.isFinite() || !intercept.isFinite()) return null
        return LinearModel(slope = slope, intercept = intercept)
    }

    private fun saneWeightedOls(targetValue: Double, targetTimestamp: Long, points: List<CalPoint>): AlgorithmComputation {
        val newestTimestamp = points.maxOfOrNull { it.timestamp } ?: targetTimestamp
        val weights = points.map { p ->
            var w = temporalWeight(
                pointTimestamp = p.timestamp,
                targetTimestamp = targetTimestamp,
                pastHalfLifeHours = 18.0,
                futureHalfLifeHours = 6.0
            )
            if (p.timestamp == newestTimestamp) w *= 1.25
            w
        }

        val model = weightedLinearModel(points, weights, slopeMin = 0.65, slopeMax = 1.35)
        val fallback = targetValue + weightedOffset(points, weights)
        val regression = model?.predict(targetValue) ?: fallback

        val nearest = points.minByOrNull {
            abs(it.x - targetValue) + abs(it.timestamp - targetTimestamp) / HOUR_MS
        }

        if (nearest != null) {
            val dx = abs(nearest.x - targetValue)
            val dtHours = abs(nearest.timestamp - targetTimestamp) / HOUR_MS
            val snap = (1.0 / (1.0 + dx * 2.5)) * (1.0 / (1.0 + dtHours / 8.0)) * 0.25
            val anchor = targetValue + (nearest.y - nearest.x)
            val blended = regression * (1.0 - snap) + anchor * snap
            val confidence = ((weights.maxOrNull() ?: 0.0) / weights.sum().coerceAtLeast(1e-6)).coerceIn(0.15, 1.0)
            return AlgorithmComputation(
                prediction = blended,
                slope = model?.slope,
                intercept = model?.intercept,
                offset = blended - targetValue,
                anchorInfluence = snap,
                confidence = confidence,
                note = "Recency-weighted OLS with local anchor snap"
            )
        }

        val confidence = ((weights.maxOrNull() ?: 0.0) / weights.sum().coerceAtLeast(1e-6)).coerceIn(0.15, 1.0)
        return AlgorithmComputation(
            prediction = regression,
            slope = model?.slope,
            intercept = model?.intercept,
            offset = regression - targetValue,
            anchorInfluence = 0.0,
            confidence = confidence,
            note = "Recency-weighted OLS"
        )
    }

    private fun xdripMedianSlope(targetValue: Double, targetTimestamp: Long, points: List<CalPoint>): AlgorithmComputation {
        val workingSet = points
            .sortedBy { abs(it.timestamp - targetTimestamp) }
            .take(12)
            .ifEmpty { points }

        val slopes = mutableListOf<Double>()
        for (i in 0 until workingSet.size) {
            for (j in i + 1 until workingSet.size) {
                val dx = workingSet[j].x - workingSet[i].x
                if (abs(dx) <= 1e-9) continue
                slopes.add((workingSet[j].y - workingSet[i].y) / dx)
            }
        }

        if (slopes.isEmpty()) {
            val weights = workingSet.map {
                temporalWeight(it.timestamp, targetTimestamp, pastHalfLifeHours = 18.0, futureHalfLifeHours = 6.0)
            }
            val prediction = targetValue + weightedOffset(workingSet, weights)
            return AlgorithmComputation(
                prediction = prediction,
                offset = prediction - targetValue,
                anchorInfluence = 0.0,
                confidence = 0.35,
                note = "Median slope fallback to weighted offset"
            )
        }

        val slope = median(slopes).coerceIn(0.60, 1.40)
        val intercept = median(workingSet.map { it.y - slope * it.x })
        val prediction = slope * targetValue + intercept

        return if (prediction.isFinite()) {
            AlgorithmComputation(
                prediction = prediction,
                slope = slope,
                intercept = intercept,
                offset = prediction - targetValue,
                anchorInfluence = 0.0,
                confidence = 0.70,
                note = "Theil-Sen style median slope"
            )
        } else {
            val weights = workingSet.map {
                temporalWeight(it.timestamp, targetTimestamp, pastHalfLifeHours = 18.0, futureHalfLifeHours = 6.0)
            }
            val fallback = targetValue + weightedOffset(workingSet, weights)
            AlgorithmComputation(
                prediction = fallback,
                offset = fallback - targetValue,
                anchorInfluence = 0.0,
                confidence = 0.40,
                note = "Median slope invalid, using weighted offset"
            )
        }
    }

    private fun timeWeightedRobustRegression(targetValue: Double, targetTimestamp: Long, points: List<CalPoint>): AlgorithmComputation {
        val baseWeights = points.map {
            temporalWeight(
                pointTimestamp = it.timestamp,
                targetTimestamp = targetTimestamp,
                pastHalfLifeHours = 24.0,
                futureHalfLifeHours = 8.0
            )
        }

        var weights = baseWeights.toMutableList()
        var model = weightedLinearModel(points, weights, slopeMin = 0.60, slopeMax = 1.40)
            ?: run {
                val fallback = targetValue + weightedOffset(points, baseWeights)
                return AlgorithmComputation(
                    prediction = fallback,
                    offset = fallback - targetValue,
                    anchorInfluence = 0.0,
                    confidence = 0.35,
                    note = "Robust model unavailable, using weighted offset"
                )
            }

        var finalMad = 0.05

        repeat(4) {
            val residuals = points.map { p -> p.y - model.predict(p.x) }
            val mad = median(residuals.map { abs(it) }).coerceAtLeast(0.05)
            finalMad = mad
            val scale = (mad * 1.4826).coerceAtLeast(0.05)

            weights = baseWeights.indices.map { idx ->
                val u = residuals[idx] / (1.5 * scale)
                val robustWeight = if (abs(u) <= 1.0) 1.0 else 1.0 / abs(u)
                (baseWeights[idx] * robustWeight).coerceAtLeast(1e-4)
            }.toMutableList()

            model = weightedLinearModel(points, weights, slopeMin = 0.60, slopeMax = 1.40) ?: model
        }

        val regression = model.predict(targetValue)
        val offsetFallback = targetValue + weightedOffset(points, weights)
        return if (regression.isFinite()) {
            val prediction = regression * 0.85 + offsetFallback * 0.15
            val confidence = (1.0 / (1.0 + finalMad)).coerceIn(0.20, 1.0)
            AlgorithmComputation(
                prediction = prediction,
                slope = model.slope,
                intercept = model.intercept,
                offset = prediction - targetValue,
                anchorInfluence = 0.0,
                confidence = confidence,
                note = "Huber-style robust regression"
            )
        } else {
            AlgorithmComputation(
                prediction = offsetFallback,
                offset = offsetFallback - targetValue,
                anchorInfluence = 0.0,
                confidence = 0.35,
                note = "Robust regression fallback to weighted offset"
            )
        }
    }

    private fun elasticTimeWeightedInterpolation(targetValue: Double, targetTimestamp: Long, points: List<CalPoint>): AlgorithmComputation {
        val scale = median(points.map { abs(it.x - targetValue) }).coerceAtLeast(0.5)

        val localWeights = points.map { p ->
            val timeWeight = temporalWeight(
                pointTimestamp = p.timestamp,
                targetTimestamp = targetTimestamp,
                pastHalfLifeHours = 14.0,
                futureHalfLifeHours = 5.0
            )
            val proximity = 1.0 / (1.0 + ((abs(p.x - targetValue) / scale).pow(2.0)))
            (timeWeight * proximity).coerceAtLeast(1e-4)
        }

        val localModel = weightedLinearModel(points, localWeights, slopeMin = 0.55, slopeMax = 1.45)
        val localPrediction = localModel?.predict(targetValue)
            ?: (targetValue + weightedOffset(points, localWeights))

        val globalPrediction = timeWeightedRobustRegression(targetValue, targetTimestamp, points).prediction
        val dominance = (localWeights.maxOrNull() ?: 0.0) / localWeights.sum().coerceAtLeast(1e-6)
        val alpha = (0.35 + 0.55 * dominance).coerceIn(0.35, 0.90)

        var blended = localPrediction * alpha + globalPrediction * (1.0 - alpha)
        var snapApplied = 0.0

        val nearestByValue = points.minByOrNull { abs(it.x - targetValue) }
        if (nearestByValue != null) {
            val dx = abs(nearestByValue.x - targetValue)
            val snap = (1.0 / (1.0 + dx * 3.0)) * 0.20
            snapApplied = snap
            val anchor = targetValue + (nearestByValue.y - nearestByValue.x)
            blended = blended * (1.0 - snap) + anchor * snap
        }

        return AlgorithmComputation(
            prediction = blended,
            slope = localModel?.slope,
            intercept = localModel?.intercept,
            offset = blended - targetValue,
            anchorInfluence = snapApplied,
            confidence = alpha,
            note = "Elastic local interpolation blended with robust global trend"
        )
    }

    private fun adaptiveEnsemble(targetValue: Double, targetTimestamp: Long, points: List<CalPoint>): AlgorithmComputation {
        val sane = saneWeightedOls(targetValue, targetTimestamp, points)
        val xdrip = xdripMedianSlope(targetValue, targetTimestamp, points)
        val robust = timeWeightedRobustRegression(targetValue, targetTimestamp, points)
        val elastic = elasticTimeWeightedInterpolation(targetValue, targetTimestamp, points)

        val pSane = sane.prediction
        val pXdrip = xdrip.prediction
        val pRobust = robust.prediction
        val pElastic = elastic.prediction

        val predictions = listOf(pSane, pXdrip, pRobust, pElastic)
        val center = median(predictions)
        val dispersion = predictions.map { abs(it - center) }.average().coerceAtLeast(0.01)
        val harmony = (1.0 / (1.0 + dispersion)).coerceIn(0.20, 1.00)

        val scale = median(points.map { abs(it.x - targetValue) }).coerceAtLeast(0.5)
        val localWeights = points.map { p ->
            val timeWeight = temporalWeight(
                pointTimestamp = p.timestamp,
                targetTimestamp = targetTimestamp,
                pastHalfLifeHours = 12.0,
                futureHalfLifeHours = 4.0
            )
            val proximity = 1.0 / (1.0 + ((abs(p.x - targetValue) / scale).pow(2.0)))
            (timeWeight * proximity).coerceAtLeast(1e-4)
        }
        val localDominance = (localWeights.maxOrNull() ?: 0.0) / localWeights.sum().coerceAtLeast(1e-6)

        var wSane = 0.20
        var wXdrip = 0.15
        var wRobust = 0.30
        var wElastic = 0.35 + (0.20 * localDominance)

        if (harmony < 0.50) {
            wSane *= 0.85
            wXdrip *= 0.70
            wRobust *= 1.10
            wElastic *= 1.10
        }

        val sumW = wSane + wXdrip + wRobust + wElastic
        var blended = (
            pSane * wSane +
                pXdrip * wXdrip +
                pRobust * wRobust +
                pElastic * wElastic
            ) / sumW
        var snapApplied = 0.0

        val nearest = points.minByOrNull {
            abs(it.x - targetValue) + abs(it.timestamp - targetTimestamp) / HOUR_MS
        }
        if (nearest != null) {
            val dx = abs(nearest.x - targetValue)
            val dtHours = abs(nearest.timestamp - targetTimestamp) / HOUR_MS
            val snap = (1.0 / (1.0 + dx * 2.0)) * (1.0 / (1.0 + dtHours / 6.0)) * 0.35
            snapApplied = snap
            val anchor = targetValue + (nearest.y - nearest.x)
            blended = blended * (1.0 - snap) + anchor * snap
        }

        return AlgorithmComputation(
            prediction = blended,
            slope = robust.slope ?: elastic.slope,
            intercept = robust.intercept ?: elastic.intercept,
            offset = blended - targetValue,
            anchorInfluence = snapApplied,
            confidence = harmony,
            note = "Adaptive ensemble blend of sane/xDrip/robust/elastic"
        )
    }
    
    /**
     * Check if there's active calibration for the given mode.
     */
    fun hasActiveCalibration(isRawMode: Boolean): Boolean {
        return hasActiveCalibration(isRawMode, null)
    }

    fun hasActiveCalibration(isRawMode: Boolean, sensorIdOverride: String? = null): Boolean {
        if (!isEnabledForMode(isRawMode)) return false

        return getValidPointsForSensor(isRawMode, sensorIdOverride).isNotEmpty()
    }

    /** Check if an enabled calibration was added at this timestamp (±30s tolerance) for given mode */
    fun hasCalibrationAt(timestamp: Long, isRawMode: Boolean): Boolean {
        if (!isEnabledForMode(isRawMode)) return false
        val currentSensor = Natives.lastsensorname() ?: ""
        return _calibrations.value.any { cal ->
            cal.isEnabled &&
            cal.isRawMode == isRawMode &&
            (cal.sensorId == currentSensor || cal.sensorId.isEmpty()) &&
            kotlin.math.abs(cal.timestamp - timestamp) <= 30_000L
        }
    }
    
    /** Get calibration at timestamp for editing (±30s tolerance) */
    fun getCalibrationAt(timestamp: Long, isRawMode: Boolean): CalibrationEntity? {
        val currentSensor = Natives.lastsensorname() ?: ""
        return _calibrations.value.find { cal ->
            cal.isRawMode == isRawMode &&
            (cal.sensorId == currentSensor || cal.sensorId.isEmpty()) &&
            kotlin.math.abs(cal.timestamp - timestamp) <= 30_000L
        }
    }
    
    /** Get visible calibrations for chart display (only enabled, matching mode and sensor) */
    fun getVisibleCalibrations(isRawMode: Boolean): List<CalibrationEntity> {
        if (!isEnabledForMode(isRawMode)) return emptyList()
        val currentSensor = Natives.lastsensorname() ?: ""
        return _calibrations.value.filter { cal ->
            cal.isEnabled &&
            cal.isRawMode == isRawMode &&
            (cal.sensorId == currentSensor || cal.sensorId.isEmpty())
        }
    }

    fun getCachedCalibrations(): List<CalibrationEntity> = _calibrations.value
    
    // Kept for backward compatibility if needed, but redundant now
    fun getCalibrationsFlow(): Flow<List<CalibrationEntity>> = _calibrations
}
