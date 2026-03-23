package tk.glucodata.drivers.aidex.native.ble

import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDexStreamingPolicyTests {

    @Test
    fun shouldRefreshLiveCccdsAfterKeyExchange_falseForAlreadyBondedReconnect() {
        assertFalse(
            AiDexStreamingPolicy.shouldRefreshLiveCccdsAfterKeyExchange(
                bondStateAtConnection = BluetoothDevice.BOND_BONDED,
                bondBecameBondedThisConnection = false,
            )
        )
    }

    @Test
    fun shouldRefreshLiveCccdsAfterKeyExchange_trueWhenBondStateChangedThisConnection() {
        assertTrue(
            AiDexStreamingPolicy.shouldRefreshLiveCccdsAfterKeyExchange(
                bondStateAtConnection = BluetoothDevice.BOND_BONDED,
                bondBecameBondedThisConnection = true,
            )
        )
    }

    @Test
    fun shouldRefreshLiveCccdsAfterKeyExchange_trueWhenNotBondedAtConnectionStart() {
        assertTrue(
            AiDexStreamingPolicy.shouldRefreshLiveCccdsAfterKeyExchange(
                bondStateAtConnection = BluetoothDevice.BOND_NONE,
                bondBecameBondedThisConnection = false,
            )
        )
    }

    @Test
    fun resolveNoStreamWatchdogDelayMs_extendsWhenRecentHistoryExists() {
        val delayMs = AiDexStreamingPolicy.resolveNoStreamWatchdogDelayMs(
            defaultDelayMs = 25_000L,
            nowMs = 200_000L,
            latestKnownReadingMs = 170_000L,
            expectedLiveIntervalMs = 60_000L,
            expectedLiveGraceMs = 20_000L,
        )

        assertEquals(50_000L, delayMs)
    }

    @Test
    fun resolveNoStreamWatchdogDelayMs_usesDefaultWithoutRecentHistory() {
        val delayMs = AiDexStreamingPolicy.resolveNoStreamWatchdogDelayMs(
            defaultDelayMs = 25_000L,
            nowMs = 200_000L,
            latestKnownReadingMs = 0L,
            expectedLiveIntervalMs = 60_000L,
            expectedLiveGraceMs = 20_000L,
        )

        assertEquals(25_000L, delayMs)
    }
}
