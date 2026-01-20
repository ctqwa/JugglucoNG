package tk.glucodata.alerts

import tk.glucodata.R

/**
 * Types of glucose alerts supported by the system.
 */
enum class AlertType(val id: Int, val nameResId: Int) {
    LOW(0, R.string.alert_low),
    HIGH(1, R.string.alert_high),
    AVAILABLE(2, R.string.alert_value_available),      
    AMOUNT(3, R.string.alert_amount),            
    LOSS(4, R.string.alert_loss),
    VERY_LOW(5, R.string.alert_very_low),
    VERY_HIGH(6, R.string.alert_very_high),
    PRE_LOW(7, R.string.alert_forecast_low),           
    PRE_HIGH(8, R.string.alert_forecast_high),         
    MISSED_READING(9, R.string.alert_missed_reading),
    PERSISTENT_HIGH(10, R.string.alert_persistent_high),
    SENSOR_EXPIRY(11, R.string.alert_sensor_expiry);

    companion object {
        fun fromId(id: Int): AlertType? = entries.find { it.id == id }
    }
}

/**
 * Volume profile presets for alert sounds.
 * Inspired by xDrip+ volume profiles.
 */
/**
 * Volume profile presets for alert sounds.
 * Inspired by xDrip+ volume profiles.
 */
enum class VolumeProfile(val displayName: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    ASCENDING("Ascending"),      // Starts low, increases over time
    VIBRATE_ONLY("Vibrate Only"),
    SILENT("Silent")
}

/**
 * Delivery mode for alerts.
 */
enum class AlertDeliveryMode(val displayName: String) {
    NOTIFICATION_ONLY("Notification"),    // High-priority notification
    SYSTEM_ALARM("Alarm"),              // Full-screen AlarmActivity
    BOTH("Both")                               // Both Notification and System Alarm
}

/**
 * Configuration for a single alert type.
 */
data class AlertConfig(
    val type: AlertType,
    val enabled: Boolean = false,
    
    // Threshold settings
    val threshold: Float? = null,           // Glucose value (null for non-threshold alerts)
    val durationMinutes: Int? = null,       // For persistent high / missed reading alerts
    val forecastMinutes: Int? = null,       // For forecast alerts (how far ahead to predict)
    
    // Delivery settings
    val deliveryMode: AlertDeliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
    val volumeProfile: VolumeProfile = VolumeProfile.HIGH,
    val overrideDND: Boolean = false,       // Override Do Not Disturb
    
    // Sound & vibration
    val soundEnabled: Boolean = true,
    val customSoundUri: String? = null,     // null = use default
    val vibrationEnabled: Boolean = true,
    val flashEnabled: Boolean = false,
    
    // Snooze settings
    val defaultSnoozeMinutes: Int = 15,
    
    // Alarm duration (how long sound plays before auto-stop)
    val alarmDurationSeconds: Int = 60,
    
    // === NEW: Time range settings ===
    // Alert only active between these hours (24h format). null = always active
    val activeStartHour: Int? = null,       // e.g., 22 for 10 PM
    val activeEndHour: Int? = null,         // e.g., 8 for 8 AM (next day if start > end)
    val timeRangeEnabled: Boolean = false,  // Master toggle for time range
    
    // === NEW: Retry settings ("try again if no reaction") ===
    val retryEnabled: Boolean = false,
    val retryIntervalMinutes: Int = 5,      // Re-alert every X minutes
    val retryCount: Int = 3                 // Max number of retries (0 = unlimited until dismissed)
) {
    /**
     * Whether this alert should use the old system alarm path (AlarmActivity).
     */
    val useSystemAlarm: Boolean
        get() = deliveryMode == AlertDeliveryMode.SYSTEM_ALARM
    
    /**
     * Check if alert is currently active based on time range.
     */
    fun isActiveNow(): Boolean {
        if (!timeRangeEnabled || activeStartHour == null || activeEndHour == null) {
            return true  // No time restriction
        }
        
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        
        return if (activeStartHour <= activeEndHour) {
            // Same day range: e.g., 9 AM to 5 PM
            currentHour in activeStartHour until activeEndHour
        } else {
            // Overnight range: e.g., 10 PM to 8 AM
            currentHour >= activeStartHour || currentHour < activeEndHour
        }
    }
}

/**
 * Snooze state for tracking when an alert is temporarily suppressed.
 */
data class SnoozeState(
    val alertType: AlertType,
    val snoozeUntilMillis: Long,
    val isPreemptive: Boolean = false      // Preemptive snooze before alert triggered
) {
    val isSnoozed: Boolean
        get() = System.currentTimeMillis() < snoozeUntilMillis
    
    val remainingMinutes: Int
        get() = ((snoozeUntilMillis - System.currentTimeMillis()) / 60000).toInt().coerceAtLeast(0)
}

/**
 * Defaults for each alert type following xDrip+ best practices.
 */
