package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.Invoice;

import java.util.List;

@Dao
public interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Invoice invoice);

    @Update
    void update(Invoice invoice);

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    Invoice getInvoiceByIdSync(String id);

    @Query("SELECT * FROM invoices WHERE sessionId = :sessionId LIMIT 1")
    Invoice getInvoiceBySessionSync(String sessionId);

    @Query("SELECT * FROM invoices WHERE consumerId = :consumerId ORDER BY issuedAtMs DESC")
    LiveData<List<Invoice>> getInvoicesForConsumer(String consumerId);

    @Query("SELECT * FROM invoices WHERE consumerId = :consumerId ORDER BY issuedAtMs DESC")
    List<Invoice> getInvoicesForConsumerSync(String consumerId);

    @Query("SELECT * FROM invoices WHERE hostId = :hostId ORDER BY issuedAtMs DESC")
    List<Invoice> getInvoicesForHostSync(String hostId);

    @Query("SELECT * FROM invoices ORDER BY issuedAtMs DESC")
    List<Invoice> getAllInvoicesSync();

    @Query("SELECT SUM(finalTotal) FROM invoices WHERE hostId = :hostId AND status != 'REFUNDED'")
    double totalRevenueForHost(String hostId);

    @Query("SELECT SUM(adjustment) FROM invoices WHERE hostId = :hostId")
    double totalAdjustmentsForHost(String hostId);
}
