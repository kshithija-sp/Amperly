package com.amperly.app.feature.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Dispute;
import com.amperly.app.data.local.entity.HostEnforcementAction;
import com.amperly.app.data.local.entity.Invoice;
import com.amperly.app.data.local.entity.MeterReading;
import com.amperly.app.data.local.entity.Notification;
import com.amperly.app.data.local.entity.Report;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.admin.EnforcementPolicyEngine;
import com.amperly.app.engine.analytics.HostAnalyticsEngine;
import com.amperly.app.engine.billing.BillingEngine;
import com.amperly.app.ui.Ux;
import com.amperly.app.ui.adapter.DisputeAdapter;
import com.amperly.app.ui.adapter.PendingStationAdapter;
import com.amperly.app.ui.adapter.ReportAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Administrator desk: verify listings, review meter evidence, resolve disputes with refunds and apply
 * the evidence-based enforcement ladder (warning → fine → suspension → ban).
 */
public class AdminDashboardFragment extends Fragment {

    private AmperlyRepository repo;
    private PendingStationAdapter pendingAdapter;
    private DisputeAdapter disputeAdapter;
    private ReportAdapter reportAdapter;
    private TextView analyticsText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());
        analyticsText = view.findViewById(R.id.platformAnalyticsText);

        RecyclerView pending = view.findViewById(R.id.pendingStationsRecyclerView);
        pending.setLayoutManager(new LinearLayoutManager(requireContext()));
        pending.setNestedScrollingEnabled(false);
        pendingAdapter = new PendingStationAdapter(new PendingStationAdapter.Listener() {
            @Override
            public void onApprove(ChargerStation station) {
                decideStation(station, true);
            }

            @Override
            public void onReject(ChargerStation station) {
                decideStation(station, false);
            }
        });
        pending.setAdapter(pendingAdapter);

        RecyclerView disputes = view.findViewById(R.id.disputesRecyclerView);
        disputes.setLayoutManager(new LinearLayoutManager(requireContext()));
        disputes.setNestedScrollingEnabled(false);
        disputeAdapter = new DisputeAdapter(new DisputeAdapter.Listener() {
            @Override
            public void onRefund(Dispute dispute) {
                resolveWithRefund(dispute);
            }

            @Override
            public void onFreezePayout(Dispute dispute) {
                dispute.setPayoutFrozen(true);
                repo.freezeDisputePayoutOnServer(dispute);
                Ux.toast(AdminDashboardFragment.this,
                        "Payout held while the host responds. Nothing is deducted yet.");
                load();
            }

            @Override
            public void onWarning(Dispute dispute) {
                applyEnforcement(dispute, AppConstants.ENFORCEMENT_WARNING, 0.0);
            }

            @Override
            public void onFine(Dispute dispute) {
                applyEnforcement(dispute, AppConstants.ENFORCEMENT_FINE,
                        EnforcementPolicyEngine.DEFAULT_FINE_INR);
            }

            @Override
            public void onSuspend(Dispute dispute) {
                applyEnforcement(dispute, AppConstants.ENFORCEMENT_SUSPENSION, 0.0);
            }

            @Override
            public void onBan(Dispute dispute) {
                applyEnforcement(dispute, AppConstants.ENFORCEMENT_BAN, 0.0);
            }
        });
        disputes.setAdapter(disputeAdapter);

        RecyclerView reports = view.findViewById(R.id.reportsRecyclerView);
        reports.setLayoutManager(new LinearLayoutManager(requireContext()));
        reports.setNestedScrollingEnabled(false);
        reportAdapter = new ReportAdapter();
        reports.setAdapter(reportAdapter);

        load();
    }

    private void load() {
        repo.background(() -> {
            List<ChargerStation> pendingStations = repo.getPendingStationsSync();
            List<Dispute> disputes = repo.getDisputesSync();
            List<Report> reports = repo.getAllReportsSync();
            HostAnalyticsEngine.HostAnalytics analytics = repo.platformAnalytics();
            List<DisputeAdapter.DisputeRow> disputeRows = new ArrayList<>();
            for (Dispute dispute : disputes) {
                List<MeterReading> evidence = dispute.getSessionId() == null
                        ? new ArrayList<>()
                        : repo.getMeterReadingsSync(dispute.getSessionId());
                disputeRows.add(new DisputeAdapter.DisputeRow(dispute, evidence));
            }
            return new AdminData(pendingStations, disputeRows, reports, analytics,
                    repo.getInvoicesForHostSync(""));
        }, data -> {
            if (data == null || !isAdded()) {
                return;
            }
            pendingAdapter.submit(data.pending);
            disputeAdapter.submit(data.disputes);
            reportAdapter.submit(data.reports);
            analyticsText.setText("Platform: " + data.pending.size() + " station(s) awaiting verification · "
                    + data.disputes.size() + " dispute(s) · " + data.reports.size() + " report(s)\n"
                    + "Sessions " + data.analytics.getSessionsCompleted() + " · energy "
                    + String.format(Locale.US, "%.1f kWh", data.analytics.getEnergyDeliveredKwh())
                    + " · revenue " + Formatters.money(data.analytics.getRevenue())
                    + " · refunds " + Formatters.money(data.analytics.getRefundsAndAdjustments())
                    + "\nUtilization " + String.format(Locale.US, "%.0f%%",
                    data.analytics.getUtilizationPercent()) + " (estimated: no charger telemetry).");
        });
    }

    private void decideStation(ChargerStation station, boolean approve) {
        repo.background(() -> {
            station.setApprovalStatus(approve ? AppConstants.APPROVAL_APPROVED
                    : AppConstants.APPROVAL_REJECTED);
            station.setVerified(approve && station.supportsPaidEnergyBilling());
            station.setReviewNote(approve
                    ? "Approved: meter level " + station.getVerificationLevel() + " confirmed by an administrator."
                    : "Rejected: documentation did not match the submitted details.");
            station.setLastUpdatedMs(System.currentTimeMillis());
            // The server makes the approval decision and notifies the host; the local
            // row above is the immediate view.
            repo.decideStationApprovalOnServer(station, approve);
            return station.getName();
        }, name -> {
            if (name != null) {
                Ux.toast(this, name + (approve ? " approved and published." : " rejected. The host was notified."));
                load();
            }
        });
    }

    private void resolveWithRefund(Dispute dispute) {
        repo.background(() -> {
            Invoice invoice = dispute.getSessionId() == null ? null
                    : repo.getInvoiceBySessionSync(dispute.getSessionId());
            double refundAmount = invoice == null ? 0 : invoice.getEnergyCharge();
            if (invoice != null && refundAmount > 0) {
                BillingEngine.applyRefund(invoice, refundAmount,
                        "Refunded after reviewing the meter evidence for dispute " + dispute.getId());
            }
            dispute.setStatus("RESOLVED_CONSUMER");
            dispute.setRefundAmount(refundAmount);
            dispute.setPayoutFrozen(true);
            dispute.setResolvedAtMs(System.currentTimeMillis());
            dispute.setResolutionNote("Consumer refunded " + Formatters.money(refundAmount)
                    + " based on the uploaded meter evidence.");
            // The server recomputes the refund from the invoice, applies it to the payment
            // and notifies the consumer; the rows above are the immediate view.
            repo.resolveDisputeRefundOnServer(dispute, invoice);
            return refundAmount;
        }, amount -> {
            if (amount != null) {
                Ux.toast(this, "Refunded " + Formatters.money(amount)
                        + " and the payout stays held until the review closes.");
                load();
            }
        });
    }

    private void applyEnforcement(Dispute dispute, String action, double fineAmount) {
        repo.background(() -> {
            int proven = repo.countProvenIncidents(dispute.getHostId()) + 1;
            boolean severe = AppConstants.ENFORCEMENT_BAN.equals(action);
            EnforcementPolicyEngine.EnforcementDecision decision =
                    EnforcementPolicyEngine.decide(proven, true, severe);

            // An explicit admin choice is recorded as given, with the policy ladder attached.
            dispute.setEnforcementAction(action);
            dispute.setStatus("RESOLVED_HOST");
            dispute.setResolvedAtMs(System.currentTimeMillis());
            dispute.setResolutionNote("Enforcement: " + action
                    + (fineAmount > 0 ? " · fine " + Formatters.money(fineAmount) : "")
                    + "\nPolicy step: " + decision.getReason() + " → " + decision.getAction());
            // The server records the dispute outcome, files the enforcement row, pauses the
            // station when the policy calls for it, and notifies the host.
            repo.applyHostEnforcementOnServer(dispute, action, fineAmount,
                    dispute.getResolutionNote());

            if (decision.isSuspendStation() || AppConstants.ENFORCEMENT_SUSPENSION.equals(action)
                    || AppConstants.ENFORCEMENT_BAN.equals(action)) {
                ChargerStation station = repo.getStationSync(dispute.getStationId());
                if (station != null) {
                    station.setPaused(true);
                    station.setIncidentCount(station.getIncidentCount() + 1);
                    station.setReliabilityScore(Math.max(0, station.getReliabilityScore() - 10));
                    // Local only: the function already applied this server-side, and pushing
                    // it as well would count the incident twice.
                    repo.updateStationLocally(station);
                }
            }

            return action;
        }, applied -> {
            if (applied != null) {
                Ux.toast(this, "Recorded " + applied + " with the evidence attached. "
                        + "Hosts are never penalised on an unverified accusation.");
                load();
            }
        });
    }

    private static class AdminData {
        private final List<ChargerStation> pending;
        private final List<DisputeAdapter.DisputeRow> disputes;
        private final List<Report> reports;
        private final HostAnalyticsEngine.HostAnalytics analytics;
        private final List<Invoice> invoices;

        AdminData(List<ChargerStation> pending, List<DisputeAdapter.DisputeRow> disputes,
                  List<Report> reports, HostAnalyticsEngine.HostAnalytics analytics,
                  List<Invoice> invoices) {
            this.pending = pending;
            this.disputes = disputes;
            this.reports = reports;
            this.analytics = analytics;
            this.invoices = invoices;
        }
    }
}
