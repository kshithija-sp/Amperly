package com.amperly.app.engine.availability;

import com.amperly.app.core.common.Formatters;
import com.amperly.app.data.local.entity.AvailabilitySchedule;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Reservation;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * The MVP has no charger API. Availability is therefore derived from host-provided schedules,
 * reservation records, port status and maintenance blocks — and it is always shown with an honest
 * last-updated timestamp rather than a claimed live hardware state.
 */
public final class AvailabilityEngine {

    public static final int DEFAULT_SLOT_MINUTES = 90;

    public static class Slot {
        private final String portId;
        private final String portLabel;
        private final long startMs;
        private final long endMs;
        private final boolean available;
        private final String reason;

        public Slot(String portId, String portLabel, long startMs, long endMs, boolean available,
                    String reason) {
            this.portId = portId;
            this.portLabel = portLabel;
            this.startMs = startMs;
            this.endMs = endMs;
            this.available = available;
            this.reason = reason;
        }

        public String getPortId() { return portId; }
        public String getPortLabel() { return portLabel; }
        public long getStartMs() { return startMs; }
        public long getEndMs() { return endMs; }
        public boolean isAvailable() { return available; }
        public String getReason() { return reason; }

        public String label() {
            return Formatters.dayAndSlot(startMs, endMs);
        }
    }

    public static class AvailabilitySummary {
        private final int totalPorts;
        private final int freePorts;
        private final long nextFreeSlotMs;
        private final long lastUpdatedMs;
        private final boolean paused;
        private final String label;

        public AvailabilitySummary(int totalPorts, int freePorts, long nextFreeSlotMs,
                                   long lastUpdatedMs, boolean paused, String label) {
            this.totalPorts = totalPorts;
            this.freePorts = freePorts;
            this.nextFreeSlotMs = nextFreeSlotMs;
            this.lastUpdatedMs = lastUpdatedMs;
            this.paused = paused;
            this.label = label;
        }

        public int getTotalPorts() { return totalPorts; }
        public int getFreePorts() { return freePorts; }
        public long getNextFreeSlotMs() { return nextFreeSlotMs; }
        public long getLastUpdatedMs() { return lastUpdatedMs; }
        public boolean isPaused() { return paused; }
        public String getLabel() { return label; }
    }

    private AvailabilityEngine() {
    }

    public static List<Slot> generateSlots(ChargerStation station, List<ChargerPort> ports,
                                           List<AvailabilitySchedule> schedules,
                                           List<Reservation> reservations, long dayStartMs,
                                           int slotMinutes, int maxSlots) {
        List<Slot> slots = new ArrayList<>();
        if (station == null || ports == null || ports.isEmpty()) {
            return slots;
        }
        if (station.isPaused()) {
            return slots;
        }

        boolean[] seenStart = new boolean[(24 * 60 / Math.max(15, slotMinutes)) + 2];
        for (int index = 0; index < maxSlots; index++) {
            long startMs = dayStartMs + ((long) index * slotMinutes * 60_000L);
            long endMs = startMs + (slotMinutes * 60_000L);
            if (startMs < System.currentTimeMillis()) {
                continue;
            }
            int startOfDayIndex = (int) (((startMs - dayStartMs) / 60000L) / Math.max(15, slotMinutes));
            if (startOfDayIndex >= 0 && startOfDayIndex < seenStart.length) {
                if (seenStart[startOfDayIndex]) {
                    continue;
                }
                seenStart[startOfDayIndex] = true;
            }

            boolean anyFree = false;
            String reason = null;
            String chosenPortId = null;
            String chosenPortLabel = null;

            for (ChargerPort port : ports) {
                if (!isPortUsable(port)) {
                    reason = "Port " + port.getPortNumber() + " is " + port.getStatus().toLowerCase();
                    continue;
                }
                AvailabilitySchedule schedule = scheduleFor(schedules, port.getId(), startMs);
                if (schedule == null) {
                    reason = "No published opening hours for this port";
                    continue;
                }
                if (schedule.isBlocked()) {
                    reason = "Blocked by the host: " + (schedule.getBlockReason() == null
                            ? "maintenance" : schedule.getBlockReason());
                    continue;
                }
                if (!withinWindow(schedule, startMs, endMs)) {
                    reason = "Outside published hours";
                    continue;
                }
                if (hasConflict(reservations, port.getId(), startMs, endMs)) {
                    reason = "Already reserved";
                    continue;
                }
                anyFree = true;
                chosenPortId = port.getId();
                chosenPortLabel = portLabel(port);
                break;
            }

            if (anyFree) {
                slots.add(new Slot(chosenPortId, chosenPortLabel, startMs, endMs, true,
                        "Free for a " + slotMinutes + "-minute slot"));
            } else if (reason != null) {
                slots.add(new Slot(null, null, startMs, endMs, false, reason));
            }
        }
        return slots;
    }

