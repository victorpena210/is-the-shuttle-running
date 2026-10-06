package technology.renatus.routepulse.attention.service.checks;

import technology.renatus.routepulse.assignments.service.planning.MorningStaffingPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliation;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliationStatus;
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

        if (plan.morningStaffingBlocked()) {
            boolean assigned = plan.driverAssignments().stream().anyMatch(a -> a.getPeriod() == ShuttlePeriod.MORNING);
            items.add(new AttentionItem("morning-office-coverage", assigned ? AttentionSeverity.WARNING : AttentionSeverity.INFO,
                    assigned ? "Review morning wardrobe coverage" : "Morning driving withheld for wardrobe coverage",
                    assigned ? "An existing morning assignment is preserved, but the uploaded roster no longer meets the office staffing rule. Review coverage before driving."
                            : MorningStaffingPolicy.MESSAGE,
                    "Review staffing", AttentionSupport.dashboardHref(plan, "coverage")));
        }
        if (plan.openingStaffingBlocked()) {
            items.add(new AttentionItem("late-opening-office-coverage", AttentionSeverity.INFO,
                    "Opening driving withheld for counter coverage",
                    technology.renatus.routepulse.assignments.service.planning.LateOpeningPolicy.MESSAGE,
                    "Review staffing", AttentionSupport.dashboardHref(plan, "coverage")));
        }
        if (plan.drivingStartConflict()) {
            items.add(new AttentionItem("driving-before-demand-or-close", AttentionSeverity.WARNING,
                    "Review driving start against demand and wardrobe hours",
                    "An existing midday or evening assignment conflicts with demand, wardrobe hours, or opening counter coverage. Its history or manager decision was preserved; review the assignment.",
                    "Review driving coverage", AttentionSupport.dashboardHref(plan, "coverage")));
        }
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

        for (ScheduleReconciliation reconciliation : plan.scheduleReconciliations()) {
            if (reconciliation.status() != ScheduleReconciliationStatus.COVERAGE_STILL_VALID) {
                mismatchedAssignmentIds.add(reconciliation.assignmentId());
            }

            if (!reconciliation.requiresAction()) {
                continue;
            }

            String title = switch (reconciliation.status()) {
                case COVERAGE_STILL_VALID ->
                        "Schedule changed: " + reconciliation.scheduledDriverName()
                                + " still covers " + reconciliation.periodLabel();
                case REPLACEMENT_RECOMMENDED ->
                        "Schedule changed: " + reconciliation.scheduledDriverName()
                                + " no longer covers " + reconciliation.periodLabel();
                case NO_REPLACEMENT_AVAILABLE ->
                        "Schedule changed: " + reconciliation.periodLabel()
                                + " needs a coverage decision";
                case HISTORY_PRESERVED ->
                        "Schedule history preserved";
            };

            String detail = reconciliation.detail();
            if (reconciliation.hasRecommendation()) {
                detail += " Recommended replacement: "
                        + reconciliation.recommendedDriverName() + ".";
            }

            items.add(new AttentionItem(
                    "schedule-reconciliation-" + reconciliation.assignmentId(),
                    AttentionSeverity.WARNING,
                    title,
                    detail,
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
            if (!plan.requiresCoverage(period)) continue;
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
                                ? "No active Is the Shuttle Running driver has an uploaded work shift that can cover this transportation period."
                                : "No active Is the Shuttle Running driver has an uploaded work shift overlapping "
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

            boolean hasNewReconciliation = plan.scheduleReconciliations().stream()
                    .anyMatch(item -> item.assignmentId().equals(assignment.getId()));

            if (assignment.getReviewedScheduleImport() != null
                    && !hasNewReconciliation) {
                // A manager already made an explicit schedule decision and
                // the reconciliation engine found no meaningful new change
                // since that reviewed version (including identical re-uploads).
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
                            : driverName + " does not have a work shift fully covering "
                                    + requestedWindow + ".",
                    "Review assignment",
                    AttentionSupport.dashboardHref(plan, "coverage")
            ));
        }
    }
}
