package com.amperly.app.car.screen;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarText;
import androidx.car.app.model.DateTimeWithZone;
import androidx.car.app.model.Distance;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.MessageInfo;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.navigation.model.RoutingInfo;
import androidx.car.app.navigation.model.Step;
import androidx.car.app.navigation.model.TravelEstimate;

import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.RoutePlan;
import com.amperly.app.data.local.entity.RouteStop;
import com.amperly.app.data.repository.AmperlyRepository;

import java.util.List;
import java.util.TimeZone;

/**
 * The driver-safe navigation view on the car display.
 *
 * <p>This is the Android Auto side of the Google navigation session: the trip, the driver's battery
 * value and the one safe action (update the actual battery) are projected in the car, while the map
 * and the spoken guidance stay on the phone. No payment form, meter reading or host control ever
 * reaches this surface — everything it shows is glanceable in a second.
 *
 * <p>The car app library's navigation template is used, which is what a navigation-category
 * {@code CarAppService} must project. When the Google Navigation SDK licence is enabled, its
 * {@code NavigationViewForAuto} replaces this template; the shared {@link AppStateStore} already
 * carries the trip and battery the SDK needs, so the swap is confined to this screen.
 */
public class NavigationCarScreen extends Screen {

    private final AmperlyRepository repo;
    private List<RouteStop> stops;
    private boolean loaded;

    public NavigationCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        this.repo = AmperlyRepository.getInstance(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        RoutePlan plan = AppStateStore.get().getActivePlan();
        int soc = AppStateStore.get().getCurrentSoc();
        boolean authoritative = AppStateStore.get().isSocAuthoritative();

        Action batteryAction = new Action.Builder()
                .setTitle("Battery")
                .setOnClickListener(() -> getScreenManager().push(new SoCUpdateCarScreen(getCarContext())))
                .build();

        Action stopAction = new Action.Builder()
                .setTitle("Stop")
                .setOnClickListener(() -> getScreenManager().pop())
                .build();

        ActionStrip strip = new ActionStrip.Builder()
                .addAction(batteryAction)
                .addAction(stopAction)
                .build();

        if (plan == null) {
            NavigationTemplate.NavigationInfo info = new MessageInfo.Builder("No active trip")
                    .setText("Plan a trip on your phone, then reconnect. Charging stays glanceable here.")
                    .build();
            return new NavigationTemplate.Builder()
                    .setNavigationInfo(info)
                    .setActionStrip(strip)
                    .build();
        }

        if (!loaded) {
            loaded = true;
            final String planId = plan.getId();
            repo.background(() -> repo.getRouteStopsSync(planId), value -> {
                stops = value;
                invalidate();
            });
        }

        Step current = new Step.Builder()
                .setCue("Continue toward " + plan.getDestination())
                .setRoad(Math.round(plan.getTotalDistanceKm()) + " km total")
                .build();
        Step next = new Step.Builder()
                .setCue("Battery " + soc + "% (" + (authoritative ? "actual" : "estimated") + ")")
                .build();

        NavigationTemplate.NavigationInfo info = new RoutingInfo.Builder()
                .setCurrentStep(current, Distance.create(Math.max(0, plan.getTotalDistanceKm()),
                        Distance.UNIT_KILOMETERS))
                .setNextStep(next)
                .build();

        int remainingMinutes = Math.max(0, plan.getTotalJourneyMinutes());
        long arrivalMillis = System.currentTimeMillis() + remainingMinutes * 60_000L;
        TravelEstimate travelEstimate = new TravelEstimate.Builder(
                Distance.create(Math.max(0, plan.getTotalDistanceKm()), Distance.UNIT_KILOMETERS),
                DateTimeWithZone.create(arrivalMillis, TimeZone.getDefault()))
                .setRemainingTimeSeconds(remainingMinutes * 60L)
                .setTripText(tripText(plan))
                .build();

        return new NavigationTemplate.Builder()
                .setNavigationInfo(info)
                .setDestinationTravelEstimate(travelEstimate)
                .setActionStrip(strip)
                .build();
    }

    /** The trip line shown under the travel estimate: destination and next charging stop. */
    @NonNull
    private CarText tripText(@NonNull RoutePlan plan) {
        StringBuilder line = new StringBuilder(plan.getOrigin()).append(" → ")
                .append(plan.getDestination());
        String stopName = nextStopName();
        if (stopName != null) {
            line.append(" · next stop: ").append(stopName);
        }
        return CarText.create(line.toString());
    }

    /** The first planned charging stop, read from the trip plan already shared with the phone. */
    @Nullable
    private String nextStopName() {
        if (stops == null) {
            return null;
        }
        for (RouteStop stop : stops) {
            if (!stop.isBackup()) {
                return stop.getStationName();
            }
        }
        return null;
    }
}
