package com.amperly.app.core.state;

import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.RoutePlan;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.engine.routing.RoutePlannerEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The phone experience and the projected Android Auto experience share one source of truth. The car
 * display reads from here so it can never drift from what the phone planned, and a battery
 * correction from the car immediately updates the phone plan too.
 */
public final class AppStateStore {

    public interface Listener {
        void onStateChanged();
    }

    private static volatile AppStateStore INSTANCE;

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private String userId = "";
    private String role = "CONSUMER";
    private Vehicle vehicle;
    private RoutePlan activePlan;
    private RoutePlannerEngine.RoutePlanResult lastPlanResult;
    private int estimatedSoc = -1;
    private int authoritativeSoc = -1;   // -1 means the consumer has not corrected the estimate yet
    private long socUpdatedAtMs;
    private boolean socIsAuthoritative;
    private String lastRecalculationSummary = "";
    private String activeReservationId = "";
    private String activeSessionId = "";
    private String lastBookingCode = "";
    private boolean carConnected;
    private String noSafeChargerMessage = "";

    private AppStateStore() {
    }

    public static AppStateStore get() {
        if (INSTANCE == null) {
            synchronized (AppStateStore.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AppStateStore();
                }
            }
        }
        return INSTANCE;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void notifyChanged() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onStateChanged();
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
        notifyChanged();
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
        notifyChanged();
    }

    public RoutePlan getActivePlan() {
        return activePlan;
    }

    public void setActivePlan(RoutePlan activePlan) {
        this.activePlan = activePlan;
        notifyChanged();
    }

    public RoutePlannerEngine.RoutePlanResult getLastPlanResult() {
        return lastPlanResult;
    }

    public void setLastPlanResult(RoutePlannerEngine.RoutePlanResult lastPlanResult) {
        this.lastPlanResult = lastPlanResult;
        notifyChanged();
    }

    public int getEstimatedSoc() {
        return estimatedSoc;
    }

    public void setEstimatedSoc(int estimatedSoc) {
        this.estimatedSoc = estimatedSoc;
    }

    /** The value used for planning: the consumer's corrected figure when one exists. */
    public int getCurrentSoc() {
        return socIsAuthoritative && authoritativeSoc >= 0 ? authoritativeSoc : estimatedSoc;
    }

    public int getAuthoritativeSoc() {
        return authoritativeSoc;
    }

    public boolean isSocAuthoritative() {
        return socIsAuthoritative;
    }

    public long getSocUpdatedAtMs() {
        return socUpdatedAtMs;
    }

    public void applySocCorrection(int previousEstimate, int actualDashboardSoc) {
        this.estimatedSoc = previousEstimate;
        this.authoritativeSoc = actualDashboardSoc;
        this.socIsAuthoritative = true;
        this.socUpdatedAtMs = System.currentTimeMillis();
        notifyChanged();
    }

    public String getLastRecalculationSummary() {
        return lastRecalculationSummary;
    }

    public void setLastRecalculationSummary(String lastRecalculationSummary) {
        this.lastRecalculationSummary = lastRecalculationSummary == null ? "" : lastRecalculationSummary;
        notifyChanged();
    }

    public String getActiveReservationId() {
        return activeReservationId;
    }

    public void setActiveReservation(Reservation reservation) {
        this.activeReservationId = reservation == null ? "" : reservation.getId();
        notifyChanged();
    }

    public void setActiveReservationId(String reservationId) {
        this.activeReservationId = reservationId == null ? "" : reservationId;
        notifyChanged();
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public void setActiveSessionId(String activeSessionId) {
        this.activeSessionId = activeSessionId == null ? "" : activeSessionId;
        notifyChanged();
    }

    public String getLastBookingCode() {
        return lastBookingCode;
    }

    public void setLastBookingCode(String lastBookingCode) {
        this.lastBookingCode = lastBookingCode == null ? "" : lastBookingCode;
        notifyChanged();
    }

    public boolean isCarConnected() {
        return carConnected;
    }

    public void setCarConnected(boolean carConnected) {
        this.carConnected = carConnected;
        notifyChanged();
    }

    public String getNoSafeChargerMessage() {
        return noSafeChargerMessage;
    }

    public void setNoSafeChargerMessage(String noSafeChargerMessage) {
        this.noSafeChargerMessage = noSafeChargerMessage == null ? "" : noSafeChargerMessage;
        notifyChanged();
    }

    public List<ChargerStation> safeReachableStations() {
        if (lastPlanResult == null || lastPlanResult.getCandidateEvaluations() == null) {
            return Collections.emptyList();
        }
        List<ChargerStation> result = new ArrayList<>();
        for (RoutePlannerEngine.CandidateEvaluation evaluation : lastPlanResult.getCandidateEvaluations()) {
            if (evaluation.isReachable()) {
                result.add(evaluation.getStation());
            }
        }
        return result;
    }

    public void reset() {
        activePlan = null;
        lastPlanResult = null;
        estimatedSoc = -1;
        authoritativeSoc = -1;
        socIsAuthoritative = false;
        lastRecalculationSummary = "";
        noSafeChargerMessage = "";
        notifyChanged();
    }
}
