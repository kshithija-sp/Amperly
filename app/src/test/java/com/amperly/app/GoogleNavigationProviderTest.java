package com.amperly.app;

import com.amperly.app.core.nav.GoogleNavigationProvider;
import com.amperly.app.core.nav.NavigationProvider;
import com.amperly.app.core.nav.NavigationState;
import com.amperly.app.core.routing.DirectionsRoute;
import com.amperly.app.core.routing.GoogleDirectionsClient;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The navigation session lifecycle, driven without any network or Google services.
 *
 * <p>The provider is handed an already-calculated route, so starting, moving, rerouting, arriving
 * and stopping are all exercised against the real {@link GoogleNavigationProvider} — a fake would
 * not prove the production provider behaves.
 */
public class GoogleNavigationProviderTest {

    private DirectionsRoute route() {
        List<double[]> points = new ArrayList<>(Arrays.asList(
                new double[]{12.30, 76.60},
                new double[]{12.35, 76.65},
                new double[]{12.40, 76.70}));
        List<DirectionsRoute.Step> steps = new ArrayList<>();
        steps.add(new DirectionsRoute.Step("Head north", "0.1 km", "1 min", "", 12.30, 76.60));
        steps.add(new DirectionsRoute.Step("Turn right", "0.2 km", "1 min", "turn-right", 12.35, 76.65));
        return new DirectionsRoute(points, 60.0, 50, 65, "Start address", "End address", steps, false);
    }

    private NavigationProvider.RouteResult result() {
        NavigationProvider.RouteRequest request = new NavigationProvider.RouteRequest(12.30, 76.60,
                12.40, 76.70, null, GoogleDirectionsClient.TravelMode.DRIVING);
        return new NavigationProvider.RouteResult(route(), request,
                GoogleNavigationProvider.PROVIDER_NAME, System.currentTimeMillis(), false);
    }

    @Test
    public void aFreshProviderIsIdle() {
        GoogleNavigationProvider provider = new GoogleNavigationProvider();
        assertFalse(provider.getCurrentNavigationState().isActive());
        assertNull(provider.getCurrentNavigationState().getSessionId());
    }

    @Test
    public void startingNavigationOpensASessionWithTheRouteAndTrafficTime() {
        GoogleNavigationProvider provider = new GoogleNavigationProvider();
        final NavigationState[] started = new NavigationState[1];
        provider.startNavigation(new NavigationProvider.NavigationRequest(result(),
                        GoogleNavigationProvider.AUDIO_VOICE_ALERTS_AND_GUIDANCE),
                new NoopCallback() {
                    @Override
                    public void onStarted(NavigationState state) {
                        started[0] = state;
                    }
                });
        assertNotNull(started[0]);
        assertTrue(started[0].isActive());
        assertNotNull(started[0].getSessionId());
        assertEquals(60.0, started[0].getRemainingDistanceKm(), 1e-6);
        assertEquals("the traffic-aware time is used", 65, started[0].getRemainingMinutes());
        assertEquals(12.30, started[0].getCurrentLat(), 1e-6);
    }

    @Test
    public void progressMovesTheSessionAlongTheRoute() {
        GoogleNavigationProvider provider = new GoogleNavigationProvider();
        provider.startNavigation(new NavigationProvider.NavigationRequest(result(),
                GoogleNavigationProvider.AUDIO_SILENT), new NoopCallback());
        provider.updateProgress(12.35, 76.65, 30.0, 33, 50);
        NavigationState state = provider.getCurrentNavigationState();
        assertTrue(state.isActive());
        assertEquals(30.0, state.getRemainingDistanceKm(), 1e-6);
        assertEquals(50, state.getTravelledPercent());
        assertFalse(state.hasArrived());
    }

    @Test
    public void reroutingSwapsTheRouteWithoutEndingTheSession() {
        GoogleNavigationProvider provider = new GoogleNavigationProvider();
        provider.startNavigation(new NavigationProvider.NavigationRequest(result(),
                GoogleNavigationProvider.AUDIO_SILENT), new NoopCallback());
        DirectionsRoute shorter = new DirectionsRoute(
                new ArrayList<>(Arrays.asList(new double[]{12.32, 76.62}, new double[]{12.40, 76.70})),
                20.0, 25, 0, "A", "B", new ArrayList<>(), false);

        final boolean[] rerouted = {false};
        provider.applyReroute(shorter, new NoopCallback() {
            @Override
            public void onRerouted(NavigationState state) {
                rerouted[0] = true;
            }
        });
        assertTrue(rerouted[0]);
        NavigationState state = provider.getCurrentNavigationState();
        assertTrue(state.isActive());
        assertEquals(20.0, state.getRemainingDistanceKm(), 1e-6);
    }

    @Test
    public void arrivingEndsNavigationButKeepsTheSummaryReadable() {
        GoogleNavigationProvider provider = new GoogleNavigationProvider();
        provider.startNavigation(new NavigationProvider.NavigationRequest(result(),
                GoogleNavigationProvider.AUDIO_SILENT), new NoopCallback());
        provider.markArrived(new NoopCallback());
        NavigationState state = provider.getCurrentNavigationState();
        assertFalse("arrived means the session stops driving", state.isActive());
        assertTrue(state.hasArrived());
        assertEquals(0.0, state.getRemainingDistanceKm(), 1e-6);
    }

    @Test
    public void stoppingClearsTheSessionSoNoStaleInstructionRemains() {
        GoogleNavigationProvider provider = new GoogleNavigationProvider();
        provider.startNavigation(new NavigationProvider.NavigationRequest(result(),
                GoogleNavigationProvider.AUDIO_SILENT), new NoopCallback());
        provider.stopNavigation();
        NavigationState state = provider.getCurrentNavigationState();
        assertFalse(state.isActive());
        assertNull(state.getRoute());
        assertNull(state.getSessionId());
    }

    private static class NoopCallback implements NavigationProvider.NavigationCallback {
        @Override
        public void onStarted(NavigationState state) {
        }

        @Override
        public void onRerouted(NavigationState state) {
        }

        @Override
        public void onArrived(NavigationState state) {
        }

        @Override
        public void onStopped() {
        }

        @Override
        public void onError(String message) {
        }
    }
}
