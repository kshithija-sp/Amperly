package com.amperly.app;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.common.GeoUtils;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.engine.billing.BillingEngine;
import com.amperly.app.engine.routing.RoutePlannerEngine;
import com.amperly.app.engine.soc.SoCEstimatorEngine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EngineUnitTest {

    private static final double ORIGIN_LAT = 12.2958;
    private static final double ORIGIN_LNG = 76.6394;
    private static final double DEST_LAT = 12.9141;
    private static final double DEST_LNG = 74.8560;

    private Vehicle nexon() {
        return new Vehicle("veh_1", "user_1", "Tata", "Nexon EV", "Max", 2023, 40.5,
                "CCS_2_DC", 7.2, 30.0, 6.5, 96.0, 0.0, 15, 80, "KA09EV1234");
    }

    private ChargerStation station(String id, String name, double lat, double lng,
                                  String connectors, double powerKw, String meterLevel) {
        return ChargerStation.create(id, "host_1", "Host", name, "Address " + id, lat, lng,
                AppConstants.LOCATION_TYPE_COMMERCIAL, "24/7", 18.0, 5.0, 4.0, meterLevel,
                connectors, powerKw, 2, "Public access", 4.5);
    }

    /**
     * Priced at the spec's worked example tariff (₹14.40/kWh) so the receipt assertions match the
     * published bill table: 12.5 kWh → ₹180 energy + ₹5 service + ₹4 gateway = ₹189.
     */
    private ChargerStation specPricedStation(String id, String name, double lat, double lng,
                                             String connectors, double powerKw, String meterLevel) {
        return ChargerStation.create(id, "host_1", "Host", name, "Address " + id, lat, lng,
                AppConstants.LOCATION_TYPE_COMMERCIAL, "24/7", 14.4, 5.0, 4.0, meterLevel,
                connectors, powerKw, 2, "Public access", 4.5);
    }

    private List<ChargerStation> corridorStations() {
        List<ChargerStation> stations = new ArrayList<>();
        // Mid-route, on the straight line between Mysore and Mangalore.
        stations.add(station("st_highway", "Highway Hub", 12.600, 75.790, "CCS_2_DC", 60.0,
                AppConstants.LEVEL_2_SIMULATED_SMART_METER));
        // Same connector as the vehicle, but much slower: the trade-off to explain.
        stations.add(station("st_slow", "Roadside DC", 12.605, 75.820, "CCS_2_DC", 22.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL));
        // Far off the corridor: must be excluded.
        stations.add(station("st_far", "Far Away Charger", 13.400, 74.700, "CCS_2_DC", 50.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL));
        // Incompatible connector: must be excluded with a reason.
        stations.add(station("st_chademo", "CHadeMO Only", 12.604, 75.800, "CHADEMO", 50.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL));
        return stations;
    }

    @Test
    public void corridorFilteringKeepsOnlyCompatibleStationInsideTheCorridor() {
        RoutePlannerEngine.RoutePlanResult result = RoutePlannerEngine.planRoute("Mysore", "Mangalore",
                ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, 140.0, 10.0, 55, false, false,
                RoutePlannerEngine.PREF_FASTEST, nexon(), corridorStations());

        assertEquals(2, result.getCandidates().size());
        List<String> candidateIds = new ArrayList<>();
        for (RoutePlannerEngine.CandidateEvaluation evaluation : result.getCandidates()) {
            candidateIds.add(evaluation.getStation().getId());
        }
        assertTrue(candidateIds.contains("st_highway"));
        assertTrue(candidateIds.contains("st_slow"));
        assertFalse(candidateIds.contains("st_far"));
        assertFalse(candidateIds.contains("st_chademo"));

        // Two excluded stations with honest reasons.
        assertEquals(2, result.getExcluded().size());
        StringBuilder reasons = new StringBuilder();
        for (RoutePlannerEngine.ExcludedStation excluded : result.getExcluded()) {
            reasons.append(excluded.getReason()).append(" | ");
        }
        assertTrue(reasons.toString().contains("CHADEMO"));
    }

    @Test
    public void plannerExplainsTheTradeOffAndKeepsABackupStop() {
        RoutePlannerEngine.RoutePlanResult result = RoutePlannerEngine.planRoute("Mysore", "Mangalore",
                ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, 140.0, 10.0, 55, false, false,
                RoutePlannerEngine.PREF_FASTEST, nexon(), corridorStations());

        assertNotNull(result.getRecommendedStop());
        assertTrue(result.getRecommendedStop().isReachable());
        String reason = result.getRecommendedStop().getReason();
        assertTrue(reason.contains("Detour:"));
        assertTrue(reason.contains("Estimated charge:"));
        assertTrue(reason.contains("Estimated energy added:"));
        assertTrue(reason.contains("Expected battery on arrival:"));

        // A stop is required at 55% and the slower AC charger is kept as the backup.
        assertEquals(1, result.getPlannedStops().size());
        assertEquals(1, result.getBackups().size());
        assertEquals("st_slow", result.getBackups().get(0).getStation().getId());

        // The total journey time includes driving plus the stop.
        assertTrue(result.getTotalJourneyMinutes() > result.getDriveMinutes());
        assertTrue(result.getAssumptions().size() >= 4);
    }

    @Test
    public void fullBatteryNeedsNoChargingStop() {
        RoutePlannerEngine.RoutePlanResult result = RoutePlannerEngine.planRoute("Mysore", "Mangalore",
                ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG, 140.0, 10.0, 100, true, false,
                RoutePlannerEngine.PREF_FASTEST, nexon(), corridorStations());

        assertTrue(result.getPlannedStops().isEmpty());
        assertFalse(result.needsCharging());
        assertEquals(0, result.getChargingMinutes());
        assertEquals(RoutePlannerEngine.CONFIDENCE_HIGH, result.getConfidence());
    }

    @Test
    public void socCorrectionMakesTheUserValueAuthoritativeAndRemovesUnreachableChargers() {
        List<ChargerStation> previouslyReachable = Arrays.asList(
                station("st_highway", "Highway Hub", 12.600, 75.790, "CCS_2_DC", 60.0,
                        AppConstants.LEVEL_1_VERIFIED_MANUAL));

        SoCEstimatorEngine.RecalculationResult result =
                SoCEstimatorEngine.applyAuthoritativeSocOverride("veh_1", "plan_1", 35, 25,
                        12.450, 76.050, 84.0, DEST_LAT, DEST_LNG, nexon(), corridorStations(),
                        previouslyReachable);

        assertTrue(result.isWasOverrideApplied());
        assertEquals(25, result.getActualDashboardSoc());
        assertEquals(35, result.getPreviousEstimatedSoc());
        assertEquals(25, result.getAuditRecord().getActualDashboardSoc());
        assertEquals(35, result.getAuditRecord().getEstimatedSoc());
        assertNotNull(result.getExplanation());
        assertTrue(result.getExplanation().contains("Original estimate 35%"));
    }

    @Test
    public void noSafeChargerStateIsReportedInsteadOfRoutingBeyondRange() {
        SoCEstimatorEngine.RecalculationResult result =
                SoCEstimatorEngine.applyAuthoritativeSocOverride("veh_1", "plan_1", 35, 6,
                        12.2958, 76.6394, 140.0, DEST_LAT, DEST_LNG, nexon(), corridorStations(),
                        new ArrayList<>());

        assertTrue(result.isNoSafeCharger());
        assertNull(result.getRecommendedNextCharger());
        assertNotNull(result.getWarningMessage());
        assertTrue(result.getWarningMessage().toLowerCase().contains("no safe charger"));
        assertTrue(result.isNeedsWiderRadius());
        assertFalse(result.getSafeReachableStations().containsAll(corridorStations()));
    }

    @Test
    public void slowerChargerAlternativeRelaxesTheReserveExplicitly() {
        SoCEstimatorEngine.RecalculationResult strict =
                SoCEstimatorEngine.applyAuthoritativeSocOverride("veh_1", "plan_1", 35, 18,
                        12.550, 75.880, 40.0, DEST_LAT, DEST_LNG, nexon(), corridorStations(),
                        new ArrayList<>(), 15);
        SoCEstimatorEngine.RecalculationResult relaxed =
                SoCEstimatorEngine.applyAuthoritativeSocOverride("veh_1", "plan_1", 35, 18,
                        12.550, 75.880, 40.0, DEST_LAT, DEST_LNG, nexon(), corridorStations(),
                        new ArrayList<>(), 8);

        assertTrue(relaxed.getSafeReachableStations().size() >= strict.getSafeReachableStations().size());
    }

    @Test
    public void billingUsesVerifiedMeterReadingsOnly() {
        ChargerStation station = specPricedStation("st_1", "Campus Charger", 12.301, 76.645, "CCS_2_DC",
                30.0, AppConstants.LEVEL_1_VERIFIED_MANUAL);

        BillingEngine.BillingCalculationResult result = BillingEngine.calculateBill("sess_1",
                "cons_1", "host_1", station.getName(), station, AppConstants.LEVEL_1_VERIFIED_MANUAL,
                true, 125.40, 137.90);

        assertTrue(result.isValid());
        assertEquals(12.50, result.getVerifiedKwh(), 0.001);
        assertEquals(180.00, result.getEnergyCharge(), 0.001);
        assertEquals(5.00, result.getServiceFee(), 0.001);
        assertEquals(4.00, result.getGatewayFee(), 0.001);
        assertEquals(189.00, result.getFinalTotal(), 0.001);
        assertNotNull(result.getInvoice());
        assertTrue(result.getLines().size() >= 7);
    }

    @Test
    public void billingRejectsLowerEndingReadingAndUnverifiedMeters() {
        ChargerStation verified = station("st_1", "Campus Charger", 12.301, 76.645, "CCS_2_DC", 30.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL);

        BillingEngine.BillingCalculationResult reversed = BillingEngine.calculateBill("sess_1",
                "cons_1", "host_1", verified.getName(), verified, AppConstants.LEVEL_1_VERIFIED_MANUAL,
                false, 137.90, 125.40);
        assertFalse(reversed.isValid());
        assertNull(reversed.getInvoice());

        BillingEngine.BillingCalculationResult noMeter = BillingEngine.calculateBill("sess_2",
                "cons_1", "host_1", "Cafe ChargePoint", verified,
                AppConstants.LEVEL_3_NO_VERIFIED_METER, false, 10.0, 20.0);
        assertFalse(noMeter.isValid());
        assertTrue(noMeter.getErrorMessage().toLowerCase().contains("no verified meter"));
    }

    @Test
    public void zeroEnergyConsumptionHasNoEnergyCharge() {
        ChargerStation station = station("st_1", "Campus Charger", 12.301, 76.645, "CCS_2_DC", 30.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL);
        BillingEngine.BillingCalculationResult result = BillingEngine.calculateBill("sess_1",
                "cons_1", "host_1", station.getName(), station, AppConstants.LEVEL_1_VERIFIED_MANUAL,
                false, 100.0, 100.0);

        assertTrue(result.isValid());
        assertEquals(0.0, result.getEnergyCharge(), 0.001);
        assertEquals(0.0, result.getFinalTotal(), 0.001);
    }

    @Test
    public void geoHelpersMeasureCorridorDistanceAndDetour() {
        double onLine = GeoUtils.distanceToRouteKm(12.600, 75.712, ORIGIN_LAT, ORIGIN_LNG,
                DEST_LAT, DEST_LNG);
        double offLine = GeoUtils.distanceToRouteKm(13.400, 74.700, ORIGIN_LAT, ORIGIN_LNG,
                DEST_LAT, DEST_LNG);

        assertTrue(onLine < 2.0);
        assertTrue(offLine > 50.0);
        assertEquals(20.4, GeoUtils.detourKm(10.0), 0.01);
        assertTrue(GeoUtils.detourMinutes(GeoUtils.detourKm(10.0)) > 20);
        assertTrue(GeoUtils.fractionAlongRoute(12.600, 75.712, ORIGIN_LAT, ORIGIN_LNG,
                DEST_LAT, DEST_LNG) > 0.4);
    }

    @Test
    public void portLabelCarriesConnectorAndPower() {
        ChargerPort port = new ChargerPort("p1", "st_1", "2", "CCS_2_DC", 30.0, true,
                AppConstants.PORT_AVAILABLE);
        String label = com.amperly.app.engine.availability.AvailabilityEngine.portLabel(port);
        assertTrue(label.contains("CCS_2_DC"));
        assertTrue(label.contains("30 kW"));
        assertTrue(label.contains("DC"));
    }
}
