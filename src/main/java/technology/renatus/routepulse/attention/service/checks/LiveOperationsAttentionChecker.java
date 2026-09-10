package technology.renatus.routepulse.attention.service.checks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.PlannedDrivingWindow;
import technology.renatus.routepulse.attention.model.AttentionItem;
import technology.renatus.routepulse.attention.model.AttentionSeverity;
import technology.renatus.routepulse.attention.service.AttentionSupport;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;

/** Flags real-time start and stop problems only for the live operating date. */
@Component
public class LiveOperationsAttentionChecker {

    private final long startGraceMinutes;
    private final long overrunGraceMinutes;

    public LiveOperationsAttentionChecker(
            @Value("${app.attention.start-grace-minutes:10}") long startGraceMinutes,
            @Value("${app.attention.overrun-grace-minutes:10}") long overrunGraceMinutes) {

        this.startGraceMinutes = Math.max(0, startGraceMinutes);
        this.overrunGraceMinutes = Math.max(0, overrunGraceMinutes);
    }

    public void addAlerts(
            DashboardPlan plan,
            LocalDateTime now,
            List<AttentionItem> items) {

        if (!AttentionSupport.shouldPlanCoverage(plan)
                || plan.selectedDate() == null
                || !plan.selectedDate().equals(now.toLocalDate())) {
            return;
        }

        for (DriverAssignment assignment : plan.driverAssignments()) {
            addLateStartAlert(plan, assignment, now, items);
            addOverrunAlert(plan, assignment, now, items);
        }
    }

    private void addLateStartAlert(
            DashboardPlan plan,
            DriverAssignment assignment,
            LocalDateTime now,
            List<AttentionItem> items) {

        if (assignment.hasPlannedDrivingWindows()) {
            addDetailedWindowNotRunningAlert(plan, assignment, now, items);
            return;
        }

        LocalDateTime scheduledStart = AttentionSupport.scheduledStart(plan, assignment);

        if (scheduledStart == null
                || assignment.getAssignmentOutcome() != AssignmentOutcome.PENDING
                || assignment.hasDrivingSessions()
                || !now.isAfter(scheduledStart)) {
            return;
        }

        long minutesLate = Math.max(
                1,
                Duration.between(scheduledStart, now).toMinutes()
        );

        LocalDateTime urgentAt = scheduledStart.plusMinutes(startGraceMinutes);
        boolean graceExpired = startGraceMinutes == 0
                || !now.isBefore(urgentAt);

        AttentionSeverity severity = graceExpired
                ? AttentionSeverity.URGENT
                : AttentionSeverity.WARNING;

        String detail = buildLateStartDetail(
                assignment,
                scheduledStart,
                urgentAt,
                now,
                minutesLate,
                graceExpired
        );

        items.add(new AttentionItem(
                "driving-not-started-" + assignment.getId(),
                severity,
                AttentionSupport.periodLabel(assignment.getPeriod())
                        + " shuttle has not started",
                detail,
                "Review shift",
                AttentionSupport.dashboardHref(plan, "coverage")
        ));
    }

    private void addDetailedWindowNotRunningAlert(
            DashboardPlan plan,
            DriverAssignment assignment,
            LocalDateTime now,
            List<AttentionItem> items) {

        if (assignment.isDriving()
                || assignment.getAssignmentOutcome() == AssignmentOutcome.CANCELLED
                || assignment.getAssignmentOutcome() == AssignmentOutcome.EXCUSED
                || assignment.getAssignmentOutcome() == AssignmentOutcome.MISSED) {
            return;
        }

        PlannedDrivingWindow activeWindow = assignment.getPlannedDrivingWindows().stream()
                .filter(window -> window.contains(now.toLocalTime()))
                .findFirst()
                .orElse(null);

        if (activeWindow == null || !now.toLocalTime().isAfter(activeWindow.getStartTime())) {
            return;
        }

        LocalDateTime scheduledStart = LocalDateTime.of(
                assignment.getAssignmentDate(),
                activeWindow.getStartTime()
        );
        LocalDateTime urgentAt = scheduledStart.plusMinutes(startGraceMinutes);
        long minutesLate = Math.max(1, Duration.between(scheduledStart, now).toMinutes());
        boolean graceExpired = startGraceMinutes == 0 || !now.isBefore(urgentAt);

        String driverName = assignment.getScheduledDriver() == null
                ? "The assigned driver"
                : assignment.getScheduledDriver().getName();

        String detail = driverName
                + " should be driving during the planned window "
                + AttentionSupport.formatTime(activeWindow.getStartTime())
                + "–"
                + AttentionSupport.formatTime(activeWindow.getEndTime())
                + ". It is now "
                + AttentionSupport.formatTime(now.toLocalTime())
                + " and no driving session is active.";

        if (!graceExpired && startGraceMinutes > 0) {
            detail += " RoutePulse will mark this urgent at "
                    + AttentionSupport.formatTime(urgentAt.toLocalTime())
                    + " if driving does not resume.";
        } else {
            detail += " Manager review is required.";
        }

        items.add(new AttentionItem(
                "driving-window-not-active-" + assignment.getId()
                        + "-" + activeWindow.getStartTime(),
                graceExpired ? AttentionSeverity.URGENT : AttentionSeverity.WARNING,
                AttentionSupport.periodLabel(assignment.getPeriod())
                        + " shuttle is not running",
                detail,
                "Review shift",
                AttentionSupport.dashboardHref(plan, "coverage")
        ));
    }

