package com.amperly.app.engine.soc;

import com.amperly.app.core.common.GeoUtils;
import com.amperly.app.data.local.entity.BatteryUpdate;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Battery State of Charge planning and correction.
 *
 * The consumer always has authority over the battery value. The moment they enter the figure shown
 * on the dashboard, that value becomes authoritative for the rest of the trip: the old estimate is
 * preserved for transparency, chargers that are no longer safely reachable are removed, newly
 * reachable ones come back, and the remaining trip (not the original plan from the start point) is
 * recalculated. If nothing can be reached safely, Amperly says so and offers alternatives instead of
 * routing the driver past the estimated safe range.
 */
public class SoCEstimatorEngine {

    public static final int DEFAULT_RESERVE_PERCENT = 15;

    public static class SocEstimate {
        private final int soc;
        private final boolean fromProfile;
        private final String source;

        public SocEstimate(int soc, boolean fromProfile, String source) {
            this.soc = soc;
            this.fromProfile = fromProfile;
            this.source = source;
        }

        public int getSoc() { return soc; }
        public boolean isFromProfile() { return fromProfile; }
        public String getSource() { return source; }
    }

    public static class RemainingStop {
        private final ChargerStation station;
        private final double distanceFromCurrentKm;
        private final double detourKm;
        private final int detourMinutes;
        private final int arrivalSoc;
        private final int departureSoc;
        private final int chargingMinutes;
        private final double energyAddedKwh;
        private final boolean reachable;
        private final String note;

        public RemainingStop(ChargerStation station, double distanceFromCurrentKm, double detourKm,
                             int detourMinutes, int arrivalSoc, int departureSoc, int chargingMinutes,
                             double energyAddedKwh, boolean reachable, String note) {
            this.station = station;
            this.distanceFromCurrentKm = distanceFromCurrentKm;
            this.detourKm = detourKm;
            this.detourMinutes = detourMinutes;
            this.arrivalSoc = arrivalSoc;
            this.departureSoc = departureSoc;
            this.chargingMinutes = chargingMinutes;
            this.energyAddedKwh = energyAddedKwh;
            this.reachable = reachable;
            this.note = note;
        }

        public ChargerStation getStation() { return station; }
        public double getDistanceFromCurrentKm() { return distanceFromCurrentKm; }
        public double getDetourKm() { return detourKm; }
        public int getDetourMinutes() { return detourMinutes; }
        public int getArrivalSoc() { return arrivalSoc; }
        public int getDepartureSoc() { return departureSoc; }
        public int getChargingMinutes() { return chargingMinutes; }
        public double getEnergyAddedKwh() { return energyAddedKwh; }
        public boolean isReachable() { return reachable; }
        public String getNote() { return note; }
    }

    /** Result of recalculating the trip after a battery correction. */
    public static class RecalculationResult {
        private final int previousEstimatedSoc;
        private final int actualDashboardSoc;
        private final boolean wasOverrideApplied;
        private final double currentLat;
        private final double currentLng;
        private final double remainingDistanceKm;
        private final List<ChargerStation> safeReachableStations;
        private final List<ChargerStation> unreachableStations;
        private final List<ChargerStation> newlyReachableStations;
        private final ChargerStation recommendedNextCharger;
        private final RemainingStop recommendedStop;
        private final List<RemainingStop> remainingOptions;
        private final int estimatedRangeKm;
        private final int remainingDriveMinutes;
        private final int remainingChargingMinutes;
        private final int remainingJourneyMinutes;
        private final boolean noSafeCharger;
        private final String warningMessage;
        private final String explanation;
        private final String reservationAdvice;
        private final boolean needsWiderRadius;
        private final BatteryUpdate auditRecord;

