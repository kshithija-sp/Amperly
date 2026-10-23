package com.amperly.app.ui;

import com.amperly.app.vehicle.ChargePortLocation;
import com.amperly.app.vehicle.VehicleGeometry;
import com.amperly.app.vehicle.WheelStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry for the 3D vehicle model: a closed lofted body, a raked greenhouse, four wheels,
 * the lamps and the charge flap, expressed in metres around the car's own origin with +z at the
 * nose.
 *
 * <p>Everything is derived from {@link VehicleGeometry}, which the fitter builds from the real
 * specs. Nothing is a scaled copy of another car: the body is lofted at the real length, width
 * and height, the wheels are cylinders of the real tyre radius and width at the real axle
 * positions, and the glass and lamps are rebuilt at their own proportions. The result is that
 * the mesh's bounding box equals the real car's dimensions.
 *
 * <p>No Android types are used here, so the shape can be checked by plain JVM tests.
 */
public final class CarMesh {

    public static final int GLASS = 0x5C7060;
    public static final int TIRE = 0x364037;
    public static final int RIM = 0xB0BEAC;
    public static final int ACCENT = 0xC6F94C;
    public static final int LIGHT = 0xF7FFD1;

    public static final int MAT_BODY = 0;
    public static final int MAT_GLASS = 1;
    public static final int MAT_EMISSIVE = 2;
    public static final int MAT_TIRE = 3;
    public static final int MAT_RIM = 4;

    /** One flat-shaded polygon with three or four corners. */
    public static final class Face {
        public final float[] x = new float[4];
        public final float[] y = new float[4];
        public final float[] z = new float[4];
        public final int count;
        public final int color;
        public final int material;

        Face(int count, int color, int material) {
            this.count = count;
            this.color = color;
            this.material = material;
        }
    }

    private CarMesh() {
    }

    public static Face[] build(VehicleGeometry geometry) {
        return new Builder(geometry).build();
    }

    /** Centre of the charge flap, for the pulse the charging flow draws over it. */
    public static float[] chargePortAnchor(VehicleGeometry geometry) {
        float halfLength = geometry.length / 2f;
        float halfWidth = geometry.width / 2f * 0.86f;
        float y = geometry.beltline * 0.7f;
        switch (geometry.chargePort) {
            case FRONT_LEFT:
                return new float[]{-halfWidth, y, halfLength * 0.52f};
            case FRONT_RIGHT:
                return new float[]{halfWidth, y, halfLength * 0.52f};
            case FRONT_CENTRE:
                return new float[]{0f, y, halfLength};
            case REAR_LEFT:
                return new float[]{-halfWidth, y, -halfLength * 0.58f};
            case REAR_RIGHT:
                return new float[]{halfWidth, y, -halfLength * 0.58f};
            case REAR_CENTRE:
            default:
                return new float[]{0f, y, -halfLength};
        }
    }

    private static final class Builder {
        private final VehicleGeometry car;
        private final float halfLength;
        private final float halfWidth;
        private final int bodyColor;
        private final int bodyTopColor;
        private final int bodyDimColor;
        private final int accentColor;
        private final List<Face> faces = new ArrayList<>();

        Builder(VehicleGeometry car) {
            this.car = car;
            this.halfLength = car.length / 2f;
            // The body is a couple of centimetres narrower than the track, so the tyres are the
            // widest part of the car and the bounding box matches the real width.
            this.halfWidth = Math.max(0.5f, car.width / 2f - 0.015f);
            this.bodyColor = car.paintColor;
            this.bodyTopColor = tint(car.paintColor, 1.08f);
            this.bodyDimColor = tint(car.paintColor, 0.64f);
            this.accentColor = car.accentColor != 0 ? car.accentColor : ACCENT;
        }

        Face[] build() {
            buildBody();
            buildCabin();
            buildWheels();
            buildLights();
            buildChargeFlap();
            if (car.roofRails) {
                buildRoofRails();
            }
            return faces.toArray(new Face[0]);
        }

        // ------------------------------------------------------------------ body

