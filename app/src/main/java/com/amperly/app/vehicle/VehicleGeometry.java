package com.amperly.app.vehicle;

/**
 * The fitted car in metres: real length, width, height, wheelbase, clearance and tyre size,
 * with the template's proportions applied to the glasshouse, bonnet and arches.
 *
 * <p>This is what {@link com.amperly.app.ui.CarMesh} turns into polygons. Because the mesh is
 * rebuilt from these numbers, its bounding box is the real car's bounding box to the millimetre.
 */
public final class VehicleGeometry {

    public final BodyType bodyType;
    public final float length;
    public final float width;
    public final float height;
    public final float wheelbase;
    public final float groundClearance;
    /** Top of the lower body shell (the beltline). */
    public final float beltline;
    /** Overall roof height, equal to {@link #height}. */
    public final float roof;

    public final float frontOverhang;
    public final float rearOverhang;
    public final float frontAxleZ;
    public final float rearAxleZ;
    public final float cabinStartZ;
    public final float cabinEndZ;

    public final float wheelRadius;
    public final float wheelWidth;
    /** Centre of each wheel along x, so the tyre sits flush with the flank. */
    public final float wheelCentreX;
    public final float archRadius;

    public final float cabinWidthRatio;
    public final float endTaper;
    public final float hoodDrop;
    public final float rearDrop;
    public final float lampHeight;

    public final int paintColor;
    public final ChargePortLocation chargePort;
    /** Curated wheel face; template-fitted cars use the five-spoke default. */
    public final WheelStyle wheelStyle;
    public final boolean roofRails;
    /** Curated highlight colour, or 0 for the scene default. */
    public final int accentColor;

    VehicleGeometry(BodyType bodyType, float length, float width, float height, float wheelbase,
                    float groundClearance, float beltline, float roof, float frontOverhang,
                    float rearOverhang, float frontAxleZ, float rearAxleZ, float cabinStartZ,
                    float cabinEndZ, float wheelRadius, float wheelWidth, float wheelCentreX,
                    float archRadius, float cabinWidthRatio, float endTaper, float hoodDrop,
                    float rearDrop, float lampHeight, int paintColor,
                    ChargePortLocation chargePort, WheelStyle wheelStyle, boolean roofRails,
                    int accentColor) {
        this.bodyType = bodyType;
        this.length = length;
        this.width = width;
        this.height = height;
        this.wheelbase = wheelbase;
        this.groundClearance = groundClearance;
        this.beltline = beltline;
        this.roof = roof;
        this.frontOverhang = frontOverhang;
        this.rearOverhang = rearOverhang;
        this.frontAxleZ = frontAxleZ;
        this.rearAxleZ = rearAxleZ;
        this.cabinStartZ = cabinStartZ;
        this.cabinEndZ = cabinEndZ;
        this.wheelRadius = wheelRadius;
        this.wheelWidth = wheelWidth;
        this.wheelCentreX = wheelCentreX;
        this.archRadius = archRadius;
        this.cabinWidthRatio = cabinWidthRatio;
        this.endTaper = endTaper;
        this.hoodDrop = hoodDrop;
        this.rearDrop = rearDrop;
        this.lampHeight = lampHeight;
        this.paintColor = paintColor;
        this.chargePort = chargePort;
        this.wheelStyle = wheelStyle == null ? WheelStyle.SPOKE : wheelStyle;
        this.roofRails = roofRails;
        this.accentColor = accentColor;
    }

    /** Steel wheels to wheel-arch: how much of the overall height is body above the arches. */
    public float bodySideHeight() {
        return Math.max(0.05f, beltline - groundClearance);
    }

    public String lengthLabel() {
        return Math.round(length * 1000f) + " mm L";
    }

    public String widthLabel() {
        return Math.round(width * 1000f) + " mm W";
    }

    public String heightLabel() {
        return Math.round(height * 1000f) + " mm H";
    }
}
