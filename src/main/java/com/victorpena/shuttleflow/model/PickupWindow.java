package com.victorpena.shuttleflow.model;

import java.time.LocalTime;

public class PickupWindow {

    private LocalTime windowStart;
    private LocalTime windowEnd;
    private int teamMemberCount;
    private String priority;

    public PickupWindow(LocalTime windowStart, LocalTime windowEnd, int teamMemberCount, String priority) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.teamMemberCount = teamMemberCount;
        this.priority = priority;
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

    public String getPriority() {
        return priority;
    }
}