    public static AvailabilitySummary summarise(ChargerStation station, List<ChargerPort> ports,
                                                List<AvailabilitySchedule> schedules,
                                                List<Reservation> reservations, long nowMs) {
        int total = ports == null ? 0 : ports.size();
        int free = 0;
        for (ChargerPort port : ports == null ? Collections.<ChargerPort>emptyList() : ports) {
            boolean busy = false;
            for (Reservation reservation : reservations == null ? Collections.<Reservation>emptyList() : reservations) {
                if (port.getId().equals(reservation.getPortId()) && reservation.isActive()
                        && nowMs >= reservation.getStartTimeMs() && nowMs <= reservation.getEndTimeMs()) {
                    busy = true;
                    break;
                }
            }
            if (isPortUsable(port) && !busy) {
                free++;
            }
        }

        long nextFree = 0;
        if (free == 0 && station != null) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(nowMs);
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            for (int dayOffset = 0; dayOffset < 7 && nextFree == 0; dayOffset++) {
                long dayStart = day.getTimeInMillis() + (dayOffset * 24L * 60L * 60L * 1000L);
                List<Slot> slots = generateSlots(station, ports, schedules, reservations, dayStart,
                        DEFAULT_SLOT_MINUTES, 16);
                for (Slot slot : slots) {
                    if (slot.isAvailable()) {
                        nextFree = slot.getStartMs();
                        break;
                    }
                }
            }
        }

        boolean paused = station != null && station.isPaused();
        String label;
        if (paused) {
            label = "Paused by the host";
        } else if (total == 0) {
            label = "No ports published";
        } else if (free == total) {
            label = "All " + total + " ports free";
        } else if (free > 0) {
            label = free + " of " + total + " ports free";
        } else {
            label = "All ports busy — next free " + (nextFree > 0 ? Formatters.clock(nextFree) : "unavailable");
        }

        long lastUpdated = station == null ? nowMs : station.getLastUpdatedMs();
        return new AvailabilitySummary(total, free, nextFree, lastUpdated, paused, label);
    }

    public static boolean hasConflict(List<Reservation> reservations, String portId,
                                      long startMs, long endMs) {
        if (reservations == null) {
            return false;
        }
        for (Reservation reservation : reservations) {
            if (portId != null && !portId.equals(reservation.getPortId())) {
                continue;
            }
            if (!reservation.isActive()) {
                continue;
            }
            if (reservation.getStartTimeMs() < endMs && reservation.getEndTimeMs() > startMs) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPortUsable(ChargerPort port) {
        return port != null && "AVAILABLE".equalsIgnoreCase(port.getStatus());
    }

    private static AvailabilitySchedule scheduleFor(List<AvailabilitySchedule> schedules,
                                                    String portId, long startMs) {
        if (schedules == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startMs);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        for (AvailabilitySchedule schedule : schedules) {
            if (portId.equals(schedule.getPortId()) && schedule.getDayOfWeek() == dayOfWeek) {
                return schedule;
            }
        }
        return null;
    }

    private static boolean withinWindow(AvailabilitySchedule schedule, long startMs, long endMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startMs);
        int startMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        calendar.setTimeInMillis(endMs);
        int endMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        if (endMinutes <= startMinutes) {
            endMinutes = 24 * 60;
        }
        return startMinutes >= schedule.getOpenMinutes() && endMinutes <= schedule.getCloseMinutes();
    }

    public static String portLabel(ChargerPort port) {
        if (port == null) {
            return "";
        }
        return "Port " + port.getPortNumber() + " · " + port.getConnectorType()
                + " · " + (int) port.getPowerKw() + " kW" + (port.isDc() ? " DC" : " AC");
    }
}
