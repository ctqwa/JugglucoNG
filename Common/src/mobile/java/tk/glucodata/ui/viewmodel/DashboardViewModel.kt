package tk.glucodata.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import tk.glucodata.Natives
import tk.glucodata.UiRefreshBus
import tk.glucodata.BatteryTrace
import tk.glucodata.CurrentDisplaySource
import tk.glucodata.DataSmoothing
import tk.glucodata.Notify
import tk.glucodata.SensorIdentity
import tk.glucodata.data.GlucoseRepository
import tk.glucodata.data.HistorySync
import tk.glucodata.alerts.AlertRepository
import tk.glucodata.alerts.CustomAlertRepository
import tk.glucodata.drivers.ManagedSensorRuntime
import tk.glucodata.drivers.ManagedSensorStatusPolicy
import tk.glucodata.ui.util.resolveDashboardSensorStatus

class DashboardViewModel(
    private val glucoseRepository: GlucoseRepository = GlucoseRepository()
) : ViewModel() {
    private data class HistoryEdgeSignature(
        val size: Int,
        val firstTimestamp: Long,
        val lastTimestamp: Long,
        val sampleHash: Int,
        val lastValueBits: Int,
        val lastRawBits: Int,
        val lastSerial: String?
    )

    private companion object {
        const val TARGET_RANGE_DEFAULTS_MIGRATION_KEY = "target_range_defaults_v2"
        const val UI_RECOVERY_SYNC_MIN_INTERVAL_MS = 30_000L
        const val HISTORY_RECOVERY_TOLERANCE_MS = 5L * 60L * 1000L
        const val HISTORY_RECOVERY_TAIL_TOLERANCE_MS = 2L * 60L * 1000L
        const val DASHBOARD_HISTORY_WINDOW_MS = 3L * 24L * 60L * 60L * 1000L
    }

    enum class CollectionMode {
        INACTIVE,
        DASHBOARD,
        FULL_HISTORY
    }

    private val _currentGlucose = MutableStateFlow("---")
    val currentGlucose = _currentGlucose.asStateFlow()

    @Volatile
    private var lastUiRecoverySyncAtMs = 0L
    @Volatile
    private var lastHistoryRecoverySyncAtMs = 0L
    @Volatile
    private var lastHistoryRecoverySerial: String? = null

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _currentRate = MutableStateFlow(0f)
    val currentRate = _currentRate.asStateFlow()

    private val _sensorName = MutableStateFlow("")
    val sensorName = _sensorName.asStateFlow()

    private val _activeSensorList = MutableStateFlow<List<String>>(emptyList())
    val activeSensorList = _activeSensorList.asStateFlow()

    private val _sensorStatus = MutableStateFlow("")
    val sensorStatus = _sensorStatus.asStateFlow()

    private val _daysRemaining = MutableStateFlow("")
    val daysRemaining = _daysRemaining.asStateFlow()

    private val _sensorProgress = MutableStateFlow(0f)
    val sensorProgress = _sensorProgress.asStateFlow()

    private val _xDripBroadcastEnabled = MutableStateFlow(false)
    val xDripBroadcastEnabled = _xDripBroadcastEnabled.asStateFlow()

    private val _patchedLibreBroadcastEnabled = MutableStateFlow(false)
    val patchedLibreBroadcastEnabled = _patchedLibreBroadcastEnabled.asStateFlow()

    private val _glucodataBroadcastEnabled = MutableStateFlow(false)
    val glucodataBroadcastEnabled = _glucodataBroadcastEnabled.asStateFlow()

    private val _glucoseHistory = MutableStateFlow<List<tk.glucodata.ui.GlucosePoint>>(emptyList())
    val glucoseHistory = _glucoseHistory.asStateFlow()

    private val _unit = MutableStateFlow("mg/dL")
    val unit = _unit.asStateFlow()

    private val _targetLow = MutableStateFlow(70f)
    val targetLow = _targetLow.asStateFlow()

    private val _targetHigh = MutableStateFlow(180f)
    val targetHigh = _targetHigh.asStateFlow()

    private val _viewMode = MutableStateFlow(0)
    val viewMode = _viewMode.asStateFlow()

    private val _sensorHoursRemaining = MutableStateFlow(999L)
    val sensorHoursRemaining = _sensorHoursRemaining.asStateFlow()

    private val _currentDay = MutableStateFlow(0)
    val currentDay = _currentDay.asStateFlow()

    // Alarm States
    private val _hasLowAlarm = MutableStateFlow(false)
    val hasLowAlarm = _hasLowAlarm.asStateFlow()

    private val _lowAlarmThreshold = MutableStateFlow(0f)
    val lowAlarmThreshold = _lowAlarmThreshold.asStateFlow()

    private val _hasHighAlarm = MutableStateFlow(false)
    val hasHighAlarm = _hasHighAlarm.asStateFlow()

    private val _highAlarmThreshold = MutableStateFlow(0f)
    val highAlarmThreshold = _highAlarmThreshold.asStateFlow()

    // New Setting: Notification Chart Toggle
    private val _notificationChartEnabled = MutableStateFlow(true)
    val notificationChartEnabled = _notificationChartEnabled.asStateFlow()

    private val _chartSmoothingMinutes = MutableStateFlow(0)
    val chartSmoothingMinutes = _chartSmoothingMinutes.asStateFlow()

    private val _dataSmoothingGraphOnly = MutableStateFlow(true)
    val dataSmoothingGraphOnly = _dataSmoothingGraphOnly.asStateFlow()

    private val _dataSmoothingCollapseChunks = MutableStateFlow(false)
    val dataSmoothingCollapseChunks = _dataSmoothingCollapseChunks.asStateFlow()

    private val _previewWindowMode = MutableStateFlow(0)
    val previewWindowMode = _previewWindowMode.asStateFlow()

    private val _lowAlarmSoundMode = MutableStateFlow(0)
    val lowAlarmSoundMode = _lowAlarmSoundMode.asStateFlow()

    private val _highAlarmSoundMode = MutableStateFlow(0)
    val highAlarmSoundMode = _highAlarmSoundMode.asStateFlow()

    private val _alertsSummary = MutableStateFlow("")
    val alertsSummary = _alertsSummary.asStateFlow()

    private var collectionMode = CollectionMode.INACTIVE
    private var currentReadingJob: Job? = null
    private var historyJob: Job? = null
    private var uiRefreshJob: Job? = null
    private var activeHistoryStartTimeMs: Long? = null

    init {
        // Keep initial UI boot light. Room backfill/targeted sensor sync now cover cold start,
        // so do not force a full native history rebuild during app startup.
        refreshData()
    }
    
    /**
     * Called when the app resumes from background.
     * Refreshes data to prevent stale chart state after Home button.
     * Also updates the sensor serial in GlucoseRepository so flows
     * re-subscribe to the correct sensor's data.
     */
    fun onResume() {
        refreshData()
        if (collectionMode != CollectionMode.INACTIVE) {
            ensureUiRefreshCollection()
            ensureCurrentReadingCollection()
            startHistoryCollectionForMode(collectionMode)
            viewModelScope.launch {
                requestUiRecoverySync()
            }
        }
    }

    fun setCollectionMode(mode: CollectionMode) {
        if (collectionMode == mode) return
        collectionMode = mode
        when (mode) {
            CollectionMode.INACTIVE -> stopCollectionJobs()
            CollectionMode.DASHBOARD,
            CollectionMode.FULL_HISTORY -> {
                refreshData()
                ensureUiRefreshCollection()
                ensureCurrentReadingCollection()
                startHistoryCollectionForMode(mode)
                viewModelScope.launch {
                    requestUiRecoverySync()
                }
            }
        }
    }

    private suspend fun requestUiRecoverySync() {
        val nowMs = SystemClock.elapsedRealtime()
        synchronized(this) {
            if ((nowMs - lastUiRecoverySyncAtMs) < UI_RECOVERY_SYNC_MIN_INTERVAL_MS) {
                android.util.Log.d(
                    "DashboardVM",
                    "requestUiRecoverySync skipped — last run was ${(nowMs - lastUiRecoverySyncAtMs)}ms ago"
                )
                return
            }
            lastUiRecoverySyncAtMs = nowMs
        }
        val serial = preferredDashboardSensorId()?.takeIf { it.isNotBlank() }
        val historyStartTimeMs = activeHistoryStartTimeMs
        val current = resolveCurrentForHistoryRecovery(serial)
        val shouldPreferHistoryRecovery = serial != null &&
            historyStartTimeMs != null &&
            shouldRequestHistoryRecovery(historyStartTimeMs, _glucoseHistory.value, serial, current)

        if (!shouldPreferHistoryRecovery) {
            glucoseRepository.syncLatestNativeReadingOnce()
        }

        if (shouldPreferHistoryRecovery) {
            requestHistoryRecoverySync(serial, reason = "ui_recovery")
        }
    }

    private fun ensureUiRefreshCollection() {
        if (uiRefreshJob?.isActive == true) return
        uiRefreshJob = viewModelScope.launch {
            UiRefreshBus.events.collect { event ->
                when (event) {
                    UiRefreshBus.Event.DataChanged -> refreshData()
                    UiRefreshBus.Event.StatusOnly -> refreshStatusOnly()
                }
            }
        }
    }

    private fun ensureCurrentReadingCollection() {
        if (currentReadingJob?.isActive == true) return
        currentReadingJob = viewModelScope.launch {
            glucoseRepository.getCurrentReading().collect { point ->
                val preferredSensorId = preferredDashboardSensorId()
                val resolved = CurrentDisplaySource.resolveCurrent(
                    maxAgeMillis = Notify.glucosetimeout,
                    preferredSensorId = preferredSensorId
                )
                if (resolved != null) {
                    _currentGlucose.value = resolved.primaryStr
                    _currentRate.value = resolved.rate.takeIf { it.isFinite() } ?: 0f
                    return@collect
                }
                if (point != null) {
                    val valueToDisplay = if (viewMode.value == 1 || viewMode.value == 3) point.rawValue else point.value
                    _currentGlucose.value = if (valueToDisplay < 30) String.format("%.1f", valueToDisplay) else valueToDisplay.toInt().toString()
                    _currentRate.value = point.rate ?: 0f
                    // Don't append to _glucoseHistory here — the Room Flow in
                    // startHistoryCollectionForMode() handles it. Appending here caused
                    // a triple-write race (append + 24h Flow + full Flow) that
                    // triggered redundant full-screen recompositions.
                }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            refreshDashboardSettings()
            refreshSensorSnapshot()
            refreshCurrentDisplaySnapshot()
        }
    }

    private fun refreshStatusOnly() {
        viewModelScope.launch {
            refreshSensorSnapshot()
            refreshCurrentDisplaySnapshot()
        }
    }

    private fun refreshDashboardSettings() {
        val unitVal = Natives.getunit()
        val isMmol = unitVal == 1
        _unit.value = if (isMmol) "mmol/L" else "mg/dL"

        val context = tk.glucodata.Applic.app
        val prefs = context.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
        migrateTargetRangeDefaultsIfNeeded(prefs, isMmol)
        _notificationChartEnabled.value = prefs.getBoolean("notification_chart_enabled", true)
        _chartSmoothingMinutes.value = DataSmoothing.getMinutes(context)
        _dataSmoothingGraphOnly.value = DataSmoothing.isGraphOnly(context)
        _dataSmoothingCollapseChunks.value = DataSmoothing.collapseChunks(context)
        _previewWindowMode.value = prefs.getInt("dashboard_chart_preview_window_mode", 0)

        _targetLow.value = Natives.targetlow()
        _targetHigh.value = Natives.targethigh()
        _xDripBroadcastEnabled.value = Natives.getxbroadcast()
        _patchedLibreBroadcastEnabled.value = Natives.getlibrelinkused()
        _glucodataBroadcastEnabled.value = Natives.getJugglucobroadcast()

        _hasLowAlarm.value = Natives.hasalarmlow()
        _lowAlarmThreshold.value = Natives.alarmlow()
        _hasHighAlarm.value = Natives.hasalarmhigh()
        _highAlarmThreshold.value = Natives.alarmhigh()

        _lowAlarmSoundMode.value = if (Natives.alarmhassound(0)) 1 else 0
        _highAlarmSoundMode.value = if (Natives.alarmhassound(1)) 1 else 0

        val anyActive = AlertRepository.loadAllConfigs().any { it.enabled }
            || CustomAlertRepository.getAll().any { it.enabled }
        _alertsSummary.value = if (anyActive) "Active" else "All Alerts Disabled"
    }

    private fun refreshSensorSnapshot() {
        var sName = SensorIdentity.resolveAppSensorId(Natives.lastsensorname())
        val activeSensors = Natives.activeSensors()

        if (activeSensors != null && activeSensors.isNotEmpty()) {
            _activeSensorList.value = activeSensors
                .mapNotNull { SensorIdentity.resolveAppSensorId(it) ?: it }
                .distinct()
        } else {
            _activeSensorList.value = emptyList()
        }

        val cachedSerial = _sensorName.value.takeIf { it.isNotBlank() }
            ?: glucoseRepository.currentSerial.value.takeIf { it.isNotBlank() }
        val fallbackSerial = SensorIdentity.resolveAvailableMainSensor(
            selectedMain = sName,
            preferredSensorId = cachedSerial,
            activeSensors = activeSensors
        ) ?: cachedSerial

        if (sName.isNullOrBlank()) {
            sName = fallbackSerial
        }

        if (!sName.isNullOrEmpty() && sName.isNotBlank()) {
            glucoseRepository.refreshSensorSerial(sName)
            _sensorName.value = sName
            val nativeStatus = try {
                Natives.getSensorStatusByName(sName).orEmpty()
            } catch (t: Throwable) {
                android.util.Log.e("DashboardVM", "getSensorStatusByName failed for '$sName'", t)
                ""
            }
            val snapshot = try {
                Natives.getSensorUiSnapshot(sName)
            } catch (t: Throwable) {
                android.util.Log.e("DashboardVM", "getSensorUiSnapshot failed for '$sName'", t)
                null
            }
            val managedSnapshot = ManagedSensorRuntime.resolveUiSnapshot(sName, sName)
            if (snapshot != null && snapshot.size >= 5) {
                val sensorKind = snapshot[0].toInt()
                val vm = snapshot[1].toInt()
                val startMsec = snapshot[2]
                val expectedEnd = snapshot[3]
                val officialEnd = snapshot[4]
                _sensorStatus.value = resolveDashboardSensorStatus(sName, sensorKind, startMsec, nativeStatus)

                _viewMode.value = managedSnapshot?.viewMode ?: vm

                val lifecycle = ManagedSensorStatusPolicy.resolveLifecycleSummary(
                    startTimeMs = managedSnapshot?.startTimeMs ?: startMsec,
                    officialEndMs = managedSnapshot?.officialEndMs ?: officialEnd,
                    expectedEndMs = managedSnapshot?.expectedEndMs ?: expectedEnd,
                    sensorRemainingHours = managedSnapshot?.sensorRemainingHours ?: -1,
                    sensorAgeHours = managedSnapshot?.sensorAgeHours ?: -1,
                    nowMs = System.currentTimeMillis()
                )
                _sensorProgress.value = lifecycle.progress
                _sensorHoursRemaining.value = lifecycle.remainingHours
                _daysRemaining.value = lifecycle.daysText
                _currentDay.value = lifecycle.currentDay
            } else {
                _sensorStatus.value = resolveDashboardSensorStatus(sName, nativeStatus)
                _viewMode.value = managedSnapshot?.viewMode ?: 0
                val lifecycle = ManagedSensorStatusPolicy.resolveLifecycleSummary(
                    startTimeMs = managedSnapshot?.startTimeMs ?: 0L,
                    officialEndMs = managedSnapshot?.officialEndMs ?: 0L,
                    expectedEndMs = managedSnapshot?.expectedEndMs ?: 0L,
                    sensorRemainingHours = managedSnapshot?.sensorRemainingHours ?: -1,
                    sensorAgeHours = managedSnapshot?.sensorAgeHours ?: -1,
                    nowMs = System.currentTimeMillis()
                )
                _sensorProgress.value = lifecycle.progress
                _sensorHoursRemaining.value = lifecycle.remainingHours
                _daysRemaining.value = lifecycle.daysText
                _currentDay.value = lifecycle.currentDay
            }
        } else {
            _sensorName.value = ""
            _sensorStatus.value = ""
            _viewMode.value = 0
            _sensorProgress.value = 0f
            _sensorHoursRemaining.value = 999L
            _daysRemaining.value = ""
        }
    }

    private fun refreshCurrentDisplaySnapshot() {
        refreshCurrentDisplayAfterSmoothingChange()
    }

    private fun startHistoryCollectionForMode(mode: CollectionMode) {
        val nowMs = System.currentTimeMillis()
        val recoveryStartTimeMs = when (mode) {
            CollectionMode.INACTIVE -> return
            CollectionMode.DASHBOARD -> (nowMs - DASHBOARD_HISTORY_WINDOW_MS).coerceAtLeast(0L)
            CollectionMode.FULL_HISTORY -> 0L
        }
        val queryStartTimeMs = when (mode) {
            CollectionMode.INACTIVE -> return
            CollectionMode.DASHBOARD,
            CollectionMode.FULL_HISTORY -> 0L
        }

        if (historyJob?.isActive == true && activeHistoryStartTimeMs == recoveryStartTimeMs) return

        historyJob?.cancel()
        activeHistoryStartTimeMs = recoveryStartTimeMs
        _isLoading.value = _glucoseHistory.value.isEmpty()

        historyJob = viewModelScope.launch {
            var lastRecoveryRequestSerial: String? = null
            combine(
                _unit,
                glucoseRepository.getHistoryFlowRaw(queryStartTimeMs)
                    .distinctUntilChangedBy(::historyEdgeSignature)
            ) { unitStr, rawHistory ->
                unitStr to rawHistory
            }.collect { (unitStr, rawHistory) ->
                val preferredSerial = preferredDashboardSensorId()?.takeIf { it.isNotBlank() }
                val current = resolveCurrentForHistoryRecovery(preferredSerial)
                if (preferredSerial != null &&
                    shouldRequestHistoryRecovery(recoveryStartTimeMs, rawHistory, preferredSerial, current) &&
                    lastRecoveryRequestSerial != preferredSerial
                ) {
                    lastRecoveryRequestSerial = preferredSerial
                    requestHistoryRecoverySync(
                        serial = preferredSerial,
                        reason = "history_flow_${mode.name.lowercase()}_${rawHistory.size}"
                    )
                }
                BatteryTrace.bump(
                    key = "dashboard.history.emission",
                    logEvery = 20L,
                    detail = "mode=$mode size=${rawHistory.size}"
                )
                val isMmol = unitStr == "mmol/L"
                _glucoseHistory.value = if (isMmol) {
                    rawHistory.map { p ->
                        val v = p.value / 18.0182f
                        val r = p.rawValue / 18.0182f
                        tk.glucodata.ui.GlucosePoint(v, p.time, p.timestamp, r, p.rate, p.sensorSerial)
                    }
                } else {
                    rawHistory
                }
                _isLoading.value = false
            }
        }
    }

    private fun historyEdgeSignature(points: List<tk.glucodata.ui.GlucosePoint>): HistoryEdgeSignature {
        val first = points.firstOrNull()
        val last = points.lastOrNull()
        return HistoryEdgeSignature(
            size = points.size,
            firstTimestamp = first?.timestamp ?: 0L,
            lastTimestamp = last?.timestamp ?: 0L,
            sampleHash = sparseHistorySampleHash(points),
            lastValueBits = java.lang.Float.floatToRawIntBits(last?.value ?: 0f),
            lastRawBits = java.lang.Float.floatToRawIntBits(last?.rawValue ?: 0f),
            lastSerial = last?.sensorSerial
        )
    }

    private fun sparseHistorySampleHash(points: List<tk.glucodata.ui.GlucosePoint>): Int {
        if (points.isEmpty()) return 0
        val sampleCount = minOf(points.size, 8)
        var hash = 1
        for (sampleIndex in 0 until sampleCount) {
            val pointIndex = ((points.lastIndex.toLong() * sampleIndex) / (sampleCount - 1).coerceAtLeast(1)).toInt()
            val point = points[pointIndex]
            hash = 31 * hash + point.timestamp.hashCode()
            hash = 31 * hash + java.lang.Float.floatToRawIntBits(point.value)
            hash = 31 * hash + java.lang.Float.floatToRawIntBits(point.rawValue)
            hash = 31 * hash + (point.sensorSerial?.hashCode() ?: 0)
        }
        return hash
    }

    private fun stopCollectionJobs() {
        currentReadingJob?.cancel()
        currentReadingJob = null
        historyJob?.cancel()
        historyJob = null
        uiRefreshJob?.cancel()
        uiRefreshJob = null
        activeHistoryStartTimeMs = null
    }

    fun setLowAlarm(enabled: Boolean, threshold: Float) {
        // Natives.alarmhigh() returns value in User Unit
        val highThreshold = Natives.alarmhigh()
        val highEnabled = Natives.hasalarmhigh()
        val loss = Natives.hasalarmloss()
        
        // Natives.setalarms expects User Units
        Natives.setalarms(threshold, highThreshold, enabled, highEnabled, false, loss)
        refreshData()
    }

    fun setHighAlarm(enabled: Boolean, threshold: Float) {
        // Natives.alarmlow() returns value in User Unit
        val lowThreshold = Natives.alarmlow()
        val lowEnabled = Natives.hasalarmlow()
        val loss = Natives.hasalarmloss()
        
        Natives.setalarms(lowThreshold, threshold, lowEnabled, enabled, false, loss)
        refreshData()
    }

    fun setAlarmSound(type: Int, mode: Int) {
        // mode: 0 = Vibrate Only, 1 = Sound (System)
        // type: 0 = Low, 1 = High
        val flash = Natives.alarmhasflash(type)
        val sound = mode == 1
        val vibration = true // Always vibrate for now, or could depend on mode
        
        // Passing "" as uri to use default/clear custom
        Natives.writering(type, "", sound, flash, vibration)
        refreshData()
    }

    fun setUnit(mode: Int) {
        val app = tk.glucodata.Applic.app
        app.setunit(mode)
        
        // Force immediate state update to trigger UI flow instantly
        _unit.value = if (mode == 1) "mmol/L" else "mg/dL"
        refreshData()
    }
    
    fun setTargetLow(value: Float) {
        // Natives.targethigh() returns value in User Unit
        val high = Natives.targethigh()
        Natives.setTargetRange(value, high)
        refreshData()
    }

    fun setTargetHigh(value: Float) {
        // Natives.targetlow() returns value in User Unit
        val low = Natives.targetlow()
        Natives.setTargetRange(low, value)
        refreshData()
    }

    fun toggleXDripBroadcast(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        if (enabled) {
             val intent = android.content.Intent("com.eveningoutpost.dexdrip.BgEstimate")
             val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
             val names = receivers.mapNotNull { it.activityInfo?.packageName }.toTypedArray()
             Natives.setxdripRecepters(names)
             tk.glucodata.SendLikexDrip.setreceivers()
        } else {
             Natives.setxdripRecepters(emptyArray())
             tk.glucodata.SendLikexDrip.setreceivers()
        }
        _xDripBroadcastEnabled.value = Natives.getxbroadcast()
    }

    fun togglePatchedLibreBroadcast(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        if (enabled) {
             val intent = android.content.Intent("com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING")
             val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
             val names = receivers.mapNotNull { it.activityInfo?.packageName }.toTypedArray()
             Natives.setlibrelinkRecepters(names)
             tk.glucodata.XInfuus.setlibrenames()
        } else {
             Natives.setlibrelinkRecepters(emptyArray())
             tk.glucodata.XInfuus.setlibrenames()
        }
        _patchedLibreBroadcastEnabled.value = Natives.getlibrelinkused()
    }

    fun toggleGlucodataBroadcast(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        if (enabled) {
             val intent = android.content.Intent("glucodata.Minute")
             val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
             val names = receivers.mapNotNull { it.activityInfo?.packageName }.toTypedArray()
             Natives.setglucodataRecepters(names)
             tk.glucodata.JugglucoSend.setreceivers()
        } else {
             Natives.setglucodataRecepters(emptyArray())
             tk.glucodata.JugglucoSend.setreceivers()
        }
        _glucodataBroadcastEnabled.value = Natives.getJugglucobroadcast()
    }

    fun toggleNotificationChart(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        val prefs = context.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notification_chart_enabled", enabled).apply()
        _notificationChartEnabled.value = enabled
        
        // Force update notification to reflect change immediately
        tk.glucodata.Notify.showoldglucose()
    }

    fun setChartSmoothingMinutes(minutes: Int) {
        val context = tk.glucodata.Applic.app
        val sanitized = DataSmoothing.sanitizeMinutes(minutes)
        DataSmoothing.setMinutes(context, sanitized)
        _chartSmoothingMinutes.value = sanitized
        refreshCurrentDisplayAfterSmoothingChange()
    }

    fun setDataSmoothingEnabled(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        DataSmoothing.setEnabled(context, enabled)
        _chartSmoothingMinutes.value = DataSmoothing.getMinutes(context)
        refreshCurrentDisplayAfterSmoothingChange()
    }

    fun setDataSmoothingGraphOnly(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        DataSmoothing.setGraphOnly(context, enabled)
        _dataSmoothingGraphOnly.value = enabled
        refreshCurrentDisplayAfterSmoothingChange()
    }

    fun setDataSmoothingCollapseChunks(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        DataSmoothing.setCollapseChunks(context, enabled)
        _dataSmoothingCollapseChunks.value = enabled
        refreshCurrentDisplayAfterSmoothingChange()
    }

    fun setPreviewWindowMode(mode: Int) {
        val sanitized = mode.coerceIn(0, 2)
        val context = tk.glucodata.Applic.app
        val prefs = context.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("dashboard_chart_preview_window_mode", sanitized).apply()
        _previewWindowMode.value = sanitized
    }

    // Floating Glucose Logic
    val floatingRepository = tk.glucodata.data.settings.FloatingSettingsRepository(tk.glucodata.Applic.app)

    fun toggleFloatingGlucose(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        floatingRepository.setEnabled(enabled)
        
        val intent = android.content.Intent(context, tk.glucodata.service.FloatingGlucoseService::class.java)
        if (enabled) {
            // Check permission before starting? Service will likely fail or just not show if no permission.
            // We assume UI handles permission check.
           try {
               context.startService(intent)
               // Disable native floating to avoid duplication
               Natives.setfloatglucose(false) 
           } catch (e: Exception) {
               android.util.Log.e("DashboardVM", "Failed to start floating service", e)
           }
        } else {
            context.stopService(intent)
        }
    }

    private fun migrateTargetRangeDefaultsIfNeeded(
        prefs: android.content.SharedPreferences,
        isMmol: Boolean
    ) {
        if (prefs.getBoolean(TARGET_RANGE_DEFAULTS_MIGRATION_KEY, false)) {
            return
        }

        val currentLow = Natives.targetlow()
        val currentHigh = Natives.targethigh()
        val oldLow = if (isMmol) 3.9f else 70f
        val oldHigh = if (isMmol) 10.0f else 180f

        if (kotlin.math.abs(currentLow - oldLow) < 0.11f && kotlin.math.abs(currentHigh - oldHigh) < 0.11f) {
            Natives.setTargetRange(
                if (isMmol) 3.6f else 65f,
                if (isMmol) 9.0f else 162f
            )
        }

        prefs.edit().putBoolean(TARGET_RANGE_DEFAULTS_MIGRATION_KEY, true).apply()
    }

    private fun refreshCurrentDisplayAfterSmoothingChange() {
        CurrentDisplaySource.resolveCurrent(
            maxAgeMillis = Notify.glucosetimeout,
            preferredSensorId = preferredDashboardSensorId()
        )?.let { resolved ->
            _currentGlucose.value = resolved.primaryStr
            _currentRate.value = resolved.rate.takeIf { it.isFinite() } ?: 0f
        }
    }

    private fun requestHistoryRecoverySync(serial: String, reason: String) {
        if (!SensorIdentity.shouldUseNativeHistorySync(serial)) {
            return
        }
        val nowMs = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (serial == lastHistoryRecoverySerial &&
                (nowMs - lastHistoryRecoverySyncAtMs) < UI_RECOVERY_SYNC_MIN_INTERVAL_MS
            ) {
                return
            }
            lastHistoryRecoverySerial = serial
            lastHistoryRecoverySyncAtMs = nowMs
        }
        BatteryTrace.bump(
            key = "dashboard.history.recovery.request",
            logEvery = 20L,
            detail = "serial=$serial reason=$reason"
        )
        HistorySync.syncSensorFromNative(serial, forceFull = false)
    }

    private fun shouldRequestHistoryRecovery(
        startTimeMs: Long,
        history: List<tk.glucodata.ui.GlucosePoint>,
        serial: String?,
        current: CurrentDisplaySource.Snapshot?
    ): Boolean {
        if (history.isEmpty()) {
            return true
        }
        val oldestTimestamp = history.firstOrNull()?.timestamp ?: return true
        if (startTimeMs > 0L && oldestTimestamp > (startTimeMs + HISTORY_RECOVERY_TOLERANCE_MS)) {
            return true
        }
        val latestTimestamp = history.lastOrNull()?.timestamp ?: return true
        if (current == null || current.timeMillis <= 0L || serial.isNullOrBlank()) {
            return false
        }
        if (!current.sensorId.isNullOrBlank() && !SensorIdentity.matches(current.sensorId, serial)) {
            return false
        }
        return current.timeMillis > (latestTimestamp + HISTORY_RECOVERY_TAIL_TOLERANCE_MS)
    }

    private fun resolveCurrentForHistoryRecovery(serial: String?): CurrentDisplaySource.Snapshot? {
        if (serial.isNullOrBlank()) return null
        return CurrentDisplaySource.resolveCurrent(
            maxAgeMillis = Notify.glucosetimeout,
            preferredSensorId = serial
        )
    }

    private fun preferredDashboardSensorId(): String? {
        val nativeCurrent = SensorIdentity.resolveAppSensorId(Natives.lastsensorname())
            ?.takeIf { it.isNotBlank() }
        if (nativeCurrent != null) {
            return nativeCurrent
        }
        val cachedSerial = _sensorName.value.takeIf { it.isNotBlank() }
            ?: glucoseRepository.currentSerial.value.takeIf { it.isNotBlank() }
        return SensorIdentity.resolveAvailableMainSensor(
            selectedMain = nativeCurrent,
            preferredSensorId = cachedSerial,
            activeSensors = Natives.activeSensors()
        ) ?: cachedSerial
    }
}
