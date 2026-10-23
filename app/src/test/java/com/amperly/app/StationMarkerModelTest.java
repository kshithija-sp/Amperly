package com.amperly.app;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.map.StationMarkerModel;
import com.amperly.app.data.local.entity.ChargerStation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Marker styling and wording for user-registered chargers.
 *
 * <p>The map must not imply paid billing where there is no verified meter, and it must distinguish
 * charger states without relying on colour alone.
 */
public class StationMarkerModelTest {

    private ChargerStation station(String verificationLevel, boolean dc) {
        ChargerStation station = ChargerStation.create("st_1", "host_1", "Host", "Charger",
                "Somewhere", 12.30, 76.64, AppConstants.LOCATION_TYPE_CAMPUS, "24/7", 18.0, 5.0,
                4.0, verificationLevel, dc ? "CCS_2_DC" : "TYPE_2_AC", dc ? 60 : 7.2, 2,
                "Contact the host.", 4.5);
        return station;
    }

    @Test
    public void aMeterlessStationSaysDiscoveryOnly() {
        String label = StationMarkerModel.supportsBillingLabel(
                station(AppConstants.LEVEL_3_NO_VERIFIED_METER, false));
        assertTrue(label.toLowerCase().contains("no verified meter"));
        assertTrue(label.toLowerCase().contains("no automatic billing"));
    }

    @Test
    public void aVerifiedStationSaysPaidBillingIsAvailable() {
        String label = StationMarkerModel.supportsBillingLabel(
                station(AppConstants.LEVEL_1_VERIFIED_MANUAL, false));
        assertTrue(label.toLowerCase().contains("verified meter"));
    }

    @Test
    public void theSnippetCarriesConnectorPowerAndPorts() {
        String snippet = StationMarkerModel.snippet(station(AppConstants.LEVEL_1_VERIFIED_MANUAL, true));
        assertTrue(snippet.contains("CCS_2_DC"));
        assertTrue(snippet.contains("60"));
        assertTrue(snippet.contains("port"));
    }

    @Test
    public void aMeterlessMarkerIsStyledDifferentlyFromAVerifiedOne() {
        float verified = StationMarkerModel.hue(station(AppConstants.LEVEL_1_VERIFIED_MANUAL, false), false);
        float discoveryOnly = StationMarkerModel.hue(station(AppConstants.LEVEL_3_NO_VERIFIED_METER, false), false);
        assertNotEquals("colour alone must distinguish a non-billable charger", verified, discoveryOnly, 1e-6);
    }

    @Test
    public void aHighlightedMarkerOverridesTheStateColour() {
        float normal = StationMarkerModel.hue(station(AppConstants.LEVEL_1_VERIFIED_MANUAL, false), false);
        float highlighted = StationMarkerModel.hue(station(AppConstants.LEVEL_1_VERIFIED_MANUAL, false), true);
        assertNotEquals(normal, highlighted, 1e-6);
    }

    @Test
    public void theSnippetNeverLeaksHostContactDetails() {
        ChargerStation station = station(AppConstants.LEVEL_1_VERIFIED_MANUAL, false);
        station.setHostName("+91 90000 00000");
        String snippet = StationMarkerModel.snippet(station);
        assertFalse(snippet.contains("+91"));
    }
}
