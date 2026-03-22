package tk.glucodata

object CurrentGlucoseSource {
    private const val DEFAULT_MAX_AGE_MS = 15 * 60 * 1000L
    private const val SECONDS_EPOCH_CUTOFF = 10_000_000_000L

    data class Snapshot(
        val timeMillis: Long,
        val valueText: String,
        val numericValue: Float,
        val rate: Float,
        val sensorId: String?,
        val sensorGen: Int,
        val index: Int,
        val source: String
    )

    @JvmStatic
    fun normalizeTimeMillis(rawTime: Long): Long {
        if (rawTime <= 0L) {
            return rawTime
        }
        return if (rawTime < SECONDS_EPOCH_CUTOFF) rawTime * 1000L else rawTime
    }

    @JvmStatic
    fun getFresh(maxAgeMillis: Long = DEFAULT_MAX_AGE_MS): Snapshot? {
        val now = System.currentTimeMillis()

        val callback = getFromCallback(now, maxAgeMillis)
        if (callback != null) {
            return callback
        }

        return getFromNative(now, maxAgeMillis)
    }

    @JvmStatic
    fun getFresh(): Snapshot? = getFresh(DEFAULT_MAX_AGE_MS)

    private fun getFromCallback(now: Long, maxAgeMillis: Long): Snapshot? {
        val latest = SuperGattCallback.previousglucose ?: return null
        val numericValue = SuperGattCallback.previousglucosevalue
        if (!numericValue.isFinite() || numericValue < 0.1f) {
            return null
        }
        val timeMillis = normalizeTimeMillis(latest.time)
        if (kotlin.math.abs(now - timeMillis) > maxAgeMillis) {
            return null
        }
        return Snapshot(
            timeMillis = timeMillis,
            valueText = latest.value ?: "",
            numericValue = numericValue,
            rate = latest.rate,
            sensorId = SuperGattCallback.previousglucosesensorid ?: Natives.lastsensorname(),
            sensorGen = latest.sensorgen2,
            index = 0,
            source = "callback"
        )
    }

    private fun getFromNative(now: Long, maxAgeMillis: Long): Snapshot? {
        val latest = Natives.lastglucose() ?: return null
        val numericValue = GlucoseValueParser.parseFirst(latest.value)
            ?.takeIf { it.isFinite() && it > 0.1f }
            ?: return null
        val timeMillis = normalizeTimeMillis(latest.time)
        if (kotlin.math.abs(now - timeMillis) > maxAgeMillis) {
            return null
        }
        return Snapshot(
            timeMillis = timeMillis,
            valueText = latest.value ?: "",
            numericValue = numericValue,
            rate = latest.rate,
            sensorId = latest.sensorid,
            sensorGen = latest.sensorgen2,
            index = latest.index,
            source = "native"
        )
    }

    @JvmStatic
    fun getFreshNotGlucose(maxAgeMillis: Long): notGlucose? {
        val snapshot = getFresh(maxAgeMillis) ?: return null
        return notGlucose(snapshot.timeMillis, snapshot.valueText, snapshot.rate, snapshot.sensorGen)
    }

    @JvmStatic
    fun getFreshNotGlucose(): notGlucose? = getFreshNotGlucose(DEFAULT_MAX_AGE_MS)
}
