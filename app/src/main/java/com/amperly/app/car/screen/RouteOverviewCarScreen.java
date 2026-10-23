package com.amperly.app.car.screen;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.MapController;
import androidx.car.app.navigation.model.MapWithContentTemplate;

import com.amperly.app.car.CarBridge;
import com.amperly.app.data.repository.AmperlyRepository;

/**
 * The main car screen: the map takes the display and a narrow content pane down the side carries
 * the trip summary plus the links to the other car pages.
 *
 * <p>{@link MapWithContentTemplate} is the only template in the car app library that draws content
 * next to a map, and the host decides the split - a wide map with a side pane on a car display.
 * It needs car API level 7, so on older hosts this screen falls back to the plain list it used
 * before instead of handing the host a template it cannot draw.
 */
public class RouteOverviewCarScreen extends Screen {

    /** Car API level that introduced {@link MapWithContentTemplate}. */
    private static final int CAR_API_LEVEL_MAP_WITH_CONTENT = 7;

    private final AmperlyRepository repo;
    private CarBridge.Snapshot snapshot;

    public RouteOverviewCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        this.repo = AmperlyRepository.getInstance(carContext);
    }

    @NonNull
    @Override
    @androidx.annotation.OptIn(markerClass = androidx.car.app.annotations.ExperimentalCarApi.class)
    public Template onGetTemplate() {
        if (snapshot == null) {
            CarBridge.load(repo, value -> {
                snapshot = value;
                invalidate();
            });
        }

        ListTemplate sidebar = buildSidebar();

        if (getCarContext().getCarAppApiLevel() < CAR_API_LEVEL_MAP_WITH_CONTENT) {
            // A host below car API 7 cannot draw a map with side content; the list is the fallback.
            return sidebar;
        }

        MapController mapController = new MapController.Builder()
                .setMapActionStrip(buildMapActionStrip())
                .build();

        return new MapWithContentTemplate.Builder()
                .setContentTemplate(sidebar)
                .setMapController(mapController)
                .setActionStrip(buildActionStrip())
                .build();
    }

    /**
     * The side pane: the trip at a glance, the safe actions, and the links to the other car pages.
     */
    private ListTemplate buildSidebar() {
        ItemList.Builder list = new ItemList.Builder();

        if (snapshot == null) {
            list.addItem(new Row.Builder()
                    .setTitle("Loading your trip")
                    .addText("Amperly is reading the plan you set up on your phone.")
                    .build());
        } else {
            list.addItem(new Row.Builder()
                    .setTitle(snapshot.routeTitle())
                    .addText(snapshot.summaryLine())
                    .build());

            if (snapshot.getRecommendedStation() != null) {
                list.addItem(new Row.Builder()
                        .setTitle("Recommended stop: " + snapshot.getRecommendedStation().getName())
                        .addText(snapshot.getRecommendedDetail() == null ? ""
                                : snapshot.getRecommendedDetail())
                        .setOnClickListener(() -> getScreenManager().push(
                                new ChargerDetailsCarScreen(getCarContext(),
                                        snapshot.getRecommendedStation().getId())))
                        .build());
            } else if (snapshot.getNoSafeChargerMessage() != null
                    && !snapshot.getNoSafeChargerMessage().isEmpty()) {
                list.addItem(new Row.Builder()
                        .setTitle("No safe charger in range")
                        .addText(snapshot.getNoSafeChargerMessage())
                        .build());
            } else {
                list.addItem(new Row.Builder()
                        .setTitle("No charging stop needed")
                        .addText("Your battery covers this trip down to the arrival reserve.")
                        .build());
            }

            list.addItem(new Row.Builder()
                    .setTitle("Start navigation")
                    .addText("Driver-safe navigation to the recommended stop and destination")
                    .setOnClickListener(() -> getScreenManager().push(
                            new NavigationCarScreen(getCarContext())))
                    .build());

            list.addItem(new Row.Builder()
                    .setTitle("Chargers along the route ("
                            + snapshot.getReachableStations().size() + ")")
                    .addText("Safely reachable at your current battery level")
                    .setOnClickListener(() -> getScreenManager().push(
                            new ChargerListCarScreen(getCarContext())))
                    .build());

            list.addItem(new Row.Builder()
                    .setTitle("Update actual battery")
                    .addText("Enter the dashboard value with two taps - no typing while driving")
                    .setOnClickListener(() -> getScreenManager().push(
                            new SoCUpdateCarScreen(getCarContext())))
                    .build());

            if (snapshot.getReservation() != null) {
                list.addItem(new Row.Builder()
                        .setTitle("Reservation " + snapshot.getReservation().getBookingCode())
                        .addText((snapshot.getReservation().getStationName() == null ? ""
                                : snapshot.getReservation().getStationName())
                                + " - " + snapshot.getReservation().getStatus())
                        .setOnClickListener(() -> getScreenManager().push(
                                new ReservationStatusCarScreen(getCarContext())))
                        .build());
            }
        }

        list.addItem(new Row.Builder()
                .setTitle("Trip history")
                .addText("Your recent trips")
                .setOnClickListener(() -> getScreenManager().push(
                        new TripHistoryCarScreen(getCarContext())))
                .build());
        list.addItem(new Row.Builder()
                .setTitle("Reservations")
                .addText("Manage your reservations")
                .setOnClickListener(() -> getScreenManager().push(
                        new ReservationStatusCarScreen(getCarContext())))
                .build());
        list.addItem(new Row.Builder()
                .setTitle("Vehicle")
                .addText("Battery, range and arrival reserve")
                .setOnClickListener(() -> getScreenManager().push(
                        new VehicleCarScreen(getCarContext())))
                .build());
        list.addItem(new Row.Builder()
                .setTitle("Settings")
                .addText("Car connection and app details")
                .setOnClickListener(() -> getScreenManager().push(
                        new CarSettingsScreen(getCarContext())))
                .build());

        return new ListTemplate.Builder()
                .setTitle("Amperly")
                .setSingleList(list.build())
                .setActionStrip(buildActionStrip())
                .build();
    }

    /** The strip along the bottom of the whole screen. */
    private ActionStrip buildActionStrip() {
        return new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("SOS")
                        .setOnClickListener(() -> {
                            CarToast.makeText(getCarContext(),
                                    "Amperly is not an emergency service. Call 112 for help.",
                                    CarToast.LENGTH_LONG).show();
                            getScreenManager().push(new ReservationStatusCarScreen(getCarContext()));
                        })
                        .build())
                .build();
    }

    /** The strip that sits over the map, where the glanceable actions belong. */
    private ActionStrip buildMapActionStrip() {
        return new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("Battery")
                        .setOnClickListener(() -> getScreenManager().push(
                                new SoCUpdateCarScreen(getCarContext())))
                        .build())
                .addAction(new Action.Builder()
                        .setTitle("Chargers")
                        .setOnClickListener(() -> getScreenManager().push(
                                new ChargerListCarScreen(getCarContext())))
                        .build())
                .addAction(new Action.Builder()
                        .setTitle("Navigate")
                        .setOnClickListener(() -> getScreenManager().push(
                                new NavigationCarScreen(getCarContext())))
                        .build())
                .build();
    }
}
