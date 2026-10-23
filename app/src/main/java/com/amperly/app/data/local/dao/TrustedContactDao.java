package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.amperly.app.data.local.entity.TrustedContact;

import java.util.List;

@Dao
public interface TrustedContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TrustedContact contact);

    @Delete
    void delete(TrustedContact contact);

    @Query("SELECT * FROM trusted_contacts WHERE userId = :userId")
    LiveData<List<TrustedContact>> getContactsForUser(String userId);

    @Query("SELECT * FROM trusted_contacts WHERE userId = :userId")
    List<TrustedContact> getContactsForUserSync(String userId);

    @Query("SELECT * FROM trusted_contacts ORDER BY name")
    List<TrustedContact> getAllContactsSync();
}
