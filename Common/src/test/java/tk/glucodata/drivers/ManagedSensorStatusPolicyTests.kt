package tk.glucodata.drivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedSensorStatusPolicyTests {
    @Test
    fun collapseSummaryStatus_suppressesPassiveConnectionText() {
        assertEquals("", ManagedSensorStatusPolicy.collapseSummaryStatus("Connected"))
        assertEquals("", ManagedSensorStatusPolicy.collapseSummaryStatus("Connected (polling)"))
        assertEquals("", ManagedSensorStatusPolicy.collapseSummaryStatus("Searching for sensors"))
    }

    @Test
    fun collapseSummaryStatus_keepsMeaningfulActionStatus() {
        assertEquals("Syncing...", ManagedSensorStatusPolicy.collapseSummaryStatus("Syncing..."))
        assertEquals("No Data", ManagedSensorStatusPolicy.collapseSummaryStatus("No Data"))
    }

    @Test
    fun resolveLifecycleSummary_prefersDriverRemainingHoursAndExpectedEnd() {
        val start = 1_000L
        val expectedEnd = start + (21L * 24L * 60L * 60L * 1000L)
        val summary = ManagedSensorStatusPolicy.resolveLifecycleSummary(
            startTimeMs = start,
            officialEndMs = 0L,
            expectedEndMs = expectedEnd,
            sensorRemainingHours = 13,
            sensorAgeHours = 19 * 24,
            nowMs = expectedEnd + (5L * 60L * 60L * 1000L),
        )

        assertEquals("20 / 21", summary.daysText)
        assertEquals(20, summary.currentDay)
        assertEquals(13L, summary.remainingHours)
        assertTrue(summary.progress > 0.8f)
    }

    @Test
    fun resolveLifecycleSummary_clampsNegativeRemainingHours() {
        val start = 10_000L
        val end = start + (15L * 24L * 60L * 60L * 1000L)
        val summary = ManagedSensorStatusPolicy.resolveLifecycleSummary(
            startTimeMs = start,
            officialEndMs = end,
            expectedEndMs = 0L,
            sensorRemainingHours = -1,
            sensorAgeHours = -1,
            nowMs = end + (142L * 60L * 60L * 1000L),
        )

        assertEquals(0L, summary.remainingHours)
        assertEquals("15 / 15", summary.daysText)
        assertFalse(summary.progress < 0f)
    }
}
