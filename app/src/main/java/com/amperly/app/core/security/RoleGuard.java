package com.amperly.app.core.security;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.data.local.entity.User;

/**
 * Client-side mirror of the backend's rules. It decides what the UI offers; the server's row level
 * security remains the thing that actually enforces it.
 */
public class RoleGuard {

    /** The admin desk is platform operations, controlled by the server-side role column. */
    public static boolean canPerformAdminAction(User user) {
        return user != null && user.isPlatformAdmin();
    }

    /**
     * Ownership, not the role column, is what grants control of a station — matching the
     * {@code "hostId" = current_app_user_id()} policies in schema.sql. Any account can manage the
     * chargers it registered, which is what makes one account able to be both Consumer and Host.
     */
    public static boolean canManageStation(User user, String stationHostId) {
        if (user == null) return false;
        if (user.isPlatformAdmin()) return true;
        return user.getId() != null && user.getId().equals(stationHostId);
    }

    /** Every signed-in account, in either capacity, may book a port. */
    public static boolean canReservePort(User user) {
        return user != null && user.canActAsConsumer();
    }

    /** Every signed-in account may list a charger it owns. */
    public static boolean canRegisterStation(User user) {
        return user != null && user.canActAsHost();
    }
}
