package tk.glucodata.drivers.anytime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CT2 profile table: per-prefix warmupMinutes (initNumber × 3 min) and
 * ratedLifetimeDays (endNumber × 3 min). SN08 confirmed live 2026-09-07:
 * warmup 20×3 = 60 min, lifetime 6720×3 = 20160 min = 14.0 days.
 */
class AnytimeCt2ProfileTests {

    private val ct2Prefixes = listOf(
        "SN04", "SN06", "SN08", "SN12", "SN18", "SN20", "SN22", "SN48", "SN50", "SN52",
    )

    @Test
    fun everyCt2PrefixResolvesToCt2Family() {
        for (prefix in ct2Prefixes) {
            val profile = AnytimeProfileResolver.resolve("$prefix-device")
            assertEquals("$prefix must be CT2", AnytimeConstants.Family.CT2, profile.family)
        }
    }

    @Test
    fun sn06AndSn12Have180MinuteWarmup() {
        assertEquals(180, AnytimeProfileResolver.resolve("SN06-x").warmupMinutes)
        assertEquals(180, AnytimeProfileResolver.resolve("SN12-x").warmupMinutes)
    }

    @Test
    fun otherCt2PrefixesHave60MinuteWarmup() {
        for (prefix in ct2Prefixes.filter { it !in setOf("SN06", "SN12") }) {
            assertEquals("$prefix warmup", 60, AnytimeProfileResolver.resolve("$prefix-x").warmupMinutes)
        }
    }

    @Test
    fun sn08Has60MinuteWarmupAnd14DayLife() {
        val profile = AnytimeProfileResolver.resolve("SN08-device")
        assertEquals(60, profile.warmupMinutes)
        assertEquals(14, profile.ratedLifetimeDays)
        assertEquals(6720, profile.endNumber)
    }

    @Test
    fun ct2CadenceIsThreeMinutes() {
        for (prefix in ct2Prefixes) {
            assertEquals(3, AnytimeProfileResolver.resolve("$prefix-x").readingIntervalMinutes)
        }
    }
}
