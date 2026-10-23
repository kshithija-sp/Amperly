package com.amperly.app.feature.consumer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amperly.app.R;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.common.GoogleConfig;
import com.amperly.app.core.location.LocationProvider;
import com.amperly.app.core.map.MapPanel;
import com.amperly.app.core.nav.GoogleNavigationProvider;
import com.amperly.app.core.nav.NavigationProvider;
import com.amperly.app.core.nav.NavigationState;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.core.nav.RouteProgress;
import com.amperly.app.core.routing.DirectionsRoute;
import com.amperly.app.core.routing.GoogleDirectionsClient;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.core.state.SocStore;
import com.amperly.app.ui.Ux;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * In-app navigation, inside Amperly, on Google Maps.
 *
 * <p>The route comes from the Google {@link NavigationProvider}; the map is the Google Maps SDK;
 * the driver-safe strip shows only the next maneuver, the remaining distance and the ETA. While the
 * session is active the screen re-reads the device position on a short timer and advances the
 * progress along the real road geometry — it never keeps announcing a stale instruction after the
 * session stops.
 *
 * <p>Spoken voice guidance is deliberately not simulated here: it is supplied by the Google
 * Navigation SDK, whose licence is an external setup step documented in the migration guide. The
 * screen says so rather than pretending to speak.
 */
public class InAppNavigationFragment extends Fragment {

    public static final String ARG_ORIGIN_LAT = "origin_lat";
    public static final String ARG_ORIGIN_LNG = "origin_lng";
    public static final String ARG_DEST_LAT = "dest_lat";
    public static final String ARG_DEST_LNG = "dest_lng";
    public static final String ARG_LABEL = "label";

    private static final long PROGRESS_INTERVAL_MS = 6000L;

    private final NavigationProvider provider = new GoogleNavigationProvider();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView nextTurnText;
    private TextView instructionText;
    private TextView remainingText;
    private TextView etaText;
    private TextView statusText;
    private MapPanel mapPanel;

