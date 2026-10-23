package com.amperly.app.vehicle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** The Vehicle Databases YMMT payload maps into the app's spec contract. */
public class VehicleDatabasesProviderTest {

    private static final String AUDI_Q8 = "{\n"
            + "  \"status\": \"success\",\n"
            + "  \"data\": {\n"
            + "    \"year\": 2024,\n"
            + "    \"make\": \"Audi\",\n"
            + "    \"model\": \"Q8\",\n"
            + "    \"trim\": \"55 Premium Plus\",\n"
            + "    \"style\": \"4dr All Wheel Drive quattro Sport Utility\",\n"
            + "    \"vehicle\": { \"body_type\": \"SUV\", \"doors\": \"4\" },\n"
            + "    \"dimensions\": [\n"
            + "      { \"exterior\": [\n"
            + "        { \"overall_length\": [\n"
            + "            { \"value\": \"4,993.64\", \"unit\": \"mm\" },\n"
            + "            { \"value\": \"196.6\", \"unit\": \"in\" } ] },\n"
            + "        { \"overall_width\": [\n"
            + "            { \"value\": \"1,993.9\", \"unit\": \"mm\" } ] },\n"
            + "        { \"overall_height\": [\n"
            + "            { \"value\": \"1,706.88\", \"unit\": \"mm\" } ] },\n"
            + "        { \"wheelbase\": [\n"
            + "            { \"value\": \"2,994.66\", \"unit\": \"mm\" } ] }\n"
            + "      ] },\n"
            + "      { \"ground_clearance\": [\n"
            + "        { \"ground_clearance_min\": [\n"
            + "            { \"value\": \"147.32\", \"unit\": \"mm\" } ] } ] }\n"
            + "    ]\n"
            + "  }\n"
            + "}";

    @Test
    public void aYmmtResponseBecomesHighConfidenceSpecs() {
        VehicleSpecs specs = VehicleDatabasesProvider.parse(AUDI_Q8, "Audi", "Q8", "55 Premium Plus");

        assertNotNull(specs);
        assertEquals("Audi", specs.make);
        assertEquals("Q8", specs.model);
        assertEquals("55 Premium Plus", specs.variant);
        assertEquals(BodyType.SUV, specs.bodyType);
        assertEquals(4994, specs.lengthMm);
        assertEquals(1994, specs.widthMm);
        assertEquals(1707, specs.heightMm);
        assertEquals(2995, specs.wheelbaseMm);
        assertEquals(147, specs.groundClearanceMm);
        assertEquals(SpecConfidence.HIGH, specs.confidence);
        assertEquals("api", specs.source);
    }

    @Test
    public void inchesAreConvertedWhenMillimetresAreAbsent() {
        String json = "{\"status\":\"success\",\"data\":{\"make\":\"Ford\",\"model\":\"Ranger\","
                + "\"trim\":\"XLT\",\"vehicle\":{\"body_type\":\"Pickup Truck\"},"
                + "\"dimensions\":[{\"exterior\":["
                + "{\"overall_length\":[{\"value\":\"210.8\",\"unit\":\"in\"}]},"
                + "{\"overall_width\":[{\"value\":\"73.3\",\"unit\":\"in\"}]},"
                + "{\"overall_height\":[{\"value\":\"75.1\",\"unit\":\"in\"}]},"
                + "{\"wheelbase\":[{\"value\":\"126.8\",\"unit\":\"in\"}]}]}]}}";

        VehicleSpecs specs = VehicleDatabasesProvider.parse(json, "Ford", "Ranger", "XLT");

        assertNotNull(specs);
        assertEquals(BodyType.PICKUP, specs.bodyType);
        assertEquals(5354, specs.lengthMm);
        assertEquals(1862, specs.widthMm);
        assertEquals(1908, specs.heightMm);
        assertEquals(3221, specs.wheelbaseMm);
    }

    @Test
    public void anUnknownBodyStyleFallsBackToTheNameEstimator() {
        String json = "{\"status\":\"success\",\"data\":{\"make\":\"Tata\",\"model\":\"Nexon EV\","
                + "\"trim\":\"Empowered+\",\"dimensions\":[{\"exterior\":["
                + "{\"overall_length\":[{\"value\":\"3994\",\"unit\":\"mm\"}]},"
                + "{\"overall_width\":[{\"value\":\"1811\",\"unit\":\"mm\"}]},"
                + "{\"overall_height\":[{\"value\":\"1616\",\"unit\":\"mm\"}]}]}]}}";

        VehicleSpecs specs = VehicleDatabasesProvider.parse(json, "Tata", "Nexon EV", "Empowered+");

        assertNotNull(specs);
        assertEquals(BodyType.COMPACT_SUV, specs.bodyType);
        assertEquals(SpecConfidence.MEDIUM, specs.confidence);
    }

    @Test
    public void errorsAndPartialPayloadsAreRejected() {
        assertNull(VehicleDatabasesProvider.parse(null, "Audi", "Q8", "55"));
        assertNull(VehicleDatabasesProvider.parse("", "Audi", "Q8", "55"));
        assertNull(VehicleDatabasesProvider.parse("not json", "Audi", "Q8", "55"));
        assertNull(VehicleDatabasesProvider.parse(
                "{\"status\":\"error\",\"message\":\"No vehicle found\"}",
                "Audi", "Q8", "55"));
        assertNull(VehicleDatabasesProvider.parse(
                "{\"status\":\"success\",\"data\":{\"make\":\"Audi\",\"model\":\"Q8\","
                        + "\"dimensions\":[{\"overall_length\":[{\"value\":\"4994\",\"unit\":\"mm\"}]}]}}",
                "Audi", "Q8", "55"));
    }
}
