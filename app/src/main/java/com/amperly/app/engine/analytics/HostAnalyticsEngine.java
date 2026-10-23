package com.amperly.app.engine.analytics;

import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.ChargingSession;
import com.amperly.app.data.local.entity.Dispute;
import com.amperly.app.data.local.entity.Invoice;
import com.amperly.app.data.local.entity.Reservation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Operational numbers for hosts and admins. Utilization is explicitly labelled as an estimate
 * because the MVP has no charger telemetry to prove idle time.
 */
public final class HostAnalyticsEngine {

    public static class HostAnalytics {
        private final int sessionsCompleted;
        private final double energyDeliveredKwh;
        private final double utilizationPercent;
        private final double revenue;
        private final double refundsAndAdjustments;
        private final int repeatConsumers;
        private final double cancellationRatePercent;
        private final double averageSessionKwh;
        private final int disputes;
        private final int openDisputes;
        private final int activeReservations;
        private final int ports;
        private final String utilizationNote;

        public HostAnalytics(int sessionsCompleted, double energyDeliveredKwh, double utilizationPercent,
                             double revenue, double refundsAndAdjustments, int repeatConsumers,
                             double cancellationRatePercent, double averageSessionKwh, int disputes,
                             int openDisputes, int activeReservations, int ports, String utilizationNote) {
            this.sessionsCompleted = sessionsCompleted;
            this.energyDeliveredKwh = energyDeliveredKwh;
            this.utilizationPercent = utilizationPercent;
            this.revenue = revenue;
            this.refundsAndAdjustments = refundsAndAdjustments;
            this.repeatConsumers = repeatConsumers;
            this.cancellationRatePercent = cancellationRatePercent;
            this.averageSessionKwh = averageSessionKwh;
            this.disputes = disputes;
            this.openDisputes = openDisputes;
            this.activeReservations = activeReservations;
            this.ports = ports;
            this.utilizationNote = utilizationNote;
        }

        public int getSessionsCompleted() { return sessionsCompleted; }
        public double getEnergyDeliveredKwh() { return energyDeliveredKwh; }
        public double getUtilizationPercent() { return utilizationPercent; }
        public double getRevenue() { return revenue; }
        public double getRefundsAndAdjustments() { return refundsAndAdjustments; }
        public int getRepeatConsumers() { return repeatConsumers; }
        public double getCancellationRatePercent() { return cancellationRatePercent; }
        public double getAverageSessionKwh() { return averageSessionKwh; }
        public int getDisputes() { return disputes; }
        public int getOpenDisputes() { return openDisputes; }
        public int getActiveReservations() { return activeReservations; }
        public int getPorts() { return ports; }
        public String getUtilizationNote() { return utilizationNote; }
    }

    private HostAnalyticsEngine() {
    }

    public static HostAnalytics compute(List<ChargerStation> stations, List<ChargerPort> ports,
                                        List<ChargingSession> sessions, List<Reservation> reservations,
                                        List<Invoice> invoices, List<Dispute> disputes) {
        int sessionsCompleted = 0;
        double energy = 0;
        double minutes = 0;
        Map<String, Integer> consumerCounts = new HashMap<>();

        if (sessions != null) {
            for (ChargingSession session : sessions) {
                if ("COMPLETED".equals(session.getStatus()) || "DISPUTED".equals(session.getStatus())
                        || "REFUNDED".equals(session.getStatus())) {
                    sessionsCompleted++;
                    energy += Math.max(0, session.getVerifiedKwhConsumed());
                    long durationMs = Math.max(0, session.getEndTimeMs() - session.getStartTimeMs());
                    minutes += durationMs / 60000.0;
                    if (session.getConsumerId() != null) {
                        Integer current = consumerCounts.get(session.getConsumerId());
                        consumerCounts.put(session.getConsumerId(), current == null ? 1 : current + 1);
                    }
                }
            }
        }

        int repeatConsumers = 0;
        for (Integer count : consumerCounts.values()) {
            if (count != null && count > 1) {
                repeatConsumers++;
            }
        }

        double revenue = 0;
        double adjustments = 0;
        if (invoices != null) {
            for (Invoice invoice : invoices) {
                revenue += invoice.getFinalTotal();
                adjustments += invoice.getAdjustment();
            }
        }

        int cancelled = 0;
        int activeReservations = 0;
        Set<String> allReservations = new HashSet<>();
        if (reservations != null) {
            for (Reservation reservation : reservations) {
                allReservations.add(reservation.getId());
                if ("CANCELLED".equals(reservation.getStatus()) || "EXPIRED".equals(reservation.getStatus())) {
                    cancelled++;
                }
                if (reservation.isActive()) {
                    activeReservations++;
                }
            }
        }

        int disputeCount = disputes == null ? 0 : disputes.size();
        int openDisputes = 0;
        if (disputes != null) {
            for (Dispute dispute : disputes) {
                if (dispute.isOpen()) {
                    openDisputes++;
                }
            }
        }

        int portCount = ports == null ? 0 : ports.size();
        // Assume a 12-hour bookable day per port over the last 30 days.
        double availablePortMinutes = Math.max(1.0, portCount * 12.0 * 60.0 * 30.0);
        double utilization = Math.min(100.0, (minutes / availablePortMinutes) * 100.0);
        double cancellationRate = allReservations.isEmpty() ? 0.0
                : (cancelled * 100.0) / allReservations.size();
        double averageSession = sessionsCompleted == 0 ? 0.0 : energy / sessionsCompleted;

        String note = "Utilization is estimated from recorded session time over a 12-hour bookable day "
                + "per port for the last 30 days. The MVP has no charger telemetry, so idle time is not measured directly.";

        return new HostAnalytics(sessionsCompleted, round2(energy), round2(utilization),
                round2(revenue), round2(adjustments), repeatConsumers, round2(cancellationRate),
                round2(averageSession), disputeCount, openDisputes, activeReservations, portCount, note);
    }

    public static String describe(HostAnalytics analytics) {
        return String.format(Locale.US,
                "Sessions: %d · Energy: %.1f kWh · Utilization: %.0f%% · Revenue: ₹%.2f",
                analytics.getSessionsCompleted(), analytics.getEnergyDeliveredKwh(),
                analytics.getUtilizationPercent(), analytics.getRevenue());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
