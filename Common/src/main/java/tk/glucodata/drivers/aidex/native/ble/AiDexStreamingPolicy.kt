package tk.glucodata.drivers.aidex.native.ble

import android.bluetooth.BluetoothDevice

internal object AiDexStreamingPolicy {

    fun shouldRefreshLiveCccdsAfterKeyExchange(
        bondStateAtConnection: Int,
        bondBecameBondedThisConnection: Boolean,
    ): Boolean {
        return bondStateAtConnection != BluetoothDevice.BOND_BONDED || bondBecameBondedThisConnection
    }

    fun resolveNoStreamWatchdogDelayMs(
        defaultDelayMs: Long,
        nowMs: Long,
        latestKnownReadingMs: Long,
        expectedLiveIntervalMs: Long,
        expectedLiveGraceMs: Long,
    ): Long {
        if (latestKnownReadingMs <= 0L) return defaultDelayMs
        val waitUntil = latestKnownReadingMs + expectedLiveIntervalMs + expectedLiveGraceMs
        val historyAwareDelay = (waitUntil - nowMs).takeIf { it > 0L } ?: 0L
        return maxOf(defaultDelayMs, historyAwareDelay)
    }
}
