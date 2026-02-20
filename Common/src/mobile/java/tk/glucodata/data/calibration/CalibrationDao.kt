package tk.glucodata.data.calibration

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calibration: CalibrationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calibrations: List<CalibrationEntity>)

    @Update
    suspend fun update(calibration: CalibrationEntity)

    @Delete
    suspend fun delete(calibration: CalibrationEntity)

    @Query("SELECT * FROM calibrations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CalibrationEntity>>

    @Query("SELECT * FROM calibrations ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<CalibrationEntity>

    @Query("SELECT * FROM calibrations WHERE sensorId = :sensorId ORDER BY timestamp DESC")
    fun getAllForSensor(sensorId: String): Flow<List<CalibrationEntity>>

    @Query("DELETE FROM calibrations WHERE sensorId = :sensorId")
    suspend fun deleteForSensor(sensorId: String): Int
    
    @Query("DELETE FROM calibrations")
    suspend fun deleteAll()
}
