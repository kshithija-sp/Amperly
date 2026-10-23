package com.amperly.app;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.engine.billing.BillingEngine;
import com.amperly.app.engine.booking.ReservationEngine;
import com.amperly.app.engine.meter.MeterSimulator;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BookingAndMeterTest {

    private ChargerStation station;
    private ChargerPort port;

    @Before
    public void setUp() {
        station = ChargerStation.create("st_1", "host_1", "ABC College", "Campus Charger", "Gate 2",
                12.301, 76.645, AppConstants.LOCATION_TYPE_CAMPUS, "08:00 - 20:00", 18.0, 5.0, 4.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL, "CCS_2_DC,TYPE_2_AC", 30.0, 2,
                "Campus pass required", 4.9);
        port = new ChargerPort("port_1", "st_1", "2", "CCS_2_DC", 30.0, true,
                AppConstants.PORT_AVAILABLE);
    }

    private Vehicle vehicle() {
        return new Vehicle("veh_1", "cons_1", "Tata", "Nexon EV", "Max", 2023, 40.5, "CCS_2_DC",
                7.2, 30.0, 6.5, 96.0, 0.0, 15, 80, "KA09EV1234");
    }

    @Test
    public void aFreeCompatibleSlotCanBeReserved() {
        long now = System.currentTimeMillis();
        ReservationEngine.BookingCheck check = ReservationEngine.check(station, port, vehicle(),
                now, now + 5_400_000L, 0, false);

        assertTrue(check.isAllowed());
        assertTrue(check.isRequiresPaidAuthorization());
    }

    @Test
    public void doubleBookingIsPrevented() {
        long now = System.currentTimeMillis();
        ReservationEngine.BookingCheck check = ReservationEngine.check(station, port, vehicle(),
                now, now + 5_400_000L, 1, false);

        assertFalse(check.isAllowed());
        assertTrue(check.getReason().toLowerCase().contains("double booking"));
    }

    @Test
    public void incompatibleConnectorCannotBeReserved() {
        ChargerPort chademo = new ChargerPort("port_3", "st_1", "3", "CHADEMO", 50.0, true,
                AppConstants.PORT_AVAILABLE);
        long now = System.currentTimeMillis();
        ReservationEngine.BookingCheck check = ReservationEngine.check(station, chademo, vehicle(),
                now, now + 5_400_000L, 0, false);

        assertFalse(check.isAllowed());
        assertTrue(check.getReason().toLowerCase().contains("connector"));
    }

    @Test
    public void pausedOrUnverifiedStationsCannotBeReserved() {
        long now = System.currentTimeMillis();
        ChargerStation paused = ChargerStation.create("st_2", "host_1", "Host", "Paused", "Addr",
                12.30, 76.64, AppConstants.LOCATION_TYPE_OFFICE, "24/7", 18.0, 5.0, 4.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL, "CCS_2_DC", 30.0, 1, "rules", 4.0);
        paused.setPaused(true);
        assertFalse(ReservationEngine.check(paused, port, vehicle(), now, now + 60_000L, 0, false).isAllowed());

        ChargerStation pending = ChargerStation.create("st_3", "host_1", "Host", "Pending", "Addr",
                12.30, 76.64, AppConstants.LOCATION_TYPE_OFFICE, "24/7", 18.0, 5.0, 4.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL, "CCS_2_DC", 30.0, 1, "rules", 4.0);
        pending.setApprovalStatus(AppConstants.APPROVAL_PENDING);
        assertFalse(ReservationEngine.check(pending, port, vehicle(), now, now + 60_000L, 0, false).isAllowed());
    }

    @Test
    public void meterlessStationsReserveWithoutAutomaticBilling() {
        ChargerStation cafe = ChargerStation.create("st_4", "host_1", "Cafe", "Cafe ChargePoint", "Addr",
                12.306, 76.650, AppConstants.LOCATION_TYPE_CAFE, "09:00 - 21:00", 14.0, 0.0, 0.0,
                AppConstants.LEVEL_3_NO_VERIFIED_METER, "TYPE_2_AC", 3.3, 1, "Ask at the counter", 4.2);
        ChargerPort acPort = new ChargerPort("port_9", "st_4", "1", "TYPE_2_AC", 3.3, false,
                AppConstants.PORT_AVAILABLE);
        long now = System.currentTimeMillis();

        ReservationEngine.BookingCheck check = ReservationEngine.check(cafe, acPort, null, now,
                now + 5_400_000L, 0, false);
        assertTrue(check.isAllowed());
        assertFalse(check.isRequiresPaidAuthorization());
        assertTrue(check.getReason().toLowerCase().contains("no verified meter"));
    }

    @Test
    public void pricePreviewSeparatesEnergyServiceAndGatewayCharges() {
        ReservationEngine.PricePreview preview = ReservationEngine.preview(station, 12.5);

        assertEquals(225.00, preview.getEnergyCharge(), 0.001);
        assertEquals(5.00, preview.getServiceFee(), 0.001);
        assertEquals(4.00, preview.getGatewayFee(), 0.001);
        assertEquals(234.00, preview.getEstimatedTotal(), 0.001);
        assertTrue(preview.getAuthorizedAmount() > preview.getEstimatedTotal());
    }

    @Test
    public void publishedCancellationPolicyIsAlwaysDisclosed() {
        assertTrue(ReservationEngine.CANCELLATION_POLICY.contains("Free cancellation"));
        assertTrue(ReservationEngine.CANCELLATION_POLICY.contains("late-cancellation"));
        assertTrue(ReservationEngine.CANCELLATION_POLICY.contains("No energy charge"));
    }

    @Test
    public void freeCancellationWindowEndsAnHourBeforeTheSlot() {
        long now = System.currentTimeMillis();
        long slotStart = now + (30L * 60L * 1000L);
        Reservation reservation = new Reservation("res_1", "cons_1", "st_1", "Campus", "port_1",
                "Port 1", "AMP-1", slotStart, slotStart + 5_400_000L, AppConstants.STATUS_RESERVED,
                18.0, 5.0, 4.0, 0.0, true, "pay_1", 0, 0, now);

        BillingEngine.CancellationResult late = BillingEngine.calculateCancellation(reservation, now);
        assertFalse(late.isFree());
        assertEquals(AppConstants.LATE_CANCELLATION_FEE_INR, late.getFee(), 0.001);
        assertTrue(late.getMessage().contains("No energy charge"));

        long slotLater = now + (5L * 60L * 60L * 1000L);
        Reservation later = new Reservation("res_2", "cons_1", "st_1", "Campus", "port_1",
                "Port 1", "AMP-2", slotLater, slotLater + 5_400_000L, AppConstants.STATUS_RESERVED,
                18.0, 5.0, 4.0, 0.0, true, "pay_2", 0, 0, now);
        BillingEngine.CancellationResult free = BillingEngine.calculateCancellation(later, now);
        assertTrue(free.isFree());
        assertEquals(0.0, free.getFee(), 0.001);
    }

    @Test
    public void meterSimulatorDeliversEnergyAndAdvancesTheCumulativeReading() {
        double start = MeterSimulator.beginSession("st_sim");
        MeterSimulator.SimulatedSession session = MeterSimulator.simulateSession("st_sim", 30.0, 60);

        assertTrue(session.getDeliveredKwh() > 20.0);
        assertEquals(start + session.getDeliveredKwh(), session.getEndReadingKwh(), 0.001);
        assertTrue(MeterSimulator.currentReading("st_sim") > start);
        assertTrue(session.isSimulated());
    }

    @Test
    public void meterSimulatorSupportsInvalidAndImplausibleReadingScenarios() {
        assertFalse(MeterSimulator.isValidReading(137.90, 125.40));
        assertTrue(MeterSimulator.isValidReading(125.40, 137.90));

        // 40 kWh delivered in 30 minutes from a 7.2 kW port is physically impossible.
        assertTrue(MeterSimulator.isImplausibleReading(100.0, 140.0, 7.2, 30));
        assertFalse(MeterSimulator.isImplausibleReading(100.0, 103.0, 7.2, 30));
    }

    @Test
    public void meterOfflineStateIsExplicit() {
        MeterSimulator.setOffline("st_offline", true);
        assertTrue(MeterSimulator.isOffline("st_offline"));
        MeterSimulator.setOffline("st_offline", false);
        assertFalse(MeterSimulator.isOffline("st_offline"));
    }

    @Test
    public void billingLinesAreItemisedForTheReceipt() {
        // The spec's worked example tariff: 12.5 kWh × ₹14.40 = ₹180 energy, ₹189 final total.
        ChargerStation specStation = ChargerStation.create("st_spec", "host_1", "ABC College",
                "Campus Charger", "Gate 2", 12.301, 76.645, AppConstants.LOCATION_TYPE_CAMPUS,
                "08:00 - 20:00", 14.4, 5.0, 4.0, AppConstants.LEVEL_1_VERIFIED_MANUAL,
                "CCS_2_DC,TYPE_2_AC", 30.0, 2, "Campus pass required", 4.9);
        BillingEngine.BillingCalculationResult result = BillingEngine.calculateBill("sess_1", "cons_1",
                "host_1", specStation.getName(), specStation, AppConstants.LEVEL_1_VERIFIED_MANUAL, true,
                125.40, 137.90);

        assertNotNull(result.getInvoice());
        assertEquals(189.00, result.getInvoice().getFinalTotal(), 0.001);
        assertTrue(result.getLines().stream().anyMatch(line ->
                line.getLabel().contains("Verified energy consumed")));
        assertTrue(result.getLines().stream().anyMatch(line ->
                line.getLabel().contains("Demo meter")));
    }
}
