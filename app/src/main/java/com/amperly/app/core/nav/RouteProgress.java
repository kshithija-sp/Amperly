package com.amperly.app.core.nav;

import androidx.annotation.NonNull;

import com.amperly.app.core.routing.DirectionsRoute;

import java.util.List;

/**
 * Progress maths for in-app navigation, kept free of Android so it can be unit tested.
 *
 * <p>Given the route geometry and the driver's real position, this works out how far along the
 * route they are, how much is left, and which Google turn-by-turn step comes next. A wrong answer
 * here would announce the wrong turn or overstate the remaining range, so the maths is isolated
 * and tested rather than buried in the screen.
 */
public final class RouteProgress {

    private RouteProgress() {
    }

    /**
     * The fraction of the route nearest to a position, 0 at the origin and 1 at the destination.
     * The nearest-vertex measure is deliberate: it never runs ahead of the driver.
     */
    public static double fractionAlong(@NonNull List<double[]> points, double lat, double lng) {
        if (points.size() < 2) {
            return 0;
        }
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            double dLat = points.get(i)[0] - lat;
            double dLng = points.get(i)[1] - lng;
            double d = dLat * dLat + dLng * dLng;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best / (double) (points.size() - 1);
    }

    /** The distance still to drive, from the fraction of the route already covered. */
    public static double remainingKm(@NonNull DirectionsRoute route, double fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        return route.getDistanceKm() * (1 - clamped);
    }

    /** The time still to drive, from the fraction of the route already covered. */
    public static int remainingMinutes(@NonNull DirectionsRoute route, double fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        return (int) Math.round(route.getEffectiveDurationMinutes() * (1 - clamped));
    }

    /**
     * The first turn-by-turn step the driver has not yet passed, or null when the route has no
     * steps.
     */
    public static DirectionsRoute.Step nextStep(@NonNull DirectionsRoute route, double fraction) {
        List<DirectionsRoute.Step> steps = route.getSteps();
        if (steps.isEmpty()) {
            return null;
        }
        for (DirectionsRoute.Step step : steps) {
            double stepFraction = fractionAlong(route.getPoints(), step.getStartLat(),
                    step.getStartLng());
            if (stepFraction >= fraction - 0.02) {
                return step;
            }
        }
        return steps.get(steps.size() - 1);
    }
}
