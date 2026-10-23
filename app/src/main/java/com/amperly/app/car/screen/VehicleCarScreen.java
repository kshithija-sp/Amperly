package com.amperly.app.car.screen;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.amperly.app.core.security.AuthManager;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.data.repository.AmperlyRepository;

/**
 * The vehicle the trip is calculated for: usable battery, consumption and the arrival reserve.
 *
 * <p>Everything here is read-only. Changing the car is a form, and forms belong to the phone.
 */
public class VehicleCarScreen extends Screen {

    private final AmperlyRepository repo;
    private Vehicle vehicle;
    private boolean loaded;

    public VehicleCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        this.repo = AmperlyRepository.getInstance(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (!loaded) {
            loaded = true;
            final String userId = AuthManager.getInstance().getCurrentUserId();
            repo.background(() -> repo.getVehicleSync(userId), value -> {
                vehicle = value;
                invalidate();
            });
        }

        ItemList.Builder list = new ItemList.Builder();

        if (vehicle == null) {
            list.addItem(new Row.Builder()
                    .setTitle("No vehicle on file")
                    .addText("Add your vehicle on the phone and the range follows it here.")
                    .build());
        } else {
            list.addItem(new Row.Builder()
                    .setTitle(vehicle.getMake() + " " + vehicle.getModel())
                    .addText(vehicle.getVariant() == null ? "" : vehicle.getVariant())
                    .build());
            list.addItem(new Row.Builder()
                    .setTitle("Battery")
                    .addText(Math.round(vehicle.effectiveCapacityKwh()) + " kWh usable"
                            + " - " + Math.round(vehicle.getBatteryHealthPercent()) + "% health")
                    .build());
            list.addItem(new Row.Builder()
                    .setTitle("Consumption")
                    .addText(Math.round(vehicle.effectiveEfficiencyKmPerKwh()) + " km/kWh"
                            + " - DC up to " + Math.round(vehicle.getMaxDcKw()) + " kW")
                    .build());
            list.addItem(new Row.Builder()
                    .setTitle("Arrival reserve")
                    .addText(vehicle.getMinArrivalSocPercent() + "%"
                            + " - charge to " + vehicle.getMaxChargeSocPercent() + "%")
                    .build());
            if (vehicle.getLicensePlate() != null && !vehicle.getLicensePlate().isEmpty()) {
                list.addItem(new Row.Builder()
                        .setTitle("Plate")
                        .addText(vehicle.getLicensePlate())
                        .build());
            }
        }

        return new ListTemplate.Builder()
                .setTitle("Vehicle")
                .setSingleList(list.build())
                .build();
    }
}
