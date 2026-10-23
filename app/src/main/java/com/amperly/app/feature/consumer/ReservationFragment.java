package com.amperly.app.feature.consumer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.communication.ChatManager;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.CommunicationThread;
import com.amperly.app.data.local.entity.Notification;
import com.amperly.app.data.local.entity.Payment;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.local.entity.User;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.availability.AvailabilityEngine;
import com.amperly.app.engine.billing.BillingEngine;
import com.amperly.app.engine.booking.ReservationEngine;
import com.amperly.app.payment.PaymentManager;
import com.amperly.app.ui.Ux;
import com.amperly.app.ui.adapter.ReservationAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Reservations: pick a real free slot, see the transparent price breakdown and the published
 * cancellation policy, authorize in sandbox mode, and only then create the booking. If the gateway
 * cannot be reached nothing is reserved.
 */
public class ReservationFragment extends Fragment implements ReservationAdapter.Listener {

    public static final String ARG_STATION_ID = "station_id";

    private AmperlyRepository repo;
    private ReservationAdapter adapter;
    private TextView reserveStatusText;
    private String stationId;

    private ChargerStation station;
    private final List<ChargerPort> ports = new ArrayList<>();
    private final List<AvailabilityEngine.Slot> slots = new ArrayList<>();
    private Spinner portSpinner;
    private Spinner slotSpinner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reservation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());
        stationId = getArguments() == null ? null : getArguments().getString(ARG_STATION_ID);

        reserveStatusText = view.findViewById(R.id.reserveStatusText);
        TextView stationText = view.findViewById(R.id.reserveStationText);
        LinearLayout formContainer = view.findViewById(R.id.bookingFormContainer);
        Button authorize = view.findViewById(R.id.authorizeButton);

        RecyclerView reservations = view.findViewById(R.id.reservationsRecyclerView);
        reservations.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ReservationAdapter(this);
        reservations.setAdapter(adapter);

        repo.observeReservationsForConsumer(AuthManager.getInstance().getCurrentUserId())
                .observe(getViewLifecycleOwner(), list -> adapter.submit(list));

        view.findViewById(R.id.chooseStationButton).setOnClickListener(v -> {
            Navigator.openRoot(requireActivity(), new ChargerDiscoveryFragment(), "discover");
        });

        TextView policy = view.findViewById(R.id.policyText);
        policy.setText(ReservationEngine.CANCELLATION_POLICY);
        TextView priceNote = view.findViewById(R.id.priceNoteText);
        priceNote.setText("Energy is estimated from the slot length; the final amount is always based on verified meter readings.");
        authorize.setText(R.string.action_pay);

        final String userId = AuthManager.getInstance().getCurrentUserId();
        if (stationId != null) {
            formContainer.setVisibility(View.VISIBLE);
            loadBookingForm(view, userId);
            return;
        }

        // Resolve a sensible default station on a background thread (Room forbids main-thread reads).
        repo.background(() -> {
            Reservation active = repo.getActiveReservationSync(userId);
            if (active != null && active.isActive()) {
                return active.getStationId();
            }
            List<ChargerStation> stations = repo.getApprovedStationsSync();
            for (ChargerStation candidate : stations) {
                if (candidate.supportsPaidEnergyBilling() && !candidate.isPaused()) {
                    return candidate.getId();
                }
            }
            return stations.isEmpty() ? null : stations.get(0).getId();
        }, resolvedId -> {
            if (!isAdded()) {
                return;
            }
            if (resolvedId == null) {
                formContainer.setVisibility(View.GONE);
                stationText.setText("Choose a station from Discover to start a reservation. Your existing reservations are listed below.");
                reserveStatusText.setText("No station selected yet.");
                return;
            }
            stationId = resolvedId;
            formContainer.setVisibility(View.VISIBLE);
            loadBookingForm(view, userId);
        });
    }

    private void loadBookingForm(View view, String userId) {
        TextView stationText = view.findViewById(R.id.reserveStationText);
        TextView slotHint = view.findViewById(R.id.slotHintText);
        TextView meterWarning = view.findViewById(R.id.meterWarningText);
        portSpinner = view.findViewById(R.id.portSpinner);
        slotSpinner = view.findViewById(R.id.slotSpinner);
        Button authorize = view.findViewById(R.id.authorizeButton);
        authorize.setText(R.string.action_pay);

        repo.background(() -> {
            ChargerStation loaded = repo.getStationSync(stationId);
            Vehicle vehicle = repo.getVehicleSync(userId);
            List<ChargerPort> stationPorts = repo.getPortsSync(stationId);
            return new BookingData(loaded, vehicle, stationPorts);
        }, data -> {
            if (data == null || data.station == null || !isAdded()) {
                Ux.toast(this, "Station not found.");
                return;
            }
            station = data.station;
            stationText.setText(station.getName() + " · " + station.getAddress() + "\n"
                    + station.getOperatingHours() + " · " + station.getConnectorTypes());
            meterWarning.setVisibility(station.supportsPaidEnergyBilling() ? View.GONE : View.VISIBLE);
            if (!station.supportsPaidEnergyBilling()) {
                meterWarning.setText(R.string.meter_gating_warning);
            }

            ports.clear();
            List<String> portLabels = new ArrayList<>();
            for (ChargerPort port : data.ports) {
                if (AppConstants.PORT_AVAILABLE.equalsIgnoreCase(port.getStatus())) {
                    ports.add(port);
                    portLabels.add(AvailabilityEngine.portLabel(port));
                }
            }
            if (ports.isEmpty()) {
                slotHint.setText("All ports are busy or under maintenance right now.");
            }
            portSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_dropdown_item, portLabels));
            portSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view1, int position, long id) {
                    if (position < ports.size()) {
                        loadSlots(ports.get(position));
                    }
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });

            view.findViewById(R.id.authorizeButton).setOnClickListener(v -> authorizeBooking(userId));
        });
    }

    private void loadSlots(ChargerPort port) {
        repo.background(() -> {
            Calendar day = Calendar.getInstance();
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            List<AvailabilityEngine.Slot> generated = new ArrayList<>();
            for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
                long start = day.getTimeInMillis() + (dayOffset * 24L * 60L * 60L * 1000L);
                generated.addAll(repo.getSlots(stationId, start, AvailabilityEngine.DEFAULT_SLOT_MINUTES, 12));
            }
            List<AvailabilityEngine.Slot> free = new ArrayList<>();
            for (AvailabilityEngine.Slot slot : generated) {
                if (slot.isAvailable() && port.getId().equals(slot.getPortId())) {
                    free.add(slot);
                }
            }
            return free;
        }, freeSlots -> {
            if (!isAdded()) {
                return;
            }
            slots.clear();
            slots.addAll(freeSlots);
            List<String> labels = new ArrayList<>();
            for (AvailabilityEngine.Slot slot : slots) {
                labels.add(slot.label());
            }
            if (labels.isEmpty()) {
                labels.add(getString(R.string.reserve_no_slots));
            }
            slotSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_dropdown_item, labels));
            updatePricePreview(port);
        });
    }

    private void updatePricePreview(ChargerPort port) {
        if (station == null || port == null) {
            return;
        }
        double hours = AvailabilityEngine.DEFAULT_SLOT_MINUTES / 60.0;
        double estimatedKwh = port.getPowerKw() * hours * 0.75;
        ReservationEngine.PricePreview preview = ReservationEngine.preview(station, estimatedKwh);
        if (!isAdded()) {
            return;
        }
        View view = getView();
        if (view == null) {
            return;
        }
        ((TextView) view.findViewById(R.id.priceEnergyText)).setText("Estimated energy "
                + String.format(java.util.Locale.US, "%.1f kWh", preview.getEstimatedKwh()) + " · "
                + Formatters.money(preview.getEnergyCharge()) + " at "
                + Formatters.money(station.getPricePerKwh()) + "/kWh");
        ((TextView) view.findViewById(R.id.priceServiceText)).setText("Amperly service fee: "
                + Formatters.money(preview.getServiceFee()));
        ((TextView) view.findViewById(R.id.priceGatewayText)).setText("Payment gateway charge: "
                + Formatters.money(preview.getGatewayFee()));
        ((TextView) view.findViewById(R.id.priceTotalText)).setText("Estimated total "
                + Formatters.money(preview.getEstimatedTotal()) + " · authorize up to "
                + Formatters.money(preview.getAuthorizedAmount()));
    }

    private void authorizeBooking(String userId) {
        if (station == null) {
            Ux.toast(this, "Select a station first.");
            return;
        }
        int portIndex = portSpinner == null ? -1 : portSpinner.getSelectedItemPosition();
        int slotIndex = slotSpinner == null ? -1 : slotSpinner.getSelectedItemPosition();
        ChargerPort port = portIndex >= 0 && portIndex < ports.size() ? ports.get(portIndex) : null;
        AvailabilityEngine.Slot slot = slotIndex >= 0 && slotIndex < slots.size() ? slots.get(slotIndex) : null;

        if (port == null || slot == null) {
            reserveStatusText.setText(getString(R.string.reserve_no_slots));
            return;
        }

        repo.background(() -> {
            Vehicle vehicle = repo.getVehicleSync(userId);
            int conflicts = repo.countSlotConflicts(port.getId(), slot.getStartMs(), slot.getEndMs());
            ReservationEngine.BookingCheck check = ReservationEngine.check(station, port, vehicle,
                    slot.getStartMs(), slot.getEndMs(), conflicts, station.isPaused());
            if (!check.isAllowed()) {
                return new BookingOutcome(false, check.getReason(), null, null, null);
            }

            double estimatedKwh = port.getPowerKw() * (AvailabilityEngine.DEFAULT_SLOT_MINUTES / 60.0) * 0.75;
            ReservationEngine.PricePreview preview = ReservationEngine.preview(station, estimatedKwh);
            String idempotencyKey = PaymentManager.newIdempotencyKey(userId, station.getId(), slot.getStartMs());
            PaymentManager.PaymentResult paymentResult = PaymentManager.authorize(userId,
                    station.getHostId(), null, preview.getAuthorizedAmount(), idempotencyKey);
            if (!paymentResult.isSuccess()) {
                return new BookingOutcome(false, paymentResult.getMessage(), null, null, null);
            }

            String reservationId = UUID.randomUUID().toString();
            String bookingCode = ReservationEngine.buildBookingCode();
            Payment payment = paymentResult.getPayment();
            payment.setReservationId(reservationId);

            Reservation reservation = new Reservation(reservationId, userId, station.getId(),
                    station.getName(), port.getId(), AvailabilityEngine.portLabel(port), bookingCode,
                    slot.getStartMs(), slot.getEndMs(), AppConstants.STATUS_RESERVED,
                    station.getPricePerKwh(), station.getServiceFee(), station.getGatewayFee(), 0.0,
                    station.supportsPaidEnergyBilling(), payment.getId(), 0, 0, System.currentTimeMillis());
            // The reservation has to exist on the server before authorize_payment runs: that
            // function looks the booking up to decide who may hold the authorization.
            repo.saveReservation(reservation);
            repo.authorizePayment(payment, station.getId());

            User consumer = AuthManager.getInstance().getCurrentUser();
            User host = repo.getUserSync(station.getHostId());
            CommunicationThread thread = ChatManager.createThread(reservation, consumer, host);
            repo.saveThread(thread);

            // Only the booking consumer's notification is written. `notifications` is scoped to
            // its owner by Row Level Security, so a notification addressed to the host was
            // refused by the server on every attempt; it never reached the database.
            repo.saveNotification(new Notification(UUID.randomUUID().toString(), userId,
                    "Reservation confirmed", bookingCode + " at " + station.getName()
                    + ". Show the booking code at the entrance.", false, System.currentTimeMillis()));

            AppStateStore.get().setActiveReservation(reservation);
            AppStateStore.get().setLastBookingCode(bookingCode);
            return new BookingOutcome(true, "Reservation confirmed. Booking code " + bookingCode,
                    reservationId, bookingCode, payment.getId());
        }, outcome -> {
            if (outcome == null || !isAdded()) {
                return;
            }
            reserveStatusText.setText(outcome.message);
            if (!outcome.success) {
                Ux.toast(this, outcome.message);
                return;
            }
            BookingQrFragment fragment = new BookingQrFragment();
            Bundle args = new Bundle();
            args.putString(BookingQrFragment.ARG_RESERVATION_ID, outcome.reservationId);
            fragment.setArguments(args);
            Navigator.open(this, fragment, "booking_qr");
        });
    }

    @Override
    public void onOpenQr(Reservation reservation) {
        BookingQrFragment fragment = new BookingQrFragment();
        Bundle args = new Bundle();
        args.putString(BookingQrFragment.ARG_RESERVATION_ID, reservation.getId());
        fragment.setArguments(args);
        Navigator.open(this, fragment, "booking_qr");
    }

    @Override
    public void onCheckIn(Reservation reservation) {
        AppStateStore.get().setActiveReservation(reservation);
        ActiveSessionFragment fragment = new ActiveSessionFragment();
        Bundle args = new Bundle();
        args.putString(ActiveSessionFragment.ARG_RESERVATION_ID, reservation.getId());
        fragment.setArguments(args);
        Navigator.open(this, fragment, "session");
    }

    @Override
    public void onMessageHost(Reservation reservation) {
        AppStateStore.get().setActiveReservation(reservation);
        Navigator.open(this, new HostChatFragment(), "chat");
    }

    @Override
    public void onCancel(Reservation reservation) {
        repo.background(() -> {
            BillingEngine.CancellationResult result =
                    BillingEngine.calculateCancellation(reservation, System.currentTimeMillis());
            reservation.setStatus(AppConstants.STATUS_CANCELLED);
            reservation.setCancelledAtMs(System.currentTimeMillis());
            reservation.setCancellationFee(result.getFee());
            repo.updateReservation(reservation);

            Payment payment = repo.getPaymentForReservationSync(reservation.getId());
            if (payment != null) {
                PaymentManager.PaymentResult release = PaymentManager.release(payment, result.getFee());
                repo.savePayment(release.getPayment());
            }
            // The server applies the same published policy and releases the authorization;
            // the local rows above are the immediate view.
            repo.cancelReservationOnServer(reservation.getId());
            return result.getMessage();
        }, message -> {
            if (message != null) {
                reserveStatusText.setText(message);
                Ux.toast(this, message);
            }
        });
    }

    private static class BookingData {
        private final ChargerStation station;
        private final Vehicle vehicle;
        private final List<ChargerPort> ports;

        BookingData(ChargerStation station, Vehicle vehicle, List<ChargerPort> ports) {
            this.station = station;
            this.vehicle = vehicle;
            this.ports = ports;
        }
    }

    private static class BookingOutcome {
        private final boolean success;
        private final String message;
        private final String reservationId;
        private final String bookingCode;
        private final String paymentId;

        BookingOutcome(boolean success, String message, String reservationId, String bookingCode,
                       String paymentId) {
            this.success = success;
            this.message = message;
            this.reservationId = reservationId;
            this.bookingCode = bookingCode;
            this.paymentId = paymentId;
        }
    }
}
