package tk.glucodata.aidex

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.data.HistoryRepository
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor
import kotlin.math.abs

/**
 * A dedicated probe tool to reverse engineer the AiDex/Micro Tech Medical
 * sensor protocol.
 * This class scans for devices, connects to them, discovers services, and logs
 * all communication to Logcat with the tag "AIDEX_RAW".
 *
 * Converted to Kotlin to resolve compilation visibility issues.
 */
class AiDexProbe private constructor() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastSeed: ByteArray? = null
    
    // Track the last valid glucose value for adaptive logic
    // Default to 90 mg/dL (~5.0 mmol/L) as a safe starting point
    private var lastValidGlucose: Float = 90f

    companion object {
        private const val TAG = "AIDEX_RAW"
        private const val SCAN_PERIOD: Long = 10000
        
        @Volatile
        private var instance: AiDexProbe? = null

        @JvmStatic
        fun getInstance(): AiDexProbe {
            return instance ?: synchronized(this) {
                instance ?: AiDexProbe().also { instance = it }
            }
        }

        private fun hexStringToByteArray(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4) +
                        Character.digit(s[i + 1], 16)).toByte()
            }
            return data
        }

        private fun bytesToHex(bytes: ByteArray?): String {
            if (bytes == null) return "null"
            val sb = StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02X ", b))
            }
            return sb.toString()
        }
    }

    @SuppressLint("MissingPermission")
    fun startProbe() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            Applic.Toaster("Enable Bluetooth first!")
            return
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }

        if (!isScanning) {
            scanLeDevice()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopProbe() {
        if (isScanning && bluetoothLeScanner != null) {
            isScanning = false
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.i(TAG, "Scan stopped manually")
        }
        if (bluetoothGatt != null) {
            bluetoothGatt?.close()
            bluetoothGatt = null
            Log.i(TAG, "Gatt closed manually")
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice() {
        if (!isScanning) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                if (isScanning) {
                    isScanning = false
                    bluetoothLeScanner?.stopScan(scanCallback)
                    Log.i(TAG, "Scan stopped after timeout")
                }
            }, SCAN_PERIOD)

            isScanning = true
            bluetoothLeScanner?.startScan(scanCallback)
            Log.i(TAG, "Scan started...")
            Applic.Toaster("AiDex Probe: Scanning...")
        } else {
            isScanning = false
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.i(TAG, "Scan stopped")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            @SuppressLint("MissingPermission")
            val deviceName = device.name
            val address = device.address
            val rssi = result.rssi

            Log.d(TAG, "Found device: $deviceName [$address] RSSI: $rssi")

            // Heuristic to find the sensor
            if (deviceName != null && (deviceName.lowercase().contains("aidex") || deviceName.lowercase().contains("meter"))) {
                connectToDevice(device)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (result in results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (isScanning) {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }

        Log.i(TAG, "Connecting to ${device.address}")
        bluetoothGatt = device.connectGatt(Applic.app, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.")
                @SuppressLint("MissingPermission")
                val success = gatt.discoverServices()
                Log.i(TAG, "Attempting to start service discovery: $success")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered:")
                val service = gatt.getService(UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb"))

                if (service != null) {
                    val chars = service.characteristics
                    for (c in chars) {
                        if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            enableNotification(gatt, c)
                        }
                        if ((c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                            enableIndication(gatt, c)
                        }
                    }

                    // Handshake Initiation on F002
                    val ctrlChar = service.getCharacteristic(UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb"))
                    if (ctrlChar != null) {
                        Log.i(TAG, "Auth Skipped. Scheduling Official Handshake (Step 1) on F002 in 1.5s...")
                        handler.postDelayed({
                            handshakeStep = 1
                            writeHandshakeStep(gatt, ctrlChar)
                        }, 1500)
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val uuid = characteristic.uuid.toString()
            Log.i(TAG, "WRITE $uuid status: $status")

            if (uuid == "0000f002-0000-1000-8000-00805f9b34fb") {
                if (handshakeStep > 0 && handshakeStep < 9) {
                    handshakeStep++
                    val service = gatt.getService(UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb"))
                    if (service != null) {
                        val ctrlChar = service.getCharacteristic(UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb"))
                        writeHandshakeStep(gatt, ctrlChar)
                    }
                } else if (handshakeStep == 9) {
                    Log.i(TAG, "Handshake Sequence Complete! Waiting for Data...")
                    handshakeStep = 0
                }
            }
        }

        private var handshakeStep = 0
        private fun writeHandshakeStep(gatt: BluetoothGatt, ctrlChar: BluetoothGattCharacteristic) {
            var cmd: ByteArray? = null
            when (handshakeStep) {
                1 -> cmd = hexStringToByteArray("55FB0631")
                2 -> cmd = hexStringToByteArray("54FB3702")
                3 -> cmd = hexStringToByteArray("711AAB")
                4 -> cmd = hexStringToByteArray("422AAD")
                5 -> cmd = hexStringToByteArray("43BA4C847E")
                6 -> cmd = hexStringToByteArray("44C14CB72F")
                7 -> cmd = hexStringToByteArray("802454")
                8 -> cmd = hexStringToByteArray("81FB486A48")
                9 -> cmd = hexStringToByteArray("826674")
            }
            if (cmd != null) {
                Log.i(TAG, "Handshake Step $handshakeStep: Writing ${bytesToHex(cmd)}")
                ctrlChar.value = cmd
                ctrlChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @SuppressLint("MissingPermission")
                val success = gatt.writeCharacteristic(ctrlChar)
                Log.i(TAG, "Write Initiated: $success")

                // Blind chaining fallback
                if (success && handshakeStep < 9) {
                    val nextStep = handshakeStep + 1
                    handler.postDelayed({
                        if (handshakeStep != 0) {
                            handshakeStep = nextStep
                            writeHandshakeStep(gatt, ctrlChar)
                        }
                    }, 200)
                } else if (success && handshakeStep == 9) {
                    Log.i(TAG, "Handshake Sequence Complete (Blind)! Waiting for Data...")
                    handshakeStep = 0
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val uuid = characteristic.uuid.toString()
            val data = characteristic.value
            val hexData = bytesToHex(data)
            Log.i(TAG, "NOTIFY/INDICATE $uuid -> $hexData")

            if (uuid == "0000f003-0000-1000-8000-00805f9b34fb") {
                if (data.size == 5) {
                    lastSeed = data
                    Log.i(TAG, "CAPTURED SEED (F003): $hexData")
                    val seedAck = ByteArray(8)
                    System.arraycopy(data, 0, seedAck, 0, 5)
                    seedAck[0] = (seedAck[0] xor 0x01)
                    Log.i(TAG, "Writing Seed Ack: ${bytesToHex(seedAck)}")
                    characteristic.value = seedAck
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @SuppressLint("MissingPermission")
                    gatt.writeCharacteristic(characteristic)
                } else if (data.size >= 16) {
                    try {
                        val encrypted = ByteArray(16)
                        var skip = if (data.size >= 17) 1 else 0
                        if (data.size < 16 + skip) skip = 0
                        System.arraycopy(data, skip, encrypted, 0, 16)
                        
                        val masterKey = hexStringToByteArray("AC4C8ECDD8761B512EEB95D707942912")
                        val ivZero = ByteArray(16) // All zeros
                        val pt = aesDecryptCFB(masterKey, ivZero, encrypted)

                        if (pt != null) {
                            val hexPt = bytesToHex(pt)
                            val pType = pt[0].toInt() and 0xFF
                            val b3 = pt[3].toInt() and 0xFF

                            Log.e(TAG, String.format("AIDEX-DEC [Type %02X] Val3: %d | Bytes: %s", pType, b3, hexPt))

                            // ADAPTIVE DECRYPTION STRATEGY
                            // Candidates: Index 3 (Raw/Scaled), Index 5 (Raw/Scaled)
                            val candidates = floatArrayOf(
                                (pt[3].toInt() and 0xFF).toFloat(),       // Index 3 Raw
                                (pt[3].toInt() and 0xFF) / 2.0f,         // Index 3 Scaled
                                (pt[5].toInt() and 0xFF).toFloat(),       // Index 5 Raw
                                (pt[5].toInt() and 0xFF) / 2.0f          // Index 5 Scaled
                            )

                            var bestArg = -1f
                            var minDiff = Float.MAX_VALUE

                            // Initialize if needed
                            if (lastValidGlucose < 30) lastValidGlucose = 90f

                            for (c in candidates) {
                                if (c > 30 && c < 500) {
                                    val diff = abs(c - lastValidGlucose)
                                    if (diff < minDiff) {
                                        minDiff = diff
                                        bestArg = c
                                    }
                                }
                            }

                            // Heuristic Check
                            val isPlausible = (minDiff < 60) || (lastValidGlucose == 90f)

                            if (bestArg > 0 && isPlausible) {
                                lastValidGlucose = bestArg
                                val glucoseMmol = bestArg / 18.0182f
                                val timestamp = System.currentTimeMillis()

                                Log.e(TAG, String.format(">>> ADAPTIVE MATCH [Type %02X]: Selected %.1f mg/dL (Diff %.1f) -> %.1f mmol/L",
                                    pType, bestArg, minDiff, glucoseMmol))

                                // INJECTION
                                HistoryRepository.storeReadingAsync(
                                    timestamp,
                                    glucoseMmol,
                                    HistoryRepository.GLUCODATA_SOURCE_AIDEX
                                )
                            } else {
                                Log.w(TAG, String.format("No plausible glucose in [Type %02X] closest: %.1f (Diff %.1f)", pType, bestArg, minDiff))
                            }
                        }
                    } catch (e: Exception) {
                        Log.stack(TAG, "Decryption Loop Error", e)
                    }
                }
            }
        }
    }

    private fun aesDecryptCFB(key: ByteArray, iv: ByteArray, src: ByteArray): ByteArray? {
        return try {
            val skeySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CFB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec)
            cipher.doFinal(src)
        } catch (e: Exception) {
            Log.e(TAG, "AES CFB Decrypt error: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val success = gatt.writeDescriptor(descriptor)
            Log.i(TAG, "Enabling NOTIFY for ${characteristic.uuid}: $success")
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun enableIndication(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            val success = gatt.writeDescriptor(descriptor)
            Log.i(TAG, "Enabling INDICATE for ${characteristic.uuid}: $success")
        }
    }
}
