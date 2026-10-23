package com.amperly.app;

import com.amperly.app.ui.CarMesh;
import com.amperly.app.vehicle.BodyTemplate;
import com.amperly.app.vehicle.BodyType;
import com.amperly.app.vehicle.CarModelFitter;
import com.amperly.app.vehicle.CuratedProfile;
import com.amperly.app.vehicle.SpecConfidence;
import com.amperly.app.vehicle.WheelStyle;
import com.amperly.app.vehicle.VehicleGeometry;
import com.amperly.app.vehicle.VehicleSpecs;
import com.amperly.app.vehicle.VehicleSpecDatabase;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The fitted model must carry the real car's proportions. Because the mesh is regenerated from
 * the specs, its bounding box is checked against length, width and height to within 2%.
 */
public class CarMeshTest {

    @Test
    public void everyCatalogueCarKeepsItsRealBoundingBox() throws Exception {
        VehicleSpecDatabase database = database();
        assertTrue("catalogue is seeded", database.catalogue().size() >= 12);
        for (VehicleSpecs specs : database.catalogue()) {
            assertBoxMatches(specs);
            assertEquals(specs.displayName(), SpecConfidence.HIGH, specs.confidence);
        }
    }

    @Test
    public void everyBodyTemplateProducesAUsableMesh() {
        for (BodyTemplate template : BodyTemplate.all()) {
            VehicleSpecs specs = VehicleSpecs.builder("Test", template.bodyType.name(),
                            template.bodyType)
                    .dimensionsMm(template.referenceLengthMm, template.referenceWidthMm,
                            template.referenceHeightMm)
                    .wheelbaseMm(template.referenceWheelbaseMm)
                    .groundClearanceMm(template.referenceGroundClearanceMm)
                    .tyre(template.referenceTyreWidthMm, template.referenceTyreAspectPercent,
                            template.referenceRimDiameterIn)
                    .confidence(SpecConfidence.HIGH)
                    .build();

            assertBoxMatches(specs);

            VehicleGeometry geometry = CarModelFitter.fit(specs);
            CarMesh.Face[] faces = CarMesh.build(geometry);
            assertTrue(template.bodyType.name(), faces.length > 700);

            int wheels = 0;
            int lamps = 0;
            float minY = Float.MAX_VALUE;
            for (CarMesh.Face face : faces) {
                for (int i = 0; i < face.count; i++) {
                    assertTrue(template.bodyType.name(), isFinite(face.x[i]));
                    assertTrue(template.bodyType.name(), isFinite(face.y[i]));
                    assertTrue(template.bodyType.name(), isFinite(face.z[i]));
                    minY = Math.min(minY, face.y[i]);
                }
                if (face.material == CarMesh.MAT_TIRE) {
                    wheels++;
                } else if (face.material == CarMesh.MAT_EMISSIVE) {
                    lamps++;
                }
            }
            // Four wheels, each with a tread band and a sidewall fan.
            assertEquals(template.bodyType.name(), 96, wheels);
            // Two head lamps, one tail bar and the charge flap.
            assertTrue(template.bodyType.name(), lamps >= 4);
            // The tyres rest on the turntable plane.
            assertEquals(template.bodyType.name(), 0f, minY, 0.001f);
        }
    }

    @Test
    public void theTyreSizeDrivesTheWheelDiameter() {
        VehicleSpecs specs = VehicleSpecs.builder("Test", "Tyre", BodyType.HATCHBACK)
                .dimensionsMm(3769, 1677, 1536)
                .wheelbaseMm(2400)
                .groundClearanceMm(165)
                .tyre(175, 65, 14)
                .confidence(SpecConfidence.HIGH)
                .build();
        VehicleGeometry geometry = CarModelFitter.fit(specs);
        // 14" rim + two 65-profile sidewalls: (355.6 + 227.5) / 2 = 291.55 mm radius.
        assertEquals(0.29155f, geometry.wheelRadius, 0.0005f);
        assertTrue(geometry.wheelWidth > 0.17f && geometry.wheelWidth < 0.19f);
    }

