package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.SafetyEvent;

import java.util.List;

@Dao
public interface SafetyEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SafetyEvent event);

    @Update
    void update(SafetyEvent event);

    @Query("SELECT * FROM safety_events WHERE consumerId = :consumerId ORDER BY createdAtMs DESC")
    LiveData<List<SafetyEvent>> getEventsForConsumer(String consumerId);

    @Query("SELECT * FROM safety_events WHERE consumerId = :consumerId ORDER BY createdAtMs DESC")
    List<SafetyEvent> getEventsForConsumerSync(String consumerId);

    /** Sharing is time limited; anything past its expiry is treated as ended. */
    @Query("SELECT * FROM safety_events WHERE consumerId = :consumerId AND isLiveLocationSharingActive = 1 AND expiresAtMs > :nowMs ORDER BY createdAtMs DESC LIMIT 1")
    SafetyEvent getActiveSharingSync(String consumerId, long nowMs);

    @Query("UPDATE safety_events SET isLiveLocationSharingActive = 0 WHERE expiresAtMs > 0 AND expiresAtMs < :nowMs")
    int expireStaleSharing(long nowMs);

    @Query("SELECT * FROM safety_events ORDER BY createdAtMs DESC")
    List<SafetyEvent> getAllEventsSync();
}
