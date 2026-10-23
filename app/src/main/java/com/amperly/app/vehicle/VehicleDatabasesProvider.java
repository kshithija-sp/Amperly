package com.amperly.app.vehicle;

import androidx.annotation.Nullable;

import com.amperly.app.BuildConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tier 3(b) adapter for the Vehicle Databases YMMT specifications API.
 *
 * <p>CarsXE could not serve this app: its specs endpoint requires a VIN and its YMM endpoint
 * returns trim names only. Vehicle Databases accepts year/make/model/trim - exactly what the
 * vehicle profile stores - and returns exterior dimensions, so it is the direct provider here.
 *
 * <pre>
 * GET https://api.vehicledatabases.com/ymm-specs/v3/{year}/{make}/{model}/{trim}
 * x-authkey: &lt;key&gt;
 * </pre>
 *
 * <p>The response nests each measurement in an array of {@code {value, unit}} pairs. Dimensions
 * are preferred in millimetres and converted from inches when only that unit is present. A
 * response without length, width and height is rejected rather than half-applied.
 *
 * <p>The trim path segment is required by the vendor, so a vehicle with no variant entered stays
 * on the estimator. Coverage is North America and Europe from 1981; the owned database remains
 * the India-first source of truth.
 */
public final class VehicleDatabasesProvider implements VehicleSpecProvider {

    static final String ENDPOINT = "https://api.vehicledatabases.com/ymm-specs/v3/";

    private final String apiKey;
    private final OkHttpClient client;

    VehicleDatabasesProvider(String apiKey, @Nullable OkHttpClient client) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.client = client != null ? client : newClient();
    }

    /** Builds the provider from BuildConfig, or null when no key is configured. */
    @Nullable
    public static VehicleDatabasesProvider fromConfig() {
        String key = BuildConfig.VEHICLE_DATABASES_API_KEY;
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        return new VehicleDatabasesProvider(key, newClient());
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
        return !apiKey.isEmpty();
    }

    @Nullable
    @Override
    public VehicleSpecs lookup(String make, String model, String variant) {
        return lookup(make, model, variant, 0);
    }

    @Nullable
    @Override
    public VehicleSpecs lookup(String make, String model, String variant, int year) {
        if (!isConfigured() || year < 1981 || isBlank(make) || isBlank(model) || isBlank(variant)) {
            return null;
        }
        HttpUrl base = HttpUrl.parse(ENDPOINT);
        if (base == null) {
            return null;
        }
        HttpUrl url = base.newBuilder()
                .addPathSegment(String.valueOf(year))
                .addPathSegment(make.trim())
                .addPathSegment(model.trim())
                .addPathSegment(variant.trim())
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("x-authkey", apiKey)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return parse(response.body().string(), make, model, variant);
        } catch (IOException | RuntimeException e) {
            // A lookup failure must never block the estimator fallback.
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
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject body = root.getAsJsonObject();
            if (body.has("status") && !"success".equalsIgnoreCase(string(body, "status"))) {
                return null;
            }
            JsonObject data = object(body, "data");
            if (data == null) {
                return null;
            }

            int length = millimetres(find(data, "overall_length", 0));
            int width = millimetres(find(data, "overall_width", 0));
            int height = millimetres(find(data, "overall_height", 0));
            if (length <= 0 || width <= 0 || height <= 0) {
                return null;
            }
            int wheelbase = millimetres(find(data, "wheelbase", 0));
            int clearance = millimetres(find(data, "ground_clearance_min", 0));
            if (clearance <= 0) {
                clearance = millimetres(find(data, "ground_clearance_max", 0));
            }

            String resolvedMake = value(data, "make", make);
            String resolvedModel = value(data, "model", model);
            String resolvedVariant = value(data, "trim", variant);
            BodyType bodyType = bodyType(data, resolvedMake, resolvedModel);

            return VehicleSpecs.builder(resolvedMake, resolvedModel, bodyType)
                    .variant(resolvedVariant)
                    .dimensionsMm(length, width, height)
                    .wheelbaseMm(wheelbase)
                    .groundClearanceMm(clearance)
                    .chargePort(ChargePortLocation.FRONT_CENTRE)
                    .confidence(wheelbase > 0 ? SpecConfidence.HIGH : SpecConfidence.MEDIUM)
                    .source("api")
                    .build();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Finds a named measurement anywhere in the nested dimensions/specifications tree. */
    @Nullable
    private static JsonElement find(@Nullable JsonElement element, String key, int depth) {
        if (element == null || depth > 8) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has(key)) {
                return object.get(key);
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement found = find(entry.getValue(), key, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                JsonElement found = find(child, key, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A measurement is a list of {value, unit} pairs; millimetres win, inches are converted. */
    private static int millimetres(@Nullable JsonElement measurement) {
        if (measurement == null) {
            return 0;
        }
        JsonArray entries = measurement.isJsonArray()
                ? measurement.getAsJsonArray()
                : singleton(measurement);
        int fromMm = 0;
        int fromInches = 0;
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject pair = entry.getAsJsonObject();
            String unit = string(pair, "unit").toLowerCase(Locale.US);
            double value = number(string(pair, "value"));
            if (value <= 0) {
                continue;
            }
            if (unit.startsWith("mm") || unit.startsWith("millimet")) {
                fromMm = Math.max(fromMm, (int) Math.round(value));
            } else if (unit.equals("in") || unit.startsWith("inch")) {
                fromInches = Math.max(fromInches, (int) Math.round(value * 25.4));
            }
        }
        return fromMm > 0 ? fromMm : fromInches;
    }

    private static JsonArray singleton(JsonElement element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    private static double number(String value) {
        if (value == null) {
            return 0;
        }
        String cleaned = value.replace(",", "").trim();
        if (cleaned.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BodyType bodyType(JsonObject data, String make, String model) {
        JsonObject vehicle = object(data, "vehicle");
        String value = vehicle == null ? "" : string(vehicle, "body_type");
        if (value.isEmpty()) {
            value = string(data, "style");
        }
        String key = value.toLowerCase(Locale.US);
        if (key.contains("pickup") || key.contains("truck")) {
            return BodyType.PICKUP;
        }
        if (key.contains("sport utility") || key.contains("suv") || key.contains("crossover")) {
            return key.contains("compact") || key.contains("subcompact")
                    ? BodyType.COMPACT_SUV : BodyType.SUV;
        }
        if (key.contains("minivan") || key.contains("van") || key.contains("mpv")) {
            return BodyType.MPV;
        }
        if (key.contains("coupe") || key.contains("convertible") || key.contains("roadster")) {
            return BodyType.COUPE;
        }
        if (key.contains("hatch") || key.contains("wagon")) {
            return BodyType.HATCHBACK;
        }
        if (key.contains("sedan") || key.contains("saloon")) {
            return BodyType.SEDAN;
        }
        if (key.contains("micro")) {
            return BodyType.MICROCAR;
        }
        return SpecEstimator.inferBodyType(make, model);
    }

    @Nullable
    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString().trim() : "";
    }

    private static String value(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value.isEmpty() ? (fallback == null ? "" : fallback) : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
