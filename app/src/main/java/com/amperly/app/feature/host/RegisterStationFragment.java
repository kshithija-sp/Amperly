package com.amperly.app.feature.host;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.amperly.app.R;
import com.amperly.app.core.common.AppConstants;
import com.amperly.app.core.common.GoogleConfig;
import com.amperly.app.core.location.LocationProvider;
import com.amperly.app.core.map.MapPanel;
import com.amperly.app.core.nav.Navigator;
import com.amperly.app.core.places.GoogleGeocoder;
import com.amperly.app.core.places.PlacesAutocomplete;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.data.local.entity.AvailabilitySchedule;
import com.amperly.app.data.local.entity.ChargerPort;
import com.amperly.app.data.local.entity.ChargerStation;
import com.amperly.app.data.local.entity.User;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.engine.meter.MeterSimulator;
import com.amperly.app.ui.Ux;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Registers an existing charger. The station is submitted as PENDING and only goes live after an
 * administrator verifies it — nothing is promised to consumers before that.
 */
public class RegisterStationFragment extends Fragment {

    private static final String[] LOCATION_TYPES = {"CAMPUS", "APARTMENT", "OFFICE", "CAFE",
            "HOTEL", "PARKING", "PETROL_PUMP", "COMMERCIAL"};
    private static final String[] LOCATION_LABELS = {"Campus", "Apartment", "Office", "Café", "Hotel",
            "Parking", "Petrol pump", "Commercial"};
    private static final String[] METER_LEVELS = {"LEVEL_1", "LEVEL_2", "LEVEL_3"};
    private static final String[] METER_LABELS = {
            "Level 1 — visible/connected manual meter",
            "Level 2 — integrated smart meter (simulated)",
            "Level 3 — no verified meter (discovery only)"};
    private static final String[] CONNECTORS = {"TYPE_2_AC", "CCS_2_DC", "GB_T", "CHADEMO"};

    private static final int REQUEST_LOCATION = 1001;

