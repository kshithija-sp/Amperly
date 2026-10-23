package com.amperly.app.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.amperly.app.data.local.dao.AndroidAutoSessionDao;
import com.amperly.app.data.local.dao.AvailabilityScheduleDao;
import com.amperly.app.data.local.dao.BatteryUpdateDao;
import com.amperly.app.data.local.dao.ChargerPortDao;
import com.amperly.app.data.local.dao.ChargerStationDao;
import com.amperly.app.data.local.dao.ChargingSessionDao;
import com.amperly.app.data.local.dao.ChatMessageDao;
import com.amperly.app.data.local.dao.CommunicationThreadDao;
import com.amperly.app.data.local.dao.DisputeDao;
import com.amperly.app.data.local.dao.HostEnforcementActionDao;
import com.amperly.app.data.local.dao.InvoiceDao;
import com.amperly.app.data.local.dao.MeterReadingDao;
import com.amperly.app.data.local.dao.NotificationDao;
import com.amperly.app.data.local.dao.PaymentDao;
import com.amperly.app.data.local.dao.ReportDao;
import com.amperly.app.data.local.dao.ReservationDao;
import com.amperly.app.data.local.dao.RoutePlanDao;
import com.amperly.app.data.local.dao.RouteStopDao;
import com.amperly.app.data.local.dao.SafetyEventDao;
import com.amperly.app.data.local.dao.TripEstimateDao;
import com.amperly.app.data.local.dao.TrustedContactDao;
import com.amperly.app.data.local.dao.UserDao;
import com.amperly.app.data.local.dao.VehicleDao;
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

@Database(entities = {
        User.class, Vehicle.class, ChargerStation.class, ChargerPort.class,
        AvailabilitySchedule.class, Reservation.class, ChargingSession.class, MeterReading.class,
        Invoice.class, Payment.class, Dispute.class, Report.class, SafetyEvent.class,
        TrustedContact.class, BatteryUpdate.class, HostEnforcementAction.class,
        Notification.class, ChatMessage.class, CommunicationThread.class, RoutePlan.class,
        RouteStop.class, TripEstimate.class, AndroidAutoSession.class
}, version = 4, exportSchema = false)
public abstract class AmperlyDatabase extends RoomDatabase {

    private static volatile AmperlyDatabase INSTANCE;

    public abstract UserDao userDao();
    public abstract VehicleDao vehicleDao();
    public abstract ChargerStationDao chargerStationDao();
    public abstract ChargerPortDao chargerPortDao();
    public abstract AvailabilityScheduleDao availabilityScheduleDao();
    public abstract ReservationDao reservationDao();
    public abstract ChargingSessionDao chargingSessionDao();
    public abstract MeterReadingDao meterReadingDao();
    public abstract InvoiceDao invoiceDao();
    public abstract PaymentDao paymentDao();
    public abstract DisputeDao disputeDao();
    public abstract ReportDao reportDao();
    public abstract SafetyEventDao safetyEventDao();
    public abstract TrustedContactDao trustedContactDao();
    public abstract BatteryUpdateDao batteryUpdateDao();
    public abstract HostEnforcementActionDao hostEnforcementActionDao();
    public abstract NotificationDao notificationDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract CommunicationThreadDao communicationThreadDao();
    public abstract RoutePlanDao routePlanDao();
    public abstract RouteStopDao routeStopDao();
    public abstract TripEstimateDao tripEstimateDao();
    public abstract AndroidAutoSessionDao androidAutoSessionDao();

    public static AmperlyDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AmperlyDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AmperlyDatabase.class, "amperly_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
