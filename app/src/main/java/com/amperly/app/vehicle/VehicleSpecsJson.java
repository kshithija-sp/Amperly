package com.amperly.app.vehicle;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Explicit codec for the resolver cache, so cached specs survive an app restart. */
final class VehicleSpecsJson {

    private static final Gson GSON = new Gson();

    private VehicleSpecsJson() {
    }

    static String encode(VehicleSpecs specs) {
        JsonObject object = new JsonObject();
        object.addProperty("make", specs.make);
        object.addProperty("model", specs.model);
        object.addProperty("variant", specs.variant);
        object.addProperty("bodyType", specs.bodyType.name());
        object.addProperty("lengthMm", specs.lengthMm);
        object.addProperty("widthMm", specs.widthMm);
        object.addProperty("heightMm", specs.heightMm);
        object.addProperty("wheelbaseMm", specs.wheelbaseMm);
        object.addProperty("groundClearanceMm", specs.groundClearanceMm);
        object.addProperty("tyreWidthMm", specs.tyreWidthMm);
        object.addProperty("tyreAspectPercent", specs.tyreAspectPercent);
        object.addProperty("rimDiameterIn", specs.rimDiameterIn);
        object.addProperty("chargePort", specs.chargePort.name());
        object.addProperty("confidence", specs.confidence.name());
        object.addProperty("source", specs.source);
        if (specs.curated != null) {
            object.add("curated", specs.curated.toJson());
        }
        JsonArray colours = new JsonArray();
        for (String colour : specs.colours) {
            colours.add(colour);
        }
        object.add("colours", colours);
        return GSON.toJson(object);
    }

    static VehicleSpecs decode(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            if (object == null || !object.has("make") || !object.has("model")) {
                return null;
            }
            List<String> colours = new ArrayList<>();
            if (object.has("colours") && object.get("colours").isJsonArray()) {
                for (JsonElement element : object.getAsJsonArray("colours")) {
                    colours.add(element.getAsString());
                }
            }
            return VehicleSpecs.builder(
                            string(object, "make"), string(object, "model"),
                            BodyType.fromKey(string(object, "bodyType")))
                    .variant(string(object, "variant"))
                    .dimensionsMm(integer(object, "lengthMm"), integer(object, "widthMm"),
                            integer(object, "heightMm"))
                    .wheelbaseMm(integer(object, "wheelbaseMm"))
                    .groundClearanceMm(integer(object, "groundClearanceMm"))
                    .tyre(integer(object, "tyreWidthMm"), integer(object, "tyreAspectPercent"),
                            integer(object, "rimDiameterIn"))
                    .chargePort(ChargePortLocation.fromKey(string(object, "chargePort"),
                            ChargePortLocation.FRONT_CENTRE))
                    .colours(colours)
                    .confidence(parseConfidence(string(object, "confidence")))
                    .source(string(object, "source"))
                    .curated(curated(object))
                    .build();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static CuratedProfile curated(JsonObject object) {
        if (!object.has("curated") || !object.get("curated").isJsonObject()) {
            return null;
        }
        return CuratedProfile.fromJson(object.getAsJsonObject("curated"));
    }

    private static SpecConfidence parseConfidence(String value) {
        try {
            return SpecConfidence.valueOf(value);
        } catch (RuntimeException e) {
            return SpecConfidence.LOW;
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static int integer(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
    }
}
