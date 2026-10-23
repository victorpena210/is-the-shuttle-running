package technology.renatus.routepulse.driver.service.portal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.domain.DrivingSessionEndReason;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.service.DrivingSessionService;

/**
 * Coordinates the shared physical shuttle checkpoint used by both QR scans and
 * NFC taps. The physical method is recorded on every session while all state
 * decisions remain server-side.
 */
@Service
@Transactional(readOnly = true)
public class DriverCheckpointService {

    private final DriverPortalAccessService accessService;
    private final DrivingSessionService drivingSessionService;

    public DriverCheckpointService(
            DriverPortalAccessService accessService,
            DrivingSessionService drivingSessionService) {
        this.accessService = accessService;
        this.drivingSessionService = drivingSessionService;
    }

    public CheckpointPreview getPreview(
            Authentication authentication,
            String checkpointCode,
            DrivingActionMethod method) {

        requirePhysicalMethod(method);
        Driver driver = prepareDriver(authentication, checkpointCode);
        Long organizationId = driver.getOrganization().getId();

        DrivingSession activeSession = drivingSessionService.findActiveSession(organizationId);
        if (activeSession != null) {
            return previewForActiveSession(driver, activeSession, method);
        }

        return new CheckpointPreview(driver, null, CheckpointPreviewAction.START,
                false, false, null, null, method);
    }

    @Transactional
    public CheckpointResult recordCheckpoint(
            Authentication authentication,
            String checkpointCode,
            DrivingActionMethod method,
            CheckpointAction requestedAction) {

        requirePhysicalMethod(method);
        Driver driver = prepareDriver(authentication, checkpointCode);
        UserAccount user = accessService.requireCurrentUser(authentication);
        Instant eventNow = Instant.now();
        Long organizationId = driver.getOrganization().getId();

        // An organization lock also protects days with no assignment rows.
        drivingSessionService.lockOrganization(organizationId);
        DrivingSession activeSession = drivingSessionService
                .findActiveSessionForUpdate(organizationId);

        if (activeSession != null) {
            return recordAgainstActiveSession(
                    driver,
                    user,
                    activeSession,
                    eventNow,
                    method,
                    requestedAction);
        }

        // A stale pause, end, or handoff form must never silently start a new run.
        if (requestedAction != CheckpointAction.START
                && requestedAction != CheckpointAction.START_UNSCHEDULED
                && requestedAction != CheckpointAction.RESUME) {
            throw new IllegalStateException("The driving state changed. Tap or scan again and confirm Start driving.");
        }
        drivingSessionService.startUnscheduledSession(driver, user, eventNow, method);
        return new CheckpointResult(CheckpointAction.START, null,
                false, false, null, null, method);
    }

    private Driver prepareDriver(Authentication authentication, String checkpointCode) {
        Driver driver = accessService.requireCurrentDriver(authentication);
        accessService.requireActiveDriver(driver);
        accessService.validateCheckpointCode(driver.getOrganization(), checkpointCode);
        return driver;
    }

    private CheckpointPreview previewForActiveSession(
            Driver driver,
            DrivingSession activeSession,
            DrivingActionMethod method) {

        DriverAssignment assignment = activeSession.getAssignment();
        Driver scheduledDriver = assignment == null ? null : assignment.getScheduledDriver();

        if (sameDriver(activeSession.getDriver(), driver)) {
            return new CheckpointPreview(
                    driver,
                    assignment,
                    CheckpointPreviewAction.PAUSE_OR_END,
                    false,
                    false,
                    scheduledDriver,
                    activeSession.getDriver(),
                    method);
        }

        return new CheckpointPreview(
                driver,
                assignment,
                CheckpointPreviewAction.TAKEOVER,
                isCoverage(scheduledDriver, driver),
                true,
                scheduledDriver,
                activeSession.getDriver(),
                method);
    }

    private CheckpointResult recordAgainstActiveSession(
            Driver driver,
            UserAccount user,
            DrivingSession activeSession,
            Instant now,
            DrivingActionMethod method,
            CheckpointAction requestedAction) {

        DriverAssignment assignment = activeSession.getAssignment();
        Driver scheduledDriver = assignment == null ? null : assignment.getScheduledDriver();

        if (sameDriver(activeSession.getDriver(), driver)) {
            CheckpointAction stopAction = normalizeStopAction(requestedAction);
            DrivingSessionEndReason endReason = stopAction == CheckpointAction.PAUSE
                    ? DrivingSessionEndReason.PAUSE
                    : DrivingSessionEndReason.COMPLETED;

            drivingSessionService.endSession(
                    assignment,
                    activeSession,
                    user,
                    now,
                    method,
                    endReason);

            return new CheckpointResult(
                    stopAction,
                    assignment,
                    false,
                    false,
                    scheduledDriver,
                    null,
                    method);
        }

        requireRequestedAction(requestedAction, CheckpointAction.TAKEOVER);

        Driver previousActualDriver = activeSession.getDriver();
        drivingSessionService.takeOverActiveSession(
                activeSession,
                driver,
                user,
                now,
                method);

        return new CheckpointResult(
                CheckpointAction.TAKEOVER,
                assignment,
                isCoverage(scheduledDriver, driver),
                true,
                scheduledDriver,
                previousActualDriver,
                method);
    }

    private void requireRequestedAction(
            CheckpointAction requestedAction,
            CheckpointAction expectedAction) {

        if (requestedAction == expectedAction) {
            return;
        }

        throw new IllegalStateException(
                "The driving state changed before this checkpoint was confirmed. "
                        + "Tap the NFC tag or scan the QR code again to refresh the action.");
    }

    private CheckpointAction normalizeStopAction(CheckpointAction requestedAction) {
        if (requestedAction == CheckpointAction.PAUSE
                || requestedAction == CheckpointAction.END) {
            return requestedAction;
        }

        throw new IllegalStateException(
                "Driving is already active. Choose Pause driving or End driving.");
    }

    private void requirePhysicalMethod(DrivingActionMethod method) {
        if (method != DrivingActionMethod.QR && method != DrivingActionMethod.NFC) {
            throw new IllegalArgumentException(
                    "Driver checkpoints must be recorded as an NFC tap or QR scan.");
        }
    }

    private boolean isCoverage(Driver scheduledDriver, Driver actualDriver) {
        return scheduledDriver != null && !sameDriver(scheduledDriver, actualDriver);
    }

    private boolean sameDriver(Driver first, Driver second) {
        return first != null
                && second != null
                && Objects.equals(first.getId(), second.getId());
    }

    public enum CheckpointPreviewAction {
        START_UNSCHEDULED,
        START,
        RESUME,
        TAKEOVER,
        PAUSE_OR_END
    }

    public enum CheckpointAction {
        START_UNSCHEDULED,
        START,
        RESUME,
        TAKEOVER,
        PAUSE,
        END
    }

    public record CheckpointPreview(
            Driver driver,
            DriverAssignment assignment,
            CheckpointPreviewAction action,
            boolean coverageRequired,
            boolean takeoverRequired,
            Driver scheduledDriver,
            Driver currentActualDriver,
            DrivingActionMethod method) {
    }

    public record CheckpointResult(
            CheckpointAction action,
            DriverAssignment assignment,
            boolean coveragePerformed,
            boolean takeoverPerformed,
            Driver scheduledDriver,
            Driver previousActualDriver,
            DrivingActionMethod method) {
    }
}
