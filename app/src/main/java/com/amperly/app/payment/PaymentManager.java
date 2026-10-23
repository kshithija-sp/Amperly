package com.amperly.app.payment;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.data.local.entity.Payment;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sandbox payment gateway wrapper (the prototype uses Razorpay-style test mode semantics).
 *
 * Only gateway references are ever stored — never raw card details. Authorizations are idempotent so
 * a retry can never create a duplicate booking, and if the gateway cannot be reached the caller must
 * not confirm a paid reservation.
 */
public class PaymentManager {

    public static class PaymentResult {
        private final boolean success;
        private final Payment payment;
        private final String message;

        public PaymentResult(boolean success, Payment payment, String message) {
            this.success = success;
            this.payment = payment;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public Payment getPayment() { return payment; }
        public String getPaymentId() { return payment == null ? "" : payment.getId(); }
        public String getIdempotencyKey() { return payment == null ? "" : payment.getIdempotencyKey(); }
        public double getAmount() { return payment == null ? 0 : payment.getAuthorizedAmount(); }
        public String getMessage() { return message; }
    }

    /** Test hook: flip the gateway offline to prove the failure path. */
    private static volatile boolean gatewayAvailable = true;

    /**
     * Idempotency keys already authorized. Concurrent, because the repository runs work on a
     * pool: a plain HashSet could let two simultaneous retries both pass the contains() check
     * and place two holds on one booking.
     */
    private static final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    public static void setGatewayAvailable(boolean available) {
        gatewayAvailable = available;
    }

    public static boolean isGatewayAvailable() {
        return gatewayAvailable;
    }

    public static synchronized void resetIdempotencyCache() {
        processedKeys.clear();
    }

    /**
     * Authorizes the estimated amount before a reservation is confirmed. Retrying with the same
     * idempotency key returns the original authorization instead of charging twice.
     */
    /**
     * Serialised on purpose: checking and then recording an idempotency key must be one
     * atomic step, or a retry that races the original can authorize twice.
     */
    public static synchronized PaymentResult authorize(String consumerId, String hostId,
                                                        String reservationId, double amount,
                                                        String idempotencyKey) {
        long now = System.currentTimeMillis();

        if (idempotencyKey != null && processedKeys.contains(idempotencyKey)) {
            Payment existing = new Payment("PAY_IDEMPOTENT_" + idempotencyKey, reservationId, null,
                    consumerId, hostId, idempotencyKey, "SANDBOX", "auth_" + idempotencyKey, amount,
                    0, 0, 0, AppConstants.PAYMENT_AUTHORIZED, null, now, now);
            return new PaymentResult(true, existing,
                    "This authorization already exists, so no second charge was made.");
        }

        if (!gatewayAvailable) {
            Payment failed = new Payment(UUID.randomUUID().toString(), reservationId, null,
                    consumerId, hostId, idempotencyKey, "SANDBOX", null, amount, 0, 0, 0,
                    AppConstants.PAYMENT_FAILED, "Gateway unreachable", now, now);
            return new PaymentResult(false, failed,
                    "The payment gateway could not be reached, so no paid reservation was created. "
                            + "You can retry safely — no duplicate booking will be made.");
        }

        if (idempotencyKey != null) {
            processedKeys.add(idempotencyKey);
        }
        String reference = "auth_" + UUID.randomUUID().toString().substring(0, 10);
        Payment payment = new Payment(UUID.randomUUID().toString(), reservationId, null, consumerId,
                hostId, idempotencyKey, "SANDBOX", reference, amount, 0, 0, 0,
                AppConstants.PAYMENT_AUTHORIZED, null, now, now);
        return new PaymentResult(true, payment, "Sandbox payment authorized.");
    }

    /** Captures the verified amount after meter validation. */
    public static PaymentResult capture(Payment payment, String sessionId, double verifiedAmount) {
        long now = System.currentTimeMillis();
        if (payment == null) {
            return new PaymentResult(false, null, "No authorization found for this session.");
        }
        if (AppConstants.PAYMENT_FAILED.equals(payment.getStatus())) {
            return new PaymentResult(false, payment,
                    "This authorization failed earlier and cannot be captured.");
        }
        payment.setSessionId(sessionId);
        payment.setCapturedAmount(round2(Math.max(0, verifiedAmount)));
        payment.setStatus(AppConstants.PAYMENT_CAPTURED);
        payment.setUpdatedAtMs(now);
        return new PaymentResult(true, payment,
                "Captured " + format(payment.getCapturedAmount()) + " based on verified meter readings.");
    }

    /** Refund all or part of a captured amount after a dispute is upheld. */
    public static PaymentResult refund(Payment payment, double amount, String reason) {
        long now = System.currentTimeMillis();
        if (payment == null) {
            return new PaymentResult(false, null, "Nothing to refund.");
        }
        double refundable = payment.getCapturedAmount() - payment.getRefundedAmount();
        if (refundable <= 0) {
            return new PaymentResult(false, payment, "The captured amount has already been refunded.");
        }
        double applied = Math.min(Math.max(0, amount), refundable);
        payment.setRefundedAmount(round2(payment.getRefundedAmount() + applied));
        payment.setStatus(AppConstants.PAYMENT_REFUNDED);
        payment.setFailureReason(null);
        payment.setUpdatedAtMs(now);
        return new PaymentResult(true, payment, "Refunded " + format(applied) + ". " + reason);
    }

    /** Releases an unused authorization: no energy was consumed. */
    public static PaymentResult release(Payment payment, double lateCancellationFee) {
        long now = System.currentTimeMillis();
        if (payment == null) {
            return new PaymentResult(true, null, "No authorization to release.");
        }
        payment.setStatus(lateCancellationFee > 0 ? AppConstants.PAYMENT_CAPTURED : AppConstants.PAYMENT_RELEASED);
        payment.setCapturedAmount(round2(lateCancellationFee));
        payment.setLateCancellationFee(round2(lateCancellationFee));
        payment.setUpdatedAtMs(now);
        String message = lateCancellationFee > 0
                ? "Released the energy authorization; the disclosed " + format(lateCancellationFee)
                + " late-cancellation fee was applied."
                : "Released the unused authorization. No energy charge applies.";
        return new PaymentResult(true, payment, message);
    }

    public static String newIdempotencyKey(String consumerId, String stationId, long slotStartMs) {
        return consumerId + ":" + stationId + ":" + slotStartMs;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String format(double amount) {
        return String.format(java.util.Locale.US, "₹%.2f", amount);
    }
}
