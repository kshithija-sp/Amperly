package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.Notification;

import java.util.List;

@Dao
public interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Notification notification);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Notification> notifications);

    @Update
    void update(Notification notification);

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAtMs DESC")
    LiveData<List<Notification>> getNotificationsForUser(String userId);

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAtMs DESC")
    List<Notification> getNotificationsForUserSync(String userId);

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    int countUnread(String userId);

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    void markAllRead(String userId);
}
