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

import static android.app.Notification.FLAG_ONGOING_EVENT;
import static android.app.Notification.VISIBILITY_PUBLIC;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Context.VIBRATOR_SERVICE;
import android.media.MediaPlayer;
import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static java.lang.String.format;
import static tk.glucodata.Applic.DontTalk;
import static tk.glucodata.Applic.TargetSDK;
import static tk.glucodata.Applic.app;
import static tk.glucodata.Applic.isWearable;
import static tk.glucodata.Applic.usedlocale;
import static tk.glucodata.Log.doLog;
import static tk.glucodata.Natives.getUSEALARM;
import static tk.glucodata.Natives.getalarmdisturb;
import static tk.glucodata.Natives.getisalarm;
import static tk.glucodata.Natives.setisalarm;
import static tk.glucodata.R.id.arrowandvalue;
import static tk.glucodata.ScanNfcV.vibrates;
import static tk.glucodata.Talker.notifyfocus;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.view.View;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap; // Added Import
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

import androidx.annotation.ColorInt;

import java.text.DateFormat;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import tk.glucodata.alerts.AlertType;
import tk.glucodata.alerts.SnoozeManager;
import tk.glucodata.alerts.AlertConfig;
import tk.glucodata.alerts.AlertRepository;
import tk.glucodata.alerts.AlertStateTracker;
import java.util.Collections;
import java.util.List;

public class Notify {
    // ... class start ...
    static {
        makenotification_audio();
    };
    static public final int glucosetimeoutSEC = 30 * 11;
    static public final long glucosetimeout = 1000L * glucosetimeoutSEC;

    static final private String LOG_ID = "Notify";
    static Notify onenot = null;

    static void init(Context cont) {
        if (onenot == null) {
            onenot = new Notify(cont);

        }
    }

    public static String glucoseformat = null;
    public static String pureglucoseformat = null;
    static public String unitlabel = "mg/dL";

    // public static int unit=0;
    static void mkunitstr(Context cont, int unit) {
        Applic.unit = unit;
        pureglucoseformat = unit == 1 ? "%.1f" : "%.0f";
        if (isWearable) {
            glucoseformat = pureglucoseformat;
        } else {
            unitlabel = unit == 1 ? cont.getString(R.string.mmolL) : cont.getString(R.string.mgdL);
            glucoseformat = unit == 1 ? "%.1f " + unitlabel : "%.0f " + unitlabel;
        }

    }

    @SuppressLint("NewApi")
    Ringtone setring(String uristr, int res) {
        if (uristr == null || uristr.length() == 0) {
            uristr = "android.resource://" + Applic.app.getPackageName() + "/" + res;
        }
        Uri uri = Uri.parse(uristr);
        Ringtone ring = RingtoneManager.getRingtone(Applic.app, uri);
        if (ring == null) {
            {
                if (doLog) {
                    Log.i(LOG_ID, "ring==null default");
                }
                ;
            }
            ;
            uristr = "android.resource://" + Applic.app.getPackageName() + "/" + res;
            uri = Uri.parse(uristr);
            ring = RingtoneManager.getRingtone(Applic.app, uri);
        }
        // NOTE: Do NOT set looping here - the scheduled stop via runstopalarm handles
        // duration.
        // setLooping(true) causes the ringtone to loop forever and prevents proper
        // stopping.
        return ring;
    }

    static public String alarmtext(int kind) {
        return Applic.getContext().getString(switch (kind) {
            case 0 -> R.string.lowglucoseshort;
            case 1 -> R.string.highglucoseshort;
            case 5 -> R.string.verylowglucose;
            case 6 -> R.string.veryhighglucose;
            case 7 -> R.string.prelowglucose;
            case 8 -> R.string.prehighglucose;
            default -> R.string.nothing;
        });
    }

    // 0 1 2 3 4 5 6 7 8
    // low high avail amount loss very low very high pre low pre high
    static final private int[] defaults = { R.raw.siren, R.raw.classic, R.raw.ghost, R.raw.nudge, R.raw.elves,
            R.raw.verylow, R.raw.veryhigh, R.raw.lowsoon, R.raw.highsoon, R.raw.classic, R.raw.classic,
            R.raw.classic };

    // static AudioAttributes notification_audio=(android.os.Build.VERSION.SDK_INT
    // >= 21)?new
    // AudioAttributes.Builder().setUsage(isWearable?USAGE_UNKNOWN:USAGE_NOTIFICATION)
    // .build():null;
    // static AudioAttributes notification_audio=(android.os.Build.VERSION.SDK_INT
    // >= 21)?new AudioAttributes.Builder().setUsage( USAGE_ASSISTANCE_SONIFICATION)
    // .build():null;
    // static AudioAttributes notification_audio=(android.os.Build.VERSION.SDK_INT
    // >= 21)?new AudioAttributes.Builder().setUsage(USAGE_NOTIFICATION)
    // .build():null;

    static AudioAttributes notification_audio;
    // static AudioAttributes notification_audio=(android.os.Build.VERSION.SDK_INT
    // >= 21)?new AudioAttributes.Builder().setUsage(isWearable?
    // USAGE_ASSISTANCE_SONIFICATION: AudioAttributes.USAGE_NOTIFICATION)
    // .build():null;
    static AudioFocusRequest audiofocusrequest;

