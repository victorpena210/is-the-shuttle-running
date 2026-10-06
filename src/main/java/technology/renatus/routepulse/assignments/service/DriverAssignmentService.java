package technology.renatus.routepulse.assignments.service;

import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentSource;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.PlannedDrivingWindow;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.service.DrivingActionService;
import technology.renatus.routepulse.organization.service.OrganizationActorService;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.model.UploadedShift;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/** Edits manual coverage and schedule reviews; delegates planning and driving lifecycle rules. */
@Service
public class DriverAssignmentService {

    private final ZoneId operationsZone;
    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final UploadedShiftRepository uploadedShiftRepository;
    private final OrganizationActorService organizationActorService;
    private final AssignmentPlanningService assignmentPlanningService;
    private final DrivingActionService drivingActionService;

    public DriverAssignmentService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            UploadedShiftRepository uploadedShiftRepository,
            OrganizationActorService organizationActorService,
            AssignmentPlanningService assignmentPlanningService,
            DrivingActionService drivingActionService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.uploadedShiftRepository = uploadedShiftRepository;
        this.organizationActorService = organizationActorService;
        this.assignmentPlanningService = assignmentPlanningService;
        this.drivingActionService = drivingActionService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    @Transactional(readOnly = true)
    public technology.renatus.routepulse.wardrobe.WardrobeHoursService.Hours getWardrobeHours(Long organizationId, LocalDate date) {
        return assignmentPlanningService.getWardrobeHours(organizationId, date);
    }

    @Transactional
    public void updateWardrobeHours(Long organizationId, technology.renatus.routepulse.wardrobe.WardrobeHoursRule.Scope scope,
            String label, LocalDate from, LocalDate to, LocalTime closingTime, String actorEmail) {
        assignmentPlanningService.updateWardrobeHours(organizationId, scope, label, from, to, closingTime, actorEmail);
    }
    @Transactional
    public void updateWardrobeHours(Long organizationId, technology.renatus.routepulse.wardrobe.WardrobeHoursRule.Scope scope,
            String label, LocalDate from, LocalDate to, LocalTime opening, LocalTime closing, Integer dayOfWeek, String actorEmail) {
        assignmentPlanningService.updateWardrobeHours(organizationId, scope, label, from, to, opening, closing, dayOfWeek, actorEmail);
    }


    @Transactional(readOnly = true)
    public DriverAssignment getMiddaySuggestion(Long organizationId, LocalDate date) {
        return assignmentPlanningService.getMiddaySuggestion(organizationId, date);
    }

