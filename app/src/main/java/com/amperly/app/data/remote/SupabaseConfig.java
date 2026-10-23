package com.amperly.app.data.remote;

import com.amperly.app.BuildConfig;

/**
 * Single place that knows whether a Supabase project has been configured.
 *
 * <p>Both the project URL and the publishable key are baked into {@code BuildConfig}
 * from {@code local.properties} (or the matching environment variables) at build time.
 * When either is missing the whole backend layer stays switched off and the app runs
 * exactly as it did before: Room on the device, seeded locally, no networking at all.
 *
 * <p>The publishable key ({@code sb_publishable_...}, formerly the {@code anon} key) is
 * designed to be public and ships inside the APK. That is only safe because every table
 * in {@code supabase/schema.sql} has Row Level Security enabled with explicit policies.
 * A {@code sb_secret_} / {@code service_role} key bypasses RLS completely and must never
 * reach this class — the Gradle build rejects it outright.
 */
public final class SupabaseConfig {

    private SupabaseConfig() {
    }

    /** True only when both halves of the configuration are present. */
    public static boolean isConfigured() {
        return BuildConfig.SUPABASE_ENABLED
                && !BuildConfig.SUPABASE_URL.isEmpty()
                && !BuildConfig.SUPABASE_PUBLISHABLE_KEY.isEmpty();
    }

    /** Base URL with the trailing slash Retrofit requires, or an empty string. */
    public static String baseUrl() {
        String url = BuildConfig.SUPABASE_URL.trim();
        if (url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url : url + "/";
    }

    public static String publishableKey() {
        return BuildConfig.SUPABASE_PUBLISHABLE_KEY;
    }

    /** True when the configured key looks like a secret key rather than a publishable one. */
    public static boolean looksLikeSecretKey() {
        String key = publishableKey();
        return key.startsWith("sb_secret_") || key.contains("service_role");
    }
}