    private AmperlyRepository repo;
    private EditText latitudeEdit;
    private EditText longitudeEdit;
    private EditText addressEdit;
    private FrameLayout stationMapContainer;
    @Nullable
    private MapPanel mapPanel;
    /** Set while the map, rather than the host, is writing the coordinate fields. */
    private boolean applyingPickedLocation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register_station, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());

        ((Spinner) view.findViewById(R.id.locationTypeSpinner)).setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, LOCATION_LABELS));
        ((Spinner) view.findViewById(R.id.meterLevelSpinner)).setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, METER_LABELS));
        ((Spinner) view.findViewById(R.id.portConnectorSpinner)).setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_dropdown_item, CONNECTORS));

        setUpLocationPicker(view);

        view.findViewById(R.id.saveStationButton).setOnClickListener(v -> save(view));
    }

    /**
     * Puts the charger on a map the host can just tap. The coordinates stay editable by hand and
     * the pin follows whatever is typed, so the two never disagree about where the station is.
     */
    private void setUpLocationPicker(View view) {
        latitudeEdit = view.findViewById(R.id.latitudeEdit);
        longitudeEdit = view.findViewById(R.id.longitudeEdit);
        addressEdit = view.findViewById(R.id.addressEdit);
        stationMapContainer = view.findViewById(R.id.stationMapContainer);
        if (stationMapContainer == null) {
            return;
        }

        TextWatcher followTypedCoordinates = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (applyingPickedLocation || mapPanel == null) {
                    return;
                }
                mapPanel.movePickerTo(parse(latitudeEdit.getText().toString(), 0),
                        parse(longitudeEdit.getText().toString(), 0));
            }
        };
        latitudeEdit.addTextChangedListener(followTypedCoordinates);
        longitudeEdit.addTextChangedListener(followTypedCoordinates);

        // The production map is Google Maps. Tapping it picks the charger's exact spot; the host
        // can also search the address with Google Place autocomplete below.
        MapPanel.attach(this, stationMapContainer, panel -> {
            mapPanel = panel;
            panel.setMapTapListener(this::onMapPointPicked);
            if (!TextUtils.isEmpty(latitudeEdit.getText())) {
                panel.plotPicker(parse(latitudeEdit.getText().toString(), 0),
                        parse(longitudeEdit.getText().toString(), 0),
                        getString(R.string.map_pick_header), true);
            }
        });

        if (PlacesAutocomplete.isAvailable(requireContext()) && addressEdit != null) {
            PlacesAutocomplete.bind(requireContext(),
                    (com.google.android.material.textfield.MaterialAutoCompleteTextView) addressEdit,
                    new PlacesAutocomplete.Listener() {
                        @Override
                        public void onPlaceSelected(@NonNull PlacesAutocomplete.Selection selection) {
                            addressEdit.setText(selection.getAddress());
                            applyPicked(selection.getLatitude(), selection.getLongitude());
                        }

                        @Override
                        public void onSelectionCleared() {
                        }
                    });
        }

        View gpsButton = view.findViewById(R.id.useGpsButton);
        if (gpsButton != null) {
            gpsButton.setOnClickListener(v -> useCurrentLocation());
        }
    }

    /** A map tap places the charger and fills the address from Google reverse geocoding. */
    private void onMapPointPicked(double latitude, double longitude) {
        applyPicked(latitude, longitude);
        GoogleGeocoder.reverseGeocode(latitude, longitude, new GoogleGeocoder.Callback() {
            @Override
            public void onAddress(@NonNull String formattedAddress) {
                if (isAdded() && addressEdit != null) {
                    addressEdit.setText(formattedAddress);
                }
            }

            @Override
            public void onError(@NonNull String message) {
                if (isAdded()) {
                    Ux.toast(RegisterStationFragment.this,
                            "Coordinates set. Enter the address by hand: " + message);
                }
            }
        });
    }

    private void useCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        Ux.toast(this, "Finding your location…");
        LocationProvider.requestCurrentLocation(requireContext(),
                new LocationProvider.LocationCallback() {
                    @Override
                    public void onLocationRetrieved(double latitude, double longitude) {
                        onMapPointPicked(latitude, longitude);
                    }

                    @Override
                    public void onLocationUnavailable() {
                        Ux.toast(RegisterStationFragment.this,
                                "No location fix. Tap the map or search the address instead.");
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            useCurrentLocation();
        } else {
            Ux.toast(this, getString(R.string.map_no_location_permission));
        }
    }

    /** Writes a latitude/longitude pair into the form and moves the Google pin to match. */
    private void applyPicked(double latitude, double longitude) {
        if (!isAdded() || latitudeEdit == null) {
            return;
        }
        applyingPickedLocation = true;
        latitudeEdit.setText(String.format(Locale.US, "%.5f", latitude));
        longitudeEdit.setText(String.format(Locale.US, "%.5f", longitude));
        applyingPickedLocation = false;
        if (mapPanel != null) {
            mapPanel.plotPicker(latitude, longitude, getString(R.string.map_pick_header), true);
        }
    }

    private void save(View view) {
        if (!AppConstants.ROLE_HOST.equals(AuthManager.getInstance().getCurrentRole())
                && !AppConstants.ROLE_ADMIN.equals(AuthManager.getInstance().getCurrentRole())) {
            Ux.toast(this, "Switch to Host mode to register a charger.");
            return;
        }

        String name = text(view, R.id.stationNameEdit);
        String address = text(view, R.id.addressEdit);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(address)) {
            Ux.toast(this, "Station name and address are required.");
            return;
        }
        // A charger is never published at a made-up coordinate: the host must have placed it.
        String latText = text(view, R.id.latitudeEdit);
        String lngText = text(view, R.id.longitudeEdit);
        if (TextUtils.isEmpty(latText) || TextUtils.isEmpty(lngText)
                || parse(latText, 0) == 0 || parse(lngText, 0) == 0) {
            Ux.toast(this, "Set the charger location on the map or search its address first.");
            return;
        }
        double latitude = parse(latText, 0);
        double longitude = parse(lngText, 0);
        double price = parse(text(view, R.id.priceEdit), AppConstants.DEFAULT_TARIFF_INR_PER_KWH);
        double serviceFee = parse(text(view, R.id.serviceFeeEdit), AppConstants.DEFAULT_SERVICE_FEE_INR);
        double gatewayFee = parse(text(view, R.id.gatewayFeeEdit), AppConstants.DEFAULT_GATEWAY_FEE_INR);
        int portCount = (int) parse(text(view, R.id.portCountEdit), 1);
        double portPower = parse(text(view, R.id.portPowerEdit), 7.2);
        String connector = CONNECTORS[((Spinner) view.findViewById(R.id.portConnectorSpinner))
                .getSelectedItemPosition()];
        boolean dc = ((CheckBox) view.findViewById(R.id.portDcCheck)).isChecked();
        String locationType = LOCATION_TYPES[((Spinner) view.findViewById(R.id.locationTypeSpinner))
                .getSelectedItemPosition()];
        String meterLevel = METER_LEVELS[((Spinner) view.findViewById(R.id.meterLevelSpinner))
                .getSelectedItemPosition()];
        String hours = text(view, R.id.hoursEdit);
        String access = text(view, R.id.accessRulesEdit);

        User host = AuthManager.getInstance().getCurrentUser();
        String stationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        ChargerStation station = new ChargerStation(stationId, host.getId(), host.getName(), name, address,
                latitude, longitude, locationType, TextUtils.isEmpty(hours) ? "08:00 - 20:00" : hours,
                price, serviceFee, gatewayFee, meterLevel, false, false,
                AppConstants.APPROVAL_PENDING, "",
                connector, portPower, dc, Math.max(1, portCount),
                TextUtils.isEmpty(access) ? "Contact the host on arrival." : access,
                0.0, 0.0, 0, now);

        List<ChargerPort> ports = new ArrayList<>();
        for (int i = 1; i <= Math.max(1, portCount); i++) {
            ports.add(new ChargerPort(UUID.randomUUID().toString(), stationId, String.valueOf(i),
                    connector, portPower, dc, AppConstants.PORT_AVAILABLE));
        }

        List<AvailabilitySchedule> schedules = new ArrayList<>();
        int open = 8 * 60;
        int close = 20 * 60;
        for (ChargerPort port : ports) {
            for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
                schedules.add(new AvailabilitySchedule(port.getId() + "_d" + day, stationId,
                        port.getId(), day, open, close, false, null, now));
            }
        }

        repo.background(() -> {
            repo.saveStation(station);
            repo.savePorts(ports);
            repo.saveSchedules(schedules);
            MeterSimulator.setReading(stationId, 100.0 + (Math.random() * 50.0));
            // The station is the record the admin queue reads; no cross-user notification is
            // written, because the notifications policy only accepts rows owned by the caller.
            return stationId;
        }, id -> {
            if (!isAdded() || id == null) {
                return;
            }
            ((TextView) requireView().findViewById(R.id.registerStatusText)).setText(
                    getString(R.string.host_station_saved) + "\nAvailability last updated just now. "
                            + "Stations without a verified meter are published for discovery only.");
            Ux.toast(this, getString(R.string.host_station_saved));
            Navigator.openRoot(requireActivity(), new HostDashboardFragment(), "host_dashboard");
        });
    }

    private String text(View view, int id) {
        EditText edit = view.findViewById(id);
        return edit == null ? "" : edit.getText().toString().trim();
    }

    private double parse(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
