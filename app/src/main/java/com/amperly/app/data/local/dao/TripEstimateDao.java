package com.amperly.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.amperly.app.data.local.entity.TripEstimate;

import java.util.List;

@Dao
public interface TripEstimateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TripEstimate estimate);

    @Query("SELECT * FROM trip_estimates WHERE planId = :planId ORDER BY createdAtMs")
    List<TripEstimate> getEstimatesForPlanSync(String planId);

    @Query("SELECT * FROM trip_estimates WHERE vehicleId = :vehicleId ORDER BY createdAtMs DESC")
    List<TripEstimate> getEstimatesForVehicleSync(String vehicleId);

    @Query("SELECT * FROM trip_estimates ORDER BY createdAtMs DESC")
    List<TripEstimate> getAllEstimatesSync();

    /** Average drift between planned and actual drain; used to correct future estimates. */
    @Query("SELECT AVG(actualDrainKwh - estimatedDrainKwh) FROM trip_estimates WHERE vehicleId = :vehicleId AND actualDrainKwh > 0")
    double averageDriftForVehicle(String vehicleId);
}
