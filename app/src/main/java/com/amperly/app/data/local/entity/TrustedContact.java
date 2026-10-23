package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trusted_contacts")
public class TrustedContact {
    @PrimaryKey
    @NonNull
    private String id;
    private String userId;
    private String name;
    private String phone;

    public TrustedContact(@NonNull String id, String userId, String name, String phone) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.phone = phone;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
