package com.amperly.app.core.map;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.amperly.app.R;
import com.amperly.app.core.common.GoogleConfig;
import com.amperly.app.core.routing.DirectionsRoute;
import com.amperly.app.data.local.entity.ChargerStation;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The production map: Google Maps SDK for Android.
 *
 * <p>This replaces the OpenStreetMap tile renderer. Everything drawn here — the base map, the
 * user-registered charger markers and the road route — comes from Google, using the key injected at
 * build time. When that key is missing the panel shows an actionable setup notice instead of a
 * blank map.
 *
 * <p>A {@link SupportMapFragment} is added to the screen's own container, so the map follows the
 * fragment lifecycle automatically and asking for the same panel twice (as the discovery screen
 * does) reuses it instead of rebuilding.
 */
public final class MapPanel {

    /** Reports a charger the driver tapped, so the screen can open its details. */
    public interface MarkerTap {
        void onStation(@Nullable ChargerStation station);
    }

    /** Reports a point the driver tapped on the map, used to place a charger or a trip origin. */
    public interface MapTap {
        void onPoint(double latitude, double longitude);
    }

    /** A ready-to-use map. */
    public interface Ready {
        void onReady(@NonNull MapPanel panel);
    }

    /** One map per container, so a re-attach reuses the same GoogleMap. */
    private static final Map<ViewGroup, MapPanel> PANELS = new WeakHashMap<>();

    private final GoogleMap map;
    private final Map<String, ChargerStation> stationsById = new java.util.HashMap<>();
    @Nullable private MarkerTap markerTap;
    @Nullable private MapTap mapTap;
    @Nullable private Marker pickerMarker;

