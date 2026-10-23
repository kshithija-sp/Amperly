package com.amperly.app.vehicle;

/**
 * fitTemplateToSpecs: combines the body template's proportions with the resolved specs.
 *
 * <p>The body is regenerated at the real length, width and height, the axles move to the real
 * wheelbase and overhangs, the wheels take their diameter and width from the tyre size, and the
 * ride height comes from the ground clearance. Wheels, glass and lamps are built from their own
 * measurements, so nothing gets stretched the way a plain non-uniform scale would stretch it.
 */
public final class CarModelFitter {

    private static final float MIN_RADIUS = 0.18f;
    private static final float MAX_RADIUS = 0.45f;
    private static final int DEFAULT_ACCENT = 0xC6F94C;

    private CarModelFitter() {
    }

    public static VehicleGeometry fit(VehicleSpecs specs) {
        BodyTemplate template = BodyTemplate.of(specs.bodyType);
        CuratedProfile curated = specs.curated;

        float beltlineRatio = proportion(curated == null ? 0f : curated.beltlineRatio,
                template.beltlineRatio);
        float cabinStartRatio = proportion(curated == null ? 0f : curated.cabinStartRatio,
                template.cabinStartRatio);
        float cabinEndRatio = proportion(curated == null ? 0f : curated.cabinEndRatio,
                template.cabinEndRatio);
        float cabinWidthRatio = proportion(curated == null ? 0f : curated.cabinWidthRatio,
                template.cabinWidthRatio);
        float endTaper = proportion(curated == null ? 0f : curated.endTaper, template.endTaper);
        float hoodDropRatio = proportion(curated == null ? 0f : curated.hoodDropRatio,
                template.hoodDropRatio);
        float rearDropRatio = proportion(curated == null ? 0f : curated.rearDropRatio,
                template.rearDropRatio);
        float frontOverhangShare = proportion(
                curated == null ? 0f : curated.frontOverhangShare, template.frontOverhangShare);
        float archGap = proportion(curated == null ? 0f : curated.archGap, template.archGap);
        WheelStyle wheelStyle = curated == null || curated.wheelStyle == null
                ? WheelStyle.SPOKE : curated.wheelStyle;
        boolean roofRails = curated != null && curated.roofRails;
        int accentColor = curated == null || curated.accentColor == 0
                ? DEFAULT_ACCENT : curated.accentColor;

        float length = metres(specs.lengthMm, template.referenceLengthMm);
        float width = metres(specs.widthMm, template.referenceWidthMm);
        float height = metres(specs.heightMm, template.referenceHeightMm);

        float wheelbase = metres(specs.wheelbaseMm, template.referenceWheelbaseMm);
        wheelbase = clamp(wheelbase, length * 0.45f, length * 0.78f);

        float overhang = Math.max(0.1f, length - wheelbase);
        float frontOverhang = overhang * frontOverhangShare;
        float rearOverhang = overhang - frontOverhang;
        float frontAxleZ = length / 2f - frontOverhang;
        float rearAxleZ = -length / 2f + rearOverhang;

        float clearance = metres(specs.groundClearanceMm, template.referenceGroundClearanceMm);
        clearance = clamp(clearance, 0.08f, 0.35f);
        clearance = Math.min(clearance, height * 0.22f);

        float tyreWidthMm = specs.tyreWidthMm > 0 ? specs.tyreWidthMm : template.referenceTyreWidthMm;
        float tyreAspect = specs.tyreAspectPercent > 0
                ? specs.tyreAspectPercent : template.referenceTyreAspectPercent;
        float rimIn = specs.rimDiameterIn > 0 ? specs.rimDiameterIn : template.referenceRimDiameterIn;
        float radius = (rimIn * 25.4f + 2f * (tyreWidthMm * tyreAspect / 100f)) / 2000f;
        radius = clamp(radius, MIN_RADIUS, Math.min(MAX_RADIUS, height * 0.32f));

        float wheelWidth = clamp(tyreWidthMm / 1000f * 1.02f, 0.13f, 0.36f);
        wheelWidth = Math.min(wheelWidth, width * 0.22f);
        float wheelCentreX = Math.max(wheelWidth / 2f + 0.02f,
                width / 2f - wheelWidth / 2f - 0.006f);

        float beltline = clamp(height * beltlineRatio,
                clearance + 0.18f, height - 0.14f);
        beltline = Math.max(beltline, radius * 1.05f + 0.06f);

        float halfLength = length / 2f;
        float cabinStartZ = clamp(halfLength * cabinStartRatio,
                -halfLength * 0.82f, halfLength * 0.30f);
        float cabinEndZ = clamp(halfLength * cabinEndRatio,
                cabinStartZ + Math.max(0.55f, length * 0.16f), halfLength * 0.62f);

        return new VehicleGeometry(
                specs.bodyType, length, width, height, wheelbase, clearance, beltline, height,
                frontOverhang, rearOverhang, frontAxleZ, rearAxleZ, cabinStartZ, cabinEndZ,
                radius, wheelWidth, wheelCentreX, radius * (1f + archGap),
                cabinWidthRatio, endTaper,
                height * hoodDropRatio, height * rearDropRatio,
                Math.min(beltline * 0.72f, beltline - 0.06f),
                specs.paintColor(), specs.chargePort, wheelStyle, roofRails, accentColor);
    }

    /** A curated proportion wins only when it is set; zero means "keep the template value". */
    private static float proportion(float curated, float fallback) {
        return curated != 0f ? curated : fallback;
    }

    private static float metres(int value, int fallback) {
        return (value > 0 ? value : fallback) / 1000f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
