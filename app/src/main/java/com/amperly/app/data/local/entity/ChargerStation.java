package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Arrays;
import java.util.List;

@Entity(tableName = "charger_stations")
public class ChargerStation {
    @PrimaryKey
    @NonNull
    private String id;
    private String hostId;
    private String hostName;
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private String locationType;
    private String operatingHours;
    private double pricePerKwh;
    private double serviceFee;
    private double gatewayFee;
    private String verificationLevel; // LEVEL_1, LEVEL_2, LEVEL_3
    private boolean isVerified;
    private boolean isPaused;
    private String approvalStatus; // PENDING, APPROVED, REJECTED
    private String reviewNote;
    private String connectorTypes; // comma separated, e.g. "TYPE_2_AC,CCS_2_DC"
    private double maxPowerKw;
    private boolean isDcCapable;
    private int portCount;
    private String accessRules;
    private double rating;
    private double reliabilityScore; // 0..100 from completed sessions
    private int incidentCount;
    private long lastUpdatedMs;

    public ChargerStation(@NonNull String id, String hostId, String hostName, String name,
                          String address, double latitude, double longitude, String locationType,
                          String operatingHours, double pricePerKwh, double serviceFee,
                          double gatewayFee, String verificationLevel, boolean isVerified,
                          boolean isPaused, String approvalStatus, String reviewNote,
                          String connectorTypes, double maxPowerKw, boolean isDcCapable,
                          int portCount, String accessRules, double rating, double reliabilityScore,
                          int incidentCount, long lastUpdatedMs) {
        this.id = id;
        this.hostId = hostId;
        this.hostName = hostName;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationType = locationType;
        this.operatingHours = operatingHours;
        this.pricePerKwh = pricePerKwh;
        this.serviceFee = serviceFee;
        this.gatewayFee = gatewayFee;
        this.verificationLevel = verificationLevel;
        this.isVerified = isVerified;
        this.isPaused = isPaused;
        this.approvalStatus = approvalStatus;
        this.reviewNote = reviewNote;
        this.connectorTypes = connectorTypes;
        this.maxPowerKw = maxPowerKw;
        this.isDcCapable = isDcCapable;
        this.portCount = portCount;
        this.accessRules = accessRules;
        this.rating = rating;
        this.reliabilityScore = reliabilityScore;
        this.incidentCount = incidentCount;
        this.lastUpdatedMs = lastUpdatedMs;
    }

    /** Convenience factory used by the seeder and tests; fills sensible API defaults. */
    public static ChargerStation create(String id, String hostId, String hostName, String name,
                                       String address, double latitude, double longitude,
                                       String locationType, String operatingHours,
                                       double pricePerKwh, double serviceFee, double gatewayFee,
                                       String verificationLevel, String connectorTypes,
                                       double maxPowerKw, int portCount, String accessRules,
                                       double rating) {
        boolean dc = connectorTypes != null
                && (connectorTypes.contains("CCS") || connectorTypes.contains("CHADEMO")
                || connectorTypes.contains("GB_T"));
        return new ChargerStation(id, hostId, hostName, name, address, latitude, longitude,
                locationType, operatingHours, pricePerKwh, serviceFee, gatewayFee, verificationLevel,
                !"LEVEL_3".equals(verificationLevel), false, "APPROVED", "",
                connectorTypes, maxPowerKw, dc, portCount, accessRules, rating, 92.0, 0,
                System.currentTimeMillis());
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }

    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }

    public double getPricePerKwh() { return pricePerKwh; }
    public void setPricePerKwh(double pricePerKwh) { this.pricePerKwh = pricePerKwh; }

    public double getServiceFee() { return serviceFee; }
    public void setServiceFee(double serviceFee) { this.serviceFee = serviceFee; }

    public double getGatewayFee() { return gatewayFee; }
    public void setGatewayFee(double gatewayFee) { this.gatewayFee = gatewayFee; }

    public String getVerificationLevel() { return verificationLevel; }
    public void setVerificationLevel(String verificationLevel) { this.verificationLevel = verificationLevel; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public boolean isPaused() { return isPaused; }
    public void setPaused(boolean paused) { isPaused = paused; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public String getConnectorTypes() { return connectorTypes; }
    public void setConnectorTypes(String connectorTypes) { this.connectorTypes = connectorTypes; }

    public double getMaxPowerKw() { return maxPowerKw; }
    public void setMaxPowerKw(double maxPowerKw) { this.maxPowerKw = maxPowerKw; }

    public boolean isDcCapable() { return isDcCapable; }
    public void setDcCapable(boolean dcCapable) { isDcCapable = dcCapable; }

    public int getPortCount() { return portCount; }
    public void setPortCount(int portCount) { this.portCount = portCount; }

    public String getAccessRules() { return accessRules; }
    public void setAccessRules(String accessRules) { this.accessRules = accessRules; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public double getReliabilityScore() { return reliabilityScore; }
    public void setReliabilityScore(double reliabilityScore) { this.reliabilityScore = reliabilityScore; }

    public int getIncidentCount() { return incidentCount; }
    public void setIncidentCount(int incidentCount) { this.incidentCount = incidentCount; }

    public long getLastUpdatedMs() { return lastUpdatedMs; }
    public void setLastUpdatedMs(long lastUpdatedMs) { this.lastUpdatedMs = lastUpdatedMs; }

    public List<String> connectorTypeList() {
        if (connectorTypes == null || connectorTypes.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return Arrays.asList(connectorTypes.split("\\s*,\\s*"));
    }

    /** A station can only run automatic energy-based paid billing with a verified meter. */
    public boolean supportsPaidEnergyBilling() {
        return "LEVEL_1".equals(verificationLevel) || "LEVEL_2".equals(verificationLevel);
    }

    public boolean isLiveOnMarketplace() {
        return "APPROVED".equals(approvalStatus) && !isPaused;
    }
}
