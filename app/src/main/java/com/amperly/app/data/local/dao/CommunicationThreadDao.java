package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.CommunicationThread;

import java.util.List;

@Dao
public interface CommunicationThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CommunicationThread thread);

    @Update
    void update(CommunicationThread thread);

    @Query("SELECT * FROM communication_threads WHERE consumerId = :consumerId ORDER BY createdAtMs DESC")
    LiveData<List<CommunicationThread>> getThreadsForConsumer(String consumerId);

    @Query("SELECT * FROM communication_threads WHERE hostId = :hostId ORDER BY createdAtMs DESC")
    LiveData<List<CommunicationThread>> getThreadsForHost(String hostId);

    @Query("SELECT * FROM communication_threads WHERE hostId = :hostId ORDER BY createdAtMs DESC")
    List<CommunicationThread> getThreadsForHostSync(String hostId);

    @Query("SELECT * FROM communication_threads WHERE reservationId = :reservationId LIMIT 1")
    CommunicationThread getThreadByReservationSync(String reservationId);

    @Query("SELECT * FROM communication_threads ORDER BY createdAtMs DESC")
    List<CommunicationThread> getAllThreadsSync();

    /** Used by sync to re-read a thread after a bulk moderation flag changes. */
    @Query("SELECT * FROM communication_threads WHERE id = :id")
    CommunicationThread getThreadByIdSync(String id);

    @Query("UPDATE communication_threads SET isReported = 1 WHERE id = :threadId")
    void markReported(String threadId);

    @Query("UPDATE communication_threads SET isBlocked = 1 WHERE id = :threadId")
    void block(String threadId);
}
