package technology.renatus.routepulse.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

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
         * Initialize this while the transaction
         * is still open because the portal renders
         * the organization name afterward.
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

        DriverAssignment assignment =
                findActionableAssignment(
                        driver
                );

        ScanAction action =
                assignment.isDriving()
                        ? ScanAction.END
                        : ScanAction.START;

        if (action == ScanAction.START
                && (
                        assignment
                                .getScheduledStartTime()
                                == null
                        || assignment
                                .getScheduledEndTime()
                                == null
                )) {

            throw new IllegalStateException(
                    "A manager must set the scheduled start and end time before this assignment can be started."
            );
        }

        return new ScanPreview(
                driver,
                assignment,
                action
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

        DriverAssignment assignment =
                findActionableAssignment(
                        driver
                );

        UserAccount user =
                requireCurrentUser(
                        authentication
                );

        LocalDateTime now =
                currentOperationsDateTime();

        /*
         * An assignment that has already started
         * but not ended means this scan is STOP.
         */
        if (assignment.isDriving()) {

            assignment.endDriving(
                    now,
                    user
            );

            return new ScanResult(
                    ScanAction.END,
                    assignment
            );
        }

        if (assignment
                .isDrivingCompleted()) {

            throw new IllegalStateException(
                    "This driving assignment has already been completed."
            );
        }

        /*
         * Otherwise this is the first scan,
         * so this scan is START.
         */
        assignment.startDriving(
                now,
                user
        );

        return new ScanResult(
                ScanAction.START,
                assignment
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
                    "Your driver profile is currently inactive. A manager must reactivate it before you can record driving."
            );
        }
    }

    private void validateQrCode(
            Organization organization,
            String qrCode) {

        if (qrCode == null
                || qrCode.isBlank()
                || !organization
                        .getDrivingQrCode()
                        .equals(
                                qrCode.trim()
                        )) {

            throw new IllegalArgumentException(
                    "This QR code is not valid for your organization."
            );
        }
    }

    private DriverAssignment
            findActionableAssignment(
                    Driver driver) {

        List<DriverAssignment> assignments =
                driverAssignmentRepository
                        .findAllByOrganization_IdAndDriver_IdAndAssignmentDate(
                                driver
                                        .getOrganization()
                                        .getId(),
                                driver.getId(),
                                currentOperationsDate()
                        );

        if (assignments.isEmpty()) {

            throw new IllegalStateException(
                    "You do not have a driving assignment for today."
            );
        }

        /*
         * If they are already driving,
         * that assignment is unquestionably
         * the one they are trying to stop.
         */
        List<DriverAssignment> inProgress =
                assignments
                        .stream()
                        .filter(
                                DriverAssignment
                                        ::isDriving
                        )
                        .toList();

        if (inProgress.size() == 1) {

            return inProgress.get(0);
        }

        if (inProgress.size() > 1) {

            throw new IllegalStateException(
                    "More than one driving assignment is currently active. A manager must review today's coverage."
            );
        }

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
                    "All of your driving assignments for today are already complete."
            );
        }

        LocalTime now =
                currentOperationsDateTime()
                        .toLocalTime();

        /*
         * A driver can cover both morning
         * and afternoon. If both are still
         * unstarted, choose whichever scheduled
         * start is closest to the current time.
         */
        return unstarted
                .stream()
                .min(
                        Comparator
                                .comparingLong(
                                        assignment ->
                                                distanceFromScheduledStart(
                                                        now,
                                                        assignment
                                                                .getScheduledStartTime()
                                                )
                                )
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "RoutePulse could not determine which assignment to use."
                        )
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

    public record ScanPreview(
            Driver driver,
            DriverAssignment assignment,
            ScanAction action) {
    }

    public record ScanResult(
            ScanAction action,
            DriverAssignment assignment) {
    }
}