package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.Vehicle;

import java.util.List;

@Dao
public interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Vehicle vehicle);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Vehicle> vehicles);

    @Update
    void update(Vehicle vehicle);

    @Query("SELECT * FROM vehicles WHERE userId = :userId LIMIT 1")
    Vehicle getVehicleByUserIdSync(String userId);

    @Query("SELECT * FROM vehicles WHERE userId = :userId LIMIT 1")
    LiveData<Vehicle> getVehicleByUserId(String userId);

    @Query("SELECT * FROM vehicles WHERE userId = :userId")
    List<Vehicle> getVehiclesForUserSync(String userId);

    @Query("SELECT * FROM vehicles")
    List<Vehicle> getAllVehiclesSync();
}
