package com.amperly.app.core.nav;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amperly.app.core.routing.DirectionsRoute;
import com.amperly.app.core.routing.GoogleDirectionsClient;

import java.util.ArrayList;
import java.util.List;

/**
 * The single seam between Amperly's screens and whatever calculates roads and drives navigation.
 *
 * <p>Production is {@link GoogleNavigationProvider}. Keeping the interface small means the routing
 * engine, the route screen, the charger corridor search and the Android Auto bridge all depend on
 * an abstraction rather than a vendor SDK, and a JVM test can drive a fake provider without any
 * network or Google services.
 */
public interface NavigationProvider {

    /** What to calculate: an origin, a destination, optional charging waypoints and a mode. */
    final class RouteRequest {
        private final double originLat;
        private final double originLng;
        private final double destinationLat;
        private final double destinationLng;
        @Nullable private final String destinationQuery;
        private final List<double[]> waypoints;
        private final GoogleDirectionsClient.TravelMode mode;

        public RouteRequest(double originLat, double originLng, double destinationLat,
                            double destinationLng, @Nullable List<double[]> waypoints,
                            @NonNull GoogleDirectionsClient.TravelMode mode) {
            this(originLat, originLng, destinationLat, destinationLng, null, waypoints, mode);
        }

        private RouteRequest(double originLat, double originLng, double destinationLat,
                             double destinationLng, @Nullable String destinationQuery,
                             @Nullable List<double[]> waypoints,
                             @NonNull GoogleDirectionsClient.TravelMode mode) {
            this.originLat = originLat;
            this.originLng = originLng;
            this.destinationLat = destinationLat;
            this.destinationLng = destinationLng;
            this.destinationQuery = destinationQuery;
            this.waypoints = waypoints == null ? new ArrayList<>() : waypoints;
            this.mode = mode;
        }

        /**
         * A request whose destination is free text the driver typed. Google geocodes it, snaps it
         * to a road and returns the coordinates in the route geometry.
         */
        @NonNull
        public static RouteRequest toAddress(double originLat, double originLng,
                                             @NonNull String query,
                                             @NonNull GoogleDirectionsClient.TravelMode mode) {
            return new RouteRequest(originLat, originLng, 0, 0, query, null, mode);
        }

        public double getOriginLat() { return originLat; }
        public double getOriginLng() { return originLng; }
        public double getDestinationLat() { return destinationLat; }
        public double getDestinationLng() { return destinationLng; }

        /** The free-text destination, when the driver typed one instead of picking a place. */
        @Nullable public String getDestinationQuery() { return destinationQuery; }

        public boolean hasDestinationQuery() {
            return destinationQuery != null && !destinationQuery.trim().isEmpty();
        }

        @NonNull public List<double[]> getWaypoints() { return waypoints; }
        @NonNull public GoogleDirectionsClient.TravelMode getMode() { return mode; }
    }

    /** A calculated route plus the request and provider it came from. */
    final class RouteResult {
        private final DirectionsRoute route;
        private final RouteRequest request;
        private final String providerName;
        private final long calculatedAtMs;
        private final boolean stale;

        public RouteResult(DirectionsRoute route, RouteRequest request, String providerName,
                           long calculatedAtMs, boolean stale) {
            this.route = route;
            this.request = request;
            this.providerName = providerName;
            this.calculatedAtMs = calculatedAtMs;
            this.stale = stale;
        }

        @NonNull public DirectionsRoute getRoute() { return route; }
        @NonNull public RouteRequest getRequest() { return request; }
        @NonNull public String getProviderName() { return providerName; }
        public long getCalculatedAtMs() { return calculatedAtMs; }
        public boolean isStale() { return stale; }
    }

    interface RouteCallback {
        void onRoute(@NonNull RouteResult result);

        void onError(@NonNull String message);
    }

    /** What to drive: a route, optional waypoints and the audio guidance the driver wants. */
    final class NavigationRequest {
        private final RouteResult route;
        private final int audioGuidanceFlags;

        public NavigationRequest(@NonNull RouteResult route, int audioGuidanceFlags) {
            this.route = route;
            this.audioGuidanceFlags = audioGuidanceFlags;
        }

        @NonNull public RouteResult getRoute() { return route; }
        public int getAudioGuidanceFlags() { return audioGuidanceFlags; }
    }

    interface NavigationCallback {
        void onStarted(@NonNull NavigationState state);

        void onRerouted(@NonNull NavigationState state);

        void onArrived(@NonNull NavigationState state);

        void onStopped();

        void onError(@NonNull String message);
    }

    /** Calculates a route. {@code callback} fires on the main thread. */
    void calculateRoute(@NonNull RouteRequest request, @NonNull RouteCallback callback);

    /** Starts driving the calculated route. */
    void startNavigation(@NonNull NavigationRequest request, @NonNull NavigationCallback callback);

    /** Recalculates the active session from a new position (used after a battery override). */
    void recalculate(@NonNull RouteRequest request, @NonNull RouteCallback callback);

    /** Ends navigation and releases any session resources. */
    void stopNavigation();

    /** The current session state; never null. */
    @NonNull
    NavigationState getCurrentNavigationState();
}
