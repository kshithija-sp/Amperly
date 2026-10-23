package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Audit record for the projected Android Auto experience: which screen was shown, when the driver
 * last corrected the battery value from the car display, and when the session ended. Used to keep
 * the car UI glanceable and to prove that detailed forms were kept on the phone.
 */
@Entity(tableName = "android_auto_sessions")
public class AndroidAutoSession {
    @PrimaryKey
    @NonNull
    private String id;
    private String userId;
    private String vehicleId;
    private String planId;
    private String lastTemplateShown;
    private int lastSocUpdatePercent;
    private long connectedAtMs;
    private long lastSocUpdateAtMs;
    private long disconnectedAtMs;

    public AndroidAutoSession(@NonNull String id, String userId, String vehicleId, String planId,
                              String lastTemplateShown, int lastSocUpdatePercent,
                              long connectedAtMs, long lastSocUpdateAtMs, long disconnectedAtMs) {
        this.id = id;
        this.userId = userId;
        this.vehicleId = vehicleId;
        this.planId = planId;
        this.lastTemplateShown = lastTemplateShown;
        this.lastSocUpdatePercent = lastSocUpdatePercent;
        this.connectedAtMs = connectedAtMs;
        this.lastSocUpdateAtMs = lastSocUpdateAtMs;
        this.disconnectedAtMs = disconnectedAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getLastTemplateShown() { return lastTemplateShown; }
    public void setLastTemplateShown(String lastTemplateShown) { this.lastTemplateShown = lastTemplateShown; }

    public int getLastSocUpdatePercent() { return lastSocUpdatePercent; }
    public void setLastSocUpdatePercent(int lastSocUpdatePercent) { this.lastSocUpdatePercent = lastSocUpdatePercent; }

    public long getConnectedAtMs() { return connectedAtMs; }
    public void setConnectedAtMs(long connectedAtMs) { this.connectedAtMs = connectedAtMs; }

    public long getLastSocUpdateAtMs() { return lastSocUpdateAtMs; }
    public void setLastSocUpdateAtMs(long lastSocUpdateAtMs) { this.lastSocUpdateAtMs = lastSocUpdateAtMs; }

    public long getDisconnectedAtMs() { return disconnectedAtMs; }
    public void setDisconnectedAtMs(long disconnectedAtMs) { this.disconnectedAtMs = disconnectedAtMs; }
}
