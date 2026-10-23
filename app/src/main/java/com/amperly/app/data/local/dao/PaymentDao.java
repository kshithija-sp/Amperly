package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.Payment;

import java.util.List;

@Dao
public interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Payment payment);

    @Update
    void update(Payment payment);

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    Payment getPaymentByIdSync(String id);

    @Query("SELECT * FROM payments WHERE idempotencyKey = :key LIMIT 1")
    Payment findByIdempotencyKey(String key);

    @Query("SELECT * FROM payments WHERE reservationId = :reservationId LIMIT 1")
    Payment getPaymentForReservationSync(String reservationId);

    @Query("SELECT * FROM payments WHERE sessionId = :sessionId LIMIT 1")
    Payment getPaymentForSessionSync(String sessionId);

    @Query("SELECT * FROM payments WHERE consumerId = :consumerId ORDER BY createdAtMs DESC")
    LiveData<List<Payment>> getPaymentsForConsumer(String consumerId);

    @Query("SELECT * FROM payments WHERE hostId = :hostId ORDER BY createdAtMs DESC")
    List<Payment> getPaymentsForHostSync(String hostId);

    @Query("SELECT * FROM payments ORDER BY createdAtMs DESC")
    List<Payment> getAllPaymentsSync();
}
