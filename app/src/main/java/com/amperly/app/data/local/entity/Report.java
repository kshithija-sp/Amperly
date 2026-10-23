package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reports")
public class Report {
    @PrimaryKey
    @NonNull
    private String id;
    private String reporterId;
    private String reporterName;
    private String targetStationId;
    private String targetHostId;
    private String sessionId;
    private String reportType;
    private String description;
    private String status; // OPEN, REVIEWED, RESOLVED
    private long createdAtMs;

    public Report(@NonNull String id, String reporterId, String reporterName,
                  String targetStationId, String targetHostId, String sessionId,
                  String reportType, String description, String status, long createdAtMs) {
        this.id = id;
        this.reporterId = reporterId;
        this.reporterName = reporterName;
        this.targetStationId = targetStationId;
        this.targetHostId = targetHostId;
        this.sessionId = sessionId;
        this.reportType = reportType;
        this.description = description;
        this.status = status;
        this.createdAtMs = createdAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getTargetStationId() { return targetStationId; }
    public void setTargetStationId(String targetStationId) { this.targetStationId = targetStationId; }

    public String getTargetHostId() { return targetHostId; }
    public void setTargetHostId(String targetHostId) { this.targetHostId = targetHostId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
}
