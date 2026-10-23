package com.amperly.app.engine.routing;

import com.amperly.app.core.common.GeoUtils;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Plans a journey around compatible chargers that already exist.
 *
 * The engine evaluates the whole journey, not just the nearest charger to the start: it filters
 * stations to a configurable corridor around the route, prices the detour, keeps a minimum arrival
 * reserve, records the assumptions it used and always explains the trade-off behind a recommendation.
 * Everything here is a planning estimate and never feeds final billing.
 */
public class RoutePlannerEngine {

    public static final String PREF_FASTEST = "FASTEST";
    public static final String PREF_CHEAPEST = "CHEAPEST";
    public static final String PREF_RELIABLE = "RELIABLE";
    public static final String PREF_SHORTEST_DETOUR = "SHORTEST_DETOUR";

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";

    private static final double AVERAGE_SPEED_KMH = 45.0;
    /** Charging never holds peak power the whole session; this is the planning derate. */
    private static final double CHARGE_TAPER_FACTOR = 0.8;
    private static final int MAX_STOPS = 5;

    /** A charging stop the planner recommends or keeps as a safety net. */
    public static class PlannedStop {
        private final String id = UUID.randomUUID().toString();
        private final ChargerStation station;
        private final int sequence;
        private final boolean backup;
        private final boolean reachable;
        private final double distanceAlongRouteKm;
        private final double detourKm;
        private final int detourMinutes;
        private final int arrivalSoc;
        private final int departureSoc;
        private final double energyAddedKwh;
        private final int chargingMinutes;
        private final double estimatedCost;
        private final String recommendationReason;

        public PlannedStop(ChargerStation station, int sequence, boolean backup, boolean reachable,
                           double distanceAlongRouteKm, double detourKm, int detourMinutes,
                           int arrivalSoc, int departureSoc, double energyAddedKwh,
                           int chargingMinutes, double estimatedCost, String recommendationReason) {
            this.station = station;
            this.sequence = sequence;
            this.backup = backup;
            this.reachable = reachable;
            this.distanceAlongRouteKm = distanceAlongRouteKm;
            this.detourKm = detourKm;
            this.detourMinutes = detourMinutes;
            this.arrivalSoc = arrivalSoc;
            this.departureSoc = departureSoc;
            this.energyAddedKwh = energyAddedKwh;
            this.chargingMinutes = chargingMinutes;
            this.estimatedCost = estimatedCost;
            this.recommendationReason = recommendationReason;
        }

        public String getId() { return id; }
        public ChargerStation getStation() { return station; }
        public int getSequence() { return sequence; }
        public boolean isBackup() { return backup; }
        public boolean isReachable() { return reachable; }
        public double getDistanceAlongRouteKm() { return distanceAlongRouteKm; }
        public double getDetourKm() { return detourKm; }
        public int getDetourMinutes() { return detourMinutes; }
        public int getArrivalSoc() { return arrivalSoc; }
        public int getDepartureSoc() { return departureSoc; }
        public double getEnergyAddedKwh() { return energyAddedKwh; }
        public int getChargingMinutes() { return chargingMinutes; }
        public double getEstimatedCost() { return estimatedCost; }
        public String getRecommendationReason() { return recommendationReason; }
    }

    /** Full evaluation of one candidate station for this journey. */
    public static class CandidateEvaluation {
        private final ChargerStation station;
        private final double distanceFromRouteKm;
        private final double distanceAlongRouteKm;
        private final double detourKm;
        private final int detourMinutes;
        private final int arrivalSoc;
        private final int departureSoc;
        private final int arrivalAtDestinationSoc;
        private final double energyAddedKwh;
        private final int chargingMinutes;
        private final double estimatedCost;
        private final boolean reachable;
        private final boolean paidBillingAvailable;
        private final double score;
        private final String reason;

