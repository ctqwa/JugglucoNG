package tk.glucodata.data

import java.util.LinkedHashSet

internal object HistoryBucketReplacement {
    data class Plan(
        val bucketIds: List<Long>,
        val protectedTimestamps: List<Long>,
    )

    fun plan(
        readings: List<HistoryReading>,
        bucketDurationMs: Long,
    ): Plan? {
        if (readings.isEmpty() || bucketDurationMs <= 0L) return null

        val protectedTimestamps = LinkedHashSet<Long>(readings.size)
        val bucketIds = LinkedHashSet<Long>(readings.size)

        for (reading in readings) {
            val timestamp = reading.timestamp
            if (timestamp <= 0L) continue
            protectedTimestamps.add(timestamp)
            bucketIds.add(timestamp / bucketDurationMs)
        }

        if (protectedTimestamps.isEmpty() || bucketIds.isEmpty()) return null

        return Plan(
            bucketIds = bucketIds.toList(),
            protectedTimestamps = protectedTimestamps.toList(),
        )
    }
}
