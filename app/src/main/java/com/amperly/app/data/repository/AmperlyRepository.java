package com.amperly.app.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.data.local.AmperlyDatabase;
import com.amperly.app.data.local.entity.AndroidAutoSession;
import com.amperly.app.data.local.entity.AvailabilitySchedule;
import com.amperly.app.data.local.entity.BatteryUpdate;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.ChargingSession;
import com.amperly.app.data.local.entity.ChatMessage;
import com.amperly.app.data.local.entity.CommunicationThread;
import com.amperly.app.data.local.entity.Dispute;
import com.amperly.app.data.local.entity.HostEnforcementAction;
import com.amperly.app.data.local.entity.Invoice;
import com.amperly.app.data.local.entity.MeterReading;
import com.amperly.app.data.local.entity.Notification;
import com.amperly.app.data.local.entity.Payment;
import com.amperly.app.data.local.entity.Report;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.RoutePlan;
import com.amperly.app.data.local.entity.RouteStop;
import com.amperly.app.data.local.entity.SafetyEvent;
import com.amperly.app.data.local.entity.TripEstimate;
import com.amperly.app.data.local.entity.TrustedContact;
import com.amperly.app.data.local.entity.User;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.data.remote.SupabaseRest;
import com.amperly.app.data.remote.SyncEngine;
import com.amperly.app.engine.analytics.HostAnalyticsEngine;
import com.amperly.app.engine.availability.AvailabilityEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single entry point for reading and writing Amperly data. Room queries that would block are pushed
 * onto a background executor and their results are delivered back on the main thread.
 */
public class AmperlyRepository {

    private static final String TAG = "AmperlyRepository";

    public interface Callback<T> {
        void onResult(T value);
    }

    private static volatile AmperlyRepository INSTANCE;

    private final AmperlyDatabase db;
    private final Context appContext;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    /**
     * Writes run one at a time, in the order they were made. Child rows must reach the server
     * after their parent — a station's ports and schedules are authorised by looking up that
     * station — and a single write thread is what keeps that order without a queue.
     */
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Set when the server refused to create the signed-in account's profile row. The account then
     * exists on the device but not in {@code public.users}, which makes it anonymous to every RLS
     * policy, so the UI has to say so instead of showing a silently empty app.
     */
    private volatile boolean profileWriteFailed;

    private AmperlyRepository(Context context) {
        db = AmperlyDatabase.getInstance(context);
        appContext = context.getApplicationContext();
    }

    public static AmperlyRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AmperlyRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AmperlyRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public AmperlyDatabase database() {
        return db;
    }

