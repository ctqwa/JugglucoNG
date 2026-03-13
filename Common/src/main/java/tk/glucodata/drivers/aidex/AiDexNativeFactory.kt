// JugglucoNG — AiDex Native Kotlin Driver
// AiDexNativeFactory.kt — Factory bridge for Java ↔ Kotlin `native` package
//
// Java cannot import from a package named `native` (reserved keyword).
// This factory lives in tk.glucodata.drivers.aidex (Java-accessible) and
// delegates to classes in tk.glucodata.drivers.aidex.native.ble (Kotlin-only).
//
// SensorBluetooth.java uses this to conditionally create AiDexBleManager
// instead of the vendor-lib-based AiDexSensor.

package tk.glucodata.drivers.aidex

import android.content.Context
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.SuperGattCallback
import tk.glucodata.drivers.aidex.native.ble.AiDexBleManager

/**
 * Factory bridge that SensorBluetooth.java can call without importing from
 * the `native` package (which Java cannot do).
 *
 * Most UI code now uses `instanceof AiDexDriver` (the shared interface) to
 * detect either driver implementation. These factory helpers remain for:
 * - Creating native driver instances (createBleManager)
 * - Checking native mode preference (isNativeModeEnabled)
 * - Specifically identifying the native driver (isNativeAiDex)
 *
 * Usage from Java:
 *   AiDexNativeFactory.INSTANCE.isNativeModeEnabled(context)
 *   AiDexNativeFactory.INSTANCE.createBleManager(serial, dataptr)
 *   AiDexNativeFactory.INSTANCE.isNativeAiDex(callback)
 */
object AiDexNativeFactory {

    private const val TAG = "AiDexNativeFactory"
    private const val PREF_FILE = "tk.glucodata_preferences"
    private const val PREF_KEY = "aidex_native_driver_enabled"

    /**
     * Check whether the native Kotlin driver is enabled (SharedPreferences toggle).
     * Defaults to false — opt-in for now.
     */
    @JvmStatic
    fun isNativeModeEnabled(context: Context? = null): Boolean {
        val ctx = context ?: Applic.app ?: return false
        return try {
            val prefs = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            prefs.getBoolean(PREF_KEY, false)
        } catch (t: Throwable) {
            Log.stack(TAG, "isNativeModeEnabled", t)
            false
        }
    }

    /**
     * Create an [AiDexBleManager] (native Kotlin driver) as a [SuperGattCallback].
     *
     * Drop-in replacement for `new AiDexSensor(context, serial, dataptr)`.
     * The sensorGen is set to 0 (conventional value for AiDex).
     *
     * @param serial Sensor serial number (with or without "X-" prefix)
     * @param dataptr Native data pointer from Natives.getdataptr(serial)
     * @return A SuperGattCallback backed by the native Kotlin driver
     */
    @JvmStatic
    fun createBleManager(serial: String, dataptr: Long): SuperGattCallback {
        Log.i(TAG, "Creating native AiDexBleManager for $serial (dataptr=$dataptr)")
        return AiDexBleManager(serial, dataptr, 0)
    }

    /**
     * Check whether a [SuperGattCallback] is an instance of our native [AiDexBleManager].
     *
     * Java code can use `instanceof AiDexDriver` to check for *any* AiDex driver,
     * but this method specifically identifies the native Kotlin driver (vs vendor-lib).
     */
    @JvmStatic
    fun isNativeAiDex(callback: SuperGattCallback?): Boolean {
        return callback is AiDexBleManager
    }

    /**
     * Check whether a native AiDex callback is in broadcast-only mode.
     *
     * Prefer using `(callback as? AiDexDriver)?.broadcastOnlyConnection` when possible.
     * This method remains for Java code that needs a static helper.
     *
     * Returns false if the callback is not an AiDexBleManager.
     */
    @JvmStatic
    fun isBroadcastOnly(callback: SuperGattCallback?): Boolean {
        val mgr = callback as? AiDexBleManager ?: return false
        return mgr.broadcastOnlyConnection
    }
}
