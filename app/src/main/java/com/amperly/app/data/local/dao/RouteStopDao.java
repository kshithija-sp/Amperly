package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.RouteStop;

import java.util.List;

@Dao
public interface RouteStopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RouteStop stop);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RouteStop> stops);

    @Update
    void update(RouteStop stop);

    @Query("SELECT * FROM route_stops WHERE planId = :planId ORDER BY sequence")
    LiveData<List<RouteStop>> getStopsForPlan(String planId);

    @Query("SELECT * FROM route_stops WHERE planId = :planId ORDER BY sequence")
    List<RouteStop> getStopsForPlanSync(String planId);

    @Query("SELECT * FROM route_stops WHERE planId = :planId AND isBackup = 0 ORDER BY sequence")
    List<RouteStop> getPrimaryStopsSync(String planId);

    @Query("SELECT * FROM route_stops WHERE planId = :planId AND isBackup = 1 ORDER BY sequence")
    List<RouteStop> getBackupStopsSync(String planId);

    @Query("DELETE FROM route_stops WHERE planId = :planId")
    void deleteStopsForPlan(String planId);

    @Query("DELETE FROM route_stops WHERE planId = :planId AND isBackup = 1")
    void deleteBackupsForPlan(String planId);
}
