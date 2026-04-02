package tk.glucodata.drivers.aidex.native.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDexHistoryPolicyTests {

    @Test
    fun planInitialDownload_completesImmediatelyForEmptyRange() {
        val plan = AiDexHistoryPolicy.planInitialDownload(
            briefStart = 0,
            rawStart = 0,
            newest = 0,
            persistedRawNextIndex = 0,
            persistedBriefNextIndex = 0,
        )

        assertEquals(AiDexHistoryPolicy.InitialAction.COMPLETE_EMPTY, plan.action)
        assertEquals(0, plan.rawNextIndex)
        assertEquals(0, plan.briefNextIndex)
    }

    @Test
    fun planInitialDownload_rewindsPersistedOffsetsThatAreAheadOfNewest() {
        val plan = AiDexHistoryPolicy.planInitialDownload(
            briefStart = 10,
            rawStart = 12,
            newest = 40,
            persistedRawNextIndex = 120,
            persistedBriefNextIndex = 140,
        )

        assertEquals(AiDexHistoryPolicy.InitialAction.REQUEST_RAW, plan.action)
        assertEquals(12, plan.rawNextIndex)
        assertEquals(10, plan.briefNextIndex)
        assertEquals(12, plan.requestOffset)
    }

    @Test
    fun planInitialDownload_skipsToBriefWhenRawIsAlreadyCaughtUp() {
        val plan = AiDexHistoryPolicy.planInitialDownload(
            briefStart = 10,
            rawStart = 12,
            newest = 40,
            persistedRawNextIndex = 41,
            persistedBriefNextIndex = 20,
        )

        assertEquals(AiDexHistoryPolicy.InitialAction.REQUEST_BRIEF, plan.action)
        assertEquals(41, plan.rawNextIndex)
        assertEquals(20, plan.briefNextIndex)
        assertEquals(20, plan.requestOffset)
    }

    @Test
    fun planInitialDownload_completesWhenBothTracksAreAlreadyCaughtUp() {
        val plan = AiDexHistoryPolicy.planInitialDownload(
            briefStart = 10,
            rawStart = 12,
            newest = 40,
            persistedRawNextIndex = 41,
            persistedBriefNextIndex = 41,
        )

        assertEquals(AiDexHistoryPolicy.InitialAction.COMPLETE_ALREADY_CAUGHT_UP, plan.action)
    }

    @Test
    fun shouldEmitCatchUpBroadcast_onlyWhenHistoryIsNewerThanLiveCutoff() {
        assertTrue(
            AiDexHistoryPolicy.shouldEmitCatchUpBroadcast(
                lastHistoryNewestGlucose = 100f,
                lastHistoryNewestOffset = 50,
                liveOffsetCutoff = 49,
            )
        )
        assertFalse(
            AiDexHistoryPolicy.shouldEmitCatchUpBroadcast(
                lastHistoryNewestGlucose = 100f,
                lastHistoryNewestOffset = 50,
                liveOffsetCutoff = 50,
            )
        )
    }

    @Test
    fun shouldSkipHistoryEntryForLiveDedupe_onlySkipsExactLiveOffset() {
        assertTrue(
            AiDexHistoryPolicy.shouldSkipHistoryEntryForLiveDedupe(
                entryOffsetMinutes = 469,
                liveOffsetCutoff = 469,
            )
        )
        assertFalse(
            AiDexHistoryPolicy.shouldSkipHistoryEntryForLiveDedupe(
                entryOffsetMinutes = 470,
                liveOffsetCutoff = 469,
            )
        )
        assertFalse(
            AiDexHistoryPolicy.shouldSkipHistoryEntryForLiveDedupe(
                entryOffsetMinutes = 468,
                liveOffsetCutoff = 469,
            )
        )
    }

    @Test
    fun resolveOffsetBackedTimestampMs_prefersAlignedTimestampWhenStartAndOffsetAreKnown() {
        val resolved = AiDexHistoryPolicy.resolveOffsetBackedTimestampMs(
            observedAtMs = 1_000_000L,
            sensorStartMs = 100_000L,
            offsetMinutes = 15,
        )

        assertEquals(1_000_000L, resolved)
    }

    @Test
    fun resolveOffsetBackedTimestampMs_fallsBackWhenOffsetTimestampWouldBeTooFarInFuture() {
        val resolved = AiDexHistoryPolicy.resolveOffsetBackedTimestampMs(
            observedAtMs = 1_000_000L,
            sensorStartMs = 900_000L,
            offsetMinutes = 10,
        )

        assertEquals(1_000_000L, resolved)
    }
}
