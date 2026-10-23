package com.amperly.app.car;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Payment;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.RoutePlan;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.availability.AvailabilityEngine;
import com.amperly.app.engine.booking.ReservationEngine;
import com.amperly.app.engine.soc.SoCEstimatorEngine;
import com.amperly.app.payment.PaymentManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Bridge between the projected Android Auto experience and the same data the phone uses.
 *
 * The car only ever reads a small, glanceable snapshot: the active plan, the reachable chargers, the
 * battery value the driver last confirmed, and the reservation. Everything it can do is a safe,
 * short action: update the battery, reserve the next free slot, or start navigation.
 */
public final class CarBridge {

    public static class Snapshot {
        private final RoutePlan plan;
        private final Vehicle vehicle;
        private final Reservation reservation;
        private final List<ChargerStation> reachableStations;
        private final ChargerStation recommendedStation;
        private final String recommendedDetail;
        private final int currentSoc;
        private final boolean socAuthoritative;
        private final String noSafeChargerMessage;

        public Snapshot(RoutePlan plan, Vehicle vehicle, Reservation reservation,
                        List<ChargerStation> reachableStations, ChargerStation recommendedStation,
                        String recommendedDetail, int currentSoc, boolean socAuthoritative,
                        String noSafeChargerMessage) {
            this.plan = plan;
            this.vehicle = vehicle;
            this.reservation = reservation;
            this.reachableStations = reachableStations;
            this.recommendedStation = recommendedStation;
            this.recommendedDetail = recommendedDetail;
            this.currentSoc = currentSoc;
            this.socAuthoritative = socAuthoritative;
            this.noSafeChargerMessage = noSafeChargerMessage;
        }

        public RoutePlan getPlan() { return plan; }
        public Vehicle getVehicle() { return vehicle; }
        public Reservation getReservation() { return reservation; }
        public List<ChargerStation> getReachableStations() { return reachableStations; }
        public ChargerStation getRecommendedStation() { return recommendedStation; }
        public String getRecommendedDetail() { return recommendedDetail; }
        public int getCurrentSoc() { return currentSoc; }
        public boolean isSocAuthoritative() { return socAuthoritative; }
        public String getNoSafeChargerMessage() { return noSafeChargerMessage; }

        public String routeTitle() {
            if (plan == null) {
                return "No active trip";
            }
            return plan.getOrigin() + " → " + plan.getDestination() + " ("
                    + Math.round(plan.getTotalDistanceKm()) + " km)";
        }

        public String summaryLine() {
            if (plan == null) {
                return "Plan a trip on your phone, then reconnect. Charging stays glanceable here.";
            }
            String socLabel = socAuthoritative ? "actual" : "estimated";
            int usableKm = (int) Math.max(0, ((currentSoc - Math.max(5, vehicle == null ? 15
                    : vehicle.getMinArrivalSocPercent())) / 100.0)
                    * (vehicle == null ? 40 : vehicle.effectiveCapacityKwh())
                    * (vehicle == null ? 6.5 : vehicle.effectiveEfficiencyKmPerKwh()));
            return "Battery " + currentSoc + "% (" + socLabel + ") · about " + usableKm
                    + " km usable · reserve " + (vehicle == null ? 15 : vehicle.getMinArrivalSocPercent()) + "%";
        }
    }

    public interface Callback<T> {
        void onResult(T value);
    }

    private CarBridge() {
    }

    public static void load(AmperlyRepository repo, Callback<Snapshot> callback) {
        repo.background(() -> {
            String userId = AuthManager.getInstance().getCurrentUserId();
            RoutePlan plan = AppStateStore.get().getActivePlan();
            if (plan == null) {
                plan = repo.getActiveRoutePlanSync(userId);
            }
            Vehicle vehicle = repo.getVehicleSync(userId);
            Reservation reservation = repo.getActiveReservationSync(userId);

            List<ChargerStation> reachable = new ArrayList<>();
            ChargerStation recommended = null;
            String detail = null;

            if (plan != null) {
                List<ChargerStation> candidates = repo.getApprovedStationsSync();
                int soc = AppStateStore.get().getCurrentSoc() >= 0 ? AppStateStore.get().getCurrentSoc()
                        : plan.getPlannedStartSoc();
                reachable = SoCEstimatorEngine.safeAlternatives(candidates, soc, plan.getOriginLat(),
                        plan.getOriginLng(), vehicle, 5);
                if (!reachable.isEmpty()) {
                    recommended = reachable.get(0);
                }
                if (recommended != null) {
                    double detour = 0;
                    for (com.amperly.app.data.local.entity.RouteStop stop : repo.getRouteStopsSync(plan.getId())) {
                        if (stop.getStationId().equals(recommended.getId())) {
                            detour = stop.getDetourKm();
                            break;
                        }
                    }
                    detail = "Detour +" + String.format(java.util.Locale.US, "%.1f", detour)
                            + " km · " + recommended.getConnectorTypes() + " · "
                            + (int) recommended.getMaxPowerKw() + " kW · "
                            + String.format(java.util.Locale.US, "₹%.0f", recommended.getPricePerKwh()) + "/kWh";
                }
            }

            return new Snapshot(plan, vehicle, reservation, reachable, recommended, detail,
                    AppStateStore.get().getCurrentSoc(), AppStateStore.get().isSocAuthoritative(),
                    AppStateStore.get().getNoSafeChargerMessage());
        }, callback::onResult);
    }

