package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Sandbox gateway record. Only gateway references are stored — never raw card details. The
 * idempotency key prevents duplicate reservations when a request is retried after a timeout.
 */
@Entity(tableName = "payments")
public class Payment {
    @PrimaryKey
    @NonNull
    private String id;
    private String reservationId;
    private String sessionId;
    private String consumerId;
    private String hostId;
    private String idempotencyKey;
    private String gateway;
    private String gatewayReference;
    private double authorizedAmount;
    private double capturedAmount;
    private double refundedAmount;
    private double lateCancellationFee;
    private String status; // AUTHORIZED, CAPTURED, FAILED, REFUNDED, RELEASED
    private String failureReason;
    private long createdAtMs;
    private long updatedAtMs;

    public Payment(@NonNull String id, String reservationId, String sessionId, String consumerId,
                   String hostId, String idempotencyKey, String gateway, String gatewayReference,
                   double authorizedAmount, double capturedAmount, double refundedAmount,
                   double lateCancellationFee, String status, String failureReason,
                   long createdAtMs, long updatedAtMs) {
        this.id = id;
        this.reservationId = reservationId;
        this.sessionId = sessionId;
        this.consumerId = consumerId;
        this.hostId = hostId;
        this.idempotencyKey = idempotencyKey;
        this.gateway = gateway;
        this.gatewayReference = gatewayReference;
        this.authorizedAmount = authorizedAmount;
        this.capturedAmount = capturedAmount;
        this.refundedAmount = refundedAmount;
        this.lateCancellationFee = lateCancellationFee;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAtMs = createdAtMs;
        this.updatedAtMs = updatedAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getGatewayReference() { return gatewayReference; }
    public void setGatewayReference(String gatewayReference) { this.gatewayReference = gatewayReference; }

    public double getAuthorizedAmount() { return authorizedAmount; }
    public void setAuthorizedAmount(double authorizedAmount) { this.authorizedAmount = authorizedAmount; }

    public double getCapturedAmount() { return capturedAmount; }
    public void setCapturedAmount(double capturedAmount) { this.capturedAmount = capturedAmount; }

    public double getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(double refundedAmount) { this.refundedAmount = refundedAmount; }

    public double getLateCancellationFee() { return lateCancellationFee; }
    public void setLateCancellationFee(double lateCancellationFee) { this.lateCancellationFee = lateCancellationFee; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    public long getUpdatedAtMs() { return updatedAtMs; }
    public void setUpdatedAtMs(long updatedAtMs) { this.updatedAtMs = updatedAtMs; }

    public boolean isSettled() {
        return "CAPTURED".equals(status) || "REFUNDED".equals(status);
    }
}
