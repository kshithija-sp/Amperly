package com.amperly.app.car;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;

import com.amperly.app.car.screen.ChargerDetailsCarScreen;
import com.amperly.app.car.screen.ChargerListCarScreen;
import com.amperly.app.car.screen.NavigationCarScreen;
import com.amperly.app.car.screen.ReservationStatusCarScreen;
import com.amperly.app.car.screen.RouteOverviewCarScreen;
import com.amperly.app.car.screen.SoCUpdateCarScreen;

public class CarScreenFactory {

    public enum ScreenType {
        ROUTE_OVERVIEW,
        NAVIGATION,
        CHARGER_LIST,
        CHARGER_DETAILS,
        SOC_UPDATE,
        RESERVATION_STATUS
    }

    /** Screen with no extra arguments. Charger details has no station yet, so it stays on the list. */
    public static Screen createScreen(ScreenType type, CarContext carContext) {
        return createScreen(type, carContext, null);
    }

    public static Screen createScreen(ScreenType type, CarContext carContext, String stationId) {
        switch (type) {
            case CHARGER_LIST:
                return new ChargerListCarScreen(carContext);
            case CHARGER_DETAILS:
                return new ChargerDetailsCarScreen(carContext, stationId);
            case SOC_UPDATE:
                return new SoCUpdateCarScreen(carContext);
            case RESERVATION_STATUS:
                return new ReservationStatusCarScreen(carContext);
            case NAVIGATION:
                return new NavigationCarScreen(carContext);
            case ROUTE_OVERVIEW:
            default:
                return new RouteOverviewCarScreen(carContext);
        }
    }
}
