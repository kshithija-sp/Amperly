package com.amperly.app.core.nav;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amperly.app.core.routing.DirectionsRoute;
import com.amperly.app.core.routing.GoogleDirectionsClient;

import java.util.UUID;

/**
 * The production {@link NavigationProvider}: Google Maps Platform for both the road route and the
 * in-app navigation session.
 *
 * <pre>
 * GoogleNavigationProvider
 *     ├── route calculation      → Google Directions API (core/routing/GoogleDirectionsClient)
 *     ├── navigation session     → sessionId + the NavigationState the UI and Android Auto share
 *     ├── reroute from new SOC   → recalculate() after a battery override
 *     ├── charger waypoints      → the request carries waypoints for corridor stops
 *     └── Android Auto bridge     → the same NavigationState is projected in the car
 * </pre>
 *
 * <p>The route geometry is rendered by the Google Maps SDK panel, not by a home-grown tile
 * renderer, so the drawn line, the markers and the distance all come from Google.
 *
 * <p>Voice guidance flags mirror the Google Navigation SDK's audio configuration
 * ({@link #AUDIO_VOICE_ALERTS_AND_GUIDANCE} | {@link #AUDIO_BLUETOOTH}), so the intent is explicit
 * and can be handed straight to the Navigation SDK when that licensed dependency is enabled.
 */
public final class GoogleNavigationProvider implements NavigationProvider {

    /** Spoken turn-by-turn alerts and guidance. */
    public static final int AUDIO_VOICE_ALERTS_AND_GUIDANCE = 1;
    /** Route guidance through a connected Bluetooth / car speaker where available. */
    public static final int AUDIO_BLUETOOTH = 2;
    /** No spoken guidance. */
    public static final int AUDIO_SILENT = 0;

    public static final String PROVIDER_NAME = "Google Maps Platform (Directions + Maps SDK)";

    private final GoogleDirectionsClient.TravelMode defaultMode;
    private volatile NavigationState state = NavigationState.idle();

    public GoogleNavigationProvider() {
        this(GoogleDirectionsClient.TravelMode.DRIVING);
    }

    public GoogleNavigationProvider(@NonNull GoogleDirectionsClient.TravelMode defaultMode) {
        this.defaultMode = defaultMode;
    }

    @Override
    public void calculateRoute(@NonNull RouteRequest request, @NonNull RouteCallback callback) {
        calculate(request, callback);
    }

    @Override
    public void recalculate(@NonNull RouteRequest request, @NonNull RouteCallback callback) {
        calculate(request, callback);
    }

    private void calculate(@NonNull RouteRequest request, @NonNull RouteCallback callback) {
        GoogleDirectionsClient.TravelMode mode = request.getMode() == null
                ? defaultMode : request.getMode();
        GoogleDirectionsClient.Callback inner = new GoogleDirectionsClient.Callback() {
            @Override
            public void onRoute(@NonNull DirectionsRoute route) {
                callback.onRoute(new RouteResult(route, request, PROVIDER_NAME,
                        System.currentTimeMillis(), false));
            }

            @Override
            public void onError(@NonNull String message) {
                callback.onError(message);
            }
        };
        if (request.hasDestinationQuery()) {
            GoogleDirectionsClient.routeToAddress(null, mode, request.getOriginLat(),
                    request.getOriginLng(), request.getDestinationQuery(), inner);
            return;
        }
        GoogleDirectionsClient.route(null, mode, request.getOriginLat(), request.getOriginLng(),
                request.getDestinationLat(), request.getDestinationLng(), inner);
    }

    @Override
    public void startNavigation(@NonNull NavigationRequest request, @NonNull NavigationCallback callback) {
        RouteResult result = request.getRoute();
        DirectionsRoute route = result.getRoute();
        String sessionId = UUID.randomUUID().toString();
        RouteRequest origin = result.getRequest();
        state = new NavigationState(true, sessionId, route, origin.getOriginLat(),
                origin.getOriginLng(), route.getDistanceKm(), route.getEffectiveDurationMinutes(),
                0, false);
        callback.onStarted(state);
    }

    /** Moves the active session along the route. Called from location updates and after a reroute. */
    public void updateProgress(double lat, double lng, double remainingKm, int remainingMinutes,
                               int travelledPercent) {
        NavigationState current = state;
        if (!current.isActive()) {
            return;
        }
        state = current.movedTo(lat, lng, remainingKm, remainingMinutes, travelledPercent,
                remainingKm <= 0.05);
    }

    /** Swaps in a recalculated route without ending the session, then reports the reroute. */
    public void applyReroute(@NonNull DirectionsRoute newRoute, @Nullable NavigationCallback callback) {
        NavigationState current = state;
        if (!current.isActive()) {
            return;
        }
        state = current.withRoute(newRoute);
        if (callback != null) {
            callback.onRerouted(state);
        }
    }

    /** Marks the session as arrived; navigation stops but the state stays readable for the summary. */
    public void markArrived(@Nullable NavigationCallback callback) {
        NavigationState current = state;
        if (!current.isActive()) {
            return;
        }
        state = new NavigationState(false, current.getSessionId(), current.getRoute(),
                current.getCurrentLat(), current.getCurrentLng(), 0, 0, 100, true);
        if (callback != null) {
            callback.onArrived(state);
        }
    }

    @Override
    public void stopNavigation() {
        state = NavigationState.idle();
    }

    @NonNull
    @Override
    public NavigationState getCurrentNavigationState() {
        return state;
    }
}
