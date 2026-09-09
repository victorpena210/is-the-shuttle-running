package technology.renatus.routepulse.dashboard.view;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;

/** Maintains the dashboard template model, including all three coverage shifts. */
@Component
public class DashboardModelAssembler {

    public void populate(Model model, DashboardPlan plan) {
        ParkOperatingDay day = plan.operatingDay();
        model.addAttribute("selectedDate", plan.selectedDate());
        model.addAttribute("currentOperationsDate", plan.currentOperationsDate());
        model.addAttribute("availableDates", plan.availableDates());
        model.addAttribute("selectedDateAvailable", plan.selectedDateAvailable());
        model.addAttribute("scheduleUploaded", plan.scheduleUploaded());
        model.addAttribute("recentScheduleImports", plan.recentScheduleImports());
        model.addAttribute("generated", plan.selectedDateAvailable());
        model.addAttribute("transportationDemand", plan.transportationDemand());
        model.addAttribute("driverSchedules", plan.driverSchedules());
        model.addAttribute("driversWithoutSchedules", plan.driversWithoutSchedules());
        model.addAttribute("activeDrivers", plan.activeDrivers());
        model.addAttribute("operatingDay", day);
        model.addAttribute("calendarMessage", plan.calendarMessage());
        model.addAttribute("calendarOverrideActive", plan.calendarOverrideActive());
        model.addAttribute("calendarDiagnosticCode", day == null ? null : day.diagnosticCode());
        model.addAttribute("calendarConfirmationRequired", plan.selectedDateAvailable() && day != null
                && day.status() == ParkOperatingStatus.UNKNOWN && !plan.calendarOverrideActive());
        model.addAttribute("showSchedule", plan.selectedDateAvailable() && day != null
                && (day.shouldScheduleShuttle() || plan.calendarOverrideActive()));
        model.addAttribute("driverAssignments", plan.driverAssignments());
        model.addAttribute("assignmentMessage", plan.assignmentMessage());
        model.addAttribute("assignmentReviewRequired", plan.assignmentReviewRequired());
        model.addAttribute("latestScheduleImport", plan.latestScheduleImport());

        int covered = 0;
        for (ShuttlePeriod period : List.of(ShuttlePeriod.MORNING, ShuttlePeriod.MIDDAY, ShuttlePeriod.AFTERNOON)) {
            DriverAssignment assignment = plan.driverAssignments().stream()
                    .filter(a -> a.getPeriod() == period).findFirst().orElse(null);
            String prefix = period.name().toLowerCase(java.util.Locale.ROOT);
            model.addAttribute(prefix + "AssignmentExists", assignment != null);
            model.addAttribute(prefix + "Assignment", assignment);
            model.addAttribute(prefix + "AssignedDriverSchedule", findAssignedDriverSchedule(plan.driverSchedules(), assignment));
            if (assignment != null) {
                covered++;
            }
        }
        model.addAttribute("totalShiftCount", 3);
        model.addAttribute("coveredShiftCount", covered);
        model.addAttribute("needsAttentionShiftCount", 3 - covered);
    }

    private DriverSchedule findAssignedDriverSchedule(
            List<DriverSchedule> schedules,
            DriverAssignment assignment) {

        if (assignment == null
                || assignment.getScheduledDriver() == null
                || schedules == null
                || schedules.isEmpty()) {

            return null;
        }

        Long driverId =
                assignment
                        .getScheduledDriver()
                        .getId();

        List<DriverSchedule> matchingSchedules =
                schedules.stream()
                        .filter(schedule ->
                                schedule.driverId().equals(driverId)
                        )
                        .toList();

        if (matchingSchedules.isEmpty()) {
            return null;
        }

        if (assignment.getScheduledStartTime() == null
                || assignment.getScheduledEndTime() == null) {

            return matchingSchedules.get(0);
        }

        return matchingSchedules.stream()
                .filter(schedule ->
                        schedule.overlaps(
                                assignment.getScheduledStartTime(),
                                assignment.getScheduledEndTime()
                        )
                )
                .max((left, right) ->
                        Long.compare(
                                left.overlapMinutes(
                                        assignment.getScheduledStartTime(),
                                        assignment.getScheduledEndTime()
                                ),
                                right.overlapMinutes(
                                        assignment.getScheduledStartTime(),
                                        assignment.getScheduledEndTime()
                                )
                        )
                )
                .orElse(matchingSchedules.get(0));
    }
}
