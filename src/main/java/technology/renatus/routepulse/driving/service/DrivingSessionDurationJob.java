package technology.renatus.routepulse.driving.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;

/** Enforces the four-hour limit without a browser, location signal or driver check-in. */
@Component
public class DrivingSessionDurationJob {
    private static final Logger log = LoggerFactory.getLogger(DrivingSessionDurationJob.class);
    private final DrivingSessionRepository repository;
    private final DrivingSessionService sessions;

    public DrivingSessionDurationJob(DrivingSessionRepository repository, DrivingSessionService sessions) {
        this.repository = repository;
        this.sessions = sessions;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void check() {
        Instant now = Instant.now();
        for (Long organizationId : repository.findOverdueOrganizationIds(now.minus(DrivingSession.MAX_DURATION))) {
            try {
                sessions.endOverdueSessions(organizationId, now);
            } catch (RuntimeException exception) {
                log.error("Could not close overdue driving sessions for organization {}", organizationId, exception);
            }
        }
    }
}
