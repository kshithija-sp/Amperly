package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "disputes")
public class Dispute {
    @PrimaryKey
    @NonNull
    private String id;
    private String sessionId;
    private String consumerId;
    private String consumerName;
    private String hostId;
    private String stationId;
    private String stationName;
    private String reason;
    private String consumerNotes;
    private String hostNotes;
    private String evidence; // comma separated meter-reading ids / photo references
    private String status; // OPEN, AWAITING_HOST, RESOLVED_CONSUMER, RESOLVED_HOST, REJECTED
    private String enforcementAction; // NONE, WARNING, FINE, SUSPENSION, BAN
    private double refundAmount;
    private String resolutionNote;
    private boolean isPayoutFrozen;
    private long createdAtMs;
    private long resolvedAtMs;

    public Dispute(@NonNull String id, String sessionId, String consumerId, String consumerName,
                   String hostId, String stationId, String stationName, String reason,
                   String consumerNotes, String hostNotes, String evidence, String status,
                   String enforcementAction, double refundAmount, String resolutionNote,
                   boolean isPayoutFrozen, long createdAtMs, long resolvedAtMs) {
        this.id = id;
        this.sessionId = sessionId;
        this.consumerId = consumerId;
        this.consumerName = consumerName;
        this.hostId = hostId;
        this.stationId = stationId;
        this.stationName = stationName;
        this.reason = reason;
        this.consumerNotes = consumerNotes;
        this.hostNotes = hostNotes;
        this.evidence = evidence;
        this.status = status;
        this.enforcementAction = enforcementAction;
        this.refundAmount = refundAmount;
        this.resolutionNote = resolutionNote;
        this.isPayoutFrozen = isPayoutFrozen;
        this.createdAtMs = createdAtMs;
        this.resolvedAtMs = resolvedAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getConsumerName() { return consumerName; }
    public void setConsumerName(String consumerName) { this.consumerName = consumerName; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getConsumerNotes() { return consumerNotes; }
    public void setConsumerNotes(String consumerNotes) { this.consumerNotes = consumerNotes; }

    public String getHostNotes() { return hostNotes; }
    public void setHostNotes(String hostNotes) { this.hostNotes = hostNotes; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEnforcementAction() { return enforcementAction; }
    public void setEnforcementAction(String enforcementAction) { this.enforcementAction = enforcementAction; }

    public double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public boolean isPayoutFrozen() { return isPayoutFrozen; }
    public void setPayoutFrozen(boolean payoutFrozen) { isPayoutFrozen = payoutFrozen; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    public long getResolvedAtMs() { return resolvedAtMs; }
    public void setResolvedAtMs(long resolvedAtMs) { this.resolvedAtMs = resolvedAtMs; }

    public boolean isOpen() {
        return "OPEN".equals(status) || "AWAITING_HOST".equals(status);
    }
}
