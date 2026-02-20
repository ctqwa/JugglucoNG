package tk.glucodata.ui.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import tk.glucodata.Applic
import tk.glucodata.Log

/**
 * BLE scanner used by AiDex setup wizard (instance methods) and
 * Sibionics setup wizard (companion scanForSibionics).
 *
 * Edit 72: Restored working scanForSibionics() from dev-latest.
 * The previous version was an empty stub that broke Sibionics sensor discovery.
 */
class BleDeviceScanner(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScan(onResult: (ScanResult) -> Unit) {
        if (adapter?.isEnabled == true) {
            if (scanCallback != null) stopScan() // Stop existing

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { onResult(it) }
                }
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            adapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (adapter?.isEnabled == true && scanCallback != null) {
            adapter.bluetoothLeScanner?.stopScan(scanCallback)
            scanCallback = null
        }
    }

    companion object {
        private const val SCAN_DURATION = 10000L // 10 seconds
        private const val LOG_ID = "BleDeviceScanner"

        /** Check BLE scan permissions (mirrors Applic.mayscan() which is package-private) */
        private fun hasScanPermission(): Boolean {
            val ctx = Applic.app ?: return false
            if (Build.VERSION.SDK_INT >= 31) {
                return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            }
            if (Build.VERSION.SDK_INT >= 23) {
                return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }

        /**
         * Scans for Sibionics transmitters (BLE devices whose name starts with "P").
         * Returns a Flow that emits discovered device names, auto-stops after 10s.
         *
         * Edit 72: Restored from dev-latest. The previous version was an empty stub
         * that returned an empty flow, making Sibionics sensor setup impossible.
         */
        @SuppressLint("MissingPermission")
        fun scanForSibionics(): Flow<String> = callbackFlow {
            // Enforce permission check before interacting with Bluetooth adapter
            if (!hasScanPermission()) {
                Log.e(LOG_ID, "No bluetooth scan permissions, aborting scan")
                close()
                return@callbackFlow
            }

            val bluetoothManager = Applic.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter

            // Double check adapter exists and is enabled
            if (adapter == null || !adapter.isEnabled) {
                Log.e(LOG_ID, "Bluetooth adapter null or disabled")
                close()
                return@callbackFlow
            }

            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                Log.e(LOG_ID, "BluetoothLeScanner is null")
                close()
                return@callbackFlow
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Keep track of found devices to avoid duplicates in the stream
            val foundDevices = mutableSetOf<String>()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.device?.let { device ->
                        val name = device.name
                        // Filter for devices starting with "P" (common for Sibionics/SiBio transmitters)
                        if (name != null && name.startsWith("P") && !foundDevices.contains(name)) {
                            foundDevices.add(name)
                            trySend(name)
                        }
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { result ->
                        val name = result.device.name
                        if (name != null && name.startsWith("P") && !foundDevices.contains(name)) {
                            foundDevices.add(name)
                            trySend(name)
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(LOG_ID, "Scan failed with error code: $errorCode")
                    close()
                }
            }

            try {
                Log.i(LOG_ID, "Starting BLE scan for Sibionics...")
                scanner.startScan(null, settings, callback)
            } catch (e: SecurityException) {
                Log.e(LOG_ID, "SecurityException during startScan: " + e.message)
                close()
                return@callbackFlow
            } catch (e: Exception) {
                Log.e(LOG_ID, "Exception during startScan: " + e.message)
                close()
                return@callbackFlow
            }

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                try {
                    if (hasScanPermission() && adapter.isEnabled) {
                        scanner.stopScan(callback)
                    }
                } catch (e: Exception) {
                    Log.e(LOG_ID, "Error stopping scan: " + e.message)
                }
                close()
            }, SCAN_DURATION)

            awaitClose {
                try {
                    if (hasScanPermission() && adapter.isEnabled) {
                        scanner.stopScan(callback)
                    }
                } catch (e: Exception) {
                    Log.e(LOG_ID, "Error stopping scan in awaitClose: " + e.message)
                }
                handler.removeCallbacksAndMessages(null)
            }
        }
    }
}

@Composable
fun rememberBleScanner(): BleDeviceScanner {
    val context = LocalContext.current
    return remember { BleDeviceScanner(context) }
}
