package tk.glucodata.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tk.glucodata.Applic
import tk.glucodata.Natives

/**
 * Singleton that synchronizes native glucose history with the Room database.
 * 
 * This is the ONLY source of data for the Room database.
 * Called from DashboardViewModel.refreshData() when data needs to be displayed.
 */
object HistorySync {
    
    private const val TAG = "HistorySync"
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val historyRepository = HistoryRepository(Applic.app)
    
    /**
     * Sync ALL available data from native layer to Room.
     * Uses deduplication (IGNORE on conflict) so duplicates are ignored.
     */
    @JvmOverloads
    fun syncFromNative(forceFull: Boolean = false) {
        scope.launch {
            try {
                // INCREMENTAL SYNC OPTIMIZATION
                var startSec = 0L
                
                if (!forceFull) {
                    // 1. Get the latest timestamp we already have stored
                    val lastTimestamp = historyRepository.getLatestTimestamp()
                    // 2. Fetch only NEW readings from native 
                    // If lastTimestamp is 0, we fetch everything (0L)
                    // Otherwise we fetch from (lastTimestamp / 1000) + 1 to avoid re-fetching the last point
                    startSec = if (lastTimestamp > 0) (lastTimestamp / 1000L) + 1 else 0L
                }
                
                val rawHistory = Natives.getGlucoseHistory(startSec)
                if (rawHistory == null) {
                    // No new data is a valid state for incremental sync
                    // Log.v(TAG, "No new data from native") 
                    return@launch
                }
                
                val readings = mutableListOf<HistoryReading>()
                
                for (i in rawHistory.indices step 3) {
                    if (i + 2 >= rawHistory.size) break
                    
                    val timeSec = rawHistory[i]
                    val timeMs = timeSec * 1000L
                    
                    // Sanity check: Ensure we really are newer if doing incremental
                    // (Native logic should handle this, but double-check)
                    // Only check if we are NOT forcing full (if forcing full, we want everything)
                    if (!forceFull && startSec > 0) {
                         // We can also check against repository, but startSec > 0 implies we had a timestamp
                         // The query startSec is inclusive, but let's be safe against duplicates (DAO handles them anyway)
                         // We just need to make sure we don't process OLD data if native returned it by mistake
                    }
                    
                    val valueAutoRaw = rawHistory[i + 1]
                    val valueRawRaw = rawHistory[i + 2]
                    
                    // Values from native are in mg/dL * 10
                    val value = valueAutoRaw / 10f
                    val rawValue = valueRawRaw / 10f
                    
                    // Rate is not available in historical ScanData, only in real-time GlucoseNow
                    // Leave as null - the database field is nullable
                    
                    if (value > 0 || rawValue > 0) {
                        readings.add(HistoryReading(
                            timestamp = timeMs,
                            value = value,
                            rawValue = rawValue,
                            rate = null  // ScanData doesn't store rate - only available in real-time
                        ))
                    }
                }
                
                if (readings.isNotEmpty()) {
                    historyRepository.storeReadings(readings)
                    val type = if (forceFull) "Full" else "Incremental"
                    Log.d(TAG, "Synced ${readings.size} readings ($type from $startSec)")
                } else {
                    // Log.d(TAG, "Up to date")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing from native", e)
            }
        }
    }
    
    /**
     * Force a full resync.
     * Useful after sensor data wipe + reconnect.
     */
    fun forceFullSync() {
        syncFromNative(forceFull = true)
    }
}
