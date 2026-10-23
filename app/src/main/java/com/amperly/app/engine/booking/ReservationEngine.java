package com.amperly.app.engine.booking;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.Vehicle;

import java.util.Locale;
import java.security.SecureRandom;

/**
 * Reservation rules that do not depend on Android: conflict prevention, meter gating, published
 * policy copy and the price preview shown before payment authorization.
 */
public final class ReservationEngine {

    public static class BookingCheck {
        private final boolean allowed;
        private final String reason;
        private final boolean requiresPaidAuthorization;

        public BookingCheck(boolean allowed, String reason, boolean requiresPaidAuthorization) {
            this.allowed = allowed;
            this.reason = reason;
            this.requiresPaidAuthorization = requiresPaidAuthorization;
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public boolean isRequiresPaidAuthorization() { return requiresPaidAuthorization; }
    }

    public static class PricePreview {
        private final double estimatedKwh;
        private final double energyCharge;
        private final double serviceFee;
        private final double gatewayFee;
        private final double estimatedTotal;
        private final double authorizedAmount;

        public PricePreview(double estimatedKwh, double energyCharge, double serviceFee,
                            double gatewayFee, double estimatedTotal, double authorizedAmount) {
            this.estimatedKwh = estimatedKwh;
            this.energyCharge = energyCharge;
            this.serviceFee = serviceFee;
            this.gatewayFee = gatewayFee;
            this.estimatedTotal = estimatedTotal;
            this.authorizedAmount = authorizedAmount;
        }

        public double getEstimatedKwh() { return estimatedKwh; }
        public double getEnergyCharge() { return energyCharge; }
        public double getServiceFee() { return serviceFee; }
        public double getGatewayFee() { return gatewayFee; }
        public double getEstimatedTotal() { return estimatedTotal; }
        public double getAuthorizedAmount() { return authorizedAmount; }
    }

    public static final String CANCELLATION_POLICY =
            "• Free cancellation until 60 minutes before the slot.\n"
                    + "• A disclosed ₹20 late-cancellation fee may apply after that window.\n"
                    + "• No energy charge is applied when no energy was consumed.\n"
                    + "• Unused payment authorization is released or refunded through the gateway.\n"
                    + "• No hidden penalties: energy, service and gateway charges are always itemised.";

    private ReservationEngine() {
    }

    public static BookingCheck check(ChargerStation station, ChargerPort port, Vehicle vehicle,
                                     long startMs, long endMs, int conflictingReservations,
                                     boolean stationPaused) {
        if (station == null) {
            return new BookingCheck(false, "Station not found.", false);
        }
        if (stationPaused || station.isPaused()) {
            return new BookingCheck(false, "This station is paused by the host right now.", false);
        }
        if (!"APPROVED".equals(station.getApprovalStatus())) {
            return new BookingCheck(false, "This station is still awaiting admin verification.", false);
        }
        if (port == null) {
            return new BookingCheck(false, "Select an available port first.", false);
        }
        if (!AppConstants.PORT_AVAILABLE.equalsIgnoreCase(port.getStatus())) {
            return new BookingCheck(false, "Port " + port.getPortNumber() + " is "
                    + port.getStatus().toLowerCase() + " and cannot be reserved.", false);
        }
        if (endMs <= startMs) {
            return new BookingCheck(false, "The selected slot is invalid.", false);
        }
        if (conflictingReservations > 0) {
            return new BookingCheck(false,
                    "Another reservation already occupies this port in that window. Amperly prevents double booking.", false);
        }
        // The port the consumer actually selected has to fit the vehicle, even when the station hosts
        // several connector types.
        if (vehicle != null && vehicle.getConnectorType() != null && port.getConnectorType() != null
                && !port.getConnectorType().equalsIgnoreCase(vehicle.getConnectorType())) {
            return new BookingCheck(false, "Port " + port.getPortNumber() + " ("
                    + port.getConnectorType() + ") is not compatible with your vehicle connector ("
                    + vehicle.getConnectorType() + ").", false);
        }
        if (vehicle != null && station.getConnectorTypes() != null
                && !com.amperly.app.engine.routing.RoutePlannerEngine.isConnectorCompatible(station, vehicle)) {
            return new BookingCheck(false, "This station publishes " + station.getConnectorTypes()
                    + ", which does not match your vehicle connector ("
                    + vehicle.getConnectorType() + ").", false);
        }
        boolean paid = station.supportsPaidEnergyBilling();
        if (!paid) {
            return new BookingCheck(true,
                    "Reservation confirmed without automatic billing: this station has no verified meter, so no energy-based payment is captured.", false);
        }
        return new BookingCheck(true, "Slot is free and the station can bill a verified energy session.", true);
    }

    public static PricePreview preview(ChargerStation station, double estimatedKwh) {
        double tariff = station != null ? station.getPricePerKwh() : AppConstants.DEFAULT_TARIFF_INR_PER_KWH;
        double service = station != null ? station.getServiceFee() : AppConstants.DEFAULT_SERVICE_FEE_INR;
        double gateway = station != null ? station.getGatewayFee() : AppConstants.DEFAULT_GATEWAY_FEE_INR;
        double energy = estimatedKwh * tariff;
        double total = energy + service + gateway;
        // Authorize a little headroom, then capture only the verified amount.
        return new PricePreview(estimatedKwh, round2(energy), service, gateway, round2(total),
                round2(total * 1.2));
    }

    /**
     * A booking code is a bearer credential: the host checks the car in by looking it up.
     *
     * <p>A four-digit code drawn from {@code java.util.Random} was both guessable in 9,000
     * attempts and predictable from the previous code. This draws six characters from a
     * 31-symbol alphabet with {@link SecureRandom} — about 30 bits — while staying easy to
     * read aloud and type. The ambiguous characters I, L, O, 0 and 1 are left out.
     */
    private static final char[] CODE_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String buildBookingCode() {
        StringBuilder code = new StringBuilder("AMP-");
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET[SECURE_RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    public static String bookingQrPayload(Reservation reservation) {
        if (reservation == null) {
            return "amperly://booking/unknown";
        }
        return String.format(Locale.US, "amperly://booking/%s?station=%s&port=%s&start=%d&end=%d",
                reservation.getBookingCode(), reservation.getStationId(), reservation.getPortId(),
                reservation.getStartTimeMs(), reservation.getEndTimeMs());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
