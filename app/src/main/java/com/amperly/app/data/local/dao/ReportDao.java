package com.amperly.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.amperly.app.data.local.entity.Report;

import java.util.List;

@Dao
public interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Report report);

    @Update
    void update(Report report);

    @Query("SELECT * FROM reports ORDER BY createdAtMs DESC")
    LiveData<List<Report>> getAllReports();

    @Query("SELECT * FROM reports ORDER BY createdAtMs DESC")
    List<Report> getAllReportsSync();

    @Query("SELECT * FROM reports WHERE status = 'OPEN' ORDER BY createdAtMs DESC")
    List<Report> getOpenReportsSync();

    @Query("SELECT * FROM reports WHERE reporterId = :reporterId ORDER BY createdAtMs DESC")
    LiveData<List<Report>> getReportsForReporter(String reporterId);

    @Query("SELECT * FROM reports WHERE targetStationId = :stationId ORDER BY createdAtMs DESC")
    List<Report> getReportsForStationSync(String stationId);

    @Query("SELECT COUNT(*) FROM reports WHERE targetStationId = :stationId")
    int countForStation(String stationId);
}
