package technology.renatus.routepulse.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.AssignmentOutcome;
import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.DrivingActionMethod;
import technology.renatus.routepulse.model.DrivingSession;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;

@Service
@Transactional(readOnly = true)
public class DriverPortalService {

    private final CurrentOrganizationService currentOrganizationService;
    private final UserAccountRepository userAccountRepository;
    private final DriverRepository driverRepository;
    private final DriverAssignmentRepository driverAssignmentRepository;
    private final DrivingSessionService drivingSessionService;
    private final ZoneId operationsZone;

    public DriverPortalService(
            CurrentOrganizationService currentOrganizationService,
            UserAccountRepository userAccountRepository,
            DriverRepository driverRepository,
            DriverAssignmentRepository driverAssignmentRepository,
            DrivingSessionService drivingSessionService,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.currentOrganizationService = currentOrganizationService;
        this.userAccountRepository = userAccountRepository;
        this.driverRepository = driverRepository;
        this.driverAssignmentRepository = driverAssignmentRepository;
        this.drivingSessionService = drivingSessionService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public Driver requireCurrentDriver(Authentication authentication) {
        Organization organization = currentOrganizationService
                .requireOrganization(authentication);
        UserAccount user = requireCurrentUser(authentication);

        Driver driver = driverRepository
                .findByOrganization_IdAndUser_Id(
                        organization.getId(),
                        user.getId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "This account is not linked to a driver profile."
                ));

        driver.getOrganization().getDisplayName();
        return driver;
    }

    public List<DriverAssignment> getTodayAssignments(
            Authentication authentication) {

        Driver driver = requireCurrentDriver(authentication);

        return driverAssignmentRepository
                .findAllVisibleToDriverOnDate(
                        driver.getOrganization().getId(),
                        driver.getId(),
                        currentOperationsDate()
                )
                .stream()
                .sorted(Comparator.comparingInt(
                        assignment -> assignment.getPeriod().ordinal()
                ))
                .toList();
    }

