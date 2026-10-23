package com.amperly.app.vehicle;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * resolveVehicleSpecs: the lookup order is owned database, then (when a key is configured) a
 * third-party spec API adapter, then the estimator. Every result - including estimates - is
 * cached in memory and on disk, keyed by make and model.
 */
public final class SpecResolver {

    private static final String PREFS = "vehicle_spec_cache";
    private static final String PREFIX = "spec:";

    private static volatile SpecResolver instance;

    private final VehicleSpecDatabase database;
    private final SharedPreferences prefs;
    private final CuratedProfileStore profiles;
    private final VehicleSpecProvider provider;
    private final Map<String, VehicleSpecs> memory = new ConcurrentHashMap<>();

    public SpecResolver(VehicleSpecDatabase database, @Nullable SharedPreferences prefs) {
        this(database, prefs, null, null);
    }

    public SpecResolver(VehicleSpecDatabase database, @Nullable SharedPreferences prefs,
                        @Nullable CuratedProfileStore profiles,
                        @Nullable VehicleSpecProvider provider) {
        this.database = database;
        this.prefs = prefs;
        this.profiles = profiles;
        this.provider = provider;
    }

    public static SpecResolver getInstance(Context context) {
        SpecResolver local = instance;
        if (local == null) {
            synchronized (SpecResolver.class) {
                local = instance;
                if (local == null) {
                    Context app = context.getApplicationContext();
                    VehicleSpecProvider remote = VehicleDatabasesProvider.fromConfig();
                    if (remote == null) {
                        remote = RemoteSpecProvider.fromConfig();
                    }
                    local = new SpecResolver(VehicleSpecDatabase.load(app),
                            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                            CuratedProfileStore.load(app), remote);
                    instance = local;
                }
            }
        }
        return local;
    }

    public VehicleSpecs resolve(String make, String model, String variant) {
        return resolve(make, model, variant, 0);
    }

    /** Year-aware resolution; the year is passed to providers that need it and keyed in the cache. */
    public VehicleSpecs resolve(String make, String model, String variant, int year) {
        String key = cacheKey(make, model, year);
        VehicleSpecs cached = memory.get(key);
        if (cached != null) {
            return cached;
        }

        VehicleSpecs fromDisk = readDisk(key);
        if (fromDisk != null) {
            memory.put(key, fromDisk);
            return fromDisk;
        }

        // Tier 3: owned database, then the configured third-party adapter, then the estimator.
        VehicleSpecs resolved = database.find(make, model, variant);
        if (resolved == null && provider != null && provider.isConfigured()) {
            try {
                resolved = provider.lookup(make, model, variant, year);
            } catch (RuntimeException ignored) {
                // A provider failure must never block the estimator fallback.
            }
        }
        if (resolved == null) {
            resolved = SpecEstimator.estimate(make, model, variant);
        }

        // Tier 1: a curated profile wins where it supplies values and is attached otherwise.
        CuratedProfile profile = profiles == null ? null : profiles.find(make, model);
        if (profile != null) {
            resolved = profile.apply(resolved);
        }

        cache(key, resolved);
        return resolved;
    }

    /** The driver confirmed or corrected the silhouette; dimensions follow the chosen template. */
    public VehicleSpecs confirmBodyType(VehicleSpecs base, BodyType bodyType) {
        BodyTemplate template = BodyTemplate.of(bodyType);
        boolean keepRealDimensions = base.confidence == SpecConfidence.HIGH;
        VehicleSpecs confirmed = VehicleSpecs.builder(base.make, base.model, bodyType)
                .variant(base.variant)
                .dimensionsMm(
                        keepRealDimensions ? base.lengthMm : template.referenceLengthMm,
                        keepRealDimensions ? base.widthMm : template.referenceWidthMm,
                        keepRealDimensions ? base.heightMm : template.referenceHeightMm)
                .wheelbaseMm(keepRealDimensions
                        ? base.wheelbaseMm : template.referenceWheelbaseMm)
                .groundClearanceMm(keepRealDimensions
                        ? base.groundClearanceMm : template.referenceGroundClearanceMm)
                .tyre(
                        keepRealDimensions ? base.tyreWidthMm : template.referenceTyreWidthMm,
                        keepRealDimensions
                                ? base.tyreAspectPercent : template.referenceTyreAspectPercent,
                        keepRealDimensions
                                ? base.rimDiameterIn : template.referenceRimDiameterIn)
                .chargePort(base.chargePort)
                .colours(base.colours)
                .confidence(SpecConfidence.MEDIUM)
                .source("driver")
                .curated(base.curated)
                .build();
        cache(confirmed.key(), confirmed);
        return confirmed;
    }

    /** Everything in the owned database, with curation attached, used by the validation gallery. */
    public List<VehicleSpecs> catalogue() {
        List<VehicleSpecs> catalogue = new ArrayList<>();
        for (VehicleSpecs specs : database.catalogue()) {
            CuratedProfile profile = profiles == null
                    ? null : profiles.find(specs.make, specs.model);
            catalogue.add(profile == null ? specs : profile.apply(specs));
        }
        return catalogue;
    }

    private static String cacheKey(String make, String model, int year) {
        String key = VehicleSpecs.normalize(make) + "|" + VehicleSpecs.normalize(model);
        return year > 0 ? key + "|" + year : key;
    }

    public void clearCache() {
        memory.clear();
        if (prefs != null) {
            prefs.edit().clear().apply();
        }
    }

    private void cache(String key, VehicleSpecs specs) {
        memory.put(key, specs);
        if (prefs != null) {
            prefs.edit().putString(PREFIX + key, VehicleSpecsJson.encode(specs)).apply();
        }
    }

    private VehicleSpecs readDisk(String key) {
        if (prefs == null) {
            return null;
        }
        return VehicleSpecsJson.decode(prefs.getString(PREFIX + key, null));
    }
}