        public CandidateEvaluation(ChargerStation station, double distanceFromRouteKm,
                                   double distanceAlongRouteKm, double detourKm, int detourMinutes,
                                   int arrivalSoc, int departureSoc, int arrivalAtDestinationSoc,
                                   double energyAddedKwh, int chargingMinutes, double estimatedCost,
                                   boolean reachable, boolean paidBillingAvailable, double score,
                                   String reason) {
            this.station = station;
            this.distanceFromRouteKm = distanceFromRouteKm;
            this.distanceAlongRouteKm = distanceAlongRouteKm;
            this.detourKm = detourKm;
            this.detourMinutes = detourMinutes;
            this.arrivalSoc = arrivalSoc;
            this.departureSoc = departureSoc;
            this.arrivalAtDestinationSoc = arrivalAtDestinationSoc;
            this.energyAddedKwh = energyAddedKwh;
            this.chargingMinutes = chargingMinutes;
            this.estimatedCost = estimatedCost;
            this.reachable = reachable;
            this.paidBillingAvailable = paidBillingAvailable;
            this.score = score;
            this.reason = reason;
        }

        public ChargerStation getStation() { return station; }
        public double getDistanceFromRouteKm() { return distanceFromRouteKm; }
        public double getDistanceAlongRouteKm() { return distanceAlongRouteKm; }
        public double getDetourKm() { return detourKm; }
        public int getDetourMinutes() { return detourMinutes; }
        public int getArrivalSoc() { return arrivalSoc; }
        public int getDepartureSoc() { return departureSoc; }
        public int getArrivalAtDestinationSoc() { return arrivalAtDestinationSoc; }
        public double getEnergyAddedKwh() { return energyAddedKwh; }
        public int getChargingMinutes() { return chargingMinutes; }
        public double getEstimatedCost() { return estimatedCost; }
        public boolean isReachable() { return reachable; }
        public boolean isPaidBillingAvailable() { return paidBillingAvailable; }
        public double getScore() { return score; }
        public String getReason() { return reason; }
    }

    /** A station that could not be considered, with an honest reason. */
    public static class ExcludedStation {
        private final ChargerStation station;
        private final String reason;

        public ExcludedStation(ChargerStation station, String reason) {
            this.station = station;
            this.reason = reason;
        }

        public ChargerStation getStation() { return station; }
        public String getReason() { return reason; }
    }

    public static class RoutePlanResult {
        private final String origin;
        private final String destination;
        private final double originLat;
        private final double originLng;
        private final double destinationLat;
        private final double destinationLng;
        private final double totalDistanceKm;
        private final double corridorKm;
        private final int driveMinutes;
        private final int detourMinutes;
        private final int chargingMinutes;
        private final int totalJourneyMinutes;
        private final int startSoc;
        private final int arrivalTargetSoc;
        private final String preference;
        private final CandidateEvaluation recommendedStop;
        private final List<CandidateEvaluation> candidates;
        private final List<PlannedStop> plannedStops;
        private final List<PlannedStop> backups;
        private final List<ExcludedStation> excluded;
        private final List<String> assumptions;
        private final String confidence;
        private final String noStopWarning;

        public RoutePlanResult(String origin, String destination, double originLat, double originLng,
                               double destinationLat, double destinationLng, double totalDistanceKm,
                               double corridorKm, int driveMinutes, int detourMinutes,
                               int chargingMinutes, int totalJourneyMinutes, int startSoc,
                               int arrivalTargetSoc, String preference,
                               CandidateEvaluation recommendedStop, List<CandidateEvaluation> candidates,
                               List<PlannedStop> plannedStops, List<PlannedStop> backups,
                               List<ExcludedStation> excluded, List<String> assumptions,
                               String confidence, String noStopWarning) {
            this.origin = origin;
            this.destination = destination;
            this.originLat = originLat;
            this.originLng = originLng;
            this.destinationLat = destinationLat;
            this.destinationLng = destinationLng;
            this.totalDistanceKm = totalDistanceKm;
            this.corridorKm = corridorKm;
            this.driveMinutes = driveMinutes;
            this.detourMinutes = detourMinutes;
            this.chargingMinutes = chargingMinutes;
            this.totalJourneyMinutes = totalJourneyMinutes;
            this.startSoc = startSoc;
            this.arrivalTargetSoc = arrivalTargetSoc;
            this.preference = preference;
            this.recommendedStop = recommendedStop;
            this.candidates = candidates;
            this.plannedStops = plannedStops;
            this.backups = backups;
            this.excluded = excluded;
            this.assumptions = assumptions;
            this.confidence = confidence;
            this.noStopWarning = noStopWarning;
        }

