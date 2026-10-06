package technology.renatus.routepulse.driving.movement;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;

@Component
public class MovementMonitoringJob {
    private static final Logger log=LoggerFactory.getLogger(MovementMonitoringJob.class);
    private final DrivingSessionRepository repository;
    private final MovementMonitoringService monitoring;
    public MovementMonitoringJob(DrivingSessionRepository repository, MovementMonitoringService monitoring) {
        this.repository=repository; this.monitoring=monitoring;
    }
    @Scheduled(fixedDelayString="${app.movement.check-ms:30000}", initialDelayString="${app.movement.check-ms:30000}")
    public void check() {
        for (Long organizationId:repository.findMonitoredOrganizationIds()) {
            try { monitoring.evaluateOrganization(organizationId,Instant.now()); }
            catch (RuntimeException ex) { log.error("Movement monitoring failed for organization {}",organizationId,ex); }
        }
    }
}
