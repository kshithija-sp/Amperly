package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reservations")
public class Reservation {
    @PrimaryKey
    @NonNull
    private String id;
    private String consumerId;
    private String stationId;
    private String stationName;
    private String portId;
    private String portLabel;
    private String bookingCode;
    private long startTimeMs;
    private long endTimeMs;
    private String status; // RESERVED, CHECKED_IN, CHARGING, COMPLETED, CANCELLED, EXPIRED, DISPUTED, REFUNDED
    private double pricePerKwh;
    private double serviceFee;
    private double gatewayFee;
    private double cancellationFee;
    private boolean isPaid;
    private String paymentId;
    private long checkedInAtMs;
    private long cancelledAtMs;
    private long createdAtMs;

    public Reservation(@NonNull String id, String consumerId, String stationId, String stationName,
                       String portId, String portLabel, String bookingCode, long startTimeMs,
                       long endTimeMs, String status, double pricePerKwh, double serviceFee,
                       double gatewayFee, double cancellationFee, boolean isPaid, String paymentId,
                       long checkedInAtMs, long cancelledAtMs, long createdAtMs) {
        this.id = id;
        this.consumerId = consumerId;
        this.stationId = stationId;
        this.stationName = stationName;
        this.portId = portId;
        this.portLabel = portLabel;
        this.bookingCode = bookingCode;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.status = status;
        this.pricePerKwh = pricePerKwh;
        this.serviceFee = serviceFee;
        this.gatewayFee = gatewayFee;
        this.cancellationFee = cancellationFee;
        this.isPaid = isPaid;
        this.paymentId = paymentId;
        this.checkedInAtMs = checkedInAtMs;
        this.cancelledAtMs = cancelledAtMs;
        this.createdAtMs = createdAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getPortId() { return portId; }
    public void setPortId(String portId) { this.portId = portId; }

    public String getPortLabel() { return portLabel; }
    public void setPortLabel(String portLabel) { this.portLabel = portLabel; }

    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }

    public long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; }

    public long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(long endTimeMs) { this.endTimeMs = endTimeMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getPricePerKwh() { return pricePerKwh; }
    public void setPricePerKwh(double pricePerKwh) { this.pricePerKwh = pricePerKwh; }

    public double getServiceFee() { return serviceFee; }
    public void setServiceFee(double serviceFee) { this.serviceFee = serviceFee; }

    public double getGatewayFee() { return gatewayFee; }
    public void setGatewayFee(double gatewayFee) { this.gatewayFee = gatewayFee; }

    public double getCancellationFee() { return cancellationFee; }
    public void setCancellationFee(double cancellationFee) { this.cancellationFee = cancellationFee; }

    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { isPaid = paid; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public long getCheckedInAtMs() { return checkedInAtMs; }
    public void setCheckedInAtMs(long checkedInAtMs) { this.checkedInAtMs = checkedInAtMs; }

    public long getCancelledAtMs() { return cancelledAtMs; }
    public void setCancelledAtMs(long cancelledAtMs) { this.cancelledAtMs = cancelledAtMs; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    /** Free cancellation until 60 minutes before the slot starts (published policy). */
    public boolean isInFreeCancellationWindow(long nowMs) {
        return nowMs <= (startTimeMs - 60L * 60L * 1000L);
    }

    public boolean isActive() {
        return "RESERVED".equals(status) || "CHECKED_IN".equals(status) || "CHARGING".equals(status);
    }
}
