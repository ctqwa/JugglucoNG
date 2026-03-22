package tk.glucodata.alerts

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import tk.glucodata.Applic
import tk.glucodata.CurrentDisplaySource
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.Notify
import tk.glucodata.R

object AlertRuntimeManager {
    private const val LOG_ID = "AlertRuntimeManager"
    private const val CHECK_INTERVAL_MS = 60_000L
    private const val SENSOR_EXPIRY_WARNING_MS = 24L * 60L * 60L * 1000L

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val lock = Any()
    private var monitorTask: ScheduledFuture<*>? = null

    private var lastReadingTimeMs: Long = 0L
    private var lastGlucoseValue: Float = Float.NaN
    private var lastRate: Float = Float.NaN
    private var persistentHighStartedAtMs: Long = 0L

    fun ensureMonitoring() {
        synchronized(lock) {
            bootstrapLastReadingLocked()
            ensureTaskLocked()
            evaluateLocked(System.currentTimeMillis())
        }
    }

    fun onNewReading(glucoseValue: Float, rate: Float, readingTimeMs: Long) {
        synchronized(lock) {
            lastReadingTimeMs = readingTimeMs
            lastGlucoseValue = glucoseValue
            lastRate = rate
            ensureTaskLocked()
            evaluateLocked(readingTimeMs)
        }
    }

    private fun ensureTaskLocked() {
        if (monitorTask == null || monitorTask?.isCancelled == true) {
            monitorTask = scheduler.scheduleAtFixedRate(
                {
                    synchronized(lock) {
                        evaluateLocked(System.currentTimeMillis())
                    }
                },
                CHECK_INTERVAL_MS,
                CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun evaluateLocked(nowMs: Long) {
        bootstrapLastReadingLocked()

        evaluateMissedReadingLocked(nowMs)
        evaluatePersistentHighLocked(nowMs)
        evaluateSensorExpiryLocked(nowMs)
    }

    private fun evaluateMissedReadingLocked(nowMs: Long) {
        val type = AlertType.MISSED_READING
        val config = AlertRepository.loadConfig(type)
        val durationMs = (config.durationMinutes ?: 0) * 60_000L

        if (!config.enabled || durationMs <= 0L || lastReadingTimeMs <= 0L) {
            clearRuntimeAlert(type, "missed-reading-disabled")
            return
        }

        if (!config.isActiveNow() || SnoozeManager.isSnoozed(type)) {
            return
        }

        val missed = nowMs - lastReadingTimeMs >= durationMs
        if (!missed) {
            clearRuntimeAlert(type, "new-reading-arrived")
            return
        }

        val glucoseValue = currentGlucoseValueLocked() ?: return
        val message = Applic.app.getString(R.string.alert_missed_reading) + " - " +
            Applic.app.getString(R.string.minutes_short_format, config.durationMinutes ?: 0)

        triggerAlert(type, glucoseValue, currentRateLocked(), message)
    }

    private fun evaluatePersistentHighLocked(nowMs: Long) {
        val type = AlertType.PERSISTENT_HIGH
        val config = AlertRepository.loadConfig(type)
        val threshold = config.threshold
        val durationMs = (config.durationMinutes ?: 0) * 60_000L
        val glucoseValue = currentGlucoseValueLocked()

        if (!config.enabled || threshold == null || durationMs <= 0L || glucoseValue == null) {
            persistentHighStartedAtMs = 0L
            clearRuntimeAlert(type, "persistent-high-disabled")
            return
        }

        if (glucoseValue <= threshold) {
            persistentHighStartedAtMs = 0L
            clearRuntimeAlert(type, "persistent-high-cleared")
            return
        }

        if (persistentHighStartedAtMs == 0L) {
            persistentHighStartedAtMs = lastReadingTimeMs.takeIf { it > 0L } ?: nowMs
        }

        if (!config.isActiveNow() || SnoozeManager.isSnoozed(type)) {
            return
        }

        if (nowMs - persistentHighStartedAtMs < durationMs) {
            return
        }

        val message = Applic.app.getString(R.string.alert_persistent_high) + " " + Notify.glucosestr(glucoseValue)
        triggerAlert(type, glucoseValue, currentRateLocked(), message)
    }

    private fun evaluateSensorExpiryLocked(nowMs: Long) {
        val type = AlertType.SENSOR_EXPIRY
        val config = AlertRepository.loadConfig(type)
        if (!config.enabled) {
            clearRuntimeAlert(type, "sensor-expiry-disabled")
            return
        }

        val endTimeMs = try {
            Natives.getendtime()
        } catch (t: Throwable) {
            0L
        }

        if (endTimeMs <= 0L || endTimeMs - nowMs > SENSOR_EXPIRY_WARNING_MS) {
            clearRuntimeAlert(type, "sensor-expiry-not-due")
            return
        }

        if (!config.isActiveNow() || SnoozeManager.isSnoozed(type)) {
            return
        }

        val glucoseValue = currentGlucoseValueLocked() ?: return
        val remainingHours = ((endTimeMs - nowMs).coerceAtLeast(0L) / 3_600_000L).toInt().coerceAtLeast(1)
        val message = Applic.app.getString(R.string.alert_sensor_expiry) + " - " +
            Applic.app.getString(R.string.hours_short, remainingHours)

        triggerAlert(type, glucoseValue, currentRateLocked(), message)
    }

    private fun triggerAlert(type: AlertType, glucoseValue: Float, rate: Float, message: String) {
        try {
            val triggered = Notify.triggerSupplementalGlucoseAlert(type.id, glucoseValue, rate, message)
            if (triggered) {
                Log.i(LOG_ID, "Triggered ${type.name}: $message")
            }
        } catch (t: Throwable) {
            Log.stack(LOG_ID, "triggerAlert ${type.name}", t)
        }
    }

    private fun clearRuntimeAlert(type: AlertType, reason: String) {
        AlertStateTracker.resetState(type)
        Notify.cancelRetrySession(type.id, reason)
    }

    private fun bootstrapLastReadingLocked() {
        if (lastReadingTimeMs > 0L && lastGlucoseValue.isFinite()) {
            return
        }
        val latest = try {
            CurrentDisplaySource.resolveCurrent(Notify.glucosetimeout)
        } catch (t: Throwable) {
            null
        } ?: return

        if (lastReadingTimeMs <= 0L) {
            lastReadingTimeMs = latest.timeMillis
        }
        if (!lastGlucoseValue.isFinite()) {
            lastGlucoseValue = latest.primaryValue
        }
        if (!lastRate.isFinite()) {
            lastRate = latest.rate
        }
    }

    private fun currentGlucoseValueLocked(): Float? {
        if (lastGlucoseValue.isFinite()) {
            return lastGlucoseValue
        }
        bootstrapLastReadingLocked()
        return lastGlucoseValue.takeIf { it.isFinite() }
    }

    private fun currentRateLocked(): Float {
        if (lastRate.isFinite()) {
            return lastRate
        }
        bootstrapLastReadingLocked()
        return lastRate.takeIf { it.isFinite() } ?: Float.NaN
    }
}
