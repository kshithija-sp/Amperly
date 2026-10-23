package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "charging_sessions")
public class ChargingSession {
    @PrimaryKey
    @NonNull
    private String id;
    private String reservationId;
    private String consumerId;
    private String hostId;
    private String stationId;
    private String stationName;
    private String portId;
    private long startTimeMs;
    private long endTimeMs;
    private double startingMeterKwh;
    private double endingMeterKwh;
    private double verifiedKwhConsumed;
    private double energyCharge;
    private double serviceFee;
    private double gatewayFee;
    private double totalPaid;
    private String status; // CHECKED_IN, CHARGING, COMPLETED, DISPUTED, REFUNDED
    private String meterReadingId;
    private String invoiceId;
    private boolean isMeterSimulated;
    private String verificationLevel;

    public ChargingSession(@NonNull String id, String reservationId, String consumerId, String hostId,
                           String stationId, String stationName, String portId, long startTimeMs,
                           long endTimeMs, double startingMeterKwh, double endingMeterKwh,
                           double verifiedKwhConsumed, double energyCharge, double serviceFee,
                           double gatewayFee, double totalPaid, String status, String meterReadingId,
                           String invoiceId, boolean isMeterSimulated, String verificationLevel) {
        this.id = id;
        this.reservationId = reservationId;
        this.consumerId = consumerId;
        this.hostId = hostId;
        this.stationId = stationId;
        this.stationName = stationName;
        this.portId = portId;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.startingMeterKwh = startingMeterKwh;
        this.endingMeterKwh = endingMeterKwh;
        this.verifiedKwhConsumed = verifiedKwhConsumed;
        this.energyCharge = energyCharge;
        this.serviceFee = serviceFee;
        this.gatewayFee = gatewayFee;
        this.totalPaid = totalPaid;
        this.status = status;
        this.meterReadingId = meterReadingId;
        this.invoiceId = invoiceId;
        this.isMeterSimulated = isMeterSimulated;
        this.verificationLevel = verificationLevel;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getPortId() { return portId; }
    public void setPortId(String portId) { this.portId = portId; }

    public long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; }

    public long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(long endTimeMs) { this.endTimeMs = endTimeMs; }

    public double getStartingMeterKwh() { return startingMeterKwh; }
    public void setStartingMeterKwh(double startingMeterKwh) { this.startingMeterKwh = startingMeterKwh; }

    public double getEndingMeterKwh() { return endingMeterKwh; }
    public void setEndingMeterKwh(double endingMeterKwh) { this.endingMeterKwh = endingMeterKwh; }

    public double getVerifiedKwhConsumed() { return verifiedKwhConsumed; }
    public void setVerifiedKwhConsumed(double verifiedKwhConsumed) { this.verifiedKwhConsumed = verifiedKwhConsumed; }

    public double getEnergyCharge() { return energyCharge; }
    public void setEnergyCharge(double energyCharge) { this.energyCharge = energyCharge; }

    public double getServiceFee() { return serviceFee; }
    public void setServiceFee(double serviceFee) { this.serviceFee = serviceFee; }

    public double getGatewayFee() { return gatewayFee; }
    public void setGatewayFee(double gatewayFee) { this.gatewayFee = gatewayFee; }

    public double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(double totalPaid) { this.totalPaid = totalPaid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMeterReadingId() { return meterReadingId; }
    public void setMeterReadingId(String meterReadingId) { this.meterReadingId = meterReadingId; }

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

    public boolean isMeterSimulated() { return isMeterSimulated; }
    public void setMeterSimulated(boolean meterSimulated) { isMeterSimulated = meterSimulated; }

    public String getVerificationLevel() { return verificationLevel; }
    public void setVerificationLevel(String verificationLevel) { this.verificationLevel = verificationLevel; }
}
