package com.amperly.app.feature.consumer;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.booking.ReservationEngine;
import com.amperly.app.ui.Ux;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * The booking code the consumer shows at the station. The QR payload is a signed-style deep link so
 * the Host side can validate the code offline in the prototype.
 */
public class BookingQrFragment extends Fragment {

    public static final String ARG_RESERVATION_ID = "reservation_id";

    private AmperlyRepository repo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_booking_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());
        String reservationId = getArguments() == null ? null : getArguments().getString(ARG_RESERVATION_ID);

        TextView codeText = view.findViewById(R.id.bookingCodeText);
        TextView stationText = view.findViewById(R.id.qrStationText);
        TextView slotText = view.findViewById(R.id.qrSlotText);
        ImageView qrImage = view.findViewById(R.id.qrImageView);

        repo.background(() -> {
            Reservation reservation = reservationId == null ? null : repo.getReservationSync(reservationId);
            if (reservation == null) {
                reservation = repo.getActiveReservationSync(com.amperly.app.core.security.AuthManager
                        .getInstance().getCurrentUserId());
            }
            return reservation;
        }, reservation -> {
            if (!isAdded()) {
                return;
            }
            if (reservation == null) {
                codeText.setText("No reservation");
                stationText.setText("Reserve a port to get a booking code.");
                return;
            }
            codeText.setText(reservation.getBookingCode());
            stationText.setText((reservation.getStationName() == null ? reservation.getStationId()
                    : reservation.getStationName()) + " · " + reservation.getPortLabel());
            slotText.setText(Formatters.dayAndSlot(reservation.getStartTimeMs(), reservation.getEndTimeMs())
                    + " · " + reservation.getStatus());
            qrImage.setImageBitmap(renderQr(ReservationEngine.bookingQrPayload(reservation)));
        });

        view.findViewById(R.id.goToSessionButton).setOnClickListener(v -> {
            AppStateStore.get().setActiveReservationId(reservationId);
            ActiveSessionFragment fragment = new ActiveSessionFragment();
            Bundle args = new Bundle();
            args.putString(ActiveSessionFragment.ARG_RESERVATION_ID, reservationId);
            fragment.setArguments(args);
            Navigator.open(this, fragment, "session");
        });

        view.findViewById(R.id.closeButton).setOnClickListener(v -> {
            if (!Navigator.pop(this)) {
                Navigator.openRoot(requireActivity(), new ChargerDiscoveryFragment(), "discover");
            }
        });
    }

    private Bitmap renderQr(String payload) {
        int size = 600;
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int offset = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            return bitmap;
        } catch (WriterException e) {
            Ux.toast(this, "Could not render the booking QR code.");
            return null;
        }
    }
}
