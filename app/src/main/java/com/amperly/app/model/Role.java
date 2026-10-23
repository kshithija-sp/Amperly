package com.amperly.app.model;

/** Capacities of a single Amperly account. Switching never creates a new login. */
public enum Role {
    CONSUMER("Consumer", "Consumer mode"),
    HOST("Host", "Host mode"),
    ADMIN("Admin", "Admin mode");

    private final String label;
    private final String modeText;

    Role(String label, String modeText) {
        this.label = label;
        this.modeText = modeText;
    }

    public String getLabel() {
        return label;
    }

    public String getModeText() {
        return modeText;
    }

    public static Role fromKey(String key) {
        if (key != null) {
            for (Role role : values()) {
                if (role.name().equals(key)) {
                    return role;
                }
            }
        }
        return CONSUMER;
    }
}
