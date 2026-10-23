package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.ChargerPort;

import java.util.List;

@Dao
public interface ChargerPortDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ChargerPort port);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChargerPort> ports);

    @Update
    void update(ChargerPort port);

    @Delete
    void delete(ChargerPort port);

    @Query("SELECT * FROM charger_ports WHERE stationId = :stationId ORDER BY portNumber")
    LiveData<List<ChargerPort>> getPortsForStation(String stationId);

    @Query("SELECT * FROM charger_ports WHERE stationId = :stationId ORDER BY portNumber")
    List<ChargerPort> getPortsForStationSync(String stationId);

    @Query("SELECT * FROM charger_ports WHERE id = :id LIMIT 1")
    ChargerPort getPortByIdSync(String id);

    @Query("SELECT * FROM charger_ports")
    List<ChargerPort> getAllPortsSync();

    @Query("SELECT COUNT(*) FROM charger_ports WHERE stationId = :stationId AND status = 'AVAILABLE'")
    int countAvailablePorts(String stationId);
}
