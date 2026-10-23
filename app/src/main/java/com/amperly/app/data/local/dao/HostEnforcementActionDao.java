package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.amperly.app.data.local.entity.HostEnforcementAction;

import java.util.List;

@Dao
public interface HostEnforcementActionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HostEnforcementAction action);

    @Query("SELECT * FROM host_enforcements ORDER BY createdAtMs DESC")
    LiveData<List<HostEnforcementAction>> getAllActions();

    @Query("SELECT * FROM host_enforcements ORDER BY createdAtMs DESC")
    List<HostEnforcementAction> getAllActionsSync();

    @Query("SELECT * FROM host_enforcements WHERE hostId = :hostId ORDER BY createdAtMs DESC")
    List<HostEnforcementAction> getActionsForHostSync(String hostId);

    @Query("SELECT * FROM host_enforcements WHERE stationId = :stationId ORDER BY createdAtMs DESC")
    List<HostEnforcementAction> getActionsForStationSync(String stationId);

    @Query("SELECT COUNT(*) FROM host_enforcements WHERE hostId = :hostId AND actionType IN ('WARNING','FINE')")
    int countWarningsAndFines(String hostId);

    @Query("SELECT COUNT(*) FROM host_enforcements WHERE stationId = :stationId AND actionType = 'BAN'")
    int countBans(String stationId);
}
