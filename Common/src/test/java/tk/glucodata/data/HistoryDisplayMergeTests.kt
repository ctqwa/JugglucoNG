package tk.glucodata.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryDisplayMergeTests {
    private companion object {
        const val HOUR_MS = 60L * 60L * 1000L
        const val MINUTE_MS = 60L * 1000L
    }

    @Test
    fun mergeReadings_keepsOlderNonConflictingRowsAndPrefersCurrentSensorOnConflict() {
        val merged = HistoryDisplayMerge.mergeReadings(
            readings = listOf(
                reading(id = 1, timestamp = 1 * HOUR_MS, sensorSerial = "sensor-old", value = 100f, rawValue = 95f),
                reading(id = 2, timestamp = 2 * HOUR_MS, sensorSerial = "sensor-old", value = 110f, rawValue = 104f),
                reading(id = 3, timestamp = 2 * HOUR_MS, sensorSerial = "sensor-new", value = 111f, rawValue = 105f),
                reading(id = 4, timestamp = 3 * HOUR_MS, sensorSerial = "sensor-new", value = 120f, rawValue = 114f)
            ),
            preferredSerial = "sensor-new"
        )

        assertEquals(listOf(1 * HOUR_MS, 2 * HOUR_MS, 3 * HOUR_MS), merged.map { it.timestamp })
        assertEquals(listOf("sensor-old", "sensor-new", "sensor-new"), merged.map { it.sensorSerial })
    }

    @Test
    fun mergeReadings_withoutPreferredSensorChoosesRicherReadingForSameTimestamp() {
        val merged = HistoryDisplayMerge.mergeReadings(
            readings = listOf(
                reading(id = 1, timestamp = 2 * HOUR_MS, sensorSerial = "sensor-a", value = 110f, rawValue = 0f),
                reading(id = 2, timestamp = 2 * HOUR_MS, sensorSerial = "sensor-b", value = 0f, rawValue = 108f),
                reading(id = 3, timestamp = 2 * HOUR_MS, sensorSerial = "sensor-c", value = 111f, rawValue = 109f)
            ),
            preferredSerial = null
        )

        assertEquals(1, merged.size)
        assertEquals("sensor-c", merged.single().sensorSerial)
        assertEquals(111f, merged.single().value, 0.001f)
    }

    @Test
    fun mergeReadings_dropsOverlappingOlderSensorRangeWhenPreferredSensorHasCoverage() {
        val merged = HistoryDisplayMerge.mergeReadings(
            readings = listOf(
                reading(id = 1, timestamp = 1 * HOUR_MS, sensorSerial = "sensor-old", value = 100f, rawValue = 95f),
                reading(id = 2, timestamp = 2 * HOUR_MS + 42 * MINUTE_MS, sensorSerial = "sensor-old", value = 101f, rawValue = 96f),
                reading(id = 3, timestamp = 2 * HOUR_MS + 58 * MINUTE_MS, sensorSerial = "sensor-old", value = 102f, rawValue = 97f),
                reading(id = 4, timestamp = 2 * HOUR_MS + 40 * MINUTE_MS, sensorSerial = "sensor-new", value = 120f, rawValue = 115f),
                reading(id = 5, timestamp = 2 * HOUR_MS + 50 * MINUTE_MS, sensorSerial = "sensor-new", value = 121f, rawValue = 116f),
                reading(id = 6, timestamp = 3 * HOUR_MS, sensorSerial = "sensor-new", value = 122f, rawValue = 117f)
            ).sortedBy { it.timestamp },
            preferredSerial = "sensor-new"
        )

        assertEquals(
            listOf(
                1 * HOUR_MS,
                2 * HOUR_MS + 40 * MINUTE_MS,
                2 * HOUR_MS + 50 * MINUTE_MS,
                3 * HOUR_MS
            ),
            merged.map { it.timestamp }
        )
        assertEquals(
            listOf("sensor-old", "sensor-new", "sensor-new", "sensor-new"),
            merged.map { it.sensorSerial }
        )
    }

    @Test
    fun mergeReadings_keepsOlderRowsAcrossLargePreferredSensorGap() {
        val merged = HistoryDisplayMerge.mergeReadings(
            readings = listOf(
                reading(id = 1, timestamp = 1 * HOUR_MS, sensorSerial = "sensor-old", value = 100f, rawValue = 95f),
                reading(id = 2, timestamp = 2 * HOUR_MS, sensorSerial = "sensor-new", value = 120f, rawValue = 115f),
                reading(id = 3, timestamp = 3 * HOUR_MS, sensorSerial = "sensor-old", value = 101f, rawValue = 96f),
                reading(id = 4, timestamp = 4 * HOUR_MS, sensorSerial = "sensor-new", value = 121f, rawValue = 116f)
            ).sortedBy { it.timestamp },
            preferredSerial = "sensor-new"
        )

        assertEquals(
            listOf(1 * HOUR_MS, 2 * HOUR_MS, 3 * HOUR_MS, 4 * HOUR_MS),
            merged.map { it.timestamp }
        )
        assertEquals(
            listOf("sensor-old", "sensor-new", "sensor-old", "sensor-new"),
            merged.map { it.sensorSerial }
        )
    }

    private fun reading(
        id: Long,
        timestamp: Long,
        sensorSerial: String,
        value: Float,
        rawValue: Float
    ) = HistoryReading(
        id = id,
        timestamp = timestamp,
        sensorSerial = sensorSerial,
        value = value,
        rawValue = rawValue,
        rate = null
    )
}
