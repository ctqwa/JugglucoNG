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
// ... imports ...

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

    static String glucoseformat = null;
    static String pureglucoseformat = null;
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
        try {
            if (Build.VERSION.SDK_INT >= 23)
                ring.setLooping(true);
        } catch (Throwable e) {
            Log.stack(LOG_ID, "setring", e);
        }
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
            R.raw.verylow, R.raw.veryhigh, R.raw.lowsoon, R.raw.highsoon };

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
        {
            if (doLog) {
                Log.i(LOG_ID, "ringtone " + kind + " " + uristr);
            }
            ;
        }
        ;
        var ring = setring(uristr, defaults[kind]);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            try {
                ring.setAudioAttributes((kind != 2 && getUSEALARM()) ? ScanNfcV.audioattributes : notification_audio);
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
        alertseparate = Natives.getSeparate();
        mkunitstr(cont, Natives.getunit());
        notificationManager = (NotificationManager) Applic.app.getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel(Applic.app);
        mkpaint();
    }

    private static final String NUMALARM = "MedicationReminder";
    private static final String GLUCOSEALARM = "glucoseAlarm";
    // private static final String LOSSALARM = "LossofSensorAlarm";
    private static final String GLUCOSENOTIFICATION = "glucoseNotification";

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String description = context.getString(R.string.numalarm_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(NUMALARM, NUMALARM, importance);
            channel.setSound(null, null);
            channel.setDescription(description);
            // allowbubbel(channel);
            notificationManager.createNotificationChannel(channel);

            description = context.getString(R.string.alarm_description);
            importance = NotificationManager.IMPORTANCE_HIGH;
            channel = new NotificationChannel(GLUCOSEALARM, GLUCOSEALARM, importance);
            channel.setSound(null, null);
            channel.setDescription(description);
            // allowbubbel(channel);
            notificationManager.createNotificationChannel(channel);

            description = context.getString(R.string.notification_description);
            importance = NotificationManager.IMPORTANCE_HIGH;
            channel = new NotificationChannel(GLUCOSENOTIFICATION, GLUCOSENOTIFICATION, importance);
            // allowbubbel(channel);
            channel.setSound(null, null);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }

    }

    // channel.setShowBadge(false);
    void lowglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        arrowglucosealarm(0, gl,
                format(usedlocale, glucoseformat, gl)
                        + Applic.getContext().getString(isWearable ? R.string.lowglucoseshort : R.string.lowglucose),
                strgl, GLUCOSEALARM, alarm);
        if (!isWearable) {
            if (alarm) {
                tk.glucodata.WearInt.alarm("LOW " + strgl.value);
            }
        }
    }

    void highglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        arrowglucosealarm(1, gl,
                format(usedlocale, glucoseformat, gl)
                        + Applic.getContext().getString(isWearable ? R.string.highglucoseshort : R.string.highglucose),
                strgl, GLUCOSEALARM, alarm);
        if (!isWearable) {
            if (alarm) {
                tk.glucodata.WearInt.alarm("HIGH " + strgl.value);
            }
        }
    }

    void veryhighglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        arrowglucosealarm(6, gl,
                format(usedlocale, glucoseformat, gl) + Applic.getContext()
                        .getString(isWearable ? R.string.veryhighglucoseshort : R.string.veryhighglucose),
                strgl, GLUCOSEALARM, alarm);
        if (!isWearable) {
            if (alarm) {
                tk.glucodata.WearInt.alarm("HIGH " + strgl.value);
            }
        }
    }

    void verylowglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        arrowglucosealarm(5, gl,
                format(usedlocale, glucoseformat, gl) + Applic.getContext()
                        .getString(isWearable ? R.string.verylowglucoseshort : R.string.verylowglucose),
                strgl, GLUCOSEALARM, alarm);
        if (!isWearable) {
            if (alarm) {
                tk.glucodata.WearInt.alarm("LOW " + strgl.value);
            }
        }
    }

    void prehighglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        arrowglucosealarm(8, gl,
                format(usedlocale, glucoseformat, gl) + Applic.getContext().getString(R.string.prehighglucose), strgl,
                GLUCOSEALARM, alarm);
    }

    void prelowglucose(notGlucose strgl, float gl, float rate, boolean alarm) {
        arrowglucosealarm(7, gl,
                format(usedlocale, glucoseformat, gl) + Applic.getContext().getString(R.string.prelowglucose), strgl,
                GLUCOSEALARM, alarm);
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
                    makeseparatenotification(gl, message, strgl, GLUCOSENOTIFICATION);
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

    private void vibratealarm(int kind) {
        var context = Applic.app;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            vibrator = ((VibratorManager) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)))
                    .getDefaultVibrator();
        } else
            vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        if (android.os.Build.VERSION.SDK_INT < 26) {
            if (kind != 0)
                vibrator.vibrate(new long[] { 0, 100, 10, 50, 50 }, 1);
            else
                vibrator.vibrate(new long[] { 0, 1000, 500, 100, 500, 500, 500, 100, 100 }, 1);
        } else {
            if (kind != 0) {
                final long[] vibrationPatternstart = { 0, 70, 50, 50, 50, 50, 50, 200, 30 };
                final int[] amplitude = { 0, 255, 150, 0, 255, 50, 0, 255, 50 };
                vibrates(vibrator, vibrationPatternstart, amplitude);
            } else {
                final long[] vibrationPatternstart = { 0, 1000, 500, 100, 500, 500, 500, 100, 100 };
                final int[] amplitude = { 0, 0xff, 128, 255, 0, 255, 0, 255, 50 };
                vibrates(vibrator, vibrationPatternstart, amplitude);
            }

        }
        {
            if (doLog) {
                Log.i(LOG_ID, "vibratealarm " + kind);
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
            if (disturb) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int filt = notificationManager.getCurrentInterruptionFilter();
                    {
                        if (doLog) {
                            Log.i(LOG_ID, "getCurrentInterruptionFilter()=" + filt);
                        }
                        ;
                    }
                    ;

                    if (filt != NotificationManager.INTERRUPTION_FILTER_ALL) {
                        if (notificationManager.isNotificationPolicyAccessGranted()) {
                            // curfilter[0]=filt;
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                        }
                    }
                }
            }
            if (doLog) {
                {
                    if (doLog) {
                        Log.d(LOG_ID, "play " + ring.getTitle(app));
                    }
                    ;
                }
                ;
            }
            if (doplaysound[0]) {
                ring.play();
            }
        }
        if (!isWearable) {
            if (flash)
                Flash.start(app);
        }
        if (vibrate) {
            vibratealarm(kind);
        }
        runstopalarm = () -> {
            notifyfocus = false;
            lastalarm = -1;
            if (getisalarm()) {
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
        stopschedule = Applic.scheduler.schedule(runstopalarm, duration, TimeUnit.SECONDS);

    }

    void mksound(int kind) {
        final Ringtone ring = // rings[kind];
                mkring(Natives.readring(kind), kind);
        final int duration = Natives.readalarmduration(kind);
        final boolean flash = Natives.alarmhasflash(kind);
        final boolean sound = Natives.alarmhassound(kind);
        final boolean vibration = Natives.alarmhasvibration(kind);
        final boolean dist = isWearable || getalarmdisturb(kind);

        playringhier(ring, duration, sound, flash, vibration, dist, kind);
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

    private static void showpopupalarm(String message, Boolean cancel) {
        var act = MainActivity.thisone;
        if (act != null && act.active) {
            if (cancel)
                MainActivity.showmessage = null;
            {
                if (doLog) {
                    Log.i(LOG_ID, "showpopupalarm direct " + message);
                }
                ;
            }
            ;
            act.runOnUiThread(() -> {
                if (act.isFinishing() || act.isDestroyed() || !act.active) {
                    setmessage(message, cancel);
                    return;
                }
                act.showindialog(message, cancel);
            });
        } else {
            setmessage(message, cancel);
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
            boolean alarm) {
        if (alarm) {
            // wasdraw=draw;
            wasvalue = glvalue;
            wasmessage = message;
            wastype = type;
            makeseparatenotification(glvalue, message, sglucose, type);
            {
                if (doLog) {
                    Log.d(LOG_ID, "arrowsoundalarm " + kind);
                }
                ;
            }
            ;
            mksound(kind);
        }
        arrowplacelargenotification(kind, glvalue, message, sglucose, type, !alarm);
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
            if (kind != 2)
                showpopupalarm(message, true);
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
        if (alarm) {
            if (kind != 2)
                showpopupalarm(message, true);
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
            arrowsoundalarm(kind, glvalue, message, strglucose, type, alarm);
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

    private final boolean makeicon = !isWearable && tk.glucodata.BuildConfig.minSDK >= 23;
    private final StatusIcon icons = makeicon ? new StatusIcon() : null;

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
            var glstr = format(Applic.usedlocale, Notify.pureglucoseformat, glvalue);
            if (glstr.charAt(glstr.length() - 1) == '0')
                glstr = glstr.substring(0, glstr.length() - 2);
            return glstr;
        } else {
            int intval = (int) glvalue;
            if (intval < 40)
                return "40>";
            if (intval > maxglucose)
                return "500<";
            return format(Applic.usedlocale, Notify.pureglucoseformat, glvalue);
        }
    }

    private void setIcon(Notification.Builder GluNotBuilder, float glvalue, int sensorgen2) {
        if (makeicon) {
            final var icon = icons.getIcon(getglstring(glvalue, sensorgen2));
            GluNotBuilder.setSmallIcon(icon);
        } else {
            var draw = GlucoseDraw.getgludraw(glvalue, sensorgen2);
            GluNotBuilder.setSmallIcon(draw);
        }
    }

    private void makeseparatenotification(float glvalue, String message, notGlucose glucose, String type) {
        if (!isWearable) {
            if (alertseparate) {
                notificationManager.cancel(glucosealarmid);
                var intent = mkpending();
                var GluNotBuilder = mkbuilderintent(type, intent);
                GluNotBuilder.setDeleteIntent(DeleteReceiver.getDeleteIntent());
                {
                    if (doLog) {
                        Log.i(LOG_ID, "makeseparatenotification " + glucose.value);
                    }
                    ;
                }
                ;

                setIcon(GluNotBuilder, glvalue, glucose.sensorgen2);
                GluNotBuilder.setShowWhen(true).setContentTitle(message);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // final int timeout= Build.VERSION.SDK_INT >= 30? 60*1500:60*3000;
                    final int timeout = 800 * 60;// Build.VERSION.SDK_INT >= 30? 60*1500:60*3000;
                    GluNotBuilder.setTimeoutAfter(timeout);
                }
                GluNotBuilder.setAutoCancel(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    GluNotBuilder.setVisibility(VISIBILITY_PUBLIC);
                }
                GluNotBuilder.setPriority(Notification.PRIORITY_HIGH);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    GluNotBuilder.setCategory(Notification.CATEGORY_ALARM);
                }
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
    private CharSequence formatGlucoseText(String value, float glvalue, java.util.List<GlucosePoint> points,
            int viewMode,
            long targetTime) {
        String valueText;
        boolean isSimple = false;
        try {
            Float.parseFloat(value);
            // If simple number, use PURE formatter
            valueText = format(usedlocale, pureglucoseformat, glvalue);
            isSimple = true;
        } catch (NumberFormatException e) {
            // Complex string (Raw+Auto?), replace " (" with " · " and remove ")"
            valueText = value.replace(" (", " · ").replace(")", "");
        }

        // Force comma separator as requested
        valueText = valueText.replace(".", ",");

        if (isSimple && points != null && !points.isEmpty()) {
            boolean found = false;
            String secondary = null;

            // Mode 3: Raw+Auto (User receives Raw, wants Raw · Auto)
            if (viewMode == 3) {
                // Input 'glvalue' is likely RAW. Find companion Auto.
                for (int i = points.size() - 1; i >= 0; i--) {
                    GlucosePoint p = points.get(i);

                    // Strict Time Match (approx 1 min tolerance to be safe, but usually exact)
                    if (targetTime > 0 && Math.abs(p.timestamp - targetTime) > 60000)
                        continue;

                    // Check if this point's Raw matches our input
                    if (Math.abs(p.rawValue - glvalue) < 0.1) {
                        String autoStr = format(usedlocale, pureglucoseformat, p.value);
                        secondary = autoStr.replace(".", ",");
                        found = true;
                        break;
                    }
                }
                // Try fallback using JUST time if value match failed (60s tolerance)
                if (!found && targetTime > 0) {
                    for (int i = points.size() - 1; i >= 0; i--) {
                        GlucosePoint p = points.get(i);
                        if (Math.abs(p.timestamp - targetTime) < 60000) {
                            String autoStr = format(usedlocale, pureglucoseformat, p.value);
                            secondary = autoStr.replace(".", ",");
                            found = true;
                            break;
                        }
                    }
                }
                // Ultimate fallback: use latest point's value
                if (!found && !points.isEmpty()) {
                    GlucosePoint latest = points.get(points.size() - 1);
                    if (latest.value > 0.1f) {
                        String autoStr = format(usedlocale, pureglucoseformat, latest.value);
                        secondary = autoStr.replace(".", ",");
                        found = true;
                    }
                }
            }
            // Mode 2: Auto+Raw (User receives Auto, wants Auto · Raw)
            else if (viewMode == 2) {
                // Input 'glvalue' is likely AUTO. Find companion Raw.
                for (int i = points.size() - 1; i >= 0; i--) {
                    GlucosePoint p = points.get(i);

                    // Strict Time Match
                    if (targetTime > 0 && Math.abs(p.timestamp - targetTime) > 60000)
                        continue;

                    if (Math.abs(p.value - glvalue) < 0.1) {
                        if (p.rawValue > 0.1f) {
                            String rawStr = format(usedlocale, pureglucoseformat, p.rawValue);
                            secondary = rawStr.replace(".", ",");
                            found = true;
                            break;
                        }
                    }
                }
                // Fallback time match (60s tolerance to handle Room DB vs Native timing
                // differences)
                if (!found && targetTime > 0) {
                    for (int i = points.size() - 1; i >= 0; i--) {
                        GlucosePoint p = points.get(i);
                        if (Math.abs(p.timestamp - targetTime) < 60000) {
                            if (p.rawValue > 0.1f) {
                                String rawStr = format(usedlocale, pureglucoseformat, p.rawValue);
                                secondary = rawStr.replace(".", ",");
                                found = true;
                                break;
                            }
                        }
                    }
                }
                // Ultimate fallback: use latest point if it has rawValue
                if (!found && !points.isEmpty()) {
                    GlucosePoint latest = points.get(points.size() - 1);
                    if (latest.rawValue > 0.1f) {
                        String rawStr = format(usedlocale, pureglucoseformat, latest.rawValue);
                        secondary = rawStr.replace(".", ",");
                        found = true;
                    }
                }
            }

            if (secondary != null) {
                // Apply HTML styling: Primary + Grey Secondary
                // Use a lighter grey color code that works on dark backgrounds (#B0B0B0 or
                // similar)
                // User requested "grey or preferably in lighter font"
                String html = valueText + " <font color='#AAAAAA'>· " + secondary + "</font>";
                return android.text.Html.fromHtml(html);
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

        // Draw Arrow Bitmap
        Bitmap arrowBitmap = NotificationChartDrawer.drawArrow(Applic.app, rate, isMmol);

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
        // If ViewMode == 3 (Combined), we force appending Raw if available
        CharSequence valueText = formatGlucoseText(glucose.value, glvalue, nativePoints, viewMode, glucose.time);

        // 3a. Construct RemoteViews (Collapsed)
        RemoteViews remoteViews = new RemoteViews(Applic.app.getPackageName(), R.layout.notification_material);
        remoteViews.setTextViewText(R.id.notification_glucose, valueText);
        remoteViews.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);

        if (statusText == null || statusText.isEmpty()) {
            remoteViews.setViewVisibility(R.id.notification_status, View.GONE);
        } else {
            remoteViews.setViewVisibility(R.id.notification_status, View.VISIBLE);
            remoteViews.setTextViewText(R.id.notification_status, statusText);
        }

        // 3b. Construct RemoteViews (Expanded)
        RemoteViews remoteViewsExpanded = new RemoteViews(Applic.app.getPackageName(),
                R.layout.notification_material_expanded);
        remoteViewsExpanded.setTextViewText(R.id.notification_glucose, valueText);
        remoteViewsExpanded.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);
        // Time REMOVED from Expanded View (Relies on System Header)

        // Status for Expanded
        if (statusText == null || statusText.isEmpty()) {
            remoteViewsExpanded.setViewVisibility(R.id.notification_status, View.GONE);
        } else {
            remoteViewsExpanded.setViewVisibility(R.id.notification_status, View.VISIBLE);
            remoteViewsExpanded.setTextViewText(R.id.notification_status, statusText);
        }

        // Set Chart
        // Collapsed (approx 400x128dp -> ~400x150px)
        // Check if chart is enabled
        android.content.SharedPreferences prefs = Applic.app
                .getSharedPreferences(Applic.app.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        boolean showChart = prefs.getBoolean("notification_chart_enabled", true);

        Bitmap chartBitmapCollapsed = null;
        Bitmap chartBitmapExpanded = null;

        if (showChart) {
            // Set Chart
            // Collapsed (approx 400x128dp -> ~400x150px)
            chartBitmapCollapsed = NotificationChartDrawer.drawChart(Applic.app, chartPoints, 600, 100, isMmol,
                    viewMode);
            // Expanded (approx 400x256dp -> ~400x300px)
            chartBitmapExpanded = NotificationChartDrawer.drawChart(Applic.app, chartPoints, 600, 180, isMmol,
                    viewMode);
        }

        remoteViews.setImageViewBitmap(R.id.notification_chart, chartBitmapCollapsed);
        if (showChart && chartBitmapCollapsed != null) {
            remoteViews.setViewVisibility(R.id.notification_chart, View.VISIBLE);
        } else {
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

        setIcon(GluNotBuilder, glvalue, glucose.sensorgen2);
        GluNotBuilder.setSmallIcon(R.drawable.novalue);

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
            chartPoints = tk.glucodata.data.HistoryRepository.getHistoryForNotification(startT, isMmol);
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
                    startupValue = rStr + " · " + vStr;
                } else {
                    startupValue = vStr;
                }
            }
        }

        // Check if chart is enabled
        android.content.SharedPreferences prefs = Applic.app
                .getSharedPreferences(Applic.app.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        boolean showChart = prefs.getBoolean("notification_chart_enabled", true);

        Bitmap chartBitmapCollapsed = null;
        Bitmap chartBitmapExpanded = null;

        if (showChart) {
            chartBitmapCollapsed = NotificationChartDrawer.drawChart(Applic.app, chartPoints, 600, 100, isMmol,
                    viewMode);
            chartBitmapExpanded = NotificationChartDrawer.drawChart(Applic.app, chartPoints, 600, 180, isMmol,
                    viewMode);
        }

        Bitmap arrowBitmap = NotificationChartDrawer.drawArrow(Applic.app, (last != null) ? last.rate : 0, isMmol);

        RemoteViews remoteViews = new RemoteViews(Applic.app.getPackageName(), R.layout.notification_material);
        remoteViews.setTextViewText(R.id.notification_glucose, startupValue);
        remoteViews.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);

        if (showChart && chartBitmapCollapsed != null) {
            remoteViews.setImageViewBitmap(R.id.notification_chart, chartBitmapCollapsed);
            remoteViews.setViewVisibility(R.id.notification_chart, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.notification_chart, View.GONE);
        }

        remoteViews.setViewVisibility(R.id.notification_status, View.VISIBLE);
        remoteViews.setTextViewText(R.id.notification_status, message);

        RemoteViews remoteViewsExpanded = new RemoteViews(Applic.app.getPackageName(),
                R.layout.notification_material_expanded);
        remoteViewsExpanded.setTextViewText(R.id.notification_glucose, startupValue);
        remoteViewsExpanded.setImageViewBitmap(R.id.notification_arrow, arrowBitmap);
        // Time removed from expanded view

        if (showChart && chartBitmapExpanded != null) {
            remoteViewsExpanded.setImageViewBitmap(R.id.notification_chart, chartBitmapExpanded);
            remoteViewsExpanded.setViewVisibility(R.id.notification_chart, View.VISIBLE);
        } else {
            remoteViewsExpanded.setViewVisibility(R.id.notification_chart, View.GONE);
        }
        remoteViewsExpanded.setViewVisibility(R.id.notification_status, View.VISIBLE);
        remoteViewsExpanded.setTextViewText(R.id.notification_status, message);

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
        showpopupalarm(timemess, false);
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
        notificationManager.notify(notid, NumNotBuilder.build());
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
        lossofsignalalarm(4, R.drawable.loss, message, GLUCOSENOTIFICATION, true);
    }

}
