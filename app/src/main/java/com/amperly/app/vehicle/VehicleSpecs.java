package com.amperly.app.vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One resolved vehicle: the real dimensions, tyre, charge port and paint that the renderer
 * needs, plus how confident the resolver is in them.
 *
 * <p>Units are millimetres, matching the vehicle spec database and manufacturer sheets. The
 * renderer converts to metres in {@link CarModelFitter}.
 */
public final class VehicleSpecs {

    public static final int DEFAULT_PAINT = 0xE8EDE2;

    public final String make;
    public final String model;
    public final String variant;
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
    public final SpecConfidence confidence;
    /** Where the numbers came from: "curated", "database", "api", "estimate" or "driver". */
    public final String source;
    /** Tier 1 curation for this model, or null when it is fitted from a bare template. */
    public final CuratedProfile curated;

    private VehicleSpecs(Builder builder) {
        this.make = clean(builder.make);
        this.model = clean(builder.model);
        this.variant = clean(builder.variant);
        this.bodyType = builder.bodyType == null ? BodyType.HATCHBACK : builder.bodyType;
        this.lengthMm = builder.lengthMm;
        this.widthMm = builder.widthMm;
        this.heightMm = builder.heightMm;
        this.wheelbaseMm = builder.wheelbaseMm;
        this.groundClearanceMm = builder.groundClearanceMm;
        this.tyreWidthMm = builder.tyreWidthMm;
        this.tyreAspectPercent = builder.tyreAspectPercent;
        this.rimDiameterIn = builder.rimDiameterIn;
        this.chargePort = builder.chargePort == null
                ? ChargePortLocation.FRONT_CENTRE : builder.chargePort;
        this.colours = Collections.unmodifiableList(new ArrayList<>(builder.colours));
        this.confidence = builder.confidence == null ? SpecConfidence.LOW : builder.confidence;
        this.source = builder.source == null ? "estimate" : builder.source;
        this.curated = builder.curated;
    }

    public static Builder builder(String make, String model, BodyType bodyType) {
        return new Builder(make, model, bodyType);
    }

    public VehicleSpecs withBodyType(BodyType type, SpecConfidence newConfidence, String newSource) {
        Builder copy = toBuilder();
        copy.bodyType = type;
        copy.confidence = newConfidence;
        copy.source = newSource;
        return copy.build();
    }

    /** Attaches curation without changing any resolved number. */
    public VehicleSpecs withCurated(CuratedProfile profile) {
        Builder copy = toBuilder();
        copy.curated = profile;
        return copy.build();
    }

    public Builder toBuilder() {
        Builder builder = new Builder(make, model, bodyType);
        builder.variant = variant;
        builder.lengthMm = lengthMm;
        builder.widthMm = widthMm;
        builder.heightMm = heightMm;
        builder.wheelbaseMm = wheelbaseMm;
        builder.groundClearanceMm = groundClearanceMm;
        builder.tyreWidthMm = tyreWidthMm;
        builder.tyreAspectPercent = tyreAspectPercent;
        builder.rimDiameterIn = rimDiameterIn;
        builder.chargePort = chargePort;
        builder.colours = new ArrayList<>(colours);
        builder.confidence = confidence;
        builder.source = source;
        builder.curated = curated;
        return builder;
    }

    /** Normalised key used for lookups and the cache. Variants share one silhouette. */
    public String key() {
        return normalize(make) + "|" + normalize(model);
    }

    public String displayName() {
        String name = (make + " " + model).trim();
        return name.isEmpty() ? "Your vehicle" : name;
    }

    /** Overall tyre diameter: rim plus two sidewalls. */
    public float tyreDiameterMm() {
        return rimDiameterIn * 25.4f + 2f * (tyreWidthMm * Math.max(0, tyreAspectPercent) / 100f);
    }

    public float tyreRadiusMm() {
        return tyreDiameterMm() / 2f;
    }

    public int paintColor() {
        for (String colour : colours) {
            Integer parsed = parseColour(colour);
            if (parsed != null) {
                return parsed;
            }
        }
        return DEFAULT_PAINT;
    }

    public static Integer parseColour(String value) {
        if (value == null) {
            return null;
        }
        try {
            String hex = value.trim().replace("#", "");
            if (hex.length() == 6) {
                return 0xFF000000 | Integer.parseInt(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {
            // A paint name rather than a hex value; the default colour is used instead.
        }
        return null;
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private final String make;
        private final String model;
        private BodyType bodyType;
        private String variant = "";
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
        private SpecConfidence confidence;
        private String source;
        private CuratedProfile curated;

        private Builder(String make, String model, BodyType bodyType) {
            this.make = make;
            this.model = model;
            this.bodyType = bodyType;
        }

        public Builder variant(String value) {
            this.variant = value;
            return this;
        }

        public Builder bodyType(BodyType value) {
            this.bodyType = value;
            return this;
        }

        public Builder dimensionsMm(int length, int width, int height) {
            this.lengthMm = length;
            this.widthMm = width;
            this.heightMm = height;
            return this;
        }

        public Builder wheelbaseMm(int value) {
            this.wheelbaseMm = value;
            return this;
        }

        public Builder groundClearanceMm(int value) {
            this.groundClearanceMm = value;
            return this;
        }

        public Builder tyre(int widthMm, int aspectPercent, int rimIn) {
            this.tyreWidthMm = widthMm;
            this.tyreAspectPercent = aspectPercent;
            this.rimDiameterIn = rimIn;
            return this;
        }

        public Builder chargePort(ChargePortLocation location) {
            this.chargePort = location;
            return this;
        }

        public Builder colours(List<String> values) {
            this.colours = values == null ? new ArrayList<>() : new ArrayList<>(values);
            return this;
        }

        public Builder confidence(SpecConfidence value) {
            this.confidence = value;
            return this;
        }

        public Builder source(String value) {
            this.source = value;
            return this;
        }

        public Builder curated(CuratedProfile value) {
            this.curated = value;
            return this;
        }

        public VehicleSpecs build() {
            return new VehicleSpecs(this);
        }
    }
}
