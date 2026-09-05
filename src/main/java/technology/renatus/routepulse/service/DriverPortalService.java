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

import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;

@Service
@Transactional(readOnly = true)
public class DriverPortalService {

    private static final String QR_OVERRIDE_REASON =
            "Driver self-override recorded from shuttle QR scan.";

    private final CurrentOrganizationService currentOrganizationService;
    private final UserAccountRepository userAccountRepository;
    private final DriverRepository driverRepository;
    private final DriverAssignmentRepository driverAssignmentRepository;
    private final ZoneId operationsZone;

    public DriverPortalService(
            CurrentOrganizationService currentOrganizationService,
            UserAccountRepository userAccountRepository,
            DriverRepository driverRepository,
            DriverAssignmentRepository driverAssignmentRepository,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.currentOrganizationService = currentOrganizationService;
        this.userAccountRepository = userAccountRepository;
        this.driverRepository = driverRepository;
        this.driverAssignmentRepository = driverAssignmentRepository;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public Driver requireCurrentDriver(Authentication authentication) {
        Organization organization = currentOrganizationService
                .requireOrganization(authentication);
        UserAccount user = requireCurrentUser(authentication);

        Driver driver = driverRepository
                .findByOrganization_IdAndUser_Id(organization.getId(), user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "This account is not linked to a driver profile."));

        driver.getOrganization().getDisplayName();
        return driver;
    }

    public List<DriverAssignment> getTodayAssignments(Authentication authentication) {
        Driver driver = requireCurrentDriver(authentication);

        return driverAssignmentRepository
                .findAllByOrganization_IdAndDriver_IdAndAssignmentDate(
                        driver.getOrganization().getId(),
                        driver.getId(), currentOperationsDate())
                .stream()
                .sorted(Comparator.comparingInt(a -> a.getPeriod().ordinal()))
                .toList();
    }

    public List<DriverAssignment> getRecentAssignments(Authentication authentication) {
        Driver driver = requireCurrentDriver(authentication);

        return driverAssignmentRepository
                .findAllByOrganization_IdAndDriver_IdOrderByAssignmentDateDesc(
                        driver.getOrganization().getId(), driver.getId())
                .stream()
                .sorted(Comparator.comparing(
                        DriverAssignment::getAssignmentDate,
                        Comparator.reverseOrder())
                        .thenComparingInt(a -> a.getPeriod().ordinal()))
                .limit(20)
                .toList();
    }

    public ScanPreview getScanPreview(
            Authentication authentication, String qrCode) {

        Driver driver = requireCurrentDriver(authentication);
        requireActiveDriver(driver);
        validateQrCode(driver.getOrganization(), qrCode);

        LocalDateTime now = currentOperationsDateTime();

        // Include unfinished driving from previous dates, even after midnight.
        List<DriverAssignment> assignments = driverAssignmentRepository
                .findTodayOrActiveByOrganization(
                        driver.getOrganization().getId(), now.toLocalDate());

        DriverAssignment assignment = findActionableAssignment(driver, assignments, now);
        ScanAction action = assignment.isDriving() ? ScanAction.END : ScanAction.START;

        if (action == ScanAction.START) {
            requireScheduledTimes(assignment);
        }

        Driver scheduledDriver = assignment.getDriver();
        boolean overrideRequired = action == ScanAction.START
                && !sameDriver(scheduledDriver, driver);

        return new ScanPreview(
                driver, assignment, action, overrideRequired, scheduledDriver);
    }

    @Transactional
    public ScanResult recordQrScan(
            Authentication authentication, String qrCode) {

        Driver driver = requireCurrentDriver(authentication);
        requireActiveDriver(driver);
        validateQrCode(driver.getOrganization(), qrCode);
        UserAccount user = requireCurrentUser(authentication);
        LocalDateTime now = currentOperationsDateTime();

        // Lock today's assignments and any unfinished earlier driving.
        List<DriverAssignment> assignments = driverAssignmentRepository
                .findTodayOrActiveForUpdateByOrganization(
                        driver.getOrganization().getId(), now.toLocalDate());

        DriverAssignment assignment = findActionableAssignment(driver, assignments, now);

        if (assignment.isDriving()) {
            assignment.endDriving(now, user);
            return new ScanResult(ScanAction.END, assignment, false, null);
        }

        requireScheduledTimes(assignment);
        Driver previousDriver = assignment.getDriver();
        boolean overridePerformed = false;

        if (!sameDriver(previousDriver, driver)) {
            overridePerformed = assignment.overrideDriverFromQr(
                    driver, QR_OVERRIDE_REASON, user, now);
        }

        // Record the real confirmation time, even before or after scheduled hours.
        // Scheduled start/end times remain unchanged for comparison in records.
        assignment.startDriving(now, user);

        return new ScanResult(
                ScanAction.START, assignment, overridePerformed,
                overridePerformed ? previousDriver : null);
    }

