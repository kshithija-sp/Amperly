package com.amperly.app.vehicle;

import androidx.annotation.Nullable;

import com.amperly.app.BuildConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tier 3(b) adapter for a third-party specifications service.
 *
 * <p>The adapter speaks one small, normalised JSON contract instead of a vendor's full schema:
 *
 * <pre>
 * { "vehicles": [ {
 *     "make": "Tata", "model": "Nexon EV", "variant": "Empowered+",
 *     "bodyType": "COMPACT_SUV",
 *     "lengthMm": 3994, "widthMm": 1811, "heightMm": 1616,
 *     "wheelbaseMm": 2498, "groundClearanceMm": 190,
 *     "tyre": { "widthMm": 215, "aspectPercent": 60, "rimDiameterIn": 16 },
 *     "chargePort": "FRONT_CENTRE",
 *     "colours": ["#E9EFE3"], "confidence": "HIGH"
 * } ] }
 * </pre>
 *
 * <p>It accepts a bare array or a single object too. Pointing {@code VEHICLE_SPECS_API_URL} at
 * a thin proxy that maps CarsXE, API Ninjas or a dealer feed into this shape keeps the app
 * independent of any one vendor. With no URL or key configured the provider is inert and never
 * makes a request; the owned database and estimator remain the only sources.
 */
public final class RemoteSpecProvider implements VehicleSpecProvider {

    private final String endpoint;
    private final String apiKey;
    private final OkHttpClient client;

    RemoteSpecProvider(String endpoint, String apiKey, OkHttpClient client) {
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.client = client != null ? client : newClient();
    }

    /** Builds the provider from BuildConfig, or null when the tier is not configured. */
    @Nullable
    public static RemoteSpecProvider fromConfig() {
        String url = BuildConfig.VEHICLE_SPECS_API_URL;
        String key = BuildConfig.VEHICLE_SPECS_API_KEY;
        if (url == null || url.trim().isEmpty() || key == null || key.trim().isEmpty()) {
            return null;
        }
        return new RemoteSpecProvider(url, key, newClient());
    }

    private static OkHttpClient newClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return !endpoint.isEmpty() && !apiKey.isEmpty();
    }

    @Nullable
    @Override
    public VehicleSpecs lookup(String make, String model, String variant) {
        if (!isConfigured() || make == null || model == null || model.trim().isEmpty()) {
            return null;
        }
        HttpUrl base = HttpUrl.parse(endpoint);
        if (base == null) {
            return null;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("make", make)
                .addQueryParameter("model", model)
                .addQueryParameter("variant", variant == null ? "" : variant)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("X-Api-Key", apiKey)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return parse(response.body().string(), make, model, variant);
        } catch (IOException | RuntimeException e) {
            // A specs lookup must never break the vehicle screen; fall through to the estimator.
            return null;
        }
    }

    /** Package-visible so the response mapping can be tested without a network. */
    @Nullable
    static VehicleSpecs parse(@Nullable String json, String make, String model, String variant) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonObject vehicle = firstVehicle(root);
            if (vehicle == null) {
                return null;
            }
            JsonObject dimensions = object(vehicle, "dimensionsMm");
            int length = integer(vehicle, dimensions, "lengthMm");
            int width = integer(vehicle, dimensions, "widthMm");
            int height = integer(vehicle, dimensions, "heightMm");
            if (length <= 0 || width <= 0 || height <= 0) {
                return null;
            }
            JsonObject tyre = object(vehicle, "tyre");
            String bodyValue = string(vehicle, "bodyType");
            BodyType bodyType = bodyValue.isEmpty()
                    ? SpecEstimator.inferBodyType(make, model)
                    : BodyType.fromKey(bodyValue);

            VehicleSpecs.Builder builder = VehicleSpecs.builder(
                            value(vehicle, "make", make), value(vehicle, "model", model), bodyType)
                    .variant(value(vehicle, "variant", variant))
                    .dimensionsMm(length, width, height)
                    .wheelbaseMm(integer(vehicle, dimensions, "wheelbaseMm"))
                    .groundClearanceMm(integer(vehicle, dimensions, "groundClearanceMm"))
                    .tyre(integer(tyre, "widthMm"), integer(tyre, "aspectPercent"),
                            integer(tyre, "rimDiameterIn"))
                    .chargePort(ChargePortLocation.fromKey(string(vehicle, "chargePort"),
                            ChargePortLocation.FRONT_CENTRE))
                    .confidence(confidence(vehicle))
                    .source("api");
            List<String> colours = strings(vehicle, "colours");
            if (!colours.isEmpty()) {
                builder.colours(colours);
            }
            return builder.build();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static JsonObject firstVehicle(JsonElement root) {
        if (root == null) {
            return null;
        }
        if (root.isJsonArray()) {
            return firstObject(root.getAsJsonArray());
        }
        if (!root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        if (object.has("vehicles") && object.get("vehicles").isJsonArray()) {
            return firstObject(object.getAsJsonArray("vehicles"));
        }
        if (object.has("data") && object.get("data").isJsonArray()) {
            return firstObject(object.getAsJsonArray("data"));
        }
        return object;
    }

    @Nullable
    private static JsonObject firstObject(JsonArray array) {
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                JsonObject dimensions = object(object, "dimensionsMm");
                if (integer(object, dimensions, "lengthMm") > 0) {
                    return object;
                }
            }
        }
        return null;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static int integer(JsonObject parent, JsonObject nested, String key) {
        if (parent != null && parent.has(key) && !parent.get(key).isJsonNull()) {
            try {
                return parent.get(key).getAsInt();
            } catch (RuntimeException ignored) {
                // fall through to the nested object
            }
        }
        if (nested != null && nested.has(key) && !nested.get(key).isJsonNull()) {
            try {
                return nested.get(key).getAsInt();
            } catch (RuntimeException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static int integer(JsonObject parent, String key) {
        return integer(parent, null, key);
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString().trim() : "";
    }

    private static String value(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value.isEmpty() ? (fallback == null ? "" : fallback) : value;
    }

    private static List<String> strings(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        if (object != null && object.has(key) && object.get(key).isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray(key)) {
                if (element != null && !element.isJsonNull()) {
                    values.add(element.getAsString());
                }
            }
        }
        return values;
    }

    private static SpecConfidence confidence(JsonObject vehicle) {
        String value = string(vehicle, "confidence").toUpperCase(java.util.Locale.US);
        if (value.equals("HIGH")) {
            return SpecConfidence.HIGH;
        }
        if (value.equals("MEDIUM")) {
            return SpecConfidence.MEDIUM;
        }
        if (value.equals("LOW")) {
            return SpecConfidence.LOW;
        }
        JsonObject dimensions = object(vehicle, "dimensionsMm");
        boolean complete = integer(vehicle, dimensions, "lengthMm") > 0
                && integer(vehicle, dimensions, "widthMm") > 0
                && integer(vehicle, dimensions, "heightMm") > 0
                && integer(vehicle, dimensions, "wheelbaseMm") > 0;
        return complete ? SpecConfidence.HIGH : SpecConfidence.MEDIUM;
    }
}
