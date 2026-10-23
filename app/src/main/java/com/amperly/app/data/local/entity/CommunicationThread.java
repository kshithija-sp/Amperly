package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Reservation-scoped conversation between a consumer and a host. Only the minimum information
 * needed for the visit is exchanged, real phone numbers stay masked, and the thread expires
 * automatically after the reservation/dispute window.
 */
@Entity(tableName = "communication_threads")
public class CommunicationThread {
    @PrimaryKey
    @NonNull
    private String id;
    private String reservationId;
    private String consumerId;
    private String consumerName;
    private String hostId;
    private String hostName;
    private String maskedConsumerNumber;
    private String maskedHostNumber;
    private boolean isReported;
    private boolean isBlocked;
    private boolean isChatEnabled;      // free-text chat is phone-only, never on the car display
    private long createdAtMs;
    private long expiresAtMs;

    public CommunicationThread(@NonNull String id, String reservationId, String consumerId,
                               String consumerName, String hostId, String hostName,
                               String maskedConsumerNumber, String maskedHostNumber,
                               boolean isReported, boolean isBlocked, boolean isChatEnabled,
                               long createdAtMs, long expiresAtMs) {
        this.id = id;
        this.reservationId = reservationId;
        this.consumerId = consumerId;
        this.consumerName = consumerName;
        this.hostId = hostId;
        this.hostName = hostName;
        this.maskedConsumerNumber = maskedConsumerNumber;
        this.maskedHostNumber = maskedHostNumber;
        this.isReported = isReported;
        this.isBlocked = isBlocked;
        this.isChatEnabled = isChatEnabled;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getConsumerName() { return consumerName; }
    public void setConsumerName(String consumerName) { this.consumerName = consumerName; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getMaskedConsumerNumber() { return maskedConsumerNumber; }
    public void setMaskedConsumerNumber(String maskedConsumerNumber) { this.maskedConsumerNumber = maskedConsumerNumber; }

    public String getMaskedHostNumber() { return maskedHostNumber; }
    public void setMaskedHostNumber(String maskedHostNumber) { this.maskedHostNumber = maskedHostNumber; }

    public boolean isReported() { return isReported; }
    public void setReported(boolean reported) { isReported = reported; }

    public boolean isBlocked() { return isBlocked; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }

    public boolean isChatEnabled() { return isChatEnabled; }
    public void setChatEnabled(boolean chatEnabled) { isChatEnabled = chatEnabled; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    public long getExpiresAtMs() { return expiresAtMs; }
    public void setExpiresAtMs(long expiresAtMs) { this.expiresAtMs = expiresAtMs; }

    public boolean isExpired(long nowMs) {
        return expiresAtMs > 0 && nowMs > expiresAtMs;
    }
}
