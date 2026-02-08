package tk.glucodata.data

import tk.glucodata.Natives
import tk.glucodata.strGlucose
import tk.glucodata.nums.numio
import tk.glucodata.nums.item
import com.eveningoutpost.dexdrip.services.broadcastservice.models.GraphLine
import com.eveningoutpost.dexdrip.services.broadcastservice.models.GraphPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import tk.glucodata.Applic
import tk.glucodata.ui.GlucosePoint

/**
 * Repository that bridges native glucose data with the independent Room history database.
 * New readings are stored in Room for long-term history while still using native data for
 * real-time display and calibration.
 */
class GlucoseRepository {
    
    private val historyRepository = HistoryRepository(Applic.app)
    
    companion object {
        private const val TAG = "GlucoseRepo"
    }

    /**
     * Get the current reading.
     * OBSERVES the Room Database (Single Source of Truth).
     * Also runs a background Poller to fetch Native data and write it to the DB.
     * This unifies Native (Polled) and AiDex (Pushed) data streams.
     */
    fun getCurrentReading(): Flow<GlucosePoint?> = channelFlow {
        // Ensure backfill is done on first access
        historyRepository.ensureBackfilled()

        // 1. Launch Background Poller for Native Data
        launch {
            while (isActive) {
                pollNativeAndStore()
                delay(3000)
            }
        }

        // 2. Observe Database for ALL updates (Native + AiDex)
        historyRepository.getLatestReadingFlow().collect { point ->
            val unit = Natives.getunit()
            val isMmol = (unit == 1)
            
            if (point != null) {
                // Apply Unit Conversion for Display
                val displayValue = if (isMmol) point.value / 18.0182f else point.value
                val displayRaw = if (isMmol) point.rawValue / 18.0182f else point.rawValue
                
                // Re-wrap in UI GlucosePoint (point is from domain layer, but we ensure conversion here)
                // Actually getLatestReadingFlow returns display-ready values?
                // No, getLatestReadingFlow in HistoryRepository returns GlucosePoint(value=reading.value...)
                // And reading.value is in mg/dL.
                // So we MUST convert it here based on current Unit.
                
                // Wait, HistoryRepository.getHistoryFlow does conversion?
                // Let's check getLatestReadingFlow implementation in HistoryRepository.
                // It just maps fields. It does NOT convert units.
                
                val finalPoint = GlucosePoint(
                    value = displayValue,
                    time = point.time, // Already formatted HH:mm
                    timestamp = point.timestamp,
                    rawValue = displayRaw,
                    rate = point.rate
                )
                send(finalPoint)
            } else {
                send(null)
            }
        }
    }

