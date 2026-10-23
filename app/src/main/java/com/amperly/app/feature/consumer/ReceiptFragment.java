package com.amperly.app.feature.consumer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.data.local.entity.ChargingSession;
import com.amperly.app.data.local.entity.Invoice;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.ui.Ux;
import com.amperly.app.ui.dialog.ReportDialog;

import java.util.Locale;
import java.util.UUID;

public class ReceiptFragment extends Fragment {

    public static final String ARG_SESSION_ID = "session_id";

    private AmperlyRepository repo;
    private LinearLayout container;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_receipt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());
        container = view.findViewById(R.id.receiptContainer);

        String sessionId = getArguments() == null ? null : getArguments().getString(ARG_SESSION_ID);

        repo.background(() -> {
            ChargingSession session = sessionId == null ? null : repo.getSessionSync(sessionId);
            if (session == null) {
                session = repo.getSessionByReservationSync(com.amperly.app.core.state.AppStateStore
                        .get().getActiveReservationId());
            }
            Invoice invoice = session == null ? null : repo.getInvoiceBySessionSync(session.getId());
            return new ReceiptData(session, invoice);
        }, data -> {
            if (data == null || !isAdded()) {
                return;
            }
            render(view, data);
        });
    }

    private void render(View view, ReceiptData data) {
        TextView title = view.findViewById(R.id.receiptTitleText);
        TextView stationText = view.findViewById(R.id.receiptStationText);
        TextView totalText = view.findViewById(R.id.receiptTotalText);

        if (data.session == null) {
            title.setText("No receipt yet");
            stationText.setText("Complete a charging session to get an itemised receipt based on verified meter readings.");
            return;
        }

        stationText.setText((data.session.getStationName() == null ? data.session.getStationId()
                : data.session.getStationName())
                + "\n" + Formatters.dateTime(data.session.getStartTimeMs())
                + (data.session.isMeterSimulated()
                ? "\nDemo meter — readings are simulated or manually entered, not read from live hardware."
                : "\nVerified meter readings."));

        container.removeAllViews();
        addLine("Starting meter reading", String.format(Locale.US, "%.2f kWh", data.session.getStartingMeterKwh()));
        addLine("Ending meter reading", String.format(Locale.US, "%.2f kWh", data.session.getEndingMeterKwh()));
        addLine("Verified energy consumed", Formatters.kwh(data.session.getVerifiedKwhConsumed()));
        addLine("Energy charge", Formatters.money(data.session.getEnergyCharge()));
        addLine("Amperly service fee", Formatters.money(data.session.getServiceFee()));
        addLine("Payment gateway charge", Formatters.money(data.session.getGatewayFee()));
        if (data.invoice != null && data.invoice.getAdjustment() > 0) {
            addLine("Refund or adjustment", "-" + Formatters.money(data.invoice.getAdjustment()));
        }
        totalText.setText("Final amount " + Formatters.money(data.session.getTotalPaid()));

        view.findViewById(R.id.reportIssueButton).setOnClickListener(v ->
                ReportDialog.show(this, getString(R.string.report_title), null, (type, details) -> {
                    repo.background(() -> {
                        com.amperly.app.data.local.entity.Report report =
                                new com.amperly.app.data.local.entity.Report(UUID.randomUUID().toString(),
                                        AuthManager.getInstance().getCurrentUserId(),
                                        AuthManager.getInstance().getCurrentUser().getName(),
                                        data.session.getStationId(), data.session.getHostId(),
                                        data.session.getId(), type,
                                        details == null || details.isEmpty() ? "(no details provided)" : details,
                                        "OPEN", System.currentTimeMillis());
                        repo.saveReport(report);
                        return Boolean.TRUE;
                    }, ok -> Ux.toast(this, getString(R.string.report_submitted)));
                }));
    }

    private void addLine(String label, String value) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_billing_line, container, false);
        ((TextView) row.findViewById(R.id.lineLabelText)).setText(label);
        ((TextView) row.findViewById(R.id.lineValueText)).setText(value);
        container.addView(row);
    }

    private static class ReceiptData {
        private final ChargingSession session;
        private final Invoice invoice;

        ReceiptData(ChargingSession session, Invoice invoice) {
            this.session = session;
            this.invoice = invoice;
        }
    }
}
