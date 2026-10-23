package com.amperly.app;

import com.amperly.app.core.nav.RouteProgress;
import com.amperly.app.core.routing.DirectionsRoute;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Progress maths for in-app navigation. A wrong answer here announces the wrong turn or overstates
 * the remaining range, so it is tested on its own.
 */
public class RouteProgressTest {

    private DirectionsRoute route() {
        List<double[]> points = new ArrayList<>(Arrays.asList(
                new double[]{0.0, 0.0},
                new double[]{1.0, 1.0},
                new double[]{2.0, 2.0},
                new double[]{3.0, 3.0},
                new double[]{4.0, 4.0}));
        List<DirectionsRoute.Step> steps = new ArrayList<>();
        steps.add(new DirectionsRoute.Step("Start", "1 km", "10 min", "", 0.0, 0.0));
        steps.add(new DirectionsRoute.Step("Turn left", "1 km", "10 min", "turn-left", 2.0, 2.0));
        steps.add(new DirectionsRoute.Step("Arrive", "1 km", "10 min", "", 4.0, 4.0));
        return new DirectionsRoute(points, 100.0, 60, 0, "A", "B", steps, false);
    }

    @Test
    public void theOriginIsTheStartOfTheRoute() {
        assertEquals(0.0, RouteProgress.fractionAlong(route().getPoints(), 0.0, 0.0), 1e-9);
    }

    @Test
    public void theDestinationIsTheEndOfTheRoute() {
        assertEquals(1.0, RouteProgress.fractionAlong(route().getPoints(), 4.0, 4.0), 1e-9);
    }

    @Test
    public void theNearestVertexDecidesTheFraction() {
        assertEquals(0.5, RouteProgress.fractionAlong(route().getPoints(), 2.0, 2.0), 1e-9);
    }

    @Test
    public void remainingDistanceAndTimeShrinkWithProgress() {
        DirectionsRoute route = route();
        assertEquals(100.0, RouteProgress.remainingKm(route, 0.0), 1e-6);
        assertEquals(50.0, RouteProgress.remainingKm(route, 0.5), 1e-6);
        assertEquals(0.0, RouteProgress.remainingKm(route, 1.0), 1e-6);
        assertEquals(30, RouteProgress.remainingMinutes(route, 0.5));
    }

    @Test
    public void theNextStepIsTheFirstOneNotYetPassed() {
        DirectionsRoute route = route();
        assertEquals("Turn left", RouteProgress.nextStep(route, 0.25).getInstruction());
        assertEquals("Arrive", RouteProgress.nextStep(route, 0.75).getInstruction());
    }

    @Test
    public void aRouteWithNoStepsHasNoNextManeuver() {
        DirectionsRoute bare = new DirectionsRoute(
                new ArrayList<>(Arrays.asList(new double[]{0, 0}, new double[]{1, 1})),
                1.0, 1, 0, "A", "B", new ArrayList<>(), false);
        assertNull(RouteProgress.nextStep(bare, 0.0));
        assertNotNull(bare.getPoints());
    }
}
