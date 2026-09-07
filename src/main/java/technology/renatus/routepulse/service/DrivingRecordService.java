package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.AssignmentOutcome;
import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.DrivingSession;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.DrivingRecordRepository;
import technology.renatus.routepulse.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;

@Service
@Transactional(readOnly = true)
public class DrivingRecordService {

    private final DrivingRecordRepository drivingRecordRepository;
    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final DriverWorkloadService driverWorkloadService;
    private final ZoneId operationsZone;

    public DrivingRecordService(
            DrivingRecordRepository drivingRecordRepository,
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository membershipRepository,
            DriverWorkloadService driverWorkloadService,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.drivingRecordRepository = drivingRecordRepository;
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.driverWorkloadService = driverWorkloadService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    /**
     * Returns the individual assignment records shown on the
     * "Individual records" tab.
     *
     * A driver-filtered record is included if that driver was either:
     *
     * 1. The scheduled driver
     * 2. An actual driver on a driving session
     *
     * This keeps scheduled work and actual work separate.
     */
    public List<DriverAssignment> getDrivingRecords(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate,
            Long driverId) {

        validateDateRange(
                organizationId,
                fromDate,
                toDate
        );

        validateDriverIfPresent(
                organizationId,
                driverId
        );

        return drivingRecordRepository
                .findAllByOrganization_IdAndAssignmentDateBetween(
                        organizationId,
                        fromDate,
                        toDate
                )
                .stream()
                .filter(assignment ->
                        driverId == null
                                || matchesDriver(
                                        assignment,
                                        driverId
                                )
                )
                .sorted(
                        Comparator
                                .comparing(
                                        DriverAssignment::getAssignmentDate,
                                        Comparator.reverseOrder()
                                )
                                .thenComparingInt(
                                        assignment ->
                                                assignment
                                                        .getPeriod()
                                                        .ordinal()
                                )
                )
                .toList();
    }

    /**
     * Builds the workload report directly from historical records.
     *
     * Scheduled minutes:
     * DriverAssignment.scheduledDriver
     *
     * Actual minutes:
     * DrivingSession.driver
     *
     * We deliberately do NOT persist aggregate totals.
     */
    public WorkloadSummary getWorkloadSummary(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate,
            Long driverId) {

        validateDateRange(
                organizationId,
                fromDate,
                toDate
        );

        validateDriverIfPresent(
                organizationId,
                driverId
        );

        List<Driver> organizationDrivers =
                driverRepository
                        .findAllByOrganization_IdOrderByNameAsc(
                                organizationId
                        );

        Map<Long, MutableDriverWorkload> workloadByDriver =
                new LinkedHashMap<>();

        /*
         * Create one workload bucket for every driver.
         *
         * When a driver filter is active, only create a bucket
         * for that driver.
         */
        for (Driver driver : organizationDrivers) {

            if (driverId == null
                    || Objects.equals(
                            driver.getId(),
                            driverId
                    )) {

                workloadByDriver.put(
                        driver.getId(),
                        new MutableDriverWorkload(driver)
                );
            }
        }

        List<DriverAssignment> assignments =
                drivingRecordRepository
                        .findAllByOrganization_IdAndAssignmentDateBetween(
                                organizationId,
                                fromDate,
                                toDate
                        );

        for (DriverAssignment assignment : assignments) {

            Driver scheduledDriver =
                    assignment.getScheduledDriver();

            Long scheduledDriverId =
                    scheduledDriver.getId();

            MutableDriverWorkload scheduledWorkload =
                    workloadByDriver.get(
                            scheduledDriverId
                    );

            /*
             * Scheduled time always belongs to the
             * scheduled driver.
             */
            if (scheduledWorkload != null) {

                scheduledWorkload.scheduledMinutes +=
                        Math.max(
                                0,
                                assignment.getScheduledMinutes()
                        );

                scheduledWorkload.scheduledShifts++;
            }

            /*
             * Keep track of which actual drivers participated
             * in this assignment.
             *
             * This prevents multiple sessions/scans from counting
             * as multiple "shifts driven" for the same driver.
             */
            Set<Long> actualDriverIdsForAssignment =
                    new LinkedHashSet<>();

            for (DrivingSession session
                    : assignment.getDrivingSessions()) {

                Driver actualDriver =
                        session.getDriver();

                if (actualDriver == null
                        || actualDriver.getId() == null) {
                    continue;
                }

                Long actualDriverId =
                        actualDriver.getId();

                boolean firstSessionForDriverOnAssignment =
                        actualDriverIdsForAssignment.add(
                                actualDriverId
                        );

                MutableDriverWorkload actualWorkload =
                        workloadByDriver.get(
                                actualDriverId
                        );

                if (actualWorkload == null) {
                    continue;
                }

                /*
                 * Actual time only belongs to the person
                 * who actually drove.
                 *
                 * Only completed sessions have final duration.
                 */
                if (session.isCompleted()) {

                    actualWorkload.actualMinutes +=
                            Math.max(
                                    0,
                                    session.getActualMinutes()
                            );
                }

                /*
                 * Count this driver as having driven the
                 * assignment once, regardless of the number
                 * of sessions they created.
                 */
                if (firstSessionForDriverOnAssignment) {

                    actualWorkload.shiftsDriven++;

                    /*
                     * This driver drove an assignment that
                     * was scheduled to somebody else.
                     */
                    if (!Objects.equals(
                            scheduledDriverId,
                            actualDriverId
                    )) {

                        actualWorkload
                                .coveredForAnotherDriver++;
                    }
                }
            }

            /*
             * Determine whether somebody besides the scheduled
             * driver participated in the assignment.
             */
            if (scheduledWorkload != null) {

                boolean anotherDriverParticipated =
                        actualDriverIdsForAssignment
                                .stream()
                                .anyMatch(
                                        actualDriverId ->
                                                !Objects.equals(
                                                        actualDriverId,
                                                        scheduledDriverId
                                                )
                                );

                if (anotherDriverParticipated) {

                    scheduledWorkload
                            .coveredByAnotherDriver++;
                }
            }
        }

        List<DriverWorkloadRow> rows =
                workloadByDriver
                        .values()
                        .stream()
                        .map(
                                MutableDriverWorkload::toRow
                        )
                        .toList();

        long scheduledMinutes =
                rows.stream()
                        .mapToLong(
                                DriverWorkloadRow::getScheduledMinutes
                        )
                        .sum();

        long actualMinutes =
                rows.stream()
                        .mapToLong(
                                DriverWorkloadRow::getActualMinutes
                        )
                        .sum();

        int scheduledShifts =
                rows.stream()
                        .mapToInt(
                                DriverWorkloadRow::getScheduledShifts
                        )
                        .sum();

        int shiftsDriven =
                rows.stream()
                        .mapToInt(
                                DriverWorkloadRow::getShiftsDriven
                        )
                        .sum();

        return new WorkloadSummary(
                fromDate,
                toDate,
                scheduledMinutes,
                actualMinutes,
                scheduledShifts,
                shiftsDriven,
                rows
        );
    }

    public Map<Long, Boolean> getMakeupStatusMap(
            Long organizationId,
            List<DriverAssignment> records) {

        Map<Long, Boolean> result =
                new LinkedHashMap<>();

        for (DriverAssignment assignment : records) {

            result.put(
                    assignment.getId(),
                    driverWorkloadService
                            .isMakeupOutstandingForAssignment(
                                    organizationId,
                                    assignment
                                            .getScheduledDriver()
                                            .getId(),
                                    assignment.getId()
                            )
            );
        }

        return result;
    }

    @Transactional
    public DriverAssignment reviewAssignmentOutcome(
            Long organizationId,
            Long assignmentId,
            AssignmentOutcome outcome,
            boolean requireMakeup,
            String reason,
            String actorEmail) {

        if (outcome == null) {

            throw new IllegalArgumentException(
                    "Assignment outcome is required."
            );
        }

        validateReason(reason);

        if (requireMakeup
                && outcome
                        != AssignmentOutcome.MISSED
                && outcome
                        != AssignmentOutcome.COVERED_BY_OTHER_DRIVER
                && outcome
                        != AssignmentOutcome.PARTIALLY_COVERED) {

            throw new IllegalArgumentException(
                    "Future make-up priority can only be applied to a missed or covered assignment."
            );
        }

        DriverAssignment assignment =
                assignmentRepository
                        .findByIdAndOrganizationForUpdate(
                                assignmentId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Driver assignment was not found."
                                )
                        );

        if (assignment.isDriving()) {

            throw new IllegalStateException(
                    "End the active driving session before reviewing this assignment outcome."
            );
        }

        UserAccount actor =
                loadActor(
                        organizationId,
                        actorEmail
                );

        LocalDateTime now =
                LocalDateTime.now(
                        operationsZone
                );

        assignment.reviewOutcome(
                outcome,
                reason,
                actor,
                now
        );

        driverWorkloadService
                .setMakeupRequired(
                        assignment,
                        requireMakeup,
                        reason,
                        actor,
                        now
                );

        return assignment;
    }

