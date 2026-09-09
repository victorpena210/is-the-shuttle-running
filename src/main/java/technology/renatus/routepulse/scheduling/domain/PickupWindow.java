package technology.renatus.routepulse.scheduling.domain;

import java.time.LocalTime;

public class PickupWindow {

    private final LocalTime windowStart;
    private final LocalTime windowEnd;
    private final int teamMemberCount;
    private final PickupPriority priority;

    /*
     * True when this window belongs to a sustained
     * demand cluster of 3+ consecutive busy windows.
     */
    private boolean sustainedDemand;

    public PickupWindow(
            LocalTime windowStart,
            LocalTime windowEnd,
            int teamMemberCount,
            PickupPriority priority) {

        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.teamMemberCount = teamMemberCount;
        this.priority = priority;
        this.sustainedDemand = false;
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

    public boolean isSustainedDemand() {
        return sustainedDemand;
    }

    public void setSustainedDemand(
            boolean sustainedDemand) {

        this.sustainedDemand = sustainedDemand;
    }
}
