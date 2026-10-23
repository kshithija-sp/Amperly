package com.amperly.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.data.local.entity.ChargerStation;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PendingStationAdapter extends RecyclerView.Adapter<PendingStationAdapter.PendingViewHolder> {

    public interface Listener {
        void onApprove(ChargerStation station);
        void onReject(ChargerStation station);
    }

    private final List<ChargerStation> items = new ArrayList<>();
    private final Listener listener;

    public PendingStationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<ChargerStation> stations) {
        items.clear();
        if (stations != null) {
            items.addAll(stations);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PendingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_station, parent, false);
        return new PendingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PendingViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class PendingViewHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView host;
        private final TextView meta;
        private final MaterialButton approve;
        private final MaterialButton reject;

        PendingViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.pendingNameText);
            host = itemView.findViewById(R.id.pendingHostText);
            meta = itemView.findViewById(R.id.pendingMetaText);
            approve = itemView.findViewById(R.id.approveButton);
            reject = itemView.findViewById(R.id.rejectButton);
        }

        void bind(ChargerStation station) {
            name.setText(station.getName());
            host.setText("Host: " + (station.getHostName() == null ? station.getHostId() : station.getHostName()));
            meta.setText(station.getAddress() + "\n"
                    + station.getConnectorTypes() + " · " + (int) station.getMaxPowerKw() + " kW · "
                    + station.getPortCount() + " port(s) · " + station.getOperatingHours() + "\n"
                    + "Meter level: " + station.getVerificationLevel() + " · submitted "
                    + Formatters.agoFromNow(station.getLastUpdatedMs()) + "\nAccess: "
                    + (station.getAccessRules() == null ? "not provided" : station.getAccessRules()));
            approve.setOnClickListener(v -> {
                if (listener != null) listener.onApprove(station);
            });
            reject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(station);
            });
        }
    }
}
