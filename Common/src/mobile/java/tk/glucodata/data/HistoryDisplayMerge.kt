package tk.glucodata.data

import tk.glucodata.SensorIdentity

internal object HistoryDisplayMerge {
    private const val OVERLAP_PADDING_MS = 5L * 60L * 1000L
    private const val COVERAGE_SEGMENT_GAP_MS = 15L * 60L * 1000L

    fun mergeReadings(
        readings: List<HistoryReading>,
        preferredSerial: String?
    ): List<HistoryReading> {
        if (readings.isEmpty()) return emptyList()

        val filtered = applyPreferredOverlapDominance(readings, preferredSerial)
        val merged = ArrayList<HistoryReading>(readings.size)
        var currentTimestamp = Long.MIN_VALUE
        var currentBest: HistoryReading? = null

        for (reading in filtered) {
            if (currentBest == null || reading.timestamp != currentTimestamp) {
                currentBest?.let(merged::add)
                currentTimestamp = reading.timestamp
                currentBest = reading
            } else {
                currentBest = choosePreferred(currentBest, reading, preferredSerial)
            }
        }

        currentBest?.let(merged::add)
        return merged
    }

    private fun applyPreferredOverlapDominance(
        readings: List<HistoryReading>,
        preferredSerial: String?
    ): List<HistoryReading> {
        if (preferredSerial.isNullOrBlank()) return readings

        val preferredReadings = readings
            .filter { SensorIdentity.matches(it.sensorSerial, preferredSerial) }
            .sortedBy { it.timestamp }
        if (preferredReadings.isEmpty()) return readings

        val coverageSegments = buildCoverageSegments(preferredReadings)

        return readings.filter { reading ->
            SensorIdentity.matches(reading.sensorSerial, preferredSerial) ||
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
        preferredSerial: String?
    ): HistoryReading {
        val currentScore = score(current, preferredSerial)
        val candidateScore = score(candidate, preferredSerial)
        if (candidateScore != currentScore) {
            return if (candidateScore > currentScore) candidate else current
        }
        return if (candidate.id > current.id) candidate else current
    }

    private fun score(reading: HistoryReading, preferredSerial: String?): Int {
        var score = 0
        if (!preferredSerial.isNullOrBlank() && SensorIdentity.matches(reading.sensorSerial, preferredSerial)) {
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
