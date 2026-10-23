package com.amperly.app.feature.consumer;

import android.Manifest;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.common.Formatters;
import com.amperly.app.core.common.GoogleConfig;
import com.amperly.app.core.location.LocationProvider;
import com.amperly.app.core.map.MapPanel;
import com.amperly.app.core.nav.GoogleNavigationProvider;
import com.amperly.app.core.nav.NavigationProvider;
import com.amperly.app.core.places.GoogleGeocoder;
import com.amperly.app.core.places.PlacesAutocomplete;
import com.amperly.app.core.routing.DirectionsRoute;
import com.amperly.app.core.routing.GoogleDirectionsClient;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.core.state.SocStore;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.RoutePlan;
import com.amperly.app.data.local.entity.RouteStop;
import com.amperly.app.data.local.entity.TripEstimate;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.routing.RoutePlannerEngine;
import com.amperly.app.engine.soc.SoCEstimatorEngine;
import com.amperly.app.ui.Ux;
import com.amperly.app.ui.adapter.RouteStopAdapter;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Route and charging plan, on Google Maps.
 *
 * <p>The starting point is the device's real current position — the driver never types it — and the
 * destination is chosen through Google Place autocomplete. Google supplies the road distance and
 * the turn-by-turn directions; Amperly layers its compatibility, detour, battery and safety checks
 * on top of that route. A dashboard battery figure is authoritative the moment the consumer enters
 * it, which removes unreachable chargers and recalculates the remaining trip.
 *
 * <p>Nothing here silently falls back to a pilot coordinate. When the location or the Google key is
 * missing the screen says so and offers an explicit map-pin origin.
 */
public class RoutePlannerFragment extends Fragment implements RouteStopAdapter.Listener {

    private AmperlyRepository repo;
    private TextView originText;
    private TextView originDetailText;
    private TextView destinationResolvedText;
    private TextView summaryText;
    private TextView confidenceText;
    private TextView planStatusText;
    private TextView socSourceText;
    private TextView directionsMetaText;
    private TextView directionsNoteText;
    private EditText destinationEdit;
    private TextInputLayout destinationField;
    private EditText currentSocEdit;
    private EditText actualSocEdit;
    private LinearLayout assumptionsContainer;
    private LinearLayout recalcContainer;
    private LinearLayout directionsContainer;
    private View noSafeChargerCard;
    private View mapContainer;
    private RouteStopAdapter plannedAdapter;
    private RouteStopAdapter backupsAdapter;
    private RouteStopAdapter candidatesAdapter;
    private MapPanel mapPanel;

    private final NavigationProvider navigationProvider = new GoogleNavigationProvider();

    private double corridorKm = AppConstants.DEFAULT_CORRIDOR_KM;
    private String preference = RoutePlannerEngine.PREF_FASTEST;
    private RoutePlannerEngine.RoutePlanResult lastResult;
    private RoutePlan lastPlan;
    private List<ChargerStation> lastCandidates = new ArrayList<>();

    @Nullable private Double originLat;
    @Nullable private Double originLng;
    @Nullable private DirectionsRoute lastRoute;
    @Nullable private PlacesAutocomplete.Selection destination;

