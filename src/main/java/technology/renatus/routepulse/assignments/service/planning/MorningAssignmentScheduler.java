package technology.renatus.routepulse.assignments.service.planning;

import java.time.LocalDate;
import java.util.List;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.DriverScheduleResult;
import technology.renatus.routepulse.scheduling.domain.MorningDrivingPlan;
import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.scheduling.planning.MorningDrivingPlanningService;

/** Applies generated morning-demand windows without overwriting manager or actual-driving data. */
public class MorningAssignmentScheduler {

    private final DriverScheduleService driverScheduleService;
    private final MorningDrivingPlanningService morningDrivingPlanningService;
    private final DriverScheduleSelector scheduleSelector;

    public MorningAssignmentScheduler(
            DriverScheduleService driverScheduleService,
            MorningDrivingPlanningService morningDrivingPlanningService,
            DriverScheduleSelector scheduleSelector) {
        this.driverScheduleService = driverScheduleService;
        this.morningDrivingPlanningService = morningDrivingPlanningService;
        this.scheduleSelector = scheduleSelector;
    }

    public void applyIfMissing(
            Long organizationId,
            LocalDate assignmentDate,
            List<DriverAssignment> assignments) {
        DriverAssignment morningAssignment = assignments.stream()
                .filter(assignment -> assignment.getPeriod() == ShuttlePeriod.MORNING)
                .findFirst()
                .orElse(null);

        // Never overwrite existing times or an assignment after actual driving has started.
        if (morningAssignment == null
                || morningAssignment.getScheduledStartTime() != null
                || morningAssignment.getScheduledEndTime() != null
                || morningAssignment.hasDrivingSessions()) {
            return;
        }

        MorningDrivingPlan morningPlan = morningDrivingPlanningService.getPlanForDate(
                organizationId,
                assignmentDate
        );
        if (!morningPlan.hasDemand()) {
            return;
        }

        DriverScheduleResult scheduleResult = driverScheduleService.getDriverSchedulesForDate(
                organizationId,
                assignmentDate
        );
        DriverSchedule assignedDriverSchedule = scheduleSelector.findBestOverlapping(
                scheduleResult.scheduledDrivers(),
                morningAssignment.getScheduledDriver().getId(),
                morningPlan.getScheduledStartTime(),
                morningPlan.getScheduledEndTime()
        );
        applyIfAvailable(morningPlan, assignedDriverSchedule, morningAssignment);
    }

    public void applyIfAvailable(
            MorningDrivingPlan morningPlan,
            DriverSchedule driverSchedule,
            DriverAssignment morningAssignment) {
        if (morningPlan == null
                || !morningPlan.hasDemand()
                || driverSchedule == null
                || !driverSchedule.overlaps(
                        morningPlan.getScheduledStartTime(),
                        morningPlan.getScheduledEndTime()
                )) {
            return;
        }

        DriverSchedule.DrivingWindow clippedWindow = driverSchedule.clipTo(
                morningPlan.getScheduledStartTime(),
                morningPlan.getScheduledEndTime()
        );
        if (clippedWindow == null) {
            return;
        }

        morningAssignment.updateScheduledTimes(clippedWindow.startTime(), clippedWindow.endTime());
    }
}