        public String getOrigin() { return origin; }
        public String getDestination() { return destination; }
        public double getOriginLat() { return originLat; }
        public double getOriginLng() { return originLng; }
        public double getDestinationLat() { return destinationLat; }
        public double getDestinationLng() { return destinationLng; }
        public double getTotalDistanceKm() { return totalDistanceKm; }
        public double getCorridorKm() { return corridorKm; }
        public int getDriveMinutes() { return driveMinutes; }
        public int getDetourMinutes() { return detourMinutes; }
        public int getChargingMinutes() { return chargingMinutes; }
        public int getTotalJourneyMinutes() { return totalJourneyMinutes; }
        public int getStartSoc() { return startSoc; }
        public int getArrivalTargetSoc() { return arrivalTargetSoc; }
        public String getPreference() { return preference; }
        public CandidateEvaluation getRecommendedStop() { return recommendedStop; }
        public List<CandidateEvaluation> getCandidates() { return candidates; }
        public List<CandidateEvaluation> getCandidateEvaluations() { return candidates; }
        public List<PlannedStop> getPlannedStops() { return plannedStops; }
        public List<PlannedStop> getBackups() { return backups; }
        public List<ExcludedStation> getExcluded() { return excluded; }
        public List<String> getAssumptions() { return assumptions; }
        public String getConfidence() { return confidence; }
        public String getNoStopWarning() { return noStopWarning; }

        public boolean needsCharging() {
            return !plannedStops.isEmpty();
        }
    }

    private RoutePlannerEngine() {
    }

    /** Convenience overload: no measured drive time, so the average-speed estimate is used. */
    public static RoutePlanResult planRoute(String origin, String destination,
                                           double originLat, double originLng,
                                           double destinationLat, double destinationLng,
                                           double totalDistanceKm, double corridorKm,
                                           int startSoc, boolean socIsAuthoritative,
                                           boolean socEstimatedFromProfile, String preference,
                                           Vehicle vehicle, List<ChargerStation> stations) {
        return planRoute(origin, destination, originLat, originLng, destinationLat, destinationLng,
                totalDistanceKm, corridorKm, startSoc, socIsAuthoritative, socEstimatedFromProfile,
                preference, vehicle, stations, 0);
    }

