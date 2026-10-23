package com.amperly.app.vehicle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Tier 3(b): the adapter maps the normalised contract without a network or an API key. */
public class RemoteSpecProviderTest {

    @Test
    public void aNestedVehicleIsMappedToResolvedSpecs() {
        String json = "{\"vehicles\":[{"
                + "\"make\":\"Tata\",\"model\":\"Nexon EV\",\"variant\":\"Empowered+\","
                + "\"bodyType\":\"COMPACT_SUV\",\"dimensionsMm\":{"
                + "\"lengthMm\":3994,\"widthMm\":1811,\"heightMm\":1616,\"wheelbaseMm\":2498,"
                + "\"groundClearanceMm\":190},"
                + "\"tyre\":{\"widthMm\":215,\"aspectPercent\":60,\"rimDiameterIn\":16},"
                + "\"chargePort\":\"FRONT_CENTRE\",\"colours\":[\"#E9EFE3\"],"
                + "\"confidence\":\"HIGH\"}]}";

        VehicleSpecs specs = RemoteSpecProvider.parse(json, "Tata", "Nexon EV", "Empowered+");

        assertTrue(specs != null);
        assertEquals("Tata", specs.make);
        assertEquals("Nexon EV", specs.model);
        assertEquals("Empowered+", specs.variant);
        assertEquals(BodyType.COMPACT_SUV, specs.bodyType);
        assertEquals(3994, specs.lengthMm);
        assertEquals(1811, specs.widthMm);
        assertEquals(1616, specs.heightMm);
        assertEquals(2498, specs.wheelbaseMm);
        assertEquals(215, specs.tyreWidthMm);
        assertEquals(SpecConfidence.HIGH, specs.confidence);
        assertEquals("api", specs.source);
        assertEquals("#E9EFE3", specs.colours.get(0));
    }

    @Test
    public void aFlatArrayAndInferredBodyTypeAreAccepted() {
        String json = "[{\"lengthMm\":4700,\"widthMm\":1850,\"heightMm\":1450,"
                + "\"wheelbaseMm\":2800,\"confidence\":\"MEDIUM\"}]";

        VehicleSpecs specs = RemoteSpecProvider.parse(json, "Acme", "Roadster", "");

        assertTrue(specs != null);
        assertEquals(BodyType.COUPE, specs.bodyType);
        assertEquals(SpecConfidence.MEDIUM, specs.confidence);
    }

    @Test
    public void partialOrMalformedResponsesFallThrough() {
        assertNull(RemoteSpecProvider.parse(null, "Tata", "Nexon EV", ""));
        assertNull(RemoteSpecProvider.parse("", "Tata", "Nexon EV", ""));
        assertNull(RemoteSpecProvider.parse("not json", "Tata", "Nexon EV", ""));
        assertNull(RemoteSpecProvider.parse("{\"vehicles\":[]}", "Tata", "Nexon EV", ""));
        assertNull(RemoteSpecProvider.parse("{\"lengthMm\":3994}", "Tata", "Nexon EV", ""));
    }
}
