package com.amperly.app.vehicle;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the Tier 1 curated profiles from {@code assets/vehicle_profiles.json}.
 *
 * <p>The store is intentionally simple and immutable: profiles are keyed by normalised
 * make + model, and a missing or malformed asset simply means "no curated models", letting the
 * template fitter take over. It never throws at startup.
 */
public final class CuratedProfileStore {

    private static final String ASSET = "vehicle_profiles.json";

    private final Map<String, CuratedProfile> byKey = new LinkedHashMap<>();
    private final int schemaVersion;

    private CuratedProfileStore(int schemaVersion, List<CuratedProfile> profiles) {
        this.schemaVersion = schemaVersion;
        for (CuratedProfile profile : profiles) {
            byKey.put(VehicleSpecs.normalize(profile.make) + "|"
                    + VehicleSpecs.normalize(profile.model), profile);
        }
    }

    public static CuratedProfileStore load(Context context) {
        try {
            return fromJson(readAsset(context));
        } catch (IOException e) {
            return empty();
        }
    }

    public static CuratedProfileStore fromJson(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return empty();
        }
        try {
            Document document = new Gson().fromJson(json, Document.class);
            if (document == null || document.profiles == null) {
                return empty();
            }
            List<CuratedProfile> profiles = new ArrayList<>();
            for (JsonObject object : document.profiles) {
                CuratedProfile profile = CuratedProfile.fromJson(object);
                if (profile != null) {
                    profiles.add(profile);
                }
            }
            return new CuratedProfileStore(document.schemaVersion, profiles);
        } catch (RuntimeException e) {
            return empty();
        }
    }

    private static CuratedProfileStore empty() {
        return new CuratedProfileStore(0, Collections.emptyList());
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public int size() {
        return byKey.size();
    }

    /** Exact make + model, then a model-only match, mirroring the spec database's tolerance. */
    @Nullable
    public CuratedProfile find(String make, String model) {
        if (model == null || model.trim().isEmpty()) {
            return null;
        }
        String key = VehicleSpecs.normalize(make) + "|" + VehicleSpecs.normalize(model);
        CuratedProfile profile = byKey.get(key);
        if (profile != null) {
            return profile;
        }
        String modelKey = VehicleSpecs.normalize(model);
        for (Map.Entry<String, CuratedProfile> entry : byKey.entrySet()) {
            if (entry.getKey().endsWith("|" + modelKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public List<CuratedProfile> all() {
        return Collections.unmodifiableList(new ArrayList<>(byKey.values()));
    }

    private static String readAsset(Context context) throws IOException {
        try (InputStream stream = context.getAssets().open(ASSET)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class Document {
        int schemaVersion;
        List<JsonObject> profiles;
    }
}
