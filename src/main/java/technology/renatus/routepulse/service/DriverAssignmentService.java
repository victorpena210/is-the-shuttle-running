package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.AssignmentSource;
import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.DriverSchedule;
import technology.renatus.routepulse.model.DriverScheduleResult;
import technology.renatus.routepulse.model.DrivingActionMethod;
import technology.renatus.routepulse.model.MorningDrivingPlan;
import technology.renatus.routepulse.model.ScheduleImport;
import technology.renatus.routepulse.model.ShuttlePeriod;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.repository.UploadedShiftRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;

@Service
public class DriverAssignmentService {

    private final DriverAssignmentRepository
            assignmentRepository;

    private final DriverRepository driverRepository;

    private final DriverScheduleService
            driverScheduleService;

    private final UploadedShiftRepository
            uploadedShiftRepository;

    private final UserAccountRepository
            userAccountRepository;

    private final OrganizationMembershipRepository
            organizationMembershipRepository;

    private final DriverWorkloadService
            driverWorkloadService;

    private final DrivingSessionService
            drivingSessionService;

    private final MorningDrivingPlanningService
            morningDrivingPlanningService;

    private final ZoneId operationsZone;

    public DriverAssignmentService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            DriverScheduleService driverScheduleService,
            UploadedShiftRepository uploadedShiftRepository,
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository
                    organizationMembershipRepository,
            DriverWorkloadService driverWorkloadService,
            DrivingSessionService drivingSessionService,
            MorningDrivingPlanningService morningDrivingPlanningService,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.assignmentRepository =
                assignmentRepository;

        this.driverRepository =
                driverRepository;

        this.driverScheduleService =
                driverScheduleService;

        this.uploadedShiftRepository =
                uploadedShiftRepository;

        this.userAccountRepository =
                userAccountRepository;

        this.organizationMembershipRepository =
                organizationMembershipRepository;

        this.driverWorkloadService =
                driverWorkloadService;

        this.drivingSessionService =
                drivingSessionService;

        this.morningDrivingPlanningService =
                morningDrivingPlanningService;

