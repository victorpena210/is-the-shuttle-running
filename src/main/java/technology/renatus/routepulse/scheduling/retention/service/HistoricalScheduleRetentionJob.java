package technology.renatus.routepulse.scheduling.retention.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly privacy/forecasting retention job for completed operating days. */
// Retired: schedule forecasting is no longer part of the active application.
@ConditionalOnProperty(
        name = "app.retention.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HistoricalScheduleRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(
            HistoricalScheduleRetentionJob.class
    );

    private final HistoricalScheduleRetentionService retentionService;

    public HistoricalScheduleRetentionJob(
            HistoricalScheduleRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void archiveBacklogAfterStartup() {
        archiveCompletedSchedules();
    }

    @Scheduled(
            cron = "${app.retention.cron:0 15 2 * * *}",
            zone = "${app.operations-zone:America/Chicago}"
    )
    public synchronized void archiveCompletedSchedules() {
        try {
            ScheduleRetentionResult result = retentionService
                    .archiveAndPurgeCompletedDays();

            if (result.archivedOperatingDays() > 0
                    || result.purgedRawShiftRows() > 0
                    || result.expiredHistoricalSnapshots() > 0) {
                log.info(
                        "Schedule retention completed: archivedDays={}, purgedRawRows={}, expiredSnapshots={}",
                        result.archivedOperatingDays(),
                        result.purgedRawShiftRows(),
                        result.expiredHistoricalSnapshots()
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Schedule retention failed. Raw employee schedules were left intact for a later retry.",
                    exception
            );
        }
    }
}
