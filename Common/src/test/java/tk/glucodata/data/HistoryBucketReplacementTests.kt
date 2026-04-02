package tk.glucodata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryBucketReplacementTests {

    @Test
    fun plan_groupsReadingsIntoDistinctBucketsAndPreservesTimestampOrder() {
        val plan = HistoryBucketReplacement.plan(
            readings = listOf(
                HistoryReading(timestamp = 60_000L, sensorSerial = "sensor", value = 100f, rawValue = 100f, rate = null),
                HistoryReading(timestamp = 61_000L, sensorSerial = "sensor", value = 101f, rawValue = 101f, rate = null),
                HistoryReading(timestamp = 119_999L, sensorSerial = "sensor", value = 102f, rawValue = 102f, rate = null),
                HistoryReading(timestamp = 120_000L, sensorSerial = "sensor", value = 103f, rawValue = 103f, rate = null),
                HistoryReading(timestamp = 120_000L, sensorSerial = "sensor", value = 103f, rawValue = 103f, rate = null),
            ),
            bucketDurationMs = 60_000L,
        )

        requireNotNull(plan)
        assertEquals(listOf(1L, 2L), plan.bucketIds)
        assertEquals(listOf(60_000L, 61_000L, 119_999L, 120_000L), plan.protectedTimestamps)
    }

    @Test
    fun plan_returnsNullWhenNoValidTimestampsRemain() {
        val plan = HistoryBucketReplacement.plan(
            readings = listOf(
                HistoryReading(timestamp = 0L, sensorSerial = "sensor", value = 100f, rawValue = 100f, rate = null),
                HistoryReading(timestamp = -1L, sensorSerial = "sensor", value = 101f, rawValue = 101f, rate = null),
            ),
            bucketDurationMs = 60_000L,
        )

        assertNull(plan)
    }
}
