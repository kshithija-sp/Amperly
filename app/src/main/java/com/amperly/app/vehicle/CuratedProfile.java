package com.amperly.app.vehicle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Tier 1 in the native renderer: a curated per-model profile.
 *
 * <p>The brief's Tier 1 is a hand-made {@code .glb}. This app draws its cars procedurally on a
 * Canvas, so the equivalent curation unit is a JSON profile: it can pin the silhouette and
 * dimensions, choose the wheel face and accent, add roof rails and a tailgate badge, and nudge
 * the template proportions that make a particular car recognisable. Every field is optional, so
 * a profile can add only a badge to an otherwise template-fitted car.
 *
 * <p>Profiles live in {@code assets/vehicle_profiles.json}. When one exists for a make/model,
 * {@link SpecResolver} attaches it to the resolved specs and {@link CarModelFitter} applies its
 * proportions; dimensions supplied here win over the owned database.
 */
public final class CuratedProfile {

    public final String make;
    public final String model;
    public final String badge;
    public final BodyType bodyType;
    public final int lengthMm;
    public final int widthMm;
    public final int heightMm;
    public final int wheelbaseMm;
    public final int groundClearanceMm;
    public final int tyreWidthMm;
    public final int tyreAspectPercent;
    public final int rimDiameterIn;
    public final ChargePortLocation chargePort;
    public final List<String> colours;
    public final WheelStyle wheelStyle;
    public final boolean roofRails;
    /** Highlight colour for hubs and the charge flap, or 0 for the scene default. */
    public final int accentColor;

    public final float beltlineRatio;
    public final float cabinStartRatio;
    public final float cabinEndRatio;
    public final float cabinWidthRatio;
    public final float endTaper;
    public final float hoodDropRatio;
    public final float rearDropRatio;
    public final float frontOverhangShare;
    public final float archGap;

    private CuratedProfile(Builder builder) {
        this.make = clean(builder.make);
        this.model = clean(builder.model);
        this.badge = clean(builder.badge);
        this.bodyType = builder.bodyType;
        this.lengthMm = builder.lengthMm;
        this.widthMm = builder.widthMm;
        this.heightMm = builder.heightMm;
        this.wheelbaseMm = builder.wheelbaseMm;
        this.groundClearanceMm = builder.groundClearanceMm;
        this.tyreWidthMm = builder.tyreWidthMm;
        this.tyreAspectPercent = builder.tyreAspectPercent;
        this.rimDiameterIn = builder.rimDiameterIn;
        this.chargePort = builder.chargePort;
        this.colours = Collections.unmodifiableList(new ArrayList<>(builder.colours));
        this.wheelStyle = builder.wheelStyle == null ? WheelStyle.SPOKE : builder.wheelStyle;
        this.roofRails = builder.roofRails;
        this.accentColor = builder.accentColor;
        this.beltlineRatio = builder.beltlineRatio;
        this.cabinStartRatio = builder.cabinStartRatio;
        this.cabinEndRatio = builder.cabinEndRatio;
        this.cabinWidthRatio = builder.cabinWidthRatio;
        this.endTaper = builder.endTaper;
        this.hoodDropRatio = builder.hoodDropRatio;
        this.rearDropRatio = builder.rearDropRatio;
        this.frontOverhangShare = builder.frontOverhangShare;
        this.archGap = builder.archGap;
    }

    public static Builder builder(String make, String model) {
        return new Builder(make, model);
    }

    public String key() {
        return (VehicleSpecs.normalize(make) + "|" + VehicleSpecs.normalize(model));
    }

    public boolean hasDimensionOverrides() {
        return lengthMm > 0 || widthMm > 0 || heightMm > 0 || wheelbaseMm > 0
                || groundClearanceMm > 0 || hasTyreOverride();
    }

    public boolean hasTyreOverride() {
        return tyreWidthMm > 0 || tyreAspectPercent > 0 || rimDiameterIn > 0;
    }

