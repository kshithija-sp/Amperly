package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.RoutePlan;

import java.util.List;

@Dao
public interface RoutePlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RoutePlan plan);

    @Update
    void update(RoutePlan plan);

    @Query("SELECT * FROM route_plans WHERE id = :id LIMIT 1")
    RoutePlan getPlanByIdSync(String id);

    @Query("SELECT * FROM route_plans WHERE consumerId = :consumerId ORDER BY createdAtMs DESC")
    LiveData<List<RoutePlan>> getPlansForConsumer(String consumerId);

    @Query("SELECT * FROM route_plans WHERE consumerId = :consumerId ORDER BY createdAtMs DESC")
    List<RoutePlan> getPlansForConsumerSync(String consumerId);

    @Query("SELECT * FROM route_plans WHERE consumerId = :consumerId AND status = 'ACTIVE' ORDER BY createdAtMs DESC LIMIT 1")
    RoutePlan getActivePlanSync(String consumerId);

    @Query("SELECT * FROM route_plans ORDER BY createdAtMs DESC")
    List<RoutePlan> getAllPlansSync();

    @Query("SELECT COUNT(*) FROM route_plans WHERE consumerId = :consumerId")
    int countPlans(String consumerId);
}
