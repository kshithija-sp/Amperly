package com.amperly.app.core.places;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amperly.app.core.common.GoogleConfig;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Destination and address entry through Google Place autocomplete.
 *
 * <p>The driver types a shop, hotel, office, landmark or town and picks the real place; Amperly
 * keeps the Place ID, the formatted address and the coordinates, and never stores anything else
 * about the user's search.
 *
 * <p>Only the fields the flow genuinely needs are requested
 * ({@code ID}, {@code DISPLAY_NAME}, {@code FORMATTED_ADDRESS}, {@code LAT_LNG}), in line with
 * Google's field-masking guidance. Everything degrades gracefully when the key is missing or the
 * service is unreachable: the plain text field keeps working and the screen says so.
 */
public final class PlacesAutocomplete {

    /** A place the driver chose. */
    public static final class Selection {
        private final String placeId;
        private final String label;
        private final String address;
        private final double latitude;
        private final double longitude;

        public Selection(String placeId, String label, String address, double latitude,
                         double longitude) {
            this.placeId = placeId;
            this.label = label;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @NonNull public String getPlaceId() { return placeId == null ? "" : placeId; }
        @NonNull public String getLabel() { return label == null ? "" : label; }
        @NonNull public String getAddress() { return address == null ? "" : address; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }

    /** Reports the chosen place, or that the driver typed free text with no Google match. */
    public interface Listener {
        void onPlaceSelected(@NonNull Selection selection);

        /** The text no longer matches a Google place (they edited it after choosing). */
        void onSelectionCleared();

        /**
         * Google Place lookup could not answer (the key's project does not have the Places API
         * enabled, there is no connectivity, or the quota is spent). Defaulted to a no-op so that a
         * caller with nowhere to show the message still compiles.
         */
        default void onLookupFailed(@NonNull String message) {
        }
    }

    private static volatile boolean initialized;
    @Nullable private static PlacesClient client;

    private PlacesAutocomplete() {
    }

    /** True when Place autocomplete can run (key present and the SDK initialised). */
    public static boolean isAvailable(@NonNull Context context) {
        return GoogleConfig.isConfigured() && client(context) != null;
    }

    @Nullable
    private static synchronized PlacesClient client(@NonNull Context context) {
        if (!GoogleConfig.isConfigured()) {
            return null;
        }
        if (client == null) {
            try {
                if (!initialized) {
                    Places.initialize(context.getApplicationContext(), GoogleConfig.apiKey());
                    initialized = true;
                }
                client = Places.createClient(context.getApplicationContext());
            } catch (Exception e) {
                client = null;
            }
        }
        return client;
    }

    /**
     * Wires autocomplete onto a text field. Suggestions appear as the driver types; the chosen
     * place is resolved to coordinates in the background.
     */
    public static void bind(@NonNull Context context, @NonNull AutoCompleteTextView field,
                            @NonNull Listener listener) {
        PlacesClient places = client(context);
        if (places == null) {
            field.setHint("Search a town, shop, hotel or landmark");
            field.setOnFocusChangeListener(null);
            return;
        }
        final List<AutocompletePrediction> predictions = new ArrayList<>();
        final ArrayAdapter<AutocompletePrediction> adapter =
                new ArrayAdapter<AutocompletePrediction>(context,
                        android.R.layout.simple_list_item_2, predictions) {

            /**
             * Google has already matched these predictions against what the driver typed, so the
             * adapter hands the backend's list straight back.
             *
             * <p>{@link ArrayAdapter}'s own filter compares {@code AutocompletePrediction.toString()}
             * with the query, which never matches. It therefore emptied the list, replaced the
             * adapter's own backing list with that empty result, and reported a count of zero - so
             * the field looked like it had no suggestions at all and the drop-down never opened.
             * This pass-through keeps the adapter's list identical to {@link #predictions} and
             * reports the real count, which is what makes the drop-down appear.
             */
            @NonNull
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = predictions;
                        results.count = predictions.size();
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView,
                                @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView
                        : LayoutInflater.from(getContext()).inflate(
                                android.R.layout.simple_list_item_2, parent, false);
                AutocompletePrediction prediction = position >= 0 && position < predictions.size()
                        ? predictions.get(position) : null;
                TextView primary = view.findViewById(android.R.id.text1);
                TextView secondary = view.findViewById(android.R.id.text2);
                primary.setText(prediction == null ? "" : prediction.getPrimaryText(null));
                if (secondary != null) {
                    CharSequence detail = prediction == null
                            ? null : prediction.getSecondaryText(null);
                    secondary.setText(prediction == null ? ""
                            : (detail == null || detail.length() == 0
                            ? prediction.getFullText(null) : detail));
                }
                return view;
            }
        };
        field.setAdapter(adapter);
        field.setThreshold(2);

        // The item the user picked; cleared as soon as they edit the text again, so a stale place
        // can never be routed to silently.
        final Selection[] chosen = new Selection[1];
        final boolean[] suppress = {false};

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suppress[0]) {
                    return;
                }
                if (chosen[0] != null) {
                    chosen[0] = null;
                    listener.onSelectionCleared();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppress[0]) {
                    return;
                }
                String query = s == null ? "" : s.toString().trim();
                if (query.length() < 2) {
                    predictions.clear();
                    adapter.notifyDataSetChanged();
                    return;
                }
                FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest
                        .builder().setQuery(query).build();
                places.findAutocompletePredictions(request)
                        .addOnSuccessListener(response -> {
                            predictions.clear();
                            predictions.addAll(response.getAutocompletePredictions());
                            adapter.notifyDataSetChanged();
                            // notifyDataSetChanged() opens the drop-down through the field's own
                            // data-set observer; asking again covers the response landing while the
                            // driver is still typing, which is the normal case for a slow lookup.
                            if (!predictions.isEmpty() && field.hasFocus() && field.hasWindowFocus()) {
                                field.showDropDown();
                            }
                        })
                        .addOnFailureListener(e -> {
                            predictions.clear();
                            adapter.notifyDataSetChanged();
                            listener.onLookupFailed(describe(e));
                        });
            }
        });

        field.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= predictions.size()) {
                return;
            }
            AutocompletePrediction prediction = predictions.get(position);
            suppress[0] = true;
            field.setText(prediction.getPrimaryText(null).toString(), false);
            field.dismissDropDown();
            suppress[0] = false;

            // Field masking: only the identity, name, address and coordinates are requested, and
            // nothing else about the driver's search is kept.
            List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME,
                    Place.Field.ADDRESS, Place.Field.LAT_LNG);
            FetchPlaceRequest fetch = FetchPlaceRequest.builder(prediction.getPlaceId(), fields).build();
            places.fetchPlace(fetch).addOnSuccessListener(response -> {
                Place place = response.getPlace();
                if (place.getLatLng() == null) {
                    return;
                }
                String label = place.getName() != null
                        ? place.getName() : prediction.getPrimaryText(null).toString();
                String address = place.getAddress() != null
                        ? place.getAddress() : prediction.getFullText(null).toString();
                Selection selection = new Selection(place.getId(), label, address,
                        place.getLatLng().latitude, place.getLatLng().longitude);
                chosen[0] = selection;
                listener.onPlaceSelected(selection);
            });
        });
    }

    /** A short, actionable reason a search returned nothing at all. */
    @NonNull
    private static String describe(@NonNull Exception e) {
        String detail = e.getMessage() == null ? "" : e.getMessage().trim();
        return "Place search is unavailable"
                + (detail.isEmpty() ? " right now." : ": " + detail);
    }
}