    /**
     * Returns the base specs with this profile's overrides applied. A profile that supplies real
     * dimensions promotes the result to HIGH confidence and marks the source as curated; a
     * visual-only profile leaves the resolver's confidence and source untouched.
     */
    public VehicleSpecs apply(VehicleSpecs base) {
        VehicleSpecs.Builder builder = base.toBuilder();
        if (bodyType != null) {
            builder.bodyType(bodyType);
        }
        if (lengthMm > 0 || widthMm > 0 || heightMm > 0) {
            builder.dimensionsMm(
                    lengthMm > 0 ? lengthMm : base.lengthMm,
                    widthMm > 0 ? widthMm : base.widthMm,
                    heightMm > 0 ? heightMm : base.heightMm);
        }
        if (wheelbaseMm > 0) {
            builder.wheelbaseMm(wheelbaseMm);
        }
        if (groundClearanceMm > 0) {
            builder.groundClearanceMm(groundClearanceMm);
        }
        if (hasTyreOverride()) {
            builder.tyre(
                    tyreWidthMm > 0 ? tyreWidthMm : base.tyreWidthMm,
                    tyreAspectPercent > 0 ? tyreAspectPercent : base.tyreAspectPercent,
                    rimDiameterIn > 0 ? rimDiameterIn : base.rimDiameterIn);
        }
        if (chargePort != null) {
            builder.chargePort(chargePort);
        }
        if (!colours.isEmpty()) {
            builder.colours(colours);
        }
        builder.curated(this);
        if (hasDimensionOverrides()) {
            builder.confidence(SpecConfidence.HIGH).source("curated");
        }
        return builder.build();
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("make", make);
        object.addProperty("model", model);
        if (!badge.isEmpty()) {
            object.addProperty("badge", badge);
        }
        if (bodyType != null) {
            object.addProperty("bodyType", bodyType.name());
        }
        if (lengthMm > 0) {
            object.addProperty("lengthMm", lengthMm);
        }
        if (widthMm > 0) {
            object.addProperty("widthMm", widthMm);
        }
        if (heightMm > 0) {
            object.addProperty("heightMm", heightMm);
        }
        if (wheelbaseMm > 0) {
            object.addProperty("wheelbaseMm", wheelbaseMm);
        }
        if (groundClearanceMm > 0) {
            object.addProperty("groundClearanceMm", groundClearanceMm);
        }
        if (hasTyreOverride()) {
            JsonObject tyre = new JsonObject();
            if (tyreWidthMm > 0) {
                tyre.addProperty("widthMm", tyreWidthMm);
            }
            if (tyreAspectPercent > 0) {
                tyre.addProperty("aspectPercent", tyreAspectPercent);
            }
            if (rimDiameterIn > 0) {
                tyre.addProperty("rimDiameterIn", rimDiameterIn);
            }
            object.add("tyre", tyre);
        }
        if (chargePort != null) {
            object.addProperty("chargePort", chargePort.name());
        }
        if (!colours.isEmpty()) {
            JsonArray array = new JsonArray();
            for (String colour : colours) {
                array.add(colour);
            }
            object.add("colours", array);
        }
        if (wheelStyle != null) {
            object.addProperty("wheelStyle", wheelStyle.name());
        }
        if (roofRails) {
            object.addProperty("roofRails", true);
        }
        if (accentColor != 0) {
            object.addProperty("accentColor", String.format(Locale.US, "#%06X",
                    accentColor & 0xFFFFFF));
        }
        JsonObject proportions = new JsonObject();
        putIfSet(proportions, "beltlineRatio", beltlineRatio);
        putIfSet(proportions, "cabinStartRatio", cabinStartRatio);
        putIfSet(proportions, "cabinEndRatio", cabinEndRatio);
        putIfSet(proportions, "cabinWidthRatio", cabinWidthRatio);
        putIfSet(proportions, "endTaper", endTaper);
        putIfSet(proportions, "hoodDropRatio", hoodDropRatio);
        putIfSet(proportions, "rearDropRatio", rearDropRatio);
        putIfSet(proportions, "frontOverhangShare", frontOverhangShare);
        putIfSet(proportions, "archGap", archGap);
        if (proportions.size() > 0) {
            object.add("proportions", proportions);
        }
        return object;
    }

    public static CuratedProfile fromJson(JsonObject object) {
        if (object == null || !object.has("model")) {
            return null;
        }
        Builder builder = builder(string(object, "make"), string(object, "model"));
        if (object.has("badge")) {
            builder.badge(string(object, "badge"));
        }
        if (object.has("bodyType")) {
            builder.bodyType(BodyType.fromKey(string(object, "bodyType")));
        }
        builder.lengthMm(integer(object, "lengthMm"));
        builder.widthMm(integer(object, "widthMm"));
        builder.heightMm(integer(object, "heightMm"));
        builder.wheelbaseMm(integer(object, "wheelbaseMm"));
        builder.groundClearanceMm(integer(object, "groundClearanceMm"));
        if (object.has("tyre") && object.get("tyre").isJsonObject()) {
            JsonObject tyre = object.getAsJsonObject("tyre");
            builder.tyre(integer(tyre, "widthMm"), integer(tyre, "aspectPercent"),
                    integer(tyre, "rimDiameterIn"));
        }
        if (object.has("chargePort")) {
            builder.chargePort(ChargePortLocation.fromKey(string(object, "chargePort"), null));
        }
        if (object.has("colours") && object.get("colours").isJsonArray()) {
            List<String> colours = new ArrayList<>();
            for (JsonElement colour : object.getAsJsonArray("colours")) {
                colours.add(colour.getAsString());
            }
            builder.colours(colours);
        }
        builder.wheelStyle(WheelStyle.fromKey(string(object, "wheelStyle"), WheelStyle.SPOKE));
        builder.roofRails(bool(object, "roofRails"));
        Integer accent = VehicleSpecs.parseColour(string(object, "accentColor"));
        if (accent != null) {
            builder.accentColor(accent);
        }
        if (object.has("proportions") && object.get("proportions").isJsonObject()) {
            JsonObject p = object.getAsJsonObject("proportions");
            builder.beltlineRatio(number(p, "beltlineRatio"));
            builder.cabinStartRatio(number(p, "cabinStartRatio"));
            builder.cabinEndRatio(number(p, "cabinEndRatio"));
            builder.cabinWidthRatio(number(p, "cabinWidthRatio"));
            builder.endTaper(number(p, "endTaper"));
            builder.hoodDropRatio(number(p, "hoodDropRatio"));
            builder.rearDropRatio(number(p, "rearDropRatio"));
            builder.frontOverhangShare(number(p, "frontOverhangShare"));
            builder.archGap(number(p, "archGap"));
        }
        return builder.build();
    }

