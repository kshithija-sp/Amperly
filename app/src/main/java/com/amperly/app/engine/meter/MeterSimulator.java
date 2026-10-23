package com.amperly.app.engine.meter;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo meter service used by the prototype instead of live charger hardware.
 *
 * It exposes a cumulative reading, start/end capture, simulated session progression, an invalid
 * reading test and an offline state. Everything it produces is labelled as simulated or manually
 * entered — the app never claims live hardware integration.
 */
public final class MeterSimulator {

    private static final double SEED_READING = 125.40;
    private static final Map<String, Double> CUMULATIVE = new HashMap<>();
    private static final Map<String, Boolean> OFFLINE = new HashMap<>();

    public static class SimulatedSession {
        private final double startReadingKwh;
        private final double endReadingKwh;
        private final double deliveredKwh;
        private final int minutes;
        private final boolean simulated;

        public SimulatedSession(double startReadingKwh, double endReadingKwh, double deliveredKwh,
                                int minutes, boolean simulated) {
            this.startReadingKwh = startReadingKwh;
            this.endReadingKwh = endReadingKwh;
            this.deliveredKwh = deliveredKwh;
            this.minutes = minutes;
            this.simulated = simulated;
        }

        public double getStartReadingKwh() { return startReadingKwh; }
        public double getEndReadingKwh() { return endReadingKwh; }
        public double getDeliveredKwh() { return deliveredKwh; }
        public int getMinutes() { return minutes; }
        public boolean isSimulated() { return simulated; }
    }

    private MeterSimulator() {
    }

    /** Cumulative reading for a station, seeded so demo data looks like a real meter. */
    public static synchronized double currentReading(String stationId) {
        if (stationId == null) {
            return SEED_READING;
        }
        Double stored = CUMULATIVE.get(stationId);
        if (stored == null) {
            stored = SEED_READING;
            CUMULATIVE.put(stationId, stored);
        }
        return stored;
    }

    public static synchronized void setReading(String stationId, double reading) {
        if (stationId != null) {
            CUMULATIVE.put(stationId, reading);
        }
    }

    public static synchronized double beginSession(String stationId) {
        return currentReading(stationId);
    }

    /** Delivers energy at the port power for a duration and advances the cumulative reading. */
    public static synchronized double deliver(String stationId, double powerKw, int minutes) {
        double kwh = (Math.max(0, powerKw) * (Math.max(0, minutes) / 60.0)) * 0.92; // 8% AC/DC loss
        double updated = currentReading(stationId) + round2(kwh);
        setReading(stationId, updated);
        return round2(kwh);
    }

    public static synchronized double endSession(String stationId) {
        return currentReading(stationId);
    }

    public static synchronized SimulatedSession simulateSession(String stationId, double powerKw,
                                                                int minutes) {
        double start = beginSession(stationId);
        double delivered = deliver(stationId, powerKw, minutes);
        double end = endSession(stationId);
        return new SimulatedSession(start, end, delivered, minutes, true);
    }

    public static synchronized boolean isOffline(String stationId) {
        Boolean offline = OFFLINE.get(stationId);
        return offline != null && offline;
    }

    public static synchronized void setOffline(String stationId, boolean offline) {
        if (stationId != null) {
            OFFLINE.put(stationId, offline);
        }
    }

    /** Test scenario: an ending reading below the starting reading must be rejected by billing. */
    public static boolean isValidReading(double startReading, double endReading) {
        return startReading >= 0 && endReading >= startReading;
    }

    /** Test scenario: an implausibly large jump that should be flagged for admin review. */
    public static boolean isImplausibleReading(double startReading, double endReading,
                                              double portPowerKw, int minutes) {
        double physicalMax = Math.max(0.1, portPowerKw) * (Math.max(1, minutes) / 60.0) * 1.15;
        return (endReading - startReading) > physicalMax;
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
