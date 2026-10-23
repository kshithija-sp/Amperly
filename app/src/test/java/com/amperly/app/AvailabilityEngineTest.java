package com.amperly.app;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.data.local.entity.AvailabilitySchedule;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.engine.availability.AvailabilityEngine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AvailabilityEngineTest {

    private ChargerStation station() {
        return ChargerStation.create("st_1", "host_1", "Host", "Campus Charger", "Gate 2",
                12.301, 76.645, AppConstants.LOCATION_TYPE_CAMPUS, "08:00 - 20:00", 18.0, 5.0, 4.0,
                AppConstants.LEVEL_1_VERIFIED_MANUAL, "CCS_2_DC", 30.0, 2, "Campus pass", 4.9);
    }

    private ChargerPort port(String id, String number, String status) {
        return new ChargerPort(id, "st_1", number, "CCS_2_DC", 30.0, true, status);
    }

    private List<AvailabilitySchedule> schedulesFor(String portId, int open, int close, boolean blocked) {
        List<AvailabilitySchedule> schedules = new ArrayList<>();
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            schedules.add(new AvailabilitySchedule(portId + "_d" + day, "st_1", portId, day,
                    open, close, blocked, blocked ? "Maintenance" : null, System.currentTimeMillis()));
        }
        return schedules;
    }

    private long tomorrowMorning() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis() - (calendar.getTimeInMillis() % 1000);
    }

    @Test
    public void slotsAreGeneratedInsidePublishedHours() {
        List<ChargerPort> ports = new ArrayList<>();
        ports.add(port("port_1", "1", AppConstants.PORT_AVAILABLE));
        List<AvailabilitySchedule> schedules = schedulesFor("port_1", 8 * 60, 20 * 60, false);

        List<AvailabilityEngine.Slot> slots = AvailabilityEngine.generateSlots(station(), ports,
                schedules, new ArrayList<>(), tomorrowMorning(), 90, 6);

        assertFalse(slots.isEmpty());
        for (AvailabilityEngine.Slot slot : slots) {
            assertTrue(slot.isAvailable());
            assertNotNull(slot.getPortId());
            assertEquals("port_1", slot.getPortId());
        }
    }

    @Test
    public void maintenanceBlockRemovesSlotsWithAnHonestReason() {
        List<ChargerPort> ports = new ArrayList<>();
        ports.add(port("port_1", "1", AppConstants.PORT_MAINTENANCE));
        List<AvailabilitySchedule> schedules = schedulesFor("port_1", 8 * 60, 20 * 60, false);

        List<AvailabilityEngine.Slot> slots = AvailabilityEngine.generateSlots(station(), ports,
                schedules, new ArrayList<>(), tomorrowMorning(), 90, 4);

        assertFalse(slots.isEmpty());
        for (AvailabilityEngine.Slot slot : slots) {
            assertFalse(slot.isAvailable());
            assertTrue(slot.getReason().toLowerCase().contains("maintenance"));
        }
    }

    @Test
    public void blockedScheduleClosesThePort() {
        List<ChargerPort> ports = new ArrayList<>();
        ports.add(port("port_1", "1", AppConstants.PORT_AVAILABLE));
        List<AvailabilitySchedule> schedules = schedulesFor("port_1", 8 * 60, 20 * 60, true);

        List<AvailabilityEngine.Slot> slots = AvailabilityEngine.generateSlots(station(), ports,
                schedules, new ArrayList<>(), tomorrowMorning(), 90, 4);

        for (AvailabilityEngine.Slot slot : slots) {
            assertFalse(slot.isAvailable());
            assertTrue(slot.getReason().toLowerCase().contains("blocked"));
        }
    }

    @Test
    public void anExistingReservationRemovesOverlappingSlots() {
        long slotStart = tomorrowMorning();
        List<ChargerPort> ports = new ArrayList<>();
        ports.add(port("port_1", "1", AppConstants.PORT_AVAILABLE));
        List<AvailabilitySchedule> schedules = schedulesFor("port_1", 8 * 60, 20 * 60, false);
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("res_1", "cons_1", "st_1", "Campus Charger", "port_1",
                "Port 1", "AMP-1", slotStart, slotStart + 5_400_000L, AppConstants.STATUS_RESERVED,
                18.0, 5.0, 4.0, 0.0, true, "pay_1", 0, 0, slotStart));

        assertTrue(AvailabilityEngine.hasConflict(reservations, "port_1", slotStart,
                slotStart + 5_400_000L));

        List<AvailabilityEngine.Slot> slots = AvailabilityEngine.generateSlots(station(), ports,
                schedules, reservations, slotStart, 90, 3);
        assertFalse(slots.isEmpty());
        assertEquals("Already reserved", slots.get(0).getReason());
    }

    @Test
    public void aSecondPortKeepsTheSlotOpenWhenTheFirstIsBusy() {
        long slotStart = tomorrowMorning();
        List<ChargerPort> ports = new ArrayList<>();
        ports.add(port("port_1", "1", AppConstants.PORT_AVAILABLE));
        ports.add(port("port_2", "2", AppConstants.PORT_AVAILABLE));
        List<AvailabilitySchedule> schedules = new ArrayList<>();
        schedules.addAll(schedulesFor("port_1", 8 * 60, 20 * 60, false));
        schedules.addAll(schedulesFor("port_2", 8 * 60, 20 * 60, false));
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("res_1", "cons_1", "st_1", "Campus Charger", "port_1",
                "Port 1", "AMP-1", slotStart, slotStart + 5_400_000L, AppConstants.STATUS_RESERVED,
                18.0, 5.0, 4.0, 0.0, true, "pay_1", 0, 0, slotStart));

        List<AvailabilityEngine.Slot> slots = AvailabilityEngine.generateSlots(station(), ports,
                schedules, reservations, slotStart, 90, 2);
        assertFalse(slots.isEmpty());
        assertTrue(slots.get(0).isAvailable());
        assertEquals("port_2", slots.get(0).getPortId());
    }

    @Test
    public void summaryCountsFreePortsAndReportsFreshness() {
        List<ChargerPort> ports = new ArrayList<>();
        ports.add(port("port_1", "1", AppConstants.PORT_AVAILABLE));
        ports.add(port("port_2", "2", AppConstants.PORT_MAINTENANCE));

        AvailabilityEngine.AvailabilitySummary summary = AvailabilityEngine.summarise(station(), ports,
                schedulesFor("port_1", 0, 1440, false), new ArrayList<>(), System.currentTimeMillis());

        assertEquals(2, summary.getTotalPorts());
        assertEquals(1, summary.getFreePorts());
        assertTrue(summary.getLabel().contains("1 of 2 ports free"));
        assertTrue(summary.getLastUpdatedMs() > 0);
    }

    @Test
    public void pausedStationsReportTheHostAction() {
        ChargerStation paused = station();
        paused.setPaused(true);
        AvailabilityEngine.AvailabilitySummary summary = AvailabilityEngine.summarise(paused,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), System.currentTimeMillis());
        assertTrue(summary.isPaused());
        assertTrue(summary.getLabel().toLowerCase().contains("paused"));
    }
}