        private void buildBody() {
            int stations = 30;
            int ring = 18;
            float[] ring0 = new float[3];
            float[] ring1 = new float[3];

            for (int i = 0; i < stations; i++) {
                float z0 = -halfLength + 2f * halfLength * i / stations;
                float z1 = -halfLength + 2f * halfLength * (i + 1) / stations;
                Section s0 = sectionAt(z0);
                Section s1 = sectionAt(z1);

                for (int j = 0; j < ring; j++) {
                    double theta0 = Math.PI * 2.0 * j / ring;
                    double theta1 = Math.PI * 2.0 * (j + 1) / ring;

                    ringPoint(s0, z0, theta0, ring0);
                    float x0 = ring0[0], y0 = ring0[1];
                    ringPoint(s0, z0, theta1, ring1);
                    float x1 = ring1[0], y1 = ring1[1];
                    ringPoint(s1, z1, theta1, ring0);
                    float x2 = ring0[0], y2 = ring0[1];
                    ringPoint(s1, z1, theta0, ring1);
                    float x3 = ring1[0], y3 = ring1[1];

                    float sin = (float) Math.sin((theta0 + theta1) / 2.0);
                    int color = sin > 0.55f ? bodyTopColor : (sin < -0.5f ? bodyDimColor : bodyColor);
                    addQuad(x0, y0, z0, x1, y1, z0, x2, y2, z1, x3, y3, z1, color, MAT_BODY);
                }
            }

            addCap(-halfLength, bodyDimColor);
            addCap(halfLength, bodyTopColor);
        }

        private void addCap(float z, int color) {
            int ring = 18;
            float[] point = new float[3];
            Section section = sectionAt(z);
            for (int j = 0; j < ring; j++) {
                double theta0 = Math.PI * 2.0 * j / ring;
                double theta1 = Math.PI * 2.0 * (j + 1) / ring;
                ringPoint(section, z, theta0, point);
                float x0 = point[0];
                float y0 = point[1];
                ringPoint(section, z, theta1, point);
                addTriangle(0f, section.centerY, z, x0, y0, z, point[0], point[1], z,
                        color, MAT_BODY);
            }
        }

        /** Closed loft of the lower body: superellipse rings swept from tail to nose. */
        private Section sectionAt(float z) {
            float t = z / halfLength;
            float absT = Math.abs(t);
            float taper = 1f - car.endTaper * (float) Math.pow(absT, 2.2);
            float bumper = 1f - 0.5f * (float) Math.pow(absT, 8.0);
            float width = halfWidth * Math.max(0.12f, taper * bumper);
            float top = topAt(z);
            float bottom = Math.max(car.groundClearance, archHeightAt(z));

            Section section = new Section();
            section.width = width;
            section.centerY = (top + bottom) / 2f;
            section.height = Math.max(0.02f, (top - bottom) / 2f);
            section.exponent = 0.62f;
            return section;
        }

        private float topAt(float z) {
            float t = z / halfLength;
            float drop = t > 0f
                    ? car.hoodDrop * t * t
                    : car.rearDrop * t * t;
            return Math.max(car.groundClearance + 0.1f, car.beltline - drop);
        }

        /** Height of the wheel-arch opening at z, or 0 away from the axles. */
        private float archHeightAt(float z) {
            float arch = Math.max(0f, car.archRadius);
            float bottom = 0f;
            float[] axles = {car.frontAxleZ, car.rearAxleZ};
            for (float axle : axles) {
                float distance = Math.abs(z - axle);
                if (distance < arch) {
                    float height = car.wheelRadius + (float) Math.sqrt(arch * arch - distance * distance);
                    bottom = Math.max(bottom, Math.min(height, car.beltline - 0.04f));
                }
            }
            return bottom;
        }

        // ------------------------------------------------------------------ cabin

