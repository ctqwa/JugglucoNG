package tk.glucodata

import android.util.Log
import androidx.annotation.Keep

@Keep
object CalibrationProfileAccess {
    private const val TAG = "CalibrationProfileAccess"
    private const val CLASS_NAME = "tk.glucodata.data.calibration.CalibrationManager"

    private val holder by lazy { runCatching { Class.forName(CLASS_NAME) }.getOrNull() }
    private val instance by lazy { runCatching { holder?.getField("INSTANCE")?.get(null) }.getOrNull() }
    private val exportMethod by lazy {
        runCatching { holder?.getMethod("exportProfileForSensorAsJson", String::class.java) }.getOrNull()
    }
    private val importMirrorMethod by lazy {
        runCatching {
            holder?.getMethod(
                "importMirrorProfileFromJsonBlocking",
                String::class.java,
                String::class.java
            )
        }.getOrNull()
    }

    @JvmStatic
    fun exportProfileForSensorAsJson(sensorId: String?): String? {
        if (sensorId.isNullOrBlank()) return null
        val method = exportMethod
        val manager = instance
        if (method == null || manager == null) {
            Log.w(TAG, "exportProfileForSensorAsJson unavailable for sensor=$sensorId")
            return null
        }
        return runCatching {
            method.invoke(manager, sensorId) as? String
        }.onFailure {
            Log.w(TAG, "exportProfileForSensorAsJson failed for sensor=$sensorId", it)
        }.getOrNull()
    }

    @JvmStatic
    fun importMirrorProfileFromJson(json: String?, overrideSensorId: String? = null): Boolean {
        if (json.isNullOrBlank()) return false
        val method = importMirrorMethod
        val manager = instance
        if (method == null || manager == null) {
            Log.w(TAG, "importMirrorProfileFromJson unavailable for sensor=$overrideSensorId")
            return false
        }
        return runCatching {
            method.invoke(manager, json, overrideSensorId)
            true
        }.onFailure {
            Log.w(TAG, "importMirrorProfileFromJson failed for sensor=$overrideSensorId", it)
        }.getOrDefault(false)
    }
}
