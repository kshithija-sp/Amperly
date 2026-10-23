package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "invoices")
public class Invoice {
    @PrimaryKey
    @NonNull
    private String id;
    private String sessionId;
    private String consumerId;
    private String hostId;
    private String stationName;
    private double verifiedKwh;
    private double energyCharge;
    private double serviceFee;
    private double gatewayFee;
    private double adjustment;
    private String adjustmentNote;
    private double finalTotal;
    private String paymentId;
    private String status; // ISSUED, PARTIALLY_REFUNDED, REFUNDED, PAID
    private long issuedAtMs;

    public Invoice(@NonNull String id, String sessionId, String consumerId, String hostId,
                   String stationName, double verifiedKwh, double energyCharge, double serviceFee,
                   double gatewayFee, double adjustment, String adjustmentNote, double finalTotal,
                   String paymentId, String status, long issuedAtMs) {
        this.id = id;
        this.sessionId = sessionId;
        this.consumerId = consumerId;
        this.hostId = hostId;
        this.stationName = stationName;
        this.verifiedKwh = verifiedKwh;
        this.energyCharge = energyCharge;
        this.serviceFee = serviceFee;
        this.gatewayFee = gatewayFee;
        this.adjustment = adjustment;
        this.adjustmentNote = adjustmentNote;
        this.finalTotal = finalTotal;
        this.paymentId = paymentId;
        this.status = status;
        this.issuedAtMs = issuedAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public double getVerifiedKwh() { return verifiedKwh; }
    public void setVerifiedKwh(double verifiedKwh) { this.verifiedKwh = verifiedKwh; }

    public double getEnergyCharge() { return energyCharge; }
    public void setEnergyCharge(double energyCharge) { this.energyCharge = energyCharge; }

    public double getServiceFee() { return serviceFee; }
    public void setServiceFee(double serviceFee) { this.serviceFee = serviceFee; }

    public double getGatewayFee() { return gatewayFee; }
    public void setGatewayFee(double gatewayFee) { this.gatewayFee = gatewayFee; }

    public double getAdjustment() { return adjustment; }
    public void setAdjustment(double adjustment) { this.adjustment = adjustment; }

    public String getAdjustmentNote() { return adjustmentNote; }
    public void setAdjustmentNote(String adjustmentNote) { this.adjustmentNote = adjustmentNote; }

    public double getFinalTotal() { return finalTotal; }
    public void setFinalTotal(double finalTotal) { this.finalTotal = finalTotal; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getIssuedAtMs() { return issuedAtMs; }
    public void setIssuedAtMs(long issuedAtMs) { this.issuedAtMs = issuedAtMs; }
}
