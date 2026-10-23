package com.amperly.app.vehicle;

import android.content.Context;

import com.google.gson.Gson;

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
 * The owned vehicle spec database: the primary source in the resolver's lookup order.
 *
 * <p>It is a plain JSON document in {@code assets/vehicle_specs.json}. Dimensions are
 * millimetres, tyre sizes use the sidewall notation, and every entry is HIGH confidence
 * because it was checked against a manufacturer sheet.
 */
public final class VehicleSpecDatabase {

    private static final String ASSET = "vehicle_specs.json";

    private final Map<String, Entry> byModel = new LinkedHashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final int schemaVersion;

    private VehicleSpecDatabase(int schemaVersion, List<Entry> entries) {
        this.schemaVersion = schemaVersion;
        for (Entry entry : entries) {
            this.entries.add(entry);
            byModel.put(key(entry.make, entry.model), entry);
        }
    }

    public static VehicleSpecDatabase load(Context context) {
        try {
            return fromJson(readAsset(context));
        } catch (IOException e) {
            return empty();
        }
    }

    public static VehicleSpecDatabase fromJson(String json) {
        Document document = new Gson().fromJson(json, Document.class);
        if (document == null || document.vehicles == null) {
            return empty();
        }
        List<Entry> parsed = new ArrayList<>();
        for (Document.EntryDto dto : document.vehicles) {
            Entry entry = Entry.from(dto);
            if (entry != null) {
                parsed.add(entry);
            }
        }
        return new VehicleSpecDatabase(document.schemaVersion, parsed);
    }

    private static VehicleSpecDatabase empty() {
        return new VehicleSpecDatabase(0, new ArrayList<>());
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public List<VehicleSpecs> catalogue() {
        List<VehicleSpecs> specs = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            specs.add(entry.toSpecs(""));
        }
        return Collections.unmodifiableList(specs);
    }

    /** Exact make+model, then model-only, then a loose contains match. */
    public VehicleSpecs find(String make, String model, String variant) {
        if (model == null || model.trim().isEmpty()) {
            return null;
        }
        Entry entry = byModel.get(key(make, model));
        if (entry == null) {
            entry = byModel.get(VehicleSpecs.normalize(model));
        }
        if (entry == null) {
            String wanted = VehicleSpecs.normalize((make == null ? "" : make) + " " + model);
            for (Entry candidate : entries) {
                if (wanted.contains(candidate.lookupKey()) || candidate.lookupKey().contains(wanted)) {
                    entry = candidate;
                    break;
                }
            }
        }
        return entry == null ? null : entry.toSpecs(variant);
    }

    private static String key(String make, String model) {
        return (VehicleSpecs.normalize(make) + " " + VehicleSpecs.normalize(model)).trim();
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

    private static final class Entry {
        final String make;
        final String model;
        final BodyType bodyType;
        final int lengthMm;
        final int widthMm;
        final int heightMm;
        final int wheelbaseMm;
        final int groundClearanceMm;
        final int tyreWidthMm;
        final int tyreAspect;
        final int rimIn;
        final ChargePortLocation chargePort;
        final List<String> colours;
        final String source;
        final SpecConfidence confidence;

        Entry(String make, String model, BodyType bodyType, int lengthMm, int widthMm,
              int heightMm, int wheelbaseMm, int groundClearanceMm, int tyreWidthMm,
              int tyreAspect, int rimIn, ChargePortLocation chargePort, List<String> colours,
              String source, SpecConfidence confidence) {
            this.make = make;
            this.model = model;
            this.bodyType = bodyType;
            this.lengthMm = lengthMm;
            this.widthMm = widthMm;
            this.heightMm = heightMm;
            this.wheelbaseMm = wheelbaseMm;
            this.groundClearanceMm = groundClearanceMm;
            this.tyreWidthMm = tyreWidthMm;
            this.tyreAspect = tyreAspect;
            this.rimIn = rimIn;
            this.chargePort = chargePort;
            this.colours = colours;
            this.source = source;
            this.confidence = confidence == null ? SpecConfidence.HIGH : confidence;
        }

        static Entry from(Document.EntryDto dto) {
            if (dto == null || dto.model == null || dto.model.trim().isEmpty()) {
                return null;
            }
            if (dto.lengthMm <= 0 || dto.widthMm <= 0 || dto.heightMm <= 0) {
                return null;
            }
            Document.TyreDto tyre = dto.tyre == null ? new Document.TyreDto() : dto.tyre;
            return new Entry(
                    dto.make == null ? "" : dto.make.trim(),
                    dto.model.trim(),
                    BodyType.fromKey(dto.bodyType),
                    dto.lengthMm, dto.widthMm, dto.heightMm, dto.wheelbaseMm,
                    dto.groundClearanceMm,
                    tyre.widthMm, tyre.aspectPercent, tyre.rimDiameterIn,
                    ChargePortLocation.fromKey(dto.chargePort, ChargePortLocation.FRONT_CENTRE),
                    dto.colours == null ? Collections.emptyList() : dto.colours,
                    dto.source == null ? "database" : dto.source,
                    confidence(dto.confidence));
        }

        String lookupKey() {
            return (VehicleSpecs.normalize(make) + " " + VehicleSpecs.normalize(model)).trim();
        }

        VehicleSpecs toSpecs(String variant) {
            return VehicleSpecs.builder(make, model, bodyType)
                    .variant(variant)
                    .dimensionsMm(lengthMm, widthMm, heightMm)
                    .wheelbaseMm(wheelbaseMm)
                    .groundClearanceMm(groundClearanceMm)
                    .tyre(tyreWidthMm, tyreAspect, rimIn)
                    .chargePort(chargePort)
                    .colours(colours)
                    .confidence(confidence)
                    .source(source)
                    .build();
        }

        private static SpecConfidence confidence(String value) {
            try {
                return value == null ? SpecConfidence.HIGH : SpecConfidence.valueOf(value);
            } catch (RuntimeException e) {
                return SpecConfidence.HIGH;
            }
        }
    }

    /** Gson shape of the asset document. */
    private static final class Document {
        int schemaVersion;
        List<EntryDto> vehicles;

        static final class EntryDto {
            String make;
            String model;
            List<String> variants;
            String bodyType;
            int lengthMm;
            int widthMm;
            int heightMm;
            int wheelbaseMm;
            int groundClearanceMm;
            TyreDto tyre;
            String chargePort;
            List<String> colours;
            String source;
            String confidence;
        }

        static final class TyreDto {
            int widthMm;
            int aspectPercent;
            int rimDiameterIn;
        }
    }
}
