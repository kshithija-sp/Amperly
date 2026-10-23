package com.amperly.app.vehicle;

import java.util.Locale;

/**
 * The eight body silhouettes the parametric renderer can build. Every vehicle resolves to one
 * of these; the template supplies the proportions, the specs supply the real dimensions.
 */
public enum BodyType {
    HATCHBACK("Hatchback"),
    SEDAN("Sedan"),
    COMPACT_SUV("Compact SUV"),
    SUV("SUV"),
    MPV("MPV"),
    COUPE("Coupe"),
    PICKUP("Pickup"),
    MICROCAR("Microcar");

    private final String label;

    BodyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Tolerant parser for JSON values such as "compact_suv" or "Compact SUV". */
    public static BodyType fromKey(String value) {
        if (value == null) {
            return HATCHBACK;
        }
        String key = value.trim().toUpperCase(Locale.US).replace(' ', '_').replace('-', '_');
        for (BodyType type : values()) {
            if (type.name().equals(key)) {
                return type;
            }
        }
        return HATCHBACK;
    }
}
