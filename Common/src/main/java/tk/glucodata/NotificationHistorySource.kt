package tk.glucodata

import android.util.Log
import java.util.TreeMap

object NotificationHistorySource {
    private const val TAG = "NotifyHistory"
    private const val MGDL_PER_MMOL = 18.0182f

    @JvmStatic
    fun resolveSensorSerial(preferredSerial: String? = null): String? {
        if (!preferredSerial.isNullOrBlank()) {
            return SensorIdentity.resolveAppSensorId(preferredSerial) ?: preferredSerial
        }
        val lastSensor = Natives.lastsensorname()
        if (!lastSensor.isNullOrBlank()) {
            return SensorIdentity.resolveAppSensorId(lastSensor) ?: lastSensor
        }
        return Natives.activeSensors()
            ?.firstOrNull { !it.isNullOrBlank() }
            ?.let { SensorIdentity.resolveAppSensorId(it) ?: it }
    }

    @JvmStatic
    fun getDisplayHistory(startTimeMs: Long, isMmol: Boolean, sensorSerial: String? = null): List<GlucosePoint> {
        return loadHistory(startTimeMs, isMmol, sensorSerial)
    }

    @JvmStatic
    fun getRawHistory(startTimeMs: Long, sensorSerial: String? = null): List<GlucosePoint> {
        return loadHistory(startTimeMs, false, sensorSerial)
    }

    private fun loadHistory(startTimeMs: Long, isMmol: Boolean, sensorSerial: String?): List<GlucosePoint> {
        loadRoomHistory(startTimeMs, isMmol, sensorSerial)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val startSec = startTimeMs / 1000L
        val resolvedSerial = resolveSensorSerial(sensorSerial)
        if (resolvedSerial.isNullOrBlank()) {
            return emptyList()
        }
        val nativeSerial = SensorIdentity.resolveNativeSensorName(resolvedSerial) ?: resolvedSerial
        val history = try {
            Natives.getGlucoseHistoryForSensor(nativeSerial, startSec)
        } catch (t: Throwable) {
            Log.w(TAG, "loadHistory(${nativeSerial ?: "main"}, $startSec) failed", t)
            null
        } ?: return emptyList()

        if (history.size < 3) {
            return emptyList()
        }

        val orderedPoints = TreeMap<Long, GlucosePoint>()
        for (i in history.indices step 3) {
            if (i + 2 >= history.size) break

            val timestamp = history[i] * 1000L
            var value = history[i + 1] / 10f
            var rawValue = history[i + 2] / 10f
            if (value <= 0f && rawValue <= 0f) continue

            if (isMmol) {
                value /= MGDL_PER_MMOL
                rawValue /= MGDL_PER_MMOL
            }

            val candidate = GlucosePoint(timestamp, value, rawValue)
            val existing = orderedPoints[timestamp]
            if (existing == null || shouldReplace(existing, candidate)) {
                orderedPoints[timestamp] = candidate
            }
        }
        return ArrayList(orderedPoints.values)
    }

    private fun loadRoomHistory(startTimeMs: Long, isMmol: Boolean, sensorSerial: String?): List<GlucosePoint>? {
        val resolvedSerial = resolveSensorSerial(sensorSerial) ?: return null
        return HistoryRepositoryAccess.getHistoryForSensor(resolvedSerial, startTimeMs, isMmol)
    }

    private fun shouldReplace(existing: GlucosePoint, candidate: GlucosePoint): Boolean {
        val existingScore = score(existing)
        val candidateScore = score(candidate)
        if (candidateScore != existingScore) {
            return candidateScore > existingScore
        }
        return candidate.value >= existing.value
    }

    private fun score(point: GlucosePoint): Int {
        var score = 0
        if (point.value > 0f) score += 1
        if (point.rawValue > 0f) score += 2
        return score
    }
}
