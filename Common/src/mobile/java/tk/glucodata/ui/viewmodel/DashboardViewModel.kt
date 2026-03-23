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
import kotlinx.coroutines.flow.distinctUntilChanged
import tk.glucodata.Natives
import tk.glucodata.UiRefreshBus
import tk.glucodata.BatteryTrace
import tk.glucodata.data.GlucoseRepository
import tk.glucodata.data.HistorySync
import tk.glucodata.alerts.AlertRepository
import tk.glucodata.ui.util.resolveDashboardSensorStatus

class DashboardViewModel(
    private val glucoseRepository: GlucoseRepository = GlucoseRepository()
) : ViewModel() {
    private companion object {
        const val TARGET_RANGE_DEFAULTS_MIGRATION_KEY = "target_range_defaults_v2"
        const val DASHBOARD_HISTORY_WINDOW_MS = 72L * 60L * 60L * 1000L
        const val UI_RECOVERY_SYNC_MIN_INTERVAL_MS = 30_000L
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
        refreshData(syncHistory = false)
    }
    
    /**
     * Called when the app resumes from background.
     * Refreshes data to prevent stale chart state after Home button.
     * Also updates the sensor serial in GlucoseRepository so flows
     * re-subscribe to the correct sensor's data.
     */
    fun onResume() {
        glucoseRepository.refreshSensorSerial()
        refreshData(syncHistory = false)
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
                refreshData(syncHistory = false)
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
        glucoseRepository.syncLatestNativeReadingOnce()
    }

    private fun ensureUiRefreshCollection() {
        if (uiRefreshJob?.isActive == true) return
        uiRefreshJob = viewModelScope.launch {
            UiRefreshBus.events.collect { event ->
                when (event) {
                    UiRefreshBus.Event.DataChanged -> refreshData(syncHistory = false)
                    UiRefreshBus.Event.StatusOnly -> refreshData(syncHistory = false)
                }
            }
        }
    }

    private fun ensureCurrentReadingCollection() {
        if (currentReadingJob?.isActive == true) return
        currentReadingJob = viewModelScope.launch {
            glucoseRepository.getCurrentReading().collect { point ->
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

    fun refreshData(syncHistory: Boolean = false) {
        viewModelScope.launch {
            // Update the sensor serial in GlucoseRepository — if it changed,
            // all flatMapLatest flows will automatically re-subscribe
            glucoseRepository.refreshSensorSerial()
            
            val unitVal = Natives.getunit()
            val isMmol = unitVal == 1
            _unit.value = if (isMmol) "mmol/L" else "mg/dL"
            
            // Load Notification Chart Setting
            val context = tk.glucodata.Applic.app
            val prefs = context.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
            migrateTargetRangeDefaultsIfNeeded(prefs, isMmol)
            _notificationChartEnabled.value = prefs.getBoolean("notification_chart_enabled", true)
            _chartSmoothingMinutes.value = prefs.getInt("dashboard_chart_smoothing_minutes", 0)
            
            _targetLow.value = Natives.targetlow()
            _targetHigh.value = Natives.targethigh()
            _xDripBroadcastEnabled.value = Natives.getxbroadcast()
            _patchedLibreBroadcastEnabled.value = Natives.getlibrelinkused()
            _glucodataBroadcastEnabled.value = Natives.getJugglucobroadcast()
            
            // Alarms - Native getters return values in User Unit
            _hasLowAlarm.value = Natives.hasalarmlow()
            _lowAlarmThreshold.value = Natives.alarmlow()
            
            _hasHighAlarm.value = Natives.hasalarmhigh()
            _highAlarmThreshold.value = Natives.alarmhigh()
            
            // Sound Modes: 0 = Vibrate Only, 1 = Sound (System/Custom)
            val lowSound = Natives.alarmhassound(0)
            _lowAlarmSoundMode.value = if (lowSound) 1 else 0
            
            val highSound = Natives.alarmhassound(1)
            _highAlarmSoundMode.value = if (highSound) 1 else 0

            // Alerts Summary
            val anyActive = AlertRepository.loadAllConfigs().any { it.enabled }
            _alertsSummary.value = if (anyActive) "Active" else "All Alerts Disabled" // TODO: String resource
            
            // Get view mode from active sensor.
            var sName = Natives.lastsensorname()
            val activeSensors = Natives.activeSensors()

            // Conflict Resolution: Validate sName against active sensors
            // If the "last used" sensor is gone (expired/removed) but we have other active sensors,
            // we must switch focus to the valid one to prevent "split brain" state.
            if (activeSensors != null && activeSensors.isNotEmpty()) {
                _activeSensorList.value = activeSensors.toList()
                if (sName.isNullOrEmpty() || !activeSensors.contains(sName)) {
                     val valid = activeSensors[0]
                     android.util.Log.w("DashboardVM", "Stale sensor check: '$sName' is invalid. Switching to '$valid'")
                     sName = valid
                }
            } else {
                _activeSensorList.value = emptyList()
            }

            if (!sName.isNullOrEmpty() && sName.isNotBlank()) {
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
                if (snapshot != null && snapshot.size >= 5) {
                    val sensorKind = snapshot[0].toInt()
                    val vm = snapshot[1].toInt()
                    val startMsec = snapshot[2]
                    val expectedEnd = snapshot[3]
                    val officialEnd = snapshot[4]
                    _sensorStatus.value = resolveDashboardSensorStatus(sName, sensorKind, startMsec, nativeStatus)

                    _viewMode.value = vm

                    if (startMsec > 0) {
                         val now = System.currentTimeMillis()
                         // Progress Calculation for Dynamic UI
                         val endMs = if (expectedEnd > 0) expectedEnd 
                                     else if (officialEnd > 0) officialEnd 
                                     else startMsec + (14L * 24 * 3600 * 1000)
                         val totalDur = (endMs - startMsec).coerceAtLeast(1)
                         val usedDur = (now - startMsec).coerceAtLeast(0)
                         _sensorProgress.value = (usedDur.toFloat() / totalDur).coerceIn(0f, 1f)
                         
                         // Calculate Days Info "1 / 14"
                         if (endMs > startMsec) {
                             val oneDayMs = 86400000L
                             val totalMs = endMs - startMsec
                             // Calculate Current Day (1-based) and Total Days
                             val currentDay = (usedDur / oneDayMs) + 1
                             val totalDays = (totalMs / oneDayMs)
                             _daysRemaining.value = "$currentDay / $totalDays"
                             _currentDay.value = currentDay.toInt()
                             
                             // Expose raw hours for UI Logic (Calendar vs Hourglass)
                             val hoursLeft = (totalMs - usedDur) / 3600000L
                             _sensorHoursRemaining.value = hoursLeft
                         } else {
                             _daysRemaining.value = ""
                             _sensorHoursRemaining.value = 999L // Default safe value
                         }
                    } else {
                        _sensorProgress.value = 0f
                        _daysRemaining.value = ""
                    }
                    
                    // Removed separate block that caused scope errors.
                    // All logic is now consolidated above.
                } else {
                     _sensorStatus.value = resolveDashboardSensorStatus(sName, nativeStatus)
                     _viewMode.value = 0
                     _sensorProgress.value = 0f
                     _sensorHoursRemaining.value = 999L
                     _daysRemaining.value = ""
                }
            } else {
                 _sensorName.value = ""
                 _sensorStatus.value = ""
                 _viewMode.value = 0
                 _sensorProgress.value = 0f
                 _sensorHoursRemaining.value = 999L
                 _daysRemaining.value = ""
            }

            // PERFORMANCE FIX: Sync history asynchronously in background
            // Prevents blocking UI thread on cold start (Back button)
            if (syncHistory) {
                viewModelScope.launch {
                    HistorySync.syncFromNative()
                }
            }
            
            // Note: History is now collected reactively via startHistoryCollectionForMode()
        }
    }

    private fun startHistoryCollectionForMode(mode: CollectionMode) {
        val startTimeMs = when (mode) {
            CollectionMode.INACTIVE -> return
            CollectionMode.DASHBOARD -> System.currentTimeMillis() - DASHBOARD_HISTORY_WINDOW_MS
            CollectionMode.FULL_HISTORY -> 0L
        }

        if (historyJob?.isActive == true && activeHistoryStartTimeMs == startTimeMs) return

        historyJob?.cancel()
        activeHistoryStartTimeMs = startTimeMs
        _isLoading.value = true

        historyJob = viewModelScope.launch {
            combine(
                _unit,
                glucoseRepository.getHistoryFlowRaw(startTimeMs).distinctUntilChanged()
            ) { unitStr, rawHistory ->
                unitStr to rawHistory
            }.collect { (unitStr, rawHistory) ->
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
                        tk.glucodata.ui.GlucosePoint(v, p.time, p.timestamp, r, p.rate)
                    }
                } else {
                    rawHistory
                }
                _isLoading.value = false
            }
        }
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
        val sanitized = minutes.coerceIn(0, 5).let {
            when (it) {
                1, 2, 5 -> it
                else -> 0
            }
        }
        val context = tk.glucodata.Applic.app
        val prefs = context.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("dashboard_chart_smoothing_minutes", sanitized).apply()
        _chartSmoothingMinutes.value = sanitized
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
}
