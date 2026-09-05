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

    private final CurrentOrganizationService
            currentOrganizationService;

    private final UserAccountRepository
            userAccountRepository;

    private final DriverRepository
            driverRepository;

    private final DriverAssignmentRepository
            driverAssignmentRepository;

    private final ZoneId operationsZone;

    public DriverPortalService(
            CurrentOrganizationService
                    currentOrganizationService,
            UserAccountRepository
                    userAccountRepository,
            DriverRepository
                    driverRepository,
            DriverAssignmentRepository
                    driverAssignmentRepository,
            @Value(
                    "${app.operations-zone:America/Chicago}"
            )
            String operationsZone) {

        this.currentOrganizationService =
                currentOrganizationService;

        this.userAccountRepository =
                userAccountRepository;

        this.driverRepository =
                driverRepository;

        this.driverAssignmentRepository =
                driverAssignmentRepository;

        this.operationsZone =
                ZoneId.of(
                        operationsZone
                );
    }

    public Driver requireCurrentDriver(
            Authentication authentication) {

        Organization organization =
                currentOrganizationService
                        .requireOrganization(
                                authentication
                        );

        UserAccount user =
                requireCurrentUser(
                        authentication
                );

        Driver driver =
                driverRepository
                        .findByOrganization_IdAndUser_Id(
                                organization.getId(),
                                user.getId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "This account is not linked to a driver profile."
                                )
                        );

        /*
         * Initialize the organization while the transaction
         * is still open because the driver portal displays it.
         */
        driver
                .getOrganization()
                .getDisplayName();

        return driver;
    }

    public List<DriverAssignment>
            getTodayAssignments(
                    Authentication authentication) {

        Driver driver =
                requireCurrentDriver(
                        authentication
                );

        return driverAssignmentRepository
                .findAllByOrganization_IdAndDriver_IdAndAssignmentDate(
                        driver
                                .getOrganization()
                                .getId(),
                        driver.getId(),
                        currentOperationsDate()
                )
                .stream()
                .sorted(
                        Comparator
                                .comparingInt(
                                        assignment ->
                                                assignment
                                                        .getPeriod()
                                                        .ordinal()
                                )
                )
                .toList();
    }

    public List<DriverAssignment>
            getRecentAssignments(
                    Authentication authentication) {

        Driver driver =
                requireCurrentDriver(
                        authentication
                );

        return driverAssignmentRepository
                .findAllByOrganization_IdAndDriver_IdOrderByAssignmentDateDesc(
                        driver
                                .getOrganization()
                                .getId(),
                        driver.getId()
                )
                .stream()
                .sorted(
                        Comparator
                                .comparing(
                                        DriverAssignment
                                                ::getAssignmentDate,
                                        Comparator
                                                .reverseOrder()
                                )
                                .thenComparingInt(
                                        assignment ->
                                                assignment
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

        Driver driver =
                requireCurrentDriver(
                        authentication
                );

        requireActiveDriver(
                driver
        );

        validateQrCode(
                driver.getOrganization(),
                qrCode
        );

        /*
         * We deliberately look at ALL of today's assignments
         * for the organization instead of only assignments
         * already belonging to this driver.
         *
         * This is what allows an unscheduled fill-in driver
         * to scan the shuttle QR and take over an unstarted
         * shift.
         */
        List<DriverAssignment> assignments =
                driverAssignmentRepository
                        .findAllByOrganization_IdAndAssignmentDate(
                                driver
                                        .getOrganization()
                                        .getId(),
                                currentOperationsDate()
                        );

        DriverAssignment assignment =
                findActionableAssignment(
                        driver,
                        assignments
                );

        ScanAction action =
                assignment.isDriving()
                        ? ScanAction.END
                        : ScanAction.START;

        if (action == ScanAction.START) {

            requireScheduledTimes(
                    assignment
            );
        }

        /*
         * Capture the scheduled/current driver before
         * an override actually takes place.
         */
        Driver scheduledDriver =
                assignment.getDriver();

        boolean overrideRequired =
                action == ScanAction.START
                        && !sameDriver(
                                scheduledDriver,
                                driver
                        );

        return new ScanPreview(
                driver,
                assignment,
                action,
                overrideRequired,
                scheduledDriver
        );
    }

    @Transactional
    public ScanResult recordQrScan(
            Authentication authentication,
            String qrCode) {

        Driver driver =
                requireCurrentDriver(
                        authentication
                );

        requireActiveDriver(
                driver
        );

        validateQrCode(
                driver.getOrganization(),
                qrCode
        );

        UserAccount user =
                requireCurrentUser(
                        authentication
                );

        LocalDateTime now =
                currentOperationsDateTime();

        /*
         * Lock today's assignments while processing the scan.
         *
         * This helps prevent two drivers from scanning at nearly
         * the same time and both attempting to take over the same
         * shift.
         */
        List<DriverAssignment> assignments =
                driverAssignmentRepository
                        .findAllForUpdateByOrganizationAndDate(
                                driver
                                        .getOrganization()
                                        .getId(),
                                now.toLocalDate()
                        );

        DriverAssignment assignment =
                findActionableAssignment(
                        driver,
                        assignments
                );

        /*
         * If this signed-in driver already has the active
         * assignment, scanning the QR again means END DRIVING.
         */
        if (assignment.isDriving()) {

            assignment.endDriving(
                    now,
                    user
            );

            return new ScanResult(
                    ScanAction.END,
                    assignment,
                    false,
                    null
            );
        }

        if (assignment
                .isDrivingCompleted()) {

            throw new IllegalStateException(
                    "This driving assignment has already been completed."
            );
        }

        /*
         * Do not create an override unless the assignment
         * is actually ready to start.
         */
        requireScheduledTimes(
                assignment
        );

        Driver previousDriver =
                assignment.getDriver();

        boolean overridePerformed =
                false;

        /*
         * The signed-in driver is different from the person
         * who was scheduled.
         *
         * Because the assignment has not started yet,
         * RoutePulse allows the signed-in driver to take over.
         */
        if (!sameDriver(
                previousDriver,
                driver
        )) {

            overridePerformed =
                    assignment
                            .overrideDriverFromQr(
                                    driver,
                                    QR_OVERRIDE_REASON,
                                    user,
                                    now
                            );
        }

        /*
         * Whether this was the scheduled driver or a fill-in,
         * the actual start is now recorded against the
         * signed-in account.
         */
        assignment.startDriving(
                now,
                user
        );

        return new ScanResult(
                ScanAction.START,
                assignment,
                overridePerformed,
                overridePerformed
                        ? previousDriver
                        : null
        );
    }

    public LocalDate currentOperationsDate() {

        return LocalDate.now(
                operationsZone
        );
    }

    private LocalDateTime
            currentOperationsDateTime() {

        return LocalDateTime.now(
                operationsZone
        );
    }

    private UserAccount requireCurrentUser(
            Authentication authentication) {

        if (authentication == null
                || !authentication
                        .isAuthenticated()) {

            throw new IllegalStateException(
                    "A signed-in driver account is required."
            );
        }

        return userAccountRepository
                .findByEmailIgnoreCase(
                        authentication
                                .getName()
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "The signed-in account could not be found."
                        )
                );
    }

    private void requireActiveDriver(
            Driver driver) {

        if (!driver.isActive()) {

            throw new IllegalStateException(
                    "Your driver profile is currently inactive. "
                            + "A manager must reactivate it before "
                            + "you can record driving."
            );
        }
    }

    private void validateQrCode(
            Organization organization,
            String qrCode) {

        String activeQrCode =
                organization.getDrivingQrCode();

        if (qrCode == null
                || qrCode.isBlank()
                || activeQrCode == null
                || activeQrCode.isBlank()
                || !activeQrCode.equals(
                        qrCode.trim()
                )) {

            throw new IllegalArgumentException(
                    "This QR code is not valid for your organization."
            );
        }
    }

    private void requireScheduledTimes(
            DriverAssignment assignment) {

        if (assignment
                .getScheduledStartTime()
                == null
                || assignment
                        .getScheduledEndTime()
                        == null) {

            throw new IllegalStateException(
                    "A manager must set the scheduled start "
                            + "and end time before this assignment "
                            + "can be started."
            );
        }
    }

    private DriverAssignment
            findActionableAssignment(
                    Driver driver,
                    List<DriverAssignment>
                            assignments) {

        if (assignments.isEmpty()) {

            throw new IllegalStateException(
                    "There are no driving assignments scheduled for today."
            );
        }

        /*
         * First determine whether somebody is already driving.
         */
        List<DriverAssignment> inProgress =
                assignments
                        .stream()
                        .filter(
                                DriverAssignment
                                        ::isDriving
                        )
                        .toList();

        /*
         * RoutePulse should not have multiple simultaneous
         * active shuttle-driving assignments.
         */
        if (inProgress.size() > 1) {

            throw new IllegalStateException(
                    "More than one driving assignment is currently "
                            + "active. A manager must review today's coverage."
            );
        }

        /*
         * Exactly one assignment is active.
         */
        if (inProgress.size() == 1) {

            DriverAssignment activeAssignment =
                    inProgress.get(0);

            /*
             * If the active assignment belongs to the signed-in
             * driver, this scan will stop their driving session.
             */
            if (sameDriver(
                    activeAssignment
                            .getDriver(),
                    driver
            )) {

                return activeAssignment;
            }

            /*
             * Never automatically replace somebody who has
             * already started driving.
             */
            throw new IllegalStateException(
                    "Another driver has already started driving. "
                            + "RoutePulse will not automatically replace "
                            + "an active driver. A manager must resolve "
                            + "the active assignment first."
            );
        }

        /*
         * Nothing is currently active.
         *
         * Find assignments that have never been started.
         */
        List<DriverAssignment> unstarted =
                assignments
                        .stream()
                        .filter(assignment ->
                                assignment
                                        .getActualStartedAt()
                                        == null
                                && assignment
                                        .getActualEndedAt()
                                        == null
                        )
                        .toList();

        if (unstarted.isEmpty()) {

            throw new IllegalStateException(
                    "All driving assignments for today are already complete."
            );
        }

        /*
         * If only one shift is left, that is clearly
         * the shift the driver is scanning for.
         *
         * This is also what allows a driver who was not
         * originally scheduled to fill in.
         */
        if (unstarted.size() == 1) {

            return unstarted.get(0);
        }

        LocalTime now =
                currentOperationsDateTime()
                        .toLocalTime();

        /*
         * If morning and afternoon are both still available,
         * use their scheduled times to determine which shift
         * the scan most likely belongs to.
         */
        List<DriverAssignment> scheduledCandidates =
                unstarted
                        .stream()
                        .filter(assignment ->
                                assignment
                                        .getScheduledStartTime()
                                        != null
                                && assignment
                                        .getScheduledEndTime()
                                        != null
                        )
                        .toList();

        if (scheduledCandidates.isEmpty()) {

            throw new IllegalStateException(
                    "RoutePulse cannot determine which shift this scan "
                            + "belongs to because today's remaining "
                            + "assignments do not have scheduled times. "
                            + "A manager must set the scheduled times first."
            );
        }

        /*
         * Choose whichever scheduled shift begins closest
         * to the current time.
         *
         * If two shifts happen to be equally close,
         * prefer the one already assigned to the
         * signed-in driver.
         */
        return scheduledCandidates
                .stream()
                .min(
                        Comparator
                                .<DriverAssignment>comparingLong(
                                        assignment ->
                                                distanceFromScheduledStart(
                                                        now,
                                                        assignment
                                                                .getScheduledStartTime()
                                                )
                                )
                                .thenComparingInt(
                                        assignment ->
                                                sameDriver(
                                                        assignment
                                                                .getDriver(),
                                                        driver
                                                )
                                                        ? 0
                                                        : 1
                                )
                                .thenComparingInt(
                                        assignment ->
                                                assignment
                                                        .getPeriod()
                                                        .ordinal()
                                )
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "RoutePulse could not determine "
                                        + "which assignment to use."
                        )
                );
    }

    private boolean sameDriver(
            Driver first,
            Driver second) {

        if (first == null
                || second == null) {

            return false;
        }

        return Objects.equals(
                first.getId(),
                second.getId()
        );
    }

    private long distanceFromScheduledStart(
            LocalTime now,
            LocalTime scheduledStartTime) {

        if (scheduledStartTime == null) {

            return Long.MAX_VALUE;
        }

        return Math.abs(
                Duration.between(
                        now,
                        scheduledStartTime
                ).toMinutes()
        );
    }

    public enum ScanAction {
        START,
        END
    }

    /*
     * Used by DriverPortalController when the driver
     * first opens the scanned QR URL.
     */
    public record ScanPreview(
            Driver driver,
            DriverAssignment assignment,
            ScanAction action,
            boolean overrideRequired,
            Driver scheduledDriver) {
    }

    /*
     * Used by DriverPortalController after the driver
     * confirms the scan.
     */
    public record ScanResult(
            ScanAction action,
            DriverAssignment assignment,
            boolean overridePerformed,
            Driver previousDriver) {
    }
}