    /**
     * Plans the journey with the distance and drive time Google measured.
     *
     * @param measuredDriveMinutes the traffic-aware drive time from the routing provider, or 0 to
     *                             fall back to the average-speed estimate. Supplying it means the
     *                             plan's total journey time reflects real roads and traffic rather
     *                             than a flat 45 km/h assumption.
     */
    public static RoutePlanResult planRoute(String origin, String destination,
                                           double originLat, double originLng,
                                           double destinationLat, double destinationLng,
                                           double totalDistanceKm, double corridorKm,
                                           int startSoc, boolean socIsAuthoritative,
                                           boolean socEstimatedFromProfile, String preference,
                                           Vehicle vehicle, List<ChargerStation> stations,
                                           int measuredDriveMinutes) {
        double capacity = vehicle != null ? vehicle.effectiveCapacityKwh() : 40.0;
        double efficiency = vehicle != null ? vehicle.effectiveEfficiencyKmPerKwh() : 6.5;
        int reserve = vehicle != null ? vehicle.getMinArrivalSocPercent() : 15;
        int maxTarget = vehicle != null ? vehicle.getMaxChargeSocPercent() : 80;
        int arrivalTarget = Math.max(reserve, Math.min(maxTarget, reserve + 5));
        if (vehicle != null && vehicle.getMinArrivalSocPercent() > 0) {
            arrivalTarget = Math.min(maxTarget, vehicle.getMinArrivalSocPercent() + 5);
        }

        List<CandidateEvaluation> candidates = new ArrayList<>();
        List<ExcludedStation> excluded = new ArrayList<>();
        List<ChargerStation> corridor = new ArrayList<>();

        for (ChargerStation station : safeList(stations)) {
            if (!station.isLiveOnMarketplace()) {
                excluded.add(new ExcludedStation(station, "Paused or awaiting admin verification"));
                continue;
            }
            double distanceFromRoute = GeoUtils.distanceToRouteKm(station.getLatitude(),
                    station.getLongitude(), originLat, originLng, destinationLat, destinationLng);
            if (distanceFromRoute > corridorKm) {
                // Kept as an honest exclusion rather than dropped silently, so the consumer can see
                // that a station exists and choose to widen the corridor.
                excluded.add(new ExcludedStation(station, String.format(Locale.US,
                        "%.0f km from the route, outside the %d km corridor — widen the corridor to include it.",
                        distanceFromRoute, (int) corridorKm)));
                continue;
            }
            if (vehicle != null && !isConnectorCompatible(station, vehicle)) {
                excluded.add(new ExcludedStation(station,
                        "Connector " + station.getConnectorTypes() + " is not supported by your "
                                + (vehicle.getMake() == null ? "vehicle" : vehicle.getMake() + " " + vehicle.getModel())));
                continue;
            }
            corridor.add(station);
            candidates.add(evaluate(station, distanceFromRoute, originLat, originLng,
                    destinationLat, destinationLng, totalDistanceKm, startSoc, capacity, efficiency,
                    reserve, maxTarget, vehicle, preference));
        }

        List<PlannedStop> plannedStops = new ArrayList<>();
        List<PlannedStop> backups = new ArrayList<>();
        String noStopWarning = null;

        List<CandidateEvaluation> ordered = new ArrayList<>(candidates);
        Collections.sort(ordered, Comparator.comparingDouble(CandidateEvaluation::getDistanceAlongRouteKm));

        List<CandidateEvaluation> reachableSorted = new ArrayList<>();
        for (CandidateEvaluation evaluation : ordered) {
            if (evaluation.isReachable()) {
                reachableSorted.add(evaluation);
            }
        }
        Collections.sort(reachableSorted, Comparator.comparingDouble(CandidateEvaluation::getScore));

        // Multi-stop greedy plan: only add a stop where the vehicle would otherwise run short.
        double soc = startSoc;
        double distanceCovered = 0.0;
        for (int stopIndex = 0; stopIndex < MAX_STOPS; stopIndex++) {
            double remainingDistance = totalDistanceKm - distanceCovered;
            int socAtDestinationWithoutStopping = (int) Math.round(soc
                    - drain(remainingDistance, capacity, efficiency));
            if (socAtDestinationWithoutStopping >= arrivalTarget) {
                break;
            }
            CandidateEvaluation chosen = null;
            for (CandidateEvaluation evaluation : reachableSorted) {
                if (evaluation.getDistanceAlongRouteKm() <= distanceCovered + 1.0) {
                    continue;
                }
                int socAtCandidate = (int) Math.round(soc - drain(
                        evaluation.getDistanceAlongRouteKm() - distanceCovered, capacity, efficiency));
                if (socAtCandidate < reserve) {
                    continue;
                }
                if (chosen == null || evaluation.getScore() < chosen.getScore()) {
                    chosen = evaluation;
                }
            }
            if (chosen == null) {
                noStopWarning = "No compatible charger inside the " + (int) corridorKm
                        + " km corridor can be reached before the " + reserve
                        + "% arrival reserve. No charging stop was planned, and Amperly will not route you beyond the estimated safe range.";
                break;
            }
            PlannedStop stop = toPlannedStop(chosen, plannedStops.size() + 1, false, soc, capacity, efficiency);
            plannedStops.add(stop);
            soc = stop.getDepartureSoc();
            distanceCovered = stop.getDistanceAlongRouteKm() + stop.getDetourKm();
        }

        CandidateEvaluation recommended = null;
        if (!plannedStops.isEmpty()) {
            recommended = findCandidate(candidates, plannedStops.get(0).getStation().getId());
        } else if (!reachableSorted.isEmpty()) {
            recommended = reachableSorted.get(0);
        } else if (!candidates.isEmpty()) {
            recommended = candidates.get(0);
        }

        if (recommended != null) {
            backups.addAll(buildBackups(reachableSorted, recommended.getStation().getId(), capacity, efficiency, startSoc));
        }

        int detourMinutes = 0;
        int chargingMinutes = 0;
        for (PlannedStop stop : plannedStops) {
            detourMinutes += stop.getDetourMinutes();
            chargingMinutes += stop.getChargingMinutes();
        }
        if (plannedStops.isEmpty() && recommended != null && !recommended.isReachable()) {
            detourMinutes = recommended.getDetourMinutes();
            chargingMinutes = recommended.getChargingMinutes();
        }

        boolean measuredDriveTime = measuredDriveMinutes > 0;
        int driveMinutes = measuredDriveTime ? measuredDriveMinutes
                : (int) Math.round((totalDistanceKm / AVERAGE_SPEED_KMH) * 60.0);
        int totalJourneyMinutes = driveMinutes + detourMinutes + chargingMinutes;

        List<String> assumptions = new ArrayList<>();
        if (measuredDriveTime) {
            assumptions.add("Distance and drive time from Google Maps for this route: "
                    + String.format(Locale.US, "%.1f", totalDistanceKm) + " km, "
                    + measuredDriveMinutes + " min (traffic-aware where Google publishes it).");
        } else {
            assumptions.add("Distance of " + String.format(Locale.US, "%.0f", totalDistanceKm)
                    + " km at an average " + (int) AVERAGE_SPEED_KMH
                    + " km/h, with no live traffic or weather.");
        }
        assumptions.add("Vehicle efficiency of " + String.format(Locale.US, "%.1f", efficiency)
                + " km/kWh on a usable capacity of " + String.format(Locale.US, "%.1f", capacity) + " kWh.");
        assumptions.add("Starting battery " + startSoc + "% "
                + (socIsAuthoritative ? "(your dashboard value, treated as authoritative)"
                : socEstimatedFromProfile ? "(Amperly estimate from the vehicle profile — you can correct it)"
                : "(entered by you)") + ".");
        assumptions.add("Minimum arrival reserve " + reserve + "%, corridor " + (int) corridorKm + " km.");
        assumptions.add("Charging time assumes 80% of the lower of vehicle and charger peak power to allow for taper.");

        String confidence;
        if (socIsAuthoritative && totalDistanceKm <= 200 && !corridor.isEmpty()) {
            confidence = CONFIDENCE_HIGH;
        } else if (socEstimatedFromProfile && totalDistanceKm > 250) {
            confidence = CONFIDENCE_LOW;
        } else if (corridor.isEmpty()) {
            confidence = CONFIDENCE_LOW;
        } else {
            confidence = CONFIDENCE_MEDIUM;
        }

        return new RoutePlanResult(origin, destination, originLat, originLng, destinationLat,
                destinationLng, totalDistanceKm, corridorKm, driveMinutes, detourMinutes,
                chargingMinutes, totalJourneyMinutes, startSoc, arrivalTarget, preference,
                recommended, candidates, plannedStops, backups, excluded, assumptions, confidence,
                noStopWarning);
    }

