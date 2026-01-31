package tk.glucodata.aidex;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import tk.glucodata.Applic;
import tk.glucodata.Log;

/**
 * A dedicated probe tool to reverse engineer the AiDex/Micro Tech Medical
 * sensor protocol.
 * This class scans for devices, connects to them, discovers services, and logs
 * all
 * communication to Logcat with the tag "AIDEX_RAW".
 */
public class AiDexProbe {
    private static final String TAG = "AIDEX_RAW";
    private static final long SCAN_PERIOD = 10000;
    private static AiDexProbe instance;

    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private byte[] sessionKey = null;
    private byte[] lastSeed = null;

    @SuppressWarnings("deprecation")
    private AiDexProbe() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static synchronized AiDexProbe getInstance() {
        if (instance == null) {
            instance = new AiDexProbe();
        }
        return instance;
    }

    @SuppressLint("MissingPermission")
    public void startProbe() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not enabled");
            Applic.Toaster("Enable Bluetooth first!");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            return;
        }

        if (!isScanning) {
            scanLeDevice();
        }
    }

    @SuppressLint("MissingPermission")
    public void stopProbe() {
        if (isScanning && bluetoothLeScanner != null) {
            isScanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            Log.i(TAG, "Scan stopped manually");
        }
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
            Log.i(TAG, "Gatt closed manually");
        }
    }

    @SuppressLint("MissingPermission")
    private void scanLeDevice() {
        if (!isScanning) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(() -> {
                if (isScanning) {
                    isScanning = false;
                    bluetoothLeScanner.stopScan(scanCallback);
                    Log.i(TAG, "Scan stopped after timeout");
                }
            }, SCAN_PERIOD);

            isScanning = true;
            bluetoothLeScanner.startScan(scanCallback);
            Log.i(TAG, "Scan started...");
            Applic.Toaster("AiDex Probe: Scanning...");
        } else {
            isScanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            Log.i(TAG, "Scan stopped");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            @SuppressLint("MissingPermission")
            String deviceName = device.getName();
            String address = device.getAddress();
            int rssi = result.getRssi();

            Log.d(TAG, "Found device: " + deviceName + " [" + address + "] RSSI: " + rssi);

            // Heuristic to find the sensor (Adjust as needed based on user feedback)
            // For now, let's look for "AiDex" or just log everything.
            // Since we want to be aggressive in Phase 1, we might connect to the first
            // strong signal
            // if name matches nothing, but let's try to match name first.
            if (deviceName != null
                    && (deviceName.toLowerCase().contains("aidex") || deviceName.toLowerCase().contains("meter"))) {
                connectToDevice(device);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Scan failed with error: " + errorCode);
        }
    };

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        if (isScanning) {
            bluetoothLeScanner.stopScan(scanCallback);
            isScanning = false;
        }

        Log.i(TAG, "Connecting to " + device.getAddress());
        bluetoothGatt = device.connectGatt(Applic.app, false, gattCallback);
    }

    @SuppressWarnings("deprecation")
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                @SuppressLint("MissingPermission")
                boolean success = gatt.discoverServices();
                Log.i(TAG, "Attempting to start service discovery: " + success);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered:");
                BluetoothGattService service = gatt.getService(UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb"));

                if (service != null) {
                    // 1. Enable Notifications/Indications FIRST
                    List<BluetoothGattCharacteristic> chars = service.getCharacteristics();
                    for (BluetoothGattCharacteristic c : chars) {
                        if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            enableNotification(gatt, c);
                        }
                        if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                            enableIndication(gatt, c);
                        }
                    }
                    
                    // 2. Start Handshake                // F001 (Auth) - Bypassing for now as it seems to hang and Official App logs don't show it immediately
                /*
                BluetoothGattCharacteristic authChar = service.getCharacteristic(UUID.fromString("0000f001-0000-1000-8000-00805f9b34fb"));
                if (authChar != null) {
                    Log.i(TAG, "Starting Handshake: Reading F001 Challenge...");
                    gatt.readCharacteristic(authChar);
                }
                */

                // DIRECT START: Official Handshake on F002
                BluetoothGattCharacteristic ctrlChar = service.getCharacteristic(UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb"));
                if (ctrlChar != null) {
                    final BluetoothGatt finalGatt = gatt;
                    final BluetoothGattCharacteristic finalChar = ctrlChar;
                    Log.i(TAG, "Auth Skipped. Scheduling Official Handshake (Step 1) on F002 in 1.5s...");
                    
                    handler.postDelayed(() -> {
                        handshakeStep = 1;
                        writeHandshakeStep(finalGatt, finalChar);
                    }, 1500);
                }
            }
        }
    } // Extra brace to close onServicesDiscovered or its enclosing block if needed

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // Unused if we skip F001 read
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String uuid = characteristic.getUuid().toString();
            Log.i(TAG, "WRITE " + uuid + " status: " + status);
            
            if (uuid.equals("0000f002-0000-1000-8000-00805f9b34fb")) {
                // Continue Handshake Chain
                if (handshakeStep > 0 && handshakeStep < 9) {
                    handshakeStep++;
                    BluetoothGattService service = gatt.getService(UUID.fromString("0000181f-0000-1000-8000-00805f9b34fb"));
                    if (service != null) {
                        BluetoothGattCharacteristic ctrlChar = service.getCharacteristic(UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb"));
                        writeHandshakeStep(gatt, ctrlChar);
                    }
                } else if (handshakeStep == 9) {
                    Log.i(TAG, "Handshake Sequence Complete! Waiting for Data...");
                    handshakeStep = 0; // Done
                }
            }
        }

        private int handshakeStep = 0;
        private void writeHandshakeStep(BluetoothGatt gatt, BluetoothGattCharacteristic ctrlChar) {
            byte[] cmd = null;
            switch (handshakeStep) {
                case 1: cmd = hexStringToByteArray("55FB0631"); break;
                case 2: cmd = hexStringToByteArray("54FB3702"); break;
                case 3: cmd = hexStringToByteArray("711AAB"); break;
                case 4: cmd = hexStringToByteArray("422AAD"); break;
                case 5: cmd = hexStringToByteArray("43BA4C847E"); break;
                case 6: cmd = hexStringToByteArray("44C14CB72F"); break;
                case 7: cmd = hexStringToByteArray("802454"); break;
                case 8: cmd = hexStringToByteArray("81FB486A48"); break;
                case 9: cmd = hexStringToByteArray("826674"); break;
            }
            if (cmd != null) {
                Log.i(TAG, String.format("Handshake Step %d: Writing %s", handshakeStep, bytesToHex(cmd)));
                ctrlChar.setValue(cmd);
                ctrlChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); 
                boolean success = gatt.writeCharacteristic(ctrlChar);
                Log.i(TAG, "Write Initiated: " + success);
                
                // Force chain next step after 200ms (Blind Chaining) because callback is unreliable
                if (success && handshakeStep < 9) {
                     final int nextStep = handshakeStep + 1;
                     // Capture needed variables for lambda
                     final BluetoothGatt finalGatt = gatt;
                     final BluetoothGattCharacteristic finalChar = ctrlChar;
                     
                     handler.postDelayed(() -> {
                         // Only proceed if we aren't reset
                         if (handshakeStep != 0) {
                             handshakeStep = nextStep;
                             writeHandshakeStep(finalGatt, finalChar);
                         }
                     }, 200);
                } else if (success && handshakeStep == 9) {
                    Log.i(TAG, "Handshake Sequence Complete (Blind)! Waiting for Data...");
                    handshakeStep = 0; 
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String uuid = characteristic.getUuid().toString();
            byte[] data = characteristic.getValue();
            String hexData = bytesToHex(data);
            Log.i(TAG, "NOTIFY/INDICATE " + uuid + " -> " + hexData);

            if (uuid.equals("0000f003-0000-1000-8000-00805f9b34fb")) {
                if (data.length == 5) {
                    lastSeed = data;
                    Log.i(TAG, "CAPTURED SEED (F003): " + hexData);
                    byte[] seedAck = new byte[8];
                    System.arraycopy(data, 0, seedAck, 0, 5);
                    seedAck[0] = (byte)(seedAck[0] ^ 0x01);
                    Log.i(TAG, "Writing Seed Ack: " + bytesToHex(seedAck));
                    characteristic.setValue(seedAck);
                    characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    gatt.writeCharacteristic(characteristic);
                } else if (data.length >= 16) {
                    try {
                        byte[] encrypted = new byte[16];
                        int skip = data.length >= 17 ? 1 : 0;
                        if (data.length < 16 + skip) skip = 0;
                        
                        System.arraycopy(data, skip, encrypted, 0, 16);
                        byte[] masterKey = hexStringToByteArray("AC4C8ECDD8761B512EEB95D707942912");
                        // Decrypt and check immediately
                        // Decrypt and check immediately (Legacy ECB - Renamed to avoid collision)
                        // Decrypt and check immediately (Legacy ECB - Renamed to avoid collision)
                        // Decrypt and check immediately (Legacy ECB - Renamed to avoid collision)
                        // Kept minimally for log continuity if needed, but primary logic is CFB below.
                        byte[] ptEcb = aesDecrypt(masterKey, encrypted);
                        int opEcb = (ptEcb != null) ? (ptEcb[0] & 0xFF) : 0;

                        // --- FINAL AIDEX DECRYPTION (Refined) ---
                        // Cipher: AES-128 CFB NoPadding
                        // Key: Master Key
                        // IV: Zero IV (16 bytes of 0x00)
                        // Header: 0x6F (Live Status)
                        // Glucose: Byte 3 (Index 3)

                        byte[] ivZero = new byte[16]; // All zeros
                        byte[] pt = aesDecryptCFB(masterKey, ivZero, encrypted);

                        if (pt != null) {
                            String hexPt = bytesToHex(pt);
                            int pType = pt[0] & 0xFF; // Expect 0x6F
                            int b3 = pt[3] & 0xFF;    // Glucose Byte

                            Log.e(TAG, String.format("AIDEX-DEC [Type %02X] Val3: %d | Bytes: %s", pType, b3, hexPt));

                            // ADAPTIVE DECRYPTION STRATEGY
                            // The sensor cycles through packet types (55, 72, C7, B9, E4, etc).
                            // Glucose is usually in Byte 3 or Byte 5, sometimes Scaled (x2).
                            // We test candidates and pick the one closest to the previous valid reading.
                            
                            float[] candidates = new float[] {
                                (float)(pt[3] & 0xFF),         // Index 3 Raw
                                (pt[3] & 0xFF) / 2.0f,         // Index 3 Scaled
                                (float)(pt[5] & 0xFF),         // Index 5 Raw
                                (pt[5] & 0xFF) / 2.0f          // Index 5 Scaled
                            };
                            
                            float bestArg = -1;
                            float minDiff = Float.MAX_VALUE;
                            
                            // Initialize lastValidGlucose if needed
                            if (lastValidGlucose < 30) lastValidGlucose = 90f; // Default ~5.0 mmol
                            
                            for (float c : candidates) {
                                if (c > 30 && c < 500) {
                                    float diff = Math.abs(c - lastValidGlucose);
                                    if (diff < minDiff) {
                                        minDiff = diff;
                                        bestArg = c;
                                    }
                                }
                            }
                            
                            // Heuristic: If jump is too large (> 60 mg/dL ~ 3.3 mmol), be skeptical unless it's the first reading
                            boolean isPlausible = (minDiff < 60) || (lastValidGlucose == 90f); 

                            if (bestArg > 0 && isPlausible) {
                                lastValidGlucose = bestArg; // Update tracking
                                float glucoseMmol = bestArg / 18.0182f;
                                long timestamp = System.currentTimeMillis();
                                
                                Log.e(TAG, String.format(">>> ADAPTIVE MATCH [Type %02X]: Selected %.1f mg/dL (Diff %.1f) -> %.1f mmol/L", 
                                    pType, bestArg, minDiff, glucoseMmol));
                                
                                // INJECTION
                                tk.glucodata.data.HistoryRepository.storeReadingAsync(
                                    timestamp, 
                                    glucoseMmol, 
                                    tk.glucodata.data.HistoryRepository.GLUCODATA_SOURCE_AIDEX
                                );
                            } else {
                                Log.w(TAG, String.format("No plausible glucose in [Type %02X] closest: %.1f (Diff %.1f)", pType, bestArg, minDiff));
                            }
                                Log.i(TAG, "History/Trend Packet ignored.");
                            } else {
                                Log.w(TAG, "Unknown Packet Type: " + String.format("%02X", pType));
                            }
                        }
                    } catch (Exception e) {
                        Log.stack(TAG, "Decryption Loop Error", e);
                    }
                }
            }
        }
    };

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private void enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null)
            return; // Added null check for characteristic
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic
                .getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean success = gatt.writeDescriptor(descriptor);
            Log.i(TAG, "Enabling NOTIFY for " + characteristic.getUuid() + ": " + success);
        }
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private void enableIndication(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic
                .getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            boolean success = gatt.writeDescriptor(descriptor);
            Log.i(TAG, "Enabling INDICATE for " + characteristic.getUuid() + ": " + success);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    private byte[] aesDecryptCFB(byte[] key, byte[] iv, byte[] src) {
        try {
            javax.crypto.spec.SecretKeySpec skeySpec = new javax.crypto.spec.SecretKeySpec(key, "AES");
            javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CFB/NoPadding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, skeySpec, ivSpec);
            return cipher.doFinal(src);
        } catch (Exception e) {
            Log.e(TAG, "AES CFB Decrypt error: " + e.getMessage());
            return null;
        }
    }

    private byte[] aesDecrypt(byte[] key, byte[] src) {
        try {
            javax.crypto.spec.SecretKeySpec skeySpec = new javax.crypto.spec.SecretKeySpec(key, "AES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, skeySpec);
            return cipher.doFinal(src);
        } catch (Exception e) {
            Log.e(TAG, "AES Decrypt error: " + e.getMessage());
            return null;
        }
    }

    private void logIVResult(String label, byte[] pt, int op) {
        if (pt == null) {
            Log.e(TAG, String.format("CFB-%-8s [Op %02X] Val8: null", label, op));
            return;
        }
        int b0 = pt[0] & 0xFF;
        int b1 = pt[1] & 0xFF;
        String hex = bytesToHex(pt);
        Log.e(TAG, String.format("CFB-%-8s [Op %02X] Val8: %3d | Val0: %3d (%s)", label, op, b1, b0, hex));
        
        // Highlight plausible values (e.g., 40-400 mg/dL)
        if (b1 > 40 && b1 < 400) {
            Log.e(TAG, ">>> " + label + " MATCH?: " + b1 + " mg/dL");
        }
    }

    private void testDecrypt(byte[] encrypted, byte[] key, byte rawByte0, String label) {
        byte[] dec = aesDecrypt(key, encrypted);
        if (dec != null) {
            int op = dec[0] & 0xFF;
            Log.i(TAG, String.format("ECB-%s [Op %02X]: %s", label, op, bytesToHex(dec)));
            
            // Check for sequence match (index 5)
            if ((rawByte0 & 0xFF) == (dec[5] & 0xFF)) {
                // Match Log only
                Log.e(TAG, ">>> VALID-8BIT [Op " + String.format("%02X", op) + "] " + (dec[1] & 0xFF) + " mg/dL (" + String.format("%.1f", (dec[1] & 0xFF) / 18.0182) + " mmol/L)");
            }

            // Check for plausible glucose in dec[1]
            int glucose = dec[1] & 0xFF;
            if (glucose >= 40 && glucose <= 450) {
                 if (op == 0x93 || op == 0xA1 || op == 0xA4 || op == 0xD7 || op == 0x06) {
                    Log.e(TAG, "!!! POTENTIAL GLUCOSE MATCH FOUND (Op " + String.format("%02X", op) + ", Val " + glucose + ") !!!");
                 }
            }
        }
    }

    private void testIv(javax.crypto.spec.SecretKeySpec keySpec, byte[] encrypted, byte[] seed, String label) {
        List<byte[]> ivs = new ArrayList<>();
        
        // IV S1: Seed at start
        byte[] ivs1 = new byte[16];
        System.arraycopy(seed, 0, ivs1, 0, seed.length);
        ivs.add(ivs1);
        
        // IV S2: Seed at end
        byte[] ivs2 = new byte[16];
        System.arraycopy(seed, 0, ivs2, 16 - seed.length, seed.length);
        ivs.add(ivs2);
        
        // IV S3: Common padded version (0x01)
        byte[] ivs3 = new byte[16];
        java.util.Arrays.fill(ivs3, (byte)0x01);
        System.arraycopy(seed, 0, ivs3, 0, seed.length);
        ivs.add(ivs3);

        for (int i=0; i<ivs.size(); i++) {
            try {
                javax.crypto.Cipher cfb = javax.crypto.Cipher.getInstance("AES/CFB/NoPadding");
                cfb.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, new javax.crypto.spec.IvParameterSpec(ivs.get(i)));
                byte[] pt = cfb.doFinal(encrypted);
                int op = pt[0] & 0xFF;
                Log.i(TAG, String.format("CFB-%s-%d [Op %02X]: %s", label, i, op, bytesToHex(pt)));
                
                if (op == 0x93 || op == 0xA1 || op == 0xA4 || op == 0xD7 || op == 0x06) {
                    Log.e(TAG, "!!! POTENTIAL DECRYPTION MATCH FOUND !!!");
                }
            } catch (Exception e) {}
        }
    }

    // Helper to fix missing symbol in generated code
    private static class BluetoothProfile {
        static final int STATE_CONNECTED = 2;
        static final int STATE_DISCONNECTED = 0;
    }
}
