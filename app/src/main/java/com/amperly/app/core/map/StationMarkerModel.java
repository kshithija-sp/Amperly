package com.amperly.app.core.map;

import androidx.annotation.NonNull;

import com.amperly.app.data.local.entity.ChargerStation;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.util.Locale;

/**
 * Maps a user-registered {@link ChargerStation} onto what a Google map marker should look like.
 *
 * <p>The hosted chargers are the product, so the marker has to answer three questions at a glance:
 * is this one usable by my car, is it available, and does it charge by a verified meter (so paid
 * billing works)? Colour alone is not an answer — the snippet carries the same information in text
 * for colour-blind drivers and for the car display.
 *
 * <p>No host phone number, email or account id is ever placed in marker text; only the public
 * marketplace facts.
 */
public final class StationMarkerModel {

    private StationMarkerModel() {
    }

    /**
     * The marker hue. Recommended/selected chargers and incompatible or unverified ones are pulled
     * apart so the map reads without consulting the legend.
     */
    public static float hue(@NonNull ChargerStation station, boolean highlighted) {
        if (highlighted) {
            return BitmapDescriptorFactory.HUE_ORANGE;
        }
        if (station.isPaused()) {
            return BitmapDescriptorFactory.HUE_VIOLET;
        }
        if (!station.supportsPaidEnergyBilling()) {
            // No verified meter: discovery only, never a paid energy session.
            return BitmapDescriptorFactory.HUE_YELLOW;
        }
        if (station.isDcCapable()) {
            return BitmapDescriptorFactory.HUE_GREEN;
        }
        return BitmapDescriptorFactory.HUE_AZURE;
    }

    /** One line of public facts shown in the info window under the station name. */
    @NonNull
    public static String snippet(@NonNull ChargerStation station) {
        StringBuilder builder = new StringBuilder();
        builder.append(station.getConnectorTypes() == null || station.getConnectorTypes().isEmpty()
                ? "Connector not published" : station.getConnectorTypes());
        builder.append(" · ").append(String.format(Locale.US, "%.1f", station.getMaxPowerKw()))
                .append(" kW");
        builder.append(" · ").append(station.getPortCount()).append(" port")
                .append(station.getPortCount() == 1 ? "" : "s");
        builder.append("\n").append(supportsBillingLabel(station));
        return builder.toString();
    }

    /** A plain-language meter label, honest about whether paid billing applies. */
    @NonNull
    public static String supportsBillingLabel(@NonNull ChargerStation station) {
        if (!station.supportsPaidEnergyBilling()) {
            return "No verified meter — discovery only, no automatic billing";
        }
        return "Verified meter — paid energy session available";
    }
}