        private void buildCabin() {
            int stations = 16;
            int ring = 14;
            float[] point0 = new float[3];
            float[] point1 = new float[3];

            for (int i = 0; i < stations; i++) {
                float u0 = (float) i / stations;
                float u1 = (float) (i + 1) / stations;
                float z0 = car.cabinStartZ + (car.cabinEndZ - car.cabinStartZ) * u0;
                float z1 = car.cabinStartZ + (car.cabinEndZ - car.cabinStartZ) * u1;
                Section s0 = cabinSectionAt(z0, u0);
                Section s1 = cabinSectionAt(z1, u1);

                for (int j = 0; j < ring; j++) {
                    double theta0 = Math.PI * 2.0 * j / ring;
                    double theta1 = Math.PI * 2.0 * (j + 1) / ring;

                    ringPoint(s0, z0, theta0, point0);
                    float x0 = point0[0], y0 = point0[1];
                    ringPoint(s0, z0, theta1, point1);
                    float x1 = point1[0], y1 = point1[1];
                    ringPoint(s1, z1, theta1, point0);
                    float x2 = point0[0], y2 = point0[1];
                    ringPoint(s1, z1, theta0, point1);
                    float x3 = point1[0], y3 = point1[1];

                    boolean roof = Math.sin((theta0 + theta1) / 2.0) > 0.62f;
                    // A, B and C pillars stay in body colour so the glass reads as windows.
                    boolean pillar = u0 < 0.07f || u1 > 0.93f || (u0 > 0.44f && u1 < 0.57f);
                    boolean glass = !roof && !pillar;
                    addQuad(x0, y0, z0, x1, y1, z0, x2, y2, z1, x3, y3, z1,
                            roof ? bodyTopColor : (glass ? GLASS : bodyColor),
                            glass ? MAT_GLASS : MAT_BODY);
                }
            }

            addCabinCap(car.cabinStartZ, 0f);
            addCabinCap(car.cabinEndZ, 1f);
        }

        private Section cabinSectionAt(float z, float u) {
            Section body = sectionAt(z);
            float belt = Math.min(body.centerY + body.height, car.beltline);
            float bump = (float) Math.pow(Math.max(0f, Math.sin(Math.PI * u)), 0.7);
            float roof = belt + 0.02f + Math.max(0.02f, car.roof - belt - 0.02f) * bump;

            Section section = new Section();
            section.width = body.width * car.cabinWidthRatio;
            section.centerY = (belt + roof) / 2f;
            section.height = Math.max(0.01f, (roof - belt) / 2f);
            section.exponent = 0.7f;
            return section;
        }

        private void addCabinCap(float z, float u) {
            int ring = 14;
            float[] point = new float[3];
            Section section = cabinSectionAt(z, u);
            for (int j = 0; j < ring; j++) {
                double theta0 = Math.PI * 2.0 * j / ring;
                double theta1 = Math.PI * 2.0 * (j + 1) / ring;
                ringPoint(section, z, theta0, point);
                float x0 = point[0];
                float y0 = point[1];
                ringPoint(section, z, theta1, point);
                addTriangle(0f, section.centerY, z, x0, y0, z, point[0], point[1], z,
                        GLASS, MAT_GLASS);
            }
        }

        // ------------------------------------------------------------------ wheels

        private void buildWheels() {
            for (int side = -1; side <= 1; side += 2) {
                addWheel(side, car.frontAxleZ);
                addWheel(side, car.rearAxleZ);
            }
        }

        private void addWheel(int side, float axleZ) {
            int segments = 12;
            float radius = car.wheelRadius;
            float centerY = radius;
            float centerZ = axleZ;
            float outerX = side * (car.wheelCentreX + car.wheelWidth / 2f);
            float innerX = side * (car.wheelCentreX - car.wheelWidth / 2f);

            float[] wy = new float[segments];
            float[] wz = new float[segments];
            for (int i = 0; i < segments; i++) {
                double angle = Math.PI * 2.0 * i / segments;
                wy[i] = centerY + (float) Math.sin(angle) * radius;
                wz[i] = centerZ + (float) Math.cos(angle) * radius;
            }

            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                addQuad(innerX, wy[i], wz[i], innerX, wy[next], wz[next],
                        outerX, wy[next], wz[next], outerX, wy[i], wz[i], TIRE, MAT_TIRE);
            }

