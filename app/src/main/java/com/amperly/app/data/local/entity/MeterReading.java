package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "meter_readings")
public class MeterReading {
    @PrimaryKey
    @NonNull
    private String id;
    private String sessionId;
    private String stationId;
    private double startMeterKwh;
    private double endMeterKwh;
    private String photoEvidenceUri;
    private String verificationLevel;
    private long timestampMs;
    private boolean isSimulated;

    public MeterReading(@NonNull String id, String sessionId, String stationId,
                        double startMeterKwh, double endMeterKwh, String photoEvidenceUri,
                        String verificationLevel, long timestampMs, boolean isSimulated) {
        this.id = id;
        this.sessionId = sessionId;
        this.stationId = stationId;
        this.startMeterKwh = startMeterKwh;
        this.endMeterKwh = endMeterKwh;
        this.photoEvidenceUri = photoEvidenceUri;
        this.verificationLevel = verificationLevel;
        this.timestampMs = timestampMs;
        this.isSimulated = isSimulated;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public double getStartMeterKwh() { return startMeterKwh; }
    public void setStartMeterKwh(double startMeterKwh) { this.startMeterKwh = startMeterKwh; }

    public double getEndMeterKwh() { return endMeterKwh; }
    public void setEndMeterKwh(double endMeterKwh) { this.endMeterKwh = endMeterKwh; }

    public String getPhotoEvidenceUri() { return photoEvidenceUri; }
    public void setPhotoEvidenceUri(String photoEvidenceUri) { this.photoEvidenceUri = photoEvidenceUri; }

    public String getVerificationLevel() { return verificationLevel; }
    public void setVerificationLevel(String verificationLevel) { this.verificationLevel = verificationLevel; }

    public long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; }

    public boolean isSimulated() { return isSimulated; }
    public void setSimulated(boolean simulated) { isSimulated = simulated; }
}
