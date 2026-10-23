package com.amperly.app;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.map.StationVisibility;
import com.amperly.app.data.local.entity.ChargerStation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which user-registered chargers may appear as bookable public markers.
 *
 * <p>This is a product rule, not a rendering detail: a pending, rejected or paused station must
 * never be bookable, and a station with a made-up coordinate must never be drawn.
 */
public class StationVisibilityTest {

    private ChargerStation station() {
        return ChargerStation.create("st_1", "host_1", "Host", "Campus Charger",
                "Gate 2, Mysore", 12.3010, 76.6450, AppConstants.LOCATION_TYPE_CAMPUS,
                "08:00 - 20:00", 18.0, 5.0, 4.0, AppConstants.LEVEL_1_VERIFIED_MANUAL,
                "TYPE_2_AC", 7.2, 2, "Contact the host.", 4.5);
    }

    @Test
    public void anApprovedUnpausedStationIsPlotable() {
        assertTrue(StationVisibility.isPlotable(station()));
    }

    @Test
    public void aPendingStationIsNotPublic() {
        ChargerStation pending = station();
        pending.setApprovalStatus(AppConstants.APPROVAL_PENDING);
        assertFalse(StationVisibility.isPlotable(pending));
    }

    @Test
    public void aRejectedStationIsNotPublic() {
        ChargerStation rejected = station();
        rejected.setApprovalStatus(AppConstants.APPROVAL_REJECTED);
        assertFalse(StationVisibility.isPlotable(rejected));
    }

    @Test
    public void aPausedStationIsHiddenFromTheBookableMap() {
        ChargerStation paused = station();
        paused.setPaused(true);
        assertFalse(StationVisibility.isPlotable(paused));
    }

    @Test
    public void aMissingCoordinateIsNotPlotted() {
        ChargerStation noFix = station();
        noFix.setLatitude(0);
        noFix.setLongitude(0);
        assertFalse(StationVisibility.isPlotable(noFix));
    }

    @Test
    public void anImpossibleCoordinateIsRejected() {
        assertFalse(StationVisibility.hasValidCoordinates(120, 76));
        assertFalse(StationVisibility.hasValidCoordinates(12, 200));
        assertTrue(StationVisibility.hasValidCoordinates(12.3010, 76.6450));
    }

    @Test
    public void aNullStationIsNeverPlotted() {
        assertFalse(StationVisibility.isPlotable(null));
    }
}