    public LocalDate currentOperationsDate() {
        return LocalDate.now(operationsZone);
    }

    private LocalDateTime currentOperationsDateTime() {
        return LocalDateTime.now(operationsZone);
    }

    private UserAccount requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("A signed-in driver account is required.");
        }

        return userAccountRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "The signed-in account could not be found."));
    }

    private void requireActiveDriver(Driver driver) {
        if (!driver.isActive()) {
            throw new IllegalStateException(
                    "Your driver profile is currently inactive. "
                            + "A manager must reactivate it before you can record driving.");
        }
    }

    private void validateQrCode(Organization organization, String qrCode) {
        String activeQrCode = organization.getDrivingQrCode();
        if (qrCode == null || qrCode.isBlank()
                || activeQrCode == null || activeQrCode.isBlank()
                || !activeQrCode.equals(qrCode.trim())) {
            throw new IllegalArgumentException(
                    "This QR code is not valid for your organization.");
        }
    }

    private void requireScheduledTimes(DriverAssignment assignment) {
        if (assignment.getScheduledStartTime() == null
                || assignment.getScheduledEndTime() == null) {
            throw new IllegalStateException(
                    "A manager must set the scheduled start and end time "
                            + "before this assignment can be started.");
        }
    }

    private DriverAssignment findActionableAssignment(
            Driver driver, List<DriverAssignment> assignments, LocalDateTime now) {

        List<DriverAssignment> inProgress = assignments.stream()
                .filter(DriverAssignment::isDriving)
                .toList();

        if (inProgress.size() > 1) {
            throw new IllegalStateException(
                    "More than one driving assignment is currently active. "
                            + "A manager must review the active driving records.");
        }

        // An active session takes priority over today's schedule.
        if (inProgress.size() == 1) {
            DriverAssignment activeAssignment = inProgress.get(0);
            if (sameDriver(activeAssignment.getDriver(), driver)) {
                return activeAssignment;
            }
            throw new IllegalStateException(
                    "Another driver has already started driving. "
                            + "RoutePulse will not automatically replace an active driver. "
                            + "A manager must resolve the active assignment first.");
        }

        List<DriverAssignment> todayAssignments = assignments.stream()
                .filter(a -> now.toLocalDate().equals(a.getAssignmentDate()))
                .toList();

        if (todayAssignments.isEmpty()) {
            throw new IllegalStateException(
                    "There are no driving assignments scheduled for today.");
        }

        List<DriverAssignment> unstarted = todayAssignments.stream()
                .filter(a -> a.getActualStartedAt() == null && a.getActualEndedAt() == null)
                .toList();

        if (unstarted.isEmpty()) {
            throw new IllegalStateException(
                    "All driving assignments for today are already complete.");
        }

        if (unstarted.size() == 1) {
            return unstarted.get(0);
        }

        // Scheduled times choose the shift; they do not restrict when it can start.
        return unstarted.stream()
                .filter(a -> a.getScheduledStartTime() != null
                        && a.getScheduledEndTime() != null)
                .min(Comparator.<DriverAssignment>comparingLong(
                        a -> distanceFromScheduledStart(
                                now.toLocalTime(), a.getScheduledStartTime()))
                        .thenComparingInt(a -> sameDriver(a.getDriver(), driver) ? 0 : 1)
                        .thenComparingInt(a -> a.getPeriod().ordinal()))
                .orElseThrow(() -> new IllegalStateException(
                        "RoutePulse cannot determine which shift this scan belongs to "
                                + "because today's remaining assignments do not have "
                                + "scheduled times. A manager must set the scheduled times first."));
    }

    private boolean sameDriver(Driver first, Driver second) {
        return first != null && second != null
                && Objects.equals(first.getId(), second.getId());
    }

    private long distanceFromScheduledStart(LocalTime now, LocalTime scheduledStartTime) {
        return Math.abs(Duration.between(now, scheduledStartTime).toMinutes());
    }

    public enum ScanAction {
        START,
        END
    }

    public record ScanPreview(
            Driver driver,
            DriverAssignment assignment,
            ScanAction action,
            boolean overrideRequired,
            Driver scheduledDriver) {
    }

    public record ScanResult(
            ScanAction action,
            DriverAssignment assignment,
            boolean overridePerformed,
            Driver previousDriver) {
    }
}