    private MapPanel(@NonNull GoogleMap map) {
        this.map = map;
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);
    }

    // ------------------------------------------------------------- attaching

    /**
     * Shows a Google map inside {@code container}. Building the fragment is deferred to the
     * container's first layout so creating the map never delays the rest of the screen from
     * painting.
     */
    public static void attach(@NonNull Fragment parent, @Nullable ViewGroup container,
                              @NonNull Ready callback) {
        if (container == null) {
            return;
        }
        if (!GoogleConfig.isConfigured()) {
            showSetupNotice(parent, container);
            return;
        }
        container.post(() -> {
            // The screen may have been replaced before this ran (a tab switch, a back press).
            // Touching getChildFragmentManager() on a detached fragment throws, and the crash
            // lands on the main thread with no screen left to catch it.
            if (!parent.isAdded() || container.getParent() == null) {
                return;
            }
            MapPanel existing = PANELS.get(container);
            if (existing != null) {
                callback.onReady(existing);
                return;
            }
            FragmentManager childManager = parent.getChildFragmentManager();
            final String tag = "amperly-map-" + container.getId();
            SupportMapFragment fragment = (SupportMapFragment) childManager.findFragmentByTag(tag);
            if (fragment == null) {
                fragment = SupportMapFragment.newInstance();
                childManager.beginTransaction().replace(container.getId(), fragment, tag)
                        .commitAllowingStateLoss();
            }
            final SupportMapFragment mapFragment = fragment;
            mapFragment.getMapAsync(googleMap -> {
                if (!parent.isAdded()) {
                    return;
                }
                if (googleMap == null) {
                    showSetupNotice(parent, container);
                    return;
                }
                MapPanel panel = new MapPanel(googleMap);
                PANELS.put(container, panel);
                panel.map.setOnMarkerClickListener(marker -> {
                    if (panel.markerTap != null) {
                        panel.markerTap.onStation(panel.stationsById.get(marker.getId()));
                    }
                    return panel.markerTap != null;
                });
                panel.map.setOnMarkerDragListener(new com.google.android.gms.maps.GoogleMap.OnMarkerDragListener() {
                    @Override
                    public void onMarkerDragStart(@NonNull Marker marker) {
                    }

                    @Override
                    public void onMarkerDrag(@NonNull Marker marker) {
                    }

                    @Override
                    public void onMarkerDragEnd(@NonNull Marker marker) {
                        if (panel.mapTap != null) {
                            panel.mapTap.onPoint(marker.getPosition().latitude,
                                    marker.getPosition().longitude);
                        }
                    }
                });
                callback.onReady(panel);
            });
        });
    }

    /** Replaces the map with a clear notice when no key was configured at build time. */
    private static void showSetupNotice(@NonNull Fragment parent, @NonNull ViewGroup container) {
        Context context;
        try {
            context = parent.requireContext();
        } catch (IllegalStateException e) {
            return;
        }
        container.removeAllViews();
        TextView notice = new TextView(context);
        notice.setText(GoogleConfig.setupMessage());
        notice.setTextAppearance(R.style.TextAppearance_Amperly_Body);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(24, 24, 24, 24);
        notice.setTextColor(ContextCompat.getColor(context, R.color.muted));
        container.addView(notice, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ------------------------------------------------------------- plotting

    public void setMarkerTapListener(@Nullable MarkerTap listener) {
        this.markerTap = listener;
    }

    public void setMapTapListener(@Nullable MapTap listener) {
        this.mapTap = listener;
        this.map.setOnMapClickListener(listener == null ? null : latLng ->
                listener.onPoint(latLng.latitude, latLng.longitude));
    }

    /** Centres the camera on a point. */
    public void centerOn(double latitude, double longitude, float zoom) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), zoom));
    }

    /**
     * Draws the user-registered chargers and, when known, the driver's own position. The camera
     * follows the driver when there is a fix, otherwise it frames every marker.
     */
    public void plotStations(@Nullable List<ChargerStation> stations,
                             @Nullable String highlightStationId,
                             @Nullable Double userLat, @Nullable Double userLng) {
        map.clear();
        stationsById.clear();
        List<ChargerStation> safe = stations == null ? new ArrayList<>() : stations;
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        boolean any = false;

        for (ChargerStation station : safe) {
            if (!isPlotable(station)) {
                continue;
            }
            boolean highlighted = station.getId().equals(highlightStationId);
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(new LatLng(station.getLatitude(), station.getLongitude()))
                    .title(station.getName())
                    .snippet(StationMarkerModel.snippet(station))
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            StationMarkerModel.hue(station, highlighted))));
            if (marker != null) {
                stationsById.put(marker.getId(), station);
            }
            bounds.include(new LatLng(station.getLatitude(), station.getLongitude()));
            any = true;
        }

        if (userLat != null && userLng != null) {
            map.addMarker(new MarkerOptions()
                    .position(new LatLng(userLat, userLng))
                    .title("Your location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            bounds.include(new LatLng(userLat, userLng));
            any = true;
        }

        if (!any) {
            return;
        }
        if (userLat != null && userLng != null) {
            // "Where am I, what is near me": the driver's own position wins the camera, and nearby
            // chargers fall inside the view on their own.
            centerOn(userLat, userLng, 13f);
        } else {
            frameTo(bounds);
        }
    }

    /** Draws a road route with its endpoints and the charging stops along it. */
    public void plotRoute(@Nullable DirectionsRoute route, double originLat, double originLng,
                          double destinationLat, double destinationLng,
                          @Nullable List<ChargerStation> corridorStations,
                          @Nullable String highlightStationId) {
        map.clear();
        stationsById.clear();
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        boolean any = false;

        if (route != null && route.getPoints().size() >= 2) {
            List<LatLng> line = new ArrayList<>();
            for (double[] point : route.getPoints()) {
                line.add(new LatLng(point[0], point[1]));
                bounds.include(new LatLng(point[0], point[1]));
            }
            map.addPolyline(new PolylineOptions().addAll(line).width(10f).color(
                    route.isApproximate() ? Color.parseColor("#B45309") : Color.parseColor("#1D4ED8")));
            any = true;
        }

        map.addMarker(new MarkerOptions().position(new LatLng(originLat, originLng))
                .title("Start").icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_AZURE)));
        map.addMarker(new MarkerOptions().position(new LatLng(destinationLat, destinationLng))
                .title("Destination").icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_ROSE)));
        bounds.include(new LatLng(originLat, originLng));
        bounds.include(new LatLng(destinationLat, destinationLng));
        any = true;

        if (corridorStations != null) {
            for (ChargerStation station : corridorStations) {
                if (!isPlotable(station)) {
                    continue;
                }
                boolean highlighted = station.getId().equals(highlightStationId);
                Marker marker = map.addMarker(new MarkerOptions()
                        .position(new LatLng(station.getLatitude(), station.getLongitude()))
                        .title(station.getName())
                        .snippet(StationMarkerModel.snippet(station))
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                StationMarkerModel.hue(station, highlighted))));
                if (marker != null) {
                    stationsById.put(marker.getId(), station);
                }
                bounds.include(new LatLng(station.getLatitude(), station.getLongitude()));
            }
        }

        if (any) {
            frameTo(bounds);
        }
    }

    /**
     * Shows a single pinned point, with an optional drag-to-place listener, used when a host places
     * a charger or a driver sets a trip origin by hand.
     */
    public void plotPicker(double latitude, double longitude, @Nullable String label,
                           boolean draggable) {
        map.clear();
        stationsById.clear();
        MarkerOptions options = new MarkerOptions()
                .position(new LatLng(latitude, longitude))
                .title(label == null ? "Selected point" : label)
                .draggable(draggable);
        pickerMarker = map.addMarker(options);
        centerOn(latitude, longitude, 16f);
    }

    /** Moves the picker marker without rebuilding it, used when a host types new coordinates. */
    public void movePickerTo(double latitude, double longitude) {
        if (pickerMarker != null) {
            pickerMarker.setPosition(new LatLng(latitude, longitude));
        }
        centerOn(latitude, longitude, 16f);
    }

    /** The coordinates of the picker marker, or null when there is none. */
    @Nullable
    public LatLng pickerPosition() {
        return pickerMarker == null ? null : pickerMarker.getPosition();
    }

    private void frameTo(LatLngBounds.Builder bounds) {
        try {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120));
        } catch (IllegalStateException e) {
            // A single point or a degenerate bounds cannot be framed; fall back to a fixed zoom.
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(0, 0), 2f));
        }
    }

    /** Marker visibility is owned by {@link StationVisibility} so it can be unit tested. */
    static boolean isPlotable(@Nullable ChargerStation station) {
        return StationVisibility.isPlotable(station);
    }
}
