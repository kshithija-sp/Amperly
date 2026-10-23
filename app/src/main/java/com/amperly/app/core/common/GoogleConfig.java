package com.amperly.app.core.common;

import androidx.annotation.NonNull;

import com.amperly.app.BuildConfig;

/**
 * One place that answers "is Google Maps Platform configured?".
 *
 * <p>The key itself lives in {@code local.properties} (gitignored) and reaches the app two ways:
 * {@link BuildConfig#GOOGLE_MAPS_API_KEY} for the in-app Directions and Place-autocomplete web
 * calls, and a manifest placeholder for the Maps SDK renderer. Nothing about the key is ever
 * written into Java or XML by hand.
 *
 * <p>Every screen that needs the map or a route asks this class first. When the key is missing the
 * screen shows an actionable setup notice instead of a blank, silently broken map.
 */
public final class GoogleConfig {

    private GoogleConfig() {
    }

    /** True when a non-blank Google Maps Platform key was injected at build time. */
    public static boolean isConfigured() {
        String key = apiKey();
        return BuildConfig.GOOGLE_MAPS_ENABLED && !key.isEmpty();
    }

    /** The injected key, never null (blank when unset). */
    @NonNull
    public static String apiKey() {
        String key = BuildConfig.GOOGLE_MAPS_API_KEY;
        return key == null ? "" : key.trim();
    }

    /** A short, user-facing message explaining how to fix an unconfigured build. */
    @NonNull
    public static String setupMessage() {
        return "Google Maps is not configured in this build. Add GOOGLE_MAPS_API_KEY to "
                + "local.properties (Maps SDK for Android + Places API + Directions API) and "
                + "rebuild. See README section 6.";
    }
}
