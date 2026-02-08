package com.microtechmd.blecomm;

import android.util.Log;

public final class BlecommLoader {
    private static final String TAG = "BlecommLoader";
    private static volatile boolean loaded = false;
    private static volatile boolean failedLogged = false;

    private BlecommLoader() {
    }

    public static synchronized boolean ensureLoaded() {
        if (loaded) {
            return true;
        }
        try {
            System.loadLibrary("blecomm-lib");
            loaded = true;
        } catch (Throwable t) {
            if (!failedLogged) {
                failedLogged = true;
                Log.e(TAG, "Failed to load libblecomm-lib", t);
            }
        }
        return loaded;
    }

    public static boolean isLoaded() {
        return loaded;
    }
}
