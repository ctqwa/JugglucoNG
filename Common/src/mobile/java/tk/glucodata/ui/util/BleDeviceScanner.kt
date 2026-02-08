package tk.glucodata.ui.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
            adapter.bluetoothLeScanner?.startScan(scanCallback)
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
        fun scanForSibionics(): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
            // Placeholder: Emit known devices or scan results if implemented
            // For now, return empty flow to satisfy compilation
        }
    }
}

@Composable
fun rememberBleScanner(): BleDeviceScanner {
    val context = LocalContext.current
    return remember { BleDeviceScanner(context) }
}
