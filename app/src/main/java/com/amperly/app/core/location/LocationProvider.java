package com.amperly.app.core.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * One place that turns the platform's location services into a coordinate.
 *
 * <p>{@link #requestCurrentLocation} asks for the position now and reports
 * {@link LocationCallback#onLocationUnavailable()} when there is genuinely none. It never invents a
 * coordinate: there is deliberately no pilot-area fallback here, because silently substituting a
 * fixed point for the driver's real position is forbidden — the screens must show an unavailable
 * state (or let the user pick an explicit map origin) instead.
 */
public final class LocationProvider {

    public interface LocationCallback {
        void onLocationRetrieved(double latitude, double longitude);

        /** The device has no fix to give and permission is not the problem. */
        default void onLocationUnavailable() {
        }
    }

    private LocationProvider() {
    }

    private static final String PREFS_NAME = "amperly.location";
    private static final String KEY_PERMISSION_ASKED = "permission_asked";

    /** True when the app currently holds a usable location permission. */
    public static boolean hasLocationPermission(Context context) {
        if (context == null) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * True once the system permission prompt has been shown at least once. Screens use this to ask
     * a single time rather than on every visit.
     */
    public static boolean hasAskedForPermission(Context context) {
        return context != null
                && context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PERMISSION_ASKED, false);
    }

    /** Records that the system permission prompt has been shown. */
    public static void markPermissionAsked(Context context) {
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_PERMISSION_ASKED, true).apply();
        }
    }

    /**
     * Asks for a fresh, high-accuracy fix. If the platform cannot deliver one it falls back to the
     * last known position and, failing that, reports the location as unavailable rather than
     * returning a made-up coordinate.
     */
    public static void requestCurrentLocation(Context context, LocationCallback callback) {
        if (context == null || callback == null) {
            return;
        }
        if (!hasLocationPermission(context)) {
            callback.onLocationUnavailable();
            return;
        }
        try {
            FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(context);
            CancellationTokenSource cancellation = new CancellationTokenSource();
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onLocationRetrieved(location.getLatitude(), location.getLongitude());
                        } else {
                            useLastKnown(client, callback);
                        }
                    })
                    .addOnFailureListener(e -> useLastKnown(client, callback));
        } catch (SecurityException e) {
            callback.onLocationUnavailable();
        } catch (Exception e) {
            callback.onLocationUnavailable();
        }
    }

    @SuppressWarnings("MissingPermission")
    private static void useLastKnown(FusedLocationProviderClient client, LocationCallback callback) {
        try {
            client.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onLocationRetrieved(location.getLatitude(), location.getLongitude());
                        } else {
                            callback.onLocationUnavailable();
                        }
                    })
                    .addOnFailureListener(e -> callback.onLocationUnavailable());
        } catch (SecurityException e) {
            callback.onLocationUnavailable();
        }
    }

}
