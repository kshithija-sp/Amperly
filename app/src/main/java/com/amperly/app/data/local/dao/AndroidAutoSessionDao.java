package com.amperly.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.AndroidAutoSession;

import java.util.List;

@Dao
public interface AndroidAutoSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AndroidAutoSession session);

    @Update
    void update(AndroidAutoSession session);

    @Query("SELECT * FROM android_auto_sessions WHERE userId = :userId ORDER BY connectedAtMs DESC LIMIT 1")
    AndroidAutoSession getLatestForUserSync(String userId);

    @Query("SELECT * FROM android_auto_sessions WHERE userId = :userId ORDER BY connectedAtMs DESC")
    List<AndroidAutoSession> getSessionsForUserSync(String userId);

    @Query("SELECT * FROM android_auto_sessions ORDER BY connectedAtMs DESC")
    List<AndroidAutoSession> getAllSessionsSync();

    @Query("SELECT COUNT(*) FROM android_auto_sessions")
    int countSessions();
}
