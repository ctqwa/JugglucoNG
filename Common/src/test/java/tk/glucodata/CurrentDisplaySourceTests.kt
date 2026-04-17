package tk.glucodata

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentDisplaySourceTests {

    @Test
    fun resolveFromLive_usesLiveRawWhenExactHistoryPointLacksRawLane() {
        val timestamp = 1_700_000_000_000L
        val recentPoints = listOf(GlucosePoint(timestamp, 75f, 0f))

        val snapshot = CurrentDisplaySource.resolveFromLive(
            liveValueText = null,
            liveNumericValue = 75f,
            liveRawValue = 31f,
            rate = 0f,
            targetTimeMillis = timestamp,
            sensorId = "8760080A00070000",
            sensorGen = 0,
            index = 0,
            source = "test",
            recentPoints = recentPoints,
            viewMode = 1,
            isMmol = false
        )

        requireNotNull(snapshot)
        assertEquals(31f, snapshot.rawValue)
        assertEquals(31f, snapshot.primaryValue)
        assertEquals(75f, snapshot.autoValue)
    }
}
