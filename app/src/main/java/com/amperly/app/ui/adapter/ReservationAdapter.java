package com.amperly.app.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.data.local.entity.Reservation;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ReservationAdapter extends RecyclerView.Adapter<ReservationAdapter.ReservationViewHolder> {

    public interface Listener {
        void onOpenQr(Reservation reservation);
        void onCheckIn(Reservation reservation);
        void onMessageHost(Reservation reservation);
        void onCancel(Reservation reservation);
    }

    private final List<Reservation> items = new ArrayList<>();
    private final Listener listener;

    public ReservationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Reservation> reservations) {
        items.clear();
        if (reservations != null) {
            items.addAll(reservations);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReservationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reservation, parent, false);
        return new ReservationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReservationViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ReservationViewHolder extends RecyclerView.ViewHolder {
        private final TextView station;
        private final TextView status;
        private final TextView slot;
        private final TextView code;
        private final TextView amount;
        private final MaterialButton openQr;
        private final MaterialButton checkIn;
        private final MaterialButton messageHost;
        private final MaterialButton cancel;

        ReservationViewHolder(@NonNull View itemView) {
            super(itemView);
            station = itemView.findViewById(R.id.reservationStationText);
            status = itemView.findViewById(R.id.reservationStatusText);
            slot = itemView.findViewById(R.id.reservationSlotText);
            code = itemView.findViewById(R.id.reservationCodeText);
            amount = itemView.findViewById(R.id.reservationAmountText);
            openQr = itemView.findViewById(R.id.openQrButton);
            checkIn = itemView.findViewById(R.id.checkInButton);
            messageHost = itemView.findViewById(R.id.messageHostButton);
            cancel = itemView.findViewById(R.id.cancelReservationButton);
        }

        void bind(Reservation reservation) {
            station.setText(reservation.getStationName() == null
                    ? reservation.getStationId() : reservation.getStationName());
            status.setText(reservation.getStatus());
            slot.setText(Formatters.dayAndSlot(reservation.getStartTimeMs(), reservation.getEndTimeMs())
                    + (reservation.getPortLabel() == null ? "" : " · " + reservation.getPortLabel()));
            code.setText("Booking code " + reservation.getBookingCode());
            String paymentLine = reservation.isPaid()
                    ? "Authorized: " + Formatters.money(reservation.getPricePerKwh()) .replace("₹", "₹") + "/kWh + "
                    + Formatters.money(reservation.getServiceFee()) + " service + "
                    + Formatters.money(reservation.getGatewayFee()) + " gateway. Captured after the meter is verified."
                    : "Reservation without automatic billing. No energy-based payment is captured at this station.";
            amount.setText(paymentLine);

            boolean active = reservation.isActive();
            openQr.setVisibility(active ? View.VISIBLE : View.GONE);
            checkIn.setEnabled(active);
            cancel.setVisibility(active ? View.VISIBLE : View.GONE);
            cancel.setText(reservation.isInFreeCancellationWindow(System.currentTimeMillis())
                    ? "Cancel (free)" : "Cancel (₹20 late fee)");

            openQr.setOnClickListener(v -> {
                if (listener != null) listener.onOpenQr(reservation);
            });
            checkIn.setOnClickListener(v -> {
                if (listener != null) listener.onCheckIn(reservation);
            });
            messageHost.setOnClickListener(v -> {
                if (listener != null) listener.onMessageHost(reservation);
            });
            cancel.setOnClickListener(v -> {
                if (listener != null) listener.onCancel(reservation);
            });
        }
    }
}
