package com.amperly.app.core.nav;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amperly.app.core.routing.DirectionsRoute;

/**
 * The live state of an in-app navigation session: where we are, what is left, and which route we are
 * driving. The phone screen and the Android Auto bridge read the same object, so the car and the
 * handset can never disagree about the trip.
 */
public final class NavigationState {

    /** An idle session: nothing is being driven. */
    @NonNull
    public static NavigationState idle() {
        return new NavigationState(false, null, null, null, null, 0, 0, 0, false);
    }

    private final boolean active;
    @Nullable private final String sessionId;
    @Nullable private final DirectionsRoute route;
    @Nullable private final Double currentLat;
    @Nullable private final Double currentLng;
    private final double remainingDistanceKm;
    private final int remainingMinutes;
    private final int travelledPercent;
    private final long updatedAtMs;
    private final boolean arrived;

    public NavigationState(boolean active, @Nullable String sessionId,
                           @Nullable DirectionsRoute route, @Nullable Double currentLat,
                           @Nullable Double currentLng, double remainingDistanceKm,
                           int remainingMinutes, int travelledPercent, boolean arrived) {
        this.active = active;
        this.sessionId = sessionId;
        this.route = route;
        this.currentLat = currentLat;
        this.currentLng = currentLng;
        this.remainingDistanceKm = remainingDistanceKm;
        this.remainingMinutes = remainingMinutes;
        this.travelledPercent = travelledPercent;
        this.arrived = arrived;
        this.updatedAtMs = System.currentTimeMillis();
    }

    public boolean isActive() { return active; }

    @Nullable public String getSessionId() { return sessionId; }

    @Nullable public DirectionsRoute getRoute() { return route; }

    @Nullable public Double getCurrentLat() { return currentLat; }

    @Nullable public Double getCurrentLng() { return currentLng; }

    public double getRemainingDistanceKm() { return remainingDistanceKm; }

    public int getRemainingMinutes() { return remainingMinutes; }

    /** How far through the route the driver is, 0-100. */
    public int getTravelledPercent() { return travelledPercent; }

    public long getUpdatedAtMs() { return updatedAtMs; }

    public boolean hasArrived() { return arrived; }

    /** A new state that keeps the session and route but moves the position and progress. */
    @NonNull
    public NavigationState movedTo(double lat, double lng, double remainingKm, int remainingMin,
                                   int travelled, boolean hasArrived) {
        return new NavigationState(true, sessionId, route, lat, lng, remainingKm, remainingMin,
                travelled, hasArrived);
    }

    /** A new state that keeps the session but swaps in a freshly recalculated route. */
    @NonNull
    public NavigationState withRoute(@NonNull DirectionsRoute newRoute) {
        return new NavigationState(true, sessionId, newRoute, currentLat, currentLng,
                newRoute.getDistanceKm(), newRoute.getEffectiveDurationMinutes(), 0, false);
    }
}
