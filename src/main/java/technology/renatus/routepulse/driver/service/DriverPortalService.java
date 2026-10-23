package technology.renatus.routepulse.driver.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointAction;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointPreview;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointResult;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.model.UnscheduledDrivingRecord;

/** Facade for the driver portal's profile, actual driving history, and physical checkpoint actions. */
@Service
@Transactional(readOnly = true)
public class DriverPortalService {

    private final DriverPortalAccessService accessService;
    private final DriverCheckpointService checkpointService;
    private final ZoneId operationsZone;
    private final DrivingSessionRepository drivingSessionRepository;
    private final technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy freshness;

    public DriverPortalService(
            DriverPortalAccessService accessService,
            DriverCheckpointService checkpointService,
            DrivingSessionRepository drivingSessionRepository,
            technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy freshness,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.drivingSessionRepository = drivingSessionRepository;
        this.freshness = freshness;
        this.accessService = accessService;
        this.checkpointService = checkpointService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public Driver requireCurrentDriver(Authentication authentication) {
        return accessService.requireCurrentDriver(authentication);
    }

    public ActiveConfirmation getActiveConfirmation(Authentication authentication) {
        var driver = requireCurrentDriver(authentication);
        return drivingSessionRepository.findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc(driver.getOrganization().getId())
                .filter(s -> s.getDriver().getId().equals(driver.getId()))
                .map(s -> new ActiveConfirmation(s.getId(), freshness.confirmationDueAt(s), !freshness.isFresh(s, java.time.Instant.now())))
                .orElse(null);
    }

    public record ActiveConfirmation(Long sessionId, java.time.Instant dueAt, boolean overdue) {}

    public List<UnscheduledDrivingRecord> getRecentDrivingRecords(Authentication authentication) {
        Driver driver = requireCurrentDriver(authentication);
        return drivingSessionRepository
                .findTop20ByOrganization_IdAndDriver_IdOrderByStartedAtDesc(
                        driver.getOrganization().getId(), driver.getId())
                .stream().map(session -> UnscheduledDrivingRecord.from(session, operationsZone)).toList();
    }

    public CheckpointPreview getCheckpointPreview(
            Authentication authentication,
            String checkpointCode,
            DrivingActionMethod method) {

        return checkpointService.getPreview(authentication, checkpointCode, method);
    }

    @Transactional
    public CheckpointResult recordCheckpoint(
            Authentication authentication,
            String checkpointCode,
            DrivingActionMethod method,
            CheckpointAction requestedAction) {

        return checkpointService.recordCheckpoint(
                authentication,
                checkpointCode,
                method,
                requestedAction);
    }

    public LocalDate currentOperationsDate() {
        return LocalDate.now(operationsZone);
    }

}
