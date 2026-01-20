package tk.glucodata.alerts

import tk.glucodata.Log

/**
 * Tracks the state of active alerts to support "Retry" logic.
 * Ensures alerts don't fire continuously if they are already active,
 * unless the retry interval has passed.
 */
object AlertStateTracker {
    private const val LOG_ID = "AlertStateTracker"

    // Last time an alert of this type was triggered (ms)
    private val lastTriggerTime = mutableMapOf<AlertType, Long>()
    
    // How many times we have retried for the current alert cycle
    private val retryCounts = mutableMapOf<AlertType, Int>()

    /**
     * Determine if an alert should trigger based on retry settings.
     * 
     * Logic:
     * - If !retryEnabled -> always true (but reset state)
     * - If snoozed -> false
     * - If first time -> true
     * - If within retryInterval -> false (suppress)
     * - If passed retryInterval:
     *   - If retries left -> true (increment count)
     *   - If no retries left -> false (gave up)
     */
    fun shouldTrigger(type: AlertType, config: AlertConfig): Boolean {
        // 1. Snooze Check (Global priority)
        if (SnoozeManager.isSnoozed(type)) {
             // If snoozed, we don't care about retry state, just suppress.
             // We DO NOT reset retry state here, so when snooze ends, we might resume or start fresh?
             // Actually, if snoozed, we should probably treat it as "handled".
             return false
        }

        if (!config.retryEnabled) {
            // Logic: One Shot per Episode (until Dismissed)
            // If already fired (and not reset by Dismiss), suppress it.
            // This prevents "constant vibration" every minute.
            val lastTime = lastTriggerTime[type] ?: 0L
            if (lastTime > 0) {
                 // Failsafe: If it's been > 2 hours, assume it's a new episode or user ignored it?
                 // Let's stick to strict "Until Dismissed" but maybe limit to prevent stuck state forever?
                 // actually, if we simply return false, we get the "Silent Update" behavior from Notify.java
                 // unique issue: If user ignores it, and it goes invalid, then low again 3 hrs later... we blocked it.
                 // We need an "Auto Reset" if timeDiff is huge.
                 val timeDiff = System.currentTimeMillis() - lastTime
                 if (timeDiff > 120 * 60 * 1000L) { // 2 hours
                     Log.i(LOG_ID, "${type.name}: Auto-resetting stale One-Shot state (>120m)")
                     resetState(type)
                     return true
                 }
                 
                 return false 
            }
            return true
        }

        val lastTime = lastTriggerTime[type] ?: 0L
        val now = System.currentTimeMillis()
        val intervalMs = config.retryIntervalMinutes * 60 * 1000L

        // 2. First Trigger (or very old)
        // If it's been a long time (e.g. > 2x interval + buffer), treat as fresh? 
        // For now, let's say if we reset explicitly on Dismiss/Normal, then lastTriggerTime is 0.
        if (lastTime == 0L) {
            Log.i(LOG_ID, "${type.name}: First trigger (Starting retry cycle)")
            return true
        }

        // 3. Retry Logic
        val timeDiff = now - lastTime
        
        if (timeDiff < intervalMs) {
            // Too soon - suppress
            // Log.v(LOG_ID, "${type.name}: Suppressing (Time since last: ${timeDiff/1000}s < ${intervalMs/1000}s)")
            return false
        } else {
            // Interval passed - check retry count
            val currentCount = retryCounts[type] ?: 0
            
            if (currentCount < config.retryCount) {
                Log.i(LOG_ID, "${type.name}: Retrying ($currentCount < ${config.retryCount})")
                return true
            } else {
                Log.i(LOG_ID, "${type.name}: Max retries reached ($currentCount). Suppressing.")
                return false
            }
        }
    }

    /**
     * Call this when the alert ACTUALLY fires (sound/notification played).
     * Updates timestamps and counters.
     */
    fun onAlertTriggered(type: AlertType) {
        val lastTime = lastTriggerTime[type] ?: 0L
        
        if (lastTime == 0L) {
            // First trigger
            lastTriggerTime[type] = System.currentTimeMillis()
            retryCounts[type] = 0 // Initial trigger doesn't count as a "retry", or is it retry #0?
                                  // Let's say initial is 0. Next one is retry #1.
        } else {
            // This is a retry
            lastTriggerTime[type] = System.currentTimeMillis()
            val current = retryCounts[type] ?: 0
            retryCounts[type] = current + 1
        }
    }

    /**
     * Reset state for an alert type.
     * Call this when:
     * - Alert is Dismissed by user
     * - Glucose returns to normal (handled by Notify logic usually?)
     */
    fun resetState(type: AlertType) {
        if (lastTriggerTime.containsKey(type)) {
            Log.i(LOG_ID, "Resetting state for ${type.name}")
            lastTriggerTime.remove(type)
            retryCounts.remove(type)
        }
    }
}
