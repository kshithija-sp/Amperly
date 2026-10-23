package com.amperly.app.car.screen;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.amperly.app.car.CarBridge;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.Reservation;
import com.amperly.app.data.repository.AmperlyRepository;

public class ReservationStatusCarScreen extends Screen {

    private final AmperlyRepository repo;
    private Reservation reservation;
    private ChargerStation station;
    private boolean loaded;

    public ReservationStatusCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        this.repo = AmperlyRepository.getInstance(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (!loaded) {
            CarBridge.load(repo, snapshot -> {
                reservation = snapshot.getReservation();
                if (reservation != null) {
                    repo.background(() -> repo.getStationSync(reservation.getStationId()), loadedStation -> {
                        station = loadedStation;
                        loaded = true;
                        invalidate();
                    });
                } else {
                    loaded = true;
                    invalidate();
                }
            });
            return new PaneTemplate.Builder(new Pane.Builder()
                    .addRow(new Row.Builder().setTitle("Checking your reservation").build())
                    .build())
                    .setTitle("Reservation")
                    .setHeaderAction(Action.BACK)
                    .build();
        }

        if (reservation == null) {
            Pane pane = new Pane.Builder()
                    .addRow(new Row.Builder()
                            .setTitle("No active reservation")
                            .addText("Reserve a port on your phone before departure, or use the charger "
                                    + "details screen to take the next free slot.")
                            .build())
                    .build();
            return new PaneTemplate.Builder(pane)
                    .setTitle("Reservation")
                    .setHeaderAction(Action.BACK)
                    .build();
        }

        Row statusRow = new Row.Builder()
                .setTitle("Booking " + reservation.getBookingCode() + " · " + reservation.getStatus())
                .addText((reservation.getStationName() == null ? reservation.getStationId()
                        : reservation.getStationName()) + " · " + reservation.getPortLabel())
                .addText(Formatters.dayAndSlot(reservation.getStartTimeMs(), reservation.getEndTimeMs()))
                .build();

        Row checkInRow = new Row.Builder()
                .setTitle("On arrival")
                .addText("Show the booking QR code on your phone at the entrance. The host scans it to "
                        + "start the session, and the meter reading is captured then.")
                .build();

        Pane.Builder paneBuilder = new Pane.Builder()
                .addRow(statusRow)
                .addRow(checkInRow);

        if (station != null) {
            paneBuilder.addAction(new Action.Builder()
                    .setTitle("Navigate")
                    .setOnClickListener(() -> Navigator.navigateTo(getCarContext(), station.getLatitude(),
                            station.getLongitude(), station.getName()))
                    .build());
        }
        paneBuilder.addAction(new Action.Builder()
                .setTitle("Amperly is not emergency services")
                .setOnClickListener(() -> CarToast.makeText(getCarContext(),
                        "In an emergency call 112. Amperly cannot dispatch help.",
                        CarToast.LENGTH_LONG).show())
                .build());

        return new PaneTemplate.Builder(paneBuilder.build())
                .setTitle("Reservation")
                .setHeaderAction(Action.BACK)
                .build();
    }
}
