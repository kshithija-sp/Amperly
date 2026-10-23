package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "safety_events")
public class SafetyEvent {
    @PrimaryKey
    @NonNull
    private String id;
    private String consumerId;
    private String stationId;
    private String emergencyType;
    private boolean isLiveLocationSharingActive;
    private String trustedContactPhone;
    private long expiresAtMs;
    private long createdAtMs;

    public SafetyEvent(@NonNull String id, String consumerId, String stationId,
                       String emergencyType, boolean isLiveLocationSharingActive,
                       String trustedContactPhone, long expiresAtMs, long createdAtMs) {
        this.id = id;
        this.consumerId = consumerId;
        this.stationId = stationId;
        this.emergencyType = emergencyType;
        this.isLiveLocationSharingActive = isLiveLocationSharingActive;
        this.trustedContactPhone = trustedContactPhone;
        this.expiresAtMs = expiresAtMs;
        this.createdAtMs = createdAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }

    public boolean isLiveLocationSharingActive() { return isLiveLocationSharingActive; }
    public void setLiveLocationSharingActive(boolean liveLocationSharingActive) { isLiveLocationSharingActive = liveLocationSharingActive; }

    public String getTrustedContactPhone() { return trustedContactPhone; }
    public void setTrustedContactPhone(String trustedContactPhone) { this.trustedContactPhone = trustedContactPhone; }

    public long getExpiresAtMs() { return expiresAtMs; }
    public void setExpiresAtMs(long expiresAtMs) { this.expiresAtMs = expiresAtMs; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
}
