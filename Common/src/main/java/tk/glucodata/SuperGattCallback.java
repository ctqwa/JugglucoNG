/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Copyright (C) 2021 Jaap Korthals Altes <jaapkorthalsaltes@gmail.com>         */
/*                                                                                   */
/*      Juggluco is free software: you can redistribute it and/or modify             */
/*      it under the terms of the GNU General Public License as published            */
/*      by the Free Software Foundation, either version 3 of the License, or         */
/*      (at your option) any later version.                                          */
/*                                                                                   */
/*      Juggluco is distributed in the hope that it will be useful, but              */
/*      WITHOUT ANY WARRANTY; without even the implied warranty of                   */
/*      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         */
/*      See the GNU General Public License for more details.                         */
/*                                                                                   */
/*      You should have received a copy of the GNU General Public License            */
/*      along with Juggluco. If not, see <https://www.gnu.org/licenses/>.            */
/*                                                                                   */
/*      Fri Jan 27 15:31:05 CET 2023                                                 */

package tk.glucodata;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Build;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH;
import static android.bluetooth.BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
import static java.util.Objects.isNull;
import static tk.glucodata.Applic.DontTalk;
import static tk.glucodata.Applic.app;
import static tk.glucodata.Applic.isWearable;
import static tk.glucodata.Applic.mgdLmult;
import static tk.glucodata.Log.doLog;
import static tk.glucodata.Log.showbytes;
import static tk.glucodata.Natives.thresholdchange;
import static tk.glucodata.SensorBluetooth.blueone;
import tk.glucodata.alerts.AlertStateTracker;
import tk.glucodata.alerts.AlertType;

public abstract class SuperGattCallback extends BluetoothGattCallback {
    volatile protected boolean stop = false;
    public static boolean doWearInt = false;
    public static boolean doGadgetbridge = false;
    private static final String LOG_ID = "SuperGattCallback";
    static final private int use_priority = CONNECTION_PRIORITY_HIGH;
    static boolean autoconnect = false;
    String mDeviceName = null;

    protected static final UUID mCharacteristicConfigDescriptor = UUID
            .fromString("00002902-0000-1000-8000-00805f9b34fb");

