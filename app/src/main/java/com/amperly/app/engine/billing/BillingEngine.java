package com.amperly.app.engine.billing;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Invoice;
import com.amperly.app.data.local.entity.Reservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Meter-first billing.
 *
 * The final amount is always based on verified meter readings. Planning estimates, reservation
 * duration and charging duration never determine what the consumer pays. Stations without a verified
 * meter cannot run automatic energy-based paid sessions at all.
 */
public class BillingEngine {

    public static class BillingLine {
        private final String label;
        private final String value;

        public BillingLine(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() { return label; }
        public String getValue() { return value; }
    }

    public static class BillingCalculationResult {
        private final boolean valid;
        private final String errorMessage;
        private final double startMeterKwh;
        private final double endMeterKwh;
        private final double verifiedKwh;
        private final double energyCharge;
        private final double serviceFee;
        private final double gatewayFee;
        private final double adjustment;
        private final double finalTotal;
        private final boolean simulated;
        private final boolean flaggedForReview;
        private final List<BillingLine> lines;
        private final Invoice invoice;

        public BillingCalculationResult(boolean valid, String errorMessage, double startMeterKwh,
                                        double endMeterKwh, double verifiedKwh, double energyCharge,
                                        double serviceFee, double gatewayFee, double adjustment,
                                        double finalTotal, boolean simulated, boolean flaggedForReview,
                                        List<BillingLine> lines, Invoice invoice) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.startMeterKwh = startMeterKwh;
            this.endMeterKwh = endMeterKwh;
            this.verifiedKwh = verifiedKwh;
            this.energyCharge = energyCharge;
            this.serviceFee = serviceFee;
            this.gatewayFee = gatewayFee;
            this.adjustment = adjustment;
            this.finalTotal = finalTotal;
            this.simulated = simulated;
            this.flaggedForReview = flaggedForReview;
            this.lines = lines;
            this.invoice = invoice;
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public double getStartMeterKwh() { return startMeterKwh; }
        public double getEndMeterKwh() { return endMeterKwh; }
        public double getVerifiedKwh() { return verifiedKwh; }
        public double getEnergyCharge() { return energyCharge; }
        public double getServiceFee() { return serviceFee; }
        public double getGatewayFee() { return gatewayFee; }
        public double getAdjustment() { return adjustment; }
        public double getFinalTotal() { return finalTotal; }
        public boolean isSimulated() { return simulated; }
        public boolean isFlaggedForReview() { return flaggedForReview; }
        public List<BillingLine> getLines() { return lines; }
        public Invoice getInvoice() { return invoice; }
    }

    public static class CancellationResult {
        private final boolean free;
        private final double fee;
        private final double refundAmount;
        private final String message;

        public CancellationResult(boolean free, double fee, double refundAmount, String message) {
            this.free = free;
            this.fee = fee;
            this.refundAmount = refundAmount;
            this.message = message;
        }

        public boolean isFree() { return free; }
        public double getFee() { return fee; }
        public double getRefundAmount() { return refundAmount; }
        public String getMessage() { return message; }
    }

    private BillingEngine() {
    }

