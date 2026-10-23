package com.amperly.app.vehicle;

import java.util.Locale;

/** The wheel face a curated profile asks the mesh builder to draw. */
public enum WheelStyle {
    /** Five-spoke alloy face (the default). */
    SPOKE,
    /** Flat aero disc, as fitted to efficiency-focused EVs. */
    AERO,
    /** Simple steel-style disc with a small hub cap. */
    DISC;

    public static WheelStyle fromKey(String value, WheelStyle fallback) {
        if (value == null) {
            return fallback;
        }
        String key = value.trim().toUpperCase(Locale.US).replace(' ', '_').replace('-', '_');
        for (WheelStyle style : values()) {
            if (style.name().equals(key)) {
                return style;
            }
        }
        return fallback;
    }
}
