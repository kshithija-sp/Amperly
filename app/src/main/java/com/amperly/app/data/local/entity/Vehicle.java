package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Consumer vehicle profile. Battery health and efficiency values are planning inputs only and
 * are never presented as a certified measurement of battery degradation.
 */
@Entity(tableName = "vehicles")
public class Vehicle {
    @PrimaryKey
    @NonNull
    private String id;
    private String userId;
    private String make;
    private String model;
    private String variant;
    private int purchaseYear;
    private double batteryCapacityKwh;
    private String connectorType;
    private double maxAcKw;
    private double maxDcKw;
    private double typicalEfficiencyKmPerKwh;
    private double batteryHealthPercent;      // 100 = as new
    private double efficiencyCorrectionPercent; // learned correction from reported trips
    private int minArrivalSocPercent;
    private int maxChargeSocPercent;
    private String licensePlate;

    public Vehicle(@NonNull String id, String userId, String make, String model, String variant,
                   int purchaseYear, double batteryCapacityKwh, String connectorType,
                   double maxAcKw, double maxDcKw, double typicalEfficiencyKmPerKwh,
                   double batteryHealthPercent, double efficiencyCorrectionPercent,
                   int minArrivalSocPercent, int maxChargeSocPercent, String licensePlate) {
        this.id = id;
        this.userId = userId;
        this.make = make;
        this.model = model;
        this.variant = variant;
        this.purchaseYear = purchaseYear;
        this.batteryCapacityKwh = batteryCapacityKwh;
        this.connectorType = connectorType;
        this.maxAcKw = maxAcKw;
        this.maxDcKw = maxDcKw;
        this.typicalEfficiencyKmPerKwh = typicalEfficiencyKmPerKwh;
        this.batteryHealthPercent = batteryHealthPercent;
        this.efficiencyCorrectionPercent = efficiencyCorrectionPercent;
        this.minArrivalSocPercent = minArrivalSocPercent;
        this.maxChargeSocPercent = maxChargeSocPercent;
        this.licensePlate = licensePlate;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public int getPurchaseYear() { return purchaseYear; }
    public void setPurchaseYear(int purchaseYear) { this.purchaseYear = purchaseYear; }

    public double getBatteryCapacityKwh() { return batteryCapacityKwh; }
    public void setBatteryCapacityKwh(double batteryCapacityKwh) { this.batteryCapacityKwh = batteryCapacityKwh; }

    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

    public double getMaxAcKw() { return maxAcKw; }
    public void setMaxAcKw(double maxAcKw) { this.maxAcKw = maxAcKw; }

    public double getMaxDcKw() { return maxDcKw; }
    public void setMaxDcKw(double maxDcKw) { this.maxDcKw = maxDcKw; }

    public double getTypicalEfficiencyKmPerKwh() { return typicalEfficiencyKmPerKwh; }
    public void setTypicalEfficiencyKmPerKwh(double typicalEfficiencyKmPerKwh) { this.typicalEfficiencyKmPerKwh = typicalEfficiencyKmPerKwh; }

    public double getBatteryHealthPercent() { return batteryHealthPercent; }
    public void setBatteryHealthPercent(double batteryHealthPercent) { this.batteryHealthPercent = batteryHealthPercent; }

    public double getEfficiencyCorrectionPercent() { return efficiencyCorrectionPercent; }
    public void setEfficiencyCorrectionPercent(double efficiencyCorrectionPercent) { this.efficiencyCorrectionPercent = efficiencyCorrectionPercent; }

    public int getMinArrivalSocPercent() { return minArrivalSocPercent; }
    public void setMinArrivalSocPercent(int minArrivalSocPercent) { this.minArrivalSocPercent = minArrivalSocPercent; }

    public int getMaxChargeSocPercent() { return maxChargeSocPercent; }
    public void setMaxChargeSocPercent(int maxChargeSocPercent) { this.maxChargeSocPercent = maxChargeSocPercent; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    /** Planning-only capacity after the user-provided health adjustment. */
    public double effectiveCapacityKwh() {
        double health = batteryHealthPercent > 0 ? Math.min(100.0, batteryHealthPercent) : 100.0;
        return Math.max(1.0, batteryCapacityKwh * (health / 100.0));
    }

    /** Planning-only efficiency in km/kWh after the learned correction. */
    public double effectiveEfficiencyKmPerKwh() {
        double base = typicalEfficiencyKmPerKwh > 0 ? typicalEfficiencyKmPerKwh : 6.5;
        double corrected = base * (1.0 + (efficiencyCorrectionPercent / 100.0));
        return Math.max(1.0, corrected);
    }

    /** Model without a user-supplied health figure: assume a conservative 2% loss per year after 2020. */
    public static double defaultHealthForYear(int purchaseYear) {
        int age = Math.max(0, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - purchaseYear);
        return Math.max(70.0, 100.0 - (age * 2.0));
    }
}