    public static BillingCalculationResult calculateBill(String sessionId, String consumerId,
                                                         String hostId, String stationName,
                                                         ChargerStation station, String meterLevel,
                                                         boolean simulated, double startMeterKwh,
                                                         double endMeterKwh) {
        List<BillingLine> lines = new ArrayList<>();

        if (!isVerifiedMeterLevel(meterLevel)) {
            return invalid("This station has no verified meter, so automatic energy-based paid billing is not available. The station stays visible for discovery and can only take a reservation without automatic billing.");
        }
        if (startMeterKwh < 0) {
            return invalid("Starting meter reading must not be negative.");
        }
        if (endMeterKwh < startMeterKwh) {
            return invalid("Ending meter reading cannot be lower than the starting meter reading.");
        }

        double verifiedKwh = round2(endMeterKwh - startMeterKwh);
        double tariff = station != null ? station.getPricePerKwh() : AppConstants.DEFAULT_TARIFF_INR_PER_KWH;
        double serviceFee = station != null ? station.getServiceFee() : AppConstants.DEFAULT_SERVICE_FEE_INR;
        double gatewayFee = station != null ? station.getGatewayFee() : AppConstants.DEFAULT_GATEWAY_FEE_INR;

        double energyCharge = round2(verifiedKwh * tariff);
        boolean noEnergy = verifiedKwh <= 0.0;
        if (noEnergy) {
            // Published rule: no energy charge when no energy was consumed.
            serviceFee = 0;
            gatewayFee = 0;
        }
        double finalTotal = round2(energyCharge + serviceFee + gatewayFee);
        boolean flagged = verifiedKwh > 150.0;

        lines.add(new BillingLine("Starting meter reading", String.format(Locale.US, "%.2f kWh", startMeterKwh)));
        lines.add(new BillingLine("Ending meter reading", String.format(Locale.US, "%.2f kWh", endMeterKwh)));
        lines.add(new BillingLine("Verified energy consumed", String.format(Locale.US, "%.2f kWh", verifiedKwh)));
        lines.add(new BillingLine("Energy charge", money(energyCharge) + "  (" + money(tariff) + "/kWh)"));
        lines.add(new BillingLine("Amperly service fee", money(serviceFee)));
        lines.add(new BillingLine("Payment gateway charge", money(gatewayFee)));
        lines.add(new BillingLine("Final amount", money(finalTotal)));
        if (simulated) {
            // The spec requires this to be unmissable on the receipt, so it carries its own label.
            lines.add(new BillingLine("Demo meter", "Simulated reading — not live charger hardware"));
        }
        if (flagged) {
            lines.add(new BillingLine("Review", "Reading is unusually large and has been flagged for admin review"));
        }

        Invoice invoice = new Invoice(UUID.randomUUID().toString(), sessionId, consumerId, hostId,
                stationName, verifiedKwh, energyCharge, serviceFee, gatewayFee, 0.0, "",
                finalTotal, "", "ISSUED", System.currentTimeMillis());

        return new BillingCalculationResult(true, null, startMeterKwh, endMeterKwh, verifiedKwh,
                energyCharge, serviceFee, gatewayFee, 0.0, finalTotal, simulated, flagged, lines, invoice);
    }

    /** The published cancellation policy: free until 60 minutes before the slot. */
    public static CancellationResult calculateCancellation(Reservation reservation, long nowMs) {
        return calculateCancellation(reservation, nowMs, AppConstants.LATE_CANCELLATION_FEE_INR);
    }

    public static CancellationResult calculateCancellation(Reservation reservation, long nowMs,
                                                           double lateFee) {
        if (reservation == null) {
            return new CancellationResult(true, 0, 0, "Nothing to cancel.");
        }
        boolean free = reservation.isInFreeCancellationWindow(nowMs);
        if (free) {
            return new CancellationResult(true, 0, 0,
                    "Cancelled free of charge. Any unused payment authorization is released, and no energy charge applies because no energy was consumed.");
        }
        return new CancellationResult(false, lateFee, 0,
                "Late cancellation: the disclosed " + money(lateFee)
                        + " reservation-protection fee applies. No energy charge applies because no energy was consumed.");
    }

    public static Invoice applyRefund(Invoice invoice, double refundAmount, String note) {
        if (invoice == null) {
            return null;
        }
        double amount = round2(Math.max(0, refundAmount));
        invoice.setAdjustment(round2(invoice.getAdjustment() + amount));
        invoice.setAdjustmentNote(note);
        invoice.setFinalTotal(round2(Math.max(0,
                invoice.getEnergyCharge() + invoice.getServiceFee() + invoice.getGatewayFee()
                        - invoice.getAdjustment())));
        if (invoice.getAdjustment() >= invoice.getEnergyCharge() + invoice.getServiceFee() + invoice.getGatewayFee()) {
            invoice.setStatus("REFUNDED");
        } else if (invoice.getAdjustment() > 0) {
            invoice.setStatus("PARTIALLY_REFUNDED");
        }
        return invoice;
    }

    private static BillingCalculationResult invalid(String message) {
        return new BillingCalculationResult(false, message, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                new ArrayList<>(), null);
    }

    public static boolean isVerifiedMeterLevel(String meterLevel) {
        return AppConstants.LEVEL_1_VERIFIED_MANUAL.equals(meterLevel)
                || AppConstants.LEVEL_2_SIMULATED_SMART_METER.equals(meterLevel);
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static String money(double amount) {
        return String.format(Locale.US, "₹%.2f", amount);
    }
}
