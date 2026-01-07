package tk.glucodata.ui.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import tk.glucodata.Applic

object BleDeviceScanner {
    private const val SCAN_DURATION = 10000L // 10 seconds

    @SuppressLint("MissingPermission")
    fun scanForSibionics(): Flow<String> = callbackFlow {
        val bluetoothManager = Applic.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val scanner = adapter.bluetoothLeScanner

        if (scanner == null || !adapter.isEnabled) {
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
                    // Filter for devices starting with "P" as requested (common for Sibionics/SiBio)
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
                close()
            }
        }

        scanner.startScan(null, settings, callback)

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            scanner.stopScan(callback)
            close()
        }, SCAN_DURATION)

        awaitClose {
            scanner.stopScan(callback)
            handler.removeCallbacksAndMessages(null)
        }
    }
}
