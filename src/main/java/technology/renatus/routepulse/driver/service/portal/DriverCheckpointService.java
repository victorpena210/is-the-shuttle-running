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
    private final DriverScanAssignmentSelector assignmentSelector;
    private final DriverCheckpointStartPolicy startPolicy;
    private final DriverAssignmentRepository assignmentRepository;
    private final DrivingSessionService drivingSessionService;
    private final ZoneId operationsZone;

    public DriverCheckpointService(
            DriverPortalAccessService accessService,
            DriverScanAssignmentSelector assignmentSelector,
            DriverCheckpointStartPolicy startPolicy,
            DriverAssignmentRepository assignmentRepository,
            DrivingSessionService drivingSessionService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.accessService = accessService;
        this.assignmentSelector = assignmentSelector;
        this.startPolicy = startPolicy;
        this.assignmentRepository = assignmentRepository;
        this.drivingSessionService = drivingSessionService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public CheckpointPreview getPreview(
            Authentication authentication,
            String checkpointCode,
            DrivingActionMethod method) {

        requirePhysicalMethod(method);
        Driver driver = prepareDriver(authentication, checkpointCode);
        LocalDateTime now = currentOperationsDateTime();
        Long organizationId = driver.getOrganization().getId();

        DrivingSession activeSession = drivingSessionService.findActiveSession(organizationId);
        if (activeSession != null) {
            return previewForActiveSession(driver, activeSession, method);
        }

        List<DriverAssignment> assignments = assignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(organizationId, now.toLocalDate());
        DriverAssignment assignment;
        try {
            assignment = assignmentSelector.selectStartAssignment(driver, assignments, now);
        } catch (DriverScanAssignmentSelector.NoStartableAssignmentException exception) {
            return new CheckpointPreview(driver, null, CheckpointPreviewAction.START_UNSCHEDULED,
                    false, false, null, null, method);
        }
        assignmentSelector.requireScheduledTimes(assignment);
        startPolicy.requireStartAllowed(driver, assignment, now);

        Driver scheduledDriver = assignment == null ? null : assignment.getScheduledDriver();
        CheckpointPreviewAction action = assignment.hasDrivingSessions()
                ? CheckpointPreviewAction.RESUME
                : CheckpointPreviewAction.START;

        return new CheckpointPreview(
                driver,
                assignment,
                action,
                isCoverage(scheduledDriver, driver),
                false,
                scheduledDriver,
                null,
                method);
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
        LocalDateTime now = LocalDateTime.ofInstant(eventNow, operationsZone);
        Long organizationId = driver.getOrganization().getId();

        // An organization lock also protects days with no assignment rows.
        drivingSessionService.lockOrganization(organizationId);
        List<DriverAssignment> assignments = assignmentRepository
                .findAllForUpdateByOrganizationAndDate(organizationId, now.toLocalDate());
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

        DriverAssignment assignment;
        try {
            assignment = assignmentSelector.selectStartAssignment(driver, assignments, now);
        } catch (DriverScanAssignmentSelector.NoStartableAssignmentException exception) {
            // Require explicit consent: an old scheduled-start form must never become unplanned work.
            if (requestedAction != CheckpointAction.START_UNSCHEDULED) {
                throw new IllegalStateException("No assignment is available. Tap again and confirm Start unscheduled driving.");
            }
            drivingSessionService.startUnscheduledSession(driver, user, eventNow, method);
            return new CheckpointResult(CheckpointAction.START_UNSCHEDULED, null,
                    false, false, null, null, method);
        }
        assignmentSelector.requireScheduledTimes(assignment);
        startPolicy.requireStartAllowed(driver, assignment, now);

        Driver scheduledDriver = assignment == null ? null : assignment.getScheduledDriver();
        CheckpointAction action = assignment.hasDrivingSessions()
                ? CheckpointAction.RESUME
                : CheckpointAction.START;
        requireRequestedAction(requestedAction, action);

        drivingSessionService.startSession(
                assignment,
                driver,
                user,
                eventNow,
                method);

        return new CheckpointResult(
                action,
                assignment,
                isCoverage(scheduledDriver, driver),
                false,
                scheduledDriver,
                null,
                method);
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

        // Older QR confirmation pages did not submit an explicit action. Keep
        // those safe during a rolling deploy, but reject any explicit stale or
        // tampered action after the server has re-evaluated the current state.
        if (requestedAction == null || requestedAction == expectedAction) {
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

        // Preserve the old QR behavior for any already-open confirmation form
        // that posts without an explicit action.
        if (requestedAction == null) {
            return CheckpointAction.END;
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

    private LocalDateTime currentOperationsDateTime() {
        return LocalDateTime.now(operationsZone);
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
