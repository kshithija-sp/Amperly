package com.amperly.app;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.security.RoleGuard;
import com.amperly.app.data.local.entity.User;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoleGuardTest {

    private User user(String role) {
        return new User("user_1", "Test User", "user@example.com", "+919876543210", role,
                role.equals(AppConstants.ROLE_HOST) ? "ABC College" : null, "VERIFIED", false);
    }

    @Test
    public void onlyAdministratorsCanPerformAdminActions() {
        assertTrue(RoleGuard.canPerformAdminAction(user(AppConstants.ROLE_ADMIN)));
        assertFalse(RoleGuard.canPerformAdminAction(user(AppConstants.ROLE_HOST)));
        assertFalse(RoleGuard.canPerformAdminAction(user(AppConstants.ROLE_CONSUMER)));
        assertFalse(RoleGuard.canPerformAdminAction(null));
    }

    @Test
    public void hostsCanOnlyManageTheirOwnStations() {
        User host = user(AppConstants.ROLE_HOST);
        assertTrue(RoleGuard.canManageStation(host, "user_1"));
        assertFalse(RoleGuard.canManageStation(host, "someone_else"));
    }

    @Test
    public void administratorsCanManageAnyStation() {
        assertTrue(RoleGuard.canManageStation(user(AppConstants.ROLE_ADMIN), "any_station_host"));
    }

    @Test
    public void aSingleAccountCanReserveAsConsumerOrHost() {
        assertTrue(RoleGuard.canReservePort(user(AppConstants.ROLE_CONSUMER)));
        assertTrue(RoleGuard.canReservePort(user(AppConstants.ROLE_HOST)));
    }
}
