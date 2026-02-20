package tk.glucodata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for glucose history readings.
 * Multi-sensor: queries can filter by sensorSerial or return all sensors.
 */
@Dao
interface HistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reading: HistoryReading)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(readings: List<HistoryReading>)

    // ── Per-sensor queries (used for dashboard, chart, current reading) ──

    @Query("SELECT * FROM history_readings WHERE sensorSerial = :serial AND timestamp >= :startTime ORDER BY timestamp ASC")
    fun getHistoryFlowForSensor(serial: String, startTime: Long): Flow<List<HistoryReading>>

    @Query("SELECT * FROM history_readings WHERE sensorSerial = :serial AND timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getReadingsSinceForSensor(serial: String, startTime: Long): List<HistoryReading>

    @Query("SELECT * FROM history_readings WHERE sensorSerial = :serial ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReadingForSensor(serial: String): HistoryReading?

    @Query("SELECT * FROM history_readings WHERE sensorSerial = :serial ORDER BY timestamp DESC LIMIT 1")
    fun getLatestReadingFlowForSensor(serial: String): Flow<HistoryReading?>

    @Query("SELECT COUNT(*) FROM history_readings WHERE sensorSerial = :serial")
    suspend fun getCountForSensor(serial: String): Int

    @Query("SELECT MIN(timestamp) FROM history_readings WHERE sensorSerial = :serial")
    suspend fun getOldestTimestampForSensor(serial: String): Long?

    // ── All-sensor queries (used for export, global count, migration) ──

    @Query("SELECT * FROM history_readings WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getHistoryFlow(startTime: Long): Flow<List<HistoryReading>>
    
    @Query("SELECT * FROM history_readings WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    suspend fun getReadingsSince(startTime: Long): List<HistoryReading>
    
    @Query("SELECT * FROM history_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReading(): HistoryReading?

    @Query("SELECT * FROM history_readings ORDER BY timestamp DESC LIMIT 1")
    fun getLatestReadingFlow(): Flow<HistoryReading?>
    
    @Query("SELECT COUNT(*) FROM history_readings")
    suspend fun getCount(): Int
    
    @Query("SELECT MIN(timestamp) FROM history_readings")
    suspend fun getOldestTimestamp(): Long?

    @Query("SELECT DISTINCT sensorSerial FROM history_readings")
    suspend fun getAllSensorSerials(): List<String>

    // ── Cleanup queries ──

    @Query("DELETE FROM history_readings WHERE sensorSerial = :serial")
    suspend fun deleteForSensor(serial: String)

    @Query("""
        UPDATE history_readings
        SET value = :value
        WHERE sensorSerial = :sensorSerial AND timestamp = :timestamp
    """)
    suspend fun updateValueAtTime(sensorSerial: String, timestamp: Long, value: Float): Int

    @Query("""
        UPDATE history_readings SET sensorSerial = :newSerial 
        WHERE sensorSerial = :oldSerial
    """)
    suspend fun retagSensor(oldSerial: String, newSerial: String)
}