    @Transactional
    public DriverAssignment acceptMiddaySuggestion(Long organizationId, LocalDate date, String expectedPlan) {
        return assignmentPlanningService.acceptMiddaySuggestion(organizationId, date, expectedPlan);
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
        assignmentPlanningService.lockPlanning(organizationId);


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
        assignment.replacePlannedDrivingWindows(
                List.of(new PlannedDrivingWindow(scheduledStartTime, scheduledEndTime))
        );

        assignmentPlanningService.validateDemandStartTime(organizationId, assignment);
        assignmentPlanningService.validateMorningCoverageAfterChange(organizationId, assignment);
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public DriverAssignment changeAssignedDriver(
            Long organizationId,
            Long assignmentId,
            Long newDriverId,
            String reason,
            String actorEmail) {
        assignmentPlanningService.lockPlanning(organizationId);


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
                organizationActorService.loadActor(
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

        assignmentPlanningService.validateDemandStartTime(organizationId, assignment);
        assignmentPlanningService.validateMorningCoverageAfterChange(organizationId, assignment);
        return assignment;
    }

    @Transactional
    public DriverAssignment updateScheduledTimes(
            Long organizationId,
            Long assignmentId,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime) {
        assignmentPlanningService.lockPlanning(organizationId);


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
        assignment.replacePlannedDrivingWindows(
                List.of(new PlannedDrivingWindow(scheduledStartTime, scheduledEndTime))
        );

        assignment.protectManualTimeEdit();

        assignmentPlanningService.validateDemandStartTime(organizationId, assignment);
        assignmentPlanningService.validateMorningCoverageAfterChange(organizationId, assignment);
        return assignment;
    }

    @Transactional
    public DriverAssignment acceptScheduleRecommendation(
            Long organizationId,
            Long assignmentId,
            Long recommendedDriverId,
            Long expectedScheduleImportId,
            String actorEmail) {
        assignmentPlanningService.lockPlanning(organizationId);


        DriverAssignment assignment = loadAssignment(
                organizationId,
                assignmentId
        );

        ensureAutomaticScheduleAssignment(assignment);

        ScheduleImport latestImport = requireExpectedLatestImport(
                organizationId,
                assignment.getAssignmentDate(),
                expectedScheduleImportId
        );

        Driver recommendedDriver = loadActiveDriver(
                organizationId,
                recommendedDriverId
        );

        List<UploadedShift> latestDriverShifts = uploadedShiftRepository
                .findAllByOrganization_IdAndScheduleImport_IdAndShiftDateAndEmployeeIdOrderByStartTimeAsc(
                        organizationId,
                        latestImport.getId(),
                        assignment.getAssignmentDate(),
                        recommendedDriver.getEmployeeId()
                );

        if (latestDriverShifts.isEmpty()) {
            throw new IllegalStateException(
                    "That recommendation is no longer on the latest uploaded schedule. Refresh coverage and try again."
            );
        }

        List<DriverSchedule> recommendedSchedules = latestDriverShifts.stream()
                .map(shift -> new DriverSchedule(
                        recommendedDriver.getId(),
                        recommendedDriver.getEmployeeId(),
                        recommendedDriver.getName(),
                        assignment.getAssignmentDate(),
                        shift.getStartTime(),
                        shift.getEndTime()
                ))
                .toList();

        boolean recommendationStillCovers = assignment.hasPlannedDrivingWindows()
                ? assignment.getPlannedDrivingWindows().stream()
                        .allMatch(window -> recommendedSchedules.stream().anyMatch(schedule -> schedule.covers(
                                window.getStartTime(),
                                window.getEndTime()
                        )))
                : assignment.getScheduledStartTime() == null
                        || assignment.getScheduledEndTime() == null
                        || recommendedSchedules.stream().anyMatch(schedule -> schedule.covers(
                                assignment.getScheduledStartTime(),
                                assignment.getScheduledEndTime()
                        ));

        if (!recommendationStillCovers) {
            throw new IllegalStateException(
                    "That recommendation no longer covers the required driving time. Refresh coverage and review the latest schedule."
            );
        }

        UserAccount actor = organizationActorService.loadActor(
                organizationId,
                actorEmail
        );

        boolean changed = assignment.changeDriver(
                recommendedDriver,
                "Accepted Is the Shuttle Running recommendation after schedule change",
                actor
        );

        if (!changed) {
            throw new IllegalStateException(
                    "The recommended driver is already assigned. Refresh coverage to review the current schedule."
            );
        }

        assignment.confirmScheduleReview(
                latestImport,
                actor,
                Instant.now()
        );

        assignmentPlanningService.validateDemandStartTime(organizationId, assignment);
        assignmentPlanningService.validateMorningCoverageAfterChange(organizationId, assignment);
        return assignment;
    }

    @Transactional
    public DriverAssignment keepCurrentAssignmentAfterScheduleChange(
            Long organizationId,
            Long assignmentId,
            Long expectedScheduleImportId,
            String actorEmail) {
        assignmentPlanningService.lockPlanning(organizationId);


        DriverAssignment assignment = loadAssignment(
                organizationId,
                assignmentId
        );

        ensureAutomaticScheduleAssignment(assignment);

        ScheduleImport latestImport = requireExpectedLatestImport(
                organizationId,
                assignment.getAssignmentDate(),
                expectedScheduleImportId
        );

        UserAccount actor = organizationActorService.loadActor(
                organizationId,
                actorEmail
        );

        assignment.confirmScheduleReview(
                latestImport,
                actor,
                Instant.now()
        );

        assignmentPlanningService.validateDemandStartTime(organizationId, assignment);
        assignmentPlanningService.validateMorningCoverageAfterChange(organizationId, assignment);
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
                organizationActorService.loadActor(
                        organizationId,
                        actorEmail
                );

        Instant reviewedAt =
                Instant.now();

        List<DriverAssignment> assignments =
                assignmentRepository
                        .findAllForUpdateByOrganizationAndDate(
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
                        assignment.getAssignmentSource() != AssignmentSource.MANUAL
                                && !assignment.hasDrivingSessions()
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

    public List<DriverAssignment> getExistingAssignments(
            Long organizationId,
            LocalDate assignmentDate) {

        if (organizationId == null) {
            throw new IllegalArgumentException("Organization id is required.");
        }
        if (assignmentDate == null) {
            throw new IllegalArgumentException("Assignment date is required.");
        }

        return assignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(
                        organizationId,
                        assignmentDate
                );
    }

    public LocalDate getCurrentOperationsDate() {

        return LocalDate.now(
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

    private ScheduleImport requireExpectedLatestImport(
            Long organizationId,
            LocalDate assignmentDate,
            Long expectedScheduleImportId) {

        ScheduleImport latestImport = uploadedShiftRepository
                .findLatestScheduleImportForDate(
                        organizationId,
                        assignmentDate
                )
                .orElseThrow(() -> new IllegalStateException(
                        "No current schedule import was found for this date."
                ));

        if (expectedScheduleImportId == null
                || !latestImport.getId().equals(expectedScheduleImportId)) {
            throw new IllegalStateException(
                    "A newer schedule was uploaded after this recommendation was calculated. Refresh coverage before making a decision."
            );
        }

        return latestImport;
    }

    private void ensureAutomaticScheduleAssignment(DriverAssignment assignment) {
        if (assignment.getAssignmentSource() == AssignmentSource.MANUAL) {
            throw new IllegalStateException(
                    "Manual assignments are not reconciled against schedule imports."
            );
        }
    }

    private DriverAssignment loadAssignment(
            Long organizationId,
            Long assignmentId) {

        return assignmentRepository
                .findByIdAndOrganizationForUpdate(
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

    public List<DriverAssignment> getOrCreateAssignments(Long organizationId, LocalDate date) {
        return assignmentPlanningService.getOrCreateAssignments(organizationId, date);
    }

    public DriverAssignment startDriving(Long organizationId, Long assignmentId, String actorEmail) {
        return drivingActionService.startDriving(organizationId, assignmentId, actorEmail);
    }

    public DriverAssignment endDriving(Long organizationId, Long assignmentId, String actorEmail) {
        return drivingActionService.endDriving(organizationId, assignmentId, actorEmail);
    }
}
