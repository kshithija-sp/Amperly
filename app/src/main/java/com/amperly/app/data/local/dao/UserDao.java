package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.User;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(User user);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<User> users);

    @Update
    void update(User user);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User getUserByIdSync(String id);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    LiveData<User> getUserById(String id);

    @Query("SELECT * FROM users ORDER BY name")
    LiveData<List<User>> getAllUsers();

    @Query("SELECT * FROM users ORDER BY name")
    List<User> getAllUsersSync();

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User getUserByEmailSync(String email);

    @Query("SELECT COUNT(*) FROM users")
    int countUsers();
}
