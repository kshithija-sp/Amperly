package com.amperly.app;

import com.amperly.app.vehicle.BodyTemplate;
import com.amperly.app.vehicle.BodyType;
import com.amperly.app.vehicle.ChargePortLocation;
import com.amperly.app.vehicle.CuratedProfileStore;
import com.amperly.app.vehicle.SpecConfidence;
import com.amperly.app.vehicle.SpecEstimator;
import com.amperly.app.vehicle.SpecResolver;
import com.amperly.app.vehicle.VehicleSpecProvider;
import com.amperly.app.vehicle.VehicleSpecs;
import com.amperly.app.vehicle.VehicleSpecDatabase;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Tier 3: database first, estimator second, every result cached. */
public class SpecResolverTest {

    @Test
    public void databaseEntriesResolveAtHighConfidence() throws Exception {
        SpecResolver resolver = resolver();
        VehicleSpecs specs = resolver.resolve("Tata", "Nexon EV", "Empowered+");

        assertEquals(SpecConfidence.HIGH, specs.confidence);
        assertEquals(BodyType.COMPACT_SUV, specs.bodyType);
        assertEquals(3994, specs.lengthMm);
        assertEquals(1811, specs.widthMm);
        assertEquals(1616, specs.heightMm);
        assertEquals(2498, specs.wheelbaseMm);
        assertEquals(190, specs.groundClearanceMm);
        assertEquals(332.2f, specs.tyreRadiusMm(), 0.5f);
    }

    @Test
    public void theModelIsFoundEvenWhenTheMakeIsInTheModelField() throws Exception {
        SpecResolver resolver = resolver();
        VehicleSpecs specs = resolver.resolve("", "Tata Nexon EV", "");
        assertEquals(BodyType.COMPACT_SUV, specs.bodyType);
        assertEquals(SpecConfidence.HIGH, specs.confidence);
    }

    @Test
    public void unknownCarsFallBackToTheEstimator() throws Exception {
        SpecResolver resolver = resolver();
        VehicleSpecs specs = resolver.resolve("Acme Motors", "Comet ZX", "Base");

        assertEquals(SpecConfidence.LOW, specs.confidence);
        assertTrue(specs.confidence.needsConfirmation());
        assertEquals("estimate", specs.source);
        assertTrue(specs.lengthMm > 2000);
        assertTrue(specs.widthMm > 1000);
        assertTrue(specs.heightMm > 1000);
        assertTrue(specs.wheelbaseMm > 1000);
    }

    @Test
    public void resultsAreCachedPerMakeAndModel() throws Exception {
        SpecResolver resolver = resolver();
        VehicleSpecs first = resolver.resolve("Tata", "Punch EV", "Fearless");
        VehicleSpecs second = resolver.resolve("Tata", "Punch EV", "Adventure");
        assertSame(first, second);

        VehicleSpecs unknownFirst = resolver.resolve("Acme", "Roadster Z", "");
        VehicleSpecs unknownSecond = resolver.resolve("Acme", "Roadster Z", "");
        assertSame(unknownFirst, unknownSecond);
    }

    @Test
    public void theDriverCanConfirmABodyType() throws Exception {
        SpecResolver resolver = resolver();
        VehicleSpecs estimated = resolver.resolve("Acme", "Car 500", "");
        VehicleSpecs confirmed = resolver.confirmBodyType(estimated, BodyType.MPV);
        BodyTemplate template = BodyTemplate.of(BodyType.MPV);

        assertEquals(BodyType.MPV, confirmed.bodyType);
        assertEquals(SpecConfidence.MEDIUM, confirmed.confidence);
        assertEquals("driver", confirmed.source);
        assertEquals(template.referenceLengthMm, confirmed.lengthMm);
        assertEquals(template.referenceWheelbaseMm, confirmed.wheelbaseMm);
    }

