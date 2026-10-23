package com.amperly.app.core.routing;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amperly.app.core.common.GoogleConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Road routing through the Google Directions API.
 *
 * <p>This is the production provider: it turns an origin and destination into real road geometry,
 * a distance, a traffic-aware travel time and the ordered turn-by-turn steps that the route screen
 * and the in-app navigation session show.
 *
 * <p>The parser is a pure static method so it can be unit tested against a captured payload; only
 * {@link #route} touches the network. When the key is missing or the call fails the caller is told,
 * and may fall back to a clearly labelled straight-line estimate — the client never passes an
 * estimate off as a Google route.
 */
public final class GoogleDirectionsClient {

    /** How the route is measured. Google exposes these as request modes. */
    public enum TravelMode {
        DRIVING("driving", true, 45.0),
        WALKING("walking", false, 5.0);

        private final String requestMode;
        private final boolean trafficAware;
        private final double estimateSpeedKmh;

        TravelMode(String requestMode, boolean trafficAware, double estimateSpeedKmh) {
            this.requestMode = requestMode;
            this.trafficAware = trafficAware;
            this.estimateSpeedKmh = estimateSpeedKmh;
        }

        /** The value Google expects for the {@code mode} parameter. */
        public String requestMode() {
            return requestMode;
        }

        /** True when Google can return a traffic-aware duration for this mode. */
        public boolean isTrafficAware() {
            return trafficAware;
        }

        /** Speed used only for the labelled straight-line fallback. */
        public double estimateSpeedKmh() {
            return estimateSpeedKmh;
        }

        public String label() {
            return this == WALKING ? "walking" : "driving";
        }
    }

    public interface Callback {
        void onRoute(@NonNull DirectionsRoute route);

        void onError(@NonNull String message);
    }

    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/directions/json";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build();

    private GoogleDirectionsClient() {
    }

    /**
     * Requests a road route between two points. The callback fires on the main thread, so callers
     * can touch the UI directly.
     */
    public static void route(@Nullable Context context, @NonNull TravelMode mode,
                             double fromLat, double fromLng, double toLat, double toLng,
                             @NonNull Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        if (!GoogleConfig.isConfigured()) {
            main.post(() -> callback.onError(GoogleConfig.setupMessage()));
            return;
        }
        new Thread(() -> {
            try {
                DirectionsRoute route = fetch(mode, format(fromLat, fromLng), format(toLat, toLng));
                main.post(() -> callback.onRoute(route));
            } catch (Exception e) {
                String message = describe(e);
                main.post(() -> callback.onError(message));
            }
        }, "google-directions").start();
    }

    /**
     * Requests a route to a free-text destination (a place name, shop, hotel or address), which is
     * what the driver typed when Place autocomplete had no match. Google snaps it to a road and the
     * snapped coordinates come back in the route geometry.
     */
    public static void routeToAddress(@Nullable Context context, @NonNull TravelMode mode,
                                      double fromLat, double fromLng, @NonNull String destination,
                                      @NonNull Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        if (!GoogleConfig.isConfigured()) {
            main.post(() -> callback.onError(GoogleConfig.setupMessage()));
            return;
        }
        if (destination.trim().isEmpty()) {
            main.post(() -> callback.onError("Enter a destination to plan the trip."));
            return;
        }
        new Thread(() -> {
            try {
                DirectionsRoute route = fetch(mode, format(fromLat, fromLng), destination.trim());
                main.post(() -> callback.onRoute(route));
            } catch (Exception e) {
                String message = describe(e);
                main.post(() -> callback.onError(message));
            }
        }, "google-directions").start();
    }

    @NonNull
    static DirectionsRoute fetch(@NonNull TravelMode mode, @NonNull String origin,
                                 @NonNull String destination) throws Exception {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("?origin=").append(enc(origin))
                .append("&destination=").append(enc(destination))
                .append("&mode=").append(mode.requestMode())
                .append("&alternatives=false")
                .append("&key=").append(GoogleConfig.apiKey());
        if (mode.isTrafficAware()) {
            // departure_time=now is what makes Google return duration_in_traffic.
            url.append("&departure_time=now");
        }
        Request request = new Request.Builder().url(url.toString()).build();
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody body = response.body();
            String json = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("The Google Directions service returned HTTP "
                        + response.code() + ".");
            }
            return parseRoute(mode, json);
        }
    }

    private static String format(double lat, double lng) {
        return String.format(Locale.US, "%.6f,%.6f", lat, lng);
    }

    private static String enc(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /**
     * Parses a Directions response. Public and network-free so it can be unit tested against a
     * captured payload.
     */
    @NonNull
    public static DirectionsRoute parseRoute(@NonNull TravelMode mode, @NonNull String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalStateException("The Google Directions service returned an unexpected response.");
        }
        JsonObject root = parsed.getAsJsonObject();
        String status = root.has("status") ? root.get("status").getAsString() : "";
        if (!"OK".equals(status)) {
            // error_message is optional; when it is absent the status code maps to a plain message.
            String detail = root.has("error_message") && !root.get("error_message").isJsonNull()
                    ? root.get("error_message").getAsString() : null;
            throw new IllegalStateException(statusMessage(status, detail));
        }
        JsonArray routes = root.getAsJsonArray("routes");
        if (routes == null || routes.size() == 0) {
            throw new IllegalStateException("Google found no route between those points.");
        }
        JsonObject route = routes.get(0).getAsJsonObject();
        JsonArray legs = route.getAsJsonArray("legs");
        if (legs == null || legs.size() == 0) {
            throw new IllegalStateException("Google found no route between those points.");
        }
        JsonObject leg = legs.get(0).getAsJsonObject();

        double distanceMeters = metricValue(leg, "distance");
        double durationSeconds = metricValue(leg, "duration");
        double durationInTrafficSeconds = metricValue(leg, "duration_in_traffic");
        String startAddress = stringOrEmpty(leg, "start_address");
        String endAddress = stringOrEmpty(leg, "end_address");

        List<DirectionsRoute.Step> steps = new ArrayList<>();
        JsonArray stepArray = leg.getAsJsonArray("steps");
        if (stepArray != null) {
            for (JsonElement element : stepArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject step = element.getAsJsonObject();
                String instruction = stripHtml(stringOrEmpty(step, "html_instructions"));
                String distanceText = textValue(step, "distance");
                String durationText = textValue(step, "duration");
                String maneuver = stringOrEmpty(step, "maneuver");
                double lat = 0;
                double lng = 0;
                if (step.has("start_location") && step.get("start_location").isJsonObject()) {
                    JsonObject start = step.getAsJsonObject("start_location");
                    lat = doubleOrZero(start, "lat");
                    lng = doubleOrZero(start, "lng");
                }
                steps.add(new DirectionsRoute.Step(instruction, distanceText, durationText,
                        maneuver, lat, lng));
            }
        }

        List<double[]> points = new ArrayList<>();
        if (route.has("overview_polyline") && route.get("overview_polyline").isJsonObject()) {
            JsonObject polyline = route.getAsJsonObject("overview_polyline");
            if (polyline.has("points")) {
                points = Polyline.decode(polyline.get("points").getAsString());
            }
        }
        if (points.size() < 2) {
            throw new IllegalStateException("Google returned a route with no usable geometry.");
        }

        int durationMinutes = (int) Math.round(durationSeconds / 60d);
        int trafficMinutes = durationInTrafficSeconds > 0
                ? (int) Math.round(durationInTrafficSeconds / 60d) : 0;
        return new DirectionsRoute(points, distanceMeters / 1000d, durationMinutes, trafficMinutes,
                startAddress, endAddress, steps, false);
    }

    private static String statusMessage(String status, String detail) {
        if (detail != null && !detail.isEmpty()) {
            return detail;
        }
        switch (status) {
            case "ZERO_RESULTS":
                return "Google found no route between those points.";
            case "NOT_FOUND":
                return "One of those places could not be found on Google Maps.";
            case "REQUEST_DENIED":
                return "Google denied the request. Check that the Directions API is enabled for "
                        + "your key and that the key restrictions allow this app.";
            case "OVER_QUERY_LIMIT":
            case "OVER_DAILY_LIMIT":
                return "Google's routing quota for this key has been reached.";
            case "INVALID_REQUEST":
                return "Google rejected the route request.";
            default:
                return "Google could not calculate a route right now.";
        }
    }

    private static double metricValue(JsonObject parent, String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            JsonObject metric = parent.getAsJsonObject(key);
            if (metric.has("value")) {
                return metric.get("value").getAsDouble();
            }
        }
        return 0d;
    }

    private static String textValue(JsonObject parent, String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            JsonObject metric = parent.getAsJsonObject(key);
            if (metric.has("text")) {
                return metric.get("text").getAsString();
            }
        }
        return "";
    }

    private static String stringOrEmpty(JsonObject parent, String key) {
        return parent.has(key) && !parent.get(key).isJsonNull()
                ? parent.get(key).getAsString() : "";
    }

    private static double doubleOrZero(JsonObject parent, String key) {
        return parent.has(key) && !parent.get(key).isJsonNull()
                ? parent.get(key).getAsDouble() : 0d;
    }

    /** Google returns instructions with {@code <b>}/{@code <div>} markup; the screen needs plain text. */
    static String stripHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return html.replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Could not reach Google Directions.";
        }
        return message;
    }
}
