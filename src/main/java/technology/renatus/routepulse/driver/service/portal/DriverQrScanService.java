package technology.renatus.routepulse.driver.service.portal;

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
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.service.DrivingSessionService;

/** Coordinates driver QR previews and start/end/takeover recording. */
@Service
@Transactional(readOnly = true)
public class DriverQrScanService {

    private final DriverPortalAccessService accessService;
    private final DriverScanAssignmentSelector assignmentSelector;
    private final DriverAssignmentRepository assignmentRepository;
    private final DrivingSessionService drivingSessionService;
    private final ZoneId operationsZone;

    public DriverQrScanService(
            DriverPortalAccessService accessService,
            DriverScanAssignmentSelector assignmentSelector,
            DriverAssignmentRepository assignmentRepository,
            DrivingSessionService drivingSessionService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.accessService = accessService;
        this.assignmentSelector = assignmentSelector;
        this.assignmentRepository = assignmentRepository;
        this.drivingSessionService = drivingSessionService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public ScanPreview getScanPreview(Authentication authentication, String qrCode) {
        Driver driver = prepareDriver(authentication, qrCode);
        LocalDateTime now = currentOperationsDateTime();
        Long organizationId = driver.getOrganization().getId();

        DrivingSession activeSession = drivingSessionService.findActiveSession(organizationId);
        if (activeSession != null) {
            return previewForActiveSession(driver, activeSession);
        }

        List<DriverAssignment> assignments = assignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(organizationId, now.toLocalDate());
        DriverAssignment assignment = assignmentSelector
                .selectStartAssignment(driver, assignments, now);
        assignmentSelector.requireScheduledTimes(assignment);

        Driver scheduledDriver = assignment.getScheduledDriver();
        return new ScanPreview(
                driver,
                assignment,
                ScanAction.START,
                !sameDriver(scheduledDriver, driver),
                false,
                scheduledDriver,
                null);
    }

    @Transactional
    public ScanResult recordQrScan(Authentication authentication, String qrCode) {
        Driver driver = prepareDriver(authentication, qrCode);
        UserAccount user = accessService.requireCurrentUser(authentication);
        LocalDateTime now = currentOperationsDateTime();
        Long organizationId = driver.getOrganization().getId();

        // Lock today's assignments so simultaneous scans cannot start separate shifts.
        List<DriverAssignment> assignments = assignmentRepository
                .findAllForUpdateByOrganizationAndDate(organizationId, now.toLocalDate());
        DrivingSession activeSession = drivingSessionService
                .findActiveSessionForUpdate(organizationId);

        if (activeSession != null) {
            return recordAgainstActiveSession(driver, user, activeSession, now);
        }

        DriverAssignment assignment = assignmentSelector
                .selectStartAssignment(driver, assignments, now);
        assignmentSelector.requireScheduledTimes(assignment);

        Driver scheduledDriver = assignment.getScheduledDriver();
        drivingSessionService.startSession(
                assignment,
                driver,
                user,
                now,
                DrivingActionMethod.QR);

        return new ScanResult(
                ScanAction.START,
                assignment,
                !sameDriver(scheduledDriver, driver),
                false,
                scheduledDriver,
                null);
    }

    private Driver prepareDriver(Authentication authentication, String qrCode) {
        Driver driver = accessService.requireCurrentDriver(authentication);
        accessService.requireActiveDriver(driver);
        accessService.validateQrCode(driver.getOrganization(), qrCode);
        return driver;
    }

    private ScanPreview previewForActiveSession(Driver driver, DrivingSession activeSession) {
        DriverAssignment assignment = activeSession.getAssignment();
        Driver scheduledDriver = assignment.getScheduledDriver();

        if (sameDriver(activeSession.getDriver(), driver)) {
            return new ScanPreview(
                    driver,
                    assignment,
                    ScanAction.END,
                    false,
                    false,
                    scheduledDriver,
                    activeSession.getDriver());
        }

        return new ScanPreview(
                driver,
                assignment,
                ScanAction.START,
                !sameDriver(scheduledDriver, driver),
                true,
                scheduledDriver,
                activeSession.getDriver());
    }

    private ScanResult recordAgainstActiveSession(
            Driver driver,
            UserAccount user,
            DrivingSession activeSession,
            LocalDateTime now) {

        DriverAssignment assignment = activeSession.getAssignment();
        Driver scheduledDriver = assignment.getScheduledDriver();

        if (sameDriver(activeSession.getDriver(), driver)) {
            drivingSessionService.endSession(
                    assignment,
                    activeSession,
                    user,
                    now,
                    DrivingActionMethod.QR);
            return new ScanResult(
                    ScanAction.END,
                    assignment,
                    false,
                    false,
                    scheduledDriver,
                    null);
        }

        Driver previousActualDriver = activeSession.getDriver();
        drivingSessionService.takeOverActiveSession(
                activeSession,
                driver,
                user,
                now,
                DrivingActionMethod.QR);

        return new ScanResult(
                ScanAction.START,
                assignment,
                !sameDriver(scheduledDriver, driver),
                true,
                scheduledDriver,
                previousActualDriver);
    }

    private LocalDateTime currentOperationsDateTime() {
        return LocalDateTime.now(operationsZone);
    }

    private boolean sameDriver(Driver first, Driver second) {
        return first != null
                && second != null
                && Objects.equals(first.getId(), second.getId());
    }

    public enum ScanAction {
        START,
        END
    }

    public record ScanPreview(
            Driver driver,
            DriverAssignment assignment,
            ScanAction action,
            boolean coverageRequired,
            boolean takeoverRequired,
            Driver scheduledDriver,
            Driver currentActualDriver) {
    }

    public record ScanResult(
            ScanAction action,
            DriverAssignment assignment,
            boolean coveragePerformed,
            boolean takeoverPerformed,
            Driver scheduledDriver,
            Driver previousActualDriver) {
    }
}
