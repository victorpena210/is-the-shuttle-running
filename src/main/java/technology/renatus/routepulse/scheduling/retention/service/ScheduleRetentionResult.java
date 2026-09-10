package technology.renatus.routepulse.scheduling.retention.service;

public record ScheduleRetentionResult(
        int archivedOperatingDays,
        int purgedRawShiftRows,
        long expiredHistoricalSnapshots) {
}