    private boolean mapOriginPicking;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(
                        result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(
                        result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) {
                    resolveOrigin();
                } else {
                    showOriginUnavailable();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_route_planner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());

        originText = view.findViewById(R.id.originText);
        originDetailText = view.findViewById(R.id.originDetailText);
        destinationEdit = view.findViewById(R.id.destinationEdit);
        destinationField = view.findViewById(R.id.destinationField);
        destinationResolvedText = view.findViewById(R.id.destinationResolvedText);
        currentSocEdit = view.findViewById(R.id.currentSocEdit);
        actualSocEdit = view.findViewById(R.id.actualSocEdit);
        summaryText = view.findViewById(R.id.routeSummaryText);
        confidenceText = view.findViewById(R.id.confidenceText);
        planStatusText = view.findViewById(R.id.planStatusText);
        socSourceText = view.findViewById(R.id.socSourceText);
        directionsMetaText = view.findViewById(R.id.directionsMetaText);
        directionsNoteText = view.findViewById(R.id.directionsNoteText);
        assumptionsContainer = view.findViewById(R.id.assumptionsContainer);
        recalcContainer = view.findViewById(R.id.recalcContainer);
        directionsContainer = view.findViewById(R.id.directionsContainer);
        noSafeChargerCard = view.findViewById(R.id.noSafeChargerContainer);
        mapContainer = view.findViewById(R.id.routeMapContainer);

        plannedAdapter = new RouteStopAdapter(this);
        backupsAdapter = new RouteStopAdapter(this);
        candidatesAdapter = new RouteStopAdapter(this);
        setupList(view.findViewById(R.id.plannedStopsRecyclerView), plannedAdapter);
        setupList(view.findViewById(R.id.backupsRecyclerView), backupsAdapter);
        setupList(view.findViewById(R.id.candidatesRecyclerView), candidatesAdapter);

        setUpChips(view);
        setUpInputs(view);
        attachMap();
        resolveOrigin();
    }

    private void setUpChips(View view) {
        ((ChipGroup) view.findViewById(R.id.corridorChipGroup))
                .setOnCheckedStateChangeListener((group, ids) -> {
                    if (ids.isEmpty()) {
                        return;
                    }
                    int id = ids.get(0);
                    if (id == R.id.corridor5Chip) {
                        corridorKm = 5;
                    } else if (id == R.id.corridor20Chip) {
                        corridorKm = 20;
                    } else {
                        corridorKm = AppConstants.DEFAULT_CORRIDOR_KM;
                    }
                });

        ((ChipGroup) view.findViewById(R.id.preferenceChipGroup))
                .setOnCheckedStateChangeListener((group, ids) -> {
                    if (ids.isEmpty()) {
                        return;
                    }
                    int id = ids.get(0);
                    if (id == R.id.prefCheapestChip) {
                        preference = RoutePlannerEngine.PREF_CHEAPEST;
                    } else if (id == R.id.prefReliableChip) {
                        preference = RoutePlannerEngine.PREF_RELIABLE;
                    } else if (id == R.id.prefShortestDetourChip) {
                        preference = RoutePlannerEngine.PREF_SHORTEST_DETOUR;
                    } else {
                        preference = RoutePlannerEngine.PREF_FASTEST;
                    }
                });
    }

    private void setUpInputs(View view) {
        Button planButton = view.findViewById(R.id.planRouteButton);
        planButton.setOnClickListener(v -> planRoute());

        view.findViewById(R.id.updateSocButton).setOnClickListener(v -> applySocCorrection(0));
        view.findViewById(R.id.startNavigationButton).setOnClickListener(v -> startInAppNavigation());

        view.findViewById(R.id.setOriginOnMapButton).setOnClickListener(v -> {
            mapOriginPicking = true;
            Ux.toast(this, getString(R.string.route_start_map_hint));
        });

        view.findViewById(R.id.widerRadiusButton).setOnClickListener(v -> {
            corridorKm = 20;
            planStatusText.setVisibility(View.VISIBLE);
            planStatusText.setText("Widening the search radius to 20 km and re-evaluating the corridor…");
            if (originLat != null && originLng != null) {
                planRoute();
            }
        });

        view.findViewById(R.id.slowerChargerButton).setOnClickListener(v -> {
            planStatusText.setVisibility(View.VISIBLE);
            planStatusText.setText("Allowing a slower compatible charger with a 10% arrival reserve instead of "
                    + "your usual reserve. Amperly will not go below this floor.");
            applySocCorrection(10);
        });

        view.findViewById(R.id.emergencyButton).setOnClickListener(v -> showEmergencyGuidance());

        // Google Place autocomplete for the destination. Absent a key the plain field still works,
        // and the typed text is handed to Google Directions as a free-text destination.
        if (PlacesAutocomplete.isAvailable(requireContext())) {
            PlacesAutocomplete.bind(requireContext(), (MaterialAutoCompleteTextView) destinationEdit,
                    new PlacesAutocomplete.Listener() {
                        @Override
                        public void onPlaceSelected(@NonNull PlacesAutocomplete.Selection selection) {
                            destination = selection;
                            destinationField.setError(null);
                            destinationResolvedText.setVisibility(View.VISIBLE);
                            destinationResolvedText.setText(getString(R.string.route_destination_resolved,
                                    selection.getAddress()));
                        }

                        @Override
                        public void onSelectionCleared() {
                            destination = null;
                            destinationField.setError(null);
                            destinationResolvedText.setVisibility(View.GONE);
                        }

                        @Override
                        public void onLookupFailed(@NonNull String message) {
                            // Say why there is no suggestion list rather than leaving a field that
                            // silently never completes.
                            destinationField.setError(message);
                        }
                    });
        } else {
            directionsNoteText.setVisibility(View.VISIBLE);
            directionsNoteText.setText(R.string.map_setup_required);
        }
    }

