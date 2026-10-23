package com.amperly.app.core.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Who the app is acting as, and in which capacity.
 *
 * <p>An Amperly account is dual-role. The same person reserves charging as a Consumer and lists
 * their own charger as a Host, so there is only ever one account behind the session: switching
 * role changes what the app shows and is allowed to do, never who is signed in.
 * {@link #getCurrentUserId()} returns the same id in both modes, which is exactly what the
 * server expects — every host policy is written as {@code "hostId" = current_app_user_id()}.
 *
 * <p>There is no offline demo identity. The account is always a real Supabase user resolved from
 * the server, because every screen reads through Row Level Security and a fabricated local
 * identity would only produce rows the server would never authorise.
 */
public class AuthManager {

    private static volatile AuthManager INSTANCE;

    /** The one account behind this session; null until a real sign-in completes. */
    private User account;

    private String currentRole = AppConstants.ROLE_CONSUMER;

    private AuthManager() {
        AppStateStore.get().setRole(currentRole);
        AppStateStore.get().setUserId("");
    }

    public static synchronized AuthManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AuthManager();
        }
        return INSTANCE;
    }

    /**
     * The identity the current screen is acting as. The account never changes with the role:
     * only what it is allowed to see and do changes.
     */
    @Nullable
    public User getCurrentUser() {
        return account;
    }

    /** The signed-in account itself, whatever role is currently being acted out. */
    @Nullable
    public User getAccount() {
        return account;
    }

    public void setCurrentUser(@Nullable User user) {
        account = user;
        currentRole = AppConstants.ROLE_CONSUMER;
        AppStateStore.get().setRole(currentRole);
        AppStateStore.get().setUserId(getCurrentUserId());
    }

    /**
     * Forgets the acting identity entirely. Called when a Supabase session ends, so the next
     * account on this device starts from its own profile rather than the previous holder's.
     */
    public void clearCurrentUser() {
        account = null;
        currentRole = AppConstants.ROLE_CONSUMER;
        AppStateStore.get().setRole(currentRole);
        AppStateStore.get().setUserId("");
    }

    public boolean isLoggedIn() {
        return account != null;
    }

    @NonNull
    public String getCurrentUserId() {
        User user = getCurrentUser();
        return user == null ? "" : user.getId();
    }

    @NonNull
    public String getCurrentRole() {
        return currentRole;
    }

    /**
     * Roles this account may act in. Consumer and Host always come as a pair — that is the
     * dual-role rule — while Admin is only offered when the server says the account is an ADMIN.
     */
    @NonNull
    public List<String> availableRoles() {
        List<String> roles = new ArrayList<>();
        if (account != null) {
            if (account.canActAsConsumer()) {
                roles.add(AppConstants.ROLE_CONSUMER);
            }
            if (account.canActAsHost()) {
                roles.add(AppConstants.ROLE_HOST);
            }
            if (account.isPlatformAdmin() && !roles.contains(AppConstants.ROLE_ADMIN)) {
                roles.add(AppConstants.ROLE_ADMIN);
            }
        }
        return roles;
    }

    public boolean canActAs(String role) {
        return role != null && availableRoles().contains(role);
    }

    /**
     * Switches the acting role. Returns false when the account has no authority for it, so the
     * caller can put the selection back rather than leaving the UI claiming a capability the
     * account does not have.
     */
    public boolean setRole(@Nullable String role) {
        if (!canActAs(role)) {
            return false;
        }
        currentRole = role;
        AppStateStore.get().setRole(role);
        AppStateStore.get().setUserId(getCurrentUserId());
        return true;
    }

    @NonNull
    public String describeCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            return "Not signed in";
        }
        return "Acting as " + user.getName() + " (" + roleLabel(currentRole) + ")"
                + (user.getOrganizationName() == null || user.getOrganizationName().isEmpty()
                        ? "" : " · " + user.getOrganizationName());
    }

    /**
     * What an account is allowed to do, spelled out. Used on the account page, where the answer
     * to "why can I see a Host area?" is simply that every account can be both.
     */
    @NonNull
    public static String authorityLabel(@Nullable User user) {
        if (user == null) {
            return "—";
        }
        StringBuilder builder = new StringBuilder("Consumer + Host");
        if (user.isPlatformAdmin()) {
            builder.append(" + Admin");
        }
        return builder.toString();
    }

    @NonNull
    public static String roleLabel(@Nullable String role) {
        if (AppConstants.ROLE_HOST.equals(role)) {
            return "Host";
        }
        if (AppConstants.ROLE_ADMIN.equals(role)) {
            return "Admin";
        }
        return "Consumer";
    }
}
