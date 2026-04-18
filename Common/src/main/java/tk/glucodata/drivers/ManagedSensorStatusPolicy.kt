package tk.glucodata.drivers

data class ManagedSensorLifecycleSummary(
    val progress: Float,
    val daysText: String,
    val currentDay: Int,
    val remainingHours: Long,
)

object ManagedSensorStatusPolicy {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val HOUR_MS = 60L * 60L * 1000L

    private val passiveSummaryStatuses = setOf(
        "ready",
        "connected",
        "disconnected",
        "receiving",
        "broadcast",
        "broadcast mode",
        "connected (broadcast)",
        "connected (polling)",
        "connected (initializing)",
        "connected, waiting for data...",
        "waiting for data...",
        "reconnecting...",
        "connecting...",
        "pairing...",
        "bonding...",
        "discovering services...",
        "setting up notifications...",
        "handshaking...",
        "scanning...",
        "searching for sensors",
        "waiting for broadcast...",
    )

    @JvmStatic
    fun collapseSummaryStatus(status: String?): String {
        val normalized = status?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return ""
        }
        return if (isPassiveSummaryStatus(normalized)) "" else normalized
    }

    @JvmStatic
    fun isPassiveSummaryStatus(status: String?): Boolean {
        val normalized = status?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) {
            return true
        }
        return normalized in passiveSummaryStatuses
    }

    @JvmStatic
    @JvmOverloads
    fun resolveLifecycleSummary(
        startTimeMs: Long,
        officialEndMs: Long,
        expectedEndMs: Long,
        sensorRemainingHours: Int = -1,
        sensorAgeHours: Int = -1,
        fallbackDurationDays: Int = 14,
        nowMs: Long = System.currentTimeMillis(),
    ): ManagedSensorLifecycleSummary {
        if (startTimeMs <= 0L) {
            return ManagedSensorLifecycleSummary(0f, "", 0, 999L)
        }

        val fallbackEndMs = startTimeMs + (fallbackDurationDays.toLong() * DAY_MS)
        val endMs = when {
            expectedEndMs > startTimeMs -> expectedEndMs
            officialEndMs > startTimeMs -> officialEndMs
            else -> fallbackEndMs
        }
        val totalMs = (endMs - startTimeMs).coerceAtLeast(1L)

        val remainingHours = sensorRemainingHours
            .takeIf { it >= 0 }
            ?.toLong()
            ?.coerceAtLeast(0L)
            ?: ((endMs - nowMs).coerceAtLeast(0L) / HOUR_MS)

        val usedMs = (totalMs - (remainingHours * HOUR_MS)).coerceIn(0L, totalMs)
        val progress = (usedMs.toFloat() / totalMs).coerceIn(0f, 1f)
        val totalDays = ((totalMs + DAY_MS - 1L) / DAY_MS).coerceAtLeast(1L)
        val computedCurrentDay = sensorAgeHours
            .takeIf { it >= 0 }
            ?.let { (it / 24L) + 1L }
            ?: ((usedMs / DAY_MS) + 1L)
        val currentDay = computedCurrentDay.coerceIn(1L, totalDays).toInt()

        return ManagedSensorLifecycleSummary(
            progress = progress,
            daysText = "$currentDay / $totalDays",
            currentDay = currentDay,
            remainingHours = remainingHours,
        )
    }
}
