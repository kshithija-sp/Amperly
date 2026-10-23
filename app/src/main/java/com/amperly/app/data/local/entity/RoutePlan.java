package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A saved trip plan. Every plan stores the assumptions it was built from so the consumer can see
 * (and correct) what Amperly was thinking. Plans are estimates and never drive final billing.
 */
@Entity(tableName = "route_plans")
public class RoutePlan {
    @PrimaryKey
    @NonNull
    private String id;
    private String consumerId;
    private String vehicleId;
    private String origin;
    private String destination;
    private double originLat;
    private double originLng;
    private double destinationLat;
    private double destinationLng;
    private double corridorKm;
    private String preference;          // FASTEST, CHEAPEST, RELIABLE, SHORTEST_DETOUR
    private double totalDistanceKm;
    private int driveMinutes;
    private int detourMinutes;
    private int chargingMinutes;
    private int totalJourneyMinutes;
    private int plannedStartSoc;
    private int arrivalSocTarget;
    private boolean isSocAuthoritative;  // true once the consumer entered the dashboard value
    private String assumptions;
    private String confidence;          // HIGH, MEDIUM, LOW
    private String status;              // DRAFT, ACTIVE, COMPLETED, CANCELLED
    /** Which provider calculated the route, e.g. "Google Maps Platform (Directions + Maps SDK)". */
    private String routeProvider;
    /** The Google Place ID of the destination when the consumer picked one, else null. */
    private String googlePlaceId;
    private long createdAtMs;
    private long updatedAtMs;

    public RoutePlan(@NonNull String id, String consumerId, String vehicleId, String origin,
                     String destination, double originLat, double originLng, double destinationLat,
                     double destinationLng, double corridorKm, String preference,
                     double totalDistanceKm, int driveMinutes, int detourMinutes, int chargingMinutes,
                     int totalJourneyMinutes, int plannedStartSoc, int arrivalSocTarget,
                     boolean isSocAuthoritative, String assumptions, String confidence, String status,
                     long createdAtMs, long updatedAtMs) {
        this.id = id;
        this.consumerId = consumerId;
        this.vehicleId = vehicleId;
        this.origin = origin;
        this.destination = destination;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destinationLat = destinationLat;
        this.destinationLng = destinationLng;
        this.corridorKm = corridorKm;
        this.preference = preference;
        this.totalDistanceKm = totalDistanceKm;
        this.driveMinutes = driveMinutes;
        this.detourMinutes = detourMinutes;
        this.chargingMinutes = chargingMinutes;
        this.totalJourneyMinutes = totalJourneyMinutes;
        this.plannedStartSoc = plannedStartSoc;
        this.arrivalSocTarget = arrivalSocTarget;
        this.isSocAuthoritative = isSocAuthoritative;
        this.assumptions = assumptions;
        this.confidence = confidence;
        this.status = status;
        this.createdAtMs = createdAtMs;
        this.updatedAtMs = updatedAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public double getOriginLat() { return originLat; }
    public void setOriginLat(double originLat) { this.originLat = originLat; }

    public double getOriginLng() { return originLng; }
    public void setOriginLng(double originLng) { this.originLng = originLng; }

    public double getDestinationLat() { return destinationLat; }
    public void setDestinationLat(double destinationLat) { this.destinationLat = destinationLat; }

    public double getDestinationLng() { return destinationLng; }
    public void setDestinationLng(double destinationLng) { this.destinationLng = destinationLng; }

    public double getCorridorKm() { return corridorKm; }
    public void setCorridorKm(double corridorKm) { this.corridorKm = corridorKm; }

    public String getPreference() { return preference; }
    public void setPreference(String preference) { this.preference = preference; }

    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }

    public int getDriveMinutes() { return driveMinutes; }
    public void setDriveMinutes(int driveMinutes) { this.driveMinutes = driveMinutes; }

    public int getDetourMinutes() { return detourMinutes; }
    public void setDetourMinutes(int detourMinutes) { this.detourMinutes = detourMinutes; }

    public int getChargingMinutes() { return chargingMinutes; }
    public void setChargingMinutes(int chargingMinutes) { this.chargingMinutes = chargingMinutes; }

    public int getTotalJourneyMinutes() { return totalJourneyMinutes; }
    public void setTotalJourneyMinutes(int totalJourneyMinutes) { this.totalJourneyMinutes = totalJourneyMinutes; }

    public int getPlannedStartSoc() { return plannedStartSoc; }
    public void setPlannedStartSoc(int plannedStartSoc) { this.plannedStartSoc = plannedStartSoc; }

    public int getArrivalSocTarget() { return arrivalSocTarget; }
    public void setArrivalSocTarget(int arrivalSocTarget) { this.arrivalSocTarget = arrivalSocTarget; }

    public boolean isSocAuthoritative() { return isSocAuthoritative; }
    public void setSocAuthoritative(boolean socAuthoritative) { isSocAuthoritative = socAuthoritative; }

    public String getAssumptions() { return assumptions; }
    public void setAssumptions(String assumptions) { this.assumptions = assumptions; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRouteProvider() { return routeProvider; }
    public void setRouteProvider(String routeProvider) { this.routeProvider = routeProvider; }

    public String getGooglePlaceId() { return googlePlaceId; }
    public void setGooglePlaceId(String googlePlaceId) { this.googlePlaceId = googlePlaceId; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    public long getUpdatedAtMs() { return updatedAtMs; }
    public void setUpdatedAtMs(long updatedAtMs) { this.updatedAtMs = updatedAtMs; }
}
