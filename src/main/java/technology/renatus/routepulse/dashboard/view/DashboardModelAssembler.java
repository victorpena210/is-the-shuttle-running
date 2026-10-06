package technology.renatus.routepulse.dashboard.view;

import technology.renatus.routepulse.assignments.service.AssignmentPlanningService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliation;
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
        model.addAttribute("calendarOverride", plan.calendarOverride());
        model.addAttribute("calendarOverrideActive", plan.calendarOverrideActive());
        model.addAttribute("calendarDiagnosticCode", day == null ? null : day.diagnosticCode());
        model.addAttribute("calendarConfirmationRequired", plan.selectedDateAvailable() && day != null
                && (day.status() == ParkOperatingStatus.UNKNOWN || day.status() == ParkOperatingStatus.CLOSED)
                && !plan.calendarOverrideActive());
        model.addAttribute("showSchedule", plan.selectedDateAvailable() && day != null
                && (day.shouldScheduleShuttle() || plan.calendarOverrideActive()));
        model.addAttribute("driverAssignments", plan.driverAssignments());
        model.addAttribute("scheduleReconciliations", plan.scheduleReconciliations());
        model.addAttribute(
                "scheduleReconciliationActionCount",
                plan.scheduleReconciliations().stream()
                        .filter(ScheduleReconciliation::requiresAction)
                        .count()
        );
        model.addAttribute("assignmentMessage", plan.assignmentMessage());
        model.addAttribute("assignmentReviewRequired", plan.assignmentReviewRequired());
        model.addAttribute("latestScheduleImport", plan.latestScheduleImport());

        model.addAttribute("wardrobeHours", plan.wardrobeHours());
        model.addAttribute("configuredOpening", plan.wardrobeHours().usesOpeningWindow());
        model.addAttribute("openingDrivingDemand", plan.openingDrivingDemand());
        model.addAttribute("eveningDrivingDemand", plan.eveningDrivingDemand());
        model.addAttribute("middayDrivingDemand", plan.middayDrivingDemand());
        model.addAttribute("drivingStartConflict", plan.drivingStartConflict());
        model.addAttribute("morningStaffingBlocked", plan.morningStaffingBlocked());
        model.addAttribute("lateOpeningPeriod", plan.lateOpeningPeriod() == null ? "" : plan.lateOpeningPeriod().name());
        model.addAttribute("openingStaffingBlocked", plan.openingStaffingBlocked());
        model.addAttribute("middaySuggestion", plan.middaySuggestion());
        model.addAttribute("middaySuggestionKey", AssignmentPlanningService
                .suggestionKey(plan.middaySuggestion()));
        int required = 0, covered = 0;
        for (ShuttlePeriod period : List.of(ShuttlePeriod.MORNING, ShuttlePeriod.MIDDAY, ShuttlePeriod.AFTERNOON)) {
            DriverAssignment assignment = plan.driverAssignments().stream()
                    .filter(a -> a.getPeriod() == period).findFirst().orElse(null);
            String prefix = period.name().toLowerCase(java.util.Locale.ROOT);
            model.addAttribute(prefix + "AssignmentExists", assignment != null);
            model.addAttribute(prefix + "Assignment", assignment);
            model.addAttribute(prefix + "AssignedDriverSchedule", findAssignedDriverSchedule(plan.driverSchedules(), assignment));
            model.addAttribute(prefix + "Required", plan.requiresCoverage(period));
            if (plan.requiresCoverage(period)) {
                required++;
                if (assignment != null) covered++;
            }
        }
        model.addAttribute("totalShiftCount", required);
        model.addAttribute("coveredShiftCount", covered);
        model.addAttribute("needsAttentionShiftCount", required - covered);
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
