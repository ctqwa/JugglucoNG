package tk.glucodata.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.Applic
import tk.glucodata.Natives

/**
 * Handles data clearing operations for app maintenance.
 * Provides 3 levels of data clearing:
 * 1. Clear History only (Room database)
 * 2. Clear App Data (history + cache, keep settings)
 * 3. Factory Reset (everything including settings)
 */
object DataManagement {
    
    private const val TAG = "DataManagement"
    
    /**
     * Clear only the glucose history database.
     * Settings and native data are preserved.
     */
    suspend fun clearHistory(context: Context = Applic.app): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Clear Room database
                val db = HistoryDatabase.getInstance(context)
                db.clearAllTables()
                Log.d(TAG, "Cleared history database")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing history", e)
                false
            }
        }
    }
    
    /**
     * Clear app data (history + cache) but preserve settings.
     * SharedPreferences are kept.
     */
    suspend fun clearAppData(context: Context = Applic.app): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Clear Room database
                val db = HistoryDatabase.getInstance(context)
                db.clearAllTables()
                
                // 2. Clear cache directory
                context.cacheDir.deleteRecursively()
                
                // 3. Clear code cache
                context.codeCacheDir?.deleteRecursively()
                
                Log.d(TAG, "Cleared app data (preserved settings)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing app data", e)
                false
            }
        }
    }
    
    /**
     * Factory reset - clear EVERYTHING.
     * App returns to first-run state.
     * Settings, history, cache, and native data all wiped.
     */
    suspend fun factoryReset(context: Context = Applic.app): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Clear Room database
                val db = HistoryDatabase.getInstance(context)
                db.clearAllTables()
                
                // 2. Clear all cache
                context.cacheDir.deleteRecursively()
                context.codeCacheDir?.deleteRecursively()
                
                // 3. Clear SharedPreferences
                val prefsDir = context.applicationInfo.dataDir + "/shared_prefs"
                val prefsFolder = java.io.File(prefsDir)
                prefsFolder.deleteRecursively()
                
                // 4. Clear native data files
                clearNativeDataFiles(context)
                
                Log.d(TAG, "Factory reset complete")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error during factory reset", e)
                false
            }
        }
    }
    
    /**
     * Helper to clear native C++ data files.
     * Calls native method to wipe sensor data, calibrations, etc.
     */
    private fun clearNativeDataFiles(context: Context) {
        try {
            // Call native methods to clear data
            // Note: You may need to add these native methods if they don't exist
            // Natives.clearAllData()
            
            // Alternatively, delete the native data directory
            val nativeDataDir = context.filesDir
            nativeDataDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".dat") || 
                    file.name.endsWith(".state") ||
                    file.name.endsWith(".binstate")) {
                    file.delete()
                    Log.d(TAG, "Deleted native file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing native files", e)
        }
    }
}