    private void attachMap() {
        if (!(mapContainer instanceof ViewGroup)) {
            return;
        }
        MapPanel.attach(this, (ViewGroup) mapContainer, panel -> {
            mapPanel = panel;
            mapPanel.setMapTapListener((latitude, longitude) -> {
                if (!mapOriginPicking) {
                    return;
                }
                originLat = latitude;
                originLng = longitude;
                mapOriginPicking = false;
                originText.setText(R.string.route_start_map_pin);
                describeOrigin(latitude, longitude);
                planRoute();
            });
        });
    }

    // ------------------------------------------------------------ origin

    private void resolveOrigin() {
        if (!GoogleConfig.isConfigured()) {
            originText.setText(GoogleConfig.setupMessage());
            showStatus(GoogleConfig.setupMessage());
            return;
        }
        originText.setText(R.string.route_start_locating);
        if (!LocationProvider.hasLocationPermission(requireContext())) {
            if (LocationProvider.hasAskedForPermission(requireContext())) {
                showOriginUnavailable();
                return;
            }
            LocationProvider.markPermissionAsked(requireContext());
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
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
                        originText.setText(R.string.route_start_current);
                        describeOrigin(latitude, longitude);
                        planRoute();
                    }

                    @Override
                    public void onLocationUnavailable() {
                        if (isAdded()) {
                            showOriginUnavailable();
                        }
                    }
                });
    }

    /**
     * Puts the place name under the origin, instead of a raw coordinate pair.
     *
     * <p>The fix is already known — the lookup only names it. So the line shows "looking up the
     * address" while Google answers, then the formatted address, and falls back to saying it is
     * a GPS position. It never blocks routing and never invents a place name.
     */
    private void describeOrigin(double latitude, double longitude) {
        if (originDetailText == null) {
            return;
        }
        originDetailText.setVisibility(View.VISIBLE);
        originDetailText.setText(R.string.route_start_locating_address);

        GoogleGeocoder.reverseGeocode(latitude, longitude, new GoogleGeocoder.Callback() {
            @Override
            public void onAddress(@NonNull String formattedAddress) {
                if (!isAdded() || originDetailText == null) {
                    return;
                }
                originDetailText.setText(getString(R.string.route_start_address, formattedAddress));
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded() || originDetailText == null) {
                    return;
                }
                originDetailText.setText(R.string.route_start_gps_only);
            }
        });
    }

    private void showOriginUnavailable() {
        originText.setText(R.string.route_start_unavailable);
        if (originDetailText != null) {
            originDetailText.setVisibility(View.GONE);
        }
        View pick = getView() == null ? null : getView().findViewById(R.id.setOriginOnMapButton);
        if (pick != null) {
            pick.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------ planning

    private void planRoute() {
        if (originLat == null || originLng == null) {
            Ux.toast(this, getString(R.string.route_needs_origin));
            return;
        }
        String typed = destinationEdit.getText().toString().trim();
        NavigationProvider.RouteRequest request;
        if (destination != null) {
            request = new NavigationProvider.RouteRequest(originLat, originLng,
                    destination.getLatitude(), destination.getLongitude(), null,
                    GoogleDirectionsClient.TravelMode.DRIVING);
        } else if (!TextUtils.isEmpty(typed)) {
            request = NavigationProvider.RouteRequest.toAddress(originLat, originLng, typed,
                    GoogleDirectionsClient.TravelMode.DRIVING);
        } else {
            Ux.toast(this, getString(R.string.route_needs_destination));
            return;
        }

        showStatus(getString(R.string.route_planning));
        navigationProvider.calculateRoute(request, new NavigationProvider.RouteCallback() {
            @Override
            public void onRoute(@NonNull NavigationProvider.RouteResult result) {
                if (!isAdded()) {
                    return;
                }
                lastRoute = result.getRoute();
                runPlan(result);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isAdded()) {
                    return;
                }
                showStatus(message);
                // A free-text destination Google could not geocode has no coordinates to fall back
                // on, so the plan honestly stops instead of inventing a location.
                if (destination != null) {
                    DirectionsRoute estimate = DirectionsRoute.straightLineEstimate(originLat, originLng,
                            destination.getLatitude(), destination.getLongitude(), 45.0);
                    lastRoute = estimate;
                    renderDirections(estimate);
                }
            }
        });
    }

    private void runPlan(@NonNull NavigationProvider.RouteResult routeResult) {
        final DirectionsRoute route = routeResult.getRoute();
        final double distance = route.getDistanceKm();
        final int driveMinutes = route.getEffectiveDurationMinutes();
        final String originLabel = route.getStartAddress().isEmpty()
                ? getString(R.string.route_start_current) : route.getStartAddress();
        final String destinationLabel = route.getEndAddress().isEmpty()
                ? destinationEdit.getText().toString().trim() : route.getEndAddress();

        String socText = currentSocEdit.getText().toString().trim();
        final Integer enteredSoc = TextUtils.isEmpty(socText) ? null : safeInt(socText);
        final String userId = AuthManager.getInstance().getCurrentUserId();
        final double originLatValue = originLat;
        final double originLngValue = originLng;

        repo.background(() -> {
            Vehicle vehicle = repo.getVehicleSync(userId);
            List<ChargerStation> stations = repo.getApprovedStationsSync();

            int startSoc;
            boolean fromProfile = false;
            if (enteredSoc != null) {
                startSoc = Math.max(0, Math.min(100, enteredSoc));
            } else {
                SoCEstimatorEngine.SocEstimate estimate =
                        SoCEstimatorEngine.estimateStartingSoc(vehicle, 45.0);
                startSoc = estimate.getSoc();
                fromProfile = true;
            }

            RoutePlannerEngine.RoutePlanResult result = RoutePlannerEngine.planRoute(originLabel,
                    destinationLabel, originLatValue, originLngValue, route.getDestinationLat(),
                    route.getDestinationLng(), distance, corridorKm, startSoc,
                    AppStateStore.get().isSocAuthoritative(), fromProfile, preference, vehicle,
                    stations, driveMinutes);

            RoutePlan plan = new RoutePlan(UUID.randomUUID().toString(), userId,
                    vehicle == null ? null : vehicle.getId(), originLabel, destinationLabel,
                    originLatValue, originLngValue, route.getDestinationLat(), route.getDestinationLng(),
                    corridorKm, preference, result.getTotalDistanceKm(), result.getDriveMinutes(),
                    result.getDetourMinutes(), result.getChargingMinutes(), result.getTotalJourneyMinutes(),
                    startSoc, result.getArrivalTargetSoc(), AppStateStore.get().isSocAuthoritative(),
                    join(result.getAssumptions()), result.getConfidence(), "ACTIVE",
                    System.currentTimeMillis(), System.currentTimeMillis());
            plan.setRouteProvider(routeResult.getProviderName());
            if (destination != null) {
                plan.setGooglePlaceId(destination.getPlaceId());
            }
            repo.saveRoutePlan(plan);
            repo.replaceRouteStops(plan.getId(), toStops(plan.getId(), result));

            AppStateStore store = AppStateStore.get();
            store.setVehicle(vehicle);
            store.setEstimatedSoc(startSoc);
            SocStore.save(getContext(), userId, startSoc);
            store.setActivePlan(plan);
            store.setLastPlanResult(result);
            store.setUserId(userId);

            return new PlanBundle(result, plan, route, fromProfile);
        }, bundle -> {
            if (bundle == null || !isAdded()) {
                return;
            }
            renderPlan(bundle);
        });
    }

    private void renderPlan(@NonNull PlanBundle bundle) {
        RoutePlannerEngine.RoutePlanResult result = bundle.result;
        lastResult = result;
        lastPlan = bundle.plan;
        lastCandidates = result.getCandidates().isEmpty() ? new ArrayList<>() : stationsOf(result);

        summaryText.setText(getString(R.string.route_summary, result.getOrigin(), result.getDestination(),
                result.getTotalDistanceKm(), Formatters.minutes(result.getDriveMinutes()),
                Formatters.minutes(result.getChargingMinutes()),
                Formatters.minutes(result.getTotalJourneyMinutes())));
        confidenceText.setText(getString(R.string.route_confidence, result.getConfidence())
                + " · corridor " + (int) result.getCorridorKm() + " km · "
                + result.getCandidates().size() + " compatible charger(s) in the corridor · "
                + result.getExcluded().size() + " excluded");

        if (result.getNoStopWarning() != null) {
            showStatus(result.getNoStopWarning());
        } else if (result.getPlannedStops().isEmpty()) {
            showStatus("No charging stop is needed for this trip at " + result.getStartSoc()
                    + "% battery. Chargers along the corridor are listed below as options.");
        } else {
            planStatusText.setVisibility(View.GONE);
        }

        socSourceText.setText(bundle.fromProfile
                ? "Amperly estimated your starting battery from the vehicle profile — enter the dashboard value to override it."
                : (AppStateStore.get().isSocAuthoritative()
                ? getString(R.string.soc_now_authoritative, AppStateStore.get().getCurrentSoc(),
                Formatters.agoFromNow(AppStateStore.get().getSocUpdatedAtMs()))
                : "Using the battery percentage you entered. This is not a reading from the car."));

        renderDirections(bundle.route);

        List<RouteStopAdapter.StopRow> planned = new ArrayList<>();
        for (RoutePlannerEngine.PlannedStop stop : result.getPlannedStops()) {
            planned.add(RouteStopAdapter.fromPlannedStop(stop));
        }
        plannedAdapter.submit(planned);

        List<RouteStopAdapter.StopRow> backups = new ArrayList<>();
        for (RoutePlannerEngine.PlannedStop stop : result.getBackups()) {
            backups.add(RouteStopAdapter.fromPlannedStop(stop));
        }
        backupsAdapter.submit(backups);

        List<RouteStopAdapter.StopRow> candidates = new ArrayList<>();
        int sequence = 1;
        for (RoutePlannerEngine.CandidateEvaluation evaluation : result.getCandidates()) {
            candidates.add(RouteStopAdapter.fromCandidate(evaluation, sequence++, false));
        }
        candidatesAdapter.submit(candidates);

        assumptionsContainer.removeAllViews();
        for (String assumption : result.getAssumptions()) {
            addLine(assumptionsContainer, "• " + assumption);
        }
        for (RoutePlannerEngine.ExcludedStation excluded : result.getExcluded()) {
            addLine(assumptionsContainer, "✕ " + excluded.getStation().getName() + " — "
                    + excluded.getReason());
        }

        if (mapPanel != null) {
            List<ChargerStation> markers = new ArrayList<>();
            for (RoutePlannerEngine.CandidateEvaluation candidate : result.getCandidates()) {
                markers.add(candidate.getStation());
            }
            mapPanel.plotRoute(bundle.route, result.getOriginLat(), result.getOriginLng(),
                    result.getDestinationLat(), result.getDestinationLng(), markers,
                    result.getRecommendedStop() == null ? null
                            : result.getRecommendedStop().getStation().getId());
        }
    }

    /** Renders the Google turn-by-turn steps, clearly attributed to Google Maps. */
    private void renderDirections(@NonNull DirectionsRoute route) {
        directionsContainer.removeAllViews();
        String time = Formatters.minutes(route.getEffectiveDurationMinutes());
        if (route.isApproximate()) {
            directionsMetaText.setText(getString(R.string.route_directions_none));
            directionsMetaText.setText("Straight-line estimate · " + String.format(Locale.US,
                    "%.1f km", route.getDistanceKm()) + " · " + time
                    + " — Google Directions could not be reached.");
        } else if (route.getDurationInTrafficMinutes() > 0
                && route.getDurationInTrafficMinutes() != route.getDurationMinutes()) {
            directionsMetaText.setText(getString(R.string.route_directions_meta_traffic,
                    "Google Maps", route.getDistanceKm(), Formatters.minutes(route.getDurationMinutes()),
                    time));
        } else {
            directionsMetaText.setText(getString(R.string.route_directions_meta, "Google Maps",
                    route.getDistanceKm(), time));
        }

        int index = 1;
        for (DirectionsRoute.Step step : route.getSteps()) {
            StringBuilder line = new StringBuilder();
            line.append(index++).append(". ").append(step.getInstruction());
            if (!step.getDistanceText().isEmpty()) {
                line.append(" — ").append(step.getDistanceText());
            }
            if (!step.getDurationText().isEmpty()) {
                line.append(" (").append(step.getDurationText()).append(")");
            }
            addLine(directionsContainer, line.toString());
        }
        if (route.getSteps().isEmpty()) {
            addLine(directionsContainer, getString(R.string.route_directions_none));
        }
    }

    private void startInAppNavigation() {
        if (originLat == null || originLng == null) {
            Ux.toast(this, getString(R.string.route_needs_origin));
            return;
        }
        final double destLat;
        final double destLng;
        if (lastRoute != null) {
            destLat = lastRoute.getDestinationLat();
            destLng = lastRoute.getDestinationLng();
        } else if (destination != null) {
            destLat = destination.getLatitude();
            destLng = destination.getLongitude();
        } else {
            Ux.toast(this, getString(R.string.route_needs_destination));
            return;
        }
        InAppNavigationFragment fragment = new InAppNavigationFragment();
        Bundle args = new Bundle();
        args.putDouble(InAppNavigationFragment.ARG_ORIGIN_LAT, originLat);
        args.putDouble(InAppNavigationFragment.ARG_ORIGIN_LNG, originLng);
        args.putDouble(InAppNavigationFragment.ARG_DEST_LAT, destLat);
        args.putDouble(InAppNavigationFragment.ARG_DEST_LNG, destLng);
        args.putString(InAppNavigationFragment.ARG_LABEL,
                lastRoute != null && !lastRoute.getEndAddress().isEmpty() ? lastRoute.getEndAddress()
                        : destinationEdit.getText().toString().trim());
        fragment.setArguments(args);
        com.amperly.app.core.nav.Navigator.open(this, fragment, "in_app_nav");
    }

    private List<ChargerStation> stationsOf(RoutePlannerEngine.RoutePlanResult result) {
        List<ChargerStation> stations = new ArrayList<>();
        for (RoutePlannerEngine.CandidateEvaluation candidate : result.getCandidates()) {
            stations.add(candidate.getStation());
        }
        return stations;
    }

    private void applySocCorrection(int reserveOverridePercent) {
        String text = actualSocEdit.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Ux.toast(this, "Enter the battery percentage shown on your vehicle dashboard.");
            return;
        }
        int actual = safeInt(text);
        if (actual < 0 || actual > 100) {
            Ux.toast(this, "Battery percentage must be between 0 and 100.");
            return;
        }
        if (lastPlan == null || originLat == null || originLng == null) {
            planRoute();
            return;
        }
        final RoutePlan plan = lastPlan;
        final int previousEstimate = AppStateStore.get().getCurrentSoc() >= 0
                ? AppStateStore.get().getCurrentSoc() : plan.getPlannedStartSoc();
        final String userId = AuthManager.getInstance().getCurrentUserId();
        final double currentLat = originLat;
        final double currentLng = originLng;

        repo.background(() -> {
            Vehicle vehicle = repo.getVehicleSync(userId);
            List<ChargerStation> stations = repo.getApprovedStationsSync();
            List<ChargerStation> previouslyReachable = AppStateStore.get().safeReachableStations();
            if (lastCandidates != null && !lastCandidates.isEmpty()) {
                stations = lastCandidates;
            }
            double remainingKm = plan.getTotalDistanceKm() * 0.6;

            SoCEstimatorEngine.RecalculationResult result =
                    SoCEstimatorEngine.applyAuthoritativeSocOverride(
                            plan.getVehicleId(), plan.getId(), previousEstimate, actual,
                            currentLat, currentLng, remainingKm, plan.getDestinationLat(),
                            plan.getDestinationLng(), vehicle, stations, previouslyReachable,
                            reserveOverridePercent);

            repo.saveBatteryUpdate(result.getAuditRecord());
            if (vehicle != null) {
                double efficiency = vehicle.effectiveEfficiencyKmPerKwh();
                double estimatedDrain = efficiency > 0
                        ? plan.getTotalDistanceKm() / efficiency : 0;
                repo.saveTripEstimate(new TripEstimate(UUID.randomUUID().toString(), plan.getId(),
                        vehicle.getId(), plan.getTotalDistanceKm(), estimatedDrain, 0,
                        previousEstimate, result.getActualDashboardSoc(), plan.getConfidence(),
                        "Recorded when the consumer corrected the battery mid-trip.",
                        System.currentTimeMillis()));
            }
            return result;
        }, result -> {
            if (result == null || !isAdded()) {
                return;
            }
            renderRecalculation(result, previousEstimate);
        });
    }

    private void renderRecalculation(SoCEstimatorEngine.RecalculationResult result, int previousEstimate) {
        AppStateStore.get().applySocCorrection(previousEstimate, result.getActualDashboardSoc());
        SocStore.save(requireContext(), AuthManager.getInstance().getCurrentUserId(),
                result.getActualDashboardSoc());
        AppStateStore.get().setLastRecalculationSummary(result.getExplanation());
        AppStateStore.get().setNoSafeChargerMessage(result.getWarningMessage() == null ? ""
                : result.getWarningMessage());

        recalcContainer.removeAllViews();
        TextView header = new TextView(requireContext());
        header.setText("Original estimate → actual update → revised recommendation");
        header.setTextAppearance(R.style.TextAppearance_Amperly_SectionTitle);
        recalcContainer.addView(header);

        addLine(recalcContainer, getString(R.string.soc_previous_estimate, previousEstimate));
        addLine(recalcContainer, getString(R.string.soc_now_authoritative, result.getActualDashboardSoc(),
                Formatters.agoFromNow(System.currentTimeMillis())));
        addLine(recalcContainer, result.getExplanation());
        addLine(recalcContainer, "Usable range at this battery: about " + result.getEstimatedRangeKm() + " km. "
                + "Remaining trip: " + Formatters.minutes(result.getRemainingJourneyMinutes())
                + " (drive " + Formatters.minutes(result.getRemainingDriveMinutes())
                + " + charge " + Formatters.minutes(result.getRemainingChargingMinutes()) + ").");
        addLine(recalcContainer, getString(R.string.soc_removed_stations,
                result.getUnreachableStations().size())
                + (result.getNewlyReachableStations().isEmpty() ? ""
                : " · newly reachable: " + result.getNewlyReachableStations().size()));
        addLine(recalcContainer, result.getReservationAdvice());

        List<RouteStopAdapter.StopRow> rows = new ArrayList<>();
        int index = 1;
        for (SoCEstimatorEngine.RemainingStop stop : result.getRemainingOptions()) {
            rows.add(new RouteStopAdapter.StopRow(index == 1 ? "NEAREST SAFE" : "ALTERNATIVE",
                    String.valueOf(index++), stop.getStation().getName(),
                    String.format(Locale.US, "%.1f km away · +%.1f km detour (+%d min) · %d min charge",
                            stop.getDistanceFromCurrentKm(), stop.getDetourKm(), stop.getDetourMinutes(),
                            stop.getChargingMinutes()),
                    stop.getNote(), stop.isReachable(), stop.getStation().getId()));
        }
        plannedAdapter.submit(rows);

        noSafeChargerCard.setVisibility(result.isNoSafeCharger() ? View.VISIBLE : View.GONE);

        // A corrected battery changes which chargers are reachable along the whole remaining route,
        // so the route and the corridor are recalculated rather than left as they were.
        if (originLat != null && originLng != null) {
            replanWithNewSoc(result.getActualDashboardSoc());
        }

        Ux.toast(this, "Remaining route recalculated using your dashboard value of "
                + result.getActualDashboardSoc() + "%.");
    }

    /** Re-runs the corridor plan from the updated battery without re-requesting the road route. */
    private void replanWithNewSoc(int soc) {
        currentSocEdit.setText(String.valueOf(soc));
        if (lastRoute == null || originLat == null || originLng == null) {
            return;
        }
        NavigationProvider.RouteResult result = new NavigationProvider.RouteResult(lastRoute,
                new NavigationProvider.RouteRequest(originLat, originLng,
                        lastRoute.getDestinationLat(), lastRoute.getDestinationLng(), null,
                        GoogleDirectionsClient.TravelMode.DRIVING),
                GoogleNavigationProvider.PROVIDER_NAME, System.currentTimeMillis(), false);
        runPlan(result);
    }

    private void setupList(RecyclerView recyclerView, RouteStopAdapter adapter) {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setAdapter(adapter);
    }

    private void showStatus(String message) {
        planStatusText.setVisibility(View.VISIBLE);
        planStatusText.setText(message);
    }

    private void addLine(LinearLayout container, String text) {
        TextView line = new TextView(requireContext());
        line.setText(text);
        line.setTextAppearance(R.style.TextAppearance_Amperly_Body);
        line.setPadding(0, 6, 0, 6);
        container.addView(line);
    }

    private void showEmergencyGuidance() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.soc_emergency)
                .setMessage("Amperly is not an emergency service. If you are in danger call your local "
                        + "emergency number (112 in India). Otherwise: move the vehicle to a safe place, "
                        + "keep your hazard lights on, and use the Safety tab to share your live location "
                        + "with a trusted contact while you wait for assistance.")
                .setNegativeButton(R.string.action_close, null)
                .setPositiveButton("Call 112", (dialog, which) ->
                        com.amperly.app.core.nav.Navigator.dial(requireContext(), "112"))
                .show();
    }

    @Override
    public void onReserveStop(String stationId, String stationName) {
        ReservationFragment fragment = new ReservationFragment();
        Bundle args = new Bundle();
        args.putString(ReservationFragment.ARG_STATION_ID, stationId);
        fragment.setArguments(args);
        com.amperly.app.core.nav.Navigator.open(this, fragment, "reserve");
    }

    private List<RouteStop> toStops(String planId, RoutePlannerEngine.RoutePlanResult result) {
        List<RouteStop> stops = new ArrayList<>();
        int sequence = 1;
        for (RoutePlannerEngine.PlannedStop stop : result.getPlannedStops()) {
            stops.add(stop(planId, sequence++, stop, false));
        }
        for (RoutePlannerEngine.PlannedStop stop : result.getBackups()) {
            stops.add(stop(planId, sequence++, stop, true));
        }
        return stops;
    }

    private RouteStop stop(String planId, int sequence, RoutePlannerEngine.PlannedStop stop,
                           boolean backup) {
        return new RouteStop(UUID.randomUUID().toString(), planId, sequence,
                stop.getStation().getId(), stop.getStation().getName(), backup, stop.isReachable(),
                stop.getDistanceAlongRouteKm(), stop.getDetourKm(), stop.getDetourMinutes(),
                stop.getArrivalSoc(), stop.getDepartureSoc(), stop.getEnergyAddedKwh(),
                stop.getChargingMinutes(), stop.getEstimatedCost(), stop.getRecommendationReason(), null);
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("• ").append(value);
        }
        return builder.toString();
    }

    private int safeInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static class PlanBundle {
        private final RoutePlannerEngine.RoutePlanResult result;
        private final RoutePlan plan;
        private final DirectionsRoute route;
        private final boolean fromProfile;

        PlanBundle(RoutePlannerEngine.RoutePlanResult result, RoutePlan plan, DirectionsRoute route,
                   boolean fromProfile) {
            this.result = result;
            this.plan = plan;
            this.route = route;
            this.fromProfile = fromProfile;
        }
    }
}
