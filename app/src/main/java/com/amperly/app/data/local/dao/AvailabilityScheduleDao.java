package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.AvailabilitySchedule;

import java.util.List;

@Dao
public interface AvailabilityScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AvailabilitySchedule schedule);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AvailabilitySchedule> schedules);

    @Update
    void update(AvailabilitySchedule schedule);

    @Query("SELECT * FROM availability_schedules WHERE stationId = :stationId")
    LiveData<List<AvailabilitySchedule>> getSchedulesForStation(String stationId);

    @Query("SELECT * FROM availability_schedules WHERE stationId = :stationId")
    List<AvailabilitySchedule> getSchedulesForStationSync(String stationId);

    @Query("SELECT * FROM availability_schedules WHERE portId = :portId AND dayOfWeek = :dayOfWeek LIMIT 1")
    AvailabilitySchedule getScheduleForPortDaySync(String portId, int dayOfWeek);

    @Query("SELECT * FROM availability_schedules")
    List<AvailabilitySchedule> getAllSchedulesSync();

    @Query("DELETE FROM availability_schedules WHERE portId = :portId")
    void deleteForPort(String portId);

    @Query("UPDATE availability_schedules SET isBlocked = :blocked, blockReason = :reason, updatedAtMs = :nowMs WHERE stationId = :stationId")
    void setStationBlocked(String stationId, boolean blocked, String reason, long nowMs);

    @Query("UPDATE availability_schedules SET isBlocked = 1, blockReason = :reason, updatedAtMs = :nowMs WHERE portId = :portId")
    void blockPort(String portId, String reason, long nowMs);
}
