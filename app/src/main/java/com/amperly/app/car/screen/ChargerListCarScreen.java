package com.amperly.app.car.screen;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.availability.AvailabilityEngine;

import java.util.List;
import java.util.Locale;

/** Short, glanceable list of chargers the car can actually reach at the current battery level. */
public class ChargerListCarScreen extends Screen {

    private final AmperlyRepository repo;
    private List<ChargerStation> stations;

    public ChargerListCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        this.repo = AmperlyRepository.getInstance(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder list = new ItemList.Builder();
        if (stations == null) {
            list.addItem(new Row.Builder().setTitle("Loading chargers").build());
            com.amperly.app.car.CarBridge.load(repo, snapshot -> {
                stations = snapshot.getReachableStations();
                invalidate();
            });
        } else if (stations.isEmpty()) {
            list.addItem(new Row.Builder()
                    .setTitle("No safely reachable charger")
                    .addText("Amperly will not route you beyond your estimated safe range. "
                            + "Update the battery value or widen the search on your phone.")
                    .build());
        } else {
            for (ChargerStation station : stations) {
                final String stationId = station.getId();
                list.addItem(new Row.Builder()
                        .setTitle(station.getName())
                        .addText(station.getConnectorTypes() + " · " + (int) station.getMaxPowerKw()
                                + " kW · " + String.format(Locale.US, "₹%.0f", station.getPricePerKwh()) + "/kWh")
                        .addText(station.supportsPaidEnergyBilling()
                                ? "Verified meter: " + station.getVerificationLevel()
                                : "Discovery only — no verified meter")
                        .setOnClickListener(() -> getScreenManager().push(
                                new ChargerDetailsCarScreen(getCarContext(), stationId)))
                        .build());
            }
        }

        return new ListTemplate.Builder()
                .setTitle("Route corridor chargers")
                .setSingleList(list.build())
                .setHeaderAction(Action.BACK)
                .build();
    }

    /** Kept for callers that already have a list in hand (for example a deep link). */
    public void setStations(List<ChargerStation> stations) {
        this.stations = stations;
        invalidate();
    }

    public static String portSummary(List<com.amperly.app.data.local.entity.ChargerPort> ports) {
        if (ports == null || ports.isEmpty()) {
            return "No ports published";
        }
        return AvailabilityEngine.portLabel(ports.get(0));
    }
}
