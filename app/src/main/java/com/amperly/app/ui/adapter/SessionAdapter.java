package com.amperly.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.data.local.entity.ChargingSession;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    public interface Listener {
        void onViewReceipt(ChargingSession session);
        void onReportSession(ChargingSession session);
    }

    private final List<ChargingSession> items = new ArrayList<>();
    private final Listener listener;

    public SessionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<ChargingSession> sessions) {
        items.clear();
        if (sessions != null) {
            items.addAll(sessions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class SessionViewHolder extends RecyclerView.ViewHolder {
        private final TextView station;
        private final TextView status;
        private final TextView meta;
        private final TextView kwh;
        private final TextView total;
        private final MaterialButton receipt;
        private final MaterialButton dispute;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            station = itemView.findViewById(R.id.sessionStationText);
            status = itemView.findViewById(R.id.sessionStatusText);
            meta = itemView.findViewById(R.id.sessionMetaText);
            kwh = itemView.findViewById(R.id.sessionKwhText);
            total = itemView.findViewById(R.id.sessionTotalText);
            receipt = itemView.findViewById(R.id.viewReceiptButton);
            dispute = itemView.findViewById(R.id.disputeSessionButton);
        }

        void bind(ChargingSession session) {
            station.setText(session.getStationName() == null ? session.getStationId() : session.getStationName());
            status.setText(session.getStatus());
            meta.setText(Formatters.dateTime(session.getStartTimeMs())
                    + (session.isMeterSimulated() ? " · demo meter" : " · verified meter")
                    + (session.getVerificationLevel() == null ? "" : " (" + session.getVerificationLevel() + ")"));
            kwh.setText("Start " + String.format(java.util.Locale.US, "%.2f", session.getStartingMeterKwh())
                    + " → End " + String.format(java.util.Locale.US, "%.2f", session.getEndingMeterKwh())
                    + " = " + Formatters.kwh(session.getVerifiedKwhConsumed()));
            total.setText(Formatters.money(session.getTotalPaid()));
            receipt.setOnClickListener(v -> {
                if (listener != null) listener.onViewReceipt(session);
            });
            dispute.setOnClickListener(v -> {
                if (listener != null) listener.onReportSession(session);
            });
        }
    }
}
