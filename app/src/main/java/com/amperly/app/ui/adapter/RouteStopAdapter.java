package com.amperly.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.engine.routing.RoutePlannerEngine;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows the trade-off behind every charging option instead of an unexplained recommendation.
 */
public class RouteStopAdapter extends RecyclerView.Adapter<RouteStopAdapter.StopViewHolder> {

    public interface Listener {
        void onReserveStop(String stationId, String stationName);
    }

    public static class StopRow {
        private final String badge;
        private final String sequenceLabel;
        private final String name;
        private final String detail;
        private final String reason;
        private final boolean reservable;
        private final String stationId;

        public StopRow(String badge, String sequenceLabel, String name, String detail, String reason,
                       boolean reservable, String stationId) {
            this.badge = badge;
            this.sequenceLabel = sequenceLabel;
            this.name = name;
            this.detail = detail;
            this.reason = reason;
            this.reservable = reservable;
            this.stationId = stationId;
        }

        public String getStationId() { return stationId; }
    }

    private final List<StopRow> rows = new ArrayList<>();
    private final Listener listener;

    public RouteStopAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<StopRow> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    public static StopRow fromCandidate(RoutePlannerEngine.CandidateEvaluation evaluation,
                                       int sequence, boolean planned) {
        String badge = planned ? "PLANNED" : evaluation.isReachable() ? "CANDIDATE" : "UNREACHABLE";
        String detail = String.format(Locale.US, "%s · %.1f km along the route · +%.1f km detour (+%d min) · %s/kWh",
                evaluation.getStation().getName().isEmpty() ? "Charger" : evaluation.getStation().getLocationType(),
                evaluation.getDistanceAlongRouteKm(), evaluation.getDetourKm(),
                evaluation.getDetourMinutes(), Formatters.moneyShort(evaluation.getStation().getPricePerKwh()));
        return new StopRow(badge, sequence > 0 ? String.valueOf(sequence) : "★",
                evaluation.getStation().getName(), detail, evaluation.getReason(),
                evaluation.isReachable(), evaluation.getStation().getId());
    }

    public static StopRow fromPlannedStop(RoutePlannerEngine.PlannedStop stop) {
        String badge = stop.isBackup() ? "BACKUP" : "PLANNED";
        String detail = String.format(Locale.US, "%.1f km along the route · +%.1f km detour (+%d min) · %d min charge · %s",
                stop.getDistanceAlongRouteKm(), stop.getDetourKm(), stop.getDetourMinutes(),
                stop.getChargingMinutes(), Formatters.moneyShort(stop.getEstimatedCost()));
        return new StopRow(badge, stop.isBackup() ? "•" : String.valueOf(stop.getSequence()),
                stop.getStation().getName(), detail, stop.getRecommendationReason(), false,
                stop.getStation().getId());
    }

    @NonNull
    @Override
    public StopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route_stop, parent, false);
        return new StopViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StopViewHolder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    class StopViewHolder extends RecyclerView.ViewHolder {
        private final TextView sequence;
        private final TextView name;
        private final TextView badge;
        private final TextView detail;
        private final TextView reason;
        private final MaterialButton reserve;

        StopViewHolder(@NonNull View itemView) {
            super(itemView);
            sequence = itemView.findViewById(R.id.stopSequenceText);
            name = itemView.findViewById(R.id.stopNameText);
            badge = itemView.findViewById(R.id.stopBadgeText);
            detail = itemView.findViewById(R.id.stopDetailText);
            reason = itemView.findViewById(R.id.stopReasonText);
            reserve = itemView.findViewById(R.id.reserveStopButton);
        }

        void bind(StopRow row) {
            sequence.setText(row.sequenceLabel);
            name.setText(row.name);
            badge.setText(row.badge);
            detail.setText(row.detail);
            reason.setText(row.reason);
            reserve.setVisibility(row.reservable ? View.VISIBLE : View.GONE);
            reserve.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReserveStop(row.getStationId(), row.name);
                }
            });
        }
    }
}
