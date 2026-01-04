package tk.glucodata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for glucose history readings.
 * Provides efficient insert and query operations.
 */
@Dao
interface HistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: HistoryReading)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<HistoryReading>)
    
    @Query("SELECT * FROM history_readings WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getHistoryFlow(startTime: Long): Flow<List<HistoryReading>>
    
    @Query("SELECT * FROM history_readings WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getReadingsSince(startTime: Long): List<HistoryReading>
    
    @Query("SELECT * FROM history_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReading(): HistoryReading?
    
    @Query("SELECT COUNT(*) FROM history_readings")
    suspend fun getCount(): Int
    
    @Query("SELECT MIN(timestamp) FROM history_readings")
    suspend fun getOldestTimestamp(): Long?
}
