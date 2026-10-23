package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.Dispute;

import java.util.List;

@Dao
public interface DisputeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Dispute dispute);

    @Update
    void update(Dispute dispute);

    @Query("SELECT * FROM disputes ORDER BY createdAtMs DESC")
    LiveData<List<Dispute>> getAllDisputes();

    @Query("SELECT * FROM disputes ORDER BY createdAtMs DESC")
    List<Dispute> getAllDisputesSync();

    @Query("SELECT * FROM disputes WHERE consumerId = :consumerId ORDER BY createdAtMs DESC")
    LiveData<List<Dispute>> getDisputesForConsumer(String consumerId);

    @Query("SELECT * FROM disputes WHERE hostId = :hostId ORDER BY createdAtMs DESC")
    List<Dispute> getDisputesForHostSync(String hostId);

    @Query("SELECT * FROM disputes WHERE status IN ('OPEN','AWAITING_HOST') ORDER BY createdAtMs DESC")
    List<Dispute> getOpenDisputesSync();

    @Query("SELECT * FROM disputes WHERE id = :id LIMIT 1")
    Dispute getDisputeByIdSync(String id);

    @Query("SELECT COUNT(*) FROM disputes WHERE hostId = :hostId AND enforcementAction NOT IN ('NONE','')")
    int countProvenIncidentsForHost(String hostId);
}
