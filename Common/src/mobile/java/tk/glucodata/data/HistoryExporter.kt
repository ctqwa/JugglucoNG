package tk.glucodata.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.ui.GlucosePoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HistoryExporter {
    private const val TAG = "HistoryExporter"

    // Use a unified date format for CSV to ensure re-import consistency
    // ISO 8601 is best: yyyy-MM-dd HH:mm:ss
    private val CSV_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    // Friendly format for "Readable" export
    private val READABLE_DATE_FORMAT = SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale.getDefault())

    /**
     * Export data to a CSV file.
     * Format: Timestamp(ms),Date,Value,RawValue,Unit
     * Values are always exported in the User's preferred unit for consistency with what they see.
     */
    suspend fun exportToCsv(context: Context, uri: Uri, data: List<GlucosePoint>, unit: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        // Header
                        writer.write("Timestamp,Date,Value,RawValue,Unit\n")
                        
                        // Data
                        for (point in data) {
                            val dateStr = CSV_DATE_FORMAT.format(Date(point.timestamp))
                            // Ensure dot decimal separator for CSV
                            val valueStr = tk.glucodata.ui.util.GlucoseFormatter.formatCsv(point.value, unit)
                            val rawStr = tk.glucodata.ui.util.GlucoseFormatter.formatCsv(point.rawValue, unit)
                            
                            writer.write("${point.timestamp},$dateStr,$valueStr,$rawStr,$unit\n")
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
     * Format: Mon, 01 Jan 2024 12:00: 5.5 mmol/L (Raw: 5.4)
     */
    suspend fun exportToReadable(context: Context, uri: Uri, data: List<GlucosePoint>, unit: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
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
                            
                            val line = "$dateStr: $valueStr $unit (Raw: $rawStr)\n"
                            writer.write(line)
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
     * Expected Format via exportToCsv.
     * Note: This assumes file follows our export format. 
     * Handles unit conversion if the file unit differs from internal storage (mg/dL).
     * Internal storage is ALWAYS mg/dL.
     */
    suspend fun importFromCsv(context: Context, uri: Uri): ImportResult {
        return withContext(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            val readings = mutableListOf<HistoryReading>()

            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        // Read Header
                        val header = reader.readLine()
                        if (header == null || !header.startsWith("Timestamp")) {
                            return@withContext ImportResult(0, 0, false, "Invalid CSV format")
                        }

                        reader.forEachLine { line ->
                            try {
                                val parts = line.split(",")
                                if (parts.size >= 5) {
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

                                    readings.add(HistoryReading(timestamp, value, rawValue, 0f))
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