            // Tyre sidewall, rim face and hub sit on the outboard face of the wheel. The
            // rim/hub offsets are deliberately smaller than the tyre's half-width budget, so
            // the overall width stays the manufacturer's figure instead of growing by 30 mm.
            float rimX = outerX + side * 0.001f;
            float hubX = outerX + side * 0.0015f;
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                addTriangle(outerX, centerY, centerZ, outerX, wy[i], wz[i],
                        outerX, wy[next], wz[next], TIRE, MAT_TIRE);
            }
            buildWheelFace(rimX, hubX, centerY, centerZ, wy, wz, segments);
        }

        /** The visible wheel face: five spokes, a flat aero disc, or a steel-style disc. */
        private void buildWheelFace(float rimX, float hubX, float centerY, float centerZ,
                                    float[] wy, float[] wz, int segments) {
            if (car.wheelStyle == WheelStyle.SPOKE) {
                int spokes = 5;
                for (int spoke = 0; spoke < spokes; spoke++) {
                    double angle = Math.PI * 2.0 * spoke / spokes;
                    double half = Math.PI / spokes * 0.34;
                    float innerY0 = centerY + (float) Math.sin(angle - half) * 0.09f;
                    float innerZ0 = centerZ + (float) Math.cos(angle - half) * 0.09f;
                    float innerY1 = centerY + (float) Math.sin(angle + half) * 0.09f;
                    float innerZ1 = centerZ + (float) Math.cos(angle + half) * 0.09f;
                    float outerY0 = centerY + (float) Math.sin(angle - half) * car.wheelRadius * 0.74f;
                    float outerZ0 = centerZ + (float) Math.cos(angle - half) * car.wheelRadius * 0.74f;
                    float outerY1 = centerY + (float) Math.sin(angle + half) * car.wheelRadius * 0.74f;
                    float outerZ1 = centerZ + (float) Math.cos(angle + half) * car.wheelRadius * 0.74f;
                    addQuad(rimX, innerY0, innerZ0, rimX, outerY0, outerZ0,
                            rimX, outerY1, outerZ1, rimX, innerY1, innerZ1, RIM, MAT_RIM);
                }
                addDisc(hubX, centerY, centerZ, wy, wz, segments, 0.18f, accentColor, MAT_BODY);
                return;
            }

            float disc = car.wheelStyle == WheelStyle.AERO ? 0.8f : 0.62f;
            addDisc(rimX, centerY, centerZ, wy, wz, segments, disc, RIM, MAT_RIM);
            addDisc(hubX, centerY, centerZ, wy, wz, segments,
                    car.wheelStyle == WheelStyle.AERO ? 0.1f : 0.2f, accentColor, MAT_BODY);
        }

        private void addDisc(float x, float centerY, float centerZ, float[] wy, float[] wz,
                             int segments, float scale, int color, int material) {
            for (int i = 0; i < segments; i++) {
                int next = (i + 1) % segments;
                addTriangle(x, centerY, centerZ,
                        x, centerY + (wy[i] - centerY) * scale,
                        centerZ + (wz[i] - centerZ) * scale,
                        x, centerY + (wy[next] - centerY) * scale,
                        centerZ + (wz[next] - centerZ) * scale,
                        color, material);
            }
        }

        // ------------------------------------------------------------------ lights & flap

        private void buildLights() {
            float noseWidth = halfWidth * 0.82f;
            float lampY = car.lampHeight;
            float lampZ = halfLength - 0.004f;

            for (int side = -1; side <= 1; side += 2) {
                float x0 = side * noseWidth * 0.3f;
                float x1 = side * noseWidth * 0.72f;
                addQuad(Math.min(x0, x1), lampY, lampZ, Math.max(x0, x1), lampY, lampZ,
                        Math.max(x0, x1), lampY + 0.09f, lampZ,
                        Math.min(x0, x1), lampY + 0.09f, lampZ, LIGHT, MAT_EMISSIVE);
            }

            float tailZ = -halfLength + 0.004f;
            float tailWidth = halfWidth * 0.72f;
            float tailY = car.lampHeight;
            addQuad(-tailWidth, tailY, tailZ, tailWidth, tailY, tailZ,
                    tailWidth, tailY + 0.055f, tailZ, -tailWidth, tailY + 0.055f, tailZ,
                    ACCENT, MAT_EMISSIVE);
        }

        private void buildChargeFlap() {
            float[] anchor = chargePortAnchor(car);
            float size = 0.085f;
            float x = anchor[0];
            float y = anchor[1];
            float z = anchor[2];
            if (Math.abs(x) > 0.05f) {
                float faceX = x > 0 ? x + 0.004f : x - 0.004f;
                addQuad(faceX, y - size, z - size, faceX, y - size, z + size,
                        faceX, y + size, z + size, faceX, y + size, z - size,
                        accentColor, MAT_EMISSIVE);
            } else {
                float faceZ = z > 0 ? z + 0.002f : z - 0.002f;
                addQuad(-size, y - size, faceZ, size, y - size, faceZ,
                        size, y + size, faceZ, -size, y + size, faceZ,
                        accentColor, MAT_EMISSIVE);
            }
        }

        /** Two low-profile rails on the roof, inset so the overall height stays the real height. */
        private void buildRoofRails() {
            float railHalf = Math.max(0.008f, car.width * 0.006f);
            float railX = car.width / 2f * car.cabinWidthRatio * 0.8f;
            float top = car.height;
            float bottom = car.height - Math.max(0.02f, car.height * 0.016f);
            float z0 = car.cabinStartZ + Math.max(0.12f, car.length * 0.04f);
            float z1 = car.cabinEndZ - Math.max(0.12f, car.length * 0.04f);
            if (z1 <= z0) {
                return;
            }
            for (int side = -1; side <= 1; side += 2) {
                float x0 = side * railX - railHalf;
                float x1 = side * railX + railHalf;
                // Top, bottom and the two long sides; the ends are left open, as on a real rail.
                addQuad(x0, top, z0, x1, top, z0, x1, top, z1, x0, top, z1,
                        bodyTopColor, MAT_BODY);
                addQuad(x0, bottom, z0, x0, bottom, z1, x1, bottom, z1, x1, bottom, z0,
                        bodyDimColor, MAT_BODY);
                addQuad(x0, bottom, z0, x0, top, z0, x0, top, z1, x0, bottom, z1,
                        bodyColor, MAT_BODY);
                addQuad(x1, bottom, z0, x1, bottom, z1, x1, top, z1, x1, top, z0,
                        bodyColor, MAT_BODY);
            }
        }

        // ------------------------------------------------------------------ primitives

        private void addQuad(float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             int color, int material) {
            Face face = new Face(4, color, material);
            face.x[0] = x0;
            face.y[0] = y0;
            face.z[0] = z0;
            face.x[1] = x1;
            face.y[1] = y1;
            face.z[1] = z1;
            face.x[2] = x2;
            face.y[2] = y2;
            face.z[2] = z2;
            face.x[3] = x3;
            face.y[3] = y3;
            face.z[3] = z3;
            faces.add(face);
        }

        private void addTriangle(float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, int color, int material) {
            Face face = new Face(3, color, material);
            face.x[0] = x0;
            face.y[0] = y0;
            face.z[0] = z0;
            face.x[1] = x1;
            face.y[1] = y1;
            face.z[1] = z1;
            face.x[2] = x2;
            face.y[2] = y2;
            face.z[2] = z2;
            faces.add(face);
        }
    }

    private static int tint(int color, float factor) {
        int r = Math.min(255, Math.round(((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((color & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static final class Section {
        float width;
        float centerY;
        float height;
        float exponent;
    }

    private static void ringPoint(Section section, float z, double theta, float[] out) {
        out[0] = section.width * signPow(Math.cos(theta), section.exponent);
        out[1] = section.centerY + section.height * signPow(Math.sin(theta), section.exponent);
        out[2] = z;
    }

    private static float signPow(double value, float exponent) {
        double magnitude = Math.pow(Math.abs(value), exponent);
        return (float) (value < 0 ? -magnitude : magnitude);
    }
}
