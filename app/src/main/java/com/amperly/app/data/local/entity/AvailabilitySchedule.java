package com.amperly.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Host-managed opening window for one port on one day of the week. The MVP has no charger API, so
 * availability is derived from these schedules, reservation records, port status and blocks.
 */
@Entity(tableName = "availability_schedules")
public class AvailabilitySchedule {
    @PrimaryKey
    @NonNull
    private String id;
    private String stationId;
    private String portId;
    private int dayOfWeek;          // Calendar.MONDAY..Calendar.SUNDAY (1..7)
    private int openMinutes;        // minutes from midnight, local time
    private int closeMinutes;
    private boolean isBlocked;      // maintenance / closed block
    private String blockReason;
    private long updatedAtMs;

    public AvailabilitySchedule(@NonNull String id, String stationId, String portId, int dayOfWeek,
                                int openMinutes, int closeMinutes, boolean isBlocked,
                                String blockReason, long updatedAtMs) {
        this.id = id;
        this.stationId = stationId;
        this.portId = portId;
        this.dayOfWeek = dayOfWeek;
        this.openMinutes = openMinutes;
        this.closeMinutes = closeMinutes;
        this.isBlocked = isBlocked;
        this.blockReason = blockReason;
        this.updatedAtMs = updatedAtMs;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getPortId() { return portId; }
    public void setPortId(String portId) { this.portId = portId; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public int getOpenMinutes() { return openMinutes; }
    public void setOpenMinutes(int openMinutes) { this.openMinutes = openMinutes; }

    public int getCloseMinutes() { return closeMinutes; }
    public void setCloseMinutes(int closeMinutes) { this.closeMinutes = closeMinutes; }

    public boolean isBlocked() { return isBlocked; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public long getUpdatedAtMs() { return updatedAtMs; }
    public void setUpdatedAtMs(long updatedAtMs) { this.updatedAtMs = updatedAtMs; }
}
