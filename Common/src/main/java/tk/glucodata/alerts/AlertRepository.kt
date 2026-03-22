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
    private const val DEFAULT_THRESHOLD_MIGRATION_KEY = "alert_threshold_defaults_v3"
    @Volatile
    private var hiddenLegacyAlertCleanupDone = false
    @Volatile
    private var defaultThresholdMigrationDone = false
    
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
    private fun keyActiveStartMinute(type: AlertType) = "alert_${type.id}_startMin"
    private fun keyActiveEndHour(type: AlertType) = "alert_${type.id}_endHour"
    private fun keyActiveEndMinute(type: AlertType) = "alert_${type.id}_endMin"
    private fun keyRetryEnabled(type: AlertType) = "alert_${type.id}_retryOn"
    private fun keyRetryInterval(type: AlertType) = "alert_${type.id}_retryInt"
    private fun keyRetryCount(type: AlertType) = "alert_${type.id}_retryCnt"
    
    /**
     * Load configuration for an alert type.
     * For legacy types, reads from Natives. For new types, reads from SharedPreferences.
     */
    fun loadConfig(type: AlertType): AlertConfig {
        ensureDefaultThresholdMigration()
        ensureHiddenLegacyAlertCleanup()
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
        return applyLegacyOverrides(type, base)
    }

    private fun applyLegacyOverrides(type: AlertType, base: AlertConfig): AlertConfig {
        return base.copy(
            deliveryMode = prefs.getString(keyDeliveryMode(type), null)
                ?.let { AlertDeliveryMode.valueOf(it) } ?: base.deliveryMode,
            volumeProfile = prefs.getString(keyVolumeProfile(type), null)
                ?.let { VolumeProfile.valueOf(it) } ?: base.volumeProfile,
            overrideDND = prefs.getBoolean(keyOverrideDND(type), Natives.getalarmdisturb(type.id)),
            soundEnabled = prefs.getBoolean(keySoundEnabled(type), Natives.alarmhassound(type.id)),
            vibrationEnabled = prefs.getBoolean(keyVibration(type), Natives.alarmhasvibration(type.id)),
            flashEnabled = prefs.getBoolean(keyFlash(type), Natives.alarmhasflash(type.id)),
            customSoundUri = prefs.getString(keyCustomSound(type), null),
            defaultSnoozeMinutes = prefs.getInt(keySnooze(type), base.defaultSnoozeMinutes),
            alarmDurationSeconds = prefs.getInt(keyAlarmDuration(type), Natives.readalarmduration(type.id)),
            // NEW: Time range and retry
            timeRangeEnabled = prefs.getBoolean(keyTimeRangeEnabled(type), false),
            activeStartHour = prefs.getInt(keyActiveStartHour(type), -1).takeIf { it >= 0 },
            activeStartMinute = prefs.getInt(keyActiveStartMinute(type), -1).takeIf { it >= 0 },
            activeEndHour = prefs.getInt(keyActiveEndHour(type), -1).takeIf { it >= 0 },
            activeEndMinute = prefs.getInt(keyActiveEndMinute(type), -1).takeIf { it >= 0 },
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
            activeStartMinute = prefs.getInt(keyActiveStartMinute(type), -1).takeIf { it >= 0 },
            activeEndHour = prefs.getInt(keyActiveEndHour(type), -1).takeIf { it >= 0 },
            activeEndMinute = prefs.getInt(keyActiveEndMinute(type), -1).takeIf { it >= 0 },
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
        ensureHiddenLegacyAlertCleanup()
        // FIRST: Save to SharedPreferences
        saveToPrefs(config)
        
        // THEN: Sync to Natives for legacy types (uses the just-saved config)

        
        // Sync native alarm settings for legacy types
        syncNativeAlarmSettings(config)
    }
    
    private fun saveToPrefs(config: AlertConfig) {
        prefs.edit {
            putBoolean(keyEnabled(config.type), config.enabled)
            if (config.threshold != null) putFloat(keyThreshold(config.type), config.threshold) else remove(keyThreshold(config.type))
            if (config.durationMinutes != null) putInt(keyDuration(config.type), config.durationMinutes) else remove(keyDuration(config.type))
            if (config.forecastMinutes != null) putInt(keyForecast(config.type), config.forecastMinutes) else remove(keyForecast(config.type))
            putString(keyDeliveryMode(config.type), config.deliveryMode.name)
            putString(keyVolumeProfile(config.type), config.volumeProfile.name)
            putBoolean(keyOverrideDND(config.type), config.overrideDND)
            putBoolean(keySoundEnabled(config.type), config.soundEnabled)
            // Save customSoundUri - if null, remove the key so it defaults to app default
            if (config.customSoundUri != null) {
                putString(keyCustomSound(config.type), config.customSoundUri)
            } else {
                remove(keyCustomSound(config.type))
            }
            putBoolean(keyVibration(config.type), config.vibrationEnabled)
            putBoolean(keyFlash(config.type), config.flashEnabled)
            putInt(keySnooze(config.type), config.defaultSnoozeMinutes)
            putInt(keyAlarmDuration(config.type), config.alarmDurationSeconds)
            putBoolean(keyTimeRangeEnabled(config.type), config.timeRangeEnabled)
            if (config.activeStartHour != null) putInt(keyActiveStartHour(config.type), config.activeStartHour) else remove(keyActiveStartHour(config.type))
            if (config.activeStartMinute != null) putInt(keyActiveStartMinute(config.type), config.activeStartMinute) else remove(keyActiveStartMinute(config.type))
            if (config.activeEndHour != null) putInt(keyActiveEndHour(config.type), config.activeEndHour) else remove(keyActiveEndHour(config.type))
            if (config.activeEndMinute != null) putInt(keyActiveEndMinute(config.type), config.activeEndMinute) else remove(keyActiveEndMinute(config.type))
            putBoolean(keyRetryEnabled(config.type), config.retryEnabled)
            putInt(keyRetryInterval(config.type), config.retryIntervalMinutes)
            putInt(keyRetryCount(config.type), config.retryCount)
        }
    }
    
    private fun syncNativeAlarmSettings(config: AlertConfig) {
        val typeId = config.type.id

        // Compose alert settings must not silently toggle hidden legacy-only alert types
        // like AVAILABLE(2) / AMOUNT(3).
        if (typeId == AlertType.LOW.id || typeId == AlertType.HIGH.id || typeId == AlertType.LOSS.id) {
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
            
            Natives.writering(
                typeId,
                config.customSoundUri ?: "",
                config.soundEnabled,
                config.flashEnabled,
                config.vibrationEnabled
            );
        } else if (typeId == AlertType.VERY_LOW.id || typeId == AlertType.VERY_HIGH.id ||
            typeId == AlertType.PRE_LOW.id || typeId == AlertType.PRE_HIGH.id) {
            Natives.setAlertConfig(
                typeId,
                config.threshold ?: 0f,
                config.enabled
            )

            Natives.setalarmdisturb(typeId, config.overrideDND)
            Natives.writealarmduration(typeId, config.alarmDurationSeconds)
            Natives.writering(
                typeId,
                config.customSoundUri ?: "",
                config.soundEnabled,
                config.flashEnabled,
                config.vibrationEnabled
            );
        }
    }
    
    /**
     * Sync Low/High/Loss alarms directly from the config being saved.
     */

    
    /**
     * Load all alert configurations.
     */
    fun loadAllConfigs(): List<AlertConfig> {
        ensureHiddenLegacyAlertCleanup()
        return AlertType.settingsEntries.map { loadConfig(it) }
    }
    
    /**
     * Check if any critical alerts are enabled (for UI indicators).
     */
    fun hasAnyCriticalAlertEnabled(): Boolean {
        return loadConfig(AlertType.LOW).enabled ||
               loadConfig(AlertType.VERY_LOW).enabled ||
               loadConfig(AlertType.MISSED_READING).enabled
    }

    private fun ensureDefaultThresholdMigration() {
        if (defaultThresholdMigrationDone) {
            return
        }
        synchronized(this) {
            if (defaultThresholdMigrationDone) {
                return
            }
            if (prefs.getBoolean(DEFAULT_THRESHOLD_MIGRATION_KEY, false)) {
                defaultThresholdMigrationDone = true
                return
            }

            val isMmol = Applic.unit == 1
            migrateLegacyThresholdIfUnchanged(
                type = AlertType.LOW,
                currentThreshold = Natives.alarmlow(),
                enabled = Natives.hasalarmlow(),
                oldDefault = if (isMmol) AlertDefaults.LEGACY_LOW_THRESHOLD_MMOL else AlertDefaults.LEGACY_LOW_THRESHOLD_MGDL,
                newDefault = if (isMmol) AlertDefaults.LOW_THRESHOLD_MMOL else AlertDefaults.LOW_THRESHOLD_MGDL,
                isMmol = isMmol
            )
            migrateLegacyThresholdIfUnchanged(
                type = AlertType.HIGH,
                currentThreshold = Natives.alarmhigh(),
                enabled = Natives.hasalarmhigh(),
                oldDefault = if (isMmol) AlertDefaults.LEGACY_HIGH_THRESHOLD_MMOL else AlertDefaults.LEGACY_HIGH_THRESHOLD_MGDL,
                newDefault = if (isMmol) AlertDefaults.HIGH_THRESHOLD_MMOL else AlertDefaults.HIGH_THRESHOLD_MGDL,
                isMmol = isMmol
            )
            migrateLegacyThresholdIfUnchanged(
                type = AlertType.VERY_LOW,
                currentThreshold = Natives.alarmverylow(),
                enabled = Natives.hasalarmverylow(),
                oldDefault = if (isMmol) AlertDefaults.LEGACY_VERY_LOW_THRESHOLD_MMOL else AlertDefaults.LEGACY_VERY_LOW_THRESHOLD_MGDL,
                newDefault = if (isMmol) AlertDefaults.VERY_LOW_THRESHOLD_MMOL else AlertDefaults.VERY_LOW_THRESHOLD_MGDL,
                isMmol = isMmol
            )
            migrateLegacyThresholdIfUnchanged(
                type = AlertType.VERY_HIGH,
                currentThreshold = Natives.alarmveryhigh(),
                enabled = Natives.hasalarmveryhigh(),
                oldDefault = if (isMmol) AlertDefaults.LEGACY_VERY_HIGH_THRESHOLD_MMOL else AlertDefaults.LEGACY_VERY_HIGH_THRESHOLD_MGDL,
                newDefault = if (isMmol) AlertDefaults.VERY_HIGH_THRESHOLD_MMOL else AlertDefaults.VERY_HIGH_THRESHOLD_MGDL,
                isMmol = isMmol
            )
            migrateLegacyThresholdIfUnchanged(
                type = AlertType.PRE_LOW,
                currentThreshold = Natives.alarmprelow(),
                enabled = Natives.hasalarmprelow(),
                oldDefault = if (isMmol) AlertDefaults.LEGACY_FORECAST_LOW_THRESHOLD_MMOL else AlertDefaults.LEGACY_FORECAST_LOW_THRESHOLD_MGDL,
                newDefault = if (isMmol) AlertDefaults.FORECAST_LOW_THRESHOLD_MMOL else AlertDefaults.FORECAST_LOW_THRESHOLD_MGDL,
                isMmol = isMmol
            )
            migrateLegacyThresholdIfUnchanged(
                type = AlertType.PRE_HIGH,
                currentThreshold = Natives.alarmprehigh(),
                enabled = Natives.hasalarmprehigh(),
                oldDefault = if (isMmol) AlertDefaults.LEGACY_HIGH_THRESHOLD_MMOL else AlertDefaults.LEGACY_HIGH_THRESHOLD_MGDL,
                newDefault = if (isMmol) AlertDefaults.FORECAST_HIGH_THRESHOLD_MMOL else AlertDefaults.FORECAST_HIGH_THRESHOLD_MGDL,
                isMmol = isMmol
            )

            prefs.edit {
                putBoolean(DEFAULT_THRESHOLD_MIGRATION_KEY, true)
            }
            defaultThresholdMigrationDone = true
        }
    }

    private fun migrateLegacyThresholdIfUnchanged(
        type: AlertType,
        currentThreshold: Float,
        enabled: Boolean,
        oldDefault: Float,
        newDefault: Float,
        isMmol: Boolean
    ) {
        if (prefs.contains(keyThreshold(type)) || !approximatelyEquals(currentThreshold, oldDefault)) {
            return
        }

        val migrated = applyLegacyOverrides(
            type,
            AlertDefaults.defaultConfig(type, isMmol).copy(
                enabled = enabled,
                threshold = newDefault
            )
        )
        saveToPrefs(migrated)
        syncNativeAlarmSettings(migrated)
    }

    private fun approximatelyEquals(first: Float, second: Float): Boolean {
        return kotlin.math.abs(first - second) < 0.11f
    }

    private fun ensureHiddenLegacyAlertCleanup() {
        if (hiddenLegacyAlertCleanupDone) {
            return
        }
        synchronized(this) {
            if (hiddenLegacyAlertCleanupDone) {
                return
            }
            val hiddenPrefixes = listOf(
                "alert_${AlertType.AVAILABLE.id}_",
                "alert_${AlertType.AMOUNT.id}_"
            )
            val hiddenKeys = prefs.all.keys.filter { key ->
                hiddenPrefixes.any { prefix -> key.startsWith(prefix) }
            }
            if (hiddenKeys.isNotEmpty()) {
                prefs.edit {
                    hiddenKeys.forEach(::remove)
                }
            }
            // Compose should never have been the owner of the legacy "value available" alert.
            // Force-disable the old native flag on every startup so later alarm saves can't
            // silently resurrect it.
            Natives.setAlertConfig(AlertType.AVAILABLE.id, 0f, false)
            hiddenLegacyAlertCleanupDone = true
        }
    }
}
