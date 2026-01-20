package tk.glucodata.alerts

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import tk.glucodata.Applic
import tk.glucodata.Natives

/**
 * Repository for loading and saving alert configurations.
 * 
 * Uses SharedPreferences for new alert types (missed reading, persistent high, forecast)
 * and bridges to existing Natives methods for legacy alert types.
 */
object AlertRepository {
    
    private const val PREFS_NAME = "tk.glucodata.alerts"
    
    private val prefs: SharedPreferences by lazy {
        Applic.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Keys for SharedPreferences
    private fun keyEnabled(type: AlertType) = "alert_${type.id}_enabled"
    private fun keyThreshold(type: AlertType) = "alert_${type.id}_threshold"
    private fun keyDuration(type: AlertType) = "alert_${type.id}_duration"
    private fun keyForecast(type: AlertType) = "alert_${type.id}_forecast"
    private fun keyDeliveryMode(type: AlertType) = "alert_${type.id}_delivery"
    private fun keyVolumeProfile(type: AlertType) = "alert_${type.id}_volume"
    private fun keyOverrideDND(type: AlertType) = "alert_${type.id}_dnd"
    private fun keySoundEnabled(type: AlertType) = "alert_${type.id}_sound"
    private fun keyCustomSound(type: AlertType) = "alert_${type.id}_soundUri"
    private fun keyVibration(type: AlertType) = "alert_${type.id}_vibration"
    private fun keyFlash(type: AlertType) = "alert_${type.id}_flash"
    private fun keySnooze(type: AlertType) = "alert_${type.id}_snooze"
    private fun keyAlarmDuration(type: AlertType) = "alert_${type.id}_alarmDur"
    // NEW: Time range and retry keys
    private fun keyTimeRangeEnabled(type: AlertType) = "alert_${type.id}_timeRange"
    private fun keyActiveStartHour(type: AlertType) = "alert_${type.id}_startHour"
    private fun keyActiveEndHour(type: AlertType) = "alert_${type.id}_endHour"
    private fun keyRetryEnabled(type: AlertType) = "alert_${type.id}_retryOn"
    private fun keyRetryInterval(type: AlertType) = "alert_${type.id}_retryInt"
    private fun keyRetryCount(type: AlertType) = "alert_${type.id}_retryCnt"
    
    /**
     * Load configuration for an alert type.
     * For legacy types, reads from Natives. For new types, reads from SharedPreferences.
     */
    fun loadConfig(type: AlertType): AlertConfig {
        val isMmol = Applic.unit == 1
        val default = AlertDefaults.defaultConfig(type, isMmol)

        return when (type) {
            // Legacy types - bridge to Natives
            AlertType.LOW -> loadLegacyConfig(type, default) {
                copy(
                    enabled = Natives.hasalarmlow(),
                    threshold = Natives.alarmlow()
                )
            }
            AlertType.HIGH -> loadLegacyConfig(type, default) {
                copy(
                    enabled = Natives.hasalarmhigh(),
                    threshold = Natives.alarmhigh()
                )
            }
            AlertType.VERY_LOW -> loadLegacyConfig(type, default) {
                copy(
                    enabled = Natives.hasalarmverylow(),
                    threshold = Natives.alarmverylow()
                )
            }
            AlertType.VERY_HIGH -> loadLegacyConfig(type, default) {
                copy(
                    enabled = Natives.hasalarmveryhigh(),
                    threshold = Natives.alarmveryhigh()
                )
            }
            AlertType.PRE_LOW -> loadLegacyConfig(type, default) {
                copy(
                    enabled = Natives.hasalarmprelow(),
                    threshold = Natives.alarmprelow()
                )
            }
            AlertType.PRE_HIGH -> loadLegacyConfig(type, default) {
                copy(
                    enabled = Natives.hasalarmprehigh(),
                    threshold = Natives.alarmprehigh()
                )
            }
            AlertType.LOSS -> loadLegacyConfig(type, default) {
                copy(enabled = Natives.hasalarmloss())
            }
            // New types - use SharedPreferences
            else -> loadFromPrefs(type, default)
        }
    }
    
    /**
     * Load legacy config with overrides from SharedPreferences for new fields.
     */
    private inline fun loadLegacyConfig(
        type: AlertType,
        default: AlertConfig,
        legacyOverrides: AlertConfig.() -> AlertConfig
    ): AlertConfig {
        val base = default.legacyOverrides()
        return base.copy(
            deliveryMode = prefs.getString(keyDeliveryMode(type), null)
                ?.let { AlertDeliveryMode.valueOf(it) } ?: base.deliveryMode,
            volumeProfile = prefs.getString(keyVolumeProfile(type), null)
                ?.let { VolumeProfile.valueOf(it) } ?: base.volumeProfile,
            overrideDND = prefs.getBoolean(keyOverrideDND(type), Natives.getalarmdisturb(type.id)),
            soundEnabled = prefs.getBoolean(keySoundEnabled(type), Natives.alarmhassound(type.id)),
            vibrationEnabled = prefs.getBoolean(keyVibration(type), Natives.alarmhasvibration(type.id)),
            flashEnabled = prefs.getBoolean(keyFlash(type), Natives.alarmhasflash(type.id)),
            customSoundUri = prefs.getString(keyCustomSound(type), Natives.readring(type.id)),
            defaultSnoozeMinutes = prefs.getInt(keySnooze(type), base.defaultSnoozeMinutes),
            alarmDurationSeconds = prefs.getInt(keyAlarmDuration(type), Natives.readalarmduration(type.id)),
            // NEW: Time range and retry
            timeRangeEnabled = prefs.getBoolean(keyTimeRangeEnabled(type), false),
            activeStartHour = prefs.getInt(keyActiveStartHour(type), -1).takeIf { it >= 0 },
            activeEndHour = prefs.getInt(keyActiveEndHour(type), -1).takeIf { it >= 0 },
            retryEnabled = prefs.getBoolean(keyRetryEnabled(type), false),
            retryIntervalMinutes = prefs.getInt(keyRetryInterval(type), 5),
            retryCount = prefs.getInt(keyRetryCount(type), 3)
        )
    }
    
    /**
     * Load config entirely from SharedPreferences (new alert types).
     */
    private fun loadFromPrefs(type: AlertType, default: AlertConfig): AlertConfig {
        if (!prefs.contains(keyEnabled(type))) {
            return default  // First time - use defaults
        }
        
        return default.copy(
            enabled = prefs.getBoolean(keyEnabled(type), default.enabled),
            threshold = prefs.getFloat(keyThreshold(type), default.threshold ?: 0f).takeIf { it > 0 },
            durationMinutes = prefs.getInt(keyDuration(type), default.durationMinutes ?: 0).takeIf { it > 0 },
            forecastMinutes = prefs.getInt(keyForecast(type), default.forecastMinutes ?: 0).takeIf { it > 0 },
            deliveryMode = prefs.getString(keyDeliveryMode(type), default.deliveryMode.name)
                ?.let { AlertDeliveryMode.valueOf(it) } ?: default.deliveryMode,
            volumeProfile = prefs.getString(keyVolumeProfile(type), default.volumeProfile.name)
                ?.let { VolumeProfile.valueOf(it) } ?: default.volumeProfile,
            overrideDND = prefs.getBoolean(keyOverrideDND(type), default.overrideDND),
            soundEnabled = prefs.getBoolean(keySoundEnabled(type), default.soundEnabled),
            customSoundUri = prefs.getString(keyCustomSound(type), default.customSoundUri),
            vibrationEnabled = prefs.getBoolean(keyVibration(type), default.vibrationEnabled),
            flashEnabled = prefs.getBoolean(keyFlash(type), default.flashEnabled),
            defaultSnoozeMinutes = prefs.getInt(keySnooze(type), default.defaultSnoozeMinutes),
            alarmDurationSeconds = prefs.getInt(keyAlarmDuration(type), default.alarmDurationSeconds),
            // NEW: Time range and retry
            timeRangeEnabled = prefs.getBoolean(keyTimeRangeEnabled(type), false),
            activeStartHour = prefs.getInt(keyActiveStartHour(type), -1).takeIf { it >= 0 },
            activeEndHour = prefs.getInt(keyActiveEndHour(type), -1).takeIf { it >= 0 },
            retryEnabled = prefs.getBoolean(keyRetryEnabled(type), false),
            retryIntervalMinutes = prefs.getInt(keyRetryInterval(type), 5),
            retryCount = prefs.getInt(keyRetryCount(type), 3)
        )
    }
    
    /**
     * Save configuration for an alert type.
     * For legacy types, writes to both SharedPreferences and Natives.
     */
    fun saveConfig(config: AlertConfig) {
        // FIRST: Save to SharedPreferences
        saveToPrefs(config)
        
        // THEN: Sync to Natives for legacy types (uses the just-saved config)

        
        // Sync native alarm settings for legacy types
        syncNativeAlarmSettings(config)
    }
    
    private fun saveToPrefs(config: AlertConfig) {
        prefs.edit {
            putBoolean(keyEnabled(config.type), config.enabled)
            config.threshold?.let { putFloat(keyThreshold(config.type), it) }
            config.durationMinutes?.let { putInt(keyDuration(config.type), it) }
            config.forecastMinutes?.let { putInt(keyForecast(config.type), it) }
            putString(keyDeliveryMode(config.type), config.deliveryMode.name)
            putString(keyVolumeProfile(config.type), config.volumeProfile.name)
            putBoolean(keyOverrideDND(config.type), config.overrideDND)
            putBoolean(keySoundEnabled(config.type), config.soundEnabled)
            config.customSoundUri?.let { putString(keyCustomSound(config.type), it) }
            putBoolean(keyVibration(config.type), config.vibrationEnabled)
            putBoolean(keyFlash(config.type), config.flashEnabled)
            putInt(keySnooze(config.type), config.defaultSnoozeMinutes)
            putInt(keyAlarmDuration(config.type), config.alarmDurationSeconds)
            // NEW: Time range and retry
            putBoolean(keyTimeRangeEnabled(config.type), config.timeRangeEnabled)
            config.activeStartHour?.let { putInt(keyActiveStartHour(config.type), it) }
            config.activeEndHour?.let { putInt(keyActiveEndHour(config.type), it) }
            putBoolean(keyRetryEnabled(config.type), config.retryEnabled)
            putInt(keyRetryInterval(config.type), config.retryIntervalMinutes)
            putInt(keyRetryCount(config.type), config.retryCount)


        }
    }
    
    private fun syncNativeAlarmSettings(config: AlertConfig) {
        val typeId = config.type.id
        
        // Unified Update: Use the static Java wrapper in Natives to handle ALL native-backed types
        // This removes the distinction between Legacy and Advanced types in this repository.
        if (typeId <= 8) {
            Natives.setAlertConfig(
                typeId,
                config.threshold ?: 0f,
                config.enabled
            )
            
            // Sync extra settings (DND, Duration, Sound) which are still per-ID in Natives
            Natives.setalarmdisturb(typeId, config.overrideDND)
            
            // For LOSS (Type 4), 'alarmDuration' in Natives likely controls the Timeout (time until alarm),
            // not the sound duration. So we sync the durationMinutes (converted to seconds).
            // For others, it controls Sound Duration.
            val durationValue = if (config.type == AlertType.LOSS) {
                (config.durationMinutes ?: 30) * 60
            } else {
                config.alarmDurationSeconds
            }
            Natives.writealarmduration(typeId, durationValue)
            
            config.customSoundUri?.let { 
                Natives.writering(
                    typeId, 
                    it, 
                    config.soundEnabled, 
                    config.flashEnabled, 
                    config.vibrationEnabled
                ) 
            }
        }
    }
    
    /**
     * Sync Low/High/Loss alarms directly from the config being saved.
     */

    
    /**
     * Load all alert configurations.
     */
    fun loadAllConfigs(): List<AlertConfig> {
        return AlertType.entries.map { loadConfig(it) }
    }
    
    /**
     * Check if any critical alerts are enabled (for UI indicators).
     */
    fun hasAnyCriticalAlertEnabled(): Boolean {
        return loadConfig(AlertType.LOW).enabled ||
               loadConfig(AlertType.VERY_LOW).enabled ||
               loadConfig(AlertType.MISSED_READING).enabled
    }
}