    /** Applies the driver's dashboard value as authoritative and recalculates the remaining trip. */
    public static void applyBatteryUpdate(AmperlyRepository repo, int actualSoc,
                                         Callback<String> callback) {
        repo.background(() -> {
            String userId = AuthManager.getInstance().getCurrentUserId();
            RoutePlan plan = AppStateStore.get().getActivePlan();
            if (plan == null) {
                plan = repo.getActiveRoutePlanSync(userId);
            }
            if (plan == null) {
                return "Plan a trip on your phone first so the battery value has a route to correct.";
            }
            Vehicle vehicle = repo.getVehicleSync(userId);
            int previousEstimate = AppStateStore.get().getCurrentSoc() >= 0
                    ? AppStateStore.get().getCurrentSoc() : plan.getPlannedStartSoc();

            // The car display has no map position of its own: the remaining trip is estimated from
            // the planned origin, and the phone can refine it with a real GPS fix when parked.
            double currentLat = plan.getOriginLat();
            double currentLng = plan.getOriginLng();

            SoCEstimatorEngine.RecalculationResult result =
                    SoCEstimatorEngine.applyAuthoritativeSocOverride(plan.getVehicleId(), plan.getId(),
                            previousEstimate, actualSoc, currentLat, currentLng,
                            plan.getTotalDistanceKm() * 0.6, plan.getDestinationLat(),
                            plan.getDestinationLng(), vehicle, repo.getApprovedStationsSync(),
                            AppStateStore.get().safeReachableStations());

            repo.saveBatteryUpdate(result.getAuditRecord());
            AppStateStore.get().applySocCorrection(previousEstimate, actualSoc);
            AppStateStore.get().setLastRecalculationSummary(result.getExplanation());
            AppStateStore.get().setNoSafeChargerMessage(result.getWarningMessage() == null ? ""
                    : result.getWarningMessage());

            if (result.isNoSafeCharger()) {
                return "Battery " + actualSoc + "% applied. " + result.getWarningMessage();
            }
            return "Battery " + actualSoc + "% applied. Next safe charger: "
                    + (result.getRecommendedNextCharger() == null ? "none"
                    : result.getRecommendedNextCharger().getName() + ", "
                    + Math.round(result.getRecommendedStop().getDistanceFromCurrentKm()) + " km away");
        }, callback::onResult);
    }

    /** Reserves the next free slot on a station from the car display. */
    public static void reserveNextFreeSlot(AmperlyRepository repo, String stationId,
                                          Callback<String> callback) {
        repo.background(() -> {
            ChargerStation station = repo.getStationSync(stationId);
            if (station == null) {
                return "Station not found.";
            }
            if (!station.supportsPaidEnergyBilling()) {
                return station.getName() + " has no verified meter, so no paid energy session can be booked. "
                        + "It stays available for discovery only.";
            }
            Calendar day = Calendar.getInstance();
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            List<AvailabilityEngine.Slot> slots = new ArrayList<>();
            for (int offset = 0; offset < 2 && slots.isEmpty(); offset++) {
                long start = day.getTimeInMillis() + (offset * 24L * 60L * 60L * 1000L);
                for (AvailabilityEngine.Slot slot : repo.getSlots(stationId, start,
                        AvailabilityEngine.DEFAULT_SLOT_MINUTES, 12)) {
                    if (slot.isAvailable()) {
                        slots.add(slot);
                    }
                }
            }
            if (slots.isEmpty()) {
                return "No free slot published for " + station.getName() + " right now.";
            }
            AvailabilityEngine.Slot slot = slots.get(0);
            String userId = AuthManager.getInstance().getCurrentUserId();
            String key = PaymentManager.newIdempotencyKey(userId, stationId, slot.getStartMs());
            double energyEstimate = station.getMaxPowerKw() * 1.0;
            ReservationEngine.PricePreview preview = ReservationEngine.preview(station, energyEstimate);
            PaymentManager.PaymentResult paymentResult = PaymentManager.authorize(userId,
                    station.getHostId(), null, preview.getAuthorizedAmount(), key);
            if (!paymentResult.isSuccess()) {
                return paymentResult.getMessage();
            }
            Payment payment = paymentResult.getPayment();
            String reservationId = UUID.randomUUID().toString();
            String code = ReservationEngine.buildBookingCode();
            payment.setReservationId(reservationId);

            Reservation reservation = new Reservation(reservationId, userId, stationId, station.getName(),
                    slot.getPortId(), slot.getPortLabel(), code, slot.getStartMs(), slot.getEndMs(),
                    AppConstants.STATUS_RESERVED, station.getPricePerKwh(), station.getServiceFee(),
                    station.getGatewayFee(), 0.0, true, payment.getId(), 0, 0, System.currentTimeMillis());
            // authorize_payment resolves the booking server-side, so the reservation is written
            // first.
            repo.saveReservation(reservation);
            repo.authorizePayment(payment, stationId);
            AppStateStore.get().setActiveReservation(reservation);
            AppStateStore.get().setLastBookingCode(code);
            return "Reserved " + slot.getPortLabel() + " at " + station.getName() + " · code " + code;
        }, callback::onResult);
    }
}
