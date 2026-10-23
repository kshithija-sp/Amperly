package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.Reservation;

import java.util.List;

@Dao
public interface ReservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Reservation reservation);

    @Update
    void update(Reservation reservation);

    @Query("SELECT * FROM reservations WHERE consumerId = :consumerId ORDER BY startTimeMs DESC")
    LiveData<List<Reservation>> getReservationsForConsumer(String consumerId);

    @Query("SELECT * FROM reservations WHERE consumerId = :consumerId ORDER BY startTimeMs DESC")
    List<Reservation> getReservationsForConsumerSync(String consumerId);

    @Query("SELECT * FROM reservations WHERE stationId = :stationId ORDER BY startTimeMs DESC")
    LiveData<List<Reservation>> getReservationsForStation(String stationId);

    @Query("SELECT * FROM reservations WHERE stationId IN (SELECT id FROM charger_stations WHERE hostId = :hostId) ORDER BY startTimeMs DESC")
    LiveData<List<Reservation>> getReservationsForHost(String hostId);

    @Query("SELECT * FROM reservations WHERE stationId IN (SELECT id FROM charger_stations WHERE hostId = :hostId) ORDER BY startTimeMs DESC")
    List<Reservation> getReservationsForHostSync(String hostId);

    @Query("SELECT * FROM reservations WHERE stationId = :stationId ORDER BY startTimeMs DESC")
    List<Reservation> getReservationsForStationSync(String stationId);

    @Query("SELECT * FROM reservations WHERE id = :id LIMIT 1")
    Reservation getReservationByIdSync(String id);

    @Query("SELECT * FROM reservations WHERE bookingCode = :bookingCode LIMIT 1")
    Reservation getReservationByBookingCodeSync(String bookingCode);

    @Query("SELECT * FROM reservations WHERE consumerId = :consumerId AND status IN ('RESERVED','CHECKED_IN','CHARGING') ORDER BY startTimeMs LIMIT 1")
    Reservation getActiveReservationForConsumerSync(String consumerId);

    /** Slot conflict check: any live reservation on this port overlapping the requested window. */
    @Query("SELECT * FROM reservations WHERE portId = :portId AND status IN ('RESERVED','CHECKED_IN','CHARGING') AND startTimeMs < :endMs AND endTimeMs > :startMs")
    List<Reservation> findConflictingReservations(String portId, long startMs, long endMs);

    @Query("SELECT COUNT(*) FROM reservations WHERE portId = :portId AND status IN ('RESERVED','CHECKED_IN','CHARGING') AND startTimeMs < :endMs AND endTimeMs > :startMs")
    int countConflicts(String portId, long startMs, long endMs);

    @Query("SELECT * FROM reservations WHERE stationId = :stationId AND status = 'RESERVED'")
    List<Reservation> getPendingArrivalsSync(String stationId);
}
