package com.amperly.app;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.data.local.entity.Payment;
import com.amperly.app.payment.PaymentManager;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PaymentManagerTest {

    @Before
    public void setUp() {
        PaymentManager.resetIdempotencyCache();
        PaymentManager.setGatewayAvailable(true);
    }

    @Test
    public void authorizationSucceedsInSandboxMode() {
        PaymentManager.PaymentResult result = PaymentManager.authorize("cons_1", "host_1", "res_1",
                226.80, "key-1");

        assertTrue(result.isSuccess());
        assertNotNull(result.getPayment());
        assertEquals(AppConstants.PAYMENT_AUTHORIZED, result.getPayment().getStatus());
        assertEquals(226.80, result.getPayment().getAuthorizedAmount(), 0.001);
        assertEquals(0.0, result.getPayment().getCapturedAmount(), 0.001);
    }

    @Test
    public void retryingWithTheSameKeyNeverChargesTwice() {
        PaymentManager.PaymentResult first = PaymentManager.authorize("cons_1", "host_1", "res_1",
                200.0, "key-dup");
        PaymentManager.PaymentResult second = PaymentManager.authorize("cons_1", "host_1", "res_1",
                200.0, "key-dup");

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getIdempotencyKey(), second.getIdempotencyKey());
        assertTrue(second.getMessage().toLowerCase().contains("already exists"));
    }

    @Test
    public void gatewayFailureProducesNoPaidReservation() {
        PaymentManager.setGatewayAvailable(false);
        PaymentManager.PaymentResult result = PaymentManager.authorize("cons_1", "host_1", "res_1",
                200.0, "key-fail");

        assertFalse(result.isSuccess());
        assertNotNull(result.getPayment());
        assertEquals(AppConstants.PAYMENT_FAILED, result.getPayment().getStatus());
        assertTrue(result.getMessage().toLowerCase().contains("no paid reservation"));
    }

    @Test
    public void captureUsesTheVerifiedAmountNotTheEstimate() {
        PaymentManager.PaymentResult auth = PaymentManager.authorize("cons_1", "host_1", "res_1",
                226.80, "key-2");
        PaymentManager.PaymentResult capture = PaymentManager.capture(auth.getPayment(), "sess_1", 189.00);

        assertTrue(capture.isSuccess());
        assertEquals(AppConstants.PAYMENT_CAPTURED, capture.getPayment().getStatus());
        assertEquals(189.00, capture.getPayment().getCapturedAmount(), 0.001);
        // The authorization still records what was authorized; only the capture follows the meter.
        assertEquals(226.80, capture.getPayment().getAuthorizedAmount(), 0.001);
    }

    @Test
    public void refundIsCappedAtTheCapturedAmount() {
        PaymentManager.PaymentResult auth = PaymentManager.authorize("cons_1", "host_1", "res_1",
                226.80, "key-3");
        Payment payment = auth.getPayment();
        PaymentManager.capture(payment, "sess_1", 189.00);

        PaymentManager.PaymentResult refund = PaymentManager.refund(payment, 500.0, "Upheld dispute.");
        assertEquals(189.00, refund.getPayment().getRefundedAmount(), 0.001);
        assertEquals(AppConstants.PAYMENT_REFUNDED, refund.getPayment().getStatus());
    }

    @Test
    public void releasingAnUnusedAuthorizationAppliesOnlyTheDisclosedLateFee() {
        PaymentManager.PaymentResult auth = PaymentManager.authorize("cons_1", "host_1", "res_1",
                226.80, "key-4");
        PaymentManager.PaymentResult release = PaymentManager.release(auth.getPayment(),
                AppConstants.LATE_CANCELLATION_FEE_INR);

        assertTrue(release.isSuccess());
        assertEquals(AppConstants.LATE_CANCELLATION_FEE_INR,
                release.getPayment().getLateCancellationFee(), 0.001);
        assertEquals(AppConstants.PAYMENT_CAPTURED, release.getPayment().getStatus());
        assertTrue(release.getMessage().contains("late-cancellation"));
    }

    @Test
    public void releasedAuthorizationWithNoFeeIsFullyReleased() {
        PaymentManager.PaymentResult auth = PaymentManager.authorize("cons_1", "host_1", "res_1",
                226.80, "key-5");
        PaymentManager.PaymentResult release = PaymentManager.release(auth.getPayment(), 0);

        assertEquals(AppConstants.PAYMENT_RELEASED, release.getPayment().getStatus());
        assertEquals(0.0, release.getPayment().getCapturedAmount(), 0.001);
    }

    @Test
    public void idempotencyKeyIsStableForTheSameSlot() {
        String first = PaymentManager.newIdempotencyKey("cons_1", "st_1", 1000L);
        String second = PaymentManager.newIdempotencyKey("cons_1", "st_1", 1000L);
        assertEquals(first, second);
    }
}
