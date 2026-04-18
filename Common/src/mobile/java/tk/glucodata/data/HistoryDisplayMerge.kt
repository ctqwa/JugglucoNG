package tk.glucodata.data

import tk.glucodata.SensorIdentity

internal object HistoryDisplayMerge {
    private const val SENSOR_MINUTE_BUCKET_MS = 60_000L
    private const val OVERLAP_PADDING_MS = 5L * 60L * 1000L
    private const val COVERAGE_SEGMENT_GAP_MS = 15L * 60L * 1000L

    private data class LogicalSensorBucket(
        val sensorId: String,
        val bucket: Long
    )

    private class PreferredMatchResolver(preferredSerial: String?) {
        private val canonicalPreferred = SensorIdentity.resolveAppSensorId(preferredSerial)
        private val matchCache = HashMap<String, Boolean>()

        fun matches(sensorSerial: String?): Boolean {
            val preferred = canonicalPreferred ?: return false
            val raw = sensorSerial?.trim()?.takeIf { it.isNotEmpty() } ?: return false
            return matchCache.getOrPut(raw) {
                SensorIdentity.matches(raw, preferred)
            }
        }
    }

    fun mergeReadings(
        readings: List<HistoryReading>,
        preferredSerial: String?
    ): List<HistoryReading> {
        if (readings.isEmpty()) return emptyList()

        val resolver = PreferredMatchResolver(preferredSerial)
        val coalesced = collapseLogicalSensorBuckets(readings, resolver)
        val filtered = applyPreferredOverlapDominance(coalesced, resolver)
        val merged = ArrayList<HistoryReading>(readings.size)
        var currentTimestamp = Long.MIN_VALUE
        var currentBest: HistoryReading? = null

        for (reading in filtered) {
            if (currentBest == null || reading.timestamp != currentTimestamp) {
                currentBest?.let(merged::add)
                currentTimestamp = reading.timestamp
                currentBest = reading
            } else {
                currentBest = choosePreferred(currentBest, reading, resolver)
            }
        }

        currentBest?.let(merged::add)
        return merged
    }

    private fun collapseLogicalSensorBuckets(
        readings: List<HistoryReading>,
        resolver: PreferredMatchResolver
    ): List<HistoryReading> {
        val byBucket = LinkedHashMap<LogicalSensorBucket, HistoryReading>(readings.size)
        for (reading in readings) {
            val sensorSerial = reading.sensorSerial?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val resolvedSensorId = SensorIdentity.resolveAppSensorId(sensorSerial) ?: sensorSerial
            val key = LogicalSensorBucket(
                sensorId = resolvedSensorId,
                bucket = reading.timestamp / SENSOR_MINUTE_BUCKET_MS
            )
            val existing = byBucket[key]
            byBucket[key] = if (existing == null) reading else choosePreferred(existing, reading, resolver)
        }
        return byBucket.values.sortedBy { it.timestamp }
    }

    private fun applyPreferredOverlapDominance(
        readings: List<HistoryReading>,
        resolver: PreferredMatchResolver
    ): List<HistoryReading> {
        val preferredReadings = readings
            .filter { resolver.matches(it.sensorSerial) }
            .sortedBy { it.timestamp }
        if (preferredReadings.isEmpty()) return readings

        val coverageSegments = buildCoverageSegments(preferredReadings)

        return readings.filter { reading ->
            resolver.matches(reading.sensorSerial) ||
                coverageSegments.none { segment ->
                    reading.timestamp >= (segment.start - OVERLAP_PADDING_MS) &&
                        reading.timestamp <= (segment.last + OVERLAP_PADDING_MS)
                }
        }
    }

    private fun buildCoverageSegments(readings: List<HistoryReading>): List<LongRange> {
        if (readings.isEmpty()) return emptyList()

        val segments = ArrayList<LongRange>()
        var segmentStart = readings.first().timestamp
        var segmentEnd = segmentStart

        for (index in 1 until readings.size) {
            val timestamp = readings[index].timestamp
            if ((timestamp - segmentEnd) > COVERAGE_SEGMENT_GAP_MS) {
                segments.add(segmentStart..segmentEnd)
                segmentStart = timestamp
            }
            segmentEnd = timestamp
        }

        segments.add(segmentStart..segmentEnd)
        return segments
    }

    private fun choosePreferred(
        current: HistoryReading,
        candidate: HistoryReading,
        resolver: PreferredMatchResolver
    ): HistoryReading {
        val currentScore = score(current, resolver)
        val candidateScore = score(candidate, resolver)
        if (candidateScore != currentScore) {
            return if (candidateScore > currentScore) candidate else current
        }
        return if (candidate.id > current.id) candidate else current
    }

    private fun score(reading: HistoryReading, resolver: PreferredMatchResolver): Int {
        var score = 0
        if (resolver.matches(reading.sensorSerial)) {
            score += 100
        }
        if (reading.value.isFinite() && reading.value > 0f) {
            score += 10
        }
        if (reading.rawValue.isFinite() && reading.rawValue > 0f) {
            score += 5
        }
        if (reading.rate != null && reading.rate.isFinite()) {
            score += 1
        }
        return score
    }
}
