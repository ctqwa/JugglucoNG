package tk.glucodata.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import tk.glucodata.Natives
import tk.glucodata.data.GlucoseRepository
import tk.glucodata.data.HistorySync

class DashboardViewModel(
    private val glucoseRepository: GlucoseRepository = GlucoseRepository()
) : ViewModel() {
    private val _currentGlucose = MutableStateFlow("---")
    val currentGlucose = _currentGlucose.asStateFlow()

    private val _currentRate = MutableStateFlow(0f)
    val currentRate = _currentRate.asStateFlow()

    private val _sensorName = MutableStateFlow("")
    val sensorName = _sensorName.asStateFlow()

    private val _daysRemaining = MutableStateFlow("")
    val daysRemaining = _daysRemaining.asStateFlow()

    private val _xDripBroadcastEnabled = MutableStateFlow(false)
    val xDripBroadcastEnabled = _xDripBroadcastEnabled.asStateFlow()

    private val _patchedLibreBroadcastEnabled = MutableStateFlow(false)
    val patchedLibreBroadcastEnabled = _patchedLibreBroadcastEnabled.asStateFlow()

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

    private val _lowAlarmSoundMode = MutableStateFlow(0)
    val lowAlarmSoundMode = _lowAlarmSoundMode.asStateFlow()

    private val _highAlarmSoundMode = MutableStateFlow(0)
    val highAlarmSoundMode = _highAlarmSoundMode.asStateFlow()

    init {
        refreshData()
        startCollection()
        startHistoryCollection()
    }
    
    /**
     * Called when the app resumes from background.
     * Refreshes data to prevent stale chart state after Home button.
     */
    fun onResume() {
        refreshData()
    }
    private fun startCollection() {
        viewModelScope.launch {
            combine(
                glucoseRepository.getCurrentReading(),
                viewMode,
                _unit
            ) { point, mode, unitStr ->
                Triple(point, mode, unitStr)
            }.collect { (point, mode, _) ->
                if (point != null) {
                    val valueToDisplay = if (mode == 1 || mode == 3) point.rawValue else point.value
                    _currentGlucose.value = if (valueToDisplay < 30) String.format("%.1f", valueToDisplay) else valueToDisplay.toInt().toString()
                    _currentRate.value = point.rate ?: 0f  // Default to 0 if rate is null
                    
                    // Efficiently update history by appending the new point instead of re-querying the DB
                    val currentHistory = _glucoseHistory.value
                    if (currentHistory.isEmpty() || currentHistory.last().timestamp != point.timestamp) {
                        _glucoseHistory.value = currentHistory + point
                    }
                }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            val unitVal = Natives.getunit()
            val isMmol = unitVal == 1
            _unit.value = if (isMmol) "mmol/L" else "mg/dL"
            
            // Load Notification Chart Setting
            val context = tk.glucodata.Applic.app
            val prefs = context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
            _notificationChartEnabled.value = prefs.getBoolean("notification_chart_enabled", true)
            
            _targetLow.value = Natives.targetlow()
            _targetHigh.value = Natives.targethigh()
            _xDripBroadcastEnabled.value = Natives.getxbroadcast()
            _patchedLibreBroadcastEnabled.value = Natives.getlibrelinkused()
            
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
            
            // Get view mode from active sensor.
            val sName = Natives.lastsensorname()
            if (sName != null) {
                _sensorName.value = sName
                val dataptr = Natives.getdataptr(sName)
                if (dataptr != 0L) {
                    val vm = Natives.getViewMode(dataptr)
                    _viewMode.value = vm
                    
                    val expectedEnd = Natives.getSensorEndTime(dataptr, false)
                    val startMsec = Natives.getSensorStartmsec(dataptr)
                    
                    if (expectedEnd > 0 && startMsec > 0) {
                        val now = System.currentTimeMillis()
                        val usedMs = now - startMsec
                        val leftMs = expectedEnd - now
                        
                        val oneDayMs = 86400000L
                        val oneHourMs = 3600000L
                        
                        val isSibionics2 = Natives.isSibionics2(dataptr)

                        if (isSibionics2) {
                            if (usedMs < oneDayMs) {
                                val hours = usedMs / oneHourMs
                                val hStr = context.getString(tk.glucodata.R.string.hours_short, hours)
                                _daysRemaining.value = context.getString(tk.glucodata.R.string.in_use, hStr)
                            } else {
                                val days = usedMs / oneDayMs
                                val dStr = "$days " + context.getString(tk.glucodata.R.string.duration_days)
                                _daysRemaining.value = context.getString(tk.glucodata.R.string.in_use, dStr)
                            }
                        } else {
                            if (leftMs < oneDayMs) {
                                if (leftMs < 0) _daysRemaining.value = context.getString(tk.glucodata.R.string.expired)
                                else {
                                    val hours = leftMs / oneHourMs
                                    val hStr = context.getString(tk.glucodata.R.string.hours_short, hours)
                                    _daysRemaining.value = context.getString(tk.glucodata.R.string.remaining, hStr)
                                }
                            } else {
                                val days = leftMs / oneDayMs
                                val dStr = "$days " + context.getString(tk.glucodata.R.string.duration_days)
                                _daysRemaining.value = context.getString(tk.glucodata.R.string.remaining, dStr)
                            }
                        }
                    } else {
                         _daysRemaining.value = ""
                    }
                } else {
                     _daysRemaining.value = ""
                }
            } else {
                 _daysRemaining.value = ""
            }

            // PERFORMANCE FIX: Sync history asynchronously in background
            // Prevents blocking UI thread on cold start (Back button)
            viewModelScope.launch {
                HistorySync.syncFromNative()
            }
            
            // Note: History is now collected reactively via startHistoryCollection()
        }
    }

    private fun startHistoryCollection() {
        viewModelScope.launch {
            // Re-collect history when unit changes
            _unit.collect { unitStr ->
                val isMmol = unitStr == "mmol/L"
                
                // TWO-STAGE LOADING for instant UI with full history:
                // Stage 1: Load last 24h immediately (fast, for instant render)
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                
                // First collect: recent data only (instant)
                var isFirstLoad = true
                glucoseRepository.getHistoryFlow(oneDayAgo, isMmol).collect { history ->
                    _glucoseHistory.value = history
                    
                    // Stage 2: After first render, switch to full history (background)
                    if (isFirstLoad) {
                        isFirstLoad = false
                        viewModelScope.launch {
                            // Switch to loading ALL history
                            glucoseRepository.getHistoryFlow(0L, isMmol).collect { fullHistory ->
                                _glucoseHistory.value = fullHistory
                            }
                        }
                    }
                }
            }
        }
    }

    fun setLowAlarm(enabled: Boolean, threshold: Float) {
        // Natives.alarmhigh() returns value in User Unit
        val highThreshold = Natives.alarmhigh()
        val highEnabled = Natives.hasalarmhigh()
        
        val available = Natives.hasvaluealarm()
        val loss = Natives.hasalarmloss()
        
        // Natives.setalarms expects User Units
        Natives.setalarms(threshold, highThreshold, enabled, highEnabled, available, loss)
        refreshData()
    }

    fun setHighAlarm(enabled: Boolean, threshold: Float) {
        // Natives.alarmlow() returns value in User Unit
        val lowThreshold = Natives.alarmlow()
        val lowEnabled = Natives.hasalarmlow()
        
        val available = Natives.hasvaluealarm()
        val loss = Natives.hasalarmloss()
        
        Natives.setalarms(lowThreshold, threshold, lowEnabled, enabled, available, loss)
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
        Natives.setunit(mode)
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

    fun toggleNotificationChart(enabled: Boolean) {
        val context = tk.glucodata.Applic.app
        val prefs = context.getSharedPreferences(context.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notification_chart_enabled", enabled).apply()
        _notificationChartEnabled.value = enabled
        
        // Force update notification to reflect change immediately
        tk.glucodata.Notify.showoldglucose()
    }
}
