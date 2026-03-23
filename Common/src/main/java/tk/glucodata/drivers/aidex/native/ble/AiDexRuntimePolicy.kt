package tk.glucodata.drivers.aidex.native.ble

internal object AiDexRuntimePolicy {

    fun connectedWarmupStatus(
        connectionPart: String,
        anchorMs: Long,
        nowMs: Long,
        lastGlucoseTimeMs: Long,
        warmupDurationMs: Long,
        firstValidReadingWaitMaxMs: Long,
        firstValidReadingWaitActive: Boolean,
    ): String? {
        if (anchorMs <= 0L || nowMs < anchorMs) return null

        val hasValidReadingSinceStart = lastGlucoseTimeMs >= anchorMs && lastGlucoseTimeMs > 0L
        if (hasValidReadingSinceStart) return null

        val ageMs = nowMs - anchorMs
        return when {
            ageMs < warmupDurationMs -> {
                val remaining = ((warmupDurationMs - ageMs) + 59_999L) / 60_000L
                "$connectionPart — Warmup ${remaining}m"
            }
            ageMs < firstValidReadingWaitMaxMs -> {
                val remaining = ((firstValidReadingWaitMaxMs - ageMs) + 59_999L) / 60_000L
                "$connectionPart — Warmup extended ${remaining}m"
            }
            firstValidReadingWaitActive -> "$connectionPart — No valid data yet"
            else -> null
        }
    }

    fun initialAssistDelayMs(
        nowMs: Long,
        phaseStreaming: Boolean,
        pendingInitialHistoryRequest: Boolean,
        historyDownloading: Boolean,
        streamingStartedAtMs: Long,
        initialHistoryRequestDelayMs: Long,
    ): Long? {
        if (!phaseStreaming || !pendingInitialHistoryRequest || historyDownloading || streamingStartedAtMs <= 0L) {
            return null
        }
        val elapsedMs = (nowMs - streamingStartedAtMs).coerceAtLeast(0L)
        val remainingMs = initialHistoryRequestDelayMs - elapsedMs
        return remainingMs.takeIf { it > 0L }
    }

    fun shouldContinueAssistScanning(
        stop: Boolean,
        broadcastOnlyMode: Boolean,
        phaseStreaming: Boolean,
        hasRecentLiveData: Boolean,
        pendingInitialHistoryRequest: Boolean,
        historyDownloading: Boolean,
        anchorMs: Long,
        nowMs: Long,
        lastGlucoseTimeMs: Long,
        firstValidReadingWaitMaxMs: Long,
    ): Boolean {
        if (stop || broadcastOnlyMode || !phaseStreaming || hasRecentLiveData) return false
        if (pendingInitialHistoryRequest && !historyDownloading) return false
        if (anchorMs <= 0L || nowMs < anchorMs) return false
        if (lastGlucoseTimeMs >= anchorMs && lastGlucoseTimeMs > 0L) return false
        return (nowMs - anchorMs) < firstValidReadingWaitMaxMs
    }

    fun firstValidReadingWaitStatus(
        anchorMs: Long,
        nowMs: Long,
        warmupDurationMs: Long,
        firstValidReadingWaitMaxMs: Long,
    ): String? {
        if (anchorMs <= 0L || nowMs < anchorMs) return null
        val ageMs = nowMs - anchorMs
        val ageMin = ageMs / 60_000L
        return when {
            ageMs < warmupDurationMs -> {
                val remaining = ((warmupDurationMs - ageMs) + 59_999L) / 60_000L
                "age=${ageMin}m warmup=${remaining}m"
            }
            ageMs < firstValidReadingWaitMaxMs -> {
                val remaining = ((firstValidReadingWaitMaxMs - ageMs) + 59_999L) / 60_000L
                "age=${ageMin}m extended=${remaining}m"
            }
            else -> "age=${ageMin}m no-valid-data"
        }
    }

    fun shouldStartHistoryImmediately(
        pendingInitialHistoryRequest: Boolean,
        historyDownloading: Boolean,
    ): Boolean = pendingInitialHistoryRequest && !historyDownloading
}
