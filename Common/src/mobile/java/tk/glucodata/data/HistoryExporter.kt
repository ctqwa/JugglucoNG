package tk.glucodata.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.Natives
import tk.glucodata.ui.GlucosePoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HistoryExporter {
    private const val TAG = "HistoryExporter"
    private const val RECORD_TYPE_GLUCOSE = "glucose"
    private const val RECORD_TYPE_JOURNAL_ENTRY = "journal_entry"
    private const val RECORD_TYPE_INSULIN_PRESET = "journal_insulin_preset"

    // Use a unified date format for CSV to ensure re-import consistency
    // ISO 8601 is best: yyyy-MM-dd HH:mm:ss
    private val CSV_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    // Friendly format for "Readable" export
    private val READABLE_DATE_FORMAT = SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale.getDefault())

    private fun csvCell(value: Any?): String {
        val text = value?.toString() ?: ""
        return "\"${text.replace("\"", "\"\"")}\""
    }

    private fun formatExportFloat(value: Float?): String {
        return value?.let { String.format(Locale.US, "%.4f", it) }.orEmpty()
    }

    private suspend fun loadExportJournalEntries(
        journalDao: tk.glucodata.data.journal.JournalDao,
        data: List<GlucosePoint>,
        startMillis: Long?,
        endMillis: Long?
    ): List<tk.glucodata.data.journal.JournalEntryEntity> {
        val rangeStart = startMillis ?: data.minOfOrNull { it.timestamp }
        val rangeEnd = endMillis ?: data.maxOfOrNull { it.timestamp }
        return if (rangeStart != null && rangeEnd != null) {
            journalDao.getEntriesBetween(rangeStart, rangeEnd)
        } else {
            journalDao.getEntries()
        }
    }

    /**
     * Export data to a CSV file.
     * Format: Timestamp(ms),Date,Value,RawValue,Unit,SensorSerial
     * Values are always exported in the User's preferred unit for consistency with what they see.
     * Multi-sensor: includes SensorSerial column for re-import with proper tagging.
     */
    suspend fun exportToCsv(
        context: Context,
        uri: Uri,
        data: List<GlucosePoint>,
        unit: String,
        startMillis: Long? = null,
        endMillis: Long? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get all readings from Room to access sensorSerial
                val database = HistoryDatabase.getInstance(context)
                val dao = database.historyDao()
                val journalDao = database.journalDao()
                // Build a map of timestamp -> sensorSerial for enriching export
                val allReadings = dao.getReadingsSince(0L)
                val serialByTimestamp = HashMap<Long, String>(allReadings.size)
                for (reading in allReadings) {
                    serialByTimestamp[reading.timestamp] = reading.sensorSerial
                }
                val journalEntries = loadExportJournalEntries(journalDao, data, startMillis, endMillis)
                val insulinPresets = journalDao.getInsulinPresets()

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        // Header — new format with SensorSerial
                        writer.write(
                            "Timestamp,Date,Value,RawValue,Unit,SensorSerial,RecordType," +
                                "JournalId,JournalType,JournalTitle,JournalNote,JournalAmount,JournalGlucoseMgDl," +
                                "JournalDurationMinutes,JournalIntensity,JournalInsulinPresetId,JournalSource," +
                                "JournalSourceRecordId,JournalCreatedAt,JournalUpdatedAt," +
                                "PresetId,PresetName,PresetOnsetMinutes,PresetDurationMinutes,PresetAccentColor," +
                                "PresetCurveJson,PresetBuiltIn,PresetArchived,PresetCountsTowardIob,PresetSortOrder\n"
                        )
                        
                        // Data
                        for (point in data) {
                            val dateStr = CSV_DATE_FORMAT.format(Date(point.timestamp))
                            // Ensure dot decimal separator for CSV
                            val valueStr = tk.glucodata.ui.util.GlucoseFormatter.formatCsv(point.value, unit)
                            val rawStr = tk.glucodata.ui.util.GlucoseFormatter.formatCsv(point.rawValue, unit)
                            val serial = serialByTimestamp[point.timestamp] ?: "unknown"
                            
                            writer.write("${point.timestamp},$dateStr,$valueStr,$rawStr,$unit,$serial,$RECORD_TYPE_GLUCOSE\n")
                        }
                        for (entry in journalEntries) {
                            val dateStr = CSV_DATE_FORMAT.format(Date(entry.timestamp))
                            writer.write(
                                listOf(
                                    entry.timestamp,
                                    dateStr,
                                    "",
                                    "",
                                    "",
                                    entry.sensorSerial.orEmpty(),
                                    RECORD_TYPE_JOURNAL_ENTRY,
                                    entry.id,
                                    entry.entryType,
                                    entry.title,
                                    entry.note.orEmpty(),
                                    formatExportFloat(entry.amount),
                                    formatExportFloat(entry.glucoseValueMgDl),
                                    entry.durationMinutes ?: "",
                                    entry.intensity.orEmpty(),
                                    entry.insulinPresetId ?: "",
                                    entry.source,
                                    entry.sourceRecordId.orEmpty(),
                                    entry.createdAt,
                                    entry.updatedAt
                                ).joinToString(",") { csvCell(it) } + "\n"
                            )
                        }
                        for (preset in insulinPresets) {
                            writer.write(
                                listOf(
                                    0,
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    RECORD_TYPE_INSULIN_PRESET,
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    "",
                                    preset.id,
                                    preset.displayName,
                                    preset.onsetMinutes,
                                    preset.durationMinutes,
                                    preset.accentColor,
                                    preset.curveJson,
                                    preset.isBuiltIn,
                                    preset.isArchived,
                                    preset.countsTowardIob,
                                    preset.sortOrder
                                ).joinToString(",") { csvCell(it) } + "\n"
                            )
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting to CSV", e)
                false
            }
        }
    }

    /**
     * Export data to a human-readable text file.
     * Format: Mon, 01 Jan 2024 12:00: 5.5 mmol/L (Raw: 5.4) [SensorSerial]
     */
    suspend fun exportToReadable(
        context: Context,
        uri: Uri,
        data: List<GlucosePoint>,
        unit: String,
        startMillis: Long? = null,
        endMillis: Long? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get serial map for enriching export
                val database = HistoryDatabase.getInstance(context)
                val dao = database.historyDao()
                val journalDao = database.journalDao()
                val allReadings = dao.getReadingsSince(0L)
                val serialByTimestamp = HashMap<Long, String>(allReadings.size)
                for (reading in allReadings) {
                    serialByTimestamp[reading.timestamp] = reading.sensorSerial
                }
                val journalEntries = loadExportJournalEntries(journalDao, data, startMillis, endMillis)
                val insulinPresets = journalDao.getInsulinPresets()

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write("JugglucoNG Glucose History Export\n")
                        writer.write("Generated on: ${READABLE_DATE_FORMAT.format(Date())}\n")
                        writer.write("Total Readings: ${data.size}\n\n")
                        
                        for (point in data) {
                            val dateStr = READABLE_DATE_FORMAT.format(Date(point.timestamp))
                            val isMmol = tk.glucodata.ui.util.GlucoseFormatter.isMmol(unit)
                            val valueStr = tk.glucodata.ui.util.GlucoseFormatter.format(point.value, isMmol)
                            val rawStr = tk.glucodata.ui.util.GlucoseFormatter.format(point.rawValue, isMmol)
                            val serial = serialByTimestamp[point.timestamp] ?: ""
                            
                            val sensorTag = if (serial.isNotEmpty() && serial != "unknown") " [$serial]" else ""
                            val line = "$dateStr: $valueStr $unit (Raw: $rawStr)$sensorTag\n"
                            writer.write(line)
                        }
                        if (journalEntries.isNotEmpty()) {
                            writer.write("\nJournal Entries: ${journalEntries.size}\n")
                            for (entry in journalEntries) {
                                val dateStr = READABLE_DATE_FORMAT.format(Date(entry.timestamp))
                                val amount = entry.amount?.let { " · $it" }.orEmpty()
                                val glucose = entry.glucoseValueMgDl?.let { " · ${it.toInt()} mg/dL" }.orEmpty()
                                val note = entry.note?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                                writer.write("$dateStr: ${entry.entryType} · ${entry.title}$amount$glucose$note\n")
                            }
                        }
                        if (insulinPresets.isNotEmpty()) {
                            writer.write("\nInsulin Presets: ${insulinPresets.size}\n")
                            for (preset in insulinPresets) {
                                val archived = if (preset.isArchived) "archived" else "enabled"
                                val iob = if (preset.countsTowardIob) "IOB" else "no IOB"
                                writer.write(
                                    "${preset.displayName}: ${preset.onsetMinutes}-${preset.durationMinutes} min · " +
                                        "$archived · $iob · color ${preset.accentColor}\n"
                                )
                            }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting to text", e)
                false
            }
        }
    }

    /**
     * Import data from a CSV file.
     * Handles both old format (5 columns: Timestamp,Date,Value,RawValue,Unit)
     * and new format (6 columns: Timestamp,Date,Value,RawValue,Unit,SensorSerial).
     *
     * Old format: defaults sensorSerial to current main sensor.
     * New format: uses the SensorSerial column from the CSV.
     *
     * Internal storage is ALWAYS mg/dL.
     */
    suspend fun importFromCsv(context: Context, uri: Uri): ImportResult {
        return withContext(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            val readings = mutableListOf<HistoryReading>()
            // Default serial for old-format CSVs that don't have a SensorSerial column
            val defaultSerial = Natives.lastsensorname() ?: "imported"

            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        // Read Header
                        val header = reader.readLine()
                        if (header == null || !header.startsWith("Timestamp")) {
                            return@withContext ImportResult(0, 0, false, "Invalid CSV format")
                        }
                        val headerColumns = header.split(",")
                        // Detect new format by checking if header has SensorSerial
                        val hasSerialColumn = headerColumns.contains("SensorSerial")
                        val recordTypeIndex = headerColumns.indexOf("RecordType")

                        reader.forEachLine { line ->
                            try {
                                val parts = line.split(",")
                                if (parts.size >= 5) {
                                    val recordType = if (recordTypeIndex >= 0 && parts.size > recordTypeIndex) {
                                        parts[recordTypeIndex].trim().trim('"').ifBlank { RECORD_TYPE_GLUCOSE }
                                    } else {
                                        RECORD_TYPE_GLUCOSE
                                    }
                                    if (recordType != RECORD_TYPE_GLUCOSE) {
                                        return@forEachLine
                                    }
                                    val timestamp = parts[0].toLong()
                                    // parts[1] is Date string, skip
                                    var value = parts[2].toFloat()
                                    var rawValue = parts[3].toFloat()
                                    val unit = parts[4].trim()

                                    // Convert back to mg/dL if needed
                                    if (unit == "mmol/L") {
                                        value *= 18.0182f
                                        rawValue *= 18.0182f
                                    }

                                    // Use serial from CSV if available, otherwise default
                                    val serial = if (hasSerialColumn && parts.size >= 6) {
                                        parts[5].trim().ifEmpty { defaultSerial }
                                    } else {
                                        defaultSerial
                                    }

                                    readings.add(HistoryReading(
                                        timestamp = timestamp,
                                        sensorSerial = serial,
                                        value = value,
                                        rawValue = rawValue,
                                        rate = 0f
                                    ))
                                    successCount++
                                }
                            } catch (e: Exception) {
                                failCount++
                            }
                        }
                    }
                }

                if (readings.isNotEmpty()) {
                    HistoryRepository(context).storeReadings(readings)
                }
                
                ImportResult(successCount, failCount, true, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error importing CSV", e)
                ImportResult(0, 0, false, e.message)
            }
        }
    }

    data class ImportResult(
        val successCount: Int,
        val failCount: Int,
        val success: Boolean,
        val errorMessage: String?
    )
}