    private String buildLateStartDetail(
            DriverAssignment assignment,
            LocalDateTime scheduledStart,
            LocalDateTime urgentAt,
            LocalDateTime now,
            long minutesLate,
            boolean graceExpired) {

        String driverName = assignment.getScheduledDriver() == null
                ? "The assigned driver"
                : assignment.getScheduledDriver().getName();

        String base = driverName
                + " was scheduled to start at "
                + AttentionSupport.formatTime(scheduledStart.toLocalTime())
                + ". It is now "
                + AttentionSupport.formatTime(now.toLocalTime())
                + " — " + minutesLate
                + (minutesLate == 1 ? " minute" : " minutes")
                + " past the planned start.";

        if (graceExpired || startGraceMinutes == 0) {
            return base
                    + " No driving session has been recorded, so manager review is required.";
        }

        return base
                + " RoutePulse will mark this urgent at "
                + AttentionSupport.formatTime(urgentAt.toLocalTime())
                + " if no driving session starts.";
    }

    private void addOverrunAlert(
            DashboardPlan plan,
            DriverAssignment assignment,
            LocalDateTime now,
            List<AttentionItem> items) {

        if (!assignment.isDriving()) {
            return;
        }

        LocalDateTime scheduledEnd;
        if (assignment.hasPlannedDrivingWindows()) {
            PlannedDrivingWindow currentWindow = assignment.getPlannedDrivingWindows().stream()
                    .filter(window -> window.contains(now.toLocalTime()))
                    .findFirst()
                    .orElse(null);

            // Driving is valid while the clock is inside any detailed window.
            if (currentWindow != null) {
                return;
            }

            PlannedDrivingWindow previousWindow = assignment.getPlannedDrivingWindows().stream()
                    .filter(window -> !window.getEndTime().isAfter(now.toLocalTime()))
                    .reduce((first, second) -> second)
                    .orElse(null);

            if (previousWindow == null) {
                return;
            }

            scheduledEnd = LocalDateTime.of(
                    assignment.getAssignmentDate(),
                    previousWindow.getEndTime()
            );
        } else {
            scheduledEnd = AttentionSupport.scheduledEnd(plan, assignment);
        }

        if (scheduledEnd == null
                || !now.isAfter(scheduledEnd.plusMinutes(overrunGraceMinutes))) {
            return;
        }

        long minutesPastEnd = Math.max(
                1,
                Duration.between(scheduledEnd, now).toMinutes()
        );

        String actualDriverName = assignment.getFinalDriver() == null
                ? assignment.getScheduledDriver().getName()
                : assignment.getFinalDriver().getName();

        items.add(new AttentionItem(
                "driving-past-end-" + assignment.getId(),
                AttentionSeverity.URGENT,
                "Shuttle has been running " + minutesPastEnd
                        + " minutes past scheduled end",
                AttentionSupport.periodLabel(assignment.getPeriod())
                        + " coverage was scheduled to end at "
                        + AttentionSupport.formatTime(scheduledEnd.toLocalTime())
                        + ". " + actualDriverName
                        + " is still marked as driving.",
                "Review active driving",
                AttentionSupport.dashboardHref(plan, "coverage")
        ));
    }
}
