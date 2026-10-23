package com.amperly.app.vehicle;

import com.google.gson.JsonParser;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Tier 1: the curated profile asset must load, override, and never be required. */
public class CuratedProfileTest {

    @Test
    public void theShippedAssetCuratesEveryCatalogueModel() throws Exception {
        CuratedProfileStore store = store();
        assertEquals(1, store.schemaVersion());
        assertEquals(12, store.size());

        CuratedProfile nexon = store.find("Tata", "Nexon EV");
        assertNotNull(nexon);
        assertEquals("NEXON EV", nexon.badge);
        assertEquals(WheelStyle.SPOKE, nexon.wheelStyle);
        assertTrue(nexon.roofRails);
        assertEquals(0.64f, nexon.beltlineRatio, 0.0001f);
        assertEquals(0x00C6F94C, nexon.accentColor & 0xFFFFFF);
    }

    @Test
    public void visualCurationLeavesResolvedNumbersAndSourceAlone() {
        VehicleSpecs base = VehicleSpecs.builder("Tata", "Nexon EV", BodyType.COMPACT_SUV)
                .dimensionsMm(3994, 1811, 1616)
                .wheelbaseMm(2498)
                .groundClearanceMm(190)
                .tyre(215, 60, 16)
                .confidence(SpecConfidence.HIGH)
                .source("database")
                .build();

        CuratedProfile profile = CuratedProfile.builder("Tata", "Nexon EV")
                .badge("NEXON EV")
                .wheelStyle(WheelStyle.AERO)
                .roofRails(true)
                .build();
        VehicleSpecs applied = profile.apply(base);

        assertEquals(3994, applied.lengthMm);
        assertEquals(SpecConfidence.HIGH, applied.confidence);
        assertEquals("database", applied.source);
        assertEquals(profile, applied.curated);
    }

    @Test
    public void dimensionsInAProfileWinAndAreMarkedCurated() {
        CuratedProfile profile = CuratedProfile.fromJson(JsonParser.parseString(
                "{\"make\":\"Acme\",\"model\":\"Roadster\",\"bodyType\":\"COUPE\","
                        + "\"lengthMm\":4500,\"widthMm\":1900,\"heightMm\":1300,"
                        + "\"wheelbaseMm\":2700,\"groundClearanceMm\":120,"
                        + "\"tyre\":{\"widthMm\":245,\"aspectPercent\":35,\"rimDiameterIn\":20},"
                        + "\"chargePort\":\"REAR_LEFT\",\"badge\":\"ROADSTER\","
                        + "\"proportions\":{\"cabinStartRatio\":-0.5}}")
                .getAsJsonObject());

        VehicleSpecs base = SpecEstimator.estimate("Acme", "Roadster", "");
        VehicleSpecs applied = profile.apply(base);

        assertEquals(BodyType.COUPE, applied.bodyType);
        assertEquals(4500, applied.lengthMm);
        assertEquals(1900, applied.widthMm);
        assertEquals(1300, applied.heightMm);
        assertEquals(2700, applied.wheelbaseMm);
        assertEquals(245, applied.tyreWidthMm);
        assertEquals(20, applied.rimDiameterIn);
        assertEquals(ChargePortLocation.REAR_LEFT, applied.chargePort);
        assertEquals(SpecConfidence.HIGH, applied.confidence);
        assertEquals("curated", applied.source);
        assertEquals(-0.5f, applied.curated.cabinStartRatio, 0.0001f);
    }

    @Test
    public void unknownModelsAndBrokenAssetsAreSafe() {
        CuratedProfileStore store = CuratedProfileStore.fromJson(
                "{\"schemaVersion\":1,\"profiles\":[{\"make\":\"X\",\"model\":\"Y\"}]}");
        assertNotNull(store.find("X", "Y"));
        assertNull(store.find("Nobody", "Nothing"));

        assertEquals(0, CuratedProfileStore.fromJson("not json").size());
        assertEquals(0, CuratedProfileStore.fromJson(null).size());
        assertFalse(CuratedProfileStore.fromJson("{}").size() > 0);
    }

    private static CuratedProfileStore store() throws Exception {
        File asset = new File("src/main/assets/vehicle_profiles.json");
        Assume.assumeTrue("curated profile asset present", asset.exists());
        return CuratedProfileStore.fromJson(
                new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8));
    }
}
