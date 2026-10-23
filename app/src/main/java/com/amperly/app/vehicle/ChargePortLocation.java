package com.amperly.app.vehicle;

import java.util.Locale;

/** Where the charge flap sits on the body, so the renderer can highlight it in a charging flow. */
public enum ChargePortLocation {
    FRONT_LEFT("Front left"),
    FRONT_CENTRE("Front centre"),
    FRONT_RIGHT("Front right"),
    REAR_LEFT("Rear left"),
    REAR_CENTRE("Rear centre"),
    REAR_RIGHT("Rear right");

    private final String label;

    ChargePortLocation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ChargePortLocation fromKey(String value, ChargePortLocation fallback) {
        if (value == null) {
            return fallback;
        }
        String key = value.trim().toUpperCase(Locale.US).replace(' ', '_').replace('-', '_');
        for (ChargePortLocation location : values()) {
            if (location.name().equals(key)) {
                return location;
            }
        }
        return fallback;
    }
}