    /** Convenience overload for callers that only have the names and a distance between two points. */
    public static RoutePlanResult planRoute(String origin, String destination, double totalDistanceKm,
                                            int startSoc, Vehicle vehicle, List<ChargerStation> stations) {
        return planRoute(origin, destination, 12.2958, 76.6394, 12.9141, 74.8560,
                totalDistanceKm, 10.0, startSoc, false, false, PREF_FASTEST, vehicle, stations, 0);
    }

    private static CandidateEvaluation evaluate(ChargerStation station, double distanceFromRoute,
                                               double originLat, double originLng,
                                               double destinationLat, double destinationLng,
                                               double totalDistanceKm, int startSoc, double capacity,
                                               double efficiency, int reserve, int maxTarget,
                                               Vehicle vehicle, String preference) {
        double fraction = GeoUtils.fractionAlongRoute(station.getLatitude(), station.getLongitude(),
                originLat, originLng, destinationLat, destinationLng);
        fraction = Math.max(0.05, Math.min(1.0, fraction));
        double distanceAlong = totalDistanceKm * fraction;
        double detourKm = GeoUtils.detourKm(distanceFromRoute);
        int detourMinutes = GeoUtils.detourMinutes(detourKm);

        int arrivalSoc = clampSoc((int) Math.round(startSoc - drain(distanceAlong, capacity, efficiency)));
        double distanceAfterCharging = (totalDistanceKm - distanceAlong) + detourKm;
        double drainAfterCharging = drain(distanceAfterCharging, capacity, efficiency);
        int socNeededToReachDestination = (int) Math.ceil(drainAfterCharging);
        int targetDeparture = Math.min(maxTarget, arrivalSoc + socNeededToReachDestination);
        targetDeparture = Math.max(targetDeparture, Math.min(maxTarget, arrivalSoc + 10));
        targetDeparture = Math.min(100, targetDeparture);

        double energyAdded = Math.max(0.0, ((targetDeparture - arrivalSoc) / 100.0) * capacity);
        int chargingMinutes = chargingMinutes(energyAdded, station, vehicle);
        int departureSoc = clampSoc((int) Math.round(arrivalSoc + ((energyAdded / capacity) * 100.0)));
        int arrivalAtDestination = clampSoc((int) Math.round(departureSoc - drainAfterCharging));

        boolean reachable = arrivalSoc >= reserve;
        boolean paidBilling = station.supportsPaidEnergyBilling();
        // Planning estimate only: energy plus the disclosed service and gateway fees.
        double estimatedCost = (energyAdded * station.getPricePerKwh())
                + station.getServiceFee() + station.getGatewayFee();

        double score = score(preference, detourMinutes, chargingMinutes, estimatedCost, detourKm,
                station, paidBilling, arrivalAtDestination);

        String reason = "Detour: +" + String.format(Locale.US, "%.1f", detourKm) + " km / +"
                + detourMinutes + " min\n"
                + "Estimated charge: " + chargingMinutes + " min\n"
                + "Estimated energy added: " + String.format(Locale.US, "%.1f", energyAdded) + " kWh\n"
                + "Expected battery on arrival: " + arrivalSoc + "%\n"
                + "Expected battery after charging: " + departureSoc + "%\n"
                + "Expected battery at destination: " + arrivalAtDestination + "%"
                + (paidBilling ? "" : "\nNo verified meter: discovery only, no automatic paid billing.");

        return new CandidateEvaluation(station, distanceFromRoute, distanceAlong, detourKm,
                detourMinutes, arrivalSoc, departureSoc, arrivalAtDestination, energyAdded,
                chargingMinutes, estimatedCost, reachable, paidBilling, score, reason);
    }

