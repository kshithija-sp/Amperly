package com.amperly.app.feature.host;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.data.local.entity.AvailabilitySchedule;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.ChargingSession;
import com.amperly.app.data.local.entity.Dispute;
import com.amperly.app.data.local.entity.Invoice;
import com.amperly.app.data.local.entity.MeterReading;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.analytics.HostAnalyticsEngine;
import com.amperly.app.engine.billing.BillingEngine;
import com.amperly.app.engine.meter.MeterSimulator;
import com.amperly.app.ui.Ux;
import com.amperly.app.ui.adapter.ReservationAdapter;
import com.amperly.app.ui.adapter.SessionAdapter;
import com.amperly.app.ui.dialog.MeterEvidenceDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The host's operational view: live availability, reservations, sessions with meter evidence, and
 * utilisation/revenue analytics. Hosts never see consumer personal data beyond the reservation.
 */
public class HostDashboardFragment extends Fragment {

    private AmperlyRepository repo;
    private LinearLayout analyticsContainer;
    private TextView emptyText;
    private String hostId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_host_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());
        hostId = AuthManager.getInstance().getCurrentUserId();

        analyticsContainer = view.findViewById(R.id.analyticsContainer);
        emptyText = view.findViewById(R.id.hostEmptyText);

        ((TextView) view.findViewById(R.id.hostNameText)).setText(
                AuthManager.getInstance().getCurrentUser().getName() + " · host dashboard");

        RecyclerView stations = view.findViewById(R.id.stationsRecyclerView);
        stations.setLayoutManager(new LinearLayoutManager(requireContext()));
        stations.setNestedScrollingEnabled(false);

        RecyclerView reservations = view.findViewById(R.id.reservationsRecyclerView);
        reservations.setLayoutManager(new LinearLayoutManager(requireContext()));
        reservations.setNestedScrollingEnabled(false);

        RecyclerView sessions = view.findViewById(R.id.sessionsRecyclerView);
        sessions.setLayoutManager(new LinearLayoutManager(requireContext()));
        sessions.setNestedScrollingEnabled(false);

        final ReservationAdapter reservationAdapter = new ReservationAdapter(new ReservationAdapter.Listener() {
            @Override
            public void onOpenQr(Reservation reservation) {
                hostScanBooking(reservation);
            }

            @Override
            public void onCheckIn(Reservation reservation) {
                hostStartSession(reservation);
            }

            @Override
            public void onMessageHost(Reservation reservation) {
                Navigator.openRoot(requireActivity(), new com.amperly.app.feature.consumer.HostChatFragment(), "chat");
            }

            @Override
            public void onCancel(Reservation reservation) {
                releaseSlot(reservation);
            }
        });
        reservations.setAdapter(reservationAdapter);

        final SessionAdapter sessionAdapter = new SessionAdapter(new SessionAdapter.Listener() {
            @Override
            public void onViewReceipt(ChargingSession session) {
                Ux.toast(HostDashboardFragment.this, "Receipt for " + session.getStationName() + ": "
                        + Formatters.money(session.getTotalPaid()) + " ("
                        + Formatters.kwh(session.getVerifiedKwhConsumed()) + " verified)");
            }

            @Override
            public void onReportSession(ChargingSession session) {
                Ux.toast(HostDashboardFragment.this, "This session is linked to a dispute if one is open.");
            }
        });
        sessions.setAdapter(sessionAdapter);

        view.findViewById(R.id.registerNewStationButton).setOnClickListener(v ->
                Navigator.openRoot(requireActivity(), new RegisterStationFragment(), "register"));

        load(reservationAdapter, sessionAdapter, stations);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void load(ReservationAdapter reservationAdapter, SessionAdapter sessionAdapter,
                      RecyclerView stationsView) {
        repo.background(() -> {
            List<ChargerStation> stations = repo.getStationsForHostSync(hostId);
            List<Reservation> reservations = repo.getReservationsForHostSync(hostId);
            List<ChargingSession> sessions = repo.getSessionsForHostSync(hostId);
            HostAnalyticsEngine.HostAnalytics analytics = repo.hostAnalytics(hostId);
            return new HostData(stations, reservations, sessions, analytics);
        }, (HostData data) -> {
            if (data == null || !isAdded()) {
                return;
            }
            bindStats(data.analytics);
            bindStations(stationsView, data.stations);
            reservationAdapter.submit(data.reservations);
            sessionAdapter.submit(data.sessions);
            bindAnalytics(data.analytics);
        });
    }

    private void bindStats(HostAnalyticsEngine.HostAnalytics analytics) {
        View view = getView();
        if (view == null) {
            return;
        }
        ((TextView) view.findViewById(R.id.statsSessionsText)).setText(String.valueOf(analytics.getSessionsCompleted()));
        ((TextView) view.findViewById(R.id.statsEnergyText)).setText(
                String.format(Locale.US, "%.1f", analytics.getEnergyDeliveredKwh()));
        ((TextView) view.findViewById(R.id.statsUtilizationText)).setText(
                Formatters.percent(analytics.getUtilizationPercent()));
        ((TextView) view.findViewById(R.id.statsRevenueText)).setText(
                Formatters.moneyShort(analytics.getRevenue()));
    }

    private void bindAnalytics(HostAnalyticsEngine.HostAnalytics analytics) {
        analyticsContainer.removeAllViews();
        addRow("Repeat consumers", String.valueOf(analytics.getRepeatConsumers()));
        addRow("Cancellation rate", Formatters.percent(analytics.getCancellationRatePercent()));
        addRow("Average session size", Formatters.kwh(analytics.getAverageSessionKwh()));
        addRow("Disputes", analytics.getDisputes() + " (" + analytics.getOpenDisputes() + " open)");
        addRow("Refunds and adjustments", Formatters.money(analytics.getRefundsAndAdjustments()));
        addRow("Active reservations", String.valueOf(analytics.getActiveReservations()));
        addRow("Ports published", String.valueOf(analytics.getPorts()));
        ((TextView) requireView().findViewById(R.id.analyticsNoteText)).setText(analytics.getUtilizationNote());
    }

    private void addRow(String label, String value) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_billing_line, analyticsContainer, false);
        ((TextView) row.findViewById(R.id.lineLabelText)).setText(label);
        ((TextView) row.findViewById(R.id.lineValueText)).setText(value);
        analyticsContainer.addView(row);
    }

    private void bindStations(RecyclerView recyclerView, List<ChargerStation> stations) {
        emptyText.setVisibility(stations.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<StationHolder>() {
            @NonNull
            @Override
            public StationHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_host_station, parent, false);
                return new StationHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull StationHolder holder, int position) {
                holder.bind(stations.get(position));
            }

            @Override
            public int getItemCount() {
                return stations.size();
            }
        });
    }

    class StationHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView meta;
        private final TextView status;
        private final com.google.android.material.button.MaterialButton pause;
        private final com.google.android.material.button.MaterialButton evidence;

        StationHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.hostStationNameText);
            meta = itemView.findViewById(R.id.hostStationMetaText);
            status = itemView.findViewById(R.id.hostStationStatusText);
            pause = itemView.findViewById(R.id.pauseStationButton);
            evidence = itemView.findViewById(R.id.meterEvidenceButton);
        }

        void bind(ChargerStation station) {
            name.setText(station.getName());
            meta.setText(station.getAddress() + "\n" + station.getConnectorTypes() + " · "
                    + (int) station.getMaxPowerKw() + " kW · " + station.getPortCount() + " port(s) · "
                    + Formatters.money(station.getPricePerKwh()) + "/kWh");
            status.setText("Approval: " + station.getApprovalStatus()
                    + " · meter: " + station.getVerificationLevel()
                    + " · availability updated " + Formatters.agoFromNow(station.getLastUpdatedMs()));
            pause.setText(station.isPaused() ? R.string.host_resume_station : R.string.host_pause_station);
            pause.setOnClickListener(v -> togglePause(station));
            evidence.setOnClickListener(v -> captureEvidence(station));
        }
    }

    private void togglePause(ChargerStation station) {
        boolean newState = !station.isPaused();
        station.setPaused(newState);
        station.setLastUpdatedMs(System.currentTimeMillis());
        repo.updateStation(station);
        repo.blockStation(station.getId(), newState, newState ? "Paused by the host" : null);
        Ux.toast(this, newState ? "Station paused. Consumers can no longer reserve it."
                : "Station reopened. Availability is refreshed for consumers.");
        Navigator.openRoot(requireActivity(), new HostDashboardFragment(), "host_dashboard");
    }

    private void captureEvidence(ChargerStation station) {
        MeterEvidenceDialog.show(this, station.getName(), station.getId(),
                MeterSimulator.currentReading(station.getId()), (start, end, note) -> {
                    if (!MeterSimulator.isValidReading(start, end)) {
                        Ux.toast(this, getString(R.string.session_invalid_reading));
                        return;
                    }
                    if (MeterSimulator.isOffline(station.getId())) {
                        Ux.toast(this, getString(R.string.session_meter_offline));
                    }
                    MeterSimulator.setReading(station.getId(), end);
                    repo.background(() -> {
                        MeterReading reading = new MeterReading(UUID.randomUUID().toString(),
                                null, station.getId(), start, end, null,
                                station.getVerificationLevel(), System.currentTimeMillis(), true);
                        repo.saveMeterReading(reading);
                        return Boolean.TRUE;
                    }, ok -> Ux.toast(this, "Meter evidence saved ("
                            + String.format(Locale.US, "%.2f kWh", end - start)
                            + "). Readings are labelled as simulated or manually entered."));
                });
    }

    private void hostScanBooking(Reservation reservation) {
        Ux.toast(this, "Booking " + reservation.getBookingCode() + " verified for "
                + reservation.getStationName() + ". Consumer: " + reservation.getConsumerId());
    }

    private void hostStartSession(Reservation reservation) {
        repo.background(() -> {
            double start = MeterSimulator.beginSession(reservation.getStationId());
            reservation.setStatus(AppConstants.STATUS_CHECKED_IN);
            reservation.setCheckedInAtMs(System.currentTimeMillis());
            repo.updateReservation(reservation);

            ChargerStation station = repo.getStationSync(reservation.getStationId());
            ChargingSession session = new ChargingSession(UUID.randomUUID().toString(), reservation.getId(),
                    reservation.getConsumerId(), hostId, reservation.getStationId(),
                    reservation.getStationName(), reservation.getPortId(), System.currentTimeMillis(),
                    0, start, 0, 0, 0, 0, 0, 0, AppConstants.STATUS_CHARGING, null, null, true,
                    station == null ? AppConstants.LEVEL_1_VERIFIED_MANUAL : station.getVerificationLevel());
            repo.saveSession(session);
            return start;
        }, start -> {
            if (start != null) {
                Ux.toast(this, "Session started. Starting reading "
                        + String.format(Locale.US, "%.2f kWh", start) + " (demo meter).");
                Navigator.openRoot(requireActivity(), new HostDashboardFragment(), "host_dashboard");
            }
        });
    }

    private void releaseSlot(Reservation reservation) {
        repo.background(() -> {
            BillingEngine.CancellationResult result = BillingEngine.calculateCancellation(reservation,
                    System.currentTimeMillis());
            reservation.setStatus(AppConstants.STATUS_CANCELLED);
            reservation.setCancelledAtMs(System.currentTimeMillis());
            reservation.setCancellationFee(result.getFee());
            repo.updateReservation(reservation);
            List<ChargerPort> ports = repo.getPortsSync(reservation.getStationId());
            for (ChargerPort port : ports) {
                if (port.getId().equals(reservation.getPortId())) {
                    port.setStatus(AppConstants.PORT_AVAILABLE);
                    repo.database().chargerPortDao().update(port);
                }
            }
            return result.getMessage();
        }, message -> {
            if (message != null) {
                Ux.toast(this, message);
                Navigator.openRoot(requireActivity(), new HostDashboardFragment(), "host_dashboard");
            }
        });
    }

    /** Everything the host dashboard needs for one render pass, loaded off the main thread. */
    private static class HostData {
        final List<ChargerStation> stations;
        final List<Reservation> reservations;
        final List<ChargingSession> sessions;
        final HostAnalyticsEngine.HostAnalytics analytics;

        HostData(List<ChargerStation> stations, List<Reservation> reservations,
                 List<ChargingSession> sessions, HostAnalyticsEngine.HostAnalytics analytics) {
            this.stations = stations;
            this.reservations = reservations;
            this.sessions = sessions;
            this.analytics = analytics;
        }
    }
}
