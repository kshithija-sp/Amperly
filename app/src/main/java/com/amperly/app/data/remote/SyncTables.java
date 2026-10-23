package com.amperly.app.data.remote;

import androidx.annotation.Nullable;

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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The register of everything that syncs, in one place.
 *
 * <p>Each entry binds a Postgres table name to the entity class Gson should build and to
 * the Room DAO call that stores a pulled row. Every DAO insert already uses
 * {@code OnConflictStrategy.REPLACE}, so a pull is an upsert with no extra work.
 *
 * <p>Adding a synced table means adding one line here plus its counterpart in
 * {@code supabase/schema.sql}; there is no per-table sync code to write.
 */
public final class SyncTables {

    /** Writes a pulled row into Room. */
    private interface Writer {
        void write(AmperlyDatabase db, Object entity);
    }

    public static final class Table {
        public final String name;
        public final Type type;
        private final Writer writer;

        Table(String name, Type type, Writer writer) {
            this.name = name;
            this.type = type;
            this.writer = writer;
        }

        void write(AmperlyDatabase db, Object entity) {
            writer.write(db, entity);
        }
    }

    /**
     * Ordered parents-first where it matters for readability, though Room has no foreign
     * keys and the server is authoritative, so strict ordering is not required.
     */
    public static final List<Table> ALL = Collections.unmodifiableList(Arrays.asList(
            table("users", User.class, (db, e) -> db.userDao().insert((User) e)),
            table("vehicles", Vehicle.class, (db, e) -> db.vehicleDao().insert((Vehicle) e)),
            table("charger_stations", ChargerStation.class,
                    (db, e) -> db.chargerStationDao().insert((ChargerStation) e)),
            table("charger_ports", ChargerPort.class,
                    (db, e) -> db.chargerPortDao().insert((ChargerPort) e)),
            table("availability_schedules", AvailabilitySchedule.class,
                    (db, e) -> db.availabilityScheduleDao().insert((AvailabilitySchedule) e)),
            table("reservations", Reservation.class,
                    (db, e) -> db.reservationDao().insert((Reservation) e)),
            table("charging_sessions", ChargingSession.class,
                    (db, e) -> db.chargingSessionDao().insert((ChargingSession) e)),
            table("meter_readings", MeterReading.class,
                    (db, e) -> db.meterReadingDao().insert((MeterReading) e)),
            table("invoices", Invoice.class, (db, e) -> db.invoiceDao().insert((Invoice) e)),
            table("payments", Payment.class, (db, e) -> db.paymentDao().insert((Payment) e)),
            table("route_plans", RoutePlan.class,
                    (db, e) -> db.routePlanDao().insert((RoutePlan) e)),
            table("route_stops", RouteStop.class,
                    (db, e) -> db.routeStopDao().insert((RouteStop) e)),
            table("trip_estimates", TripEstimate.class,
                    (db, e) -> db.tripEstimateDao().insert((TripEstimate) e)),
            table("battery_updates", BatteryUpdate.class,
                    (db, e) -> db.batteryUpdateDao().insert((BatteryUpdate) e)),
            table("communication_threads", CommunicationThread.class,
                    (db, e) -> db.communicationThreadDao().insert((CommunicationThread) e)),
            table("chat_messages", ChatMessage.class,
                    (db, e) -> db.chatMessageDao().insert((ChatMessage) e)),
            table("safety_events", SafetyEvent.class,
                    (db, e) -> db.safetyEventDao().insert((SafetyEvent) e)),
            table("trusted_contacts", TrustedContact.class,
                    (db, e) -> db.trustedContactDao().insert((TrustedContact) e)),
            table("reports", Report.class, (db, e) -> db.reportDao().insert((Report) e)),
            table("disputes", Dispute.class, (db, e) -> db.disputeDao().insert((Dispute) e)),
            table("host_enforcements", HostEnforcementAction.class,
                    (db, e) -> db.hostEnforcementActionDao().insert((HostEnforcementAction) e)),
            table("notifications", Notification.class,
                    (db, e) -> db.notificationDao().insert((Notification) e)),
            table("android_auto_sessions", AndroidAutoSession.class,
                    (db, e) -> db.androidAutoSessionDao().insert((AndroidAutoSession) e))
    ));

    private SyncTables() {
    }

    private static Table table(String name, Type type, Writer writer) {
        return new Table(name, type, writer);
    }

    @Nullable
    public static Table byName(String name) {
        for (Table table : ALL) {
            if (table.name.equals(name)) {
                return table;
            }
        }
        return null;
    }

    /** Narrows a built-out entity to the compile-time type its writer expects. */
    @SuppressWarnings("unchecked")
    static <T> void write(AmperlyDatabase db, Table table, Object entity) {
        table.write(db, entity);
    }

    /** Guards against a registry entry whose table has no counterpart in schema.sql. */
    public static List<String> tableNames() {
        List<String> names = new ArrayList<>();
        for (Table table : ALL) {
            names.add(table.name);
        }
        return names;
    }
}