        public RecalculationResult(int previousEstimatedSoc, int actualDashboardSoc,
                                   boolean wasOverrideApplied, double currentLat, double currentLng,
                                   double remainingDistanceKm,
                                   List<ChargerStation> safeReachableStations,
                                   List<ChargerStation> unreachableStations,
                                   List<ChargerStation> newlyReachableStations,
                                   ChargerStation recommendedNextCharger, RemainingStop recommendedStop,
                                   List<RemainingStop> remainingOptions, int estimatedRangeKm,
                                   int remainingDriveMinutes, int remainingChargingMinutes,
                                   int remainingJourneyMinutes, boolean noSafeCharger,
                                   String warningMessage, String explanation,
                                   String reservationAdvice, boolean needsWiderRadius,
                                   BatteryUpdate auditRecord) {
            this.previousEstimatedSoc = previousEstimatedSoc;
            this.actualDashboardSoc = actualDashboardSoc;
            this.wasOverrideApplied = wasOverrideApplied;
            this.currentLat = currentLat;
            this.currentLng = currentLng;
            this.remainingDistanceKm = remainingDistanceKm;
            this.safeReachableStations = safeReachableStations;
            this.unreachableStations = unreachableStations;
            this.newlyReachableStations = newlyReachableStations;
            this.recommendedNextCharger = recommendedNextCharger;
            this.recommendedStop = recommendedStop;
            this.remainingOptions = remainingOptions;
            this.estimatedRangeKm = estimatedRangeKm;
            this.remainingDriveMinutes = remainingDriveMinutes;
            this.remainingChargingMinutes = remainingChargingMinutes;
            this.remainingJourneyMinutes = remainingJourneyMinutes;
            this.noSafeCharger = noSafeCharger;
            this.warningMessage = warningMessage;
            this.explanation = explanation;
            this.reservationAdvice = reservationAdvice;
            this.needsWiderRadius = needsWiderRadius;
            this.auditRecord = auditRecord;
        }

        public int getPreviousEstimatedSoc() { return previousEstimatedSoc; }
        public int getActualDashboardSoc() { return actualDashboardSoc; }
        public boolean isWasOverrideApplied() { return wasOverrideApplied; }
        public double getCurrentLat() { return currentLat; }
        public double getCurrentLng() { return currentLng; }
        public double getRemainingDistanceKm() { return remainingDistanceKm; }
        public List<ChargerStation> getSafeReachableStations() { return safeReachableStations; }
        public List<ChargerStation> getUnreachableStations() { return unreachableStations; }
        public List<ChargerStation> getNewlyReachableStations() { return newlyReachableStations; }
        public ChargerStation getRecommendedNextCharger() { return recommendedNextCharger; }
        public RemainingStop getRecommendedStop() { return recommendedStop; }
        public List<RemainingStop> getRemainingOptions() { return remainingOptions; }
        public int getEstimatedRangeKm() { return estimatedRangeKm; }
        public int getRemainingDriveMinutes() { return remainingDriveMinutes; }
        public int getRemainingChargingMinutes() { return remainingChargingMinutes; }
        public int getRemainingJourneyMinutes() { return remainingJourneyMinutes; }
        public boolean isNoSafeCharger() { return noSafeCharger; }
        public String getWarningMessage() { return warningMessage; }
        public String getExplanation() { return explanation; }
        public String getReservationAdvice() { return reservationAdvice; }
        public boolean isNeedsWiderRadius() { return needsWiderRadius; }
        public BatteryUpdate getAuditRecord() { return auditRecord; }
    }

    private SoCEstimatorEngine() {
    }

    public static int estimateSocAfterDistance(int startSoc, double distanceKm, Vehicle vehicle) {
        if (vehicle == null) {
            return Math.max(0, startSoc - (int) Math.round(distanceKm / 5.0));
        }
        double drain = RoutePlannerEngineDrain(distanceKm, vehicle);
        return Math.max(0, startSoc - (int) Math.round(drain));
    }

    private static double RoutePlannerEngineDrain(double distanceKm, Vehicle vehicle) {
        double capacity = vehicle.effectiveCapacityKwh();
        double efficiency = vehicle.effectiveEfficiencyKmPerKwh();
        return ((distanceKm / efficiency) / capacity) * 100.0;
    }

