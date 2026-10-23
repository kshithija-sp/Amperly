package com.amperly.app.car.screen;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.amperly.app.core.security.AuthManager;
import com.amperly.app.data.local.entity.RoutePlan;
import com.amperly.app.data.repository.AmperlyRepository;

import java.util.List;

/**
 * The trips the driver has already planned, listed for the car display.
 *
 * <p>Only the trip the phone already planned is shown: the car never plans a new route, it only
 * makes the ones that exist glanceable while the vehicle is moving.
 */
public class TripHistoryCarScreen extends Screen {

    private final AmperlyRepository repo;
    private List<RoutePlan> plans;
    private boolean loaded;

    public TripHistoryCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        this.repo = AmperlyRepository.getInstance(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (!loaded) {
            loaded = true;
            final String userId = AuthManager.getInstance().getCurrentUserId();
            repo.background(() -> repo.getRoutePlansSync(userId), value -> {
                plans = value;
                invalidate();
            });
        }

        ItemList.Builder list = new ItemList.Builder();

        if (plans == null) {
            list.addItem(new Row.Builder()
                    .setTitle("Loading your trips")
                    .build());
        } else if (plans.isEmpty()) {
            list.addItem(new Row.Builder()
                    .setTitle("No trips yet")
                    .addText("Plan a trip on your phone and it shows up here.")
                    .build());
        } else {
            for (RoutePlan plan : plans) {
                list.addItem(new Row.Builder()
                        .setTitle(plan.getDestination())
                        .addText(plan.getOrigin() + " to " + plan.getDestination()
                                + " - " + Math.round(plan.getTotalDistanceKm()) + " km")
                        .addText(Math.round(plan.getTotalJourneyMinutes()) + " min"
                                + " - start at " + plan.getPlannedStartSoc() + "%")
                        .build());
            }
        }

        return new ListTemplate.Builder()
                .setTitle("Trip history")
                .setSingleList(list.build())
                .build();
    }
}
