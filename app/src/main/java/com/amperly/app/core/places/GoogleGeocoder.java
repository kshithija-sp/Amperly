package com.amperly.app.core.places;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amperly.app.core.common.GoogleConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Reverse geocoding through the Google Geocoding API.
 *
 * <p>When a host taps the exact spot of their charger on the map, the app shows a readable address
 * for that point so the host can confirm it before the station is saved. The key stays in the app's
 * build configuration and is restricted to this package; the parser is split out so it can be unit
 * tested without a network.
 */
public final class GoogleGeocoder {

    public interface Callback {
        void onAddress(@NonNull String formattedAddress);

        void onError(@NonNull String message);
    }

    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build();

    private GoogleGeocoder() {
    }

    /** Resolves a coordinate to a formatted address. The callback fires on the main thread. */
    public static void reverseGeocode(double latitude, double longitude, @NonNull Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        if (!GoogleConfig.isConfigured()) {
            main.post(() -> callback.onError(GoogleConfig.setupMessage()));
            return;
        }
        new Thread(() -> {
            try {
                String url = String.format(Locale.US, "%s?latlng=%.6f,%.6f&key=%s", BASE_URL,
                        latitude, longitude, GoogleConfig.apiKey());
                Request request = new Request.Builder().url(url).build();
                try (Response response = HTTP.newCall(request).execute()) {
                    ResponseBody body = response.body();
                    String json = body == null ? "" : body.string();
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException("Google Geocoding returned HTTP "
                                + response.code() + ".");
                    }
                    String address = parseFirstAddress(json);
                    if (address.isEmpty()) {
                        throw new IllegalStateException("No address found for that point.");
                    }
                    main.post(() -> callback.onAddress(address));
                }
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Could not look up that address." : e.getMessage();
                main.post(() -> callback.onError(message));
            }
        }, "google-geocode").start();
    }

    /** The first formatted address in a Geocoding response, or an empty string. Network-free. */
    @NonNull
    public static String parseFirstAddress(@Nullable String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            return "";
        }
        JsonArray results = parsed.getAsJsonObject().getAsJsonArray("results");
        if (results == null || results.size() == 0) {
            return "";
        }
        JsonObject first = results.get(0).getAsJsonObject();
        if (first.has("formatted_address")) {
            return first.get("formatted_address").getAsString();
        }
        return "";
    }
}
