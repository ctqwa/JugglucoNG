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
import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import android.os.Handler
import android.os.Looper
import tk.glucodata.Applic
import tk.glucodata.HistorySyncAccess
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.R
import java.util.Queue
import java.util.LinkedList
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
    private var isContinuousMode = false  // For background continuous scanning
    private val handler = Handler(Looper.getMainLooper())
    private var lastSeed: ByteArray? = null
    
    // Known AiDEX sensor MAC address (set when first discovered)
    private var knownAiDexMac: String? = null
    
    // Last broadcast timestamp to avoid duplicate readings
    private var lastBroadcastTime: Long = 0L

    private val MIN_READING_INTERVAL_MS = 30_000L  // Min 30 seconds between readings
    
    // History Sync control
    private var lastHistoryRequestTime: Long = 0L
    private val HISTORY_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes for more frequent testing
    
    // State for Heuristic Parsing
    private var handshakeStartTime: Long = 0L
    private var handshakeStep = 0
    private var historyTimeCursor: Long = 0L
    private var historyTotalCount: Int = 0 
    
    // Dynamic IV captured from Handshake Step 10/11
    private var sessionIv: ByteArray = ByteArray(16)
    private var lastValidGlucose: Float = 0f 
    private var calibrationFactor: Float = 1.0f // Default to 1.0 until we calculate it
    private var isWritingDescriptor = false
    private val descriptorQueue: Queue<BluetoothGattDescriptor> = LinkedList()
    private val handshakeQueue = LinkedList<ByteArray>()
    private var isHandshaking = false


    companion object {
        private const val TAG = "AIDEX_RAW"
        private const val SCAN_PERIOD: Long = 10000

        /**
         * Check if an AiDex sensor is the current main sensor.
         * Uses Natives.lastsensorname() which reads infoblockptr()->current.
         * AiDex sensor serials are normalized by AiDexSetupWizard.normalizeAiDexSerial()
         * to the format "X-ABCD1234" (always starts with "X-").
         * If AiDex is not main, we must NOT insert readings into Room
         * to avoid cross-sensor chart contamination.
         */
        private fun isAiDexMainSensor(): Boolean {
            return try {
                val mainName = Natives.lastsensorname()
                // AiDex serials are normalized to "X-..." by normalizeAiDexSerial().
                // That's what addAiDexSensor() passes to setcurrentsensor().
                // Also check raw BLE name patterns as a fallback.
                mainName != null && (mainName.startsWith("X-")
                    || mainName.contains("AiDEX", ignoreCase = true)
                    || mainName.contains("Linx", ignoreCase = true))
            } catch (e: Exception) {
                false
            }
        }
        private const val CONTINUOUS_SCAN_INTERVAL: Long = 60_000L  // Scan every 60 seconds
        
        // Persist history across class instances (e.g. Activity restarts)
        // Default to 0 to indicate "Unknown" (Neutral start)
        
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
            Applic.Toaster(Applic.app.getString(R.string.enable_bluetooth_first))
            return
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }

        if (!isScanning) {
            startContinuousMode()
        }
    }
    
    /**
     * Start continuous background scanning mode.
     * This will scan periodically and catch AiDEX broadcasts.
     */
    @SuppressLint("MissingPermission")
    fun startContinuousMode() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }
        
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }
        
        if (!isContinuousMode) {
            isContinuousMode = true
            Log.i(TAG, "Starting continuous scanning mode")
            startContinuousScan()
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopContinuousMode() {
        isContinuousMode = false
        handler.removeCallbacksAndMessages(null)  // Remove all pending scans
        if (isScanning) {
            isScanning = false
            bluetoothLeScanner?.stopScan(scanCallback)
        }
        Log.i(TAG, "Stopped continuous scanning mode")
    }
    
    @SuppressLint("MissingPermission")
    private fun startContinuousScan() {
        if (!isContinuousMode) return
        
        if (!isScanning) {
            isScanning = true
            // OPTIMIZATION: Use Low Power for background scanning to save battery
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()

            // OPTIMIZATION: Use Scan Filters to avoid waking up for every BLE device
            val filters = ArrayList<ScanFilter>()
            
            // Filter 1: Known MAC Address
            if (knownAiDexMac != null) {
                filters.add(ScanFilter.Builder().setDeviceAddress(knownAiDexMac).build())
            }

            // Filter 2: AiDex Manufacturer ID (89 / 0x59)
            // Passing null for data means "match any data from this manufacturer"
            filters.add(ScanFilter.Builder().setManufacturerData(89, null).build())

            // Filter 3: Standard CGM Service (181F)
            filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb"))).build())
            
            // Filter 4: Proprietary Service (F000) - Fallback
            filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("0000f000-0000-1000-8000-00805f9b34fb"))).build())

            bluetoothLeScanner?.startScan(filters, settings, scanCallback)
            Log.i(TAG, "Continuous scan started (Low Power, Filtered)...")
            
            // Validating filters logic:
            // If we filter by MAC, we barely wake up.
            // If we filter by MfgID, we only wake for AiDex (and maybe Bubble).
            
            // Stop scan after SCAN_PERIOD, then restart after interval
            // Standard duty cycle: 10s ON, 50s OFF is reasonable for LOW_POWER too.
            handler.postDelayed({
                if (isScanning) {
                    isScanning = false
                    bluetoothLeScanner?.stopScan(scanCallback)
                    Log.i(TAG, "Continuous scan paused")
                    
                    // Schedule next scan
                    if (isContinuousMode) {
                        handler.postDelayed({
                            startContinuousScan()
                        }, CONTINUOUS_SCAN_INTERVAL - SCAN_PERIOD)
                    }
                }
            }, SCAN_PERIOD)
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
    fun requestHistory() {
        val mac = knownAiDexMac
        if (mac == null) {
            Log.e(TAG, "Cannot request history: No known AiDex MAC address")
            return
        }
        
        Log.i(TAG, "Requesting History from $mac")
        lastHistoryRequestTime = System.currentTimeMillis()
        
        // Stop scanning if active
        if (isScanning) {
            isScanning = false
            bluetoothLeScanner?.stopScan(scanCallback)
        }
        
        val device = bluetoothAdapter?.getRemoteDevice(mac)
        if (device != null) {
            connectToDevice(device)
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
            Applic.Toaster(Applic.app.getString(R.string.aidex_probe_scanning))
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

            // Removed verbose logging to save battery/disk I/O
            // Log.d(TAG, "Found device: $deviceName [$address] RSSI: $rssi")

            // Try to parse glucose from BLE advertisement/manufacturer data
            // This is the "broadcast mode" that doesn't require connection/handshake
            result.scanRecord?.let { scanRecord ->
                val mfgData = scanRecord.manufacturerSpecificData
                if (mfgData != null && mfgData.size() > 0) {
                    for (i in 0 until mfgData.size()) {
                        val mfgId = mfgData.keyAt(i)
                        val data = mfgData.valueAt(i)
                        if (data != null && data.isNotEmpty()) {
                            Log.i(TAG, "MFG[$mfgId]: ${bytesToHex(data)}")
                            
                            // AiDEX uses Manufacturer ID 89 (0x59)
                            // Glucose is at index 5 of the manufacturer data
                            // Filter: AiDEX has data[1]=0x0A or 0x0B. Bubble Mini has 0x1A.
                            // We accept anything that isn't Bubble Mini (0x1A) for now to be safe.
                            if (mfgId == 89 && data.size >= 6 && (data[1].toInt() and 0xFF) != 0x1A) {
                                val glucoseMgDl = data[5].toInt() and 0xFF
                                val glucoseMmol = glucoseMgDl / 18.0182f
                                val now = System.currentTimeMillis()
                                
                                // Remember this sensor's MAC
                                val mac = device.address
                                if (knownAiDexMac == null) {
                                    knownAiDexMac = mac
                                    Log.i(TAG, "Saved AiDEX sensor MAC: $mac")
                                } else {
                                    Log.d(TAG, "Known AiDEX MAC already set: $knownAiDexMac")
                                }
                                
                                Log.e(TAG, ">>> AIDEX BROADCAST: $glucoseMgDl mg/dL -> ${"%.1f".format(glucoseMmol)} mmol/L")
                                
                                // Parse Sensor Age (Hypothesis: Byte 4 is Age in Hours)
                                val ageHours = data[4].toInt() and 0xFF
                                val daysLeft = 15.0 - (ageHours / 24.0)
                                Log.i(TAG, "Sensor Status: Age=${ageHours}h, DaysLeft=${"%.1f".format(daysLeft)}d (Raw Byte4=${String.format("%02X", ageHours)})")
                                
                                // Store if in valid range AND enough time has passed
                                if (glucoseMgDl > 30 && glucoseMgDl < 500) {
                                    if (now - lastBroadcastTime >= MIN_READING_INTERVAL_MS) {
                                        lastBroadcastTime = now
                                        lastValidGlucose = glucoseMmol
                                        // Only insert into Room if AiDex is the main sensor.
                                        // Otherwise the normal sync path (getGlucoseHistory)
                                        // will pick up the main sensor's data instead.
                                        if (isAiDexMainSensor()) {
                                            HistorySyncAccess.storeAidexReadingAsync(now, glucoseMmol)
                                        }
                                        Log.i(TAG, "Stored glucose: $glucoseMgDl mg/dL")
                                    } else {
                                        Log.d(TAG, "Skipping duplicate reading (too soon)")
                                    }
                                    // Don't need to connect - we got the reading from broadcast!
                                    // BUT, check if we need to fetch history (Once per session / interval)
                                    if (now - lastHistoryRequestTime > HISTORY_INTERVAL_MS) {
                                        Log.i(TAG, "Triggering History Request (Interval > 10m or first run)")
                                        requestHistory()
                                    } else {
                                        val minsLeft = (HISTORY_INTERVAL_MS - (now - lastHistoryRequestTime)) / 60000
                                        Log.d(TAG, "Skipping History Request: Last sync was too recent ($minsLeft mins until next)")
                                    }
                                    return
                                }
                            } else if (data.size >= 6) {
                                // Log other broadcasts for debugging ONLY if mfgId matches but logic failed
                                // val v4 = data[4].toInt() and 0xFF
                                // val v5 = if (data.size > 5) data[5].toInt() and 0xFF else 0
                                // Log.d(TAG, "BROADCAST candidate: v4=$v4 v5=$v5")
                            }
                        }
                    }
                }
                
                // Also check service data
                val serviceData = scanRecord.serviceData
                if (serviceData != null && serviceData.isNotEmpty()) {
                    for ((uuid, data) in serviceData) {
                        if (data != null && data.isNotEmpty()) {
                            // Valid service data usually means relevant device if filtered
                            Log.d(TAG, "SVC[$uuid]: ${bytesToHex(data)}")
                        }
                    }
                }
            }

            // Only connect if we didn't get data from broadcast
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

        Log.i(TAG, "Connecting to ${device.address} (Bond State: ${device.bondState})")
        
        // Attempt bonding if not bonded
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            Log.i(TAG, "Device not bonded. initiating bonding...")
            device.createBond()
            // We should wait for bonding to complete, but let's connect anyway 
            // as some devices bond during connection.
        }
        
        bluetoothGatt = device.connectGatt(Applic.app, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server. Bond State: ${gatt.device.bondState}")
                    
                    // If bonding is in progress, wait? 
                    // Usually we start service discovery. 
                    // If higher security is needed, the stack handle it or we re-discover after bond.
                    
                    @SuppressLint("MissingPermission")
                    val success = gatt.discoverServices()
                    Log.i(TAG, "Attempting to start service discovery: $success")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server.")
                    bluetoothGatt?.close() // Ensure we close the gatt
                    bluetoothGatt = null
                    
                    // Resume scanning if we were in continuous mode
                    if (isContinuousMode) {
                        Log.i(TAG, "Resuming continuous scan after disconnect...")
                        startContinuousScan()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered. Connecting to AiDex Hybrid (181F)...")
                val service = gatt.getService(UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb"))
                
                if (service != null) {
                    // SETUP: Enable Notifications on F003 (Data) and Indications on F002 (Control)
                    val cF003 = service.getCharacteristic(UUID.fromString("0000f003-0000-1000-8000-00805f9b34fb"))
                    if (cF003 != null) {
                        Log.i(TAG, "F003 Properties: ${cF003.properties} (Notify: ${cF003.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0})")
                        Log.i(TAG, "Enabling Notify on Data (F003)...")
                        queueEnableNotification(gatt, cF003)
                    }
                    
                    val cF002 = service.getCharacteristic(UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb"))
                    if (cF002 != null) {
                        Log.i(TAG, "F002 Properties: ${cF002.properties} (Notify: ${cF002.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0})")
                        Log.i(TAG, "Enabling Notifications on Control (F002)...")
                        // Sensor has Notify property (0x10), not Indicate.
                        queueEnableNotification(gatt, cF002) 
                    }
                    
                    // STANDARD CGM HISTORY Support (RACP 0x2A52)
                    val cRACP = service.getCharacteristic(UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb"))
                    if (cRACP != null) {
                        Log.i(TAG, "Found RACP (0x2A52). Enabling INDICATIONS...")
                        queueEnableIndication(gatt, cRACP)
                    } else {
                        Log.w(TAG, "RACP (0x2A52) NOT found in service.")
                    }

                    // Standard CGM Specific Ops (0x2AAC)
                    val c2AAC = service.getCharacteristic(UUID.fromString("00002aac-0000-1000-8000-00805f9b34fb"))
                    if (c2AAC != null) {
                        Log.i(TAG, "Found CGM Specific Ops (0x2AAC). Enabling INDICATIONS...")
                        queueEnableIndication(gatt, c2AAC)
                    } else {
                        Log.w(TAG, "CGM Specific Ops (0x2AAC) NOT found in service.")
                    }

                    // Standard CGM Measurement (0x2AA7) - CRITICAL for backfill stream
                    val c2AA7 = service.getCharacteristic(UUID.fromString("00002aa7-0000-1000-8000-00805f9b34fb"))
                    if (c2AA7 != null) {
                        Log.i(TAG, "Found CGM Measurement (0x2AA7). Enabling NOTIFICATIONS...")
                        queueEnableNotification(gatt, c2AA7)
                    } else {
                        Log.w(TAG, "CGM Measurement (0x2AA7) NOT found in service.")
                    }
                    
                    // Clear old queue
                    handshakeQueue.clear()
                    isHandshaking = false
                    
                } else {
                    Log.e(TAG, "Service 181F NOT found! Disconnecting.")
                    gatt.disconnect()
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                gatt.disconnect()
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.i(TAG, "onDescriptorWrite for ${descriptor.characteristic.uuid}: $status")
            isWritingDescriptor = false
            processDescriptorQueue(gatt)

            // When Descriptor Queue is empty, Start Official Handshake on F002
            if (descriptorQueue.isEmpty() && !isWritingDescriptor) {
                 if (handshakeStep == 0) {
                     Log.i(TAG, "Starting Official Handshake (Step 1)...")
                     handshakeStep = 1
                     performHandshakeStep(gatt)
                 }
            }
        }
        
        @SuppressLint("MissingPermission")
        private fun performHandshakeStep(gatt: BluetoothGatt) {
            val service = gatt.getService(UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb"))
            val cF002 = service?.getCharacteristic(UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb"))
            val cRACP = service?.getCharacteristic(UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb"))
            
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
                10 -> cmd = hexStringToByteArray("B4482C") // Proprietary "Get Index"
                
                11 -> {
                     Log.i(TAG, "Handshake Step 11: Sending B5 (Key/Param Exchange)")
                     cmd = hexStringToByteArray("B597303367")
                }
                
                12 -> {
                     Log.i(TAG, "Handshake Step 12: Sending B6 (Start Stream?)")
                     cmd = hexStringToByteArray("B60A0C")
                }
                
                13 -> {
                    // STOP: Do not proceed to Standard CGM (Steps 13-15).
                    // The official app stops at B6 and listens to F003.
                    Log.i(TAG, "Handshake Completed (Proprietary Only). Listening for F003...")
                    return
                }
            }
            
            if (cmd != null && cF002 != null) {
                cF002.value = cmd
                cF002.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                val success = gatt.writeCharacteristic(cF002)
                Log.i(TAG, "Handshake Step $handshakeStep: Writing ${bytesToHex(cmd)} to F002 (Success: $success)")
                
                // Chain next step
                if (handshakeStep < 10) {
                    handshakeStep++
                    handler.postDelayed({
                        performHandshakeStep(gatt)
                    }, 1200) // Increased delay to avoid status 3 congestion
                } else if (handshakeStep < 13) {
                    Log.i(TAG, "Handshake Step $handshakeStep sent. Waiting 1s then Reading F002...")
                    // Explicit POLL since Notification might fail
                    handler.postDelayed({
                        gatt.readCharacteristic(cF002)
                    }, 1000)
                    
                    // AUTO-ADVANCE Fallback: If poll/notify fails to trigger handleF002Response, 
                    // move forward anyway after 3 seconds.
                    handler.postDelayed({
                        if ((handshakeStep == 10 || handshakeStep == 11) && gatt.device.address == knownAiDexMac) {
                            Log.w(TAG, "Handshake Step $handshakeStep Timed Out! Auto-Advancing...")
                            if (handshakeStep == 10) handshakeStep = 11
                            else if (handshakeStep == 11) handshakeStep = 12
                            performHandshakeStep(gatt)
                        }
                    }, 3000)
                }
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.i(TAG, "WRITE ${characteristic.uuid} status: $status")
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val uuid = characteristic.uuid.toString()
            val data = characteristic.value
            Log.i(TAG, "READ $uuid status: $status Value: ${bytesToHex(data)}")
            
            if (status == BluetoothGatt.GATT_SUCCESS && uuid.contains("f002")) {
                handleF002Response(gatt, data)
            }
        }

        private fun handleF002Response(gatt: BluetoothGatt, data: ByteArray?) {
            if (data == null || data.isEmpty()) return
            val firstByte = data[0].toInt() and 0xFF
            
            // Response to B4 (Get Index/Challenge) - Relaxed to accept 0x8A or anything
            if (handshakeStep == 10) {
                Log.i(TAG, "Step 10 Response (F002): ${bytesToHex(data)}. Moving to Step 11 (B5)")
                
                // DATA MINING: Use this as IV?
                // data usually 17 bytes: Start Byte + 16 bytes?
                // Log: "2A FE DB C9 ..." (17 bytes)
                if (data.size >= 17) {
                    System.arraycopy(data, 1, sessionIv, 0, 16)
                    Log.i(TAG, "Captured SESSION IV: ${bytesToHex(sessionIv)}")
                } else if (data.size == 16) {
                    System.arraycopy(data, 0, sessionIv, 0, 16)
                    Log.i(TAG, "Captured SESSION IV: ${bytesToHex(sessionIv)}")
                }

                handshakeStep = 11
                performHandshakeStep(gatt)
            } 
            // Response to B5 (Key Exchange)
            else if (handshakeStep == 11) {
                Log.i(TAG, "Step 11 Response (F002): ${bytesToHex(data)}. Moving to Step 12 (B6)")
                handshakeStep = 12
                performHandshakeStep(gatt)
            }
            // Response to B6 (Start Stream)
            else if (firstByte == 0xB6 || (handshakeStep == 12 && data.size > 2)) {
                Log.i(TAG, "Received B6 Response! History Stream Should Start...")
                // Complete proprietary part.
                handshakeStartTime = System.currentTimeMillis()
                // Reset history cursor for a new backfill burst
                historyTimeCursor = System.currentTimeMillis() - 60_000L 
                
                // Final Step: Standard RACP
                // Final Step: DONE.
                // Do NOT trigger Step 13.
                handshakeStep = 13 
                Log.i(TAG, "Handshake Finalized. Waiting for F003 Stream...")
            }
        }

        private fun processHandshakeQueue(gatt: BluetoothGatt) {
             // Deprecated in favor of performHandshakeStep
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val uuid = characteristic.uuid.toString()
            val data = characteristic.value
            
            if (uuid.contains("2aa7")) {
                // Standard CGM Measurement
                if (data != null && data.isNotEmpty()) {
                    Log.i(TAG, "CGM MEASUREMENT (2AA7) Raw: ${bytesToHex(data)}")
                    parseCgmMeasurement(data, gatt)
                }
            } else if (uuid.contains("2a52")) {
                Log.i(TAG, "RACP RESPONSE (2A52) Indication: ${bytesToHex(data)}")
                if (data == null || data.isEmpty()) return
                val opcode = data[0].toInt() and 0xFF
                
                // Handle Number of Records Response (05 ...)
                if (opcode == 0x05) {
                    // Note: Previous code used data[2] LSB, data[3] MSB? My eyes saw data[1], data[2].
                    // Log: "05 00 02 0E" -> Opcode 05, Null 00, Count LSB 02, Count MSB 0E (3586).
                    // So Byte 2 (02) is LSB. Byte 3 (0E) is MSB.
                    // Previous code: `val count = (data[2].toInt() and 0xFF) + ((data[3].toInt() and 0xFF) shl 8)` -> Correct.
                    val count = (data[2].toInt() and 0xFF) + ((data[3].toInt() and 0xFF) shl 8)
                    Log.i(TAG, "RACP Count: $count records found. Moving to Step 15 (Report All)")
                    historyTotalCount = count
                    
                    // RACP Standard: Reports Oldest to Newest.
                    // Start cursor at Now - (Count * 1 min)
                    historyTimeCursor = System.currentTimeMillis() - (count * 60_000L)
                    
                    handshakeStep = 15
                    handler.postDelayed({ performHandshakeStep(gatt) }, 1000)
                }
                // Handle Response Code (06 ...)
                else if (opcode == 0x06) {
                    val reqOpcode = data[1].toInt() and 0xFF
                    val status = data[2].toInt() and 0xFF
                    Log.i(TAG, "RACP Response for req $reqOpcode: status $status")
                    if (reqOpcode == 0x01 && status == 0x01) {
                         Log.i(TAG, "Backfill request successful.")
                    }
                }
            } else if (uuid.contains("2aac")) {
                Log.i(TAG, "CGM OPS (2AAC) Indication: ${bytesToHex(data)}")
                // Successfully started session? Advance to Count check
                if (handshakeStep == 13) {
                    handshakeStep = 14
                    handler.postDelayed({ performHandshakeStep(gatt) }, 1000)
                }
            } else if (uuid.contains("f002")) {
                Log.i(TAG, "CTRL NOTIFY (F002): ${bytesToHex(data)}")
                handleF002Response(gatt, data)
                
            } else if (uuid.contains("f003")) {
                 // Proprietary Data Stream
                 if (data != null && data.isNotEmpty()) {
                     Log.i(TAG, "F003 DATA (Size ${data.size}): ${bytesToHex(data)}")
                     
                     // Check if it's a SEED (5 bytes)
                     /*
                     if (data.size == 5) {
                         Log.i(TAG, "CAPTURED SEED (F003): ${bytesToHex(data)}")
                         // Ack the seed? (Legacy logic did this)
                         val seedAck = ByteArray(8)
                         System.arraycopy(data, 0, seedAck, 0, 5)
                         seedAck[0] = (seedAck[0].toInt() xor 0x01).toByte()
                         Log.i(TAG, "Writing Seed Ack: ${bytesToHex(seedAck)}")
                         characteristic.value = seedAck
                         characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                         gatt.writeCharacteristic(characteristic)
                         return
                     }
                     */

                     // DECRYPTION
                     val masterKey = hexStringToByteArray("AC4C8ECDD8761B512EEB95D707942912")
                     val ivZero = ByteArray(16) // Zero IV
                     
                     // AES-CFB Decrypt
                     // Extract encrypted part (skip 1 byte if length >= 17? Logic from Java file)
                     val encrypted = ByteArray(16)
                     val skip = if (data.size >= 17) 1 else 0
                     if (data.size >= 16 + skip) {
                         System.arraycopy(data, skip, encrypted, 0, 16)
                         
                         try {
                              val skeySpec = SecretKeySpec(masterKey, "AES")
                              val ivSpec = IvParameterSpec(sessionIv)
                              val cipher = Cipher.getInstance("AES/CFB/NoPadding")
                              cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec)
                              val pt = cipher.doFinal(encrypted)
                              
                              val pType = pt[0].toInt() and 0xFF
                              val b3 = pt[3].toInt() and 0xFF
                              Log.i(TAG, "AIDEX-DEC [Type ${String.format("%02X", pType)}] Val3: $b3 | PT: ${bytesToHex(pt)}")
                              
                              // Candidates for Glucose
                              val candidates = floatArrayOf(
                                  (pt[3].toInt() and 0xFF).toFloat(),
                                  (pt[3].toInt() and 0xFF) / 2.0f,
                                  (pt[5].toInt() and 0xFF).toFloat(),
                                  (pt[5].toInt() and 0xFF) / 2.0f
                              )
                              
                              var bestVal = -1f
                              var minDiff = 1000f
                              
                              // Use last valid or default 90
                              val reference = if (lastValidGlucose > 0) lastValidGlucose * 18.0182f else 90f
                              
                              for (c in candidates) {
                                  if (c > 30 && c < 500) {
                                      val diff = abs(c - reference)
                                      if (diff < minDiff) {
                                          minDiff = diff
                                          bestVal = c
                                      }
                                  }
                              }
                                                            if (bestVal > 0 && minDiff < 60) {
                                   val glucoseMmol = bestVal / 18.0182f
                                   
                                   // Calculate timestamp (back from now)
                                   val now = System.currentTimeMillis()

                                   // DYNAMIC CALIBRATION:
                                   // Broadcast is "Golden" (Calibrated). F003 is "Raw".
                                   // Factor = Broadcast / Raw.
                                   var currentFactor = calibrationFactor
                                   val broadcastMgDl = lastValidGlucose * 18.0182f
                                   
                                   // If we have a fresh broadcast (< 5 min old) and the Raw value is reasonable
                                   if (lastValidGlucose > 0 && abs(now - lastBroadcastTime) < 300_000L && bestVal > 10) {
                                       val calculatedFactor = broadcastMgDl / bestVal
                                       
                                       // Sanity Check: Factor should be reasonably close to 1.0 (e.g. 0.5 to 1.5)
                                       // In logs we saw 0.82, 0.97. 
                                       if (calculatedFactor > 0.5f && calculatedFactor < 1.5f) {
                                           // Use a Weighted Moving Average to smooth it? 
                                           // Or just take it if it's the "latest truth"?
                                           // Let's trust the latest broadcast pair.
                                           currentFactor = calculatedFactor
                                           calibrationFactor = currentFactor
                                            Log.i(TAG, "UPDATED CALIBRATION: Broadcast=$broadcastMgDl / Raw=$bestVal = Factor $calibrationFactor")
                                       }
                                   }

                                   val correctedMgDl = bestVal * currentFactor
                                   val correctedMmol = correctedMgDl / 18.0182f
                                   
                                   Log.i(TAG, ">>> DECRYPTED F003: Raw=$bestVal * Factor $currentFactor = $correctedMgDl mg/dL ($correctedMmol mmol/L)")
                                   
                                   // Store the corrected value (only if AiDex is main sensor)
                                   if (isAiDexMainSensor()) {
                                       HistorySyncAccess.storeAidexReadingAsync(System.currentTimeMillis(), correctedMmol)
                                   }
                               }
                              
                         } catch (e: Exception) {
                             Log.e(TAG, "Decryption Error: ${e.message}")
                         }
                     }
                 }
            }

                     
            // Keep connection alive for ANY characteristic activity (2AA7, 2A52, F003, etc.)
            resetDisconnectTimer(gatt)
        }
    }

    private var disconnectRunnable: Runnable? = null

    private fun resetDisconnectTimer(gatt: BluetoothGatt) {
        disconnectRunnable?.let { handler.removeCallbacks(it) }
        disconnectRunnable = Runnable {
            Log.i(TAG, "Activity Idle. Disconnecting to restore Broadcasts...")
            gatt.disconnect()
            startContinuousMode()
        }
        
        val timeSinceConnect = if (handshakeStartTime > 0) (System.currentTimeMillis() - handshakeStartTime) else 0L
        var timeout = if (timeSinceConnect < 60_000L) 15_000L else 6000L
        
        // If we are in the RACP phases (13-15), give it 45s to avoid cutting off large data dump
        // If we are in the RACP phases (13-15), give it 120s to allow full history download
        // If we are in the RACP phases (13-15), give it 120s to allow full history download
        if (handshakeStep >= 13) {
            timeout = 120_000L
        }
        
        handler.postDelayed(disconnectRunnable!!, timeout) 
    }

    private fun parseCgmMeasurement(data: ByteArray, gatt: BluetoothGatt?) {
        try {
            var offset = 0
            while (offset < data.size) {
                // Ensure enough bytes for Flags + Glucose (3 bytes min)
                if (offset + 3 > data.size) break

                val initialOffset = offset
                val flags = data[offset].toInt() and 0xFF
                offset += 1
                
                // Glucose Concentration: SFLOAT (16-bit)
                val sfloat = ((data[offset + 1].toInt() and 0xFF) shl 8) + (data[offset].toInt() and 0xFF)
                val glucoseVal = sfloatToFloat(sfloat)
                offset += 2
                
                // Time Offset (UINT16) -- AIDEX USES BIG ENDIAN FOR TIME OFFSET
                // Log: 07 01 -> 07 02 -> Gap 256 if LE. Gap 1 if BE.
                val timeOffset = if (offset + 1 < data.size) 
                    ((data[offset].toInt() and 0xFF) shl 8) + (data[offset + 1].toInt() and 0xFF) 
                    else 0
                offset += 2
                
                // Force jump to next 10-byte record boundary (0A A0 ... pattern)
                offset = initialOffset + 10 

                Log.d(TAG, "Parser Candidate: Flag=$flags Time=$timeOffset")

                // Corrected Parser Logic for AiDex
                // 1. Mantissa is UNSIGNED 12-bit
                // 2. Apply Scale Factor 0.4
                var mantissa = sfloat and 0x0FFF
                var exponent = (sfloat shr 12) and 0x0F
                if ((exponent and 0x08) != 0) {
                     exponent = exponent or 0xFFFFFFF0.toInt()
                }
                
                val rawVal = (mantissa * Math.pow(10.0, exponent.toDouble())).toFloat()
                val correctedVal = rawVal * 0.4f
                
                // Timestamp Logic: Forward Iteration
                var ts = historyTimeCursor
                historyTimeCursor += 60_000L // Increment 1 minute
                
                Log.d(TAG, "Parser Candidate: Raw=$rawVal Corrected=$correctedVal TS=$ts")

                // FILTER 0xFFA0 (160.0). 
                // The graph oscillation between 4.2 and 9.0 proves these are interleaved garbage/placeholders.
                if (sfloat == 0xFFA0) {
                     Log.d(TAG, "Skipping Default/Invalid Value (0xFFA0) at TS=$ts")
                } else if (correctedVal > 10 && correctedVal < 600) { 
                    val glucoseMmol = correctedVal / 18.0182f
                    // Calculate progress
                    val currentCount = (historyTimeCursor - (System.currentTimeMillis() - historyTotalCount*60000L))/60000L
                    val progress = if (historyTotalCount > 0) "($currentCount/$historyTotalCount)" else ""
                    
                    Log.i(TAG, "Parsed History $progress: Glucose=$correctedVal TimeOff=$timeOffset TS=${ts}")
                    if (isAiDexMainSensor()) {
                        HistorySyncAccess.storeAidexReadingAsync(ts, glucoseMmol)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CGM packet: " + e.message)
        }
    }

    private fun sfloatToFloat(sfloat: Int): Float {
        // IEEE 11073 16-bit Float
        // Bits 12-15: Exponent (4-bit signed)
        // Bits 0-11: Mantissa (12-bit signed)
        
        var mantissa = sfloat and 0x0FFF
        if ((mantissa and 0x0800) != 0) {
            mantissa = mantissa or 0xFFFFF000.toInt() // Sign extend to 32-bit int
        }
        
        var exponent = (sfloat shr 12) and 0x0F
        if ((exponent and 0x08) != 0) {
            exponent = exponent or 0xFFFFFFF0.toInt() // Sign extend to 32-bit int
        }
        
        return (mantissa * Math.pow(10.0, exponent.toDouble())).toFloat()
    }

    @SuppressLint("MissingPermission")
    private fun queueEnableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            descriptorQueue.add(descriptor)
            processDescriptorQueue(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun queueEnableIndication(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            descriptorQueue.add(descriptor)
            processDescriptorQueue(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun processDescriptorQueue(gatt: BluetoothGatt) {
        if (isWritingDescriptor) return
        if (descriptorQueue.isEmpty()) return

        val descriptor = descriptorQueue.poll()
        if (descriptor != null) {
            isWritingDescriptor = true
            val success = gatt.writeDescriptor(descriptor)
            Log.i(TAG, "Queue: Writing Descriptor for ${descriptor.characteristic.uuid}: $success")
            if (!success) {
                isWritingDescriptor = false
            }
        }
    }
}
