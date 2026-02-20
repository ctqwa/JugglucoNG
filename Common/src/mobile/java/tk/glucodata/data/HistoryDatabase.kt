package tk.glucodata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for independent glucose history storage.
 * This database is separate from the C++ native sensor data and
 * persists through "wipe sensor data" operations.
 *
 * Version history:
 *   v2 — original single-sensor schema (timestamp PK, value, rawValue, rate)
 *   v3 — multi-sensor: added sensorSerial column, auto-generated PK, composite unique index
 */
@Database(entities = [HistoryReading::class], version = 3, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    
    abstract fun historyDao(): HistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        /**
         * Migration v2 → v3: Add sensorSerial column for multi-sensor support.
         *
         * Strategy: recreate the table with the new schema and copy existing data,
         * assigning all old rows to a default sensor serial "unknown".
         * A full re-sync from native will later re-tag them correctly.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the new table with auto-generated PK and sensorSerial
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS history_readings_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        sensorSerial TEXT NOT NULL DEFAULT 'unknown',
                        value REAL NOT NULL,
                        rawValue REAL NOT NULL,
                        rate REAL
                    )
                """.trimIndent())
                
                // Copy existing data, defaulting sensorSerial to 'unknown'
                db.execSQL("""
                    INSERT INTO history_readings_new (timestamp, sensorSerial, value, rawValue, rate)
                    SELECT timestamp, 'unknown', value, rawValue, rate FROM history_readings
                """.trimIndent())
                
                // Drop old table and rename new one
                db.execSQL("DROP TABLE history_readings")
                db.execSQL("ALTER TABLE history_readings_new RENAME TO history_readings")
                
                // Create indices
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_history_readings_timestamp_sensorSerial ON history_readings (timestamp, sensorSerial)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_readings_sensorSerial ON history_readings (sensorSerial)")
            }
        }
        
        fun getInstance(context: Context): HistoryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "glucose_history.db"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()  // Fallback if migration chain is broken
                .build().also { INSTANCE = it }
            }
    }
}
