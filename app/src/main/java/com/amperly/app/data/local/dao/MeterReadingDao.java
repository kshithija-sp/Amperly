package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.MeterReading;

import java.util.List;

@Dao
public interface MeterReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MeterReading reading);

    @Update
    void update(MeterReading reading);

    @Query("SELECT * FROM meter_readings WHERE sessionId = :sessionId ORDER BY timestampMs DESC LIMIT 1")
    MeterReading getLatestForSessionSync(String sessionId);

    @Query("SELECT * FROM meter_readings WHERE sessionId = :sessionId ORDER BY timestampMs DESC")
    LiveData<List<MeterReading>> getReadingsForSession(String sessionId);

    @Query("SELECT * FROM meter_readings WHERE stationId = :stationId ORDER BY timestampMs DESC")
    List<MeterReading> getReadingsForStationSync(String stationId);

    @Query("SELECT * FROM meter_readings WHERE sessionId = :sessionId ORDER BY timestampMs DESC")
    List<MeterReading> getReadingsForSessionSync(String sessionId);

    @Query("SELECT * FROM meter_readings ORDER BY timestampMs DESC")
    List<MeterReading> getAllReadingsSync();

    @Query("SELECT COUNT(*) FROM meter_readings WHERE sessionId = :sessionId")
    int countForSession(String sessionId);
}
