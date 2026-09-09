package technology.renatus.routepulse.scheduling.domain;

import java.time.LocalTime;
import java.util.List;

/**
 * Calculated morning shuttle plan derived from employee scheduled clock-ins.
 *
 * The plan is advisory operational data. Driver fairness still determines
 * who is assigned; this object only determines when morning driving is needed.
 */
public class MorningDrivingPlan {

    private final List<MorningArrivalWindow> arrivalWindows;
    private final LocalTime firstClockIn;
    private final LocalTime lastClockIn;
    private final LocalTime scheduledStartTime;
    private final LocalTime scheduledEndTime;
    private final int teamMemberCount;

    public MorningDrivingPlan(
            List<MorningArrivalWindow> arrivalWindows,
            LocalTime firstClockIn,
            LocalTime lastClockIn,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            int teamMemberCount) {

        this.arrivalWindows = List.copyOf(arrivalWindows);
        this.firstClockIn = firstClockIn;
        this.lastClockIn = lastClockIn;
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;
        this.teamMemberCount = teamMemberCount;
    }

    public static MorningDrivingPlan empty() {
        return new MorningDrivingPlan(
                List.of(),
                null,
                null,
                null,
                null,
                0
        );
    }

    public List<MorningArrivalWindow> getArrivalWindows() {
        return arrivalWindows;
    }

    public LocalTime getFirstClockIn() {
        return firstClockIn;
    }

    public LocalTime getLastClockIn() {
        return lastClockIn;
    }

    public LocalTime getScheduledStartTime() {
        return scheduledStartTime;
    }

    public LocalTime getScheduledEndTime() {
        return scheduledEndTime;
    }

    public int getTeamMemberCount() {
        return teamMemberCount;
    }

    public boolean hasDemand() {
        return scheduledStartTime != null
                && scheduledEndTime != null
                && teamMemberCount > 0;
    }
}
