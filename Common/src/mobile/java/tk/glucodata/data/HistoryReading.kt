package tk.glucodata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing glucose readings independently from C++ sensor data.
 * Values are always stored in mg/dL and converted on display based on user preference.
 * This database persists through "wipe sensor data" operations.
 */
@Entity(
    tableName = "history_readings",
    indices = [Index(value = ["timestamp"], unique = true)]
)
data class HistoryReading(
    @PrimaryKey
    val timestamp: Long,      // Epoch milliseconds - unique key
    val value: Float,         // Calibrated/auto glucose value (mg/dL)
    val rawValue: Float,      // Raw sensor value (mg/dL)
    val rate: Float?          // Rate of change (nullable - may not always be available)
)
