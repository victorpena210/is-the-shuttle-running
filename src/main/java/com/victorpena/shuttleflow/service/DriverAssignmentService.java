package com.victorpena.shuttleflow.service;

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

import com.victorpena.shuttleflow.model.Driver;
import com.victorpena.shuttleflow.model.DriverAssignment;
import com.victorpena.shuttleflow.model.DriverSchedule;
import com.victorpena.shuttleflow.model.DriverScheduleResult;
import com.victorpena.shuttleflow.model.ShuttlePeriod;
import com.victorpena.shuttleflow.repository.DriverAssignmentRepository;
import com.victorpena.shuttleflow.repository.DriverRepository;

@Service
public class DriverAssignmentService {

    private final DriverAssignmentRepository
            assignmentRepository;

    private final DriverRepository driverRepository;

    private final DriverScheduleService
            driverScheduleService;

    private final ZoneId operationsZone;

    public DriverAssignmentService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            DriverScheduleService driverScheduleService,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.assignmentRepository =
                assignmentRepository;

        this.driverRepository =
                driverRepository;

        this.driverScheduleService =
                driverScheduleService;

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

        List<DriverAssignment> newAssignments =
                new ArrayList<>();

        newAssignments.add(
                DriverAssignment.automatic(
                        assignmentDate,
                        ShuttlePeriod.MORNING,
                        morningDriver
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
                            afternoonDriver
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

        if (assignmentRepository
                .existsByOrganization_IdAndAssignmentDateAndDriver_Id(
                        organizationId,
                        assignmentDate,
                        driverId
                )) {

            throw new IllegalArgumentException(
                    "That driver already has a shuttle assignment "
                            + "for this date."
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
            String reason) {

        DriverAssignment assignment =
                loadAssignment(
                        organizationId,
                        assignmentId
                );

        if (assignmentRepository
                .existsByOrganization_IdAndAssignmentDateAndDriver_IdAndIdNot(
                        organizationId,
                        assignment.getAssignmentDate(),
                        newDriverId,
                        assignmentId
                )) {

            throw new IllegalArgumentException(
                    "That driver already has another shuttle "
                            + "assignment for this date."
            );
        }

        Driver newDriver =
                loadActiveDriver(
                        organizationId,
                        newDriverId
                );

        boolean changed =
                assignment.changeDriver(
                        newDriver,
                        reason
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
    public DriverAssignment startDriving(
            Long organizationId,
            Long assignmentId) {

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

        assignment.startDriving(now);

        return assignment;
    }

    @Transactional
    public DriverAssignment endDriving(
            Long organizationId,
            Long assignmentId) {

        DriverAssignment assignment =
                loadAssignment(
                        organizationId,
                        assignmentId
                );

        assignment.endDriving(
                currentOperationsDateTime()
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