    /**
     * Estimates a starting SoC when the consumer has not entered one. This is a planning estimate
     * built from the vehicle profile (capacity, health, efficiency) and must always be labelled as
     * an estimate in the UI.
     */
    public static SocEstimate estimateStartingSoc(Vehicle vehicle, double typicalDailyDistanceKm) {
        if (vehicle == null) {
            return new SocEstimate(60, true, "Amperly estimate: no vehicle profile, using a neutral 60% assumption.");
        }
        double efficiency = vehicle.effectiveEfficiencyKmPerKwh();
        double capacity = vehicle.effectiveCapacityKwh();
        double consumed = Math.max(0, typicalDailyDistanceKm) / Math.max(1.0, efficiency);
        int usedPercent = (int) Math.round((consumed / capacity) * 100.0);
        int soc = Math.max(20, Math.min(95, 95 - usedPercent));
        String source = "Amperly estimate from " + vehicle.getMake() + " " + vehicle.getModel()
                + " (" + String.format(Locale.US, "%.1f", capacity) + " kWh usable, "
                + String.format(Locale.US, "%.1f", efficiency) + " km/kWh) — not a reading from the car.";
        return new SocEstimate(soc, true, source);
    }

    /**
     * Applies the consumer's dashboard value as the authoritative SoC and recalculates only the
     * remaining trip from the current location.
     */
    public static RecalculationResult applyAuthoritativeSocOverride(
            String vehicleId, String tripPlanId, int currentEstimatedSoc, int actualDashboardSoc,
            double currentLat, double currentLng, double remainingDistanceKm,
            double destinationLat, double destinationLng, Vehicle vehicle,
            List<ChargerStation> candidateStations, List<ChargerStation> previouslyReachable) {
        return applyAuthoritativeSocOverride(vehicleId, tripPlanId, currentEstimatedSoc,
                actualDashboardSoc, currentLat, currentLng, remainingDistanceKm, destinationLat,
                destinationLng, vehicle, candidateStations, previouslyReachable, 0);
    }

