package com.amperly.app.vehicle;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** The resolver's on-disk cache must round-trip every field it needs to rebuild the model. */
public class VehicleSpecsJsonTest {

    @Test
    public void aCachedSpecComesBackIdentical() {
        VehicleSpecs original = VehicleSpecs.builder("Tata", "Nexon EV", BodyType.COMPACT_SUV)
                .variant("Empowered+")
                .dimensionsMm(3994, 1811, 1616)
                .wheelbaseMm(2498)
                .groundClearanceMm(190)
                .tyre(215, 60, 16)
                .chargePort(ChargePortLocation.FRONT_CENTRE)
                .colours(Arrays.asList("#E9EFE3", "#1B211D"))
                .confidence(SpecConfidence.HIGH)
                .source("database")
                .build();

        VehicleSpecs decoded = VehicleSpecsJson.decode(VehicleSpecsJson.encode(original));

        assertNotNull(decoded);
        assertEquals(original.make, decoded.make);
        assertEquals(original.model, decoded.model);
        assertEquals(original.variant, decoded.variant);
        assertEquals(original.bodyType, decoded.bodyType);
        assertEquals(original.lengthMm, decoded.lengthMm);
        assertEquals(original.widthMm, decoded.widthMm);
        assertEquals(original.heightMm, decoded.heightMm);
        assertEquals(original.wheelbaseMm, decoded.wheelbaseMm);
        assertEquals(original.groundClearanceMm, decoded.groundClearanceMm);
        assertEquals(original.tyreWidthMm, decoded.tyreWidthMm);
        assertEquals(original.tyreAspectPercent, decoded.tyreAspectPercent);
        assertEquals(original.rimDiameterIn, decoded.rimDiameterIn);
        assertEquals(original.chargePort, decoded.chargePort);
        assertEquals(original.colours, decoded.colours);
        assertEquals(original.confidence, decoded.confidence);
        assertEquals(original.source, decoded.source);
        assertEquals(original.key(), decoded.key());
    }

    @Test
    public void curationSurvivesTheDiskCache() {
        CuratedProfile profile = CuratedProfile.builder("Tata", "Nexon EV")
                .badge("NEXON EV")
                .wheelStyle(WheelStyle.AERO)
                .roofRails(true)
                .accentColor(0xFFC6F94C)
                .beltlineRatio(0.64f)
                .cabinStartRatio(-0.6f)
                .build();
        VehicleSpecs original = VehicleSpecs.builder("Tata", "Nexon EV", BodyType.COMPACT_SUV)
                .dimensionsMm(3994, 1811, 1616)
                .wheelbaseMm(2498)
                .groundClearanceMm(190)
                .tyre(215, 60, 16)
                .confidence(SpecConfidence.HIGH)
                .source("curated")
                .curated(profile)
                .build();

        VehicleSpecs decoded = VehicleSpecsJson.decode(VehicleSpecsJson.encode(original));

        assertNotNull(decoded);
        assertNotNull(decoded.curated);
        assertEquals("NEXON EV", decoded.curated.badge);
        assertEquals(WheelStyle.AERO, decoded.curated.wheelStyle);
        assertEquals(true, decoded.curated.roofRails);
        assertEquals(0xFFC6F94C, decoded.curated.accentColor);
        assertEquals(0.64f, decoded.curated.beltlineRatio, 0.0001f);
        assertEquals(-0.6f, decoded.curated.cabinStartRatio, 0.0001f);
        assertEquals("curated", decoded.source);
    }

    @Test
    public void corruptCacheEntriesAreIgnored() {
        assertNull(VehicleSpecsJson.decode(null));
        assertNull(VehicleSpecsJson.decode(""));
        assertNull(VehicleSpecsJson.decode("not json"));
        assertNull(VehicleSpecsJson.decode("{\"bodyType\":\"SEDAN\"}"));
    }
}
