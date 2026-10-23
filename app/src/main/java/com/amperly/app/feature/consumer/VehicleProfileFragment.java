package com.amperly.app.feature.consumer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amperly.app.R;
import com.amperly.app.core.security.AuthManager;
import com.amperly.app.core.state.AppStateStore;
import com.amperly.app.data.local.entity.Vehicle;
import com.amperly.app.data.repository.AmperlyRepository;
import com.amperly.app.ui.Ux;

import java.util.Locale;
import java.util.UUID;

public class VehicleProfileFragment extends Fragment {

    private static final String[] CONNECTORS = {"TYPE_2_AC", "CCS_2_DC", "GB_T", "CHADEMO"};

    private AmperlyRepository repo;
    private EditText makeEdit;
    private EditText modelEdit;
    private EditText variantEdit;
    private EditText yearEdit;
    private EditText capacityEdit;
    private EditText maxAcEdit;
    private EditText maxDcEdit;
    private EditText efficiencyEdit;
    private EditText healthEdit;
    private EditText minArrivalEdit;
    private EditText maxChargeEdit;
    private EditText plateEdit;
    private Spinner connectorSpinner;
    private TextView derivedText;
    private String vehicleId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vehicle_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = AmperlyRepository.getInstance(requireContext());

        makeEdit = view.findViewById(R.id.makeEdit);
        modelEdit = view.findViewById(R.id.modelEdit);
        variantEdit = view.findViewById(R.id.variantEdit);
        yearEdit = view.findViewById(R.id.yearEdit);
        capacityEdit = view.findViewById(R.id.capacityEdit);
        maxAcEdit = view.findViewById(R.id.maxAcEdit);
        maxDcEdit = view.findViewById(R.id.maxDcEdit);
        efficiencyEdit = view.findViewById(R.id.efficiencyEdit);
        healthEdit = view.findViewById(R.id.healthEdit);
        minArrivalEdit = view.findViewById(R.id.minArrivalEdit);
        maxChargeEdit = view.findViewById(R.id.maxChargeEdit);
        plateEdit = view.findViewById(R.id.plateEdit);
        connectorSpinner = view.findViewById(R.id.connectorSpinner);
        derivedText = view.findViewById(R.id.vehicleDerivedText);

        connectorSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, CONNECTOR_LABELS));

        String userId = AuthManager.getInstance().getCurrentUserId();
        repo.background(() -> repo.getVehicleSync(userId), vehicle -> {
            if (!isAdded()) {
                return;
            }
            if (vehicle == null) {
                vehicleId = UUID.randomUUID().toString();
                derivedText.setText("No vehicle saved yet. Add your vehicle so charger compatibility and "
                        + "battery estimates use the right values.");
                return;
            }
            vehicleId = vehicle.getId();
            makeEdit.setText(vehicle.getMake());
            modelEdit.setText(vehicle.getModel());
            variantEdit.setText(vehicle.getVariant());
            yearEdit.setText(String.valueOf(vehicle.getPurchaseYear()));
            capacityEdit.setText(String.format(Locale.US, "%.1f", vehicle.getBatteryCapacityKwh()));
            maxAcEdit.setText(String.format(Locale.US, "%.1f", vehicle.getMaxAcKw()));
            maxDcEdit.setText(String.format(Locale.US, "%.1f", vehicle.getMaxDcKw()));
            efficiencyEdit.setText(String.format(Locale.US, "%.1f", vehicle.getTypicalEfficiencyKmPerKwh()));
            healthEdit.setText(String.format(Locale.US, "%.0f", vehicle.getBatteryHealthPercent()));
            minArrivalEdit.setText(String.valueOf(vehicle.getMinArrivalSocPercent()));
            maxChargeEdit.setText(String.valueOf(vehicle.getMaxChargeSocPercent()));
            plateEdit.setText(vehicle.getLicensePlate());
            connectorSpinner.setSelection(indexOf(vehicle.getConnectorType()));
            AppStateStore.get().setVehicle(vehicle);
            updateDerived(vehicle);
        });

        view.findViewById(R.id.saveVehicleButton).setOnClickListener(v -> save(userId));
        buildDerivedPreview();
    }

    private static final String[] CONNECTOR_LABELS = {"Type 2 (AC)", "CCS 2 (DC)", "GB/T", "CHAdeMO"};

    private int indexOf(String connector) {
        for (int i = 0; i < CONNECTORS.length; i++) {
            if (CONNECTORS[i].equalsIgnoreCase(connector)) {
                return i;
            }
        }
        return 0;
    }

    private void buildDerivedPreview() {
        View.OnFocusChangeListener listener = (v, hasFocus) -> {
            if (!hasFocus) {
                derivedText.setText(deriveText());
            }
        };
        capacityEdit.setOnFocusChangeListener(listener);
        efficiencyEdit.setOnFocusChangeListener(listener);
        healthEdit.setOnFocusChangeListener(listener);
    }

    private String deriveText() {
        try {
            double capacity = parseDouble(capacityEdit.getText().toString(), 40);
            double efficiency = parseDouble(efficiencyEdit.getText().toString(), 6.5);
            double health = parseDouble(healthEdit.getText().toString(), 100);
            double effectiveCapacity = capacity * (health / 100.0);
            return String.format(Locale.US,
                    "Planning values: usable capacity %.1f kWh · expected range at 100%% about %.0f km. "
                            + "Battery health is a planning input you control, not a certified measurement.",
                    effectiveCapacity, effectiveCapacity * efficiency);
        } catch (Exception e) {
            return "Planning values are computed from capacity, health and efficiency.";
        }
    }

    private void updateDerived(Vehicle vehicle) {
        derivedText.setText(String.format(Locale.US,
                "Planning values: usable capacity %.1f kWh · efficiency %.1f km/kWh · range at 100%% about %.0f km.",
                vehicle.effectiveCapacityKwh(), vehicle.effectiveEfficiencyKmPerKwh(),
                vehicle.effectiveCapacityKwh() * vehicle.effectiveEfficiencyKmPerKwh()));
    }

    private void save(String userId) {
        String make = makeEdit.getText().toString().trim();
        String model = modelEdit.getText().toString().trim();
        if (TextUtils.isEmpty(make) || TextUtils.isEmpty(model)) {
            Ux.toast(this, "Make and model are required.");
            return;
        }
        int year = safeInt(yearEdit.getText().toString(), 2023);
        double capacity = parseDouble(capacityEdit.getText().toString(), 40.5);
        double maxAc = parseDouble(maxAcEdit.getText().toString(), 7.2);
        double maxDc = parseDouble(maxDcEdit.getText().toString(), 30);
        double efficiency = parseDouble(efficiencyEdit.getText().toString(), 6.5);
        double health = parseDouble(healthEdit.getText().toString(),
                Vehicle.defaultHealthForYear(year));
        int minArrival = safeInt(minArrivalEdit.getText().toString(), 15);
        int maxCharge = safeInt(maxChargeEdit.getText().toString(), 80);
        String connector = CONNECTORS[connectorSpinner.getSelectedItemPosition()];

        Vehicle vehicle = new Vehicle(vehicleId == null ? UUID.randomUUID().toString() : vehicleId,
                userId, make, model, variantEdit.getText().toString().trim(), year, capacity,
                connector, maxAc, maxDc, efficiency, health, 0.0,
                Math.max(5, Math.min(40, minArrival)), Math.max(50, Math.min(100, maxCharge)),
                plateEdit.getText().toString().trim());

        repo.saveVehicle(vehicle);
        AppStateStore.get().setVehicle(vehicle);
        AppStateStore.get().setUserId(userId);
        updateDerived(vehicle);
        Ux.toast(this, getString(R.string.vehicle_saved));
    }

    private int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