    /**
     * Runs background work and delivers the result on the main thread.
     *
     * <p>A failure delivers {@code null}, but it is also logged. Swallowing it silently is how a
     * screen ends up looking like it works while showing nothing at all, which is exactly the
     * kind of bug that is hardest to report and hardest to find.
     */
    public <T> void background(Callable<T> work, Callback<T> callback) {
        executor.execute(() -> {
            try {
                T value = work.call();
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(value));
                }
            } catch (Exception e) {
                Log.e(TAG, "Background work failed: " + e, e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onResult(null));
                }
            }
        });
    }

    public void run(Runnable work) {
        writeExecutor.execute(() -> {
            try {
                work.run();
            } catch (Exception e) {
                Log.e(TAG, "Background task failed: " + e, e);
            }
        });
    }

    // ------------------------------------------------------------------ writes
    // A save writes Room for an instant UI and sends the same change to Supabase in the same
    // task. There is no outbox: a write that cannot reach the server is recorded for the
    // account screen instead of being replayed later.

    private void pushUpsert(String table, Object entity) {
        try {
            SyncEngine.save(table, entity);
        } catch (RuntimeException e) {
            recordWriteFailure("Could not save " + table + " to the database", e);
        }
    }

    private void pushUpsertAll(String table, List<?> entities) {
        if (entities == null) {
            return;
        }
        for (Object entity : entities) {
            pushUpsert(table, entity);
        }
    }

    private void pushDelete(String table, String rowId) {
        try {
            SyncEngine.delete(table, rowId);
        } catch (RuntimeException e) {
            recordWriteFailure("Could not delete from " + table, e);
        }
    }

    private void pushPatch(String table, String rowId, Object payload) {
        try {
            SyncEngine.patch(table, rowId, payload);
        } catch (RuntimeException e) {
            recordWriteFailure("Could not update " + table, e);
        }
    }

    /**
     * Runs a Postgres function. Used where the change must be produced by the server's own
     * logic rather than by copying a locally edited row onto a table.
     */
    private void pushRpc(String function, Map<String, Object> args) {
        try {
            SyncEngine.call(function, args);
        } catch (RuntimeException e) {
            recordWriteFailure("Could not call " + function, e);
        }
    }

    private void recordWriteFailure(String action, RuntimeException e) {
        String message = action + ": " + e.getMessage();
        Log.w(TAG, message);
        SyncEngine.recordWriteError(appContext, message);
    }

    private static Map<String, Object> singleArg(String key, Object value) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(key, value);
        return args;
    }

    /** Honest one-line state for the UI: when the last pull worked, and the last write error. */
    public String syncStatus() {
        return SyncEngine.statusSummary(appContext, db);
    }

    public boolean isBackendEnabled() {
        return SyncEngine.isEnabled();
    }

    // ---------------------------------------------------------------- users

    public LiveData<User> getUser(String id) {
        return db.userDao().getUserById(id);
    }

    public User getUserSync(String id) {
        return db.userDao().getUserByIdSync(id);
    }

    public List<User> getAllUsersSync() {
        return db.userDao().getAllUsersSync();
    }

    // ------------------------------------------------------ sign in / sign out

    /**
     * The application profile behind a signed-in Supabase account.
     *
     * <p>Read from the server rather than the local copy on purpose: the local
     * {@code users} table has no authUserId column — Gson ignores that key because it is
     * a server-only column — so there is nothing local to match on. Row Level Security
     * returns only the caller's own row here (an administrator sees everyone's).
     */
    public User findAppUserByAuthId(String authUserId) {
        if (authUserId == null || authUserId.isEmpty()) {
            return null;
        }
        try {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("select", "*");
            query.put("id", "eq." + authUserId);
            query.put("limit", "1");
            List<User> rows = SupabaseRest.select("users", User.class, query);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (Exception ignored) {}

        try {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("select", "*");
            query.put("authUserId", "eq." + authUserId);
            query.put("limit", "1");
            List<User> rows = SupabaseRest.select("users", User.class, query);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (Exception ignored) {}

        return null;
    }

    public User resolveOrCreateAppUser(String authUserId, String email, String fallbackName) {
        User existing = findAppUserByAuthId(authUserId);
        if (existing != null) {
            db.userDao().insert(existing);
            return existing;
        }

        String displayName = (fallbackName == null || fallbackName.trim().isEmpty())
                ? displayNameFrom(email) : fallbackName.trim();

        User localUser = new User(
                authUserId, displayName, email, "", AppConstants.ROLE_CONSUMER,
                "", "VERIFIED", false
        );

        // Always save locally so the user is never blocked from using the app
        db.userDao().insert(localUser);

        try {
            User created = SupabaseRest.upsert("users",
                    newUserProfileRow(authUserId, email, displayName), User.class);
            if (created != null) {
                profileWriteFailed = false;
                db.userDao().insert(created);
                return created;
            }
            profileWriteFailed = true;
        } catch (Exception e) {
            profileWriteFailed = true;
            String message = "Remote user profile creation failed: " + e.getMessage();
            Log.w(TAG, message);
            // There is no queue to retry this later. The account row is written by the
            // on_auth_user_created trigger on the server, and didProfileWriteFailed() makes the
            // sign-in screen say so instead of showing an app where every write is refused.
            SyncEngine.recordWriteError(appContext, message);
        }

        return localUser;
    }

    /** True when the last profile creation reached the device but not the server. */
    public boolean didProfileWriteFail() {
        return profileWriteFailed;
    }

    /**
     * The exact row a self-registered account inserts into {@code public.users}.
     *
     * <p>Column names have to match the table exactly. An earlier version also sent
     * {@code auth_user_id}, {@code verification_status} and {@code is_blocked} — columns that do
     * not exist — and PostgREST rejects the WHOLE insert when one column is unknown. No profile
     * row was ever created, so a brand-new account was anonymous to every policy that resolves the
     * caller through {@code current_app_user_id()}: the app looked empty and every write failed.
     *
     * <p>The role sent here must also satisfy the {@code users_insert_self} policy, which allows
     * insert only as CONSUMER or HOST. That is the same rule that makes one account able to be both:
     * host authority comes from station ownership, not from the role column.
     *
     * <p>Package-private so it can be asserted against {@code supabase/schema.sql} in a unit test.
     */
    @NonNull
    static Map<String, Object> newUserProfileRow(@NonNull String id, @Nullable String email,
                                                @NonNull String displayName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("authUserId", id);
        row.put("email", email);
        row.put("name", displayName);
        row.put("phone", "");
        row.put("role", AppConstants.ROLE_CONSUMER);
        row.put("organizationName", "");
        row.put("verificationStatus", "VERIFIED");
        row.put("isBlocked", false);
        return row;
    }

    private static String displayNameFrom(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "Amperly user";
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email.trim();
    }

    /**
     * Wipes every local table and the pull cursors.
     *
     * <p>Used when signing out, so one account's reservations and receipts are not left on
     * screen for whoever signs in next.
     */
    public void clearLocalData() {
        db.clearAllTables();
        SyncEngine.reset(appContext);
    }

    /**
     * Renames the signed-in account, locally and on the server.
     *
     * <p>The local row is written for an instant repaint and the server change is sent in the
     * same call. A failure is logged and shown in the account screen's sync status; the local
     * copy stays, because the database is the authoritative copy.
     *
     * <p>Only {@code name} is sent. The {@code users_update_self_or_admin} policy lets a person
     * update their own row, while {@code prevent_privilege_escalation} refuses any change to
     * role, blocked state or verification — none of which this payload carries.
     *
     * <p>Must be called off the main thread (Room); the caller gets the updated entity back.
     */
    @Nullable
    public User updateDisplayName(@Nullable User user, @Nullable String newName) {
        if (user == null) {
            return null;
        }
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty()) {
            return null;
        }

        user.setName(name);
        // insert(REPLACE) rather than update(): the row is written even if it is somehow not
        // in Room yet, so the local copy can never be the reason a rename appears to vanish.
        db.userDao().insert(user);

        // Also update the AuthManager's user object so the UI reflects the change immediately
        AuthManager.getInstance().setCurrentUser(user);

        // A rename only changes one column of a row that already exists, so it is sent as an
        // update (PATCH). Sending it as an upsert made the server evaluate users_insert_self
        // against a half-filled insert row, which it refused — the name stayed on the device.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        pushPatch("users", user.getId(), row);
        return user;
    }

    // ------------------------------------------------------------- vehicles

    public LiveData<Vehicle> observeVehicle(String userId) {
        return db.vehicleDao().getVehicleByUserId(userId);
    }

    public Vehicle getVehicleSync(String userId) {
        return db.vehicleDao().getVehicleByUserIdSync(userId);
    }

    public void saveVehicle(Vehicle vehicle) {
        run(() -> {
            db.vehicleDao().insert(vehicle);
            pushUpsert("vehicles", vehicle);
        });
    }

    // ------------------------------------------------------------- stations

    public LiveData<List<ChargerStation>> observeAllStations() {
        return db.chargerStationDao().getAllStations();
    }

    public LiveData<List<ChargerStation>> observePendingStations() {
        return db.chargerStationDao().getPendingStations();
    }

    public LiveData<List<ChargerStation>> observeStationsForHost(String hostId) {
        return db.chargerStationDao().getStationsByHost(hostId);
    }

    public LiveData<ChargerStation> observeStation(String stationId) {
        return db.chargerStationDao().getStationById(stationId);
    }

    public List<ChargerStation> getAllStationsSync() {
        return db.chargerStationDao().getAllStationsSync();
    }

    public List<ChargerStation> getApprovedStationsSync() {
        return db.chargerStationDao().getApprovedStationsSync();
    }

    public List<ChargerStation> getPendingStationsSync() {
        return db.chargerStationDao().getPendingStationsSync();
    }

    public List<ChargerStation> getStationsForHostSync(String hostId) {
        return db.chargerStationDao().getStationsByHostSync(hostId);
    }

    public ChargerStation getStationSync(String stationId) {
        return db.chargerStationDao().getStationByIdSync(stationId);
    }

    public void saveStation(ChargerStation station) {
        run(() -> {
            db.chargerStationDao().insert(station);
            pushUpsert("charger_stations", station);
        });
    }

    public void updateStation(ChargerStation station) {
        run(() -> {
            db.chargerStationDao().update(station);
            pushUpsert("charger_stations", station);
        });
    }

    /**
     * Local-only station write. Used where the server already made the change through
     * one of the settlement functions and pushing the row as well would apply it twice.
     */
    public void updateStationLocally(ChargerStation station) {
        run(() -> db.chargerStationDao().update(station));
    }

    public void setStationPaused(ChargerStation station, boolean paused) {
        run(() -> {
            station.setPaused(paused);
            station.setLastUpdatedMs(System.currentTimeMillis());
            db.chargerStationDao().update(station);
            pushUpsert("charger_stations", station);
        });
    }

    // ---------------------------------------------------------------- ports

    public LiveData<List<ChargerPort>> observePorts(String stationId) {
        return db.chargerPortDao().getPortsForStation(stationId);
    }

    public List<ChargerPort> getPortsSync(String stationId) {
        return db.chargerPortDao().getPortsForStationSync(stationId);
    }

    public List<ChargerPort> getAllPortsSync() {
        return db.chargerPortDao().getAllPortsSync();
    }

    public void savePort(ChargerPort port) {
        run(() -> {
            db.chargerPortDao().insert(port);
            pushUpsert("charger_ports", port);
        });
    }

    public void savePorts(List<ChargerPort> ports) {
        run(() -> {
            db.chargerPortDao().insertAll(ports);
            pushUpsertAll("charger_ports", ports);
        });
    }

    public void setPortStatus(ChargerPort port, String status) {
        run(() -> {
            port.setStatus(status);
            db.chargerPortDao().update(port);
            pushUpsert("charger_ports", port);
        });
    }

    // ----------------------------------------------------------- schedules

    public List<AvailabilitySchedule> getSchedulesSync(String stationId) {
        return db.availabilityScheduleDao().getSchedulesForStationSync(stationId);
    }

    public List<AvailabilitySchedule> getAllSchedulesSync() {
        return db.availabilityScheduleDao().getAllSchedulesSync();
    }

    public void saveSchedules(List<AvailabilitySchedule> schedules) {
        run(() -> {
            db.availabilityScheduleDao().insertAll(schedules);
            pushUpsertAll("availability_schedules", schedules);
        });
    }

    public void blockStation(String stationId, boolean blocked, String reason) {
        run(() -> {
            db.availabilityScheduleDao().setStationBlocked(stationId, blocked, reason,
                    System.currentTimeMillis());
            // The block is a bulk update, so re-read what changed and queue those rows.
            pushUpsertAll("availability_schedules",
                    db.availabilityScheduleDao().getSchedulesForStationSync(stationId));
        });
    }

    public void blockPort(String portId, String reason) {
        run(() -> {
            db.availabilityScheduleDao().blockPort(portId, reason, System.currentTimeMillis());
            for (int day = 1; day <= 7; day++) {
                AvailabilitySchedule schedule =
                        db.availabilityScheduleDao().getScheduleForPortDaySync(portId, day);
                if (schedule != null) {
                    pushUpsert("availability_schedules", schedule);
                }
            }
        });
    }

    // --------------------------------------------------------- availability

    public List<AvailabilityEngine.Slot> getSlots(String stationId, long dayStartMs, int slotMinutes,
                                                 int maxSlots) {
        ChargerStation station = getStationSync(stationId);
        if (station == null) {
            return Collections.emptyList();
        }
        return AvailabilityEngine.generateSlots(station, getPortsSync(stationId),
                getSchedulesSync(stationId), getReservationsForStationSync(stationId),
                dayStartMs, slotMinutes, maxSlots);
    }

    public AvailabilityEngine.AvailabilitySummary getAvailabilitySummary(String stationId) {
        ChargerStation station = getStationSync(stationId);
        if (station == null) {
            return new AvailabilityEngine.AvailabilitySummary(0, 0, 0, System.currentTimeMillis(),
                    false, "Station not found");
        }
        return AvailabilityEngine.summarise(station, getPortsSync(stationId),
                getSchedulesSync(stationId), getReservationsForStationSync(stationId),
                System.currentTimeMillis());
    }

    // --------------------------------------------------------- reservations

    public LiveData<List<Reservation>> observeReservationsForConsumer(String consumerId) {
        return db.reservationDao().getReservationsForConsumer(consumerId);
    }

    public LiveData<List<Reservation>> observeReservationsForHost(String hostId) {
        return db.reservationDao().getReservationsForHost(hostId);
    }

    public List<Reservation> getReservationsForConsumerSync(String consumerId) {
        return db.reservationDao().getReservationsForConsumerSync(consumerId);
    }

    public List<Reservation> getReservationsForHostSync(String hostId) {
        return db.reservationDao().getReservationsForHostSync(hostId);
    }

    public List<Reservation> getReservationsForStationSync(String stationId) {
        return db.reservationDao().getReservationsForStationSync(stationId);
    }

    public Reservation getActiveReservationSync(String consumerId) {
        return db.reservationDao().getActiveReservationForConsumerSync(consumerId);
    }

    public Reservation getReservationSync(String reservationId) {
        return db.reservationDao().getReservationByIdSync(reservationId);
    }

    public Reservation getReservationByCodeSync(String bookingCode) {
        return db.reservationDao().getReservationByBookingCodeSync(bookingCode);
    }

    public int countSlotConflicts(String portId, long startMs, long endMs) {
        return db.reservationDao().countConflicts(portId, startMs, endMs);
    }

    public void saveReservation(Reservation reservation) {
        run(() -> {
            db.reservationDao().insert(reservation);
            pushUpsert("reservations", reservation);
        });
    }

    public void updateReservation(Reservation reservation) {
        run(() -> {
            db.reservationDao().update(reservation);
            pushUpsert("reservations", reservation);
        });
    }

    // ------------------------------------------------------------- sessions

    public LiveData<List<ChargingSession>> observeSessionsForConsumer(String consumerId) {
        return db.chargingSessionDao().getSessionsForConsumer(consumerId);
    }

    public LiveData<List<ChargingSession>> observeSessionsForHost(String hostId) {
        return db.chargingSessionDao().getSessionsForHost(hostId);
    }

    public List<ChargingSession> getSessionsForConsumerSync(String consumerId) {
        return db.chargingSessionDao().getSessionsForConsumerSync(consumerId);
    }

    public List<ChargingSession> getSessionsForHostSync(String hostId) {
        return db.chargingSessionDao().getSessionsForHostSync(hostId);
    }

    public List<ChargingSession> getAllSessionsSync() {
        return db.chargingSessionDao().getAllSessionsSync();
    }

    public ChargingSession getSessionSync(String sessionId) {
        return db.chargingSessionDao().getSessionByIdSync(sessionId);
    }

    public ChargingSession getSessionByReservationSync(String reservationId) {
        return db.chargingSessionDao().getSessionByReservationSync(reservationId);
    }

    public void saveSession(ChargingSession session) {
        run(() -> {
            db.chargingSessionDao().insert(session);
            pushUpsert("charging_sessions", session);
        });
    }

    public void updateSession(ChargingSession session) {
        run(() -> {
            db.chargingSessionDao().update(session);
            pushUpsert("charging_sessions", session);
        });
    }

    /**
     * Completes a session on the server (finalize_charging_session).
     *
     * <p>The server recomputes the bill from the meter readings and the station's
     * tariff, writes the session, the reading, the receipt and the capture in one
     * transaction, closes the reservation and frees the port. The local rows written
     * here are the offline copy — the same figures, computed by the app's BillingEngine
     * — and are replaced by the server's rows on the next pull.
     */
    public void finalizeSession(ChargingSession session, MeterReading reading, Invoice invoice,
                                Payment payment, Reservation reservation) {
        run(() -> {
            db.chargingSessionDao().insert(session);
            if (reading != null) {
                db.meterReadingDao().insert(reading);
            }
            if (invoice != null) {
                db.invoiceDao().insert(invoice);
            }
            if (payment != null) {
                db.paymentDao().insert(payment);
            }
            if (reservation != null) {
                db.reservationDao().update(reservation);
            }

            Map<String, Object> args = new LinkedHashMap<>();
            args.put("p_session_id", session.getId());
            args.put("p_reservation_id", session.getReservationId());
            args.put("p_start_meter_kwh", session.getStartingMeterKwh());
            args.put("p_end_meter_kwh", session.getEndingMeterKwh());
            args.put("p_is_simulated", session.isMeterSimulated());
            args.put("p_meter_reading_id", reading == null ? null : reading.getId());
            args.put("p_invoice_id", invoice == null ? null : invoice.getId());
            pushRpc("finalize_charging_session", args);
        });
    }

    // ------------------------------------------------------- admin transitions
    // Each of these asks the server to make the change (the function re-checks that
    // the caller is an administrator) and writes the local rows for immediate display.

    /** Approves or rejects a station listing (decide_station_approval). */
    public void decideStationApprovalOnServer(ChargerStation station, boolean approve) {
        run(() -> {
            db.chargerStationDao().update(station);
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("p_station_id", station.getId());
            args.put("p_approve", approve);
            pushRpc("decide_station_approval", args);
        });
    }

    /** Refunds a dispute from the invoice's energy charge (resolve_dispute_refund). */
    public void resolveDisputeRefundOnServer(Dispute dispute, Invoice invoice) {
        run(() -> {
            db.disputeDao().update(dispute);
            if (invoice != null) {
                db.invoiceDao().update(invoice);
            }
            pushRpc("resolve_dispute_refund", singleArg("p_dispute_id", dispute.getId()));
        });
    }

    /** Holds a host's payout while a dispute is reviewed (freeze_dispute_payout). */
    public void freezeDisputePayoutOnServer(Dispute dispute) {
        run(() -> {
            db.disputeDao().update(dispute);
            pushRpc("freeze_dispute_payout", singleArg("p_dispute_id", dispute.getId()));
        });
    }

    /** Records an enforcement action against a host (apply_host_enforcement). */
    public void applyHostEnforcementOnServer(Dispute dispute, String action, double fineAmount,
                                             String note) {
        run(() -> {
            db.disputeDao().update(dispute);
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("p_dispute_id", dispute.getId());
            args.put("p_action", action);
            args.put("p_fine_amount", fineAmount);
            args.put("p_note", note);
            pushRpc("apply_host_enforcement", args);
        });
    }

    // -------------------------------------------------------- meter readings

    /**
     * Stores a meter reading. Only the station's host may write one: a consumer records the
     * reading locally so the trip works, and {@code finalize_charging_session} writes the
     * authoritative row on the server when the session ends.
     */
    public void saveMeterReading(MeterReading reading) {
        run(() -> {
            db.meterReadingDao().insert(reading);
            ChargerStation station = reading.getStationId() == null ? null
                    : db.chargerStationDao().getStationByIdSync(reading.getStationId());
            boolean hostedByCurrentUser = station != null
                    && AuthManager.getInstance().getCurrentUserId().equals(station.getHostId());
            if (hostedByCurrentUser) {
                pushUpsert("meter_readings", reading);
            }
        });
    }

    public MeterReading getLatestMeterReadingSync(String sessionId) {
        return db.meterReadingDao().getLatestForSessionSync(sessionId);
    }

    public List<MeterReading> getMeterReadingsSync(String sessionId) {
        return db.meterReadingDao().getReadingsForSessionSync(sessionId);
    }

    public List<MeterReading> getAllMeterReadingsSync() {
        return db.meterReadingDao().getAllReadingsSync();
    }

    // --------------------------------------------------------------- invoices

    public LiveData<List<Invoice>> observeInvoices(String consumerId) {
        return db.invoiceDao().getInvoicesForConsumer(consumerId);
    }

    /**
     * Stores an invoice locally. The server issues the real receipt from
     * finalize_charging_session() and adjusts it from resolve_dispute_refund(), so
     * these are offline views of the server's row and are not pushed.
     */
    public void saveInvoice(Invoice invoice) {
        run(() -> db.invoiceDao().insert(invoice));
    }

    public void updateInvoice(Invoice invoice) {
        run(() -> db.invoiceDao().update(invoice));
    }

    public Invoice getInvoiceSync(String invoiceId) {
        return db.invoiceDao().getInvoiceByIdSync(invoiceId);
    }

    public Invoice getInvoiceBySessionSync(String sessionId) {
        return db.invoiceDao().getInvoiceBySessionSync(sessionId);
    }

    public List<Invoice> getInvoicesForHostSync(String hostId) {
        return db.invoiceDao().getInvoicesForHostSync(hostId);
    }

    public List<Invoice> getInvoicesForConsumerSync(String consumerId) {
        return db.invoiceDao().getInvoicesForConsumerSync(consumerId);
    }

    public List<Invoice> getAllInvoicesSync() {
        return db.invoiceDao().getAllInvoicesSync();
    }

    // --------------------------------------------------------------- payments

    /**
     * Stores a payment locally, for the UI and for offline reads.
     *
     * <p>The payments table is no longer written by a client: the row is created by
     * {@link #authorizePayment} and its money fields are set by the settlement
     * functions, so pushing this row would be refused by Row Level Security anyway.
     */
    public void savePayment(Payment payment) {
        run(() -> db.paymentDao().insert(payment));
    }

    /** Creates the authorization on the server (authorize_payment). */
    public void authorizePayment(Payment payment, String stationId) {
        run(() -> {
            db.paymentDao().insert(payment);
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("p_id", payment.getId());
            args.put("p_reservation_id", payment.getReservationId());
            args.put("p_station_id", stationId);
            args.put("p_amount", payment.getAuthorizedAmount());
            args.put("p_idempotency_key", payment.getIdempotencyKey());
            pushRpc("authorize_payment", args);
        });
    }

    /**
     * Cancels on the server (cancel_reservation), which is where the published
     * late-cancellation policy and the release of the authorization are applied. The
     * local reservation and payment rows are still updated by the caller for the UI.
     */
    public void cancelReservationOnServer(String reservationId) {
        run(() -> pushRpc("cancel_reservation",
                singleArg("p_reservation_id", reservationId)));
    }

    public Payment findPaymentByKey(String idempotencyKey) {
        return db.paymentDao().findByIdempotencyKey(idempotencyKey);
    }

    public Payment getPaymentForReservationSync(String reservationId) {
        return db.paymentDao().getPaymentForReservationSync(reservationId);
    }

    public Payment getPaymentForSessionSync(String sessionId) {
        return db.paymentDao().getPaymentForSessionSync(sessionId);
    }

    public List<Payment> getPaymentsForHostSync(String hostId) {
        return db.paymentDao().getPaymentsForHostSync(hostId);
    }

    // -------------------------------------------------------------- disputes

    public LiveData<List<Dispute>> observeDisputes() {
        return db.disputeDao().getAllDisputes();
    }

    public List<Dispute> getDisputesSync() {
        return db.disputeDao().getAllDisputesSync();
    }

    public List<Dispute> getOpenDisputesSync() {
        return db.disputeDao().getOpenDisputesSync();
    }

    public List<Dispute> getDisputesForHostSync(String hostId) {
        return db.disputeDao().getDisputesForHostSync(hostId);
    }

    public Dispute getDisputeSync(String disputeId) {
        return db.disputeDao().getDisputeByIdSync(disputeId);
    }

    public void saveDispute(Dispute dispute) {
        run(() -> {
            db.disputeDao().insert(dispute);
            pushUpsert("disputes", dispute);
        });
    }

    public void updateDispute(Dispute dispute) {
        run(() -> {
            db.disputeDao().update(dispute);
            pushUpsert("disputes", dispute);
        });
    }

    public int countProvenIncidents(String hostId) {
        return db.disputeDao().countProvenIncidentsForHost(hostId);
    }

    // --------------------------------------------------------------- reports

    public LiveData<List<Report>> observeReports() {
        return db.reportDao().getAllReports();
    }

    public List<Report> getAllReportsSync() {
        return db.reportDao().getAllReportsSync();
    }

    public List<Report> getOpenReportsSync() {
        return db.reportDao().getOpenReportsSync();
    }

    public List<Report> getReportsForStationSync(String stationId) {
        return db.reportDao().getReportsForStationSync(stationId);
    }

    public void saveReport(Report report) {
        run(() -> {
            db.reportDao().insert(report);
            pushUpsert("reports", report);
        });
    }

    // ----------------------------------------------------------- enforcement

    public void saveEnforcementAction(HostEnforcementAction action) {
        run(() -> {
            db.hostEnforcementActionDao().insert(action);
            pushUpsert("host_enforcements", action);
        });
    }

    public List<HostEnforcementAction> getEnforcementForHostSync(String hostId) {
        return db.hostEnforcementActionDao().getActionsForHostSync(hostId);
    }

    public List<HostEnforcementAction> getEnforcementForStationSync(String stationId) {
        return db.hostEnforcementActionDao().getActionsForStationSync(stationId);
    }

    public List<HostEnforcementAction> getAllEnforcementSync() {
        return db.hostEnforcementActionDao().getAllActionsSync();
    }

    // --------------------------------------------------------- notifications

    public LiveData<List<Notification>> observeNotifications(String userId) {
        return db.notificationDao().getNotificationsForUser(userId);
    }

    public List<Notification> getNotificationsSync(String userId) {
        return db.notificationDao().getNotificationsForUserSync(userId);
    }

    public void saveNotification(Notification notification) {
        run(() -> {
            db.notificationDao().insert(notification);
            pushUpsert("notifications", notification);
        });
    }

    public void markNotificationsRead(String userId) {
        run(() -> {
            db.notificationDao().markAllRead(userId);
            pushUpsertAll("notifications", db.notificationDao().getNotificationsForUserSync(userId));
        });
    }

    // ------------------------------------------------------------------ chat

    public LiveData<List<CommunicationThread>> observeThreadsForConsumer(String consumerId) {
        return db.communicationThreadDao().getThreadsForConsumer(consumerId);
    }

    public LiveData<List<CommunicationThread>> observeThreadsForHost(String hostId) {
        return db.communicationThreadDao().getThreadsForHost(hostId);
    }

    public CommunicationThread getThreadByReservationSync(String reservationId) {
        return db.communicationThreadDao().getThreadByReservationSync(reservationId);
    }

    public List<CommunicationThread> getAllThreadsSync() {
        return db.communicationThreadDao().getAllThreadsSync();
    }

    public void saveThread(CommunicationThread thread) {
        run(() -> {
            db.communicationThreadDao().insert(thread);
            pushUpsert("communication_threads", thread);
        });
    }

    public void updateThread(CommunicationThread thread) {
        run(() -> {
            db.communicationThreadDao().update(thread);
            pushUpsert("communication_threads", thread);
        });
    }

    public void reportThread(String threadId) {
        run(() -> {
            db.communicationThreadDao().markReported(threadId);
            CommunicationThread thread = db.communicationThreadDao().getThreadByIdSync(threadId);
            if (thread != null) {
                pushUpsert("communication_threads", thread);
            }
        });
    }

    public void blockThread(String threadId) {
        run(() -> {
            db.communicationThreadDao().block(threadId);
            CommunicationThread thread = db.communicationThreadDao().getThreadByIdSync(threadId);
            if (thread != null) {
                pushUpsert("communication_threads", thread);
            }
        });
    }

    public LiveData<List<ChatMessage>> observeMessages(String threadId) {
        return db.chatMessageDao().getMessagesForThread(threadId);
    }

    public List<ChatMessage> getMessagesSync(String threadId) {
        return db.chatMessageDao().getMessagesForThreadSync(threadId);
    }

    public List<ChatMessage> getAllMessagesSync() {
        return db.chatMessageDao().getAllMessagesSync();
    }

    public void saveMessage(ChatMessage message) {
        run(() -> {
            db.chatMessageDao().insert(message);
            pushUpsert("chat_messages", message);
        });
    }

    public void reportMessage(String messageId) {
        run(() -> {
            db.chatMessageDao().markReported(messageId);
            ChatMessage message = db.chatMessageDao().getMessageByIdSync(messageId);
            if (message != null) {
                pushUpsert("chat_messages", message);
            }
        });
    }

    public void purgeExpiredMessages() {
        run(() -> db.chatMessageDao().purgeExpired(System.currentTimeMillis()));
    }

    // ---------------------------------------------------------------- safety

    public LiveData<List<SafetyEvent>> observeSafetyEvents(String consumerId) {
        return db.safetyEventDao().getEventsForConsumer(consumerId);
    }

    public List<SafetyEvent> getSafetyEventsSync(String consumerId) {
        return db.safetyEventDao().getEventsForConsumerSync(consumerId);
    }

    public SafetyEvent getActiveLocationSharingSync(String consumerId) {
        return db.safetyEventDao().getActiveSharingSync(consumerId, System.currentTimeMillis());
    }

    public void saveSafetyEvent(SafetyEvent event) {
        run(() -> {
            db.safetyEventDao().insert(event);
            pushUpsert("safety_events", event);
        });
    }

    public void updateSafetyEvent(SafetyEvent event) {
        run(() -> {
            db.safetyEventDao().update(event);
            pushUpsert("safety_events", event);
        });
    }

    public void expireStaleLocationSharing() {
        run(() -> db.safetyEventDao().expireStaleSharing(System.currentTimeMillis()));
    }

    public LiveData<List<TrustedContact>> observeTrustedContacts(String userId) {
        return db.trustedContactDao().getContactsForUser(userId);
    }

    public List<TrustedContact> getTrustedContactsSync(String userId) {
        return db.trustedContactDao().getContactsForUserSync(userId);
    }

    public void saveTrustedContact(TrustedContact contact) {
        run(() -> {
            db.trustedContactDao().insert(contact);
            pushUpsert("trusted_contacts", contact);
        });
    }

    public void deleteTrustedContact(TrustedContact contact) {
        run(() -> {
            db.trustedContactDao().delete(contact);
            pushDelete("trusted_contacts", contact.getId());
        });
    }

    // ---------------------------------------------------------- route plans

    public LiveData<List<RoutePlan>> observeRoutePlans(String consumerId) {
        return db.routePlanDao().getPlansForConsumer(consumerId);
    }

    public RoutePlan getRoutePlanSync(String planId) {
        return db.routePlanDao().getPlanByIdSync(planId);
    }

    public RoutePlan getActiveRoutePlanSync(String consumerId) {
        return db.routePlanDao().getActivePlanSync(consumerId);
    }

    public List<RoutePlan> getRoutePlansSync(String consumerId) {
        return db.routePlanDao().getPlansForConsumerSync(consumerId);
    }

    public void saveRoutePlan(RoutePlan plan) {
        run(() -> {
            db.routePlanDao().insert(plan);
            pushUpsert("route_plans", plan);
        });
    }

    public void updateRoutePlan(RoutePlan plan) {
        run(() -> {
            db.routePlanDao().update(plan);
            pushUpsert("route_plans", plan);
        });
    }

    public List<RouteStop> getRouteStopsSync(String planId) {
        return db.routeStopDao().getStopsForPlanSync(planId);
    }

    public List<RouteStop> getBackupStopsSync(String planId) {
        return db.routeStopDao().getBackupStopsSync(planId);
    }

    public void saveRouteStops(List<RouteStop> stops) {
        run(() -> {
            db.routeStopDao().insertAll(stops);
            pushUpsertAll("route_stops", stops);
        });
    }

    /**
     * Replaces a plan's stops locally and on the server.
     *
     * <p>The server has to see the same set: the stops that are gone are deleted there, and the
     * new ones are inserted. Without this the plan's stops only ever existed on the device, so a
     * plan pulled on another phone came back without its route.
     */
    public void replaceRouteStops(String planId, List<RouteStop> stops) {
        run(() -> {
            List<RouteStop> previous = db.routeStopDao().getStopsForPlanSync(planId);
            db.routeStopDao().deleteStopsForPlan(planId);
            if (stops != null && !stops.isEmpty()) {
                db.routeStopDao().insertAll(stops);
            }
            for (RouteStop old : previous) {
                if (!containsStop(stops, old.getId())) {
                    pushDelete("route_stops", old.getId());
                }
            }
            pushUpsertAll("route_stops", stops);
        });
    }

    private static boolean containsStop(@Nullable List<RouteStop> stops, @Nullable String id) {
        if (stops == null || id == null) {
            return false;
        }
        for (RouteStop stop : stops) {
            if (id.equals(stop.getId())) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------- trip estimates

    public void saveTripEstimate(TripEstimate estimate) {
        run(() -> {
            db.tripEstimateDao().insert(estimate);
            pushUpsert("trip_estimates", estimate);
        });
    }

    public List<TripEstimate> getTripEstimatesSync(String vehicleId) {
        return db.tripEstimateDao().getEstimatesForVehicleSync(vehicleId);
    }

    public List<TripEstimate> getTripEstimatesForPlanSync(String planId) {
        return db.tripEstimateDao().getEstimatesForPlanSync(planId);
    }

    public List<TripEstimate> getAllTripEstimatesSync() {
        return db.tripEstimateDao().getAllEstimatesSync();
    }

    public double averageDriftKwh(String vehicleId) {
        return db.tripEstimateDao().averageDriftForVehicle(vehicleId);
    }

    // -------------------------------------------------------- battery updates

    /**
     * Stores a battery correction. The server only accepts one for a vehicle the caller owns,
     * so a correction recorded against no vehicle (or someone else's) stays local instead of
     * being sent as a write the policy will refuse.
     */
    public void saveBatteryUpdate(BatteryUpdate update) {
        run(() -> {
            db.batteryUpdateDao().insert(update);
            Vehicle vehicle = db.vehicleDao()
                    .getVehicleByUserIdSync(AuthManager.getInstance().getCurrentUserId());
            if (vehicle != null && vehicle.getId().equals(update.getVehicleId())) {
                pushUpsert("battery_updates", update);
            }
        });
    }

    public List<BatteryUpdate> getBatteryUpdatesSync(String planId) {
        return db.batteryUpdateDao().getUpdatesForTripSync(planId);
    }

    public BatteryUpdate getLatestBatteryUpdateSync(String planId) {
        return db.batteryUpdateDao().getLatestForTripSync(planId);
    }

    public List<BatteryUpdate> getAllBatteryUpdatesSync() {
        return db.batteryUpdateDao().getAllUpdatesSync();
    }

    // --------------------------------------------------- android auto session

    public void saveAutoSession(AndroidAutoSession session) {
        run(() -> {
            db.androidAutoSessionDao().insert(session);
            pushUpsert("android_auto_sessions", session);
        });
    }

    public AndroidAutoSession getLatestAutoSessionSync(String userId) {
        return db.androidAutoSessionDao().getLatestForUserSync(userId);
    }

    public List<AndroidAutoSession> getAutoSessionsSync(String userId) {
        return db.androidAutoSessionDao().getSessionsForUserSync(userId);
    }

    // ------------------------------------------------------------- analytics

    public HostAnalyticsEngine.HostAnalytics hostAnalytics(String hostId) {
        List<ChargerStation> stations = getStationsForHostSync(hostId);
        List<ChargerPort> ports = new ArrayList<>();
        for (ChargerStation station : stations) {
            ports.addAll(getPortsSync(station.getId()));
        }
        List<Dispute> disputes = getDisputesForHostSync(hostId);
        return HostAnalyticsEngine.compute(stations, ports, getSessionsForHostSync(hostId),
                getReservationsForHostSync(hostId), getInvoicesForHostSync(hostId), disputes);
    }

    public HostAnalyticsEngine.HostAnalytics platformAnalytics() {
        List<ChargerStation> stations = getAllStationsSync();
        List<ChargerPort> ports = getAllPortsSync();
        return HostAnalyticsEngine.compute(stations, ports, getAllSessionsSync(),
                new ArrayList<>(), getAllInvoicesSync(), getDisputesSync());
    }

    public int stationCount() {
        return db.chargerStationDao().countStations();
    }

    public int userCount() {
        return db.userDao().countUsers();
    }
}
