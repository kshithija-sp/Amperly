package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "battery_updates")
public class BatteryUpdate {
    @PrimaryKey
    @NonNull
    private String id;
    private String vehicleId;
    private String tripPlanId;
    private int estimatedSoc;
    private int actualDashboardSoc;
    private double latitude;
    private double longitude;
    private long timestampMs;

    public BatteryUpdate(@NonNull String id, String vehicleId, String tripPlanId,
                         int estimatedSoc, int actualDashboardSoc, double latitude,
                         double longitude, long timestampMs) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.tripPlanId = tripPlanId;
        this.estimatedSoc = estimatedSoc;
        this.actualDashboardSoc = actualDashboardSoc;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestampMs = timestampMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getTripPlanId() { return tripPlanId; }
    public void setTripPlanId(String tripPlanId) { this.tripPlanId = tripPlanId; }

    public int getEstimatedSoc() { return estimatedSoc; }
    public void setEstimatedSoc(int estimatedSoc) { this.estimatedSoc = estimatedSoc; }

    public int getActualDashboardSoc() { return actualDashboardSoc; }
    public void setActualDashboardSoc(int actualDashboardSoc) { this.actualDashboardSoc = actualDashboardSoc; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; }
}
