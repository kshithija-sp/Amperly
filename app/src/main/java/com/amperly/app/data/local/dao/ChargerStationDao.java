package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.ChargerStation;

import java.util.List;

@Dao
public interface ChargerStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ChargerStation station);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChargerStation> stations);

    @Update
    void update(ChargerStation station);

    @Delete
    void delete(ChargerStation station);

    @Query("SELECT * FROM charger_stations ORDER BY name")
    LiveData<List<ChargerStation>> getAllStations();

    @Query("SELECT * FROM charger_stations ORDER BY name")
    List<ChargerStation> getAllStationsSync();

    @Query("SELECT * FROM charger_stations WHERE approvalStatus = 'APPROVED' ORDER BY name")
    List<ChargerStation> getApprovedStationsSync();

    @Query("SELECT * FROM charger_stations WHERE approvalStatus = 'PENDING' ORDER BY name")
    LiveData<List<ChargerStation>> getPendingStations();

    @Query("SELECT * FROM charger_stations WHERE approvalStatus = 'PENDING' ORDER BY name")
    List<ChargerStation> getPendingStationsSync();

    @Query("SELECT * FROM charger_stations WHERE id = :id LIMIT 1")
    LiveData<ChargerStation> getStationById(String id);

    @Query("SELECT * FROM charger_stations WHERE id = :id LIMIT 1")
    ChargerStation getStationByIdSync(String id);

    @Query("SELECT * FROM charger_stations WHERE hostId = :hostId ORDER BY name")
    LiveData<List<ChargerStation>> getStationsByHost(String hostId);

    @Query("SELECT * FROM charger_stations WHERE hostId = :hostId ORDER BY name")
    List<ChargerStation> getStationsByHostSync(String hostId);

    @Query("SELECT COUNT(*) FROM charger_stations")
    int countStations();
}
