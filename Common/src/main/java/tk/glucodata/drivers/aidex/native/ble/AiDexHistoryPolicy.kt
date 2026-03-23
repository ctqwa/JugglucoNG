package tk.glucodata.drivers.aidex.native.ble

internal object AiDexHistoryPolicy {

    enum class InitialAction {
        COMPLETE_EMPTY,
        REQUEST_RAW,
        REQUEST_BRIEF,
        COMPLETE_ALREADY_CAUGHT_UP,
    }

    data class DownloadPlan(
        val rawNextIndex: Int,
        val briefNextIndex: Int,
        val downloadStartIndex: Int,
        val action: InitialAction,
        val requestOffset: Int? = null,
    )

    fun planInitialDownload(
        briefStart: Int,
        rawStart: Int,
        newest: Int,
        persistedRawNextIndex: Int,
        persistedBriefNextIndex: Int,
    ): DownloadPlan {
        if (briefStart == 0 && rawStart == 0 && newest == 0) {
            return DownloadPlan(
                rawNextIndex = 0,
                briefNextIndex = 0,
                downloadStartIndex = 0,
                action = InitialAction.COMPLETE_EMPTY,
            )
        }

        val rawNextIndex = normalizePersistedIndex(
            persistedIndex = persistedRawNextIndex,
            startIndex = rawStart,
            newest = newest,
        )
        val briefNextIndex = normalizePersistedIndex(
            persistedIndex = persistedBriefNextIndex,
            startIndex = briefStart,
            newest = newest,
        )

        return when {
            rawNextIndex <= newest -> DownloadPlan(
                rawNextIndex = rawNextIndex,
                briefNextIndex = briefNextIndex,
                downloadStartIndex = rawNextIndex,
                action = InitialAction.REQUEST_RAW,
                requestOffset = rawNextIndex,
            )
            briefNextIndex <= newest -> DownloadPlan(
                rawNextIndex = rawNextIndex,
                briefNextIndex = briefNextIndex,
                downloadStartIndex = rawNextIndex,
                action = InitialAction.REQUEST_BRIEF,
                requestOffset = briefNextIndex,
            )
            else -> DownloadPlan(
                rawNextIndex = rawNextIndex,
                briefNextIndex = briefNextIndex,
                downloadStartIndex = rawNextIndex,
                action = InitialAction.COMPLETE_ALREADY_CAUGHT_UP,
            )
        }
    }

    fun shouldEmitCatchUpBroadcast(
        lastHistoryNewestGlucose: Float,
        lastHistoryNewestOffset: Int,
        liveOffsetCutoff: Int,
    ): Boolean {
        return lastHistoryNewestGlucose > 0f &&
            lastHistoryNewestOffset > 0 &&
            (liveOffsetCutoff == 0 || lastHistoryNewestOffset > liveOffsetCutoff)
    }

    fun shouldSkipHistoryEntryForLiveDedupe(
        entryOffsetMinutes: Int,
        liveOffsetCutoff: Int,
    ): Boolean {
        if (liveOffsetCutoff <= 0) return false

        // The current connection logic only guarantees that the live pipeline
        // has already stored the exact first live minute seen before history
        // starts. Reconnect history can legitimately contain newer offsets that
        // were not yet emitted as direct F003, so skipping >= cutoff drops
        // real backfill rows. Only skip the exact live-backed offset.
        return entryOffsetMinutes == liveOffsetCutoff
    }

    private fun normalizePersistedIndex(
        persistedIndex: Int,
        startIndex: Int,
        newest: Int,
    ): Int {
        var normalized = maxOf(persistedIndex, startIndex)
        if (normalized > newest + 10) {
            normalized = startIndex
        }
        return normalized
    }
}