    protected static final UUID mCharacteristicUUID_BLELogin = UUID.fromString("0000f001-0000-1000-8000-00805f9b34fb");
    protected static final UUID mCharacteristicUUID_CompositeRawData = UUID
            .fromString("0000f002-0000-1000-8000-00805f9b34fb");
    public static final UUID LIBRE3_DATA_SERVICE = UUID.fromString("089810cc-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID SIG_SERVICE_DEVICE_INFO = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID LIBRE3_SECURITY_SERVICE = UUID.fromString("0898203a-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_DEBUG_SERVICE = UUID.fromString("08982400-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_CHAR_BLE_LOGIN = UUID.fromString("0000f001-0000-1000-8000-00805f9b34fb");
    public static final UUID LIBRE3_CHAR_PATCH_CONTROL = UUID.fromString("08981338-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_CHAR_PATCH_STATUS = UUID.fromString("08981482-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_CHAR_EVENT_LOG = UUID.fromString("08981bee-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_CHAR_GLUCOSE_DATA = UUID.fromString("0898177a-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_CHAR_HISTORIC_DATA = UUID.fromString("0898195a-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_CHAR_CLINICAL_DATA = UUID.fromString("08981ab8-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_CHAR_FACTORY_DATA = UUID.fromString("08981d24-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_SEC_CHAR_COMMAND_RESPONSE = UUID.fromString("08982198-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_SEC_CHAR_CHALLENGE_DATA = UUID.fromString("089822ce-ef89-11e9-81b4-2a2ae2dbcce4");
    public static final UUID LIBRE3_SEC_CHAR_CERT_DATA = UUID.fromString("089823fa-ef89-11e9-81b4-2a2ae2dbcce4");
    // private final UUID mCharacteristicUUID_ManufacturerNameString =
    // UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
    // private final UUID mCharacteristicUUID_SerialNumberString =
    // UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");
    // private final UUID mSIGDeviceInfoServiceUUID =
    // UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public final long starttime = System.currentTimeMillis();
    public long connectTime = 0L;
    public String SerialNumber;
    public String mActiveDeviceAddress;
    public long dataptr = 0L;
    public BluetoothDevice mActiveBluetoothDevice;
    long foundtime = 0L;
    protected BluetoothGatt mBluetoothGatt;
    boolean superseded = false;
    public final int sensorgen;
    public int readrssi = 9999;
    protected long sensorstartmsec;

    protected SuperGattCallback(String SerialNumber, long dataptr, int gen) {
        this.SerialNumber = SerialNumber;
        this.dataptr = dataptr;
        mActiveDeviceAddress = Natives.getDeviceAddress(dataptr, true);
        sensorstartmsec = Natives.getSensorStartmsec(dataptr);
        sensorgen = gen;
        {
            if (doLog) {
                Log.i(LOG_ID, "new SuperGattCallback " + SerialNumber + " "
                        + ((mActiveDeviceAddress != null) ? mActiveDeviceAddress : "null"));
            }
            ;
        }
        ;
    }

    public void disconnect() {
        final var thegatt = mBluetoothGatt;
        if (thegatt != null) {
            {
                if (doLog) {
                    Log.i(LOG_ID, "Disconnect");
                }
                ;
            }
            ;
            thegatt.disconnect();
        } else {
            {
                if (doLog) {
                    Log.i(LOG_ID, "Disconnect mBluetoothGatt==null");
                }
                ;
            }
            ;
        }
    }

    public void setPause(boolean pause) {
        this.stop = pause;
        if (doLog)
            Log.i(LOG_ID, "setPause " + pause);
    }

    public boolean reconnect(long now) {
        final var old = now - showtime + 20;
        if (charcha[1] < old && connectTime < (now - 60 * 1000)) {
            try {
                if (doLog) {
                    Log.i(LOG_ID, "reconnect " + SerialNumber);
                }
                ;
                constatstatusstr = "Loss of signal";
                constatchange[1] = now;
                final var thegatt = mBluetoothGatt;
                if (thegatt != null) {
                    thegatt.disconnect();
                }
            } catch (Throwable th) {
                Log.stack(LOG_ID, "reconnect", th);
            } finally {
                return connectDevice(0);
            }
        }
        return true;
    }

    void setConStatus(int status) {
        constatstatusstr = "Status=" + status;
    }

    void shouldreconnect(long now) {
        final var old = now - showtime + 20;
        if (starttime < old && charcha[0] < old && connectTime < (now - 60 * 1000))
            reconnect(old);
    }

    long[] constatchange = { 0L, 0L };
    public String constatstatusstr = "";
    public String handshake = "";
    long[] wrotepass = { 0L, 0L };
    long[] charcha = { 0L, 0L };

    static final long thefuture = 0x7FFFFFFFFFFFFFFFL;
    // static long oldtime = thefuture;

    long showtime = Notify.glucosetimeout;
    static long lastfoundL = 0L;

    static long lastfound() {
        return lastfoundL;
    }

    private static long[] nextalarm = new long[10];

    static public void writealarmsuspension(int kind, short wa) {
        short prevsus = Natives.readalarmsuspension(kind);
        if (prevsus != wa) {
            Natives.writealarmsuspension(kind, wa);
            int versch = wa - prevsus;
            nextalarm[kind] += versch * 60;
        }
    }

    // New methods to handle scan data
    public void onScanResult(android.bluetooth.le.ScanResult result) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.bluetooth.le.ScanRecord record = result.getScanRecord();
            if (record != null) {
                byte[] bytes = record.getBytes();
                if (bytes != null)
                    onScanRecord(bytes);
            }
        }
    }

    public void onScanRecord(byte[] scanRecord) {
        // Default empty implementation
    }

    // Optional pre-replay sync hook for drivers that can actively request
    // latest pending sensor data (Sibionics FF30 path).
    public boolean requestLatestDataForReplay() {
        return false;
    }

    static final int mininterval = 55;
    static long nexttime = 0L; // secs
    public static tk.glucodata.GlucoseAlarms glucosealarms = null;
    public static notGlucose previousglucose = null;
    static float previousglucosevalue = 0.0f;
    public static String previousglucosesensorid = null;

    static public void initAlarmTalk() {
        if (glucosealarms == null)
            glucosealarms = new tk.glucodata.GlucoseAlarms(Applic.app);
        if (!DontTalk) {
            Talker.getvalues();
            if (Talker.shouldtalk())
                newtalker(null);
        }
    }

    static Talker talker;
    static boolean dotalk = false;

    static void newtalker(Context context) {
        if (!DontTalk) {
            if (talker != null)
                talker.destruct();
            talker = new Talker(context);
        }
    }

    static void endtalk() {
        if (!DontTalk) {
            dotalk = false;
            if (talker != null) {
                talker.destruct();
                talker = null;
            }
        }
    }

    private static void resetAlertState(AlertType type) {
        AlertStateTracker.INSTANCE.resetState(type);
        Notify.cancelRetrySession(type.getId(), "supergatt-reset");
    }

    private static void resetLowFamilyAlertState() {
        resetAlertState(AlertType.PRE_LOW);
        resetAlertState(AlertType.LOW);
        resetAlertState(AlertType.VERY_LOW);
    }

    private static void resetHighFamilyAlertState() {
        resetAlertState(AlertType.PRE_HIGH);
        resetAlertState(AlertType.HIGH);
        resetAlertState(AlertType.VERY_HIGH);
    }

    // Standard glucose alerts now use AlertStateTracker for cadence. The old
    // native alarmsuspension gate remains for loss-of-signal only.
    private static void syncStandardAlertState(int activeKind) {
        switch (activeKind) {
            case 0:
            case 5:
            case 7:
                resetHighFamilyAlertState();
                break;
            case 1:
            case 6:
            case 8:
                resetLowFamilyAlertState();
                break;
            default:
                resetLowFamilyAlertState();
                resetHighFamilyAlertState();
                break;
        }
    }

    private static int markAlertTriggered(int alarm, boolean triggered, boolean[] alarmspeak) {
        if (triggered) {
            alarm |= 8;
            if (!DontTalk) {
                if ((alarmspeak[0] = Natives.speakalarms()))
                    Talker.nexttime = 0L;
            }
        }
        return alarm;
    }

    static private int veryhigh(notGlucose sglucose, float gl, float rate, int alarm, boolean[] alarmspeak) {
        syncStandardAlertState(6);
        return markAlertTriggered(alarm, Notify.onenot.veryhighglucose(sglucose, gl, rate, true), alarmspeak);
    }

    static private int high(notGlucose sglucose, float gl, float rate, int alarm, boolean[] alarmspeak) {
        syncStandardAlertState(1);
        return markAlertTriggered(alarm, Notify.onenot.highglucose(sglucose, gl, rate, true), alarmspeak);
    }

    static private int verylow(notGlucose sglucose, float gl, float rate, int alarm, boolean[] alarmspeak) {
        syncStandardAlertState(5);
        return markAlertTriggered(alarm, Notify.onenot.verylowglucose(sglucose, gl, rate, true), alarmspeak);
    }

    static private int low(notGlucose sglucose, float gl, float rate, int alarm, boolean[] alarmspeak) {
        syncStandardAlertState(0);
        return markAlertTriggered(alarm, Notify.onenot.lowglucose(sglucose, gl, rate, true), alarmspeak);
    }

    private static boolean isStandardGlucoseAlertCode(int alarm) {
        return alarm == 4 || alarm == 5 || alarm == 6 || alarm == 7 || alarm == 16 || alarm == 17 || alarm == 18
                || alarm == 19;
    }

    private static int resolveCalibratedAlertCode(float glucoseValue, float rate) {
        if (!Float.isFinite(glucoseValue) || glucoseValue <= 0f) {
            return 0;
        }

        if (Natives.hasalarmveryhigh() && glucoseValue > Natives.alarmveryhigh()) {
            return 16;
        }
        if (Natives.hasalarmhigh() && glucoseValue > Natives.alarmhigh()) {
            return 6;
        }
        if (Natives.hasalarmverylow() && glucoseValue < Natives.alarmverylow()) {
            return 17;
        }
        if (Natives.hasalarmlow() && glucoseValue < Natives.alarmlow()) {
            return 7;
        }

        final float projected = glucoseValue + rate * 0.4f * 180.0f;
        if (Natives.hasalarmprelow() && projected < Natives.alarmprelow()) {
            return 19;
        }
        if (Natives.hasalarmprehigh() && projected > Natives.alarmprehigh()) {
            return 18;
        }
        return 0;
    }

    private static int reconcileAlertCodeWithCalibratedValue(int existingAlarm, float glucoseValue, float rate) {
        final int calibratedAlarm = resolveCalibratedAlertCode(glucoseValue, rate);
        if (calibratedAlarm != 0) {
            return calibratedAlarm;
        }
        if (isStandardGlucoseAlertCode(existingAlarm)) {
            return 0;
        }
        return existingAlarm;
    }

    private static long[] loadRecentSensorHistory(String sensorSerial, long startTimeSec) {
        long[] history = null;
        if (sensorSerial != null && !sensorSerial.isEmpty()) {
            try {
                history = Natives.getGlucoseHistoryForSensor(sensorSerial, startTimeSec);
            } catch (Throwable ignored) {
                history = null;
            }
            if ((history == null || history.length == 0) && sensorSerial.startsWith("X-") && sensorSerial.length() > 2) {
                try {
                    history = Natives.getGlucoseHistoryForSensor(sensorSerial.substring(2), startTimeSec);
                } catch (Throwable ignored) {
                    history = null;
                }
            }
        }
        if (history == null || history.length == 0) {
            try {
                history = Natives.getGlucoseHistory(startTimeSec);
            } catch (Throwable ignored) {
                history = null;
            }
        }
        return history;
    }

    private static final class SensorHistoryMatch {
        final long timestampMs;
        final float rawMgdl;

        SensorHistoryMatch(long timestampMs, float rawMgdl) {
            this.timestampMs = timestampMs;
            this.rawMgdl = rawMgdl;
        }
    }

    private static SensorHistoryMatch findSensorHistoryMatchNear(String sensorSerial, long timeSec) {
        long[] history = loadRecentSensorHistory(sensorSerial, timeSec - 30L);
        if (history == null) {
            return null;
        }
        for (int i = 0; i < history.length; i += 3) {
            if (i + 2 >= history.length)
                break;
            long hTime = history[i];
            if (Math.abs(hTime - timeSec) < 5L) {
                long rawVal = history[i + 2];
                float rawMgdl = rawVal > 0 ? (float) rawVal / 10.0f : Float.NaN;
                return new SensorHistoryMatch(hTime * 1000L, rawMgdl);
            }
        }
        return null;
    }

    private static float findRawMgdlNear(String sensorSerial, long timeSec) {
        final SensorHistoryMatch match = findSensorHistoryMatchNear(sensorSerial, timeSec);
        return match != null ? match.rawMgdl : Float.NaN;
    }

    private static void storeLiveReadingInRoom(String sensorSerial, long timmsec, float autoMgdl, float rawMgdl, float rate) {
        if (sensorSerial == null || sensorSerial.isEmpty() || timmsec <= 0L) {
            return;
        }
        if ((!Float.isFinite(autoMgdl) || autoMgdl <= 0f) && (!Float.isFinite(rawMgdl) || rawMgdl <= 0f)) {
            return;
        }
        final SensorHistoryMatch match = findSensorHistoryMatchNear(sensorSerial, timmsec / 1000L);
        final long storedTimestamp = match != null && match.timestampMs > 0L
                ? match.timestampMs
                : (timmsec / 1000L) * 1000L;
        final float storedAuto = (Float.isFinite(autoMgdl) && autoMgdl > 0f) ? autoMgdl : 0f;
        final float matchedRaw = match != null ? match.rawMgdl : Float.NaN;
        final float storedRaw = (Float.isFinite(rawMgdl) && rawMgdl > 0f)
                ? rawMgdl
                : ((Float.isFinite(matchedRaw) && matchedRaw > 0f) ? matchedRaw : storedAuto);
        HistorySyncAccess.storeCurrentReadingAsync(storedTimestamp, storedAuto, storedRaw, rate, sensorSerial);
    }

    static void dowithglucose(String SerialNumber, int mgdl, float gl, float rate, int alarm, long timmsec,
            long sensorstartmsec, long showtime, int sensorgen) {

        if (gl == 0.0)
            return;
        if (glucosealarms == null) {
            Log.e(LOG_ID, "glucosealarms==null");
            return;
        }

        // Multi-sensor fix: Check if this sensor is the user-selected main sensor.
        // Non-main sensors still store data (already done before this call), but should
        // NOT trigger notifications, alarms, broadcasts, or exchange data — those should
        // only reflect the main sensor's values to avoid confusing switching behavior.
        boolean isMainSensor = true;
        try {
            String mainName = Natives.lastsensorname();
            if (mainName != null && !mainName.isEmpty() && SerialNumber != null) {
                // Match by equality or by checking if one contains the other
                // (AiDex uses "X-..." prefix, Sibionics/Libre have different formats)
                isMainSensor = mainName.equals(SerialNumber) || SerialNumber.contains(mainName)
                        || mainName.contains(SerialNumber);
            }
        } catch (Throwable t) {
            // If we can't determine main sensor, default to allowing (safety)
            isMainSensor = true;
        }
        final boolean isAiDexSerial = SerialNumber != null && SerialNumber.startsWith("X-");

        if (!isMainSensor) {
            if (doLog) {
                Log.i(LOG_ID, "Multi-sensor: Skipping notifications/broadcasts for non-main sensor "
                        + SerialNumber + " (main=" + Natives.lastsensorname() + ")");
            }
            // Still update the screen so charts/history reflect all sensors
            Applic.updatescreen();
            if (!isAiDexSerial) {
                if (SerialNumber != null && !SerialNumber.isEmpty()) {
                    HistorySyncAccess.syncSensorFromNative(SerialNumber);
                }
                UiRefreshBus.requestDataRefresh();
            }
            return;
        }

        glucosealarms.setagealarm(timmsec, showtime);
        tk.glucodata.alerts.AlertRuntimeManager.INSTANCE.onNewReading(gl, rate, timmsec);
        final long tim = timmsec / 1000L;
        boolean waiting = false;
        var sglucose = new notGlucose(timmsec, String.format(Applic.usedlocale, Notify.pureglucoseformat, gl), rate,
                sensorgen);
        previousglucose = sglucose;
        previousglucosevalue = gl;
        previousglucosesensorid = SerialNumber;
        final var fview = Floating.floatview;
        // MainActivity.showmessage=null;
        boolean[] alarmspeak = { false };
        if (fview != null)
            fview.postInvalidate();

        try {

            switch (alarm) {
                case 4: {
                    if (Natives.hasalarmveryhigh())
                        alarm = veryhigh(sglucose, gl, rate, alarm, alarmspeak);
                    else {
                        if (Natives.hasalarmhigh())
                            alarm = high(sglucose, gl, rate, alarm, alarmspeak);
                        else {
                            syncStandardAlertState(-1);
                            Notify.onenot.normalglucose(sglucose, gl, rate, false);
                        }
                    }
                    break;
                }
                case 18: {
                    syncStandardAlertState(8);
                    alarm = markAlertTriggered(alarm, Notify.onenot.prehighglucose(sglucose, gl, rate, true),
                            alarmspeak);
                    break;
                }
                case 16:
                    alarm = veryhigh(sglucose, gl, rate, alarm, alarmspeak);
                    break;
                case 6:
                    alarm = high(sglucose, gl, rate, alarm, alarmspeak);
                    break;
                case 5: {
                    if (Natives.hasalarmverylow()) {
                        alarm = verylow(sglucose, gl, rate, alarm, alarmspeak);
                    } else {
                        if (Natives.hasalarmlow()) {
                            alarm = low(sglucose, gl, rate, alarm, alarmspeak);
                        } else {
                            syncStandardAlertState(-1);
                            Notify.onenot.normalglucose(sglucose, gl, rate, false);
                        }
                    }
                    break;
                }
                case 17:
                    alarm = verylow(sglucose, gl, rate, alarm, alarmspeak);
                    break;
                case 7:
                    alarm = low(sglucose, gl, rate, alarm, alarmspeak);
                    break;
                case 19: {
                    syncStandardAlertState(7);
                    alarm = markAlertTriggered(alarm, Notify.onenot.prelowglucose(sglucose, gl, rate, true),
                            alarmspeak);
                    break;
                }
                case 3:
                    waiting = true;
                default:
                    syncStandardAlertState(-1);
                    Notify.onenot.normalglucose(sglucose, gl, rate, waiting);
            }
            ;
        } catch (Throwable e) {
            Log.stack(LOG_ID, SerialNumber, e);
        }
        {
            if (doLog) {
                Log.v(LOG_ID, SerialNumber + " " + tim + " glucose=" + gl + " " + rate);
            }
            ;
        }
        ;

        Applic.updatescreen();
        if (!isAiDexSerial) {
            if (SerialNumber != null && !SerialNumber.isEmpty()) {
                HistorySyncAccess.syncSensorFromNative(SerialNumber);
            }
            UiRefreshBus.requestDataRefresh();
        }

        if (!DontTalk) {
            if (dotalk && !alarmspeak[0]) {
                talker.selspeak(sglucose.value);
            }
        }
        if (isWearable) {
            tk.glucodata.glucosecomplication.GlucoseValue.updateall();
        }

        if (Natives.getJugglucobroadcast())
            JugglucoSend.broadcastglucose(SerialNumber, mgdl, gl, rate, alarm, timmsec);
        if (!isWearable) {
            app.numdata.sendglucose(SerialNumber, tim, gl, thresholdchange(rate), alarm | 0x10);
            GlucoseWidget.update();
        }
        if (tim > nexttime) {
            nexttime = tim + mininterval;
            if (!isWearable) {
                if (Natives.getlibrelinkused())
                    XInfuus.sendGlucoseBroadcast(SerialNumber, mgdl, rate, timmsec);
                if (Natives.geteverSensebroadcast())
                    EverSense.broadcastglucose(mgdl, rate, timmsec);
                // SendNSClient.broadcastglucose(mgdl, rate, timmsec);
            }
            if (Natives.getxbroadcast())
                SendLikexDrip.broadcastglucose(mgdl, rate, timmsec, sensorstartmsec, sensorgen);
            if (!isWearable) {
                if (doWearInt)
                    tk.glucodata.WearInt.sendglucose(mgdl, rate, alarm, timmsec);

                if (doGadgetbridge)
                    Gadgetbridge.sendglucose(sglucose.value, mgdl, gl, rate, timmsec);
            }
        }

    }

    boolean stopHealth = false;

    private boolean dohealth(SuperGattCallback one) {
        if (!isWearable) {
            var blue = blueone;
            if (blue == null)
                return true; // false?
            final var gatts = blue.gattcallbacks;
            boolean other = gatts.size() > 1;
            if (!other) {
                return true; // TODO stopHealth=false
            }
            if (stopHealth)
                return false;
            for (var el : gatts) {
                if (el != one)
                    el.stopHealth = true;
            }
            return true;
        } else {
            return false;
        }
    }

    protected void handleGlucoseResult(long res, long timmsec) {
        handleGlucoseResultInternal(res, timmsec, 0);
    }

    private void handleGlucoseResultInternal(long res, long timmsec, int retryCount) {
        // int glumgdl = (int) (res & 0xFFFFFFFFL);
        int glumgL = (int) (res & 0xFFFFFFFFL);
        int alarm = (int) ((res >> 48) & 0xFFL);
        short ratein = (short) ((res >> 32) & 0xFFFFL);
        float rate = ratein / 1000.0f;

        // Check viewMode early - RAW modes may have data even when calibrated glucose is 0
        int viewMode = Natives.getViewMode(dataptr);
        boolean isRawMode = (viewMode == 1 || viewMode == 3);

        // In RAW mode with zero calibrated glucose, try to get raw from history
        // This handles warmup period where algorithm returns 0 but raw data exists
        if (glumgL == 0 && isRawMode) {
            long timeSec = timmsec / 1000L;
            final float rawMgdl = findRawMgdlNear(SerialNumber, timeSec);
            if (Float.isFinite(rawMgdl) && rawMgdl > 0f) {
                // Found raw value - use it even though calibrated is 0
                int mgdlToUse = (int) Math.round(rawMgdl);
                float glucoseToUse = rawMgdl;
                if (Applic.unit == 1) {
                    glucoseToUse = glucoseToUse / (float) mgdLmult;
                }
                // Keep Room current from the real live packet; calibration overwrite is applied in the repository.
                storeLiveReadingInRoom(SerialNumber, timmsec, 0f, rawMgdl, rate);
                // Apply calibration
                glucoseToUse = CalibrationAccess.getCalibratedValue(glucoseToUse, timmsec, true);
                mgdlToUse = (int) Math.round(glucoseToUse * (Applic.unit == 1 ? mgdLmult : 1.0f));

                if (doLog) {
                    Log.i(LOG_ID, "RAW mode during warmup: using raw=" + glucoseToUse + " mgdl=" + mgdlToUse);
                }

                dowithglucose(SerialNumber, mgdlToUse, glucoseToUse, rate, alarm, timmsec, sensorstartmsec, showtime, sensorgen);
                CustomAlertAccess.checkAndTrigger(tk.glucodata.Applic.app, glucoseToUse, rate, timmsec);
                charcha[0] = timmsec;

                if (!isWearable && Natives.gethealthConnect() && Build.VERSION.SDK_INT >= 28) {
                    if (dohealth(this)) {
                        final long sensorptr = Natives.getsensorptr(dataptr);
                        HealthConnection.Companion.writeAll(sensorptr, SerialNumber);
                    }
                }
                SensorBluetooth.othersworking(this, timmsec);
                return;
            }
            // No raw found yet - retry if possible
            if (retryCount < 3) {
                if (doLog) {
                    Log.i(LOG_ID, "RAW mode: no raw value found, retrying (" + (retryCount + 1) + "/3)...");
                }
                Applic.scheduler.schedule(() -> handleGlucoseResultInternal(res, timmsec, retryCount + 1), 200, TimeUnit.MILLISECONDS);
                return;
            }
        }

        if (glumgL != 0) {
            if (doLog) {
                Log.i(LOG_ID, SerialNumber + " alarm=" + alarm);
            }
            ;

            final float gl = Applic.unit == 1 ? glumgL / (mgdLmult * 10.0f) : glumgL / 10.0f;

            // LOGIC TO USE RAW VALUE IF VIEWMODE SPECIES SO
            float glucoseToUse = gl;
            int mgdlToUse = (int) Math.round(glumgL / 10.0f);
            final float autoMgdl = glumgL / 10.0f;
            float rawMgdl = Float.NaN;

            if (isRawMode) {
                long timeSec = timmsec / 1000L;
                rawMgdl = findRawMgdlNear(SerialNumber, timeSec);
                boolean found = Float.isFinite(rawMgdl) && rawMgdl > 0f;
                if (found) {
                    mgdlToUse = (int) Math.round(rawMgdl);
                    float glVal = rawMgdl;
                    if (Applic.unit == 1) {
                        glVal = glVal / (float) mgdLmult;
                    }
                    glucoseToUse = glVal;
                    if (doLog) {
                        Log.i(LOG_ID, "Using RAW value: " + glucoseToUse + " (mgdl: " + mgdlToUse + ")");
                    }
                }
                if (!found && retryCount < 3) {
                    if (doLog) {
                        Log.i(LOG_ID, "History lookup failed, retrying (" + (retryCount + 1) + "/3)...");
                    }
                    Applic.scheduler.schedule(() -> handleGlucoseResultInternal(res, timmsec, retryCount + 1), 200,
                            TimeUnit.MILLISECONDS);
                    return;
                }
            }

            if (!Float.isFinite(rawMgdl) || rawMgdl <= 0f) {
                rawMgdl = autoMgdl;
            }
            storeLiveReadingInRoom(SerialNumber, timmsec, autoMgdl, rawMgdl, rate);

            // Apply Kotlin calibration if enabled
            glucoseToUse = CalibrationAccess.getCalibratedValue(glucoseToUse, timmsec, isRawMode);
            mgdlToUse = (int) Math.round(glucoseToUse * (Applic.unit == 1 ? mgdLmult : 1.0f));
            alarm = reconcileAlertCodeWithCalibratedValue(alarm, glucoseToUse, rate);

            dowithglucose(SerialNumber, mgdlToUse, glucoseToUse, rate, alarm, timmsec, sensorstartmsec, showtime,
                    sensorgen);
            // Hook for Custom Alerts
            CustomAlertAccess.checkAndTrigger(tk.glucodata.Applic.app, glucoseToUse, rate, timmsec);

            charcha[0] = timmsec;

            if (!isWearable) {
                if (Natives.gethealthConnect()) {
                    if (Build.VERSION.SDK_INT >= 28) {
                        if (dohealth(this)) {
                            final long sensorptr = Natives.getsensorptr(dataptr);// TODO: set sensorptr in
                                                                                 // SuperGattCallback?
                            HealthConnection.Companion.writeAll(sensorptr, SerialNumber);
                        }
                    }
                }
            }
            SensorBluetooth.othersworking(this, timmsec);
        } else {
            {
                if (doLog) {
                    Log.i(LOG_ID, SerialNumber + " onCharacteristicChanged: Glucose failed");
                }
                ;
            }
            ;
            charcha[1] = timmsec;
        }
    }

    public void searchforDeviceAddress() {
        {
            if (doLog) {
                Log.i(LOG_ID, SerialNumber + " searchforDeviceAddress()");
            }
            ;
        }
        ;
        // setDeviceAddress(null);
        foundtime = 0L;
        mActiveDeviceAddress = null;
    }

    String getinfo() {
        if (dataptr != 0L)
            return Natives.getsensortext(dataptr);
        return "";
    }

    public long resetdataptr() {
        if (constatchange[1] < constatchange[0]) {
            constatchange[1] = System.currentTimeMillis();
            constatstatusstr = "resetdataptr";
        }
        Natives.freedataptr(dataptr);
        close();
        dataptr = Natives.getdataptr(SerialNumber);
        mActiveDeviceAddress = Natives.getDeviceAddress(dataptr, true);
        return dataptr;
    }

    public void setDevice(BluetoothDevice device) {

        mActiveBluetoothDevice = device;
        if (device != null) {
            String address = device.getAddress();
            {
                if (doLog) {
                    Log.i(LOG_ID, SerialNumber + " setDevice(" + address + ")");
                }
                ;
            }
            ;
            setDeviceAddress(address);
        } else {
            {
                if (doLog) {
                    Log.i(LOG_ID, SerialNumber + " setDevice(null)");
                }
                ;
            }
            ;
            setDeviceAddress(null);
        }
    }

    public void setDeviceAddress(String address) {
        {
            if (doLog) {
                Log.i(LOG_ID, SerialNumber + " " + "setDeviceAddress(" + address + ")");
            }
            ;
        }
        ;
        mActiveDeviceAddress = address;
        Natives.setDeviceAddress(dataptr, address);
    }

    void free() {
        stop = true;
        {
            if (doLog) {
                Log.i(LOG_ID, "free " + SerialNumber);
            }
            ;
        }
        ;
        close();
        Natives.freedataptr(dataptr);
        dataptr = 0L;
        // sensorbluetooth=null;
    }

    public boolean streamingEnabled() {// TODO: libre3?
        return Natives.askstreamingEnabled(dataptr);
    }

    public void finishSensor() {
        Natives.finishSensor(dataptr);
    }

    public void close() {
        {
            if (doLog) {
                Log.i(LOG_ID, "close " + SerialNumber);
            }
            ;
        }
        ;
        var tmpgatt = mBluetoothGatt;
        if (tmpgatt != null) {
            try {
                tmpgatt.disconnect();
                tmpgatt.close();
            } catch (Throwable se) {
                var mess = se.getMessage();
                mess = mess == null ? "" : mess;
                String uit = ((Build.VERSION.SDK_INT > 30)
                        ? Applic.getContext().getString(R.string.turn_on_nearby_devices_permission)
                        : mess);
                Applic.Toaster(uit);
                Log.stack(LOG_ID, SerialNumber + " " + "BluetoothGatt.close()", se);
            } finally {
                mBluetoothGatt = null;
            }
        } else {
            {
                if (doLog) {
                    Log.i(LOG_ID, "close mBluetoothGatt==null");
                }
                ;
            }
            ;
        }

    }

    private Runnable getConnectDevice() {
        var cb = this;
        if (cb.mBluetoothGatt != null) {
            // FIX: mBluetoothGatt != null does NOT mean "connected" - it means
            // "we have a GATT object reference". After disconnect(), mBluetoothGatt
            // stays non-null even though the connection is dead. Force close any
            // stale reference to allow reconnection (fixes Sibionics 1 CN reconnect bug).
            if (doLog)
                Log.d(LOG_ID, SerialNumber + " getConnectDevice: clearing stale mBluetoothGatt");
            close();
        }
        if (cb.mActiveDeviceAddress == null || cb.mActiveBluetoothDevice == null) {
            {
                if (doLog) {
                    Log.i(LOG_ID, SerialNumber + " " + "cb.mActiveBluetoothDevice == null");
                }
            }
            if (blueone != null) {
                blueone.scanStarter(0);
            }
            foundtime = 0L;
            return null;
        }
        return () -> {
            {
                if (doLog) {
                    Log.i(LOG_ID, "getConnectDevice Runnable " + SerialNumber);
                }
                ;
            }
            ;
            var device = cb.mActiveBluetoothDevice;
            var sensorbluetooth = blueone;
            if (sensorbluetooth == null) {
                Log.e(LOG_ID, SerialNumber + " " + "sensorbluetooth==null");
                return;
            }
            if (!sensorbluetooth.bluetoothIsEnabled()) {
                Log.e(LOG_ID, SerialNumber + " " + "!sensorbluetooth.bluetoothIsEnabled()");
                return;
            }
            if (device == null) {
                Log.e(LOG_ID, SerialNumber + " " + "device==null");
                return;
            }

            if (cb.mBluetoothGatt != null) {
                {
                    if (doLog) {
                        Log.d(LOG_ID, SerialNumber + " cb.mBluetoothGatt!=null");
                    }
                    ;
                }
                ;
                return;
            }
            var devname = device.getName();
            if (devname != null)
                mDeviceName = devname;
            if (doLog) {
                {
                    if (doLog) {
                        Log.d(LOG_ID, SerialNumber + " Try connection to " + device.getAddress() + " " + devname
                                + " autoconnect=" + autoconnect);
                    }
                    ;
                }
                ;
            }
            try {
                if (isWearable) {
                    cb.mBluetoothGatt = device.connectGatt(Applic.app, autoconnect, cb, BluetoothDevice.TRANSPORT_LE);
                    cb.setGattOptions(cb.mBluetoothGatt);
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cb.mBluetoothGatt = device.connectGatt(Applic.app, autoconnect, cb,
                                BluetoothDevice.TRANSPORT_LE);
                    } else {
                        cb.mBluetoothGatt = device.connectGatt(Applic.app, autoconnect, cb);
                    }
                }

                setpriority(cb.mBluetoothGatt);
                {
                    if (doLog) {
                        Log.i(LOG_ID, SerialNumber + " after connectGatt =" + cb.mBluetoothGatt);
                    }
                    ;
                }
                ;
                // cb.mBluetoothGatt.connect();
                connectTime = System.currentTimeMillis();
            } catch (SecurityException se) {
                var mess = se.getMessage();
                mess = mess == null ? "" : mess;
                String uit = ((Build.VERSION.SDK_INT > 30)
                        ? Applic.getContext().getString(R.string.turn_on_nearby_devices_permission)
                        : mess);
                Applic.Toaster(uit);

                Log.stack(LOG_ID, SerialNumber + " " + "connectGatt", se);
            } catch (Throwable e) {
                Log.stack(LOG_ID, SerialNumber + " " + "connectGatt", e);

            }
        };
    }

    public boolean connectDevice(long delayMillis) {
        if (doLog) {
            Log.i(LOG_ID, "connectDevice(" + delayMillis + ") " + SerialNumber);
        }
        ;
        Runnable connect = getConnectDevice();
        if (connect == null)
            return false;
        Applic.scheduler.schedule(connect, delayMillis, TimeUnit.MILLISECONDS);
        /*
         * if(delayMillis>0)
         * Applic.app.getHandler().postDelayed(connect, delayMillis);
         * else
         * Applic.app.getHandler().post(connect);
         */
        return true;
    }

    private boolean used_priority = false;

    @SuppressLint("MissingPermission")
    void setpriority(BluetoothGatt bluegatt) {
        if (bluegatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (Natives.getpriority()) {
                    bluegatt.requestConnectionPriority(use_priority);
                    {
                        if (doLog) {
                            Log.i(LOG_ID, "requestConnectionPriority HIGH");
                        }
                        ;
                    }
                    ;
                    used_priority = true;
                } else {
                    if (used_priority) {
                        bluegatt.requestConnectionPriority(CONNECTION_PRIORITY_BALANCED);
                        {
                            if (doLog) {
                                Log.i(LOG_ID, "requestConnectionPriority LOW");
                            }
                            ;
                        }
                        ;
                        used_priority = false;
                    }
                }
            }
        } else {
            Log.e(LOG_ID, SerialNumber + " " + "setpriority BluetoothGatt==null");
        }
    }

    boolean disableNoCheck(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        gatt.setCharacteristicNotification(ch, false);
        BluetoothGattDescriptor descriptor = ch.getDescriptor(mCharacteristicConfigDescriptor);
        if (!descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
            Log.e(LOG_ID, SerialNumber + " " + "descriptor.setValue())  failed");
            return false;
        }
        return gatt.writeDescriptor(descriptor);
    }

    boolean disablenotification(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        if (isNull(gatt)) {
            return false;
        }
        if (isNull(ch))
            return false;
        try {
            return disableNoCheck(gatt, ch);
        } catch (Throwable th) {
            Log.stack(LOG_ID, "disablenotification", th);
            return false;
        }
    }

    protected final boolean enableNotification(BluetoothGatt bluetoothGatt1,
            BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return enableNotificationnote(SerialNumber, bluetoothGatt1, bluetoothGattCharacteristic);
    }

    protected final boolean enableIndication(BluetoothGatt bluetoothGatt1,
            BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return enableIndicationnote(SerialNumber, bluetoothGatt1, bluetoothGattCharacteristic);
    }

    static boolean enableNotificationnote(String note, BluetoothGatt bluetoothGatt1,
            BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return enableGattDescriptornote(note, bluetoothGatt1, bluetoothGattCharacteristic, ENABLE_NOTIFICATION_VALUE);
    }

    static boolean enableIndicationnote(String note, BluetoothGatt bluetoothGatt1,
            BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return enableGattDescriptornote(note, bluetoothGatt1, bluetoothGattCharacteristic, ENABLE_INDICATION_VALUE);
    }

    protected boolean enableGattDescriptor(BluetoothGatt bluetoothGatt1,
            BluetoothGattCharacteristic bluetoothGattCharacteristic, byte[] type) {
        return enableGattDescriptornote(SerialNumber, bluetoothGatt1, bluetoothGattCharacteristic, type);
    }

    @SuppressLint("MissingPermission")
    static boolean enableGattDescriptornote(String note, BluetoothGatt bluetoothGatt1,
            BluetoothGattCharacteristic bluetoothGattCharacteristic, byte[] type) {
        try {
            BluetoothGattDescriptor descriptor = bluetoothGattCharacteristic
                    .getDescriptor(mCharacteristicConfigDescriptor);
            if (!descriptor.setValue(type)) {
                Log.e(LOG_ID, note + " " + "descriptor.setValue())  failed");
                return false;
            }
            final int originalWriteType = bluetoothGattCharacteristic.getWriteType();
            bluetoothGattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            var success = bluetoothGatt1.writeDescriptor(descriptor);
            bluetoothGattCharacteristic.setWriteType(originalWriteType);
            if (!success) {
                Log.e(LOG_ID, note + " " + "bluetoothGatt1.writeDescriptor(descriptor))  failed");
                return success;
            }
            if (doLog) {
                showbytes(LOG_ID + " " + note + " " + "enableNotification ", type);
            }
            ;
            if (!bluetoothGatt1.setCharacteristicNotification(bluetoothGattCharacteristic, type[0] != 0)) {
                Log.e(LOG_ID, note + " " + "setCharacteristicNotification("
                        + bluetoothGattCharacteristic.getUuid().toString() + ",true) failed");
                return false;
            }
            return success;
        } catch (Throwable th) {
            Log.stack(LOG_ID, "enableGattDescriptor", th);
            return false;
        }
    }

    protected final boolean asknotification(BluetoothGattCharacteristic charac) {
        return enableNotification(mBluetoothGatt, charac);
    }

    public boolean matchDeviceName(String deviceName, String address) {
        return false;
    }

    public UUID getService() {
        return null;
    }

    public void bonded() {
    }

    public String mygetDeviceName() {
        if (mDeviceName != null)
            return mDeviceName;
        final var device = mActiveBluetoothDevice;
        if (device != null) {
            var name = device.getName();
            if (name != null)
                return name;
        }
        if (mActiveDeviceAddress != null)
            return mActiveDeviceAddress;
        return "?";
    }

    public void setGattOptions(BluetoothGatt gatt) {
        {
            if (doLog) {
                Log.i(LOG_ID, "setGattOptions(BluetoothGatt gatt) empty");
            }
            ;
        }
        ;
    }

    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        {
            if (doLog) {
                Log.i(LOG_ID, "onPhyUpdate txPhy=" + txPhy + " rxPhy=" + rxPhy + " status=" + status);
            }
            ;
        }
        ;
    }

    static public String bondString(int bonded) {
        return switch (bonded) {
            case BOND_NONE -> "BOND_NONE";
            case BOND_BONDING -> "BOND_BONDING";
            case BOND_BONDED -> "BOND_BONDED";
            case BluetoothDevice.ERROR -> "BOND ERROR";
            default -> "BOND Unknown";
        };
    }
}
