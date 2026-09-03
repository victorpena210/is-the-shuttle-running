package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.AssignmentSource;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.DriverSchedule;
import technology.renatus.routepulse.model.DriverScheduleResult;
import technology.renatus.routepulse.model.ScheduleImport;
import technology.renatus.routepulse.model.ShuttlePeriod;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.UploadedShiftRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;
import technology.renatus.routepulse.repository.OrganizationMembershipRepository;

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

    private final ZoneId operationsZone;

    public DriverAssignmentService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            DriverScheduleService driverScheduleService,
            UploadedShiftRepository uploadedShiftRepository,
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository
                    organizationMembershipRepository,
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

        this.operationsZone =
                ZoneId.of(operationsZone);
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

        if (!existingAssignments.isEmpty()) {
            return orderByPeriod(
                    existingAssignments
            );
        }

        DriverScheduleResult scheduleResult =
                driverScheduleService
                        .getDriverSchedulesForDate(
                                organizationId,
                                assignmentDate
                        );

        Map<Long, DriverSchedule> schedulesByDriverId =
                scheduleResult.scheduledDrivers()
                        .stream()
                        .collect(Collectors.toMap(
                                DriverSchedule::driverId,
                                Function.identity(),
                                (first, duplicate) -> first
                        ));

        List<DriverSchedule> eligibleDrivers =
                schedulesByDriverId.values()
                        .stream()
                        .toList();

        if (eligibleDrivers.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> previousAssignmentCounts =
                eligibleDrivers.stream()
                        .collect(Collectors.toMap(
                                DriverSchedule::driverId,
                                schedule ->
                                        assignmentRepository
                                                .countByOrganization_IdAndDriver_IdAndAssignmentDateBefore(
                                                        organizationId,
                                                        schedule.driverId(),
                                                        assignmentDate
                                                )
                        ));

        List<DriverSchedule> fairestOrder =
                eligibleDrivers.stream()
                        .sorted(
                                Comparator
                                        .comparingLong(
                                                (DriverSchedule schedule) ->
                                                        previousAssignmentCounts
                                                                .get(
                                                                        schedule.driverId()
                                                                )
                                        )
                                        .thenComparing(
                                                DriverSchedule::driverName
                                        )
                                        .thenComparing(
                                                DriverSchedule::employeeId
                                        )
                        )
                        .toList();

        Driver morningDriver =
                loadDriver(
                        organizationId,
                        fairestOrder.get(0).driverId()
                );

        ScheduleImport sourceScheduleImport =
                uploadedShiftRepository
                        .findLatestScheduleImportForDate(
                                organizationId,
                                assignmentDate
                        )
                        .orElse(null);

        List<DriverAssignment> newAssignments =
                new ArrayList<>();

        newAssignments.add(
                DriverAssignment.automatic(
                        assignmentDate,
                        ShuttlePeriod.MORNING,
                        morningDriver,
                        sourceScheduleImport
                )
        );

        if (fairestOrder.size() >= 2) {

            Driver afternoonDriver =
                    loadDriver(
                            organizationId,
                            fairestOrder.get(1).driverId()
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

    @Transactional
    public DriverAssignment createManualAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            ShuttlePeriod period,
            Long driverId,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            String reason) {

        validateReason(reason);

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

        validateReason(reason);

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

        UserAccount actor = loadActor(
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

        UserAccount actor = loadActor(
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

        assignments.stream()
                .filter(assignment ->
                        assignment.getAssignmentSource()
                                != AssignmentSource.MANUAL
                )
                .forEach(assignment ->
                        assignment.confirmScheduleReview(
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
                .equals(now.toLocalDate())) {

            throw new IllegalStateException(
                    "Driving can only be started on the assignment date."
            );
        }

        assignment.startDriving(
                now,
                loadActor(
                        organizationId,
                        actorEmail
                )
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

        assignment.endDriving(
                currentOperationsDateTime(),
                loadActor(
                        organizationId,
                        actorEmail
                )
        );

        return assignment;
    }

    public LocalDate getCurrentOperationsDate() {
        return LocalDate.now(operationsZone);
    }

    private LocalDateTime currentOperationsDateTime() {
        return LocalDateTime.now(operationsZone);
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
                .isAfter(scheduledStartTime)) {

            throw new IllegalArgumentException(
                    "Scheduled end time must be after "
                            + "the start time."
            );
        }
    }

    private void validateReason(String reason) {

        if (reason != null
                && reason.strip().length() > 500) {

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

        if (actorEmail == null || actorEmail.isBlank()) {
            throw new IllegalStateException(
                    "The signed-in user is required for this action."
            );
        }

        UserAccount actor = userAccountRepository
                .findByEmailIgnoreCase(actorEmail.trim())
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

        return assignments.stream()
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
