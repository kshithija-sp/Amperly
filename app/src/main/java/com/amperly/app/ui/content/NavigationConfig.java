package com.amperly.app.ui.content;

import com.amperly.app.R;
import com.amperly.app.model.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * The role's real destinations.
 *
 * <p>These are the same screens the platform shell has always offered — discovery, route
 * planning, reservations, the live session and safety for drivers; the dashboard, station
 * registration and chat for hosts; the operations desk for admins — rendered by the pixel
 * bottom bar instead of a Material menu.
 */
public final class NavigationConfig {

    public static final String PAGE_DISCOVER = "consumer_discover";
    public static final String PAGE_ROUTE = "consumer_route";
    public static final String PAGE_RESERVE = "consumer_reserve";
    public static final String PAGE_SESSION = "consumer_session";
    public static final String PAGE_SAFETY = "consumer_safety";

    public static final String PAGE_HOST_DASHBOARD = "host_dashboard";
    public static final String PAGE_HOST_REGISTER = "host_register";
    public static final String PAGE_HOST_CHAT = "host_chat";

    public static final String PAGE_ADMIN = "admin_dashboard";

    private NavigationConfig() {
    }

    public static List<NavItem> forRole(Role role) {
        List<NavItem> items = new ArrayList<>();
        switch (role) {
            case HOST:
                items.add(new NavItem(PAGE_HOST_DASHBOARD, "Dashboard", R.drawable.ic_dashboard));
                items.add(new NavItem(PAGE_HOST_REGISTER, "Add station", R.drawable.ic_add));
                items.add(new NavItem(PAGE_HOST_CHAT, "Chat", R.drawable.ic_chat));
                break;
            case ADMIN:
                items.add(new NavItem(PAGE_ADMIN, "Operations", R.drawable.ic_admin));
                break;
            case CONSUMER:
            default:
                items.add(new NavItem(PAGE_DISCOVER, "Discover", R.drawable.ic_discover));
                items.add(new NavItem(PAGE_ROUTE, "Route", R.drawable.ic_route));
                items.add(new NavItem(PAGE_RESERVE, "Reserve", R.drawable.ic_reserve));
                items.add(new NavItem(PAGE_SESSION, "Session", R.drawable.ic_session));
                items.add(new NavItem(PAGE_SAFETY, "Safety", R.drawable.ic_safety));
                break;
        }
        return items;
    }

    /** Human label for a capacity, matching the shell's summary line. */
    public static String modeText(Role role) {
        switch (role) {
            case HOST:
                return "Host mode";
            case ADMIN:
                return "Admin mode";
            case CONSUMER:
            default:
                return "Consumer mode";
        }
    }
}
