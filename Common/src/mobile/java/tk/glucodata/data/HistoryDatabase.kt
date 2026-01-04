package tk.glucodata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for independent glucose history storage.
 * This database is separate from the C++ native sensor data and
 * persists through "wipe sensor data" operations.
 */
@Database(entities = [HistoryReading::class], version = 2, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    
    abstract fun historyDao(): HistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null
        
        fun getInstance(context: Context): HistoryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "glucose_history.db"
                )
                .fallbackToDestructiveMigration()  // Recreate DB on schema changes
                .build().also { INSTANCE = it }
            }
    }
}
