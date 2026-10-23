package com.amperly.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.engine.availability.AvailabilityEngine;
import com.amperly.app.engine.routing.RoutePlannerEngine;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StationAdapter extends RecyclerView.Adapter<StationAdapter.StationViewHolder> {

    public interface Listener {
        void onOpenStation(ChargerStation station);
        void onNavigateToStation(ChargerStation station);
    }

    public static class StationRow {
        private final ChargerStation station;
        private final AvailabilityEngine.AvailabilitySummary availability;
        private final String extraLine;

        public StationRow(ChargerStation station, AvailabilityEngine.AvailabilitySummary availability,
                          String extraLine) {
            this.station = station;
            this.availability = availability;
            this.extraLine = extraLine;
        }

        public ChargerStation getStation() { return station; }
        public AvailabilityEngine.AvailabilitySummary getAvailability() { return availability; }
        public String getExtraLine() { return extraLine; }
    }

    private final List<StationRow> rows = new ArrayList<>();
    private final Listener listener;
    private final com.amperly.app.data.local.entity.Vehicle vehicle;

    public StationAdapter(Listener listener, com.amperly.app.data.local.entity.Vehicle vehicle) {
        this.listener = listener;
        this.vehicle = vehicle;
    }

    public void submit(List<StationRow> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_station, parent, false);
        return new StationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StationViewHolder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    class StationViewHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView address;
        private final TextView type;
        private final TextView badge;
        private final TextView availability;
        private final TextView freshness;
        private final TextView price;
        private final TextView compatibility;
        private final MaterialButton details;
        private final MaterialButton navigate;

        StationViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.stationNameText);
            address = itemView.findViewById(R.id.stationAddressText);
            type = itemView.findViewById(R.id.stationTypeText);
            badge = itemView.findViewById(R.id.verificationBadgeText);
            availability = itemView.findViewById(R.id.availabilityText);
            freshness = itemView.findViewById(R.id.freshnessText);
            price = itemView.findViewById(R.id.priceText);
            compatibility = itemView.findViewById(R.id.compatibilityText);
            details = itemView.findViewById(R.id.viewDetailsButton);
            navigate = itemView.findViewById(R.id.navigateButton);
        }

        void bind(StationRow row) {
            ChargerStation station = row.getStation();
            name.setText(station.getName());
            address.setText(station.getAddress());
            type.setText(locationTypeLabel(station.getLocationType()) + " · "
                    + station.getConnectorTypes() + " · " + (int) station.getMaxPowerKw() + " kW"
                    + (station.isDcCapable() ? " DC" : " AC"));

            // Rounded pills, and colour that matches meaning: a station with no verified
            // meter must not carry the same green badge as a verified one.
            if (station.supportsPaidEnergyBilling()) {
                badge.setText("LEVEL 1" + (AppConstants.LEVEL_2_SIMULATED_SMART_METER.equals(station.getVerificationLevel()) ? "/2 METER" : " METER"));
                badge.setBackgroundResource(R.drawable.bg_badge);
                badge.setTextColor(androidx.core.content.ContextCompat.getColor(
                        badge.getContext(), R.color.amp_badge_text));
            } else {
                badge.setText("NO METER");
                badge.setBackgroundResource(R.drawable.bg_badge_warning);
                badge.setTextColor(androidx.core.content.ContextCompat.getColor(
                        badge.getContext(), R.color.amp_on_tertiary_container));
            }

            if (row.getAvailability() != null) {
                availability.setText(row.getAvailability().getLabel());
                freshness.setText("Availability last updated "
                        + Formatters.agoFromNow(row.getAvailability().getLastUpdatedMs()) + ".");
            } else {
                availability.setText(station.isPaused() ? "Paused by the host" : "Availability unknown");
                freshness.setText("Availability last updated " + Formatters.agoFromNow(station.getLastUpdatedMs()) + ".");
            }

            price.setText(Formatters.money(station.getPricePerKwh()) + " / kWh  + "
                    + Formatters.money(station.getServiceFee()) + " service + "
                    + Formatters.money(station.getGatewayFee()) + " gateway");

            boolean compatible = RoutePlannerEngine.isConnectorCompatible(station, vehicle);
            StringBuilder compatibilityText = new StringBuilder();
            compatibilityText.append(compatible ? "Compatible with your vehicle" : "Connector mismatch");
            compatibilityText.append(" · reliability ").append((int) station.getReliabilityScore()).append("%");
            if (station.getIncidentCount() > 0) {
                compatibilityText.append(" · ").append(station.getIncidentCount()).append(" incident(s)");
            }
            if (row.getExtraLine() != null && !row.getExtraLine().isEmpty()) {
                compatibilityText.append("\n").append(row.getExtraLine());
            }
            compatibility.setText(compatibilityText.toString());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpenStation(station);
                }
            });
            details.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpenStation(station);
                }
            });
            navigate.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNavigateToStation(station);
                }
            });
        }

        private String locationTypeLabel(String type) {
            if (type == null) {
                return "Charger";
            }
            switch (type) {
                case AppConstants.LOCATION_TYPE_CAMPUS: return "Campus";
                case AppConstants.LOCATION_TYPE_APARTMENT: return "Apartment";
                case AppConstants.LOCATION_TYPE_OFFICE: return "Office";
                case AppConstants.LOCATION_TYPE_CAFE: return "Café";
                case AppConstants.LOCATION_TYPE_HOTEL: return "Hotel";
                case AppConstants.LOCATION_TYPE_PARKING: return "Parking";
                case AppConstants.LOCATION_TYPE_PETROL_PUMP: return "Petrol pump";
                default: return String.format(Locale.US, "%s",
                        type.substring(0, 1) + type.substring(1).toLowerCase(Locale.US));
            }
        }
    }
}
