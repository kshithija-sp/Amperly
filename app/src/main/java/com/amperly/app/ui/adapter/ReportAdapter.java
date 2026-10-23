package com.amperly.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.data.local.entity.Report;

import java.util.ArrayList;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private final List<Report> items = new ArrayList<>();

    public void submit(List<Report> reports) {
        items.clear();
        if (reports != null) {
            items.addAll(reports);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        private final TextView type;
        private final TextView meta;
        private final TextView description;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            type = itemView.findViewById(R.id.reportTypeText);
            meta = itemView.findViewById(R.id.reportMetaText);
            description = itemView.findViewById(R.id.reportDescriptionText);
        }

        void bind(Report report) {
            type.setText(report.getReportType() + " · " + report.getStatus());
            meta.setText((report.getReporterName() == null ? report.getReporterId() : report.getReporterName())
                    + " · station " + report.getTargetStationId()
                    + " · " + Formatters.agoFromNow(report.getCreatedAtMs()));
            description.setText(report.getDescription());
        }
    }
}
