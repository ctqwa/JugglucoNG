package tk.glucodata.data

import tk.glucodata.Natives
import tk.glucodata.strGlucose
import tk.glucodata.nums.numio
import tk.glucodata.nums.item
import com.eveningoutpost.dexdrip.services.broadcastservice.models.GraphLine
import com.eveningoutpost.dexdrip.services.broadcastservice.models.GraphPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
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

    fun getCurrentReading(): Flow<GlucosePoint?> = flow {
        // Ensure backfill is done on first access
        historyRepository.ensureBackfilled()
        
        while (true) {
            try {
                val lastData: strGlucose? = Natives.lastglucose()
                val unit = Natives.getunit()
                if (lastData != null) {
                    var value = lastData.value.toFloatOrNull() ?: 0f
                    var rawValue = 0f
                    val timeSec = lastData.time
                    
                    // Store values in mg/dL for the history database
                    var valueMgdl = value
                    var rawValueMgdl = 0f
                    
                    // Try to get raw value from history for this specific timestamp
                    val rawHistory = Natives.getGlucoseHistory(timeSec - 1)
                    if (rawHistory != null) {
                        for (i in rawHistory.indices step 3) {
                            if (i + 2 >= rawHistory.size) break
                            val hTime = rawHistory[i]
                            if (hTime == timeSec) {
                                val valueAutoRaw = rawHistory[i+1]
                                val valueRawRaw = rawHistory[i+2]
                                
                                val isMmol = (unit == 1)
                                
                                // Values from native are in mg/dL * 10
                                valueMgdl = valueAutoRaw / 10f
                                rawValueMgdl = valueRawRaw / 10f
                                
                                // Recalculate values for display
                                var v = valueMgdl
                                var r = rawValueMgdl
                                
                                if (isMmol) {
                                    v = v / 18.0182f
                                    r = r / 18.0182f
                                }
                                value = v
                                rawValue = r
                                break
                            }
                        }
                    }
                    
                    val timestampMs = lastData.time * 1000L
                    
                    // Store in Room database (always in mg/dL)
                    historyRepository.storeReading(
                        timestamp = timestampMs,
                        value = valueMgdl,
                        rawValue = rawValueMgdl,
                        rate = lastData.rate
                    )

                    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestampMs))
                    emit(GlucosePoint(value, timeStr, timestampMs, rawValue, lastData.rate))
                } else {
                    emit(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting last glucose", e)
                emit(null)
            }
            delay(15000)
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
        
        // Fetch all history (startTime = 0 means from the beginning)
        return historyRepository.getHistory(0L, isMmol)
    }

    /**
     * Get history as a Flow for reactive updates.
     * Delegates to HistoryRepository.
     */
    fun getHistoryFlow(startTime: Long = 0L, isMmol: Boolean): Flow<List<GlucosePoint>> {
        return historyRepository.getHistoryFlow(startTime, isMmol)
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