    @Test
    public void confirmedBodyTypeKeepsVerifiedDimensions() throws Exception {
        SpecResolver resolver = resolver();
        VehicleSpecs verified = resolver.resolve("Tesla", "Model 3", "");
        VehicleSpecs confirmed = resolver.confirmBodyType(verified, BodyType.SUV);

        assertEquals(BodyType.SUV, confirmed.bodyType);
        assertEquals(4720, confirmed.lengthMm);
        assertEquals(1848, confirmed.widthMm);
        assertEquals(1442, confirmed.heightMm);
        assertEquals(SpecConfidence.MEDIUM, confirmed.confidence);
    }

    @Test
    public void theEstimatorRecognisesEachSegment() {
        assertEquals(BodyType.COMPACT_SUV, SpecEstimator.inferBodyType("Tata", "Nexon EV"));
        assertEquals(BodyType.MICROCAR, SpecEstimator.inferBodyType("MG", "Comet EV"));
        assertEquals(BodyType.MPV, SpecEstimator.inferBodyType("Toyota", "Innova Crysta"));
        assertEquals(BodyType.PICKUP, SpecEstimator.inferBodyType("Isuzu", "D-Max V-Cross"));
        assertEquals(BodyType.COUPE, SpecEstimator.inferBodyType("Porsche", "Taycan"));
        assertEquals(BodyType.HATCHBACK, SpecEstimator.inferBodyType("Tata", "Tiago EV"));
        assertEquals(BodyType.SEDAN, SpecEstimator.inferBodyType("Tesla", "Model 3"));
        assertEquals(BodyType.SUV, SpecEstimator.inferBodyType("Rivian", "R1S"));
        assertEquals(BodyType.HATCHBACK, SpecEstimator.inferBodyType("Acme", "ZX"));
    }

    @Test
    public void theCatalogueCoversEveryBodyTypeForTheGallery() throws Exception {
        VehicleSpecDatabase database = database();
        Set<BodyType> covered = EnumSet.noneOf(BodyType.class);
        for (VehicleSpecs specs : database.catalogue()) {
            covered.add(specs.bodyType);
        }
        assertEquals(EnumSet.allOf(BodyType.class), covered);
        assertTrue(database.catalogue().size() >= 12);
        assertEquals(1, database.schemaVersion());
    }

    @Test
    public void aCuratedProfileIsAttachedToADatabaseHit() throws Exception {
        File profileAsset = new File("src/main/assets/vehicle_profiles.json");
        Assume.assumeTrue("curated profile asset present", profileAsset.exists());
        String profiles = new String(Files.readAllBytes(profileAsset.toPath()),
                StandardCharsets.UTF_8);
        SpecResolver resolver = new SpecResolver(database(), null,
                CuratedProfileStore.fromJson(profiles), null);

        VehicleSpecs specs = resolver.resolve("Tata", "Nexon EV", "Empowered+");

        assertNotNull(specs.curated);
        assertEquals("NEXON EV", specs.curated.badge);
        assertEquals(SpecConfidence.HIGH, specs.confidence);
        assertEquals("database", specs.source);
        assertEquals(3994, specs.lengthMm);
    }

    @Test
    public void theProviderIsUsedWhenTheOwnedDatabaseMisses() {
        VehicleSpecs fromApi = VehicleSpecs.builder("Acme", "Roadster Z", BodyType.COUPE)
                .dimensionsMm(4400, 1880, 1320)
                .wheelbaseMm(2650)
                .groundClearanceMm(120)
                .tyre(235, 40, 19)
                .chargePort(ChargePortLocation.REAR_LEFT)
                .confidence(SpecConfidence.HIGH)
                .source("api")
                .build();
        SpecResolver resolver = new SpecResolver(emptyDatabase(), null, null,
                providerReturning(fromApi));

        VehicleSpecs specs = resolver.resolve("Acme", "Roadster Z", "");

        assertEquals("api", specs.source);
        assertEquals(SpecConfidence.HIGH, specs.confidence);
        assertEquals(4400, specs.lengthMm);
    }

