package technology.renatus.routepulse.assignments.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentSource;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.service.DrivingActionService;
import technology.renatus.routepulse.organization.service.OrganizationActorService;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
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
                organizationActorService.loadActor(
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
