package tk.glucodata

import tk.glucodata.drivers.ManagedSensorIdentityRegistry

object SensorIdentity {
    private fun normalized(sensorId: String?): String? {
        return sensorId?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun canonicalOrRaw(sensorId: String?): String? {
        val raw = normalized(sensorId) ?: return null
        return resolveAppSensorId(raw) ?: raw
    }

    @JvmStatic
    fun invalidateCaches() {
        // main keeps no identity cache by default; managed adapters call this
        // when persisted identity state changes.
    }

    @JvmStatic
    fun resolveAppSensorId(sensorId: String?): String? {
        val raw = normalized(sensorId) ?: return null
        return ManagedSensorIdentityRegistry.all
            .asSequence()
            .mapNotNull { it.resolveCanonicalSensorId(raw) }
            .firstOrNull { it.isNotBlank() }
            ?: raw
    }

    @JvmStatic
    fun resolveNativeSensorName(sensorId: String?): String? {
        val raw = normalized(sensorId) ?: return null
        return ManagedSensorIdentityRegistry.all
            .asSequence()
            .mapNotNull { it.resolveNativeSensorName(raw) }
            .firstOrNull { it.isNotBlank() }
            ?: raw
    }

    @JvmStatic
    fun shouldUseNativeHistorySync(sensorId: String?): Boolean {
        val raw = normalized(sensorId) ?: return true
        val canonical = canonicalOrRaw(raw) ?: raw
        return ManagedSensorIdentityRegistry.shouldUseNativeHistorySync(canonical)
            ?: ManagedSensorIdentityRegistry.shouldUseNativeHistorySync(raw)
            ?: true
    }

    @JvmStatic
    fun resolveMainSensor(): String? {
        val main = resolveAppSensorId(Natives.lastsensorname())
        if (!main.isNullOrBlank()) {
            return main
        }
        return Natives.activeSensors()
            ?.asSequence()
            ?.mapNotNull(::resolveAppSensorId)
            ?.firstOrNull { !it.isNullOrBlank() }
    }

    @JvmStatic
    fun resolveLiveMainSensor(preferredSensorId: String?): String? {
        val activeSensors = Natives.activeSensors()
        if (activeSensors.isNullOrEmpty()) {
            return resolveMainSensor()
        }
        return resolveAvailableMainSensor(
            selectedMain = Natives.lastsensorname(),
            preferredSensorId = preferredSensorId,
            activeSensors = activeSensors
        ) ?: resolveMainSensor()
    }

    @JvmStatic
    fun resolveAvailableMainSensor(
        selectedMain: String?,
        preferredSensorId: String?,
        activeSensors: Array<String?>?
    ): String? {
        val active = activeSensors
            ?.mapNotNull(::canonicalOrRaw)
            ?.distinct()
            .orEmpty()
        val canonicalSelected = canonicalOrRaw(selectedMain)
        val canonicalPreferred = canonicalOrRaw(preferredSensorId)

        if (active.isEmpty()) {
            return canonicalSelected ?: canonicalPreferred
        }

        if (canonicalSelected != null && active.any { matches(it, canonicalSelected) }) {
            return canonicalSelected
        }

        if (canonicalPreferred != null && active.any { matches(it, canonicalPreferred) }) {
            return canonicalPreferred
        }

        return active.firstOrNull()
    }

    @JvmStatic
    fun matches(candidate: String?, expected: String?): Boolean {
        if (expected.isNullOrBlank()) {
            return true
        }
        val left = resolveAppSensorId(candidate) ?: return false
        val right = resolveAppSensorId(expected) ?: return false
        return left.equals(right, ignoreCase = true)
    }
}
