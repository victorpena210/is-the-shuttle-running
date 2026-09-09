package technology.renatus.routepulse.attention.service.checks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.attention.model.AttentionItem;
import technology.renatus.routepulse.attention.model.AttentionSeverity;
import technology.renatus.routepulse.attention.service.AttentionSupport;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;

/** Flags stale assignments, missing coverage, and assigned drivers who are no longer scheduled. */
@Component
public class CoverageAttentionChecker {

    public void addAlerts(
            DashboardPlan plan,
            List<AttentionItem> items) {

        if (!AttentionSupport.shouldPlanCoverage(plan)) {
            return;
        }

        Set<Long> scheduleMismatchAssignmentIds =
                addScheduleReviewAlerts(plan, items);

        addMissingCoverageAlerts(plan, items);
        addAssignedDriverScheduleAlerts(
                plan,
                scheduleMismatchAssignmentIds,
                items
        );
    }

    private Set<Long> addScheduleReviewAlerts(
            DashboardPlan plan,
            List<AttentionItem> items) {

        Set<Long> mismatchedAssignmentIds = new HashSet<>();

        if (!plan.assignmentReviewRequired()) {
            return mismatchedAssignmentIds;
        }

        Long latestImportId = plan.latestScheduleImport() == null
                ? null
                : plan.latestScheduleImport().getId();

        for (DriverAssignment assignment : plan.driverAssignments()) {
            if (!assignment.requiresReviewAgainst(latestImportId)) {
                continue;
            }

            if (AttentionSupport.assignedDriverStillMatchesCurrentSchedule(
                    plan,
                    assignment
            )) {
                continue;
            }

            mismatchedAssignmentIds.add(assignment.getId());

            String driverName = assignment.getScheduledDriver().getName();
            String periodLabel = AttentionSupport.periodLabel(
                    assignment.getPeriod()
            );
            String requestedWindow = AttentionSupport.requestedWindowLabel(
                    plan,
                    assignment
            );

            items.add(new AttentionItem(
                    "schedule-change-" + assignment.getId(),
                    AttentionSeverity.WARNING,
                    "New schedule changed " + driverName + "’s shift",
                    requestedWindow == null
                            ? driverName + " no longer has a usable work shift for "
                                    + periodLabel + " coverage. Review or replace this assignment."
                            : driverName + " no longer has a work shift overlapping "
                                    + requestedWindow + " for " + periodLabel + " coverage.",
                    "Review coverage",
                    AttentionSupport.dashboardHref(plan, "coverage")
            ));
        }

        if (mismatchedAssignmentIds.isEmpty()) {
            items.add(new AttentionItem(
                    "schedule-review-required",
                    AttentionSeverity.WARNING,
                    "Driver coverage needs review after a schedule upload",
                    "A newer employee schedule is now being used for this date. Confirm the existing automatic assignments still make sense before operations begin.",
                    "Review coverage",
                    AttentionSupport.dashboardHref(plan, "coverage")
            ));
        }

        return mismatchedAssignmentIds;
    }

    private void addMissingCoverageAlerts(
            DashboardPlan plan,
            List<AttentionItem> items) {

        for (ShuttlePeriod period : ShuttlePeriod.values()) {
            TransportationDemandPeriod demandPeriod =
                    AttentionSupport.demandPeriod(plan, period);

            if (demandPeriod == null || !demandPeriod.hasDemand()) {
                continue;
            }

            boolean assignmentExists = plan.driverAssignments().stream()
                    .anyMatch(assignment -> assignment.getPeriod() == period);

            if (assignmentExists) {
                continue;
            }

            String periodLabel = AttentionSupport.periodLabel(period);
            String requestedWindow =
                    AttentionSupport.requestedWindowLabel(demandPeriod);
            boolean eligibleDriverExists = AttentionSupport.hasEligibleDriver(
                    plan.driverSchedules(),
                    demandPeriod
            );

            if (!eligibleDriverExists) {
                items.add(new AttentionItem(
                        "missing-coverage-no-eligible-" + period.name(),
                        AttentionSeverity.URGENT,
                        periodLabel + " coverage has no eligible driver",
                        requestedWindow == null
                                ? "No active RoutePulse driver has an uploaded work shift that can cover this transportation period."
                                : "No active RoutePulse driver has an uploaded work shift overlapping "
                                        + requestedWindow + ".",
                        "Resolve coverage",
                        AttentionSupport.dashboardHref(plan, "coverage")
                ));
                continue;
            }

            items.add(new AttentionItem(
                    "missing-coverage-" + period.name(),
                    AttentionSeverity.WARNING,
                    periodLabel + " coverage is unassigned",
                    requestedWindow == null
                            ? "Transportation demand exists for this period, but no driver has been assigned yet."
                            : "Transportation demand exists from " + requestedWindow
                                    + ", but no driver has been assigned yet.",
                    "Assign driver",
                    AttentionSupport.dashboardHref(plan, "coverage")
            ));
        }
    }

    private void addAssignedDriverScheduleAlerts(
            DashboardPlan plan,
            Set<Long> scheduleMismatchAssignmentIds,
            List<AttentionItem> items) {

        for (DriverAssignment assignment : plan.driverAssignments()) {
            if (scheduleMismatchAssignmentIds.contains(assignment.getId())) {
                continue;
            }

            if (AttentionSupport.assignedDriverStillMatchesCurrentSchedule(
                    plan,
                    assignment
            )) {
                continue;
            }

            String driverName = assignment.getScheduledDriver().getName();
            String periodLabel = AttentionSupport.periodLabel(
                    assignment.getPeriod()
            );
            String requestedWindow = AttentionSupport.requestedWindowLabel(
                    plan,
                    assignment
            );

            items.add(new AttentionItem(
                    "assigned-driver-not-scheduled-" + assignment.getId(),
                    AttentionSeverity.WARNING,
                    driverName + " is assigned but not scheduled for " + periodLabel,
                    requestedWindow == null
                            ? driverName + " does not have a usable uploaded work shift for this assignment."
                            : driverName + " does not have a work shift overlapping "
                                    + requestedWindow + ".",
                    "Review assignment",
                    AttentionSupport.dashboardHref(plan, "coverage")
            ));
        }
    }
}
