package tk.glucodata.drivers

import java.util.Locale
import tk.glucodata.Applic
import tk.glucodata.HistorySyncAccess
import tk.glucodata.Log
import tk.glucodata.SuperGattCallback
import tk.glucodata.UiRefreshBus

/**
 * Shared bridge for cloud/virtual CGM sources that do not own a BLE packet stream.
 * Values are stored in Room as mg/dL and published through the same live path as
 * managed BLE sensors, so dashboard, notification history, widgets, and alerts can
 * consume them without source-specific UI code.
 */
object VirtualGlucoseSensorBridge {
    private const val TAG = "VirtualGlucose"
    private const val MMOL_TO_MGDL = 18.0182f

    data class Reading(
        val timestampMs: Long,
        val glucoseMgdl: Float,
        val rawMgdl: Float = Float.NaN,
        val rate: Float = Float.NaN,
    )

    @JvmStatic
    fun importHistory(
        sensorSerial: String,
        readings: List<Reading>,
        logLabel: String = "virtual",
        backfill: Boolean = true,
        nearDuplicateWindowMs: Long = 0L,
    ): Int {
        if (sensorSerial.isBlank() || readings.isEmpty()) return 0
        val latestRoomTimestamp = if (backfill) 0L else HistorySyncAccess.getLatestTimestampForSensor(sensorSerial)
        val existingTimestamps = if (nearDuplicateWindowMs > 0L) {
            val minTimestamp = readings.minOf { it.timestampMs }
            val maxTimestamp = readings.maxOf { it.timestampMs }
            HistorySyncAccess.getHistoryTimestampsForSensor(
                sensorSerial = sensorSerial,
                startTime = (minTimestamp - nearDuplicateWindowMs).coerceAtLeast(1L),
                endTime = maxTimestamp + nearDuplicateWindowMs,
            ).sortedArray()
        } else {
            LongArray(0)
        }
        val deduped = LinkedHashMap<Long, Reading>()
        readings
            .asSequence()
            .filter { it.timestampMs > latestRoomTimestamp }
            .filter { it.glucoseMgdl.isFinite() && it.glucoseMgdl > 0f }
            .filterNot {
                existingTimestamps.isNotEmpty() &&
                    hasNearbyTimestamp(existingTimestamps, it.timestampMs, nearDuplicateWindowMs)
            }
            .forEach { deduped[it.timestampMs] = it }
        val skippedAsNearDuplicate = readings.size - deduped.size
        if (skippedAsNearDuplicate > 0 && nearDuplicateWindowMs > 0L) {
            Log.i(
                TAG,
                "Skipped $skippedAsNearDuplicate $logLabel history points near existing $sensorSerial readings"
            )
        }
        if (deduped.isEmpty()) return 0

        val ordered = deduped.values.sortedBy { it.timestampMs }
        val timestamps = LongArray(ordered.size)
        val values = FloatArray(ordered.size)
        val rawValues = FloatArray(ordered.size)
        ordered.forEachIndexed { index, reading ->
            timestamps[index] = reading.timestampMs
            values[index] = reading.glucoseMgdl
            rawValues[index] = reading.rawMgdl.takeIf { it.isFinite() && it > 0f } ?: Float.NaN
        }
        if (!HistorySyncAccess.storeSensorHistoryBatchBlocking(sensorSerial, timestamps, values, rawValues)) {
            return 0
        }
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "Imported %d %s history points for %s",
                ordered.size,
                logLabel,
                sensorSerial,
            ),
        )
        return ordered.size
    }

    private fun hasNearbyTimestamp(
        sortedTimestamps: LongArray,
        timestampMs: Long,
        windowMs: Long,
    ): Boolean {
        if (sortedTimestamps.isEmpty() || windowMs <= 0L) return false
        var low = 0
        var high = sortedTimestamps.size - 1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val candidate = sortedTimestamps[mid]
            when {
                candidate < timestampMs -> low = mid + 1
                candidate > timestampMs -> high = mid - 1
                else -> return true
            }
        }
        if (low < sortedTimestamps.size && kotlin.math.abs(sortedTimestamps[low] - timestampMs) <= windowMs) {
            return true
        }
        if (low > 0 && kotlin.math.abs(sortedTimestamps[low - 1] - timestampMs) <= windowMs) {
            return true
        }
        return false
    }

    @JvmStatic
    fun publishCurrent(
        sensorSerial: String,
        reading: Reading,
        sensorGen: Int,
        logLabel: String = "virtual",
    ) {
        if (sensorSerial.isBlank()) return
        if (reading.timestampMs <= 0L || !reading.glucoseMgdl.isFinite() || reading.glucoseMgdl <= 0f) return

        val rawMgdl = reading.rawMgdl.takeIf { it.isFinite() && it > 0f } ?: 0f
        val rate = reading.rate.takeIf { it.isFinite() } ?: 0f
        HistorySyncAccess.storeCurrentReadingAsync(
            reading.timestampMs,
            reading.glucoseMgdl,
            rawMgdl,
            rate,
            sensorSerial,
        )

        val glucoseDisplay = if (Applic.unit == 1) {
            reading.glucoseMgdl / MMOL_TO_MGDL
        } else {
            reading.glucoseMgdl
        }
        SuperGattCallback.processExternalCurrentReading(
            sensorSerial,
            glucoseDisplay,
            rate,
            reading.timestampMs,
            sensorGen,
        )
        Log.d(
            TAG,
            String.format(
                Locale.US,
                "Published %s current for %s: %.1f mg/dL at %d",
                logLabel,
                sensorSerial,
                reading.glucoseMgdl,
                reading.timestampMs,
            ),
        )
        UiRefreshBus.requestDataRefresh()
    }
}
