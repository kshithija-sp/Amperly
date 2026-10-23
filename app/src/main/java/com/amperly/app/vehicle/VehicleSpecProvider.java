package com.amperly.app.vehicle;

import androidx.annotation.Nullable;

/**
 * Tier 3(b): a third-party specification source.
 *
 * <p>Implementations must be safe to call from a background thread and must return null rather
 * than throwing when the vehicle is unknown, the network fails or the response is malformed.
 * The resolver then falls through to the estimator.
 */
public interface VehicleSpecProvider {

    /** True when a key and endpoint are configured; otherwise the tier is skipped entirely. */
    boolean isConfigured();

    @Nullable
    VehicleSpecs lookup(String make, String model, String variant);

    /**
     * Year-aware lookup for providers whose API needs the model year. Providers that do not
     * care about the year keep the three-argument behaviour.
     */
    @Nullable
    default VehicleSpecs lookup(String make, String model, String variant, int year) {
        return lookup(make, model, variant);
    }
}
