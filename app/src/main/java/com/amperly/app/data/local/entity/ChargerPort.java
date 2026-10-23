package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "charger_ports")
public class ChargerPort {
    @PrimaryKey
    @NonNull
    private String id;
    private String stationId;
    private String portNumber;
    private String connectorType;
    private double powerKw;
    private boolean isDc;
    private String status;

    public ChargerPort(@NonNull String id, String stationId, String portNumber,
                       String connectorType, double powerKw, boolean isDc, String status) {
        this.id = id;
        this.stationId = stationId;
        this.portNumber = portNumber;
        this.connectorType = connectorType;
        this.powerKw = powerKw;
        this.isDc = isDc;
        this.status = status;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getPortNumber() { return portNumber; }
    public void setPortNumber(String portNumber) { this.portNumber = portNumber; }

    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }

    public double getPowerKw() { return powerKw; }
    public void setPowerKw(double powerKw) { this.powerKw = powerKw; }

    public boolean isDc() { return isDc; }
    public void setDc(boolean dc) { isDc = dc; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
