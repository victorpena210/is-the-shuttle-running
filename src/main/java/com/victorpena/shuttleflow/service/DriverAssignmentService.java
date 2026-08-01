package com.victorpena.shuttleflow.service;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final DriverScheduleService driverScheduleService;

    public DriverAssignmentService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            DriverScheduleService driverScheduleService) {

        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.driverScheduleService = driverScheduleService;
    }

    @Transactional
    public List<DriverAssignment> getOrCreateAssignments(
            LocalDate assignmentDate) {

        /*
         * Never change assignments simply because the page
         * was refreshed.
         */
        List<DriverAssignment> existingAssignments =
                assignmentRepository.findAllByAssignmentDate(
                        assignmentDate
                );

        if (!existingAssignments.isEmpty()) {
            return orderByPeriod(existingAssignments);
        }

        /*
         * DriverScheduleService already removes inactive drivers
         * and matches employee IDs against the uploaded schedule.
         */
        DriverScheduleResult scheduleResult =
                driverScheduleService.getDriverSchedulesForDate(
                        assignmentDate
                );

        /*
         * Deduplicate by driver ID in case the CSV ever contains
         * multiple shift rows for one driver.
         */
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

        /*
         * Do not create only half of the day's coverage.
         */
        /*
         * No assignments can be created when nobody is eligible.
         */
        if (eligibleDrivers.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> previousAssignmentCounts =
                eligibleDrivers.stream()
                        .collect(Collectors.toMap(
                                DriverSchedule::driverId,
                                schedule -> assignmentRepository
                                        .countByDriver_IdAndAssignmentDateBefore(
                                                schedule.driverId(),
                                                assignmentDate
                                        )
                        ));

        /*
         * Fewest previous assignments wins.
         * Name and employee ID provide consistent tie-breakers.
         */
        List<DriverSchedule> fairestOrder =
                eligibleDrivers.stream()
                        .sorted(
                                Comparator
                                        .comparingLong(
                                                (DriverSchedule schedule) ->
                                                        previousAssignmentCounts.get(
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
                loadDriver(fairestOrder.get(0).driverId());

        List<DriverAssignment> newAssignments =
                new ArrayList<>();

        newAssignments.add(
                new DriverAssignment(
                        assignmentDate,
                        ShuttlePeriod.MORNING,
                        morningDriver
                )
        );

        /*
         * Only create the afternoon assignment when a second,
         * different driver is available.
         */
        if (fairestOrder.size() >= 2) {

            Driver afternoonDriver =
                    loadDriver(fairestOrder.get(1).driverId());

            newAssignments.add(
                    new DriverAssignment(
                            assignmentDate,
                            ShuttlePeriod.AFTERNOON,
                            afternoonDriver
                    )
            );
        }

        return orderByPeriod(
                assignmentRepository.saveAll(newAssignments)
        );
    }

    private Driver loadDriver(Long driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Eligible driver was not found."
                        )
                );
    }

    private List<DriverAssignment> orderByPeriod(
            List<DriverAssignment> assignments) {

        return assignments.stream()
                .sorted(Comparator.comparingInt(
                        assignment ->
                                assignment.getPeriod().ordinal()
                ))
                .toList();
    }
}