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
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.repository.AmperlyRepository;

import java.util.List;
import java.util.Locale;

/**
 * Charger details, kept to two glanceable rows and three safe actions. Reserving here is a single tap
 * on the next published free slot — never a multi-step form.
 */
public class ChargerDetailsCarScreen extends Screen {

    private final AmperlyRepository repo;
    private final String stationId;
    private ChargerStation station;
    private List<ChargerPort> ports;

    public ChargerDetailsCarScreen(@NonNull CarContext carContext, String stationId) {
        super(carContext);
        this.repo = AmperlyRepository.getInstance(carContext);
        this.stationId = stationId;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (station == null) {
            repo.background(() -> {
                ChargerStation loaded = repo.getStationSync(stationId);
                List<ChargerPort> loadedPorts = repo.getPortsSync(stationId);
                ports = loadedPorts;
                return loaded;
            }, loaded -> {
                station = loaded;
                invalidate();
            });

            Row loading = new Row.Builder().setTitle("Loading charger").build();
            Pane pane = new Pane.Builder().addRow(loading).build();
            return new PaneTemplate.Builder(pane).setTitle("Charger details")
                    .setHeaderAction(Action.BACK).build();
        }

        Row stationRow = new Row.Builder()
                .setTitle(station.getName())
                .addText(station.getAddress())
                .addText(station.getConnectorTypes() + " · " + (int) station.getMaxPowerKw() + " kW · "
                        + String.format(Locale.US, "₹%.0f", station.getPricePerKwh()) + "/kWh + ₹"
                        + (int) station.getServiceFee() + " service + ₹" + (int) station.getGatewayFee()
                        + " gateway")
                .build();

        Row accessRow = new Row.Builder()
                .setTitle("Access and meter")
                .addText(station.getAccessRules() == null ? "No access rules published."
                        : station.getAccessRules())
                .addText(station.supportsPaidEnergyBilling()
                        ? "Verified meter (" + station.getVerificationLevel() + ") — paid energy billing enabled"
                        : "No verified meter — discovery only, no automatic paid billing")
                .build();

        Pane.Builder paneBuilder = new Pane.Builder()
                .addRow(stationRow)
                .addRow(accessRow)
                .addAction(new Action.Builder()
                        .setTitle("Navigate")
                        .setOnClickListener(() -> Navigator.navigateTo(getCarContext(),
                                station.getLatitude(), station.getLongitude(), station.getName()))
                        .build())
                .addAction(new Action.Builder()
                        .setTitle("Reserve next free slot")
                        .setOnClickListener(() -> CarBridge.reserveNextFreeSlot(repo, station.getId(),
                                message -> {
                                    CarToast.makeText(getCarContext(), message, CarToast.LENGTH_LONG).show();
                                    invalidate();
                                }))
                        .build());

        return new PaneTemplate.Builder(paneBuilder.build())
                .setTitle("Charger details")
                .setHeaderAction(Action.BACK)
                .build();
    }
}