    /**
     * @param reserveOverridePercent explicit arrival reserve for this recalculation (0 = use the
     *                               vehicle preference). Used by the "allow a slower charger"
     *                               alternative so the trade-off is explicit rather than silent.
     */
    public static RecalculationResult applyAuthoritativeSocOverride(
            String vehicleId, String tripPlanId, int currentEstimatedSoc, int actualDashboardSoc,
            double currentLat, double currentLng, double remainingDistanceKm,
            double destinationLat, double destinationLng, Vehicle vehicle,
            List<ChargerStation> candidateStations, List<ChargerStation> previouslyReachable,
            int reserveOverridePercent) {

        int reserve = reserveOverridePercent > 0 ? reserveOverridePercent
                : vehicle != null && vehicle.getMinArrivalSocPercent() > 0
                ? vehicle.getMinArrivalSocPercent() : DEFAULT_RESERVE_PERCENT;
        double capacity = vehicle != null ? vehicle.effectiveCapacityKwh() : 40.0;
        double efficiency = vehicle != null ? vehicle.effectiveEfficiencyKmPerKwh() : 6.5;

        List<ChargerStation> safe = new ArrayList<>();
        List<ChargerStation> unreachable = new ArrayList<>();
        List<ChargerStation> newlyReachable = new ArrayList<>();
        List<RemainingStop> options = new ArrayList<>();

        for (ChargerStation station : safeList(candidateStations)) {
            if (!station.isLiveOnMarketplace()) {
                continue;
            }
            if (vehicle != null && !RoutePlannerEngineBridge.isCompatible(station, vehicle)) {
                continue;
            }
            double distanceToStation = GeoUtils.distanceKm(currentLat, currentLng,
                    station.getLatitude(), station.getLongitude());
            double detourKm = GeoUtils.detourKm(distanceToStation);
            int detourMinutes = GeoUtils.detourMinutes(detourKm);
            int socUsed = (int) Math.round((distanceToStation / efficiency / capacity) * 100.0);
            int arrivalSoc = Math.max(0, actualDashboardSoc - socUsed);

            int socAfterDestination = (int) Math.round(actualDashboardSoc
                    - ((remainingDistanceKm / efficiency / capacity) * 100.0));

            double energyAdded = 0;
            int departureSoc = arrivalSoc;
            int chargingMinutes = 0;
            if (arrivalSoc >= reserve) {
                int target = Math.min(vehicle != null ? vehicle.getMaxChargeSocPercent() : 80,
                        Math.max(arrivalSoc + 10, arrivalSoc - socAfterDestination));
                target = Math.min(100, Math.max(target, arrivalSoc));
                energyAdded = Math.max(0, ((target - arrivalSoc) / 100.0) * capacity);
                double chargerPower = station.getMaxPowerKw() > 0 ? station.getMaxPowerKw() : 7.2;
                double vehiclePower = vehicle == null ? chargerPower
                        : (station.isDcCapable() ? Math.max(1.0, vehicle.getMaxDcKw())
                        : Math.max(1.0, vehicle.getMaxAcKw()));
                double effectivePower = Math.max(1.0, Math.min(chargerPower, vehiclePower) * 0.8);
                chargingMinutes = energyAdded <= 0.1 ? 0
                        : Math.max(10, (int) Math.ceil((energyAdded / effectivePower) * 60.0));
                departureSoc = Math.min(100, (int) Math.round(arrivalSoc + ((energyAdded / capacity) * 100.0)));
            }

            String note;
            if (arrivalSoc >= reserve) {
                safe.add(station);
                note = "Safely reachable: " + arrivalSoc + "% on arrival, "
                        + departureSoc + "% after charging.";
                if (previousListContains(previouslyReachable, station.getId())) {
                    // already known
                } else if (previouslyReachable != null && !previouslyReachable.isEmpty()) {
                    newlyReachable.add(station);
                }
            } else {
                unreachable.add(station);
                note = "Removed: only " + arrivalSoc + "% would remain on arrival, below the "
                        + reserve + "% reserve.";
            }

            RemainingStop stop = new RemainingStop(station, distanceToStation, detourKm, detourMinutes,
                    arrivalSoc, departureSoc, chargingMinutes, energyAdded, arrivalSoc >= reserve, note);
            if (arrivalSoc >= reserve) {
                options.add(stop);
            }
        }

        Collections.sort(options, Comparator.comparingDouble(RemainingStop::getDistanceFromCurrentKm));
        RemainingStop recommended = options.isEmpty() ? null : options.get(0);

        int estimatedRangeKm = (int) Math.floor(((actualDashboardSoc - reserve) / 100.0) * capacity * efficiency);
        estimatedRangeKm = Math.max(0, estimatedRangeKm);

        int remainingDriveMinutes = GeoUtils.drivingMinutes(remainingDistanceKm);
        int remainingChargingMinutes = recommended == null ? 0 : recommended.getChargingMinutes();
        int remainingJourneyMinutes = remainingDriveMinutes
                + (recommended == null ? 0 : recommended.getDetourMinutes()) + remainingChargingMinutes;

        boolean noSafeCharger = options.isEmpty();
        String warning = null;
        if (noSafeCharger) {
            warning = "No safe charger found within the current range ("
                    + actualDashboardSoc + "% battery, " + reserve + "% reserve, about "
                    + estimatedRangeKm + " km usable). Amperly has not routed you beyond the estimated safe range.";
        }

        StringBuilder explanation = new StringBuilder();
        explanation.append("Original estimate ").append(currentEstimatedSoc).append("% → your actual ")
                .append(actualDashboardSoc).append("% → remaining route recalculated from your current location.\n");
        explanation.append("Previously reachable: ").append(countOf(previouslyReachable)).append(" charger(s). ");
        explanation.append("Safely reachable now: ").append(safe.size()).append(". ");
        if (!unreachable.isEmpty()) {
            explanation.append("Removed: ").append(unreachable.size()).append(". ");
        }
        if (!newlyReachable.isEmpty()) {
            explanation.append("Newly reachable: ").append(newlyReachable.size()).append(". ");
        }
        if (recommended != null) {
            explanation.append("\nNearest safe compatible charger: ").append(recommended.getStation().getName())
                    .append(", ").append(String.format(Locale.US, "%.1f", recommended.getDistanceFromCurrentKm()))
                    .append(" km away, ").append(recommended.getDetourMinutes()).append("-minute detour.");
        }

        String reservationAdvice = noSafeCharger
                ? "Keep any existing reservation only if you can still reach it; otherwise cancel it free of charge before the 60-minute cut-off."
                : "Existing reservations are preserved. " + (recommended == null ? ""
                : "Consider keeping your booking if " + recommended.getStation().getName() + " is the same station, "
                + "or changing it to this closer option.");

        BatteryUpdate audit = new BatteryUpdate(UUID.randomUUID().toString(), vehicleId, tripPlanId,
                currentEstimatedSoc, actualDashboardSoc, currentLat, currentLng, System.currentTimeMillis());

        return new RecalculationResult(currentEstimatedSoc, actualDashboardSoc, true, currentLat,
                currentLng, remainingDistanceKm, safe, unreachable, newlyReachable,
                recommended == null ? null : recommended.getStation(), recommended, options,
                estimatedRangeKm, remainingDriveMinutes, remainingChargingMinutes,
                remainingJourneyMinutes, noSafeCharger, warning, explanation.toString(),
                reservationAdvice, noSafeCharger, audit);
    }