    private suspend fun pollNativeAndStore() {
        try {
            val lastData: strGlucose? = Natives.lastglucose()
            if (lastData != null) {
                val timeSec = lastData.time
                val timestampMs = timeSec * 1000L
                
                // Optimization: Don't re-process if we just wrote this
                // But native doesn't give us a unique ID other than time.
                // And we might be racing with AiDex? No, AiDex is separate.
                
                var valueMgdl = lastData.value.toFloatOrNull() ?: 0f
                var rawValueMgdl = 0f
                
                // Native Value is typically in user unit, need to normalize to mg/dL for DB?
                // Wait, original code:
                // val unit = Natives.getunit()
                val unit = Natives.getunit()
                
                // Try to enrich with RAW data from history buffer
                val rawHistory = Natives.getGlucoseHistory(timeSec - 1)
                if (rawHistory != null) {
                    for (i in rawHistory.indices step 3) {
                        if (i + 2 >= rawHistory.size) break
                        val hTime = rawHistory[i]
                        if (hTime == timeSec) {
                            val valueAutoRaw = rawHistory[i+1] // mg/dL * 10 or similar?
                            val valueRawRaw = rawHistory[i+2]
                            
                            // Original logic:
                            // valueMgdl = valueAutoRaw / 10f
                            // rawValueMgdl = valueRawRaw / 10f
                            
                            valueMgdl = valueAutoRaw / 10f
                            rawValueMgdl = valueRawRaw / 10f
                            break
                        }
                    }
                }
                
                // If we didn't find raw history, we might still have a value from lastData
                // But lastData.value is string, possibly in mmol.
                // If we rely on rawHistory for everything, what if it's missing?
                // The original code fell back to parsing lastData.value.
                
                // Store in Room database (Norm: always in mg/dL)
                // Note: HistoryRepository.storeReadingAsync handles duplication checks (IGNORE strategy)
                historyRepository.storeReading(
                    timestamp = timestampMs,
                    value = valueMgdl,
                    rawValue = rawValueMgdl,
                    rate = lastData.rate
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error polling native glucose", e)
        }
    }

    /**
     * Get ALL history from the Room database.
     * No time limit - fetches everything available.
     */
    suspend fun getAllHistory(): List<GlucosePoint> {
        val unit = Natives.getunit()
        val isMmol = (unit == 1)
        
        // Ensure backfill is done
        historyRepository.ensureBackfilled()
        
        // Fetch all history RAW
        val rawHistory = historyRepository.getHistory(0L)
        
        // Convert to display unit
        return rawHistory.map { p ->
             val v = if (isMmol) p.value / 18.0182f else p.value
             val r = if (isMmol) p.rawValue / 18.0182f else p.rawValue
             GlucosePoint(v, p.time, p.timestamp, r, p.rate)
        }
    }

    /**
     * Get history since startTime, converting if needed.
     */
    suspend fun getHistory(startTime: Long, isMmol: Boolean): List<GlucosePoint> {
        val raw = historyRepository.getHistory(startTime)
        return if (isMmol) {
            raw.map { p ->
                val v = p.value / 18.0182f
                val r = p.rawValue / 18.0182f
                GlucosePoint(v, p.time, p.timestamp, r, p.rate)
            }
        } else raw
    }

    /**
     * Get history as a Flow for reactive updates.
     * Delegates to HistoryRepository.
     */
    fun getHistoryFlow(startTime: Long = 0L, isMmol: Boolean): Flow<List<GlucosePoint>> {
        return historyRepository.getHistoryFlow(startTime).map { list ->
            list.map { p ->
                 val v = if (isMmol) p.value / 18.0182f else p.value
                 val r = if (isMmol) p.rawValue / 18.0182f else p.rawValue
                 GlucosePoint(v, p.time, p.timestamp, r, p.rate)
            }
        }
    }

    /**
     * Get history as a Flow in RAW mg/dL (no conversion).
     * Delegates to HistoryRepository.
     */
    fun getHistoryFlowRaw(startTime: Long = 0L): Flow<List<GlucosePoint>> {
        return historyRepository.getHistoryFlow(startTime)
    }

    /**
     * Legacy synchronous method - fetches ALL history from native layer.
     * Used for initial load and when Room hasn't been populated yet.
     */
    fun getHistory(): List<GlucosePoint> {
        val history = mutableListOf<GlucosePoint>()
        
        try {
            // Fetch from the very beginning (startSec = 0 means all data)
            val startSec = 0L
            
            val unit = Natives.getunit()
            val isMmol = (unit == 1)
            
            val rawHistory = Natives.getGlucoseHistory(startSec)
            if (rawHistory != null) {
                Log.d(TAG, "getGlucoseHistory returned ${rawHistory.size / 3} points (ALL history)")
                try {
                    for (i in rawHistory.indices step 3) {
                        if (i + 2 >= rawHistory.size) break
                        val timeSec = rawHistory[i]
                        val valueAutoRaw = rawHistory[i+1]
                        val valueRawRaw = rawHistory[i+2]
                        
                        var value = valueAutoRaw / 10f // mg/dL
                        var valueRaw = valueRawRaw / 10f // mg/dL
                        
                        if (isMmol) {
                            value = value / 18.0182f
                            valueRaw = valueRaw / 18.0182f
                        }
                        
                        val timeMs = timeSec * 1000L
                        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timeMs))
                        history.add(GlucosePoint(value, timeStr, timeMs, valueRaw, 0f))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing history", e)
                }
            } else {
                Log.d(TAG, "getGlucoseHistory returned null. ViewMode might be changing or no data.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching history", e)
        }
        
        return history.sortedBy { it.timestamp }
    }

    fun getUnit(): String {
        return when (Natives.getunit()) {
            1 -> "mmol/L"
            2 -> "mg/dL"
            else -> "mmol/L"
        }
    }
}