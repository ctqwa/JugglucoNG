package tk.glucodata.data.journal

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC, id DESC")
    fun observeEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries ORDER BY timestamp ASC, id ASC")
    suspend fun getEntries(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp ASC, id ASC")
    suspend fun getEntriesBetween(startMillis: Long, endMillis: Long): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: JournalEntryEntity): Long

    @Delete
    suspend fun deleteEntry(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("SELECT * FROM journal_insulin_presets ORDER BY isArchived ASC, sortOrder ASC, displayName COLLATE NOCASE ASC")
    fun observeInsulinPresets(): Flow<List<JournalInsulinPresetEntity>>

    @Query("SELECT * FROM journal_insulin_presets WHERE id = :id LIMIT 1")
    suspend fun getInsulinPresetById(id: Long): JournalInsulinPresetEntity?

    @Query("SELECT * FROM journal_insulin_presets")
    suspend fun getInsulinPresets(): List<JournalInsulinPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInsulinPreset(preset: JournalInsulinPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsulinPresets(presets: List<JournalInsulinPresetEntity>)

    @Query("SELECT COUNT(*) FROM journal_insulin_presets")
    suspend fun countInsulinPresets(): Int

    @Query("DELETE FROM journal_insulin_presets WHERE id = :id")
    suspend fun deleteInsulinPresetById(id: Long)
}