    private static double score(String preference, int detourMinutes, int chargingMinutes,
                                double estimatedCost, double detourKm, ChargerStation station,
                                boolean paidBilling, int arrivalAtDestination) {
        double base;
        if (PREF_CHEAPEST.equals(preference)) {
            base = estimatedCost + (detourKm * 2.0);
        } else if (PREF_RELIABLE.equals(preference)) {
            base = detourMinutes + (Math.max(0, 100.0 - station.getReliabilityScore()) * 1.5)
                    + (station.getIncidentCount() * 8.0);
        } else if (PREF_SHORTEST_DETOUR.equals(preference)) {
            base = detourKm * 10.0;
        } else {
            base = detourMinutes + chargingMinutes;
        }
        // Stations that cannot bill a paid energy session are less useful as a planned stop.
        if (!paidBilling) {
            base += 25.0;
        }
        if (arrivalAtDestination < 0) {
            base += 40.0;
        }
        return base;
    }

    private static int chargingMinutes(double energyAddedKwh, ChargerStation station, Vehicle vehicle) {
        if (energyAddedKwh <= 0.1) {
            return 0;
        }
        double chargerPower = station.getMaxPowerKw() > 0 ? station.getMaxPowerKw() : 7.2;
        double vehiclePower = vehicle == null ? chargerPower
                : (station.isDcCapable() ? Math.max(1.0, vehicle.getMaxDcKw()) : Math.max(1.0, vehicle.getMaxAcKw()));
        double effectivePower = Math.max(1.0, Math.min(chargerPower, vehiclePower) * CHARGE_TAPER_FACTOR);
        int minutes = (int) Math.ceil((energyAddedKwh / effectivePower) * 60.0);
        return Math.max(10, minutes);
    }