    private static void putIfSet(JsonObject object, String key, float value) {
        if (value != 0f) {
            object.addProperty(key, value);
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString().trim() : "";
    }

    private static int integer(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
    }

    private static float number(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsFloat() : 0f;
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private final String make;
        private final String model;
        private String badge = "";
        private BodyType bodyType;
        private int lengthMm;
        private int widthMm;
        private int heightMm;
        private int wheelbaseMm;
        private int groundClearanceMm;
        private int tyreWidthMm;
        private int tyreAspectPercent;
        private int rimDiameterIn;
        private ChargePortLocation chargePort;
        private List<String> colours = new ArrayList<>();
        private WheelStyle wheelStyle = WheelStyle.SPOKE;
        private boolean roofRails;
        private int accentColor;
        private float beltlineRatio;
        private float cabinStartRatio;
        private float cabinEndRatio;
        private float cabinWidthRatio;
        private float endTaper;
        private float hoodDropRatio;
        private float rearDropRatio;
        private float frontOverhangShare;
        private float archGap;

        private Builder(String make, String model) {
            this.make = make;
            this.model = model;
        }

        public Builder badge(String value) {
            this.badge = value;
            return this;
        }

        public Builder bodyType(BodyType value) {
            this.bodyType = value;
            return this;
        }

        public Builder lengthMm(int value) {
            this.lengthMm = Math.max(0, value);
            return this;
        }

        public Builder widthMm(int value) {
            this.widthMm = Math.max(0, value);
            return this;
        }

        public Builder heightMm(int value) {
            this.heightMm = Math.max(0, value);
            return this;
        }

        public Builder wheelbaseMm(int value) {
            this.wheelbaseMm = Math.max(0, value);
            return this;
        }

        public Builder groundClearanceMm(int value) {
            this.groundClearanceMm = Math.max(0, value);
            return this;
        }

        public Builder tyre(int widthMm, int aspectPercent, int rimIn) {
            this.tyreWidthMm = Math.max(0, widthMm);
            this.tyreAspectPercent = Math.max(0, aspectPercent);
            this.rimDiameterIn = Math.max(0, rimIn);
            return this;
        }

        public Builder chargePort(ChargePortLocation value) {
            this.chargePort = value;
            return this;
        }

        public Builder colours(List<String> values) {
            this.colours = values == null ? new ArrayList<>() : new ArrayList<>(values);
            return this;
        }

        public Builder wheelStyle(WheelStyle value) {
            this.wheelStyle = value;
            return this;
        }

        public Builder roofRails(boolean value) {
            this.roofRails = value;
            return this;
        }

        public Builder accentColor(int value) {
            this.accentColor = value;
            return this;
        }

        public Builder beltlineRatio(float value) {
            this.beltlineRatio = value;
            return this;
        }

        public Builder cabinStartRatio(float value) {
            this.cabinStartRatio = value;
            return this;
        }

        public Builder cabinEndRatio(float value) {
            this.cabinEndRatio = value;
            return this;
        }

        public Builder cabinWidthRatio(float value) {
            this.cabinWidthRatio = value;
            return this;
        }

        public Builder endTaper(float value) {
            this.endTaper = value;
            return this;
        }

        public Builder hoodDropRatio(float value) {
            this.hoodDropRatio = value;
            return this;
        }

        public Builder rearDropRatio(float value) {
            this.rearDropRatio = value;
            return this;
        }

        public Builder frontOverhangShare(float value) {
            this.frontOverhangShare = value;
            return this;
        }

        public Builder archGap(float value) {
            this.archGap = value;
            return this;
        }

        public CuratedProfile build() {
            return new CuratedProfile(this);
        }
    }
}
