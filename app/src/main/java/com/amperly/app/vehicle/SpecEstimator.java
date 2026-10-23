package com.amperly.app.vehicle;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Tier 3 fallback: when neither the database nor an API knows a vehicle, infer the body type
 * from the model name and use that template's reference dimensions.
 *
 * <p>Everything it returns is LOW confidence, so the UI offers the eight-silhouette picker.
 * Dimensions are the template reference car nudged by obvious name hints ("max", "mini"), not
 * invented per-vehicle figures.
 */
public final class SpecEstimator {

    private SpecEstimator() {
    }

    public static VehicleSpecs estimate(String make, String model, String variant) {
        BodyType bodyType = inferBodyType(make, model);
        BodyTemplate template = BodyTemplate.of(bodyType);
        float scale = lengthHint(model);

        return VehicleSpecs.builder(make, model, bodyType)
                .variant(variant)
                .dimensionsMm(
                        Math.round(template.referenceLengthMm * scale),
                        Math.round(template.referenceWidthMm * (1f + (scale - 1f) * 0.5f)),
                        template.referenceHeightMm)
                .wheelbaseMm(Math.round(template.referenceWheelbaseMm * scale))
                .groundClearanceMm(template.referenceGroundClearanceMm)
                .tyre(template.referenceTyreWidthMm, template.referenceTyreAspectPercent,
                        template.referenceRimDiameterIn)
                .chargePort(ChargePortLocation.FRONT_CENTRE)
                .colours(defaultColours())
                .confidence(SpecConfidence.LOW)
                .source("estimate")
                .build();
    }

    public static BodyType inferBodyType(String make, String model) {
        String all = ((make == null ? "" : make) + " "
                + (model == null ? "" : model)).toLowerCase(Locale.US);

        if (has(all, "pickup", "d-max", "dmax", "v-cross", "hilux", "ranger", "cybertruck",
                "f-150", "gladiator", "navara", "triton")) {
            return BodyType.PICKUP;
        }
        // Checked before the coupe list because "bz4x" contains the BMW "z4" token.
        if (has(all, "bz4x")) {
            return BodyType.SUV;
        }
        if (has(all, "mpv", "carens", "ertiga", "innova", "xl6", "marazzo", "vellfire",
                "carnival", "e6", "alphard", "staria")) {
            return BodyType.MPV;
        }
        if (has(all, "comet", "microcar", "quadricycle", "smart eq", "citroen ami", "ami")) {
            return BodyType.MICROCAR;
        }
        if (has(all, "coupe", "taycan", "911", "supra", "roadster", "mustang", "camaro",
                "nsx", "gt-r", "gtr", "z4", "i8")) {
            return BodyType.COUPE;
        }
        if (has(all, "xuv700", "xuv 700", "suv", "crossover", "r1s", "model y", "model x",
                "fortuner", "endeavour", "endeavor", "glc", "x5", "q7", "safari", "scorpio",
                "thar", "harrier", "sierra", "seal u", "xev", "macan", "eqs", "rivian")) {
            return BodyType.SUV;
        }
        if (has(all, "nexon", "punch", "creta", "seltos", "zs", "atto", "hector", "ioniq",
                "venue", "sonet", "kona", "xuv300", "ev6", "ec3", "curvv", "windsor",
                "evitara", "be 6", "be6", "ex30", "ix1", "compact suv")) {
            return BodyType.COMPACT_SUV;
        }
        if (has(all, "sedan", "city", "verna", "slavia", "virtus", "dzire", "amaze", "tigor",
                "camry", "accord", "civic", "octavia", "model 3", "model s", "seal", "i4",
                "eqe", "jetta", "passat")) {
            return BodyType.SEDAN;
        }
        if (has(all, "hatch", "tiago", "swift", "i20", "altroz", "baleno", "kwid", "bolt",
                "mini", "golf", "zoe", "leaf", "e6")) {
            return BodyType.HATCHBACK;
        }
        // Nothing recognisable: the most common silhouette in the pilot fleet, still LOW confidence.
        return BodyType.HATCHBACK;
    }

    private static boolean has(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static float lengthHint(String model) {
        String value = model == null ? "" : model.toLowerCase(Locale.US);
        if (has(value, " max", "max ", "xl", "long", "lwb", "7 seater", "7-seater")) {
            return 1.05f;
        }
        if (has(value, "mini", "compact", "small", "micro")) {
            return 0.95f;
        }
        return 1f;
    }

    private static ArrayList<String> defaultColours() {
        ArrayList<String> colours = new ArrayList<>();
        colours.add("#E8EDE2");
        colours.add("#121814");
        return colours;
    }
}