    @Test
    public void thePaintComesFromTheResolvedColours() {
        VehicleSpecs specs = VehicleSpecs.builder("Test", "Paint", BodyType.SEDAN)
                .dimensionsMm(4720, 1848, 1442)
                .wheelbaseMm(2875)
                .groundClearanceMm(138)
                .tyre(235, 45, 18)
                .colours(java.util.Collections.singletonList("#8E1D1D"))
                .confidence(SpecConfidence.HIGH)
                .build();
        VehicleGeometry geometry = CarModelFitter.fit(specs);
        assertEquals(0xFF8E1D1D, geometry.paintColor);
        CarMesh.Face[] faces = CarMesh.build(geometry);
        int body = 0;
        for (CarMesh.Face face : faces) {
            if (face.material == CarMesh.MAT_BODY) {
                body++;
            }
        }
        assertTrue(body > 500);
    }

    @Test
    public void curatedWheelsAndRoofRailsStayInsideTheRealBoundingBox() {
        for (BodyTemplate template : BodyTemplate.all()) {
            VehicleSpecs base = VehicleSpecs.builder("Test", template.bodyType.name(),
                            template.bodyType)
                    .dimensionsMm(template.referenceLengthMm, template.referenceWidthMm,
                            template.referenceHeightMm)
                    .wheelbaseMm(template.referenceWheelbaseMm)
                    .groundClearanceMm(template.referenceGroundClearanceMm)
                    .tyre(template.referenceTyreWidthMm, template.referenceTyreAspectPercent,
                            template.referenceRimDiameterIn)
                    .confidence(SpecConfidence.HIGH)
                    .build();

            for (WheelStyle style : WheelStyle.values()) {
                CuratedProfile profile = CuratedProfile.builder("Test", template.bodyType.name())
                        .wheelStyle(style)
                        .roofRails(true)
                        .build();
                VehicleSpecs curated = profile.apply(base);
                assertBoxMatches(curated);

                float maxY = -Float.MAX_VALUE;
                for (CarMesh.Face face : CarMesh.build(CarModelFitter.fit(curated))) {
                    for (int i = 0; i < face.count; i++) {
                        maxY = Math.max(maxY, face.y[i]);
                    }
                }
                assertTrue(template.bodyType + " / " + style,
                        maxY <= template.referenceHeightMm / 1000f + 0.001f);
            }
        }
    }

    private static void assertBoxMatches(VehicleSpecs specs) {
        VehicleGeometry geometry = CarModelFitter.fit(specs);
        CarMesh.Face[] faces = CarMesh.build(geometry);

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (CarMesh.Face face : faces) {
            for (int i = 0; i < face.count; i++) {
                minX = Math.min(minX, face.x[i]);
                maxX = Math.max(maxX, face.x[i]);
                minY = Math.min(minY, face.y[i]);
                maxY = Math.max(maxY, face.y[i]);
                minZ = Math.min(minZ, face.z[i]);
                maxZ = Math.max(maxZ, face.z[i]);
            }
        }

        assertEquals(specs.displayName() + " length", specs.lengthMm / 1000f, maxZ - minZ,
                specs.lengthMm / 1000f * 0.02f);
        assertEquals(specs.displayName() + " width", specs.widthMm / 1000f, maxX - minX,
                specs.widthMm / 1000f * 0.02f);
        assertEquals(specs.displayName() + " height", specs.heightMm / 1000f, maxY - minY,
                specs.heightMm / 1000f * 0.02f);
    }

    private static VehicleSpecDatabase database() throws Exception {
        File asset = new File("src/main/assets/vehicle_specs.json");
        Assume.assumeTrue("spec database asset present", asset.exists());
        String json = new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8);
        return VehicleSpecDatabase.fromJson(json);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