    static public void makenotification_audio() {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            int type = Natives.getSoundType();
            Log.i(LOG_ID, "getSoundType()=" + type);
            if (type == 0) {
                type = isWearable ? USAGE_ASSISTANCE_SONIFICATION : AudioAttributes.USAGE_NOTIFICATION;
            }
            notification_audio = new AudioAttributes.Builder().setUsage(type).build();
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                audiofocusrequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(notification_audio).build();
                Log.i(LOG_ID, "audiofocusrequest  has value");
            } else {
                audiofocusrequest = null;
                Log.i(LOG_ID, "audiofocusrequest=null");
            }
        } else {
            notification_audio = null;
        }
    }

    static private AudioManager audioManager = (android.os.Build.VERSION.SDK_INT < 26) ? null
            : (AudioManager) Applic.getContext().getSystemService(Context.AUDIO_SERVICE);
    static private boolean turnfocusoff = false;

    static void doTurnFocusoff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final var wasturnoff = turnfocusoff;
            turnfocusoff = false;
            if (wasturnoff) {
                audioManager.abandonAudioFocusRequest(audiofocusrequest);
            }
        }
    }

    static void doTurnFocuson() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            if (!turnfocusoff) {
                switch (audioManager.requestAudioFocus(audiofocusrequest)) {
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        Log.i(LOG_ID, "REQUEST_FAILED");
                        break;

                    case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                        turnfocusoff = true;
                        Log.i(LOG_ID, "REQUEST_GRANTED");
                        break;
                    case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                        Log.i(LOG_ID, "REQUEST_DELAYED");
                        break;
                }
                ;
            }
        }
    }

    public static Ringtone getring(int kind) {
        return mkrings(Natives.readring(kind), kind);
    }

    Ringtone mkring(String uristr, int kind) {
        // For global alerts, use getalarmdisturb to determine audio stream
        return mkring(uristr, kind, getalarmdisturb(kind));
    }

    Ringtone mkring(String uristr, int kind, boolean disturb) {
        {
            if (doLog) {
                Log.i(LOG_ID, "ringtone " + kind + " " + uristr + " disturb=" + disturb);
            }
            ;
        }
        ;
        var ring = setring(uristr, defaults[kind]);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try {
                // Use ALARM stream only if disturb=true AND USEALARM is enabled
                boolean useAlarmStream = (kind != 2 && getUSEALARM() && disturb);
                ring.setAudioAttributes(useAlarmStream ? ScanNfcV.audioattributes : notification_audio);
            } catch (Throwable e) {
                Log.stack(LOG_ID, "mkring", e);
            }

        }
        return ring;
    }

    static public Ringtone mkrings(String uristr, int kind) {
        if (onenot != null)
            return onenot.mkring(uristr, kind);
        return null;
    }

    final static boolean whiteonblack = false;
    @ColorInt
    public static int foregroundcolor = BLACK;
    static public float glucosesize;
    static RemoteGlucose arrowNotify;

    static void mkpaint() {
        if (!isWearable) {
            DisplayMetrics metrics = Applic.app.getResources().getDisplayMetrics();
            {
                if (doLog) {
                    Log.i(LOG_ID, "metrics.density=" + metrics.density + " width=" + metrics.widthPixels + " height="
                            + metrics.heightPixels);
                }
                ;
            }
            ;
            var notwidth = Math.min(metrics.widthPixels, metrics.heightPixels);
            arrowNotify = new RemoteGlucose(glucosesize, notwidth, 0.12f, whiteonblack ? 1 : 0, false);
        }
    }

    Notify(Context cont) {
        showalways = Natives.getshowalways();
        {
            if (doLog) {
                Log.i(LOG_ID, "showalways=" + showalways);
            }
            ;
        }
        ;
        alertseparate = true; // Natives.getSeparate(); // Force true for modern notification channels
        mkunitstr(cont, Natives.getunit());
        notificationManager = (NotificationManager) Applic.app.getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel(Applic.app);
        mkpaint();
    }

    private static final String NUMALARM = "MedicationReminder";
    private static final String GLUCOSEALARM = "glucoseAlarm";
    public static final String CHANNEL_LOW = "LOW";
    public static final String CHANNEL_HIGH = "HIGH";
    public static final String CHANNEL_LOSS = "LOSS";
    // private static final String LOSSALARM = "LossofSensorAlarm";
    private static String GLUCOSENOTIFICATION = "glucoseNotification";

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Determine Channel ID (Standard)
            String targetChannelId = "glucoseNotification";
            GLUCOSENOTIFICATION = targetChannelId;

            try {
                // Cleanup experiment channels
                String[] obsolete = { "glucoseNotification_nodot", "glucoseNotification_nobadge" };
                for (String s : obsolete) {
                    if (notificationManager.getNotificationChannel(s) != null) {
                        notificationManager.deleteNotificationChannel(s);
                    }
                }
            } catch (Exception e) {
            }

            String description = context.getString(R.string.numalarm_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(NUMALARM, NUMALARM, importance);
            channel.setSound(null, null);
            channel.setDescription(description);
            channel.setShowBadge(true); // Default behavior
            notificationManager.createNotificationChannel(channel);

            description = context.getString(R.string.alarm_description);
            importance = NotificationManager.IMPORTANCE_HIGH;
            channel = new NotificationChannel(GLUCOSEALARM, GLUCOSEALARM, importance);
            channel.setSound(null, null);
            channel.setDescription(description);
            channel.setShowBadge(true);
            notificationManager.createNotificationChannel(channel);

            description = context.getString(R.string.notification_description);
            importance = NotificationManager.IMPORTANCE_HIGH; // Default High

            // Standard Channel
            channel = new NotificationChannel(GLUCOSENOTIFICATION, GLUCOSENOTIFICATION, importance);
            channel.setSound(null, null);
            channel.setDescription(description);
            channel.setShowBadge(true);

            notificationManager.createNotificationChannel(channel);

            // === NEW CHANNELS (Phase 4) ===
            NotificationChannel channelLow = new NotificationChannel(CHANNEL_LOW, "Low Glucose",
                    NotificationManager.IMPORTANCE_HIGH);
            channelLow.setDescription("Alerts when glucose is below target");
            channelLow.setSound(null, null); // App plays sound manually
            channelLow.setShowBadge(false);
            notificationManager.createNotificationChannel(channelLow);

            NotificationChannel channelHigh = new NotificationChannel(CHANNEL_HIGH, "High Glucose",
                    NotificationManager.IMPORTANCE_HIGH);
            channelHigh.setDescription("Alerts when glucose is above target");
            channelHigh.setSound(null, null);
            channelHigh.setShowBadge(false);
            notificationManager.createNotificationChannel(channelHigh);

            NotificationChannel channelLoss = new NotificationChannel(CHANNEL_LOSS, "Signal Loss",
                    NotificationManager.IMPORTANCE_HIGH);
            channelLoss.setDescription("Alerts when sensor signal is lost");
            channelLoss.setSound(null, null);
            channelLoss.setShowBadge(false);
            notificationManager.createNotificationChannel(channelLoss);
        }

    }

    // channel.setShowBadge(false);
    // channel.setShowBadge(false);
    void lowglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        String msg = Applic.getContext().getString(R.string.alert_low) + " " + format(usedlocale, glucoseformat, gl);
        arrowglucosealarm(0, gl, msg, strgl, CHANNEL_LOW, alarm);
        if (!isWearable) {
            if (alarm) {
                tk.glucodata.WearInt.alarm("LOW " + strgl.value);
            }
        }
    }

    void highglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        String msg = Applic.getContext().getString(R.string.alert_high) + " " + format(usedlocale, glucoseformat, gl);
        arrowglucosealarm(1, gl, msg, strgl, CHANNEL_HIGH, alarm);
        if (!isWearable) {
            if (alarm) {
                tk.glucodata.WearInt.alarm("HIGH " + strgl.value);
            }
        }
    }

    void veryhighglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        String msg = Applic.getContext().getString(R.string.alert_very_high) + " "
                + format(usedlocale, glucoseformat, gl);
        arrowglucosealarm(6, gl, msg, strgl, GLUCOSEALARM, alarm);
        if (!isWearable) {
            if (alarm) {
                tk.glucodata.WearInt.alarm("HIGH " + strgl.value);
            }
        }
    }

    void verylowglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        String msg = Applic.getContext().getString(R.string.alert_very_low) + " "
                + format(usedlocale, glucoseformat, gl);
        arrowglucosealarm(5, gl, msg, strgl, GLUCOSEALARM, alarm);
        if (!isWearable) {
            if (alarm) {
                tk.glucodata.WearInt.alarm("LOW " + strgl.value);
            }
        }
    }

    void prehighglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        String msg = Applic.getContext().getString(R.string.alert_forecast_high) + " "
                + format(usedlocale, glucoseformat, gl);
        arrowglucosealarm(8, gl, msg, strgl, GLUCOSEALARM, alarm);
    }

    void prelowglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        String msg = Applic.getContext().getString(R.string.alert_forecast_low) + " "
                + format(usedlocale, glucoseformat, gl);
        arrowglucosealarm(7, gl, msg, strgl, GLUCOSEALARM, alarm);
    }

    static private final int glucosenotificationid = 81431;
    static private final int glucosealarmid = 81432;
    static boolean alertwatch = false;
    static private boolean showalways = Natives.getshowalways();

    static public String glucosestr(float gl) {
        return format(usedlocale, glucoseformat, gl);
    }

    static public void glucosestatus(boolean val) {
        showalways = val;
        Natives.setshowalways(val);
        if (!val) {
            if (onenot != null)
                onenot.novalue();
        } else {
            showoldglucose();
        }
    }

    boolean hasvalue = false;

    void showglucose(notGlucose strgl, float gl) {
        var message = format(usedlocale, glucoseformat, gl);
        arrowglucosenotification(2, gl, message, strgl, GLUCOSENOTIFICATION, true);
    }
    /*
     * void overwriteglucose() {
     * 
     * var strgl=SuperGattCallback.previousglucose;
     * if(strgl==null)
     * return;
     * showglucose(strgl,strgl.gl);
     * }
     */

    public static void showoldglucose() {
        var noti = onenot;
        if (noti == null)
            return;
        final var strgl = SuperGattCallback.previousglucose;
        final var gl = SuperGattCallback.previousglucosevalue;
        if (strgl == null || gl < 2.0f)
            return;
        noti.arrowglucosenotification(2, gl, format(usedlocale, glucoseformat, gl), strgl, GLUCOSENOTIFICATION, true);
    }

    void normalglucose(notGlucose strgl, float gl, float rate, boolean waiting) {
        MainActivity.showmessage = null;
        var act = MainActivity.thisone;
        if (act != null)
            act.cancelglucosedialog();
        {
            if (doLog) {
                Log.i(LOG_ID, "normalglucose waiting=" + waiting);
            }
            ;
        }
        ;
        if (waiting)
            arrowglucosealarm(2, gl, format(usedlocale, glucoseformat, gl), strgl, GLUCOSENOTIFICATION, true);

        else if (!isWearable) {
            {
                if (doLog) {
                    Log.i(LOG_ID, "arrowglucosenotification  alertwatch=" + alertwatch + " showalways=" + showalways);
                }
                ;
            }
            ;
            if (showalways || alertwatch) {
                var message = format(usedlocale, glucoseformat, gl);
                if (alertwatch)
                    makeseparatenotification(gl, message, strgl, GLUCOSENOTIFICATION, 2);
                arrowglucosenotification(2, gl, message, strgl, GLUCOSENOTIFICATION, !alertwatch);
            } else {
                if (hasvalue) {
                    if (keeprunning.started)
                        novalue();
                    else
                        notificationManager.cancel(glucosenotificationid);
                }
            }
        } else {
            notificationManager.cancel(glucosealarmid);
        }
    }

    NotificationManager notificationManager;

    // private static boolean isalarm=false;
    private static Runnable runstopalarm = null;
    private static ScheduledFuture<?> stopschedule = null;

    static public void stopalarm() {
        stopalarmnotsend(true);
    }

    static public void stopalarmnotsend(boolean send) {
        if (!getisalarm()) {
            {
                if (doLog) {
                    Log.d(LOG_ID, "stopalarm not is alarm");
                }
                ;
            }
            ;
            return;
        }
        {
            if (doLog) {
                Log.d(LOG_ID, "stopalarm is alarm");
            }
            ;
        }
        ;
        final var stopper = stopschedule;
        if (stopper != null) {
            stopper.cancel(false);
            stopschedule = null;
        }
        var runner = runstopalarm;
        if (runner != null) {
            if (!isWearable) {
                if (send)
                    Applic.app.numdata.stopalarm();
            }
            runner.run();
        }
    }
    // static int alarmnr=0;

    public static void playring(Ringtone ring, int duration, boolean sound, boolean flash, boolean vibrate,
            boolean disturb, int kind) {
        if (onenot == null)
            return;
        onenot.playringhier(ring, duration, sound, flash, vibrate, disturb, kind);
    }

    Vibrator vibrator = null;

    private void vibrateOneShot(Vibrator vibrator, long[] timings, int[] amplitudes) {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), ScanNfcV.audioattributes);
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), ScanNfcV.vibrationattributes);
        }
    }

    private void vibratealarm(int kind) {
        // Lookup profile from SharedPreferences for global alerts
        String profileName = "HIGH";
        try {
            android.content.SharedPreferences prefs = Applic.app.getSharedPreferences("tk.glucodata.alerts",
                    android.content.Context.MODE_PRIVATE);
            profileName = prefs.getString("alert_" + kind + "_volume", "HIGH");
        } catch (Exception e) {
        }
        vibratealarm(kind, profileName);
    }

    private void vibratealarm(int kind, String profileName) {
        var context = Applic.app;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            vibrator = ((VibratorManager) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)))
                    .getDefaultVibrator();
        } else
            vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);

        if (profileName == null)
            profileName = "HIGH";

        float scale = 1.0f;
        boolean ascending = false;

        switch (profileName) {
            case "MEDIUM":
                scale = 0.6f;
                break;
            case "ASCENDING":
                ascending = true;
                break;
            case "SILENT":
                scale = 0.0f;
                break;
            default:
                scale = 1.0f; // HIGH, VIBRATE_ONLY
        }

        if (scale <= 0.01f)
            return; // Silent

        // Define Patterns based on Kind (AlertType ID)
        long[] timings;
        int[] amplitudes;

        if (kind == 0) { // LOW: SOS-like (short-short-long)
            timings = new long[] { 0, 200, 100, 200, 100, 800, 200 };
            amplitudes = new int[] { 0, 255, 0, 255, 0, 255, 0 };
        } else if (kind == 1) { // HIGH: Rapid pulses
            timings = new long[] { 0, 150, 100, 150, 100, 150, 100, 150, 300 };
            amplitudes = new int[] { 0, 255, 0, 255, 0, 255, 0, 255, 0 };
        } else if (kind == 5) { // VERY_LOW: Intense, longer SOS (Urgent)
            timings = new long[] { 0, 300, 100, 300, 100, 300, 100, 1000, 200 };
            amplitudes = new int[] { 0, 255, 0, 255, 0, 255, 0, 255, 0 };
        } else if (kind == 6) { // VERY_HIGH: Double long buzz
            timings = new long[] { 0, 800, 200, 800, 500 };
            amplitudes = new int[] { 0, 255, 0, 255, 0 };
        } else if (kind == 7 || kind == 8) { // PRE_LOW / PRE_HIGH: Gentle wave
            timings = new long[] { 0, 400, 200, 400, 500 };
            amplitudes = new int[] { 0, 128, 0, 128, 0 }; // Lower intensity by default
        } else if (kind == 4) { // LOSS: Intermittent
            timings = new long[] { 0, 500, 1000, 500, 1000 };
            amplitudes = new int[] { 0, 200, 0, 200, 0 };
        } else { // DEFAULT (Missed Reading, etc)
            timings = new long[] { 0, 500, 200, 500, 500 };
            amplitudes = new int[] { 0, 200, 0, 200, 0 };
        }

        // Apply Scaling or Ascending Logic
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            for (int i = 0; i < amplitudes.length; i++) {
                if (amplitudes[i] > 0) {
                    if (ascending) {
                        // Simple ramp: later pulses get stronger, start at 30%
                        // Not implemented perfectly for looped, but fine for one-shot sequence
                        float progress = (float) i / amplitudes.length;
                        amplitudes[i] = (int) (amplitudes[i] * (0.3f + 0.7f * progress));
                    } else {
                        amplitudes[i] = (int) (amplitudes[i] * scale);
                    }
                    if (amplitudes[i] > 255)
                        amplitudes[i] = 255;
                    if (amplitudes[i] < 1)
                        amplitudes[i] = 1; // Ensure non-zero if it was meant to be on
                }
            }
            vibrateOneShot(vibrator, timings, amplitudes);
        } else {
            // Pre-Oreo fallback (no amplitudes support in standard API effectively)
            vibrator.vibrate(timings, -1);
        }

        {
            if (doLog) {
                Log.i(LOG_ID, "vibratealarm " + kind + " profile=" + profileName);
            }
            ;
        }
        ;
    }

    void stopvibratealarm() {
        vibrator.cancel();
    }

    private static int lastalarm = -1;

    static void stoplossalarm() {
        if (lastalarm == 4) {
            lastalarm = -1;
            stopalarm();
        }
    }

    private synchronized void playringhier(Ringtone ring, int duration, boolean sound, boolean flash, boolean vibrate,
            boolean disturb, int kind) {
        // Default: use global profile from SharedPrefs
        playringhier(ring, duration, sound, flash, vibrate, disturb, kind, null);
    }

    private synchronized void playringhier(Ringtone ring, int duration, boolean sound, boolean flash, boolean vibrate,
            boolean disturb, int kind, String intensityProfile) {
        // CAP DURATION to prevent infinite loops (max 120 seconds)
        if (duration <= 0 || duration > 120) {
            duration = 60; // Default to 60 seconds if invalid
            if (doLog)
                Log.i(LOG_ID, "Duration capped to 60s (was invalid or >120)");
        }

        notifyfocus = true;
        doTurnFocuson();
        stopalarm();
        // final int[] curfilter={-1};
        final boolean glucosealarm = kind < 2 || kind > 4;
        if (!DontTalk) {
            if (glucosealarm && Natives.speakalarms()) {
                final var glu = SuperGattCallback.previousglucose;
                if (glu != null) {
                    SuperGattCallback.talker.speak(glu.value,
                            getUSEALARM() ? ScanNfcV.audioattributes : notification_audio);
                    // Applic.scheduler.schedule( () -> SuperGattCallback.talker.speak(glu.value,
                    // getUSEALARM()?ScanNfcV.audioattributes:notification_audio), 50,
                    // TimeUnit.MILLISECONDS);
                }
            }
        }
        final boolean[] doplaysound = { true };
        if (sound) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int filt = notificationManager.getCurrentInterruptionFilter();
                if (doLog) {
                    Log.i(LOG_ID, "getCurrentInterruptionFilter()=" + filt + " disturb=" + disturb);
                }

                if (filt != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    // Phone is in some DND mode
                    if (disturb) {
                        // Override DND if we have permission
                        if (notificationManager.isNotificationPolicyAccessGranted()) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                        }
                    } else {
                        // Don't disturb - skip sound
                        doplaysound[0] = false;
                        if (doLog) {
                            Log.i(LOG_ID, "Skipping sound due to DND and disturb=false");
                        }
                    }
                }
            }
            if (doLog && doplaysound[0]) {
                Log.d(LOG_ID, "play " + ring.getTitle(app));
            }
        }
        runstopalarm = () -> {
            boolean wasAlarm = getisalarm(); // Check state BEFORE resetting it
            if (wasAlarm) {
                notifyfocus = false;
                lastalarm = -1; // Now safe to reset

                {
                    if (doLog) {
                        Log.d(LOG_ID, "runstopalarm  isalarm");
                    }
                    ;
                }
                ;
                if (sound) {
                    if (doplaysound[0]) {
                        try {
                            if (doLog) {
                                {
                                    if (doLog) {
                                        Log.d(LOG_ID, "stop sound " + ring.getTitle(app));
                                    }
                                    ;
                                }
                                ;
                            }
                            ring.stop();
                        } catch (Throwable th) {
                            Log.stack(LOG_ID, "ring.stop()", th);
                        }
                    }
                }

                if (!isWearable) {
                    if (flash)
                        Flash.stop();
                }
                if (vibrate) {
                    stopvibratealarm();
                }
                if (!DontTalk) {
                    if (glucosealarm && Natives.speakalarms()) {
                        final var glu = SuperGattCallback.previousglucose;
                        if (glu != null) {
                            Applic.scheduler.schedule(
                                    () -> SuperGattCallback.talker.speak(glu.value,
                                            getUSEALARM() ? ScanNfcV.audioattributes : notification_audio),
                                    300, TimeUnit.MILLISECONDS);
                        } else
                            doTurnFocusoff();
                    } else
                        doTurnFocusoff();
                    // overwriteglucose();
                } else
                    doTurnFocusoff();

                if (glucosealarm)
                    overwriteglucose(kind);
                setisalarm(false);

            } else {
                if (doLog) {
                    {
                        if (doLog) {
                            Log.d(LOG_ID, "runstopalarm not isalarm " + ring.getTitle(app));
                        }
                        ;
                    }
                    ;
                }
            }
        };
        lastalarm = kind;
        setisalarm(true);
        {
            if (doLog) {
                Log.d(LOG_ID, "schedule stop");
            }
            ;
        }
        ;

        // MOVED EFFECTS START HERE - SAFER
        // Get intensity profile: use passed value or look up from global settings
        final String profile = (intensityProfile != null) ? intensityProfile : getVolumeProfile(kind);
        int repeatCount = 1;
        switch (profile.toUpperCase()) {
            case "HIGH":
                repeatCount = 3;
                break;
            case "MEDIUM":
                repeatCount = 2;
                break;
            case "ASCENDING":
            default:
                repeatCount = 1;
                break;
        }
        final int finalRepeatCount = repeatCount;
        final int finalKind = kind;
        final boolean finalDisturb = disturb;

        if (sound) {
            if (doplaysound[0]) {
                ring.play();

                // Schedule additional plays for repeats > 1
                if (finalRepeatCount > 1) {
                    String ringUri = Natives.readring(kind);
                    for (int i = 1; i < finalRepeatCount; i++) {
                        final int delay = i * 2; // 2 seconds between each play
                        Applic.scheduler.schedule(() -> {
                            if (getisalarm()) {
                                Ringtone repeatRing = mkring(ringUri, finalKind, finalDisturb);
                                if (repeatRing != null) {
                                    repeatRing.play();
                                }
                            }
                        }, delay, TimeUnit.SECONDS);
                    }
                }
            }
        }
        if (!isWearable) {
            if (flash) {
                // Flash count based on intensity: Ascending=2, Medium=4, High=6
                int flashCount;
                switch (profile.toUpperCase()) {
                    case "HIGH":
                        flashCount = 6;
                        break;
                    case "MEDIUM":
                        flashCount = 4;
                        break;
                    case "ASCENDING":
                    default:
                        flashCount = 2;
                        break;
                }
                long flashPeriod = 200; // Fixed 200ms period for all intensities
                Flash.start(app, flashPeriod, flashCount);
            }
        }
        if (vibrate) {
            vibratealarm(kind, profile);
        }

        stopschedule = Applic.scheduler.schedule(runstopalarm, duration, TimeUnit.SECONDS);

    }

    private String getDeliveryMode(int kind) {
        try {
            android.content.SharedPreferences prefs = Applic.app.getSharedPreferences("tk.glucodata.alerts",
                    android.content.Context.MODE_PRIVATE);
            String key = "alert_" + kind + "_delivery";
            String mode = prefs.getString(key, "SYSTEM_ALARM");
            if (doLog) {
                Log.d(LOG_ID, "getDeliveryMode kind=" + kind + " key=" + key + " val=" + mode);
            }
            return mode;
        } catch (Exception e) {
            return "SYSTEM_ALARM";
        }
    }

    private String getVolumeProfile(int kind) {
        try {
            android.content.SharedPreferences prefs = Applic.app.getSharedPreferences("tk.glucodata.alerts",
                    android.content.Context.MODE_PRIVATE);
            return prefs.getString("alert_" + kind + "_volume", "HIGH");
        } catch (Exception e) {
            return "HIGH";
        }
    }

    private long getFlashPeriodFromProfile(String profile) {
        if (profile == null)
            return 150L;
        switch (profile.toUpperCase()) {
            case "LOW":
                return 1000L;
            case "MEDIUM":
                return 500L;
            case "HIGH":
            case "ASCENDING":
            default:
                return 150L;
        }
    }

    private float getVolumeFromProfile(String profile) {
        if (profile == null)
            return 1.0f;
        switch (profile.toUpperCase()) {
            case "LOW":
                return 0.4f;
            case "MEDIUM":
                return 0.7f;
            case "HIGH":
            case "ASCENDING":
            default:
                return 1.0f;
        }
    }

    void mksound(int kind) {
        String ringUri = null;
        try {
            android.content.SharedPreferences prefs = Applic.app.getSharedPreferences("tk.glucodata.alerts",
                    android.content.Context.MODE_PRIVATE);
            ringUri = prefs.getString("alert_" + kind + "_soundUri", null);
        } catch (Exception e) {
            if (doLog)
                Log.i(LOG_ID, "Error reading custom sound pref: " + e.toString());
        }

        if (ringUri == null || ringUri.isEmpty()) {
            ringUri = Natives.readring(kind);
        }

        final Ringtone ring = // rings[kind];
                mkring(ringUri, kind);

        // Read settings from Prefs (AlertRepository) to support new Alert Types that
        // Natives doesn't know about
        android.content.SharedPreferences p = Applic.app.getSharedPreferences("tk.glucodata.alerts",
                android.content.Context.MODE_PRIVATE);

        // Defaults from Natives for legacy (0-8), or standard defaults for new types
        // SPECIAL CASE: For LOSS (4), Natives now likely holds the Timeout value (e.g.
        // 20 min).
        // So we force default sound duration to 60s instead of reading Natives, to
        // prevent 20-min alarm sounds.
        int defDuration;
        if (kind == 4) {
            defDuration = 60;
        } else {
            defDuration = (kind <= 8) ? Natives.readalarmduration(kind) : 0;
        }

        boolean defSound = (kind <= 8) ? Natives.alarmhassound(kind) : true;
        boolean defFlash = (kind <= 8) ? Natives.alarmhasflash(kind) : true;
        boolean defVibrate = (kind <= 8) ? Natives.alarmhasvibration(kind) : true;

        final int duration = p.getInt("alert_" + kind + "_alarmDur", defDuration);
        final boolean flash = p.getBoolean("alert_" + kind + "_flash", defFlash);
        final boolean sound = p.getBoolean("alert_" + kind + "_sound", defSound);
        final boolean vibration = p.getBoolean("alert_" + kind + "_vibration", defVibrate);

        final boolean dist = isWearable || getalarmdisturb(kind); // DND might need Prefs too, but keeping Natives for
                                                                  // now

        // DEBUG LOGGING
        Log.i(LOG_ID, "mksound DEBUG: kind=" + kind + " ring=" + (ring != null ? ring.getTitle(app) : "NULL")
                + " duration=" + duration + " sound=" + sound + " flash=" + flash + " vibration=" + vibration);

        playringhier(ring, duration, sound, flash, vibration, dist, kind);
    }

    /**
     * Test an alarm type by triggering the full alarm flow with dummy data.
     */
    private static long lastTestTime = 0;

    public static void testTrigger(int kind) {
        long now = System.currentTimeMillis();
        if (now - lastTestTime < 2000)
            return; // Debounce 2s
        lastTestTime = now;

        // Run on main thread to be safe with UI/Toasts
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            boolean isMmol = tk.glucodata.Applic.unit == 1;
            float dummyValue;
            String typeStr;
            String message;

            // Determine appropriate dummy values based on kind
            switch (kind) {
                case 0: // Low
                    dummyValue = isMmol ? 3.5f : 63f;
                    typeStr = "glucoseNotification"; // Revert to legacy channel to avoid startForeground crash
                    message = isMmol ? "LOW 3.5" : "LOW 63";
                    break;
                case 1: // High
                    dummyValue = isMmol ? 12.0f : 216f;
                    typeStr = "glucoseNotification"; // Revert to legacy channel
                    message = isMmol ? "HIGH 12.0" : "HIGH 216";
                    break;
                case 4: // Loss (AlertType.LOSS.id = 4)
                    dummyValue = 0f;
                    typeStr = "glucoseNotification"; // Revert to legacy channel
                    message = "Signal Loss";
                    break;
                default:
                    dummyValue = isMmol ? 3.5f : 63f;
                    typeStr = "glucoseNotification";
                    message = "Test Alert";
            }

            if (onenot != null) {
                // Reset state so test always plays sound
                AlertType alertType = AlertType.Companion.fromId(kind);
                if (alertType != null) {
                    AlertStateTracker.INSTANCE.resetState(alertType);
                }

                if (kind == 4) {
                    onenot.lossofsignalalarm(kind, R.drawable.loss, message, typeStr, true);
                } else {
                    notGlucose dummyGlucose = new notGlucose(System.currentTimeMillis(), String.valueOf(dummyValue), 0f,
                            0);
                    onenot.arrowglucosealarm(kind, dummyValue, message, dummyGlucose, typeStr, true);
                }
            }
        });
    }

    /**
     * Trigger a Custom Alert. Called from CustomAlertManager when a custom
     * threshold is crossed.
     * Uses the existing alarm flow via playringhier for guaranteed stop.
     */

    public static void triggerCustomAlert(String soundUri, boolean sound, boolean vibrate, boolean flash,
            boolean isHigh, float glucoseValue, String deliveryMode, String volumeProfile, int durationSeconds,
            boolean overrideDnd) {
        triggerCustomAlertInternal(soundUri, sound, vibrate, flash, isHigh, glucoseValue, false, deliveryMode,
                volumeProfile, durationSeconds, overrideDnd);
    }

    public static void testCustomTrigger(String soundUri, boolean sound, boolean vibrate, boolean flash,
            boolean isHigh, String deliveryMode, String volumeProfile, int durationSeconds, boolean overrideDnd) {
        boolean isMmol = tk.glucodata.Applic.unit == 1;
        float dummyValue = isHigh ? (isMmol ? 12.0f : 216f) : (isMmol ? 3.5f : 63f);
        triggerCustomAlertInternal(soundUri, sound, vibrate, flash, isHigh, dummyValue, true, deliveryMode,
                volumeProfile, durationSeconds, overrideDnd);
    }

    private static long lastCustomTriggerTime = 0;

    private static void triggerCustomAlertInternal(String soundUri, boolean sound, boolean vibrate, boolean flash,
            boolean isHigh, float glucoseValue, boolean isTest, String deliveryMode,
            String volumeProfile, int durationSeconds, boolean overrideDnd) {

        long now = System.currentTimeMillis();
        // Debounce only for test mode to prevent accidental double-clicks
        if (isTest) {
            if (now - lastCustomTriggerTime < 2000)
                return;
        }
        lastCustomTriggerTime = now;

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (onenot != null) {
                int kind = isHigh ? 1 : 0;

                // For test scenarios, reset tracker state so sound always plays
                if (isTest) {
                    AlertType alertType = AlertType.Companion.fromId(kind);
                    if (alertType != null) {
                        AlertStateTracker.INSTANCE.resetState(alertType);
                    }
                }

                String message = isTest
                        ? ("Test Custom " + (isHigh ? "High" : "Low"))
                        : (isHigh ? "Custom High " : "Custom Low ")
                                + format(util.getlocale(), glucoseformat, glucoseValue);

                String typeStr = "glucoseNotification";
                notGlucose glucoseStr = new notGlucose(System.currentTimeMillis(), String.valueOf(glucoseValue), 0f, 0);

                // Delivery Mode Logic
                String mode = (deliveryMode != null) ? deliveryMode.toUpperCase() : "NOTIFICATION";
                boolean isAlarmMode = mode.equals("ALARM") || mode.equals("SYSTEM_ALARM");
                boolean isBothMode = mode.equals("BOTH");
                boolean isNotificationMode = mode.equals("NOTIFICATION") || mode.equals("NOTIFICATION_ONLY");

                boolean activityLaunched = false;

                // 1. Launch Alarm Activity (if ALARM or BOTH)
                if (isAlarmMode || isBothMode) {
                    // Custom alerts don't have rate info typically, pass 0f
                    activityLaunched = showpopupalarm(message, true, 0f);
                }

                boolean skipBanner = false;
                // If Alarm Activity launched successfully and NOT in Both mode, try to skip
                // banner
                if (activityLaunched && isAlarmMode && !isBothMode) {
                    boolean hasOverlayPerm = Build.VERSION.SDK_INT < 23
                            || android.provider.Settings.canDrawOverlays(Applic.app);
                    boolean isForeground = (MainActivity.thisone != null);
                    if (hasOverlayPerm || isForeground) {
                        skipBanner = true;
                    }
                }

                // 2. Heads-Up Notification (Separate) - This is what standard alerts do!
                // Only if we shouldn't skip the banner (Notification mode, Both mode, or Alarm
                // mode failure)
                if (!skipBanner) {
                    onenot.makeseparatenotification(glucoseValue, message, glucoseStr, typeStr, kind);
                }

                // 3. Sound/Flash/Vibrate - Use standard playringhier (handles repeats, stop,
                // etc.)
                if (sound || flash || vibrate) {
                    String actualUri;
                    if (soundUri == null || soundUri.isEmpty()) {
                        actualUri = Natives.readring(kind);
                    } else if ("SYSTEM_DEFAULT".equals(soundUri)) {
                        // Use Android's system default notification sound
                        actualUri = android.media.RingtoneManager
                                .getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION).toString();
                    } else {
                        actualUri = soundUri;
                    }

                    // Duration from config (default 10s for test, 30s for real)
                    int finalDuration = (durationSeconds > 0) ? durationSeconds : (isTest ? 10 : 30);
                    boolean disturb = isWearable || overrideDnd;

                    Log.i(LOG_ID, "Custom Alert DND check: overrideDnd=" + overrideDnd + " isWearable=" + isWearable
                            + " disturb=" + disturb + " intensity=" + volumeProfile);

                    // Pass disturb to mkring so it uses the correct audio stream
                    Ringtone ring = onenot.mkring(actualUri, kind, disturb);

                    // playringhier with intensity profile - uses custom alert's intensity not
                    // global
                    onenot.playringhier(ring, finalDuration, sound, flash, vibrate, disturb, kind, volumeProfile);

                    if (doLog)
                        Log.i(LOG_ID, "Custom Alert: sound=" + sound + " flash=" + flash + " vibrate=" + vibrate
                                + " duration=" + finalDuration + " disturb=" + disturb + " intensity=" + volumeProfile);
                }

                // 4. Update Persistent Notification (Silent)
                // Use 'true' for 'once' to ensure it's a silent update to the persistent
                // channel
                // This keeps the persistent notification in sync without double-alerting
                onenot.arrowplacelargenotification(kind, glucoseValue, message, glucoseStr, typeStr, true);
            }
        });
    }

    private boolean shouldLaunchAlarmActivity(int kind) {
        try {
            android.content.SharedPreferences prefs = Applic.app.getSharedPreferences("tk.glucodata_preferences",
                    Context.MODE_PRIVATE);
            String typeKey;
            switch (kind) {
                case 0:
                    typeKey = "low";
                    break;
                case 1:
                    typeKey = "high";
                    break;
                case 2:
                    typeKey = "loss";
                    break;
                default:
                    return true; // Default to system alarm for unknown types
            }
            // Default to true (legacy behavior) unless disabled
            return prefs.getBoolean("alert_" + typeKey + "_use_system_alarm", true);
        } catch (Exception e) {
            return true;
        }
    }

    private static void setmessage(String message, Boolean cancel) {
        {
            if (doLog) {
                Log.i(LOG_ID, "setmessage " + message + " " + cancel);
            }
            ;
        }
        ;
        if (cancel) {
            MainActivity.showmessage = message;
        } else {
            MainActivity.shownummessage.push(message);
        }
    }

    private static boolean showpopupalarm(String message, Boolean cancel, float rate) {
        if (cancel) {
            MainActivity.showmessage = null;
        }

        try {
            // Launch AlarmActivity (Reflected)
            Class<?> alarmClass = Class.forName("tk.glucodata.ui.AlarmActivity");
            Intent alarmIntent = new Intent(Applic.app, alarmClass);
            alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            // message typically contains "LOW 3.9 mmol/L"
            // Simple parsing or just pass as VAL for now
            alarmIntent.putExtra("EXTRA_GLUCOSE_VAL", message);
            alarmIntent.putExtra("EXTRA_ALARM_TYPE", "ALARM");
            alarmIntent.putExtra("EXTRA_RATE", rate);
            Applic.app.startActivity(alarmIntent);
            return true;
        } catch (ClassNotFoundException e) {
            if (doLog)
                Log.e(LOG_ID, "AlarmActivity not found (WearOS?): " + e.toString());
            return false;
        } catch (Exception e) {
            if (doLog)
                Log.e(LOG_ID, "showpopupalarm failed: " + e.toString());
            return false;
        }
    }

    private void soundalarm(int kind, int draw, String message, String type, boolean alarm) {
        if (alarm) {
            {
                if (doLog) {
                    Log.d(LOG_ID, "soundalarm " + kind);
                }
                ;
            }
            ;
            mksound(kind);
        }
        placelargenotification(draw, message, type, !alarm);
    }

    // private int wasdraw=-1;
    private float wasvalue = 0.0f;
    private String wasmessage = null, wastype;

    void overwriteglucose(int kind) {
        // if(wasdraw==-1) return;
        if (wasvalue < 0.1f)
            return;
        var strgl = SuperGattCallback.previousglucose;
        if (strgl == null)
            return;
        arrowglucosenotification(kind, wasvalue, wasmessage, strgl, wastype, true);
        wasvalue = 0.0f;
    }

    private void arrowsoundalarm(int kind, float glvalue, String message, notGlucose sglucose, String type,
            boolean alarm, boolean skipBanner) {
        if (alarm) {
            // wasdraw=draw;
            wasvalue = glvalue;
            wasmessage = message;
            wastype = type;
            if (!skipBanner) {
                makeseparatenotification(glvalue, message, sglucose, type, kind);
            }
            {
                if (doLog) {
                    Log.d(LOG_ID, "arrowsoundalarm " + kind + " skipBanner=" + skipBanner);
                }
                ;
            }
            ;
            mksound(kind);
        }
        // Force silent update (true) if alarm is active to prevent duplicate alerts
        // from persistent notification
        arrowplacelargenotification(kind, glvalue, message, sglucose, type, alarm ? true : !alarm);
    }

    private void lossofsignalalarm(int kind, int draw, String message, String type, boolean alarm) {
        {
            if (doLog) {
                Log.i(LOG_ID, "glucose alarm kind=" + kind + " " + message + " alarm=" + alarm);
            }
            ;
        }
        ;
        if (alarm) {
            if (kind != 2) {
                String deliveryMode = getDeliveryMode(kind);
                boolean isSystem = "SYSTEM_ALARM".equals(deliveryMode);
                boolean isBoth = "BOTH".equals(deliveryMode);

                if (isSystem || isBoth) {
                    showpopupalarm(message, true, Float.NaN);
                }
            }
        } else {
            final var act = MainActivity.thisone;
            if (act != null) {
                {
                    if (doLog) {
                        Log.i(LOG_ID, "act!=null");
                    }
                    ;
                }
                ;
                act.replaceDialogMessage(message);
            }
            {
                if (doLog) {
                    Log.i(LOG_ID, "act==null");
                }
                ;
            }
            ;
            if (MainActivity.showmessage != null)
                MainActivity.showmessage = message;
        }
        if (!alarm && alertwatch)
            lossofsensornotification(draw, message, GLUCOSENOTIFICATION, false);
        else
            soundalarm(kind, draw, message, type, alarm);
    }

    private void arrowglucosealarm(int kind, float glvalue, String message, notGlucose strglucose, String type,
            boolean alarm) {
        {
            if (doLog) {
                Log.i(LOG_ID, "arrowglucosealarm kind=" + kind + " " + message + " alarm=" + alarm);
            }
            ;
        }
        ;

        boolean activityLaunched = false;
        boolean skipBanner = false;
        boolean incomingAlarm = alarm; // Capture initial state from Native/Caller
        AlertType alertType = null;

        // Resolve AlertType early
        try {
            alertType = AlertType.Companion.fromId(kind);
        } catch (Exception e) {
            Log.e(LOG_ID, "Error resolving AlertType: " + e.toString());
        }

        if (alarm) {

            // SNOOZE CHECK & RETRY LOGIC
            try {
                if (alertType != null) {
                    AlertConfig config = AlertRepository.INSTANCE.loadConfig(alertType);

                    // Unified Retry & Snooze Check
                    if (!AlertStateTracker.INSTANCE.shouldTrigger(alertType, config)) {
                        if (doLog)
                            Log.i(LOG_ID, "Alert Suppressed (Snoozed or Retry Logic): kind=" + kind);
                        // Downgrade to silent update:
                        // This skips popup/sound but allows notification text update
                        alarm = false;
                    } else {
                        // If we proceed, record the trigger
                        AlertStateTracker.INSTANCE.onAlertTriggered(alertType);
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_ID, "Error checking alert state: " + e.toString());
            }

            // Re-check alarm as it might have been set to false above
            if (alarm) {
                if (kind != 2) {
                    // UNIFIED LOGIC: Check Delivery Mode preference for ALL types (Legacy & New)
                    String deliveryMode = getDeliveryMode(kind);

                    boolean isSystem = "SYSTEM_ALARM".equals(deliveryMode);
                    boolean isBoth = "BOTH".equals(deliveryMode);

                    // Launch ONLY if System or Both is explicitly selected.
                    boolean forceLaunch = isSystem || isBoth;

                    if (doLog) {
                        Log.i(LOG_ID, String.format("Alert Debug: kind=%d deliveryMode=%s forceLaunch=%b", kind,
                                deliveryMode, forceLaunch));
                    }

                    if (forceLaunch) {
                        float rate = (strglucose != null) ? strglucose.rate : Float.NaN;
                        activityLaunched = showpopupalarm(message, true, rate);
                        if (doLog)
                            Log.i(LOG_ID, "Alert Debug: showpopupalarm returned " + activityLaunched);
                    }

                    // If System Alarm launched successfully and we are NOT in Both mode, skip the
                    // banner ONLY IF we are sure the activity will show (Overlay Perm or
                    // Foreground)
                    if (activityLaunched && isSystem && !isBoth) {
                        boolean hasOverlayPerm = Build.VERSION.SDK_INT < 23
                                || android.provider.Settings.canDrawOverlays(Applic.app);
                        boolean isForeground = (MainActivity.thisone != null);

                        if (hasOverlayPerm || isForeground) {
                            skipBanner = true;
                        } else {
                            if (doLog)
                                Log.i(LOG_ID,
                                        "System Alarm logic: Activity launched but banner NOT skipped (no overlay perm & bg)");
                        }
                    }
                    if (doLog)
                        Log.i(LOG_ID, "Alert Debug: skipBanner=" + skipBanner);
                }
            }
        } else {
            // Processing for SILENT updates (alarm was false initially, OR
            // suppressed/downgraded above)

            // CRITICAL FIX: If incomingAlarm was false, it means Native logic (or caller)
            // decided
            // the alarm condition is NOT active (or cleared).
            // We must RESET the AlertStateTracker so it doesn't get stuck thinking the
            // episode is
            // still ongoing forever (preventing future triggers).
            if (!incomingAlarm && alertType != null) {
                // Only reset if it was naturally silent, NOT if we suppressed it ourselves.
                AlertStateTracker.INSTANCE.resetState(alertType);
            }

            final var act = MainActivity.thisone;
            if (act != null) {
                {
                    if (doLog) {
                        Log.i(LOG_ID, "act!=null");
                    }
                    ;
                }
                ;
                act.replaceDialogMessage(message);
            }
            if (MainActivity.showmessage != null) {
                {
                    if (doLog) {
                        Log.i(LOG_ID, "MainActivity.showmessage=" + message);
                    }
                    ;
                }
                ;
                MainActivity.showmessage = message;
            }
        }
        if (!alarm && alertwatch) {
            {
                if (doLog) {
                    Log.i(LOG_ID, "arrowglucosealarm alertwatch=" + alertwatch);
                }
                ;
            }
            ;
            arrowglucosenotification(kind, glvalue, message, strglucose, GLUCOSENOTIFICATION, false);
        } else
            arrowsoundalarm(kind, glvalue, message, strglucose, type, alarm, skipBanner);
    }

    private void canceller() {
        notificationManager.cancel(glucosenotificationid);
        notificationManager.cancel(numalarmid);
    }

    static public void cancelmessages() {
        if (onenot != null)
            onenot.canceller();

    }

    static final String fromnotification = "FromNotification";
    final static int forcecloserequest = 10;
    final static int stopalarmrequest = 8;
    // static final String closename= "ForceClose";
    final static int penmutable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
            ? PendingIntent.FLAG_IMMUTABLE
            : 0;

    private final boolean makeicon = !isWearable && android.os.Build.VERSION.SDK_INT >= 23;
    private final StatusIcon icons = makeicon ? new StatusIcon(Applic.app) : null;

    static int getMaxGlucose(int sensorgen) {
        if (sensorgen == 0x40 || sensorgen == 0x20)
            return 400;
        return 500;
    }

    static private String getglstring(float glvalue, int sensorgen2) {
        var maxglucose = getMaxGlucose(sensorgen2);
        if (Applic.unit == 1) {
            if (glvalue < 2.2f) {
                return "2.2>";
            }
            if (glvalue > (((double) maxglucose) / Applic.mgdLmult)) {
                return "27.8<";
            }
            var glstr = format(util.getlocale(), Notify.pureglucoseformat, glvalue);
            // User requested to KEEP ",0" - Removed trimming logic
            // if (glstr.charAt(glstr.length() - 1) == '0')
            // glstr = glstr.substring(0, glstr.length() - 2);
            return glstr;
        } else {
            int intval = (int) glvalue;
            if (intval < 40)
                return "40>";
            if (intval > maxglucose)
                return "500<";
            return format(util.getlocale(), Notify.pureglucoseformat, glvalue);
        }
    }

    private void setIcon(Notification.Builder GluNotBuilder, float glvalue, int sensorgen2) {
        boolean hideIcon = Applic.app.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
                .getBoolean("notification_hide_status_icon", false);

        if (hideIcon) {
            GluNotBuilder.setSmallIcon(R.drawable.transparent_icon);
            return;
        }

        if (makeicon) {
            final var icon = icons.getIcon(getglstring(glvalue, sensorgen2));
            GluNotBuilder.setSmallIcon(icon);
        } else {
            var draw = GlucoseDraw.getgludraw(glvalue, sensorgen2);
            GluNotBuilder.setSmallIcon(draw);
        }
    }

    private void makeseparatenotification(float glvalue, String message, notGlucose glucose, String type,
            int alertTypeId) {
        if (!isWearable) {
            if (alertseparate) {
                // notificationManager.cancel(glucosealarmid); // Performance optimization:
                // Don't cancel, just overwrite
                var intent = mkpending();
                var GluNotBuilder = mkbuilderintent(type, intent);
                // Swipe Dismiss Action (triggers same logic as Dismiss button)
                Intent swipeDismissIntent = new Intent(Applic.app, tk.glucodata.receivers.AlarmActionReceiver.class);
                swipeDismissIntent.setAction(tk.glucodata.receivers.AlarmActionReceiver.ACTION_DISMISS);
                swipeDismissIntent.putExtra("alert_type_id", alertTypeId);
                PendingIntent swipeDismissPendingIntent = PendingIntent.getBroadcast(Applic.app, 4, swipeDismissIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | penmutable);
                GluNotBuilder.setDeleteIntent(swipeDismissPendingIntent);
                {
                    if (doLog) {
                        Log.i(LOG_ID, "makeseparatenotification " + glucose.value);
                    }
                    ;
                }
                ;

                setIcon(GluNotBuilder, glvalue, glucose.sensorgen2);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // final int timeout= Build.VERSION.SDK_INT >= 30? 60*1500:60*3000;
                    final int timeout = 800 * 60;// Build.VERSION.SDK_INT >= 30? 60*1500:60*3000;
                    GluNotBuilder.setTimeoutAfter(timeout);
                }
                GluNotBuilder.setPriority(Notification.PRIORITY_HIGH);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    GluNotBuilder.setCategory(Notification.CATEGORY_ALARM);
                }

                // UNIFIED LOGIC: Only attach Full Screen Intent if NOT in "Notification Only"
                // mode.
                String currentDeliveryMode = getDeliveryMode(alertTypeId);
                if (!"NOTIFICATION_ONLY".equals(currentDeliveryMode)) {
                    // Use Reflection for Intent creation to safe-guard against Missing Class on
                    // Wear
                    try {
                        Class<?> alarmClass = Class.forName("tk.glucodata.ui.AlarmActivity");
                        Intent fullScreenIntent = new Intent(Applic.app, alarmClass);
                        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        fullScreenIntent.putExtra("EXTRA_GLUCOSE_VAL", glucose.value);
                        fullScreenIntent.putExtra("EXTRA_RATE", glucose.rate);
                        fullScreenIntent.putExtra("EXTRA_ALARM_TYPE", "ALARM");

                        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(Applic.app, 3,
                                fullScreenIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | penmutable);
                        GluNotBuilder.setFullScreenIntent(fullScreenPendingIntent, true);
                    } catch (ClassNotFoundException e) {
                        if (doLog)
                            Log.e(LOG_ID, "AlarmActivity not found (WearOS?): " + e.toString());
                    }
                }

                // Add Snooze Action
                Intent snoozeIntent = new Intent(Applic.app, tk.glucodata.receivers.AlarmActionReceiver.class);
                snoozeIntent.setAction(tk.glucodata.receivers.AlarmActionReceiver.ACTION_SNOOZE);
                snoozeIntent.putExtra("alert_type_id", alertTypeId);
                PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(Applic.app, 1, snoozeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | penmutable);
                if (Build.VERSION.SDK_INT >= 23) {
                    android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithResource(Applic.app,
                            R.drawable.ic_snooze);
                    Notification.Action snoozeAction = new Notification.Action.Builder(icon, "Snooze",
                            snoozePendingIntent).build();
                    GluNotBuilder.addAction(snoozeAction);
                } else if (Build.VERSION.SDK_INT >= 20) {
                    GluNotBuilder.addAction(R.drawable.ic_snooze, "Snooze", snoozePendingIntent);
                }

                // Add Dismiss Action
                Intent dismissIntent = new Intent(Applic.app, tk.glucodata.receivers.AlarmActionReceiver.class);
                dismissIntent.setAction(tk.glucodata.receivers.AlarmActionReceiver.ACTION_DISMISS);
                dismissIntent.putExtra("alert_type_id", alertTypeId);
                PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(Applic.app, 2, dismissIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | penmutable);
                if (Build.VERSION.SDK_INT >= 23) {
                    android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithResource(Applic.app,
                            R.drawable.ic_dismiss);
                    Notification.Action dismissAction = new Notification.Action.Builder(icon, "Dismiss",
                            dismissPendingIntent).build();
                    GluNotBuilder.addAction(dismissAction);
                } else if (Build.VERSION.SDK_INT >= 20) {
                    GluNotBuilder.addAction(R.drawable.ic_dismiss, "Dismiss", dismissPendingIntent);
                }

                // --- RICH UI START (Minimal: Value + Arrow + Alert Name) ---
                // Fetch Layout Prefs
                android.content.SharedPreferences prefs = Applic.app.getSharedPreferences("tk.glucodata_preferences",
                        Context.MODE_PRIVATE);
                float fontSize = prefs.getFloat("notification_font_size", 1.0f);
                int fontWeight = prefs.getInt("notification_font_weight", 400);
                boolean showArrow = prefs.getBoolean("notification_show_arrow", true);
                float arrowSize = prefs.getFloat("notification_arrow_size", 1.0f);
                boolean isMmol = Applic.unit == 1;

                // Data Prep
                int glucoseColor = NotificationChartDrawer.getGlucoseColor(Applic.app, glvalue, isMmol);
                Bitmap arrowBitmap = showArrow
                        ? NotificationChartDrawer.drawArrow(Applic.app, glucose.rate, isMmol, glucoseColor, arrowSize)
                        : null;

                // Fetch Native Points for Consistent Text Formatting (Raw/Auto)
                long endT = System.currentTimeMillis();
                long recentStartT = endT - 10 * 60 * 1000L;
                java.util.List<GlucosePoint> nativePoints = new java.util.ArrayList<>();
                try {
                    long[] historyRaw = Natives.getGlucoseHistory(recentStartT / 1000L);
                    if (historyRaw != null) {
                        for (int i = 0; i < historyRaw.length; i += 3) {
                            long t = historyRaw[i] * 1000L;
                            float val = historyRaw[i + 1] / 10.0f;
                            float valRaw = historyRaw[i + 2] / 10.0f;
                            if (isMmol) {
                                val /= 18.0182f;
                                valRaw /= 18.0182f;
                            }
                            nativePoints.add(new GlucosePoint(t, val, valRaw));
                        }
                    }
                } catch (Exception e) {
                }

                // Determine ViewMode for formatting
                int viewMode = 0;
                String mainName = Natives.lastsensorname();
                if (mainName != null && !mainName.isEmpty()) {
                    long ptr = Natives.getdataptr(mainName);
                    if (ptr != 0)
                        viewMode = Natives.getViewMode(ptr);
                }

                CharSequence valueText = formatGlucoseText(glucose.value, glvalue, nativePoints, viewMode,
                        glucose.time);

                // Construct RemoteViews Matches Regular Notification
                RemoteViews remoteViews = new RemoteViews(Applic.app.getPackageName(), R.layout.notification_material);
                RemoteViews remoteViewsExpanded = new RemoteViews(Applic.app.getPackageName(),
                        R.layout.notification_material_expanded);

                // Clean message: "Forecast Low 4.0 mmol/L" -> "Forecast Low"
                String cleanMessage = message.replaceAll("[0-9.,]+", "").replaceAll("mmol/L", "")
                        .replaceAll("mg/dL", "").trim();

                // Font Styling
                // Initialize ssb first!!
                android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(valueText);

                // Prepend Status if present (User Request: "Forecast Low 4,0")
                if (!cleanMessage.isEmpty()) {
                    android.text.SpannableStringBuilder ssbStatus = new android.text.SpannableStringBuilder(
                            cleanMessage + " ");
                    ssb.insert(0, ssbStatus);
                }

                String family = (fontWeight >= 500) ? "google-sans-medium" : "google-sans";
                ssb.setSpan(new android.text.style.TypefaceSpan(family), 0, ssb.length(),
                        android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                if (fontSize != 1.0f)
                    ssb.setSpan(new android.text.style.RelativeSizeSpan(fontSize), 0, ssb.length(),
                            android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);

                // Set Views (Collapsed)
                remoteViews.setTextViewText(R.id.notification_glucose, ssb);
                remoteViews.setTextColor(R.id.notification_glucose, glucoseColor);
                remoteViews.setTextViewTextSize(R.id.notification_glucose, android.util.TypedValue.COMPLEX_UNIT_SP,
                        24 * fontSize);

                // Hide Alert Name Status (now in main line)
                remoteViews.setViewVisibility(R.id.notification_status, View.GONE);

                if (showArrow && arrowBitmap != null) {
                    remoteViews.setViewVisibility(R.id.notification_arrow, View.VISIBLE);
                    remoteViews.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);
                } else {
                    remoteViews.setViewVisibility(R.id.notification_arrow, View.GONE);
                }

                // Hide Chart & Container
                remoteViews.setViewVisibility(R.id.notification_chart, View.GONE);
                remoteViews.setViewVisibility(R.id.chart_container, View.GONE);

                // Set Views (Expanded)
                remoteViewsExpanded.setTextViewText(R.id.notification_glucose, ssb);
                remoteViewsExpanded.setTextColor(R.id.notification_glucose, glucoseColor);
                remoteViewsExpanded.setTextViewTextSize(R.id.notification_glucose,
                        android.util.TypedValue.COMPLEX_UNIT_SP, 28 * fontSize);

                remoteViewsExpanded.setViewVisibility(R.id.notification_status, View.GONE);

                if (showArrow && arrowBitmap != null) {
                    remoteViewsExpanded.setViewVisibility(R.id.notification_arrow, View.VISIBLE);
                    remoteViewsExpanded.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);
                } else {
                    remoteViewsExpanded.setViewVisibility(R.id.notification_arrow, View.GONE);
                }

                // Hide Chart in Expanded too
                remoteViewsExpanded.setViewVisibility(R.id.notification_chart, View.GONE);

                // Bind to Builder
                if (Build.VERSION.SDK_INT >= 24) {
                    GluNotBuilder.setStyle(new Notification.DecoratedCustomViewStyle());
                    GluNotBuilder.setCustomContentView(remoteViews);
                    GluNotBuilder.setCustomBigContentView(remoteViewsExpanded);
                } else {
                    GluNotBuilder.setContent(remoteViews);
                }
                // --- RICH UI END ---

                Notification notif = GluNotBuilder.build();
                notif.when = glucose.time;
                notificationManager.notify(glucosealarmid, notif);
            }
        }
    }

    static public boolean alertseparate = false;

    static public PendingIntent mkpending() {
        {
            if (doLog) {
                Log.i(LOG_ID, "mkpending");
            }
            ;
        }
        ;
        Intent notifyIntent = new Intent(Applic.app, MainActivity.class);
        notifyIntent.putExtra(fromnotification, true);
        notifyIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notifyIntent.setAction(Intent.ACTION_MAIN);
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(Applic.app, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT | penmutable);
    }

    private Notification.Builder mkbuilderintent(String type, PendingIntent notifyPendingIntent) {
        Notification.Builder GluNotBuilder;
        if (true) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                GluNotBuilder = new Notification.Builder(Applic.app, type);
            } else {
                GluNotBuilder = new Notification.Builder(Applic.app);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                GluNotBuilder.setChannelId(type);
        }
        GluNotBuilder.setContentIntent(notifyPendingIntent).setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT >= 20) {
            GluNotBuilder.setLocalOnly(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            GluNotBuilder.setGroup("aa2");
        }
        return GluNotBuilder;
    }

    // Helper to format "Value · Raw" consistently
    // Helper to format "Value · Raw" consistently
    // Helper to format "Value · Raw" consistently
    // Helper to format "Value · Raw" consistently
    // Helper to format "Value · Raw" with HTML styling and comma separator
    // Helper to format "Value · Raw" with HTML styling and comma separator
    public static CharSequence formatGlucoseText(String value, float glvalue, java.util.List<GlucosePoint> points,
            int viewMode,
            long targetTime) {
        String valueText;
        boolean isSimple = false;
        try {
            Float.parseFloat(value);
            // If simple number, use PURE formatter
            valueText = format(java.util.Locale.getDefault(), pureglucoseformat, glvalue);
            isSimple = true;
        } catch (NumberFormatException e) {
            // Complex string? Skip parsing legacy string.
            // Format primary value and let logic below reconstruct full string from points
            // data.
            valueText = format(java.util.Locale.getDefault(), pureglucoseformat, glvalue);
            isSimple = true;
        }

        if (isSimple && points != null && !points.isEmpty()) {
            boolean isRawMode = (viewMode == 1 || viewMode == 3);
            boolean hasCalibration = tk.glucodata.data.calibration.CalibrationManager.INSTANCE
                    .hasActiveCalibration(isRawMode);

            // If calibration is active, 'glvalue' passed in IS the calibrated value.
            // We need to find the Base values (Raw & Auto) from the points.

            float rawVal = 0f;
            float autoVal = 0f;
            boolean found = false;

            // Find matching point
            for (int i = points.size() - 1; i >= 0; i--) {
                GlucosePoint p = points.get(i);
                if (targetTime > 0 && Math.abs(p.timestamp - targetTime) > 60000)
                    continue;

                // Match logic:
                // If hasCalibration: we can't easily match value to p.value/p.rawValue because
                // glvalue is calibrated.
                // Trust the TIME match.
                if (targetTime > 0 && Math.abs(p.timestamp - targetTime) < 2000) {
                    rawVal = p.rawValue;
                    autoVal = p.value;
                    found = true;
                    break;
                }

                // If no time, try value match ONLY if NO calibration (fallback)
                if (!hasCalibration) {
                    if (viewMode == 3 && Math.abs(p.rawValue - glvalue) < 0.1) {
                        rawVal = p.rawValue;
                        autoVal = p.value;
                        found = true;
                        break;
                    }
                    if (viewMode == 2 && Math.abs(p.value - glvalue) < 0.1) {
                        rawVal = p.rawValue;
                        autoVal = p.value;
                        found = true;
                        break;
                    }
                }
            }

            // Fallback: Use latest if not found
            if (!found && !points.isEmpty()) {
                GlucosePoint latest = points.get(points.size() - 1);
                rawVal = latest.rawValue;
                autoVal = latest.value;
            }

            // Build Strings
            String secondary = null;
            String tertiary = null;

            // Helper to format float
            // We can't use local func in Java, so standard code

            if (hasCalibration) {
                if (viewMode == 2 || viewMode == 3) {
                    // 3 Values: Calibrated / Sec · Ter
                    // Mode 2 (Auto+Raw): Sec=Auto, Ter=Raw
                    // Mode 3 (Raw+Auto): Sec=Raw, Ter=Auto
                    float secVal = (viewMode == 3) ? rawVal : autoVal;
                    float terVal = (viewMode == 3) ? autoVal : rawVal;

                    if (secVal > 0.1f)
                        secondary = format(java.util.Locale.getDefault(), pureglucoseformat, secVal);
                    if (terVal > 0.1f)
                        tertiary = format(java.util.Locale.getDefault(), pureglucoseformat, terVal);
                } else {
                    // 2 Values: Calibrated / Base
                    float baseVal = isRawMode ? rawVal : autoVal;
                    if (baseVal > 0.1f)
                        secondary = format(java.util.Locale.getDefault(), pureglucoseformat, baseVal);
                }
            } else {
                // No Calibration - Original Logic
                if (viewMode == 2 || viewMode == 3) {
                    float secVal = (viewMode == 3) ? autoVal : rawVal;
                    if (secVal > 0.1f)
                        secondary = format(java.util.Locale.getDefault(), pureglucoseformat, secVal);
                }
            }

            // Build Spannable
            if (secondary != null) {
                android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();
                ssb.append(valueText);

                int secStart = ssb.length(); // Include separator in span
                ssb.append(" / ");
                ssb.append(secondary);

                // Secondary Style: Gray, 0.85x
                ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFF888888), secStart, ssb.length(),
                        android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                ssb.setSpan(new android.text.style.RelativeSizeSpan(0.85f), secStart, ssb.length(),
                        android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);

                if (tertiary != null) {
                    int terStart = ssb.length(); // Include separator in span
                    ssb.append(" · ");
                    ssb.append(tertiary);

                    // Tertiary Style: Lighter Gray, 0.7x
                    ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFFAAAAAA), terStart, ssb.length(),
                            android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    ssb.setSpan(new android.text.style.RelativeSizeSpan(0.7f), terStart, ssb.length(),
                            android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                }

                return ssb;
            }
        }
        return valueText;
    }

    // Helper for relative time "1m", "5m", "now"
    private String getRelativeTimeSpanString(Context context, long time) {
        long now = System.currentTimeMillis();
        long diff = now - time;
        if (diff < 60000) {
            return "now";
        } else {
            long mins = diff / 60000;
            return mins + "m";
        }
    }

    // UPDATE METHOD
    public Notification makearrownotification(int draw, float glvalue, String message, notGlucose glucose, String type,
            boolean once) {
        // 1. Determine Arrow
        float rate = glucose.rate;

        // Delta (Current - Previous?) - omitted for now

        // Trigger History Sync to ensure Room DB has latest data for main app
        tk.glucodata.data.HistorySync.INSTANCE.syncFromNative();

        // 2. Build Chart
        // Fetch history (last 3 hours) from Room DB (same source as main chart)
        long endT = System.currentTimeMillis();
        long startT = endT - 3 * 60 * 60 * 1000L;
        boolean isMmol = Applic.unit == 1; // Check user unit preference

        // Use Room DB via HistoryRepository for chart display (consistent with main
        // app)
        java.util.List<GlucosePoint> chartPoints;
        try {
            chartPoints = tk.glucodata.data.HistoryRepository.getHistoryForNotification(startT, isMmol);
        } catch (Exception e) {
            chartPoints = new java.util.ArrayList<>();
        }

        // FRESH native points for value text lookup (has calibrateNow on-the-fly)
        // Only need recent readings for value matching
        long recentStartT = endT - 10 * 60 * 1000L; // Last 10 minutes
        java.util.List<GlucosePoint> nativePoints = new java.util.ArrayList<>();
        try {
            long[] historyRaw = Natives.getGlucoseHistory(recentStartT / 1000L); // Native expects seconds
            if (historyRaw != null) {
                for (int i = 0; i < historyRaw.length; i += 3) {
                    long t = historyRaw[i] * 1000L;
                    float val = historyRaw[i + 1] / 10.0f;
                    float valRaw = historyRaw[i + 2] / 10.0f;
                    if (isMmol) {
                        val = val / 18.0182f;
                        valRaw = valRaw / 18.0182f;
                    }
                    nativePoints.add(new GlucosePoint(t, val, valRaw));
                }
            }
        } catch (Exception e) {
            // Fall back to chart points if native fails
            nativePoints = chartPoints;
        }

        // Status Logic & ViewMode extraction
        String statusText = "";
        String activeSensorSerial = Natives.lastsensorname();
        int viewMode = 0; // Default

        if (activeSensorSerial != null && SensorBluetooth.blueone != null) {
            synchronized (SensorBluetooth.gattcallbacks) {
                for (SuperGattCallback cb : SensorBluetooth.gattcallbacks) {
                    if (cb.SerialNumber != null && cb.SerialNumber.equals(activeSensorSerial)) {
                        statusText = cb.constatstatusstr;
                        viewMode = Natives.getViewMode(cb.dataptr);
                        break;
                    }
                }
            }
        }

        // Get Consistently Formatted Text using NATIVE points (fresh data)
        // Recalculate rate using shared TrendEngine for consistency
        try {
            boolean useRaw = (viewMode == 1 || viewMode == 3);
            tk.glucodata.logic.TrendEngine.TrendResult res = tk.glucodata.logic.TrendEngine.INSTANCE
                    .calculateTrend(nativePoints, useRaw, isMmol);
            rate = res.getVelocity();
        } catch (Throwable t) {
            // keep original rate if fails
        }

        // If ViewMode == 3 (Combined), we force appending Raw if available
        boolean isRawMode = (viewMode == 1 || viewMode == 3);
        boolean hasCalibration = tk.glucodata.data.calibration.CalibrationManager.INSTANCE
                .hasActiveCalibration(isRawMode);

        CharSequence valueText = formatGlucoseText(glucose.value, glvalue, nativePoints, viewMode, glucose.time);

        // Semantic Color
        int glucoseColor = NotificationChartDrawer.getGlucoseColor(Applic.app, glvalue, isMmol);

        // ========== READ NOTIFICATION PREFERENCES ==========
        android.content.SharedPreferences prefs = Applic.app
                .getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE);

        float fontSize = prefs.getFloat("notification_font_size", 1.0f);
        int fontFamily = prefs.getInt("notification_font_family", 0); // 0=App, 1=System
        boolean useSystemFont = (fontFamily == 1);
        int fontWeight = prefs.getInt("notification_font_weight", 400);
        boolean showArrow = prefs.getBoolean("notification_show_arrow", true);
        float arrowSize = prefs.getFloat("notification_arrow_size", 1.0f);
        boolean showStatus = prefs.getBoolean("notification_show_status", true);
        boolean showChart = prefs.getBoolean("notification_chart_enabled", true);
        boolean showChartCollapsed = prefs.getBoolean("notification_chart_collapsed", false);
        boolean showTargetRange = prefs.getBoolean("notification_chart_target_range", true);

        // Render Arrow (Color + Size from Preferences) - still bitmap for colored
        // vector
        Bitmap arrowBitmap = showArrow
                ? NotificationChartDrawer.drawArrow(Applic.app, rate, isMmol, glucoseColor, arrowSize)
                : null;

        // 3a. Construct RemoteViews (Collapsed)
        RemoteViews remoteViews = new RemoteViews(Applic.app.getPackageName(), R.layout.notification_material);

        // Apply System Font Weight Mapping (Pixel-friendly)
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(valueText);
        String family = "sans-serif";
        boolean isBold = false;

        // Use standard system font logic
        if (fontWeight >= 500) {
            family = "sans-serif-medium";
        } else {
            family = "sans-serif";
        }

        // Apply Font Family
        ssb.setSpan(new android.text.style.TypefaceSpan(family), 0, ssb.length(),
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        // Apply Bold if needed
        if (isBold) {
            ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, ssb.length(),
                    android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }

        // Apply Relative Size (Scale) based on preference
        if (fontSize != 1.0f) {
            ssb.setSpan(new android.text.style.RelativeSizeSpan(fontSize), 0, ssb.length(),
                    android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }

        CharSequence finalText = ssb;

        // Fetch Status from Natives (Line under sensor name logic)
        String newStatusText = statusText;
        try {
            // Priority: Main sensor (lastsensorname) or first active
            String mainName = Natives.lastsensorname();
            long ptr = 0;
            if (mainName != null && !mainName.isEmpty()) {
                ptr = Natives.getdataptr(mainName);
            }
            if (ptr == 0) {
                long[] ptrs = Natives.activeSensorPtrs();
                if (ptrs != null && ptrs.length > 0)
                    ptr = ptrs[0];
            }

            if (ptr != 0) {
                String nativeStatus = Natives.getsensortext(ptr);
                if (nativeStatus != null && !nativeStatus.isEmpty()) {
                    newStatusText = nativeStatus;
                }
            }
        } catch (Throwable t) {
            // Fallback to passed statusText
        }

        // Apply Style to Status Text too
        CharSequence styledStatus = newStatusText;
        if (newStatusText != null && !newStatusText.isEmpty()) {
            android.text.SpannableStringBuilder ssbStatus = new android.text.SpannableStringBuilder(newStatusText);
            ssbStatus.setSpan(new android.text.style.TypefaceSpan(family), 0, ssbStatus.length(),
                    android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            if (isBold) {
                ssbStatus.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0,
                        ssbStatus.length(), android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }
            styledStatus = ssbStatus;
        }

        // Glucose Value - Render as Bitmap to support IBM Plex Font & Locale
        // consistency
        // Collapsed: Base size 24sp (scale 1.0 * fontSize)
        Bitmap valueBitmap = NotificationChartDrawer.drawGlucoseText(Applic.app, valueText.toString(), glucoseColor,
                fontSize, fontWeight, useSystemFont);
        remoteViews.setViewVisibility(R.id.notification_glucose, View.GONE);
        remoteViews.setViewVisibility(R.id.notification_glucose_image, View.VISIBLE);
        remoteViews.setImageViewBitmap(R.id.notification_glucose_image, valueBitmap);

        if (showArrow && arrowBitmap != null) {
            remoteViews.setViewVisibility(R.id.notification_arrow, View.VISIBLE);
            remoteViews.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);
        } else {
            remoteViews.setViewVisibility(R.id.notification_arrow, View.GONE);
        }

        // Status - native TextView
        if (showStatus && newStatusText != null && !newStatusText.isEmpty()) {
            remoteViews.setViewVisibility(R.id.notification_status, View.VISIBLE);
            remoteViews.setTextViewText(R.id.notification_status, styledStatus);
        } else {
            remoteViews.setViewVisibility(R.id.notification_status, View.GONE);
        }

        // 3b. Construct RemoteViews (Expanded)
        RemoteViews remoteViewsExpanded = new RemoteViews(Applic.app.getPackageName(),
                R.layout.notification_material_expanded);

        // Glucose Value - Expanded: Size 28sp (scale ~1.17 * fontSize)
        Bitmap valueBitmapExpanded = NotificationChartDrawer.drawGlucoseText(Applic.app, valueText.toString(),
                glucoseColor, fontSize * 1.166f, fontWeight, useSystemFont);
        remoteViewsExpanded.setViewVisibility(R.id.notification_glucose, View.GONE);
        remoteViewsExpanded.setViewVisibility(R.id.notification_glucose_image, View.VISIBLE);
        remoteViewsExpanded.setImageViewBitmap(R.id.notification_glucose_image, valueBitmapExpanded);

        if (showArrow && arrowBitmap != null) {
            remoteViewsExpanded.setViewVisibility(R.id.notification_arrow, View.VISIBLE);
            remoteViewsExpanded.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);
        } else {
            remoteViewsExpanded.setViewVisibility(R.id.notification_arrow, View.GONE);
        }

        // Status for Expanded - native TextView
        if (showStatus && newStatusText != null && !newStatusText.isEmpty()) {
            remoteViewsExpanded.setViewVisibility(R.id.notification_status, View.VISIBLE);
            remoteViewsExpanded.setTextViewText(R.id.notification_status, styledStatus);
        } else {
            remoteViewsExpanded.setViewVisibility(R.id.notification_status, View.GONE);
        }

        // Set Chart
        Bitmap chartBitmapCollapsed = null;
        Bitmap chartBitmapExpanded = null;

        // Create Safe Context with System Density (Fix for Low DPI/Large Text modes
        // where Applic.app is stale)
        Context safeContext = Applic.app;
        if (showChartCollapsed || showChart) {
            try {
                android.util.DisplayMetrics systemDm = android.content.res.Resources.getSystem().getDisplayMetrics();
                android.content.res.Configuration config = new android.content.res.Configuration(
                        Applic.app.getResources().getConfiguration());
                config.densityDpi = (int) (systemDm.density * 160f);
                safeContext = Applic.app.createConfigurationContext(config);
            } catch (Throwable t) {
                // fallback to app context if creation fails
            }
        }

        if (showChartCollapsed) {
            // Collapsed chart: Limit height to 48dp based on SYSTEM density
            float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
            int collapsedHeight = (int) (48 * density);
            if (collapsedHeight < 48)
                collapsedHeight = 48;

            // Use safeContext and explicit height
            chartBitmapCollapsed = NotificationChartDrawer.drawChart(safeContext, chartPoints, 0, collapsedHeight,
                    isMmol,
                    viewMode, showTargetRange, hasCalibration, true);
        }

        if (showChart) {
            // Expanded chart: Use safely resolved density context (default 0 ->
            // 256*density)
            chartBitmapExpanded = NotificationChartDrawer.drawChart(safeContext, chartPoints, 0, 0, isMmol,
                    viewMode, showTargetRange, hasCalibration, false);
        }

        remoteViews.setImageViewBitmap(R.id.notification_chart, chartBitmapCollapsed);
        if (showChartCollapsed && chartBitmapCollapsed != null) {
            remoteViews.setViewVisibility(R.id.chart_container, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.notification_chart, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.chart_container, View.GONE);
            remoteViews.setViewVisibility(R.id.notification_chart, View.GONE);
        }

        remoteViewsExpanded.setImageViewBitmap(R.id.notification_chart, chartBitmapExpanded);
        if (showChart && chartBitmapExpanded != null) {
            remoteViewsExpanded.setViewVisibility(R.id.notification_chart, View.VISIBLE);
        } else {
            remoteViewsExpanded.setViewVisibility(R.id.notification_chart, View.GONE);
        }

        // 4. Bind to Builder
        var GluNotBuilder = mkbuilder(type);
        GluNotBuilder.setOnlyAlertOnce(once);

        setIcon(GluNotBuilder, glvalue, glucose.sensorgen2);

        GluNotBuilder.setVisibility(VISIBILITY_PUBLIC);

        if (Build.VERSION.SDK_INT >= 24) {
            GluNotBuilder.setStyle(new Notification.DecoratedCustomViewStyle());
            GluNotBuilder.setCustomContentView(remoteViews);
            GluNotBuilder.setCustomBigContentView(remoteViewsExpanded);
        } else {
            GluNotBuilder.setContent(remoteViews);
        }

        GluNotBuilder.setShowWhen(true);

        // Standard priority logic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            GluNotBuilder.setTimeoutAfter(glucosetimeout);
        }
        if (isWearable) {
            GluNotBuilder.setAutoCancel(true);
        }
        if (once)
            GluNotBuilder.setPriority(Notification.PRIORITY_DEFAULT);
        else {
            GluNotBuilder.setPriority(Notification.PRIORITY_HIGH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Use CATEGORY_STATUS for regular updates, ALARM for actual alarms (kind logic
                // omitted here for simplicity or passed via 'once' equivalent?)
                // Actually 'kind' was passed but unused in my snippet?
                // Let's assume standard behavior.
                GluNotBuilder.setCategory(Notification.CATEGORY_ALARM);
            }
        }

        Notification notif = GluNotBuilder.build();
        notif.when = System.currentTimeMillis();

        return notif;
    }

    Notification getforgroundnotification() {
        // Use custom layout even for initial notification to show Graph Grid
        final String message = app
                .getString(SensorBluetooth.blueone != null ? R.string.connectwithsensor : R.string.exchangedata);

        // Fetch History for Startup Graph from Room DB (same source as main chart)
        long endT = System.currentTimeMillis();
        long startT = endT - 3 * 60 * 60 * 1000L;
        boolean isMmol = Applic.unit == 1;

        // Use Room DB via HistoryRepository for consistent data with chart
        java.util.List<GlucosePoint> chartPoints;
        try {
            // Get RAW mg/dL
            java.util.List<GlucosePoint> rawPoints = tk.glucodata.data.HistoryRepository
                    .getHistoryRawForNotification(startT);

            // Convert to Display Unit if needed (UI Layer Logic)
            chartPoints = new java.util.ArrayList<>();
            for (GlucosePoint p : rawPoints) {
                float val = p.value;
                float rawVal = p.rawValue;
                if (isMmol) {
                    val /= 18.0182f;
                    rawVal /= 18.0182f;
                }
                chartPoints.add(new GlucosePoint(p.timestamp, val, rawVal));
            }
        } catch (Exception e) {
            chartPoints = new java.util.ArrayList<>();
        }

        // FRESH native points for value text lookup (has calibrateNow on-the-fly)
        long recentStartT = endT - 15 * 60 * 1000L; // Last 15 minutes
        java.util.List<GlucosePoint> nativePoints = new java.util.ArrayList<>();
        try {
            long[] historyRaw = Natives.getGlucoseHistory(recentStartT / 1000L);
            if (historyRaw != null) {
                for (int i = 0; i < historyRaw.length; i += 3) {
                    long t = historyRaw[i] * 1000L;
                    float val = historyRaw[i + 1] / 10.0f;
                    float valRaw = historyRaw[i + 2] / 10.0f;
                    if (isMmol) {
                        val = val / 18.0182f;
                        valRaw = valRaw / 18.0182f;
                    }
                    nativePoints.add(new GlucosePoint(t, val, valRaw));
                }
            }
        } catch (Exception e) {
            nativePoints = chartPoints;
        }

        // Identify ViewMode for Startup
        int viewMode = 0;
        String activeSensorSerial = Natives.lastsensorname();
        if (activeSensorSerial != null && SensorBluetooth.blueone != null) {
            synchronized (SensorBluetooth.gattcallbacks) {
                for (SuperGattCallback cb : SensorBluetooth.gattcallbacks) {
                    if (cb.SerialNumber != null && cb.SerialNumber.equals(activeSensorSerial)) {
                        viewMode = Natives.getViewMode(cb.dataptr);
                        break;
                    }
                }
            }
        }

        // Startup Text using Helper and Natives.lastglucose()
        CharSequence startupValue = "---";
        strGlucose last = Natives.lastglucose();
        if (last != null && last.value != null) {
            long now = System.currentTimeMillis();
            // Stale Data Check: If older than 15 minutes (900,000 ms), hide it
            if (Math.abs(now - last.time) < 15 * 60 * 1000L) {
                float val = 0f;
                try {
                    val = Float.parseFloat(last.value);
                } catch (NumberFormatException e) {
                    // ignore
                }
                // Use unified formatter with TIME check using NATIVE points
                startupValue = formatGlucoseText(last.value, val, nativePoints, viewMode, last.time);
            }
        } else if (!chartPoints.isEmpty()) {
            // Fallback if Natives.lastglucose() is not ready but history is
            // Manual fall back logic if formatGlucoseText can't be used (no string value)
            GlucosePoint latest = chartPoints.get(chartPoints.size() - 1);
            // Also check staleness of history
            long now = System.currentTimeMillis();
            if (Math.abs(now - latest.timestamp) < 15 * 60 * 1000L) {
                String vStr = format(usedlocale, pureglucoseformat, latest.value);

                if (viewMode == 3 && latest.rawValue > 0.1f) {
                    String rStr = format(usedlocale, pureglucoseformat, latest.rawValue);
                    // startupValue = rStr + " · " + vStr;
                    startupValue = rStr + "/ " + vStr;
                } else {
                    startupValue = vStr;
                }
            }
        }

        // Check if chart is enabled
        // Check if chart is enabled
        android.content.SharedPreferences prefs = Applic.app
                .getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE);
        boolean showChart = prefs.getBoolean("notification_chart_enabled", true);
        float fontSize = prefs.getFloat("notification_font_size", 1.0f);
        int fontWeight = prefs.getInt("notification_font_weight", 400);
        int fontFamily = prefs.getInt("notification_font_family", 0); // 0=App, 1=System

        Bitmap chartBitmapCollapsed = null;
        Bitmap chartBitmapExpanded = null;

        if (showChart) {
            // Create Safe Context for Startup Notification too
            Context safeContext = Applic.app;
            try {
                android.util.DisplayMetrics systemDm = android.content.res.Resources.getSystem().getDisplayMetrics();
                android.content.res.Configuration config = new android.content.res.Configuration(
                        Applic.app.getResources().getConfiguration());
                config.densityDpi = (int) (systemDm.density * 160f);
                safeContext = Applic.app.createConfigurationContext(config);
            } catch (Throwable t) {
            }

            float density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
            int collapsedHeight = (int) (48 * density);
            if (collapsedHeight < 48)
                collapsedHeight = 48;

            // Collapsed: Compact Mode = TRUE, Height 48dp
            chartBitmapCollapsed = NotificationChartDrawer.drawChart(safeContext, chartPoints, 0, collapsedHeight,
                    isMmol,
                    viewMode, true, false, true);

            // Expanded: Compact Mode = FALSE, Height 256dp (via 0)
            chartBitmapExpanded = NotificationChartDrawer.drawChart(safeContext, chartPoints, 0, 0, isMmol,
                    viewMode, true, false, false);
        }

        Bitmap arrowBitmap;

        // Semantic Color Logic for Startup
        float colorVal = 0f;
        if (last != null && last.value != null) {
            try {
                colorVal = Float.parseFloat(last.value);
            } catch (Exception e) {
            }
        } else if (!chartPoints.isEmpty()) {
            colorVal = chartPoints.get(chartPoints.size() - 1).value;
        }
        int glucoseColor = NotificationChartDrawer.getGlucoseColor(Applic.app, colorVal, isMmol);

        arrowBitmap = NotificationChartDrawer.drawArrow(Applic.app, (last != null) ? last.rate : 0, isMmol,
                glucoseColor);

        // Use native TextView for startup value
        RemoteViews remoteViews = new RemoteViews(Applic.app.getPackageName(), R.layout.notification_material);

        // Apply Font Logic
        // Hoisted variables
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(startupValue);
        CharSequence finalText = ssb;
        String family = "sans-serif";
        boolean isBold = false;

        if (fontFamily == 0) { // App Font (IBM Plex) - Render as Bitmap
            remoteViews.setViewVisibility(R.id.notification_glucose, View.GONE);
            remoteViews.setViewVisibility(R.id.notification_glucose_image, View.VISIBLE);

            try {
                // Measure text using TextPaint to support Spans
                android.text.TextPaint textPaint = new android.text.TextPaint();
                textPaint.setAntiAlias(true);
                textPaint.setColor(glucoseColor);
                textPaint.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                        24 * fontSize, Applic.app.getResources().getDisplayMetrics()));

                android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(Applic.app,
                        R.font.ibm_plex_sans_var);
                textPaint.setTypeface(tf);
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    textPaint.setFontVariationSettings("'wght' " + fontWeight + ", 'wdth' 100");
                }

                int desiredWidth = (int) Math.ceil(android.text.Layout.getDesiredWidth(startupValue, textPaint));
                // Add padding
                int width = desiredWidth + 4;
                if (width <= 0)
                    width = 100;

                // Create StaticLayout
                // Use deprecated constructor for broad compatibility (or logic check)
                // For simplicity in this file, standard deprecated constructor works well on
                // Android.
                android.text.StaticLayout layout = new android.text.StaticLayout(startupValue, textPaint, width,
                        android.text.Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                int height = layout.getHeight() + 4;
                if (height <= 0)
                    height = 50;

                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(width, height,
                        android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);

                // Draw layout
                canvas.translate(2, 2); // padding
                layout.draw(canvas);

                remoteViews.setImageViewBitmap(R.id.notification_glucose_image, bmp);

            } catch (Exception e) {
                // Fallback to text view if bitmap creation fails
                remoteViews.setViewVisibility(R.id.notification_glucose, View.VISIBLE);
                remoteViews.setViewVisibility(R.id.notification_glucose_image, View.GONE);
                remoteViews.setTextViewText(R.id.notification_glucose, startupValue);
            }

        } else { // System Font (Google Sans / Roboto) - Use TextView
            remoteViews.setViewVisibility(R.id.notification_glucose, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.notification_glucose_image, View.GONE);

            remoteViews.setViewVisibility(R.id.notification_glucose_image, View.GONE);

            // ssb already initialized above

            if (fontWeight >= 500) {
                family = "google-sans-medium";
            } else {
                family = "google-sans";
            }
            // If system defaults to sans-serif because google-sans missing, it handles it.

            ssb.setSpan(new android.text.style.TypefaceSpan(family), 0, ssb.length(),
                    android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);

            if (fontSize != 1.0f) {
                ssb.setSpan(new android.text.style.RelativeSizeSpan(fontSize), 0, ssb.length(),
                        android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            }

            remoteViews.setTextViewText(R.id.notification_glucose, ssb);
            remoteViews.setTextColor(R.id.notification_glucose, glucoseColor);
            remoteViews.setTextViewTextSize(R.id.notification_glucose, android.util.TypedValue.COMPLEX_UNIT_SP,
                    24 * fontSize);

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                remoteViews.setString(R.id.notification_glucose, "setFontVariationSettings", "'wght' " + fontWeight);
            }

            finalText = ssb;
        }

        remoteViews.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);

        if (showChart && chartBitmapCollapsed != null) {
            remoteViews.setViewVisibility(R.id.chart_container, View.VISIBLE);
            remoteViews.setImageViewBitmap(R.id.notification_chart, chartBitmapCollapsed);
            remoteViews.setViewVisibility(R.id.notification_chart, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.chart_container, View.GONE);
            remoteViews.setViewVisibility(R.id.notification_chart, View.GONE);
        }

        // Apply Style to Status Text (message)
        CharSequence styledMessage = message;
        if (message != null && !message.isEmpty()) {
            android.text.SpannableStringBuilder ssbMsg = new android.text.SpannableStringBuilder(message);
            ssbMsg.setSpan(new android.text.style.TypefaceSpan(family), 0, ssbMsg.length(),
                    android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            if (isBold) {
                ssbMsg.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0,
                        ssbMsg.length(), android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            }
            styledMessage = ssbMsg;
        }

        remoteViews.setViewVisibility(R.id.notification_status, View.VISIBLE);
        remoteViews.setTextViewText(R.id.notification_status, styledMessage);

        RemoteViews remoteViewsExpanded = new RemoteViews(Applic.app.getPackageName(),
                R.layout.notification_material_expanded);
        remoteViewsExpanded.setTextViewText(R.id.notification_glucose, finalText);
        remoteViewsExpanded.setTextColor(R.id.notification_glucose, glucoseColor);
        // Apply size and weight to expanded startup notification
        remoteViewsExpanded.setTextViewTextSize(R.id.notification_glucose, android.util.TypedValue.COMPLEX_UNIT_SP,
                28 * fontSize);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            remoteViewsExpanded.setString(R.id.notification_glucose, "setFontVariationSettings",
                    "'wght' " + fontWeight);
        }

        remoteViewsExpanded.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);

        if (showChart && chartBitmapExpanded != null) {
            remoteViewsExpanded.setImageViewBitmap(R.id.notification_chart, chartBitmapExpanded);
            remoteViewsExpanded.setViewVisibility(R.id.notification_chart, View.VISIBLE);
        } else {
            remoteViewsExpanded.setViewVisibility(R.id.notification_chart, View.GONE);
        }
        remoteViewsExpanded.setViewVisibility(R.id.notification_status, View.VISIBLE);
        remoteViewsExpanded.setTextViewText(R.id.notification_status, styledMessage);

        var GluNotBuilder = mkbuilder(GLUCOSENOTIFICATION);
        if (Build.VERSION.SDK_INT >= 24) {
            GluNotBuilder.setStyle(new Notification.DecoratedCustomViewStyle());
            GluNotBuilder.setCustomContentView(remoteViews);
            GluNotBuilder.setCustomBigContentView(remoteViewsExpanded);
        } else {
            GluNotBuilder.setContent(remoteViews);
        }
        GluNotBuilder.setSmallIcon(R.drawable.novalue).setOnlyAlertOnce(true).setContentTitle(message)
                .setShowWhen(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GluNotBuilder.setVisibility(VISIBILITY_PUBLIC);
            GluNotBuilder.setCategory(Notification.CATEGORY_SERVICE);
        }
        GluNotBuilder.setOngoing(true);
        Notification not = GluNotBuilder.build();
        not.flags |= FLAG_ONGOING_EVENT;
        return not;
    }

    private Notification.Builder mkbuilder(String type) {
        var build = mkbuilderintent(type, mkpending());
        build.setDeleteIntent(DeleteReceiver.getDeleteIntent());
        return build;
    }

    // static final private boolean alertseperate=true;

    void fornotify(Notification notif) {
        {
            if (doLog) {
                Log.i(LOG_ID, "fornotify ");
            }
            // Notify AOD/Widgets of update immediately
            Applic.app.sendBroadcast(new Intent("tk.glucodata.action.GLUCOSE_UPDATE"));

            ;
        }
        ;
        if (isWearable) {
            notificationManager.notify(glucosealarmid, notif);
        } else {
            {
                notificationManager.cancel(glucosenotificationid);
                if (keeprunning.theservice != null) {
                    keeprunning.theservice.startForeground(glucosenotificationid, notif);
                } else
                    notificationManager.notify(glucosenotificationid, notif);
            }
        }
    }
    // static final long glucosetimeout=1000*60*3;

    /*
     * @SuppressWarnings("deprecation")
     * void oldnotification(long time) {
     * String message= Applic.app.getString(R.string.nonewvalue)+
     * timef.format(time);
     * {if(doLog) {Log.i(LOG_ID,"oldnotification "+message);};};
     * var GluNotBuilder=mkbuilder(GLUCOSENOTIFICATION);
     * if (Build.VERSION.SDK_INT < 31) {
     * GluNotBuilder.setStyle(new Notification.DecoratedCustomViewStyle());
     * }
     * GluNotBuilder.setContentTitle(message).setSmallIcon(R.drawable.novalue).
     * setPriority(Notification.PRIORITY_DEFAULT);
     * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
     * GluNotBuilder.setVisibility(VISIBILITY_PUBLIC);
     * }
     * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
     * GluNotBuilder.setTimeoutAfter(glucosetimeout);
     * }
     * // RemoteViews remoteViews= new
     * RemoteViews(Applic.app.getPackageName(),R.layout.smallnotification);
     * // GluNotBuilder.setContent(remoteViews);
     * Notification notif= GluNotBuilder.build();
     * fornotify(notif);
     * {if(doLog) {Log.i(LOG_ID,"end oldnotification");};};
     * }
     */
    void oldnotification(long time) {
        final String tformat = timef.format(time);
        String message = Applic.getContext().getString(R.string.nonewvalue) + tformat;
        placelargenotification(R.drawable.novalue, message, GLUCOSENOTIFICATION, true);
    }

    @SuppressWarnings("deprecation")
    private Notification makenotification(int draw, String message, String type, boolean once) {
        var GluNotBuilder = mkbuilder(type);

        if (TargetSDK < 31 || Build.VERSION.SDK_INT < 31) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                GluNotBuilder.setStyle(new Notification.DecoratedCustomViewStyle());
            }
        }
        {
            if (doLog) {
                Log.i(LOG_ID, "makenotification " + message);
            }
            ;
        }
        ;

        GluNotBuilder.setSmallIcon(draw).setOnlyAlertOnce(once).setContentTitle(message).setShowWhen(true);

        if (!isWearable) {
            RemoteViews remoteViews = new RemoteViews(app.getPackageName(), R.layout.text);
            remoteViews.setTextColor(R.id.content, foregroundcolor);
            remoteViews.setTextViewText(R.id.content, message);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                GluNotBuilder.setCustomContentView(remoteViews);
            } else
                GluNotBuilder.setContent(remoteViews);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GluNotBuilder.setVisibility(VISIBILITY_PUBLIC);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            GluNotBuilder.setTimeoutAfter(glucosetimeout);
        }
        if (isWearable) {
            GluNotBuilder.setAutoCancel(true);
        }
        if (once)
            GluNotBuilder.setPriority(Notification.PRIORITY_DEFAULT);
        else {
            // GluNotBuilder.setPriority(Notification.PRIORITY_HIGH);
            GluNotBuilder.setPriority(Notification.PRIORITY_MAX);
            // GluNotBuilder.setDefaults(Notification.DEFAULT_VIBRATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                GluNotBuilder.setCategory(Notification.CATEGORY_ALARM);
            }
        }

        {
            if (doLog) {
                Log.i(LOG_ID, (once ? "" : "not ") + "only once");
            }
            ;
        }
        ;

        Notification notif = GluNotBuilder.build();
        notif.when = System.currentTimeMillis();
        return notif;

    }

    static public void shownovalue() {
        init(Applic.app);
        onenot.novalue();
    }

    private void novalue() {
        {
            if (doLog) {
                Log.i(LOG_ID, "novalue");
            }
            ;
        }
        ;

        fornotify(getforgroundnotification());
        // notificationManager.notify(glucosenotificationid,getforgroundnotification());
    }

    public void foregroundno(Service service) {
        Notification not = getforgroundnotification();
        if (Build.VERSION.SDK_INT >= 29) {
            service.startForeground(glucosenotificationid, not,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            service.startForeground(glucosenotificationid, not);
        }
        {
            if (doLog) {
                Log.i(LOG_ID, "startforeground");
            }
            ;
        }
        ;
    }

    static public void foregroundnot(Service service) {
        // Application app=service.getApplication();
        init(service);
        onenot.foregroundno(service);
    }

    public void placelargenotification(int draw, String message, String type, boolean once) {
        hasvalue = true;
        fornotify(makenotification(draw, message, type, once));

    }

    static int testtimes = 1;
    /*
     * static void testnot() {
     * float gl=11.4f;
     * var timmsec= System.currentTimeMillis()-1000;
     * float rate=(float)(1.6*Math.pow(-1,testtimes));
     * --testtimes;
     * boolean waiting=false;
     * var sglucose=new notGlucose(timmsec,
     * format(Applic.usedlocale,Notify.pureglucoseformat, gl) , rate);
     * // Notify.onenot.normalglucose(sglucose,gl, rate,waiting);
     * // var dr=GlucoseDraw.getgludraw(gl);
     * Notify.onenot.makearrownotification(2,gl,"message",sglucose,
     * GLUCOSENOTIFICATION ,false);
     * }
     * 
     * static void test2() {
     * float gl=7.8f;
     * float rate=0.0f;
     * SuperGattCallback.dowithglucose("Serialnumber", (int)(gl*18f), gl,rate,
     * 0,System.currentTimeMillis()) ;
     * }
     */

    public void arrowplacelargenotification(int kind, float glvalue, String message, notGlucose glucose, String type,
            boolean once) {
        hasvalue = true;
        fornotify(makearrownotification(kind, glvalue, message, glucose, type, once));

    }

    public void lossofsensornotification(int draw, String message, String type, boolean once) {
        {
            if (doLog) {
                Log.i(LOG_ID, "notify " + message);
            }
            ;
        }
        ;
        fornotify(makenotification(draw, message, type, once));
    }

    public void arrowglucosenotification(int kind, float glvalue, String message, notGlucose glucose, String type,
            boolean once) {
        {
            if (doLog) {
                Log.i(LOG_ID, "notify " + message);
            }
            ;
        }
        ;
        fornotify(makearrownotification(kind, glvalue, message, glucose, type, once));
    }

    final private int numalarmid = 81432;

    static DateFormat timef = DateFormat.getTimeInstance(DateFormat.SHORT);

    public static void mkDateformat() {
        timef = DateFormat.getTimeInstance(DateFormat.SHORT);
    };

    Notification.Builder NumNotBuilder = null;

    @SuppressWarnings("deprecation")
    public void notifyer(int draw, String message, String type, int notid) {
        if (doLog)
            Log.d(LOG_ID, "notifyer called: type=" + type + " id=" + notid);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NumNotBuilder = new Notification.Builder(Applic.app, type);
        } else
            NumNotBuilder = new Notification.Builder(Applic.app);

        // notificationManager.cancel(glucosenotificationid);

        NumNotBuilder.setAutoCancel(true).setContentIntent(mkpending())
                .setDeleteIntent(DeleteReceiver.getDeleteIntent()).setContentTitle(message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NumNotBuilder.setVisibility(VISIBILITY_PUBLIC);
            NumNotBuilder.setCategory(Notification.CATEGORY_ALARM);
        }
        var timemess = timef.format(System.currentTimeMillis()) + ": " + message;

        if (!isWearable) {
            RemoteViews NumRemoteViewss = new RemoteViews(Applic.app.getPackageName(), R.layout.numalarm);
            NumRemoteViewss.setInt(R.id.text, "setBackgroundColor", WHITE);
            NumRemoteViewss.setTextColor(R.id.text, BLACK);
            NumRemoteViewss.setTextViewText(R.id.text, timemess);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NumNotBuilder.setCustomContentView(NumRemoteViewss);
            } else
                NumNotBuilder.setContent(NumRemoteViewss);
        }

        NumNotBuilder.setSmallIcon(draw).setPriority(Notification.PRIORITY_MAX);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NumNotBuilder.setTimeoutAfter(1000L * 60 * 60 * 2);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            NumNotBuilder.setGroup("aa1");
        }
        try {
            notificationManager.notify(notid, NumNotBuilder.build());
            if (doLog)
                Log.d(LOG_ID, "notifyer: notification posted successfully to " + type);
        } catch (Exception e) {
            Log.e(LOG_ID, "notifyer: failed to post notification: " + e.toString());
        }
    }

    public void amountalarm(String message) {
        try {
            mksound(3);
            notifyer(R.drawable.numalarm, message, NUMALARM, numalarmid);
        } catch (Throwable e) {
            Log.stack(LOG_ID, e);
        }
    }

    // final private int lossalarmid=77332;
    public void lossalarm(long time) {
        {
            if (doLog) {
                Log.i(LOG_ID, "lossalarm");
            }
            ;
        }
        ;
        final String tformat = timef.format(time);
        final String message = "***  " + Applic.getContext().getString(R.string.nonewvalue) + tformat + " ***";

        // oldfloatmessage(tformat, true) ;
        lossofsignalalarm(4, R.drawable.loss, message, CHANNEL_LOSS, true);
    }

}
