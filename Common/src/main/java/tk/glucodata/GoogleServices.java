package tk.glucodata;

import android.content.Context;

public final class GoogleServices {
    private static final String LOG_ID = "GoogleServices";

    private GoogleServices() {
    }

    public static boolean isPlayServicesAvailable(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Context appContext = context.getApplicationContext();
            Class<?> apiClass = Class.forName("com.google.android.gms.common.GoogleApiAvailabilityLight");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object statusObj = apiClass
                    .getMethod("isGooglePlayServicesAvailable", Context.class)
                    .invoke(api, appContext);
            int status = (statusObj instanceof Integer) ? (Integer) statusObj : -1;
            int success = 0;
            try {
                Class<?> connectionResultClass = Class.forName("com.google.android.gms.common.ConnectionResult");
                success = connectionResultClass.getField("SUCCESS").getInt(null);
            } catch (Throwable ignored) {
                // Fall back to documented SUCCESS == 0.
            }
            boolean available = status == success;
            if (!available) {
                Log.w(LOG_ID, "Google Play Services unavailable, status=" + status);
            }
            return available;
        } catch (Throwable th) {
            Log.stack(LOG_ID, "isPlayServicesAvailable", th);
            return false;
        }
    }
}