    public LocalDate getCurrentOperationsDate() {

        return LocalDate.now(
                operationsZone
        );
    }

    private boolean matchesDriver(
            DriverAssignment assignment,
            Long driverId) {

        /*
         * Scheduled driver.
         */
        if (Objects.equals(
                assignment
                        .getScheduledDriver()
                        .getId(),
                driverId
        )) {

            return true;
        }

        /*
         * Actual driver.
         */
        return assignment
                .getDrivingSessions()
                .stream()
                .anyMatch(
                        session ->
                                Objects.equals(
                                        session
                                                .getDriver()
                                                .getId(),
                                        driverId
                                )
                );
    }

    private void validateDriverIfPresent(
            Long organizationId,
            Long driverId) {

        if (driverId == null) {
            return;
        }

        driverRepository
                .findByIdAndOrganization_Id(
                        driverId,
                        organizationId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Driver was not found."
                        )
                );
    }

    private void validateDateRange(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "Organization is required."
            );
        }

        if (fromDate == null
                || toDate == null) {

            throw new IllegalArgumentException(
                    "A start date and end date are required."
            );
        }

        if (fromDate.isAfter(toDate)) {

            throw new IllegalArgumentException(
                    "The start date cannot be after the end date."
            );
        }
    }

    private void validateReason(
            String reason) {

        if (reason != null
                && reason.strip().length() > 500) {

            throw new IllegalArgumentException(
                    "Outcome reason cannot exceed 500 characters."
            );
        }
    }

    private UserAccount loadActor(
            Long organizationId,
            String actorEmail) {

        if (actorEmail == null
                || actorEmail.isBlank()) {

            throw new IllegalStateException(
                    "The signed-in user is required for this action."
            );
        }

        UserAccount actor =
                userAccountRepository
                        .findByEmailIgnoreCase(
                                actorEmail.trim()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The signed-in user could not be found."
                                )
                        );

        if (!membershipRepository
                .existsByOrganization_IdAndUser_Id(
                        organizationId,
                        actor.getId()
                )) {

            throw new IllegalStateException(
                    "The signed-in user does not belong to this organization."
            );
        }

        return actor;
    }

    /*
     * Internal mutable object used while calculating the report.
     */
    private static final class MutableDriverWorkload {

        private final Driver driver;

        private long scheduledMinutes;
        private long actualMinutes;

        private int scheduledShifts;
        private int shiftsDriven;

        private int coveredByAnotherDriver;
        private int coveredForAnotherDriver;

        private MutableDriverWorkload(
                Driver driver) {

            this.driver = driver;
        }

        private DriverWorkloadRow toRow() {

            return new DriverWorkloadRow(
                    driver,
                    scheduledMinutes,
                    actualMinutes,
                    scheduledShifts,
                    shiftsDriven,
                    coveredByAnotherDriver,
                    coveredForAnotherDriver
            );
        }
    }

    /*
     * Entire report.
     */
    public static final class WorkloadSummary {

        private final LocalDate fromDate;
        private final LocalDate toDate;

        private final long scheduledMinutes;
        private final long actualMinutes;

        private final int scheduledShifts;
        private final int shiftsDriven;

        private final List<DriverWorkloadRow> drivers;

        public WorkloadSummary(
                LocalDate fromDate,
                LocalDate toDate,
                long scheduledMinutes,
                long actualMinutes,
                int scheduledShifts,
                int shiftsDriven,
                List<DriverWorkloadRow> drivers) {

            this.fromDate = fromDate;
            this.toDate = toDate;
            this.scheduledMinutes = scheduledMinutes;
            this.actualMinutes = actualMinutes;
            this.scheduledShifts = scheduledShifts;
            this.shiftsDriven = shiftsDriven;

            this.drivers =
                    List.copyOf(
                            drivers
                    );
        }

        public static WorkloadSummary empty(
                LocalDate fromDate,
                LocalDate toDate) {

            return new WorkloadSummary(
                    fromDate,
                    toDate,
                    0,
                    0,
                    0,
                    0,
                    new ArrayList<>()
            );
        }

        public LocalDate getFromDate() {
            return fromDate;
        }

        public LocalDate getToDate() {
            return toDate;
        }

        public long getScheduledMinutes() {
            return scheduledMinutes;
        }

        public double getScheduledHours() {

            return scheduledMinutes
                    / 60.0;
        }

        public long getActualMinutes() {
            return actualMinutes;
        }

        public double getActualHours() {

            return actualMinutes
                    / 60.0;
        }

        public long getDifferenceMinutes() {

            return actualMinutes
                    - scheduledMinutes;
        }

        public double getDifferenceHours() {

            return getDifferenceMinutes()
                    / 60.0;
        }

        public int getScheduledShifts() {
            return scheduledShifts;
        }

        public int getShiftsDriven() {
            return shiftsDriven;
        }

        public List<DriverWorkloadRow> getDrivers() {
            return drivers;
        }
    }

    /*
     * One row in the driver workload table.
     */
    public static final class DriverWorkloadRow {

        private final Driver driver;

        private final long scheduledMinutes;
        private final long actualMinutes;

        private final int scheduledShifts;
        private final int shiftsDriven;

        private final int coveredByAnotherDriver;
        private final int coveredForAnotherDriver;

        public DriverWorkloadRow(
                Driver driver,
                long scheduledMinutes,
                long actualMinutes,
                int scheduledShifts,
                int shiftsDriven,
                int coveredByAnotherDriver,
                int coveredForAnotherDriver) {

            this.driver = driver;
            this.scheduledMinutes = scheduledMinutes;
            this.actualMinutes = actualMinutes;
            this.scheduledShifts = scheduledShifts;
            this.shiftsDriven = shiftsDriven;
            this.coveredByAnotherDriver =
                    coveredByAnotherDriver;
            this.coveredForAnotherDriver =
                    coveredForAnotherDriver;
        }

        public Driver getDriver() {
            return driver;
        }

        public long getScheduledMinutes() {
            return scheduledMinutes;
        }

        public double getScheduledHours() {

            return scheduledMinutes
                    / 60.0;
        }

        public long getActualMinutes() {
            return actualMinutes;
        }

        public double getActualHours() {

            return actualMinutes
                    / 60.0;
        }

        public long getDifferenceMinutes() {

            return actualMinutes
                    - scheduledMinutes;
        }

        public double getDifferenceHours() {

            return getDifferenceMinutes()
                    / 60.0;
        }

        public int getScheduledShifts() {
            return scheduledShifts;
        }

        public int getShiftsDriven() {
            return shiftsDriven;
        }

        public int getCoveredByAnotherDriver() {
            return coveredByAnotherDriver;
        }

        public int getCoveredForAnotherDriver() {
            return coveredForAnotherDriver;
        }
    }
}