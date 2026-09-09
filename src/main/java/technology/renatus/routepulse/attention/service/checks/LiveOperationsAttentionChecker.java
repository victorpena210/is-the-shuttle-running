package technology.renatus.routepulse.attention.service.checks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
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

        LocalDateTime scheduledStart =
                AttentionSupport.scheduledStart(plan, assignment);

        if (scheduledStart == null
                || assignment.getAssignmentOutcome() != AssignmentOutcome.PENDING
                || assignment.hasDrivingSessions()
                || !now.isAfter(scheduledStart.plusMinutes(startGraceMinutes))) {
            return;
        }

        long minutesLate = Math.max(
                1,
                Duration.between(scheduledStart, now).toMinutes()
        );

        items.add(new AttentionItem(
                "driving-not-started-" + assignment.getId(),
                AttentionSeverity.URGENT,
                AttentionSupport.periodLabel(assignment.getPeriod())
                        + " driving has not started",
                "Scheduled for "
                        + AttentionSupport.formatTime(scheduledStart.toLocalTime())
                        + ". It is now "
                        + AttentionSupport.formatTime(now.toLocalTime())
                        + " — " + minutesLate
                        + " minutes past the planned start.",
                "Review shift",
                AttentionSupport.dashboardHref(plan, "coverage")
        ));
    }

    private void addOverrunAlert(
            DashboardPlan plan,
            DriverAssignment assignment,
            LocalDateTime now,
            List<AttentionItem> items) {

        LocalDateTime scheduledEnd =
                AttentionSupport.scheduledEnd(plan, assignment);

        if (scheduledEnd == null
                || !assignment.isDriving()
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
