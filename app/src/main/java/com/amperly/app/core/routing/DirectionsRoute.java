package com.amperly.app.core.routing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A driving or walking route returned by the Google Directions API: the road geometry plus the
 * numbers and turn-by-turn steps the driver is shown.
 *
 * <p>{@link #isApproximate()} is only ever true for {@link #straightLineEstimate}, the clearly
 * labelled fallback used when Google cannot be reached. A real Directions response is never
 * approximate, so the UI never presents an estimate as a road route.
 */
public final class DirectionsRoute {

    /** One turn-by-turn instruction. */
    public static class Step {
        private final String instruction;
        private final String distanceText;
        private final String durationText;
        private final String maneuver;
        private final double startLat;
        private final double startLng;

        public Step(String instruction, String distanceText, String durationText, String maneuver,
                    double startLat, double startLng) {
            this.instruction = instruction;
            this.distanceText = distanceText;
            this.durationText = durationText;
            this.maneuver = maneuver;
            this.startLat = startLat;
            this.startLng = startLng;
        }

        /** Human-readable instruction, with Google's HTML markup stripped. */
        @NonNull
        public String getInstruction() { return instruction; }

        @NonNull
        public String getDistanceText() { return distanceText; }

        @NonNull
        public String getDurationText() { return durationText; }

        /** The maneuver type such as {@code turn-left} or {@code roundabout-right}, or empty. */
        @NonNull
        public String getManeuver() { return maneuver; }

        public double getStartLat() { return startLat; }

        public double getStartLng() { return startLng; }
    }

    private final List<double[]> points;
    private final double distanceKm;
    private final int durationMinutes;
    private final int durationInTrafficMinutes;
    private final String startAddress;
    private final String endAddress;
    private final List<Step> steps;
    private final boolean approximate;

    public DirectionsRoute(List<double[]> points, double distanceKm, int durationMinutes,
                           int durationInTrafficMinutes, String startAddress, String endAddress,
                           List<Step> steps, boolean approximate) {
        this.points = points == null ? new ArrayList<>() : points;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.durationInTrafficMinutes = durationInTrafficMinutes;
        this.startAddress = startAddress;
        this.endAddress = endAddress;
        this.steps = steps == null ? new ArrayList<>() : steps;
        this.approximate = approximate;
    }

    /** Road geometry as {@code {lat, lng}} pairs, in order. */
    @NonNull
    public List<double[]> getPoints() { return points; }

    /** The snapped road origin, or 0 when the geometry is empty. */
    public double getOriginLat() { return points.isEmpty() ? 0 : points.get(0)[0]; }

    public double getOriginLng() { return points.isEmpty() ? 0 : points.get(0)[1]; }

    /** The snapped road destination, or 0 when the geometry is empty. */
    public double getDestinationLat() {
        return points.isEmpty() ? 0 : points.get(points.size() - 1)[0];
    }

    public double getDestinationLng() {
        return points.isEmpty() ? 0 : points.get(points.size() - 1)[1];
    }

    public double getDistanceKm() { return distanceKm; }

    public int getDurationMinutes() { return durationMinutes; }

    /** Duration in traffic when Google returned one, otherwise the same as {@link #getDurationMinutes()}. */
    public int getDurationInTrafficMinutes() { return durationInTrafficMinutes; }

    @NonNull
    public String getStartAddress() { return startAddress == null ? "" : startAddress; }

    @NonNull
    public String getEndAddress() { return endAddress == null ? "" : endAddress; }

    @NonNull
    public List<Step> getSteps() { return steps; }

    /** True only for the straight-line fallback, never for a real Directions response. */
    public boolean isApproximate() { return approximate; }

    /** The one number the driver should plan with: traffic-aware when Google supplies it. */
    public int getEffectiveDurationMinutes() {
        return durationInTrafficMinutes > 0 ? durationInTrafficMinutes : durationMinutes;
    }

    /** A straight-line path between two points, used only when Google cannot be reached. */
    @NonNull
    public static DirectionsRoute straightLineEstimate(double fromLat, double fromLng, double toLat,
                                                       double toLng, double speedKmh) {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i <= 32; i++) {
            points.add(geodesicPoint(fromLat, fromLng, toLat, toLng, i / 32d));
        }
        double distanceKm = com.amperly.app.core.common.GeoUtils.distanceKm(fromLat, fromLng, toLat, toLng);
        int minutes = (int) Math.round(distanceKm / Math.max(1.0, speedKmh) * 60d);
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("Straight-line estimate — Google Directions could not be reached.",
                String.format(java.util.Locale.US, "%.1f km", distanceKm), minutes + " min", "",
                fromLat, fromLng));
        return new DirectionsRoute(points, distanceKm, minutes, minutes,
                "Estimated start", "Estimated destination", steps, true);
    }

    /** A great-circle point, so the fallback line still reads as a path rather than a jump. */
    private static double[] geodesicPoint(double lat1, double lon1, double lat2, double lon2,
                                          double fraction) {
        double phi1 = Math.toRadians(lat1);
        double lambda1 = Math.toRadians(lon1);
        double phi2 = Math.toRadians(lat2);
        double lambda2 = Math.toRadians(lon2);
        double d = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin((phi2 - phi1) / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin((lambda2 - lambda1) / 2), 2)));
        if (d == 0 || Double.isNaN(d)) {
            return new double[]{lat1, lon1};
        }
        double a = Math.sin((1 - fraction) * d) / Math.sin(d);
        double b = Math.sin(fraction * d) / Math.sin(d);
        double x = a * Math.cos(phi1) * Math.cos(lambda1) + b * Math.cos(phi2) * Math.cos(lambda2);
        double y = a * Math.cos(phi1) * Math.sin(lambda1) + b * Math.cos(phi2) * Math.sin(lambda2);
        double z = a * Math.sin(phi1) + b * Math.sin(phi2);
        return new double[]{
                Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y))),
                Math.toDegrees(Math.atan2(y, x))};
    }

    @Nullable
    public static String describeError(String raw) {
        return raw == null || raw.trim().isEmpty() ? null : raw.trim();
    }
}