    private double originLat;
    private double originLng;
    private double destLat;
    private double destLng;
    private String label = "";
    private DirectionsRoute route;
    private boolean navigating;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_in_app_navigation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            Ux.toast(this, getString(R.string.nav_route_failed));
            return;
        }
        originLat = args.getDouble(ARG_ORIGIN_LAT);
        originLng = args.getDouble(ARG_ORIGIN_LNG);
        destLat = args.getDouble(ARG_DEST_LAT);
        destLng = args.getDouble(ARG_DEST_LNG);
        String rawLabel = args.getString(ARG_LABEL);
        label = rawLabel == null || rawLabel.isEmpty() ? "your destination" : rawLabel;

        nextTurnText = view.findViewById(R.id.navNextTurnText);
        instructionText = view.findViewById(R.id.navInstructionText);
        remainingText = view.findViewById(R.id.navRemainingText);
        etaText = view.findViewById(R.id.navEtaText);
        statusText = view.findViewById(R.id.navStatusText);

        view.findViewById(R.id.navStopButton).setOnClickListener(v -> stopNavigation());
        view.findViewById(R.id.navUpdateBatteryButton).setOnClickListener(v -> promptBatteryUpdate());

        ViewGroup mapContainer = view.findViewById(R.id.navMapContainer);
        MapPanel.attach(this, mapContainer, panel -> {
            mapPanel = panel;
            startNavigation();
        });

        if (!GoogleConfig.isConfigured()) {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText(GoogleConfig.setupMessage());
        }
    }

    private void startNavigation() {
        if (!GoogleConfig.isConfigured()) {
            return;
        }
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(R.string.nav_starting);
        NavigationProvider.RouteRequest request = new NavigationProvider.RouteRequest(originLat,
                originLng, destLat, destLng, null, GoogleDirectionsClient.TravelMode.DRIVING);
        provider.calculateRoute(request, new NavigationProvider.RouteCallback() {
            @Override
            public void onRoute(@NonNull NavigationProvider.RouteResult result) {
                if (!isAdded()) {
                    return;
                }
                route = result.getRoute();
                provider.startNavigation(new NavigationProvider.NavigationRequest(result,
                                GoogleNavigationProvider.AUDIO_VOICE_ALERTS_AND_GUIDANCE
                                        | GoogleNavigationProvider.AUDIO_BLUETOOTH),
                        new NavigationProvider.NavigationCallback() {
                            @Override
                            public void onStarted(@NonNull NavigationState state) {
                                navigating = true;
                                render(state);
                                scheduleProgress();
                            }

                            @Override
                            public void onRerouted(@NonNull NavigationState state) {
                                render(state);
                            }

                            @Override
                            public void onArrived(@NonNull NavigationState state) {
                                arriving();
                            }

                            @Override
                            public void onStopped() {
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                showStatus(message);
                            }
                        });
            }

            @Override
            public void onError(@NonNull String message) {
                if (isAdded()) {
                    showStatus(message);
                }
            }
        });
    }

    /** Keeps the next-maneuver card current by re-reading the device position on a short timer. */
    private void scheduleProgress() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!navigating || !isAdded()) {
                    return;
                }
                refreshProgress();
                handler.postDelayed(this, PROGRESS_INTERVAL_MS);
            }
        }, PROGRESS_INTERVAL_MS);
    }

    private void refreshProgress() {
        if (route == null || mapPanel == null) {
            return;
        }
        if (!LocationProvider.hasLocationPermission(requireContext())) {
            return;
        }
        LocationProvider.requestCurrentLocation(requireContext(),
                new LocationProvider.LocationCallback() {
                    @Override
                    public void onLocationRetrieved(double latitude, double longitude) {
                        if (isAdded() && navigating) {
                            applyPosition(latitude, longitude);
                        }
                    }
                });
    }

    private void applyPosition(double latitude, double longitude) {
        DirectionsRoute current = route;
        if (current == null || current.getPoints().size() < 2) {
            return;
        }
        double fraction = RouteProgress.fractionAlong(current.getPoints(), latitude, longitude);
        double remainingKm = RouteProgress.remainingKm(current, fraction);
        int remainingMinutes = RouteProgress.remainingMinutes(current, fraction);

        GoogleNavigationProvider google = provider instanceof GoogleNavigationProvider
                ? (GoogleNavigationProvider) provider : null;
        if (google != null) {
            google.updateProgress(latitude, longitude, remainingKm, remainingMinutes,
                    (int) Math.round(fraction * 100));
        }
        render(provider.getCurrentNavigationState());
        mapPanel.centerOn(latitude, longitude, 16f);
    }

    private void render(@Nullable NavigationState state) {
        DirectionsRoute current = state != null && state.getRoute() != null ? state.getRoute() : route;
        if (current == null) {
            return;
        }
        double fraction = state == null ? 0 : state.getTravelledPercent() / 100d;
        double remainingKm = state == null ? current.getDistanceKm() : state.getRemainingDistanceKm();
        int remainingMinutes = state == null ? current.getEffectiveDurationMinutes()
                : state.getRemainingMinutes();

        DirectionsRoute.Step next = RouteProgress.nextStep(current, fraction);
        if (next != null && !next.getInstruction().isEmpty()) {
            nextTurnText.setVisibility(View.VISIBLE);
            nextTurnText.setText(getString(R.string.nav_next_maneuver, next.getDistanceText().isEmpty()
                    ? "continue" : next.getDistanceText()));
            instructionText.setText(next.getInstruction());
        } else {
            nextTurnText.setVisibility(View.VISIBLE);
            nextTurnText.setText(getString(R.string.nav_next_maneuver, "continue"));
            instructionText.setText("Continue on the route to " + label + ".");
        }

        remainingText.setText(getString(R.string.nav_remaining, remainingKm,
                Formatters.minutes(remainingMinutes)));
        etaText.setText(getString(R.string.nav_eta, arrivalClock(remainingMinutes),
                (int) Math.round(fraction * 100)));
    }

    private String arrivalClock(int remainingMinutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, Math.max(0, remainingMinutes));
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(calendar.getTimeInMillis()));
    }

    private void arriving() {
        navigating = false;
        handler.removeCallbacksAndMessages(null);
        Ux.toast(this, getString(R.string.nav_arrived, label));
        provider.stopNavigation();
        if (isAdded()) {
            Navigator.pop(this);
        }
    }

    private void promptBatteryUpdate() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.route_current_soc);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.soc_update_title)
                .setMessage("Enter the battery percentage shown on your dashboard. The remaining "
                        + "route and charger recommendations are recalculated from it.")
                .setView(input)
                .setNegativeButton(R.string.action_close, null)
                .setPositiveButton(R.string.soc_update_action, (dialog, which) -> {
                    int soc = parseInt(input.getText().toString());
                    if (soc < 0 || soc > 100) {
                        Ux.toast(this, "Battery percentage must be between 0 and 100.");
                        return;
                    }
                    AppStateStore.get().applySocCorrection(AppStateStore.get().getCurrentSoc(), soc);
                    SocStore.save(getContext(), AuthManager.getInstance().getCurrentUserId(), soc);
                    AppStateStore.get().setLastRecalculationSummary(
                            "Dashboard value " + soc + "% is now authoritative for the remaining trip.");
                    recalculateFromPosition();
                })
                .show();
    }

    /** Recalculates from the latest real position after the driver corrects the battery. */
    private void recalculateFromPosition() {
        if (!LocationProvider.hasLocationPermission(requireContext())) {
            return;
        }
        LocationProvider.requestCurrentLocation(requireContext(),
                new LocationProvider.LocationCallback() {
                    @Override
                    public void onLocationRetrieved(double latitude, double longitude) {
                        if (!isAdded()) {
                            return;
                        }
                        originLat = latitude;
                        originLng = longitude;
                        showStatus(getString(R.string.nav_off_route));
                        NavigationProvider.RouteRequest request = new NavigationProvider.RouteRequest(
                                latitude, longitude, destLat, destLng, null,
                                GoogleDirectionsClient.TravelMode.DRIVING);
                        provider.recalculate(request, new NavigationProvider.RouteCallback() {
                            @Override
                            public void onRoute(@NonNull NavigationProvider.RouteResult result) {
                                if (!isAdded()) {
                                    return;
                                }
                                route = result.getRoute();
                                if (provider instanceof GoogleNavigationProvider) {
                                    ((GoogleNavigationProvider) provider).applyReroute(route, null);
                                }
                                if (mapPanel != null) {
                                    mapPanel.plotRoute(route, latitude, longitude, destLat, destLng,
                                            null, null);
                                }
                                render(provider.getCurrentNavigationState());
                                statusText.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                showStatus(message);
                            }
                        });
                    }
                });
    }

    private void stopNavigation() {
        navigating = false;
        handler.removeCallbacksAndMessages(null);
        provider.stopNavigation();
        if (isAdded()) {
            Navigator.pop(this);
        }
    }

    private void showStatus(String message) {
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Navigation must not keep announcing stale instructions once the screen is gone.
        handler.removeCallbacksAndMessages(null);
    }
}
