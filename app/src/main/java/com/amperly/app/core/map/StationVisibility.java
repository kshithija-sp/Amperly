package com.amperly.app.core.map;

import androidx.annotation.Nullable;

import com.amperly.app.data.local.entity.ChargerStation;

/**
 * The one place that decides whether a user-registered charger may appear as a public, bookable
 * marker.
 *
 * <pre>
 * station.approvalStatus == APPROVED
 * AND station.isPaused   == false
 * AND the coordinates are real
 * </pre>
 *
 * <p>A pending or rejected station never appears; a paused station is hidden from the bookable map.
 * Banned hosts are filtered out before this point by the repository, which only returns stations
 * whose host is not under a ban. Keeping the rule pure and separate from the Google map means it
 * can be unit tested without any map, network or device.
 */
public final class StationVisibility {

    private StationVisibility() {
    }

    /** True when the station may be drawn as a bookable public marker. */
    public static boolean isPlotable(@Nullable ChargerStation station) {
        if (station == null) {
            return false;
        }
        if (!"APPROVED".equals(station.getApprovalStatus())) {
            return false;
        }
        if (station.isPaused()) {
            return false;
        }
        return hasValidCoordinates(station.getLatitude(), station.getLongitude());
    }

    /** True when a latitude/longitude pair is real rather than the (0, 0) "null island" default. */
    public static boolean hasValidCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            return false;
        }
        if (longitude < -180 || longitude > 180) {
            return false;
        }
        return !(latitude == 0 && longitude == 0);
    }
}
