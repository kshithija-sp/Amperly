package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.ChargingSession;

import java.util.List;

@Dao
public interface ChargingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ChargingSession session);

    @Update
    void update(ChargingSession session);

    @Query("SELECT * FROM charging_sessions WHERE consumerId = :consumerId ORDER BY startTimeMs DESC")
    LiveData<List<ChargingSession>> getSessionsForConsumer(String consumerId);

    @Query("SELECT * FROM charging_sessions WHERE consumerId = :consumerId ORDER BY startTimeMs DESC")
    List<ChargingSession> getSessionsForConsumerSync(String consumerId);

    @Query("SELECT * FROM charging_sessions WHERE hostId = :hostId ORDER BY startTimeMs DESC")
    LiveData<List<ChargingSession>> getSessionsForHost(String hostId);

    @Query("SELECT * FROM charging_sessions WHERE hostId = :hostId ORDER BY startTimeMs DESC")
    List<ChargingSession> getSessionsForHostSync(String hostId);

    @Query("SELECT * FROM charging_sessions WHERE id = :id LIMIT 1")
    ChargingSession getSessionByIdSync(String id);

    @Query("SELECT * FROM charging_sessions WHERE reservationId = :reservationId ORDER BY startTimeMs DESC LIMIT 1")
    ChargingSession getSessionByReservationSync(String reservationId);

    @Query("SELECT * FROM charging_sessions WHERE stationId = :stationId ORDER BY startTimeMs DESC")
    List<ChargingSession> getSessionsForStationSync(String stationId);

    @Query("SELECT * FROM charging_sessions WHERE status IN ('CHECKED_IN','CHARGING')")
    List<ChargingSession> getActiveSessionsSync();

    @Query("SELECT * FROM charging_sessions")
    List<ChargingSession> getAllSessionsSync();
}