    @Test
    public void theModelYearReachesAYearAwareProvider() {
        VehicleSpecs fromApi = VehicleSpecs.builder("Acme", "Roadster Z", BodyType.COUPE)
                .dimensionsMm(4400, 1880, 1320)
                .wheelbaseMm(2650)
                .confidence(SpecConfidence.HIGH)
                .source("api")
                .build();
        final int[] calls = {0};
        final int[] firstYear = {0};
        final int[] lastYear = {0};
        VehicleSpecProvider provider = new VehicleSpecProvider() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public VehicleSpecs lookup(String make, String model, String variant) {
                return null;
            }

            @Override
            public VehicleSpecs lookup(String make, String model, String variant, int year) {
                if (calls[0] == 0) {
                    firstYear[0] = year;
                }
                calls[0]++;
                lastYear[0] = year;
                return fromApi;
            }
        };
        SpecResolver resolver = new SpecResolver(emptyDatabase(), null, null, provider);

        resolver.resolve("Acme", "Roadster Z", "Base", 2024);
        resolver.resolve("Acme", "Roadster Z", "Base", 2025);

        assertEquals(2024, firstYear[0]);
        assertEquals(2025, lastYear[0]);
        assertEquals("a different model year is a different cache entry", 2, calls[0]);
    }

    @Test
    public void aProviderMissFallsBackToTheEstimator() {
        SpecResolver resolver = new SpecResolver(emptyDatabase(), null, null,
                providerReturning(null));

        VehicleSpecs specs = resolver.resolve("Acme", "Car 500", "");

        assertEquals("estimate", specs.source);
        assertEquals(SpecConfidence.LOW, specs.confidence);
    }

    @Test
    public void curatedDimensionsBeatBothDatabaseAndProvider() {
        String profiles = "{\"schemaVersion\":1,\"profiles\":[{"
                + "\"make\":\"Acme\",\"model\":\"Roadster Z\",\"bodyType\":\"COUPE\","
                + "\"lengthMm\":4510,\"widthMm\":1900,\"heightMm\":1330}]}";
        VehicleSpecs fromApi = VehicleSpecs.builder("Acme", "Roadster Z", BodyType.SEDAN)
                .dimensionsMm(4400, 1880, 1320)
                .wheelbaseMm(2650)
                .tyre(235, 40, 19)
                .confidence(SpecConfidence.HIGH)
                .source("api")
                .build();
        SpecResolver resolver = new SpecResolver(emptyDatabase(), null,
                CuratedProfileStore.fromJson(profiles), providerReturning(fromApi));

        VehicleSpecs specs = resolver.resolve("Acme", "Roadster Z", "");

        assertEquals(BodyType.COUPE, specs.bodyType);
        assertEquals(4510, specs.lengthMm);
        assertEquals(1900, specs.widthMm);
        assertEquals("curated", specs.source);
        assertNotNull(specs.curated);
    }

    private static VehicleSpecProvider providerReturning(VehicleSpecs specs) {
        return new VehicleSpecProvider() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public VehicleSpecs lookup(String make, String model, String variant) {
                return specs;
            }
        };
    }

    private static VehicleSpecDatabase emptyDatabase() {
        VehicleSpecDatabase database = VehicleSpecDatabase.fromJson(
                "{\"schemaVersion\":1,\"vehicles\":[]}");
        assertNotNull(database);
        assertTrue(database.catalogue().isEmpty());
        return database;
    }

    private static SpecResolver resolver() throws Exception {
        return new SpecResolver(database(), null);
    }

    private static VehicleSpecDatabase database() throws Exception {
        File asset = new File("src/main/assets/vehicle_specs.json");
        Assume.assumeTrue("spec database asset present", asset.exists());
        String json = new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8);
        VehicleSpecDatabase database = VehicleSpecDatabase.fromJson(json);
        assertNotNull(database);
        return database;
    }
}
