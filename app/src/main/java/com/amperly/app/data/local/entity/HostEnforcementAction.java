package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "host_enforcements")
public class HostEnforcementAction {
    @PrimaryKey
    @NonNull
    private String id;
    private String hostId;
    private String stationId;
    private String actionType; // WARNING, FINE, SUSPENSION, BAN
    private String reason;
    private double fineAmount;
    private long createdAtMs;

    public HostEnforcementAction(@NonNull String id, String hostId, String stationId,
                                 String actionType, String reason, double fineAmount, long createdAtMs) {
        this.id = id;
        this.hostId = hostId;
        this.stationId = stationId;
        this.actionType = actionType;
        this.reason = reason;
        this.fineAmount = fineAmount;
        this.createdAtMs = createdAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public double getFineAmount() { return fineAmount; }
    public void setFineAmount(double fineAmount) { this.fineAmount = fineAmount; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
}
