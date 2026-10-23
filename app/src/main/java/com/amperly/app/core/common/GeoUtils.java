package com.amperly.app.core.common;

/**
 * Small geodesy toolkit. Everything is a straight-line estimate on a sphere: good enough to decide
 * whether a charger sits inside a route corridor and how long a detour costs, without pretending to
 * be turn-by-turn routing.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double AVERAGE_SPEED_KMH = 45.0; // used only for detour time estimates

    private GeoUtils() {
    }

    /** Great-circle distance in kilometres. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Shortest distance from a point to the straight line between a route's two ends. */
    public static double distanceToRouteKm(double lat, double lng,
                                           double startLat, double startLng,
                                           double endLat, double endLng) {
        double[] projected = projectFraction(lat, lng, startLat, startLng, endLat, endLng);
        double frac = projected[0];
        double projLat = startLat + (endLat - startLat) * frac;
        double projLng = startLng + (endLng - startLng) * frac;
        return distanceKm(lat, lng, projLat, projLng);
    }

    /**
     * Where a point falls along the route as a 0..1 fraction of the straight line. Used to order
     * candidate stops from origin to destination.
     */
    public static double fractionAlongRoute(double lat, double lng,
                                            double startLat, double startLng,
                                            double endLat, double endLng) {
        return projectFraction(lat, lng, startLat, startLng, endLat, endLng)[0];
    }

    private static double[] projectFraction(double lat, double lng,
                                           double startLat, double startLng,
                                           double endLat, double endLng) {
        double refLat = Math.toRadians((startLat + endLat) / 2.0);
        double scaleX = Math.cos(refLat);
        double ax = startLng * scaleX, ay = startLat;
        double bx = endLng * scaleX, by = endLat;
        double px = lng * scaleX, py = lat;

        double dx = bx - ax, dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq <= 0.0000001) {
            return new double[]{0.0, distanceKm(lat, lng, startLat, startLng)};
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));

        double projLat = startLat + (endLat - startLat) * t;
        double projLng = startLng + (endLng - startLng) * t;
        return new double[]{t, distanceKm(lat, lng, projLat, projLng)};
    }

    /**
     * Detour cost of leaving the route to reach a charger and coming back. The estimate doubles the
     * corridor distance (out and back) and adds a small fixed approach allowance.
     */
    public static double detourKm(double distanceFromRouteKm) {
        return (distanceFromRouteKm * 2.0) + 0.4;
    }

    public static int detourMinutes(double detourKm) {
        return (int) Math.round((detourKm / AVERAGE_SPEED_KMH) * 60.0);
    }

    public static int drivingMinutes(double distanceKm) {
        return (int) Math.round((distanceKm / AVERAGE_SPEED_KMH) * 60.0);
    }

    /** Initial bearing in degrees, used only for a human-readable "heading" hint. */
    public static double bearingDegrees(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLng);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360.0) % 360.0;
    }

    public static String compassLabel(double bearingDegrees) {
        String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        int index = (int) Math.round(bearingDegrees / 45.0) % 8;
        return points[index];
    }
}
