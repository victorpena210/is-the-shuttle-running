package technology.renatus.routepulse.reporting.pilot.model;

import java.time.LocalDate;
import java.util.List;

/** Complete weekly pilot/ROI report shown to managers. */
public record PilotAnalyticsReport(
        LocalDate weekStart,
        LocalDate weekEnd,
        boolean currentWeek,
        long plannedMinutes,
        long actualMinutes,
        int totalAssignments,
        int automaticallyPlannedAssignments,
        double automaticCoveragePercent,
        int managerOverrides,
        int missedAssignments,
        int fillInAssignments,
        long passengerConfirmations,
        double averageStartDelayMinutes,
        int scheduleChangesHandled,
        double fairnessSpreadHours,
        int participatingDrivers,
        List<DriverFairnessRow> fairnessRows,
        List<PickupWindowStat> busiestPickupWindows,
        String executiveSummary) {

    public PilotAnalyticsReport {
        fairnessRows = List.copyOf(fairnessRows);
        busiestPickupWindows = List.copyOf(busiestPickupWindows);
    }

    public double plannedHours() {
        return plannedMinutes / 60.0;
    }

    public double actualHours() {
        return actualMinutes / 60.0;
    }

    public double actualVsPlannedPercent() {
        if (plannedMinutes <= 0) {
            return 0.0;
        }
        return (actualMinutes * 100.0) / plannedMinutes;
    }

    public LocalDate previousWeekStart() {
        return weekStart.minusWeeks(1);
    }

    public LocalDate nextWeekStart() {
        return weekStart.plusWeeks(1);
    }
}
