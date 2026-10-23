package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages")
public class ChatMessage {
    @PrimaryKey
    @NonNull
    private String id;
    private String threadId;
    private String reservationId;
    private String senderId;
    private String receiverId;
    private String messageText;
    private long createdAtMs;
    private boolean isRead;
    private boolean isReported;
    private long expiresAtMs;

    public ChatMessage(@NonNull String id, String threadId, String reservationId,
                       String senderId, String receiverId, String messageText,
                       long createdAtMs, boolean isRead, boolean isReported, long expiresAtMs) {
        this.id = id;
        this.threadId = threadId;
        this.reservationId = reservationId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.messageText = messageText;
        this.createdAtMs = createdAtMs;
        this.isRead = isRead;
        this.isReported = isReported;
        this.expiresAtMs = expiresAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public boolean isReported() { return isReported; }
    public void setReported(boolean reported) { isReported = reported; }

    public long getExpiresAtMs() { return expiresAtMs; }
    public void setExpiresAtMs(long expiresAtMs) { this.expiresAtMs = expiresAtMs; }
}