    public List<DriverAssignment> getRecentAssignments(
            Authentication authentication) {

        Driver driver = requireCurrentDriver(authentication);

        return driverAssignmentRepository
                .findAllVisibleToDriver(
                        driver.getOrganization().getId(),
                        driver.getId()
                )
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        DriverAssignment::getAssignmentDate,
                                        Comparator.reverseOrder()
                                )
                                .thenComparingInt(
                                        assignment -> assignment
                                                .getPeriod()
                                                .ordinal()
                                )
                )
                .limit(20)
                .toList();
    }

    public ScanPreview getScanPreview(
            Authentication authentication,
            String qrCode) {

        Driver driver = requireCurrentDriver(authentication);
        requireActiveDriver(driver);
        validateQrCode(driver.getOrganization(), qrCode);

        LocalDateTime now = currentOperationsDateTime();
        Long organizationId = driver.getOrganization().getId();

        DrivingSession activeSession = drivingSessionService
                .findActiveSession(organizationId);

        if (activeSession != null) {
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
                        activeSession.getDriver()
                );
            }

            /*
             * A different driver scanning while the shuttle is already running
             * is a real-world handoff. Preview it as a takeover instead of
             * rewriting the scheduled driver or forcing management to repair it.
             */
            return new ScanPreview(
                    driver,
                    assignment,
                    ScanAction.START,
                    !sameDriver(scheduledDriver, driver),
                    true,
                    scheduledDriver,
                    activeSession.getDriver()
            );
        }

        List<DriverAssignment> assignments = driverAssignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(
                        organizationId,
                        now.toLocalDate()
                );

        DriverAssignment assignment = findStartAssignment(
                driver,
                assignments,
                now
        );

        requireScheduledTimes(assignment);

        Driver scheduledDriver = assignment.getScheduledDriver();
        boolean coverageRequired = !sameDriver(scheduledDriver, driver);

        return new ScanPreview(
                driver,
                assignment,
                ScanAction.START,
                coverageRequired,
                false,
                scheduledDriver,
                null
        );
    }

    @Transactional
    public ScanResult recordQrScan(
            Authentication authentication,
            String qrCode) {

        Driver driver = requireCurrentDriver(authentication);
        requireActiveDriver(driver);
        validateQrCode(driver.getOrganization(), qrCode);

        UserAccount user = requireCurrentUser(authentication);
        LocalDateTime now = currentOperationsDateTime();
        Long organizationId = driver.getOrganization().getId();

        /*
         * Lock today's assignments so two simultaneous QR scans cannot both
         * decide that no session is active and start separate shifts.
         */
        List<DriverAssignment> assignments = driverAssignmentRepository
                .findAllForUpdateByOrganizationAndDate(
                        organizationId,
                        now.toLocalDate()
                );

        DrivingSession activeSession = drivingSessionService
                .findActiveSessionForUpdate(organizationId);

        if (activeSession != null) {
            DriverAssignment assignment = activeSession.getAssignment();
            Driver scheduledDriver = assignment.getScheduledDriver();

            if (sameDriver(activeSession.getDriver(), driver)) {
                drivingSessionService.endSession(
                        assignment,
                        activeSession,
                        user,
                        now,
                        DrivingActionMethod.QR
                );

                return new ScanResult(
                        ScanAction.END,
                        assignment,
                        false,
                        false,
                        scheduledDriver,
                        null
                );
            }

            Driver previousActualDriver = activeSession.getDriver();

            drivingSessionService.takeOverActiveSession(
                    activeSession,
                    driver,
                    user,
                    now,
                    DrivingActionMethod.QR
            );

            return new ScanResult(
                    ScanAction.START,
                    assignment,
                    !sameDriver(scheduledDriver, driver),
                    true,
                    scheduledDriver,
                    previousActualDriver
            );
        }

        DriverAssignment assignment = findStartAssignment(
                driver,
                assignments,
                now
        );

        requireScheduledTimes(assignment);

        Driver scheduledDriver = assignment.getScheduledDriver();
        boolean coveragePerformed = !sameDriver(scheduledDriver, driver);

        /*
         * This is the key V26 behavior: a fill-in driver starts a separate
         * DrivingSession. The assignment's scheduled driver is NOT changed.
         */
        drivingSessionService.startSession(
                assignment,
                driver,
                user,
                now,
                DrivingActionMethod.QR
        );

        return new ScanResult(
                ScanAction.START,
                assignment,
                coveragePerformed,
                false,
                scheduledDriver,
                null
        );
    }

    public LocalDate currentOperationsDate() {
        return LocalDate.now(operationsZone);
    }

    private LocalDateTime currentOperationsDateTime() {
        return LocalDateTime.now(operationsZone);
    }

    private UserAccount requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException(
                    "A signed-in driver account is required."
            );
        }

        return userAccountRepository
                .findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "The signed-in account could not be found."
                ));
    }

    private void requireActiveDriver(Driver driver) {
        if (!driver.isActive()) {
            throw new IllegalStateException(
                    "Your driver profile is currently inactive. "
                            + "A manager must reactivate it before you can record driving."
            );
        }
    }

    private void validateQrCode(
            Organization organization,
            String qrCode) {

        String activeQrCode = organization.getDrivingQrCode();

        if (qrCode == null
                || qrCode.isBlank()
                || activeQrCode == null
                || activeQrCode.isBlank()
                || !activeQrCode.equals(qrCode.trim())) {
            throw new IllegalArgumentException(
                    "This QR code is not valid for your organization."
            );
        }
    }

    private void requireScheduledTimes(DriverAssignment assignment) {
        if (assignment.getScheduledStartTime() == null
                || assignment.getScheduledEndTime() == null) {
            throw new IllegalStateException(
                    "A manager must set the scheduled start and end time "
                            + "before this assignment can be started."
            );
        }
    }

    private DriverAssignment findStartAssignment(
            Driver driver,
            List<DriverAssignment> assignments,
            LocalDateTime now) {

        List<DriverAssignment> available = assignments.stream()
                .filter(assignment -> !assignment.isDriving())
                .filter(assignment -> !assignment.isDrivingCompleted())
                .filter(assignment -> !isManagementClosed(assignment))
                .toList();

        if (available.isEmpty()) {
            if (assignments.isEmpty()) {
                throw new IllegalStateException(
                        "There are no driving assignments scheduled for today."
                );
            }

            throw new IllegalStateException(
                    "All driving assignments for today are already complete or closed."
            );
        }

        if (available.size() == 1) {
            return available.get(0);
        }

        return available.stream()
                .filter(assignment -> assignment.getScheduledStartTime() != null
                        && assignment.getScheduledEndTime() != null)
                .min(
                        Comparator
                                .<DriverAssignment>comparingLong(
                                        assignment -> distanceFromScheduledStart(
                                                now.toLocalTime(),
                                                assignment.getScheduledStartTime()
                                        )
                                )
                                .thenComparingInt(
                                        assignment -> sameDriver(
                                                assignment.getScheduledDriver(),
                                                driver
                                        ) ? 0 : 1
                                )
                                .thenComparingInt(
                                        assignment -> assignment
                                                .getPeriod()
                                                .ordinal()
                                )
                )
                .orElseThrow(() -> new IllegalStateException(
                        "RoutePulse cannot determine which shift this scan belongs to "
                                + "because today's remaining assignments do not have "
                                + "scheduled times. A manager must set the scheduled times first."
                ));
    }

    private boolean isManagementClosed(DriverAssignment assignment) {
        return assignment.getAssignmentOutcome() == AssignmentOutcome.MISSED
                || assignment.getAssignmentOutcome() == AssignmentOutcome.EXCUSED
                || assignment.getAssignmentOutcome() == AssignmentOutcome.CANCELLED;
    }

    private boolean sameDriver(Driver first, Driver second) {
        return first != null
                && second != null
                && Objects.equals(first.getId(), second.getId());
    }

    private long distanceFromScheduledStart(
            LocalTime now,
            LocalTime scheduledStartTime) {

        return Math.abs(
                Duration.between(now, scheduledStartTime).toMinutes()
        );
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
