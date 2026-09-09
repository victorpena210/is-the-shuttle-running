package technology.renatus.routepulse.assignments.model;

import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;

/**
 * Explains how a newer schedule import affects one already-created driving assignment.
 * The assignment itself is not changed until a manager explicitly chooses an action.
 */
public record ScheduleReconciliation(
        Long assignmentId,
        ShuttlePeriod period,
        String periodLabel,
        Long scheduledDriverId,
        String scheduledDriverName,
        Long baselineScheduleImportId,
        Long latestScheduleImportId,
        String previousScheduleLabel,
        String latestScheduleLabel,
        String drivingWindowLabel,
        ScheduleReconciliationStatus status,
        Long recommendedDriverId,
        String recommendedDriverName,
        String recommendedDriverScheduleLabel,
        String detail) {

    public boolean requiresAction() {
        return status != ScheduleReconciliationStatus.HISTORY_PRESERVED;
    }

    public boolean hasRecommendation() {
        return status == ScheduleReconciliationStatus.REPLACEMENT_RECOMMENDED
                && recommendedDriverId != null;
    }
}
