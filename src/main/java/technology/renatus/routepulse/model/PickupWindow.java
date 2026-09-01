package technology.renatus.routepulse.model;

import java.time.LocalTime;

public class PickupWindow {

    private LocalTime windowStart;
    private LocalTime windowEnd;
    private int teamMemberCount;
    private PickupPriority priority;

    public PickupWindow(
            LocalTime windowStart,
            LocalTime windowEnd,
            int teamMemberCount,
            PickupPriority priority) {

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

    public PickupPriority getPriority() {
        return priority;
    }
}