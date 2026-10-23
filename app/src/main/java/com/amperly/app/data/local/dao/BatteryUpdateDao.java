package com.amperly.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.amperly.app.data.local.entity.BatteryUpdate;

import java.util.List;

@Dao
public interface BatteryUpdateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BatteryUpdate update);

    @Query("SELECT * FROM battery_updates WHERE tripPlanId = :planId ORDER BY timestampMs DESC")
    List<BatteryUpdate> getUpdatesForTripSync(String planId);

    @Query("SELECT * FROM battery_updates WHERE tripPlanId = :planId ORDER BY timestampMs DESC LIMIT 1")
    BatteryUpdate getLatestForTripSync(String planId);

    @Query("SELECT * FROM battery_updates ORDER BY timestampMs DESC")
    List<BatteryUpdate> getAllUpdatesSync();

    /**
     * Original estimates are preserved for transparency: the first row for a trip keeps the
     * pre-correction estimate so the app can show estimate → correction → revised plan.
     */
    @Query("SELECT estimatedSoc FROM battery_updates WHERE tripPlanId = :planId ORDER BY timestampMs ASC LIMIT 1")
    Integer getFirstEstimateForTrip(String planId);
}