    /** Backup stations that are still safe after a correction, for the car display. */
    public static List<ChargerStation> safeAlternatives(List<ChargerStation> stations, int soc,
                                                        double currentLat, double currentLng,
                                                        Vehicle vehicle, int maxResults) {
        List<ChargerStation> result = new ArrayList<>();
        if (stations == null) {
            return result;
        }
        double capacity = vehicle != null ? vehicle.effectiveCapacityKwh() : 40.0;
        double efficiency = vehicle != null ? vehicle.effectiveEfficiencyKmPerKwh() : 6.5;
        int reserve = vehicle != null && vehicle.getMinArrivalSocPercent() > 0
                ? vehicle.getMinArrivalSocPercent() : DEFAULT_RESERVE_PERCENT;
        List<ChargerStation> ordered = new ArrayList<>(stations);
        Collections.sort(ordered, Comparator.comparingDouble(a -> GeoUtils.distanceKm(currentLat,
                currentLng, a.getLatitude(), a.getLongitude())));
        for (ChargerStation station : ordered) {
            double distance = GeoUtils.distanceKm(currentLat, currentLng, station.getLatitude(), station.getLongitude());
            int socUsed = (int) Math.round((distance / efficiency / capacity) * 100.0);
            if (soc - socUsed >= reserve) {
                result.add(station);
            }
            if (result.size() >= maxResults) {
                break;
            }
        }
        return result;
    }

    private static boolean previousListContains(List<ChargerStation> stations, String id) {
        if (stations == null) {
            return false;
        }
        for (ChargerStation station : stations) {
            if (station.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static int countOf(List<ChargerStation> stations) {
        return stations == null ? 0 : stations.size();
    }

    private static List<ChargerStation> safeList(List<ChargerStation> stations) {
        return stations == null ? Collections.emptyList() : stations;
    }

    /** Local bridge so this engine keeps no dependency on the planner's public API surface. */
    private static final class RoutePlannerEngineBridge {
        static boolean isCompatible(ChargerStation station, Vehicle vehicle) {
            return com.amperly.app.engine.routing.RoutePlannerEngine.isConnectorCompatible(station, vehicle);
        }
    }
}
