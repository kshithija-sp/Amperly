package com.amperly.app.vehicle;

/**
 * Reference skeleton for one body type, the native equivalent of a template .glb.
 *
 * <p>Dimensions are the reference car the silhouette was drawn around. Fitting takes the real
 * length, width, height, wheelbase, clearance and tyre from {@link VehicleSpecs} and the
 * proportions below from here, so glass, lamps and wheels are regenerated at their true size
 * instead of being stretched with the body.
 */
public final class BodyTemplate {

    public final BodyType bodyType;
    public final int referenceLengthMm;
    public final int referenceWidthMm;
    public final int referenceHeightMm;
    public final int referenceWheelbaseMm;
    public final int referenceGroundClearanceMm;
    public final int referenceTyreWidthMm;
    public final int referenceTyreAspectPercent;
    public final int referenceRimDiameterIn;

    /** Beltline height as a fraction of overall height. */
    public final float beltlineRatio;
    /** Greenhouse limits along the length, as fractions of half the length (-1..1). */
    public final float cabinStartRatio;
    public final float cabinEndRatio;
    /** Greenhouse width as a fraction of body width. */
    public final float cabinWidthRatio;
    /** How much the nose and tail pinch in. */
    public final float endTaper;
    /** Bonnet and rear deck drop as fractions of overall height. */
    public final float hoodDropRatio;
    public final float rearDropRatio;
    /** Share of the total overhang that sits ahead of the front axle. */
    public final float frontOverhangShare;
    /** Extra arch radius around the tyre, as a fraction of the tyre radius. */
    public final float archGap;

    private BodyTemplate(BodyType bodyType, int length, int width, int height, int wheelbase,
                         int clearance, int tyreWidth, int tyreAspect, int rimIn,
                         float beltlineRatio, float cabinStartRatio, float cabinEndRatio,
                         float cabinWidthRatio, float endTaper, float hoodDropRatio,
                         float rearDropRatio, float frontOverhangShare, float archGap) {
        this.bodyType = bodyType;
        this.referenceLengthMm = length;
        this.referenceWidthMm = width;
        this.referenceHeightMm = height;
        this.referenceWheelbaseMm = wheelbase;
        this.referenceGroundClearanceMm = clearance;
        this.referenceTyreWidthMm = tyreWidth;
        this.referenceTyreAspectPercent = tyreAspect;
        this.referenceRimDiameterIn = rimIn;
        this.beltlineRatio = beltlineRatio;
        this.cabinStartRatio = cabinStartRatio;
        this.cabinEndRatio = cabinEndRatio;
        this.cabinWidthRatio = cabinWidthRatio;
        this.endTaper = endTaper;
        this.hoodDropRatio = hoodDropRatio;
        this.rearDropRatio = rearDropRatio;
        this.frontOverhangShare = frontOverhangShare;
        this.archGap = archGap;
    }

    private static final BodyTemplate[] TEMPLATES = {
            // Hatchback: short overhangs, tall glasshouse (reference: Tata Tiago EV).
            new BodyTemplate(BodyType.HATCHBACK, 3769, 1677, 1536, 2400, 165, 175, 65, 14,
                    0.62f, -0.62f, 0.34f, 0.86f, 0.26f, 0.08f, 0.04f, 0.42f, 0.16f),
            // Sedan: long bonnet, low roof (reference: Tesla Model 3).
            new BodyTemplate(BodyType.SEDAN, 4720, 1848, 1442, 2875, 138, 235, 45, 18,
                    0.62f, -0.52f, 0.37f, 0.84f, 0.32f, 0.07f, 0.03f, 0.45f, 0.16f),
            // Compact SUV: high beltline, 190 mm clearance (reference: Tata Nexon EV).
            new BodyTemplate(BodyType.COMPACT_SUV, 3994, 1811, 1616, 2498, 190, 215, 60, 16,
                    0.63f, -0.60f, 0.36f, 0.86f, 0.24f, 0.05f, 0.02f, 0.45f, 0.18f),
            // SUV: taller and longer (reference: Rivian R1S).
            new BodyTemplate(BodyType.SUV, 5100, 2032, 1838, 3075, 250, 275, 55, 21,
                    0.62f, -0.66f, 0.34f, 0.87f, 0.22f, 0.04f, 0.02f, 0.45f, 0.18f),
            // MPV: longest cabin, near-vertical tail (reference: Toyota Innova Crysta).
            new BodyTemplate(BodyType.MPV, 4735, 1830, 1795, 2750, 178, 215, 60, 17,
                    0.62f, -0.72f, 0.38f, 0.88f, 0.20f, 0.05f, 0.02f, 0.44f, 0.18f),
            // Coupe: low and wide, fast rear screen (reference: Porsche Taycan).
            new BodyTemplate(BodyType.COUPE, 4963, 1966, 1395, 2900, 128, 225, 55, 19,
                    0.60f, -0.45f, 0.26f, 0.82f, 0.36f, 0.09f, 0.06f, 0.46f, 0.14f),
            // Pickup: short cab, long low load bed behind it.
            new BodyTemplate(BodyType.PICKUP, 5295, 1870, 1855, 3095, 190, 265, 60, 18,
                    0.64f, -0.30f, 0.46f, 0.85f, 0.12f, 0.05f, 0.34f, 0.40f, 0.20f),
            // Microcar: very short, tall and narrow (reference: MG Comet EV).
            new BodyTemplate(BodyType.MICROCAR, 2974, 1505, 1640, 2010, 165, 145, 70, 12,
                    0.63f, -0.70f, 0.35f, 0.88f, 0.22f, 0.05f, 0.04f, 0.40f, 0.18f)
    };

    public static BodyTemplate of(BodyType type) {
        for (BodyTemplate template : TEMPLATES) {
            if (template.bodyType == type) {
                return template;
            }
        }
        return TEMPLATES[0];
    }

    public static BodyTemplate[] all() {
        return TEMPLATES.clone();
    }
}
