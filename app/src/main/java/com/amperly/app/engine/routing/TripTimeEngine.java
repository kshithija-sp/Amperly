package com.amperly.app.engine.routing;

public class TripTimeEngine {

    public static class TripTimeBreakdown {
        private final int drivingMinutes;
        private final int detourMinutes;
        private final int chargingMinutes;
        private final int totalJourneyMinutes;

        public TripTimeBreakdown(int drivingMinutes, int detourMinutes, int chargingMinutes) {
            this.drivingMinutes = drivingMinutes;
            this.detourMinutes = detourMinutes;
            this.chargingMinutes = chargingMinutes;
            this.totalJourneyMinutes = drivingMinutes + detourMinutes + chargingMinutes;
        }

        public int getDrivingMinutes() { return drivingMinutes; }
        public int getDetourMinutes() { return detourMinutes; }
        public int getChargingMinutes() { return chargingMinutes; }
        public int getTotalJourneyMinutes() { return totalJourneyMinutes; }
    }

    public static TripTimeBreakdown calculateBreakdown(double distanceKm, int detourMinutes, int chargingMinutes) {
        int drivingMinutes = (int) Math.round((distanceKm / 60.0) * 60.0); // ~60 km/h average
        return new TripTimeBreakdown(drivingMinutes, detourMinutes, chargingMinutes);
    }
}
