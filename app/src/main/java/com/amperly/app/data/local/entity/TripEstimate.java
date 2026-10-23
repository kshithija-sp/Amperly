package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Estimate-versus-actual record for a segment of a trip. Kept so Amperly can show how far the plan
 * drifted and (with consent) improve future estimates for the same vehicle and route.
 */
@Entity(tableName = "trip_estimates")
public class TripEstimate {
    @PrimaryKey
    @NonNull
    private String id;
    private String planId;
    private String vehicleId;
    private double segmentDistanceKm;
    private double estimatedDrainKwh;
    private double actualDrainKwh;
    private int estimatedArrivalSoc;
    private int actualArrivalSoc;
    private String confidence;
    private String notes;
    private long createdAtMs;

    public TripEstimate(@NonNull String id, String planId, String vehicleId, double segmentDistanceKm,
                        double estimatedDrainKwh, double actualDrainKwh, int estimatedArrivalSoc,
                        int actualArrivalSoc, String confidence, String notes, long createdAtMs) {
        this.id = id;
        this.planId = planId;
        this.vehicleId = vehicleId;
        this.segmentDistanceKm = segmentDistanceKm;
        this.estimatedDrainKwh = estimatedDrainKwh;
        this.actualDrainKwh = actualDrainKwh;
        this.estimatedArrivalSoc = estimatedArrivalSoc;
        this.actualArrivalSoc = actualArrivalSoc;
        this.confidence = confidence;
        this.notes = notes;
        this.createdAtMs = createdAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public double getSegmentDistanceKm() { return segmentDistanceKm; }
    public void setSegmentDistanceKm(double segmentDistanceKm) { this.segmentDistanceKm = segmentDistanceKm; }

    public double getEstimatedDrainKwh() { return estimatedDrainKwh; }
    public void setEstimatedDrainKwh(double estimatedDrainKwh) { this.estimatedDrainKwh = estimatedDrainKwh; }

    public double getActualDrainKwh() { return actualDrainKwh; }
    public void setActualDrainKwh(double actualDrainKwh) { this.actualDrainKwh = actualDrainKwh; }

    public int getEstimatedArrivalSoc() { return estimatedArrivalSoc; }
    public void setEstimatedArrivalSoc(int estimatedArrivalSoc) { this.estimatedArrivalSoc = estimatedArrivalSoc; }

    public int getActualArrivalSoc() { return actualArrivalSoc; }
    public void setActualArrivalSoc(int actualArrivalSoc) { this.actualArrivalSoc = actualArrivalSoc; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
}
