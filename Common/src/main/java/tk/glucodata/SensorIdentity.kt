package tk.glucodata

object SensorIdentity {
    private fun normalized(sensorId: String?): String? {
        return sensorId?.trim()?.takeIf { it.isNotEmpty() }
    }

    @JvmStatic
    fun resolveMainSensor(): String? {
        val main = Natives.lastsensorname()
        if (!main.isNullOrBlank()) {
            return main
        }
        return Natives.activeSensors()?.firstOrNull { !it.isNullOrBlank() }
    }

    @JvmStatic
    fun resolveLiveMainSensor(preferredSensorId: String?): String? {
        val activeSensors = Natives.activeSensors()
            ?.mapNotNull(::normalized)
            ?.distinct()
            .orEmpty()
        if (activeSensors.isEmpty()) {
            return resolveMainSensor()
        }

        val selectedMain = normalized(Natives.lastsensorname())
        if (selectedMain != null && activeSensors.any { matches(it, selectedMain) }) {
            return selectedMain
        }

        val preferred = normalized(preferredSensorId)
        if (preferred != null && activeSensors.any { matches(it, preferred) }) {
            return preferred
        }

        return activeSensors.firstOrNull()
    }

    @JvmStatic
    fun matches(candidate: String?, expected: String?): Boolean {
        if (expected.isNullOrBlank()) {
            return true
        }
        val left = normalized(candidate) ?: return false
        val right = normalized(expected) ?: return false
        return left.equals(right, ignoreCase = true)
    }
}
