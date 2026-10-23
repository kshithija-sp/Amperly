package com.amperly.app.car.screen;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.amperly.app.car.CarBridge;
import com.amperly.app.data.repository.AmperlyRepository;

/**
 * Battery correction without typing: large predefined percentages. The value the driver picks becomes
 * authoritative, unreachable chargers are dropped and the nearest safe compatible charger is shown.
 */
public class SoCUpdateCarScreen extends Screen {

    private static final int[] SOC_OPTIONS = {5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90};

    private final AmperlyRepository repo;

    public SoCUpdateCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        this.repo = AmperlyRepository.getInstance(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder list = new ItemList.Builder();
        for (final int option : SOC_OPTIONS) {
            list.addItem(new Row.Builder()
                    .setTitle("Actual battery " + option + "%")
                    .addText("Tap to make this authoritative and recalculate the remaining route")
                    .setOnClickListener(() -> CarBridge.applyBatteryUpdate(repo, option, message -> {
                        CarToast.makeText(getCarContext(), message, CarToast.LENGTH_LONG).show();
                        getScreenManager().pop();
                    }))
                    .build());
        }
        return new ListTemplate.Builder()
                .setTitle("Select dashboard battery %")
                .setSingleList(list.build())
                .setHeaderAction(Action.BACK)
                .build();
    }
}