        this.operationsZone =
                ZoneId.of(
                        operationsZone
                );
    }

    @Transactional
    public List<DriverAssignment> getOrCreateAssignments(
            Long organizationId,
            LocalDate assignmentDate) {

        List<DriverAssignment> existingAssignments =
                assignmentRepository
                        .findAllByOrganization_IdAndAssignmentDate(
                                organizationId,
                                assignmentDate
                        );

        /*
         * Existing assignments are normally returned unchanged.
         *
         * The one exception is an older MORNING automatic assignment
         * that has no scheduled times yet.
         *
         * That allows assignments created before this feature was
         * introduced to receive a generated morning driving window
         * without deleting data or recreating the assignment.
         *
         * We never overwrite:
         *
         * - manager-entered times
         * - an assignment that already has times
         * - an assignment where actual driving has started
         */
        if (!existingAssignments.isEmpty()) {

            applyMorningScheduleIfMissing(
                    organizationId,
                    assignmentDate,
                    existingAssignments
            );

            return orderByPeriod(
                    existingAssignments
            );
        }

        /*
         * Determine which registered RoutePulse drivers are actually
         * scheduled to work on this date.
         */
        DriverScheduleResult scheduleResult =
                driverScheduleService
                        .getDriverSchedulesForDate(
                                organizationId,
                                assignmentDate
                        );

        /*
         * Keep every uploaded shift row for each driver. A driver may have a
         * split shift, and the exact work window matters when we clip an
         * automatically generated driving assignment to their availability.
         */
        Map<Long, List<DriverSchedule>> schedulesByDriverId =
                scheduleResult
                        .scheduledDrivers()
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        DriverSchedule::driverId
                                )
                        );

        /*
         * Fairness only needs one representative row per driver. Use the
         * earliest scheduled row for stable ordering while retaining all rows
         * above for actual availability calculations.
         */
        List<DriverSchedule> eligibleDrivers =
                schedulesByDriverId
                        .values()
                        .stream()
                        .map(this::earliestSchedule)
                        .toList();

        /*
         * If nobody registered as a driver is scheduled today,
         * RoutePulse leaves coverage unassigned so a manager can
         * handle it manually.
         */
        if (eligibleDrivers.isEmpty()) {
            return List.of();
        }

        /*
         * Driver fairness decides WHO should drive.
         *
         * MorningDrivingPlanningService separately decides WHEN
         * the morning shuttle should operate.
         */
        List<DriverSchedule> fairestOrder =
                driverWorkloadService
                        .rankEligibleDrivers(
                                organizationId,
                                assignmentDate,
                                eligibleDrivers
                        );

        MorningDrivingPlan morningPlan =
                morningDrivingPlanningService
                        .getPlanForDate(
                                organizationId,
                                assignmentDate
                        );

        /*
         * Fairness still decides who is next in line, but a driver must have
         * at least some actual work-schedule overlap with the morning demand
         * window. A 9:00 AM clock-in is allowed for an 8:45 AM demand start;
         * the assignment will simply begin at 9:00 AM. A driver whose shift
         * is entirely outside the demand window is skipped.
         */
        DriverSchedule morningDriverSchedule =
                chooseMorningDriverSchedule(
                        fairestOrder,
                        schedulesByDriverId,
                        morningPlan
                );

        Driver morningDriver =
                morningDriverSchedule == null
                        ? null
                        : loadDriver(
                                organizationId,
                                morningDriverSchedule.driverId()
                        );

        /*
         * Preserve which uploaded schedule version produced
         * this automatic assignment.
         */
        ScheduleImport sourceScheduleImport =
                uploadedShiftRepository
                        .findLatestScheduleImportForDate(
                                organizationId,
                                assignmentDate
                        )
                        .orElse(null);

        List<DriverAssignment> newAssignments =
                new ArrayList<>();

        /*
         * =============================================================
         * MORNING ASSIGNMENT
         * =============================================================
         *
         * Fairness selects the person.
         *
         * Morning clock-in demand calculates the scheduled
         * driving start/end time.
         */
        DriverAssignment morningAssignment = null;

        if (morningDriver != null) {
            morningAssignment =
                    DriverAssignment.automatic(
                            assignmentDate,
                            ShuttlePeriod.MORNING,
                            morningDriver,
                            sourceScheduleImport
                    );

            applyMorningScheduleIfAvailable(
                    morningPlan,
                    morningDriverSchedule,
                    morningAssignment
            );

            newAssignments.add(
                    morningAssignment
            );
        }

        /*
         * =============================================================
         * AFTERNOON ASSIGNMENT
         * =============================================================
         *
         * Existing afternoon behavior is intentionally preserved.
         *
         * This feature does not change your pickup-demand calculations
         * or automatically alter manager-approved afternoon scheduling.
         */
        DriverSchedule afternoonDriverSchedule =
                chooseAfternoonDriverSchedule(
                        fairestOrder,
                        morningDriverSchedule
                );

        if (afternoonDriverSchedule != null) {

            Driver afternoonDriver =
                    loadDriver(
                            organizationId,
                            afternoonDriverSchedule.driverId()
                    );

            newAssignments.add(
                    DriverAssignment.automatic(
                            assignmentDate,
                            ShuttlePeriod.AFTERNOON,
                            afternoonDriver,
                            sourceScheduleImport
                    )
            );
        }

        return orderByPeriod(
                assignmentRepository.saveAll(
                        newAssignments
                )
        );
    }

    /**
     * Fills an older morning assignment only when both generated schedule
     * fields are still blank.
     *
     * The generated demand window is clipped to the scheduled driver's actual
     * uploaded work shift before it is saved.
     */
    private void applyMorningScheduleIfMissing(
            Long organizationId,
            LocalDate assignmentDate,
            List<DriverAssignment> assignments) {

        DriverAssignment morningAssignment =
                assignments
                        .stream()
                        .filter(assignment ->
                                assignment.getPeriod()
                                        == ShuttlePeriod.MORNING
                        )
                        .findFirst()
                        .orElse(null);

        /*
         * Do not overwrite an existing schedule and do not change an
         * assignment after actual driving has started.
         */
        if (morningAssignment == null
                || morningAssignment
                        .getScheduledStartTime() != null
                || morningAssignment
                        .getScheduledEndTime() != null
                || morningAssignment
                        .hasDrivingSessions()) {

            return;
        }

        MorningDrivingPlan morningPlan =
                morningDrivingPlanningService
                        .getPlanForDate(
                                organizationId,
                                assignmentDate
                        );

        if (!morningPlan.hasDemand()) {
            return;
        }

        DriverScheduleResult scheduleResult =
                driverScheduleService
                        .getDriverSchedulesForDate(
                                organizationId,
                                assignmentDate
                        );

        DriverSchedule assignedDriverSchedule =
                findBestOverlappingSchedule(
                        scheduleResult.scheduledDrivers(),
                        morningAssignment
                                .getScheduledDriver()
                                .getId(),
                        morningPlan.getScheduledStartTime(),
                        morningPlan.getScheduledEndTime()
                );

        if (assignedDriverSchedule == null) {
            return;
        }

        applyMorningScheduleIfAvailable(
                morningPlan,
                assignedDriverSchedule,
                morningAssignment
        );
    }

    /**
     * Applies the morning demand window after constraining it to the assigned
     * driver's real uploaded work hours.
     *
     * Example:
     * demand window = 8:45 AM - 12:15 PM
     * driver shift  = 9:00 AM - 4:00 PM
     * assignment    = 9:00 AM - 12:15 PM
     */
    private void applyMorningScheduleIfAvailable(
            MorningDrivingPlan morningPlan,
            DriverSchedule driverSchedule,
            DriverAssignment morningAssignment) {

        if (morningPlan == null
                || !morningPlan.hasDemand()
                || driverSchedule == null
                || !driverSchedule.overlaps(
                        morningPlan.getScheduledStartTime(),
                        morningPlan.getScheduledEndTime()
                )) {
            return;
        }

        DriverSchedule.DrivingWindow clippedWindow =
                driverSchedule.clipTo(
                        morningPlan.getScheduledStartTime(),
                        morningPlan.getScheduledEndTime()
                );

        if (clippedWindow == null) {
            return;
        }

        morningAssignment.updateScheduledTimes(
                clippedWindow.startTime(),
                clippedWindow.endTime()
        );
    }

    /**
     * Walks the fairness queue in order and returns the first driver who is
     * actually working during at least part of the morning demand window.
     *
     * We intentionally do not require the driver to cover the entire demand
     * window. If demand begins at 8:45 AM and the fairest eligible driver
     * clocks in at 9:00 AM, that driver remains eligible and their assignment
     * begins at 9:00 AM.
     */
    private DriverSchedule chooseMorningDriverSchedule(
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            MorningDrivingPlan morningPlan) {

        if (fairestOrder == null || fairestOrder.isEmpty()) {
            return null;
        }

        /*
         * Preserve the previous behavior when there is no generated morning
         * demand window: keep the fairest scheduled driver attached to the
         * morning assignment and leave its times blank for manager review.
         */
        if (morningPlan == null || !morningPlan.hasDemand()) {
            return fairestOrder.get(0);
        }

        for (DriverSchedule rankedDriver : fairestOrder) {

            DriverSchedule overlappingSchedule =
                    findBestOverlappingSchedule(
                            schedulesByDriverId.getOrDefault(
                                    rankedDriver.driverId(),
                                    List.of()
                            ),
                            rankedDriver.driverId(),
                            morningPlan.getScheduledStartTime(),
                            morningPlan.getScheduledEndTime()
                    );

            if (overlappingSchedule != null) {
                return overlappingSchedule;
            }
        }

        return null;
    }

    /**
     * Keeps the existing two-driver AM/PM rotation behavior. If morning had to
     * skip the first fairness-ranked driver because that person's work shift
     * did not overlap morning demand, that first-ranked driver becomes the
     * natural afternoon choice instead of assigning the same person twice.
     */
    private DriverSchedule chooseAfternoonDriverSchedule(
            List<DriverSchedule> fairestOrder,
            DriverSchedule morningDriverSchedule) {

        if (fairestOrder == null || fairestOrder.size() < 2) {
            return null;
        }

        if (morningDriverSchedule == null) {
            return fairestOrder.get(1);
        }

        return fairestOrder
                .stream()
                .filter(schedule ->
                        !schedule.driverId().equals(
                                morningDriverSchedule.driverId()
                        )
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the shift row for a driver that covers the greatest portion of the
     * requested driving window. This also handles split-shift uploads without
     * accidentally discarding later rows for the same driver.
     */
    private DriverSchedule findBestOverlappingSchedule(
            List<DriverSchedule> schedules,
            Long driverId,
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        if (schedules == null || schedules.isEmpty() || driverId == null) {
            return null;
        }

        return schedules
                .stream()
                .filter(schedule ->
                        driverId.equals(schedule.driverId())
                                && schedule.overlaps(
                                        requestedStart,
                                        requestedEnd
                                )
                )
                .sorted(
                        Comparator
                                .comparingLong(
                                        (DriverSchedule schedule) ->
                                                schedule.overlapMinutes(
                                                        requestedStart,
                                                        requestedEnd
                                                )
                                )
                                .reversed()
                                .thenComparing(
                                        DriverSchedule::startTime
                                )
                )
                .findFirst()
                .orElse(null);
    }

    private DriverSchedule earliestSchedule(
            List<DriverSchedule> schedules) {

        return schedules
                .stream()
                .min(
                        Comparator
                                .comparing(
                                        DriverSchedule::startTime
                                )
                                .thenComparing(
                                        DriverSchedule::endTime
                                )
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "A scheduled driver must have at least one shift row."
                        )
                );
    }

    @Transactional
    public DriverAssignment createManualAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            ShuttlePeriod period,
            Long driverId,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            String reason) {

        validateReason(
                reason
        );

        if (assignmentDate == null) {
            throw new IllegalArgumentException(
                    "Assignment date is required."
            );
        }

        if (period == null) {
            throw new IllegalArgumentException(
                    "Shuttle period is required."
            );
        }

        validateScheduledTimes(
                scheduledStartTime,
                scheduledEndTime
        );

        if (assignmentRepository
                .findByOrganization_IdAndAssignmentDateAndPeriod(
                        organizationId,
                        assignmentDate,
                        period
                )
                .isPresent()) {

            throw new IllegalArgumentException(
                    "That shuttle period already has an assignment."
            );
        }

        Driver driver =
                loadActiveDriver(
                        organizationId,
                        driverId
                );

        DriverAssignment assignment =
                DriverAssignment.manual(
                        assignmentDate,
                        period,
                        driver,
                        reason
                );

        assignment.updateScheduledTimes(
                scheduledStartTime,
                scheduledEndTime
        );

        return assignmentRepository.save(
                assignment
        );
    }

    @Transactional
    public DriverAssignment changeAssignedDriver(
            Long organizationId,
            Long assignmentId,
            Long newDriverId,
            String reason,
            String actorEmail) {

        validateReason(
                reason
        );

        DriverAssignment assignment =
                loadAssignment(
                        organizationId,
                        assignmentId
                );

        Driver newDriver =
                loadActiveDriver(
                        organizationId,
                        newDriverId
                );

        UserAccount actor =
                loadActor(
                        organizationId,
                        actorEmail
                );

        boolean changed =
                assignment.changeDriver(
                        newDriver,
                        reason,
                        actor
                );

        if (!changed) {
            throw new IllegalArgumentException(
                    "Choose a different driver before saving."
            );
        }

        return assignment;
    }

    @Transactional
    public DriverAssignment updateScheduledTimes(
            Long organizationId,
            Long assignmentId,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime) {

        validateScheduledTimes(
                scheduledStartTime,
                scheduledEndTime
        );

        DriverAssignment assignment =
                loadAssignment(
                        organizationId,
                        assignmentId
                );

        assignment.updateScheduledTimes(
                scheduledStartTime,
                scheduledEndTime
        );

        return assignment;
    }

    @Transactional
    public void confirmAssignmentsAgainstLatestImport(
            Long organizationId,
            LocalDate assignmentDate,
            String actorEmail) {

        ScheduleImport latestImport =
                uploadedShiftRepository
                        .findLatestScheduleImportForDate(
                                organizationId,
                                assignmentDate
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No current schedule import was found for this date."
                                )
                        );

        UserAccount actor =
                loadActor(
                        organizationId,
                        actorEmail
                );

        LocalDateTime reviewedAt =
                currentOperationsDateTime();

        List<DriverAssignment> assignments =
                assignmentRepository
                        .findAllByOrganization_IdAndAssignmentDate(
                                organizationId,
                                assignmentDate
                        );

        if (assignments.isEmpty()) {

            throw new IllegalStateException(
                    "No driver assignments were found for this date."
            );
        }

        assignments
                .stream()
                .filter(assignment ->
                        assignment
                                .getAssignmentSource()
                                != AssignmentSource.MANUAL
                )
                .forEach(assignment ->
                        assignment
                                .confirmScheduleReview(
                                        latestImport,
                                        actor,
                                        reviewedAt
                                )
                );
    }

    @Transactional
    public DriverAssignment startDriving(
            Long organizationId,
            Long assignmentId,
            String actorEmail) {

        DriverAssignment assignment =
                loadAssignment(
                        organizationId,
                        assignmentId
                );

        LocalDateTime now =
                currentOperationsDateTime();

        if (!assignment
                .getAssignmentDate()
                .equals(
                        now.toLocalDate()
                )) {

            throw new IllegalStateException(
                    "Driving can only be started on the assignment date."
            );
        }

        drivingSessionService
                .startSession(
                        organizationId,
                        assignmentId,
                        assignment
                                .getScheduledDriver(),
                        loadActor(
                                organizationId,
                                actorEmail
                        ),
                        now,
                        DrivingActionMethod.MANAGER
                );

        return assignment;
    }

    @Transactional
    public DriverAssignment endDriving(
            Long organizationId,
            Long assignmentId,
            String actorEmail) {

        DriverAssignment assignment =
                loadAssignment(
                        organizationId,
                        assignmentId
                );

        drivingSessionService
                .endActiveSessionForAssignment(
                        organizationId,
                        assignmentId,
                        loadActor(
                                organizationId,
                                actorEmail
                        ),
                        currentOperationsDateTime(),
                        DrivingActionMethod.MANAGER
                );

        return assignment;
    }

    public LocalDate getCurrentOperationsDate() {

        return LocalDate.now(
                operationsZone
        );
    }

    private LocalDateTime currentOperationsDateTime() {

        return LocalDateTime.now(
                operationsZone
        );
    }

    private void validateScheduledTimes(
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime) {

        if (scheduledStartTime == null
                || scheduledEndTime == null) {

            throw new IllegalArgumentException(
                    "Scheduled start and end time are required."
            );
        }

        if (!scheduledEndTime
                .isAfter(
                        scheduledStartTime
                )) {

            throw new IllegalArgumentException(
                    "Scheduled end time must be after "
                            + "the start time."
            );
        }
    }

    private void validateReason(
            String reason) {

        if (reason != null
                && reason
                        .strip()
                        .length() > 500) {

            throw new IllegalArgumentException(
                    "Change reason cannot exceed 500 characters."
            );
        }
    }

    private DriverAssignment loadAssignment(
            Long organizationId,
            Long assignmentId) {

        return assignmentRepository
                .findByIdAndOrganization_Id(
                        assignmentId,
                        organizationId
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Driver assignment was not found."
                        )
                );
    }

    private Driver loadDriver(
            Long organizationId,
            Long driverId) {

        return driverRepository
                .findByIdAndOrganization_Id(
                        driverId,
                        organizationId
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Eligible driver was not found."
                        )
                );
    }

    private Driver loadActiveDriver(
            Long organizationId,
            Long driverId) {

        Driver driver =
                loadDriver(
                        organizationId,
                        driverId
                );

        if (!driver.isActive()) {

            throw new IllegalArgumentException(
                    "The selected driver is inactive."
            );
        }

        return driver;
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

        if (!organizationMembershipRepository
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

    private List<DriverAssignment> orderByPeriod(
            List<DriverAssignment> assignments) {

        return assignments
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                assignment ->
                                        assignment
                                                .getPeriod()
                                                .ordinal()
                        )
                )
                .toList();
    }
}