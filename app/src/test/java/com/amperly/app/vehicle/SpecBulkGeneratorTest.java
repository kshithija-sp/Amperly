package com.amperly.app.vehicle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bulk tool must never present an estimate as verified. */
public class SpecBulkGeneratorTest {

    private static final String DB = "{\"schemaVersion\":1,\"vehicles\":[{"
            + "\"make\":\"Tata\",\"model\":\"Nexon EV\",\"bodyType\":\"COMPACT_SUV\","
            + "\"lengthMm\":3994,\"widthMm\":1811,\"heightMm\":1616,"
            + "\"wheelbaseMm\":2498,\"groundClearanceMm\":190,"
            + "\"tyre\":{\"widthMm\":215,\"aspectPercent\":60,\"rimDiameterIn\":16}}]}";

    @Test
    public void coveredCandidatesAreNotRegeneratedAndEstimatesAreFlagged() {
        VehicleSpecDatabase database = VehicleSpecDatabase.fromJson(DB);
        List<SpecBulkGenerator.Candidate> candidates = new ArrayList<>();
        candidates.add(candidate("Tata", "Nexon EV", "Empowered+"));
        candidates.add(candidate("Tata", "Curvv EV", "Accomplished+"));

        List<SpecBulkGenerator.Generated> generated =
                SpecBulkGenerator.generate(candidates, database);

        assertEquals(2, generated.size());
        assertTrue(generated.get(0).covered);
        assertFalse(generated.get(1).covered);
        assertEquals(SpecConfidence.LOW, generated.get(1).specs.confidence);
        assertTrue(generated.get(1).notes.contains("confirm body type"));
        assertEquals("estimate", generated.get(1).specs.source);
        assertEquals(BodyType.COMPACT_SUV, generated.get(1).specs.bodyType);
    }

    private static SpecBulkGenerator.Candidate candidate(String make, String model, String variant) {
        SpecBulkGenerator.Candidate candidate = new SpecBulkGenerator.Candidate();
        candidate.make = make;
        candidate.model = model;
        candidate.variant = variant;
        return candidate;
    }
}
