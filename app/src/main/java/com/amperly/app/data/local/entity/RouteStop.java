package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "route_stops")
public class RouteStop {
    @PrimaryKey
    @NonNull
    private String id;
    private String planId;
    private int sequence;
    private String stationId;
    private String stationName;
    private boolean isBackup;
    private boolean isReachable;
    private double distanceAlongRouteKm;
    private double detourKm;
    private int detourMinutes;
    private int arrivalSoc;
    private int departureSoc;
    private double energyAddedKwh;
    private int chargingMinutes;
    private double estimatedCost;
    private String recommendationReason;
    private String reservationId;

    public RouteStop(@NonNull String id, String planId, int sequence, String stationId,
                     String stationName, boolean isBackup, boolean isReachable,
                     double distanceAlongRouteKm, double detourKm, int detourMinutes,
                     int arrivalSoc, int departureSoc, double energyAddedKwh, int chargingMinutes,
                     double estimatedCost, String recommendationReason, String reservationId) {
        this.id = id;
        this.planId = planId;
        this.sequence = sequence;
        this.stationId = stationId;
        this.stationName = stationName;
        this.isBackup = isBackup;
        this.isReachable = isReachable;
        this.distanceAlongRouteKm = distanceAlongRouteKm;
        this.detourKm = detourKm;
        this.detourMinutes = detourMinutes;
        this.arrivalSoc = arrivalSoc;
        this.departureSoc = departureSoc;
        this.energyAddedKwh = energyAddedKwh;
        this.chargingMinutes = chargingMinutes;
        this.estimatedCost = estimatedCost;
        this.recommendationReason = recommendationReason;
        this.reservationId = reservationId;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public boolean isBackup() { return isBackup; }
    public void setBackup(boolean backup) { isBackup = backup; }

    public boolean isReachable() { return isReachable; }
    public void setReachable(boolean reachable) { isReachable = reachable; }

    public double getDistanceAlongRouteKm() { return distanceAlongRouteKm; }
    public void setDistanceAlongRouteKm(double distanceAlongRouteKm) { this.distanceAlongRouteKm = distanceAlongRouteKm; }

    public double getDetourKm() { return detourKm; }
    public void setDetourKm(double detourKm) { this.detourKm = detourKm; }

    public int getDetourMinutes() { return detourMinutes; }
    public void setDetourMinutes(int detourMinutes) { this.detourMinutes = detourMinutes; }

    public int getArrivalSoc() { return arrivalSoc; }
    public void setArrivalSoc(int arrivalSoc) { this.arrivalSoc = arrivalSoc; }

    public int getDepartureSoc() { return departureSoc; }
    public void setDepartureSoc(int departureSoc) { this.departureSoc = departureSoc; }

    public double getEnergyAddedKwh() { return energyAddedKwh; }
    public void setEnergyAddedKwh(double energyAddedKwh) { this.energyAddedKwh = energyAddedKwh; }

    public int getChargingMinutes() { return chargingMinutes; }
    public void setChargingMinutes(int chargingMinutes) { this.chargingMinutes = chargingMinutes; }

    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }

    public String getRecommendationReason() { return recommendationReason; }
    public void setRecommendationReason(String recommendationReason) { this.recommendationReason = recommendationReason; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
}
