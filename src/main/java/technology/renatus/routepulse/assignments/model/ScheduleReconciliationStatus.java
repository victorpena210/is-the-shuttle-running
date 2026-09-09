package technology.renatus.routepulse.assignments.model;

/** Result of comparing an existing assignment with the newest uploaded employee schedule. */
public enum ScheduleReconciliationStatus {
    COVERAGE_STILL_VALID,
    REPLACEMENT_RECOMMENDED,
    NO_REPLACEMENT_AVAILABLE,
    HISTORY_PRESERVED
}