    private static PlannedStop toPlannedStop(CandidateEvaluation evaluation, int sequence,
                                            boolean backup, double socBefore, double capacity,
                                            double efficiency) {
        int arrivalSoc = clampSoc((int) Math.round(socBefore - drain(
                evaluation.getDistanceAlongRouteKm(), capacity, efficiency)));
        double energyAdded = Math.max(0.0, ((evaluation.getDepartureSoc() - arrivalSoc) / 100.0) * capacity);
        return new PlannedStop(evaluation.getStation(), sequence, backup, evaluation.isReachable(),
                evaluation.getDistanceAlongRouteKm(), evaluation.getDetourKm(),
                evaluation.getDetourMinutes(), arrivalSoc, evaluation.getDepartureSoc(),
                energyAdded, evaluation.getChargingMinutes(), evaluation.getEstimatedCost(),
                evaluation.getReason());
    }

    private static List<PlannedStop> buildBackups(List<CandidateEvaluation> reachableSorted,
                                                 String chosenStationId, double capacity,
                                                 double efficiency, int startSoc) {
        List<PlannedStop> backups = new ArrayList<>();
        CandidateEvaluation chosen = findCandidate(reachableSorted, chosenStationId);
        if (chosen == null) {
            return backups;
        }
        CandidateEvaluation before = null;
        CandidateEvaluation after = null;
        for (CandidateEvaluation evaluation : reachableSorted) {
            if (evaluation.getStation().getId().equals(chosenStationId)) {
                continue;
            }
            if (evaluation.getDistanceAlongRouteKm() < chosen.getDistanceAlongRouteKm()) {
                if (before == null || evaluation.getDistanceAlongRouteKm() > before.getDistanceAlongRouteKm()) {
                    before = evaluation;
                }
            } else if (after == null || evaluation.getDistanceAlongRouteKm() < after.getDistanceAlongRouteKm()) {
                after = evaluation;
            }
        }
        if (before != null) {
            backups.add(toPlannedStop(before, 0, true, startSoc, capacity, efficiency));
        }
        if (after != null) {
            backups.add(toPlannedStop(after, 0, true, startSoc, capacity, efficiency));
        }
        return backups;
    }

    private static CandidateEvaluation findCandidate(List<CandidateEvaluation> candidates, String stationId) {
        for (CandidateEvaluation evaluation : candidates) {
            if (evaluation.getStation().getId().equals(stationId)) {
                return evaluation;
            }
        }
        return null;
    }

    public static boolean isConnectorCompatible(ChargerStation station, Vehicle vehicle) {
        if (vehicle == null || vehicle.getConnectorType() == null) {
            return true;
        }
        for (String connector : station.connectorTypeList()) {
            if (connector.equalsIgnoreCase(vehicle.getConnectorType())) {
                return true;
            }
        }
        return false;
    }

    public static double drain(double distanceKm, double capacity, double efficiency) {
        if (capacity <= 0 || efficiency <= 0) {
            return 0;
        }
        return ((distanceKm / efficiency) / capacity) * 100.0;
    }

    public static int clampSoc(int soc) {
        return Math.max(0, Math.min(100, soc));
    }

    private static List<ChargerStation> safeList(List<ChargerStation> stations) {
        return stations == null ? Collections.emptyList() : stations;
    }
}
