package com.victorpena.shuttleflow.model;

import java.time.LocalTime;

public class PickupWindow {

    private LocalTime windowStart;
    private LocalTime windowEnd;
    private int teamMemberCount;
    private PickupPriority priority;
    private String pickupLocation;

    public PickupWindow(
            LocalTime windowStart,
            LocalTime windowEnd,
            int teamMemberCount,
            PickupPriority priority,
            String pickupLocation) {

        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.teamMemberCount = teamMemberCount;
        this.priority = priority;
        this.pickupLocation = pickupLocation;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    public int getTeamMemberCount() {
        return teamMemberCount;
    }

    public PickupPriority getPriority() {
        return priority;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }
}