object AlertDefaults {
    // Default thresholds (mmol/L - converted to mg/dL at runtime if needed)
    const val LOW_THRESHOLD_MMOL = 3.9f
    const val HIGH_THRESHOLD_MMOL = 10.0f
    const val VERY_LOW_THRESHOLD_MMOL = 3.0f
    const val VERY_HIGH_THRESHOLD_MMOL = 13.9f
    const val FORECAST_LOW_THRESHOLD_MMOL = 4.4f
    
    // mg/dL equivalents
    const val LOW_THRESHOLD_MGDL = 70f
    const val HIGH_THRESHOLD_MGDL = 180f
    const val VERY_LOW_THRESHOLD_MGDL = 55f
    const val VERY_HIGH_THRESHOLD_MGDL = 250f
    const val FORECAST_LOW_THRESHOLD_MGDL = 80f
    
    // Duration defaults
    const val MISSED_READING_MINUTES = 30
    const val PERSISTENT_HIGH_MINUTES = 60
    const val FORECAST_LOOK_AHEAD_MINUTES = 20
    
    // Snooze presets (minutes)
    val SNOOZE_PRESETS = listOf(5, 10, 15, 30, 60, 90, 120)
    
    /**
     * Get default configuration for an alert type.
     */
    fun defaultConfig(type: AlertType, isMmol: Boolean): AlertConfig {
        return when (type) {
            AlertType.LOW -> AlertConfig(
                type = type,
                enabled = true,
                threshold = if (isMmol) LOW_THRESHOLD_MMOL else LOW_THRESHOLD_MGDL,
                deliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
                volumeProfile = VolumeProfile.HIGH,
                overrideDND = true,
                defaultSnoozeMinutes = 15
            )
            AlertType.HIGH -> AlertConfig(
                type = type,
                enabled = true,
                threshold = if (isMmol) HIGH_THRESHOLD_MMOL else HIGH_THRESHOLD_MGDL,
                deliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
                volumeProfile = VolumeProfile.MEDIUM,
                overrideDND = false,
                defaultSnoozeMinutes = 30
            )
            AlertType.VERY_LOW -> AlertConfig(
                type = type,
                enabled = true,
                threshold = if (isMmol) VERY_LOW_THRESHOLD_MMOL else VERY_LOW_THRESHOLD_MGDL,
                deliveryMode = AlertDeliveryMode.SYSTEM_ALARM,  // Critical - use system alarm
                volumeProfile = VolumeProfile.ASCENDING,
                overrideDND = true,
                flashEnabled = true,
                defaultSnoozeMinutes = 10
            )
            AlertType.VERY_HIGH -> AlertConfig(
                type = type,
                enabled = true,
                threshold = if (isMmol) VERY_HIGH_THRESHOLD_MMOL else VERY_HIGH_THRESHOLD_MGDL,
                deliveryMode = AlertDeliveryMode.SYSTEM_ALARM,
                volumeProfile = VolumeProfile.ASCENDING,
                overrideDND = true,
                defaultSnoozeMinutes = 30
            )
            AlertType.PRE_LOW -> AlertConfig(
                type = type,
                enabled = false,
                threshold = if (isMmol) FORECAST_LOW_THRESHOLD_MMOL else FORECAST_LOW_THRESHOLD_MGDL,
                forecastMinutes = FORECAST_LOOK_AHEAD_MINUTES,
                deliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
                volumeProfile = VolumeProfile.MEDIUM,
                defaultSnoozeMinutes = 20
            )
            AlertType.PRE_HIGH -> AlertConfig(
                type = type,
                enabled = false,
                threshold = if (isMmol) HIGH_THRESHOLD_MMOL else HIGH_THRESHOLD_MGDL,
                forecastMinutes = FORECAST_LOOK_AHEAD_MINUTES,
                deliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
                volumeProfile = VolumeProfile.MEDIUM,
                defaultSnoozeMinutes = 30
            )
            AlertType.MISSED_READING -> AlertConfig(
                type = type,
                enabled = true,
                durationMinutes = MISSED_READING_MINUTES,
                deliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
                volumeProfile = VolumeProfile.MEDIUM,
                defaultSnoozeMinutes = 30
            )
            AlertType.PERSISTENT_HIGH -> AlertConfig(
                type = type,
                enabled = false,
                threshold = if (isMmol) HIGH_THRESHOLD_MMOL else HIGH_THRESHOLD_MGDL,
                durationMinutes = PERSISTENT_HIGH_MINUTES,
                deliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
                volumeProfile = VolumeProfile.MEDIUM,
                defaultSnoozeMinutes = 60
            )
            AlertType.SENSOR_EXPIRY -> AlertConfig(
                type = type,
                enabled = true,
                deliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
                volumeProfile = VolumeProfile.MEDIUM,
                soundEnabled = true,
                defaultSnoozeMinutes = 120
            )
            AlertType.LOSS -> AlertConfig(
                type = type,
                enabled = true,
                durationMinutes = 30,
                deliveryMode = AlertDeliveryMode.NOTIFICATION_ONLY,
                volumeProfile = VolumeProfile.VIBRATE_ONLY,
                defaultSnoozeMinutes = 30
            )
            else -> AlertConfig(type = type)
        }
    }
}
