package com.amperly.app.car.screen;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.amperly.app.BuildConfig;
import com.amperly.app.core.state.AppStateStore;

/**
 * The car-side settings: the state of the projection and the app version.
 *
 * <p>There is deliberately nothing to edit here. Account, payment and meter settings are forms,
 * and a form on a moving vehicle is a hazard, so they stay on the phone.
 */
public class CarSettingsScreen extends Screen {

    public CarSettingsScreen(@NonNull CarContext carContext) {
        super(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder list = new ItemList.Builder();

        list.addItem(new Row.Builder()
                .setTitle("Car connection")
                .addText(AppStateStore.get().isCarConnected()
                        ? "Connected. The map and the trip summary are live."
                        : "Not connected. Reconnect from the phone.")
                .build());

        list.addItem(new Row.Builder()
                .setTitle("Version")
                .addText("Amperly " + BuildConfig.VERSION_NAME)
                .build());

        list.addItem(new Row.Builder()
                .setTitle("Detailed settings live on the phone")
                .addText("Payment, meters and account details are never editable while driving.")
                .build());

        return new ListTemplate.Builder()
                .setTitle("Settings")
                .setSingleList(list.build())
                .build();
    }
}
