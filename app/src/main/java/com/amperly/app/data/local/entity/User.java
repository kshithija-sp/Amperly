package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.amperly.app.core.common.AppConstants;

@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    private String id;
    private String name;
    private String email;
    private String phone;
    private String role; // CONSUMER, HOST, ADMIN
    private String organizationName;
    private String verificationStatus; // UNVERIFIED, PENDING, VERIFIED
    private boolean isBlocked;

    public User(@NonNull String id, String name, String email, String phone, String role,
                String organizationName, String verificationStatus, boolean isBlocked) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.organizationName = organizationName;
        this.verificationStatus = verificationStatus;
        this.isBlocked = isBlocked;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public boolean isBlocked() { return isBlocked; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }

    // ------------------------------------------------------------- authority

    /**
     * Amperly accounts are dual-role: the same person books charging as a Consumer and lists
     * their own charger as a Host. {@link #role} is therefore the account's administrative
     * standing (ADMIN for platform ops, CONSUMER for everyone else), not a capability gate.
     *
     * <p>This matches the backend, where every host policy is written as
     * {@code "hostId" = current_app_user_id()} — ownership, not the role column, is what
     * grants the right to manage a station.
     */
    public boolean canActAsConsumer() {
        return !isBlocked;
    }

    /** Any account may list chargers it owns. A blocked account may not. */
    public boolean canActAsHost() {
        return !isBlocked;
    }

    /**
     * The admin desk is platform operations and is controlled by the server, so this is read
     * from the synced role column and never from the acting role the user picked in the UI.
     */
    public boolean isPlatformAdmin() {
        return AppConstants.ROLE_ADMIN.equalsIgnoreCase(role);
    }
}
