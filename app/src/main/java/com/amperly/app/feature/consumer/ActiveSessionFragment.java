package com.amperly.app.feature.consumer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amperly.app.R;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.ChargingSession;
import com.amperly.app.data.local.entity.MeterReading;
import com.amperly.app.data.local.entity.Payment;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.billing.BillingEngine;
import com.amperly.app.engine.meter.MeterSimulator;
import com.amperly.app.payment.PaymentManager;
import com.amperly.app.ui.Ux;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The charging session: check in, capture the starting reading, let the demo meter deliver energy,
 * capture the ending reading and bill only the verified kWh.
 */
public class ActiveSessionFragment extends Fragment {

    public static final String ARG_RESERVATION_ID = "reservation_id";

    private AmperlyRepository repo;
    private EditText startMeterEdit;
    private EditText endMeterEdit;
    private TextView stateText;
    private TextView infoText;
    private TextView stepsText;
    private TextView simulateResultText;
    private TextView summaryText;

    private Reservation reservation;
    private ChargerStation station;
    private ChargerPort port;
    private ChargingSession session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_active_session, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());

        stateText = view.findViewById(R.id.sessionStateText);
        infoText = view.findViewById(R.id.sessionInfoText);
        stepsText = view.findViewById(R.id.sessionStepsText);
        simulateResultText = view.findViewById(R.id.simulateResultText);
        summaryText = view.findViewById(R.id.sessionSummaryText);
        startMeterEdit = view.findViewById(R.id.startMeterEdit);
        endMeterEdit = view.findViewById(R.id.endMeterEdit);

        stepsText.setText("Reserved → Checked in → Charging → Completed\n"
                + "Other possible states: Cancelled, Expired, Disputed, Refunded");

        Button captureStart = view.findViewById(R.id.captureStartButton);
        Button simulate = view.findViewById(R.id.simulateButton);
        Button complete = view.findViewById(R.id.completeSessionButton);
        Button startCharging = view.findViewById(R.id.startChargingButton);

        captureStart.setText(R.string.session_check_in);

        String reservationId = getArguments() == null ? null
                : getArguments().getString(ARG_RESERVATION_ID);
        if (reservationId == null) {
            reservationId = AppStateStore.get().getActiveReservationId();
        }
        final String resolvedId = TextUtils.isEmpty(reservationId) ? null : reservationId;

        captureStart.setOnClickListener(v -> checkIn(resolvedId));
        startCharging.setOnClickListener(v -> markCharging(resolvedId));
        simulate.setOnClickListener(v -> simulateMeter());
        complete.setOnClickListener(v -> completeSession(resolvedId));

        loadState(resolvedId);
    }

    private void loadState(String reservationId) {
        repo.background(() -> {
            String userId = AuthManager.getInstance().getCurrentUserId();
            Reservation active = reservationId == null ? repo.getActiveReservationSync(userId)
                    : repo.getReservationSync(reservationId);
            if (active == null) {
                return new SessionState(null, null, null, null);
            }
            ChargerStation loadedStation = repo.getStationSync(active.getStationId());
            ChargerPort loadedPort = active.getPortId() == null ? null
                    : repo.database().chargerPortDao().getPortByIdSync(active.getPortId());
            ChargingSession existing = repo.getSessionByReservationSync(active.getId());
            return new SessionState(active, loadedStation, loadedPort, existing);
        }, state -> {
            if (state == null || !isAdded()) {
                return;
            }
            reservation = state.reservation;
            station = state.station;
            port = state.port;
            session = state.session;
            renderState();
        });
    }

    private void renderState() {
        if (reservation == null) {
            stateText.setText("No active reservation");
            infoText.setText("Reserve a port first. Amperly keeps the reservation, the meter evidence and the bill linked together.");
            startMeterEdit.setEnabled(false);
            endMeterEdit.setEnabled(false);
            return;
        }
        stateText.setText("State: " + reservation.getStatus());
        infoText.setText(reservation.getStationName() + " · " + reservation.getPortLabel() + "\n"
                + Formatters.dayAndSlot(reservation.getStartTimeMs(), reservation.getEndTimeMs()) + "\n"
                + "Booking code " + reservation.getBookingCode()
                + (station == null ? "" : "\nMeter level: " + station.getVerificationLevel()
                + " · paid energy billing " + (station.supportsPaidEnergyBilling() ? "enabled" : "not available")));

        if (session != null) {
            startMeterEdit.setText(String.format(Locale.US, "%.2f", session.getStartingMeterKwh()));
            if (session.getEndingMeterKwh() > 0) {
                endMeterEdit.setText(String.format(Locale.US, "%.2f", session.getEndingMeterKwh()));
            }
            summaryText.setText("Session " + session.getStatus() + " · verified "
                    + Formatters.kwh(session.getVerifiedKwhConsumed()) + " · billed "
                    + Formatters.money(session.getTotalPaid()));
        } else {
            startMeterEdit.setHint("Starting reading, e.g. " + String.format(Locale.US, "%.2f",
                    MeterSimulator.currentReading(reservation.getStationId())));
        }
    }

    private void checkIn(String reservationId) {
        repo.background(() -> {
            Reservation active = reservationId == null
                    ? repo.getActiveReservationSync(AuthManager.getInstance().getCurrentUserId())
                    : repo.getReservationSync(reservationId);
            if (active == null) {
                return "No reservation to check in.";
            }
            ChargerStation loadedStation = repo.getStationSync(active.getStationId());
            ChargerPort loadedPort = active.getPortId() == null ? null
                    : repo.database().chargerPortDao().getPortByIdSync(active.getPortId());

            double startReading = MeterSimulator.beginSession(active.getStationId());
            active.setStatus(AppConstants.STATUS_CHECKED_IN);
            active.setCheckedInAtMs(System.currentTimeMillis());
            repo.updateReservation(active);

            ChargingSession newSession = new ChargingSession(UUID.randomUUID().toString(),
                    active.getId(), active.getConsumerId(), loadedStation == null ? "" : loadedStation.getHostId(),
                    active.getStationId(), active.getStationName(), active.getPortId(),
                    System.currentTimeMillis(), 0, startReading, 0, 0, 0, 0, 0, 0,
                    AppConstants.STATUS_CHECKED_IN, null, null, true,
                    loadedStation == null ? AppConstants.LEVEL_1_VERIFIED_MANUAL : loadedStation.getVerificationLevel());
            repo.saveSession(newSession);
            AppStateStore.get().setActiveSessionId(newSession.getId());

            MeterReading reading = new MeterReading(UUID.randomUUID().toString(), newSession.getId(),
                    active.getStationId(), startReading, startReading, null,
                    newSession.getVerificationLevel(), System.currentTimeMillis(), true);
            repo.saveMeterReading(reading);
            newSession.setMeterReadingId(reading.getId());
            repo.updateSession(newSession);

            if (loadedPort != null) {
                loadedPort.setStatus(AppConstants.PORT_OCCUPIED);
                repo.database().chargerPortDao().update(loadedPort);
            }
            return "Checked in. Starting reading captured: "
                    + String.format(Locale.US, "%.2f kWh", startReading)
                    + " (demo meter).";
        }, message -> {
            if (message != null) {
                Ux.toast(this, message);
                loadState(reservationId);
            }
        });
    }

    private void markCharging(String reservationId) {
        repo.background(() -> {
            ChargingSession active = repo.getSessionByReservationSync(reservationId);
            if (active == null) {
                return "Check in first to open a session.";
            }
            active.setStatus(AppConstants.STATUS_CHARGING);
            repo.updateSession(active);
            return "Session marked as charging.";
        }, message -> {
            if (message != null) {
                Ux.toast(this, message);
                loadState(reservationId);
            }
        });
    }

    private void simulateMeter() {
        if (reservation == null) {
            Ux.toast(this, "Check in first.");
            return;
        }
        double power = port == null ? 7.2 : port.getPowerKw();
        MeterSimulator.SimulatedSession simulated = MeterSimulator.simulateSession(
                reservation.getStationId(), power, 60);
        startMeterEdit.setText(String.format(Locale.US, "%.2f", simulated.getStartReadingKwh()));
        endMeterEdit.setText(String.format(Locale.US, "%.2f", simulated.getEndReadingKwh()));
        simulateResultText.setText(getString(R.string.session_simulate_delivers, simulated.getDeliveredKwh())
                + " over " + simulated.getMinutes() + " minutes at " + (int) power
                + " kW. Readings are simulated, not read from live hardware.");
    }

    private void completeSession(String reservationId) {
        if (TextUtils.isEmpty(startMeterEdit.getText()) || TextUtils.isEmpty(endMeterEdit.getText())) {
            Ux.toast(this, "Enter both meter readings, or use Simulate meter.");
            return;
        }
        final double start;
        final double end;
        try {
            start = Double.parseDouble(startMeterEdit.getText().toString().trim());
            end = Double.parseDouble(endMeterEdit.getText().toString().trim());
        } catch (NumberFormatException e) {
            Ux.toast(this, "Meter readings must be numbers.");
            return;
        }

        repo.background(() -> {
            Reservation active = reservationId == null
                    ? repo.getActiveReservationSync(AuthManager.getInstance().getCurrentUserId())
                    : repo.getReservationSync(reservationId);
            if (active == null) {
                return new Completion(null, "No reservation found.", null);
            }
            ChargerStation loadedStation = repo.getStationSync(active.getStationId());
            if (loadedStation == null) {
                return new Completion(null, "Station not found.", null);
            }
            String meterLevel = loadedStation.getVerificationLevel();
            BillingEngine.BillingCalculationResult billing = BillingEngine.calculateBill(
                    "sess_" + active.getId(), active.getConsumerId(), loadedStation.getHostId(),
                    loadedStation.getName(), loadedStation, meterLevel, true, start, end);
            if (!billing.isValid()) {
                return new Completion(null, billing.getErrorMessage(), null);
            }

            ChargingSession activeSession = repo.getSessionByReservationSync(active.getId());
            if (activeSession == null) {
                activeSession = new ChargingSession(UUID.randomUUID().toString(), active.getId(),
                        active.getConsumerId(), loadedStation.getHostId(), active.getStationId(),
                        active.getStationName(), active.getPortId(), System.currentTimeMillis(),
                        0, start, 0, 0, 0, 0, 0, 0, AppConstants.STATUS_COMPLETED, null, null,
                        true, meterLevel);
            }
            activeSession.setStartingMeterKwh(start);
            activeSession.setEndingMeterKwh(end);
            activeSession.setVerifiedKwhConsumed(billing.getVerifiedKwh());
            activeSession.setEnergyCharge(billing.getEnergyCharge());
            activeSession.setServiceFee(billing.getServiceFee());
            activeSession.setGatewayFee(billing.getGatewayFee());
            activeSession.setTotalPaid(billing.getFinalTotal());
            activeSession.setEndTimeMs(System.currentTimeMillis());
            activeSession.setStatus(AppConstants.STATUS_COMPLETED);
            activeSession.setMeterSimulated(true);
            activeSession.setVerificationLevel(meterLevel);
            activeSession.setInvoiceId(billing.getInvoice().getId());

            MeterReading reading = new MeterReading(UUID.randomUUID().toString(), activeSession.getId(),
                    active.getStationId(), start, end, null, meterLevel,
                    System.currentTimeMillis(), true);
            activeSession.setMeterReadingId(reading.getId());

            Payment payment = repo.getPaymentForReservationSync(active.getId());
            if (payment != null) {
                PaymentManager.PaymentResult capture = PaymentManager.capture(payment,
                        activeSession.getId(), billing.getFinalTotal());
                payment = capture.getPayment();
            }

            active.setStatus(AppConstants.STATUS_COMPLETED);
            // One server call settles the session: the server recomputes the bill from
            // these readings, writes the session, the receipt and the capture, closes the
            // reservation and frees the port. The rows passed in are the offline copy.
            repo.finalizeSession(activeSession, reading, billing.getInvoice(), payment, active);

            ChargerPort loadedPort = active.getPortId() == null ? null
                    : repo.database().chargerPortDao().getPortByIdSync(active.getPortId());
            if (loadedPort != null) {
                loadedPort.setStatus(AppConstants.PORT_AVAILABLE);
                repo.database().chargerPortDao().update(loadedPort);
            }

            AppStateStore.get().setActiveSessionId(activeSession.getId());
            String summary = String.format(Locale.US,
                    "Verified %.2f kWh · %s energy · %s service · %s gateway · total %s",
                    billing.getVerifiedKwh(), Formatters.money(billing.getEnergyCharge()),
                    Formatters.money(billing.getServiceFee()), Formatters.money(billing.getGatewayFee()),
                    Formatters.money(billing.getFinalTotal()));
            return new Completion(activeSession.getId(), summary, billing);
        }, completion -> {
            if (completion == null || !isAdded()) {
                return;
            }
            summaryText.setText(completion.summary);
            if (completion.sessionId == null) {
                Ux.toast(this, completion.summary);
                return;
            }
            ReceiptFragment fragment = new ReceiptFragment();
            Bundle args = new Bundle();
            args.putString(ReceiptFragment.ARG_SESSION_ID, completion.sessionId);
            fragment.setArguments(args);
            Navigator.open(this, fragment, "receipt");
        });
    }

    private static class SessionState {
        private final Reservation reservation;
        private final ChargerStation station;
        private final ChargerPort port;
        private final ChargingSession session;

        SessionState(Reservation reservation, ChargerStation station, ChargerPort port,
                     ChargingSession session) {
            this.reservation = reservation;
            this.station = station;
            this.port = port;
            this.session = session;
        }
    }

    private static class Completion {
        private final String sessionId;
        private final String summary;
        private final BillingEngine.BillingCalculationResult billing;

        Completion(String sessionId, String summary, BillingEngine.BillingCalculationResult billing) {
            this.sessionId = sessionId;
            this.summary = summary;
            this.billing = billing;
        }
    }
}
