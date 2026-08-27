package com.victorpena.shuttleflow.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.victorpena.shuttleflow.model.Driver;
import com.victorpena.shuttleflow.model.DriverSchedule;
import com.victorpena.shuttleflow.model.DriverScheduleResult;
import com.victorpena.shuttleflow.model.ShiftRecord;
import com.victorpena.shuttleflow.provider.ScheduleProvider;
import com.victorpena.shuttleflow.repository.DriverRepository;

@Service
@Transactional(readOnly = true)
public class DriverScheduleService {

    private final DriverRepository driverRepository;
    private final ScheduleProvider scheduleProvider;

    public DriverScheduleService(
            DriverRepository driverRepository,
            ScheduleProvider scheduleProvider) {

        this.driverRepository = driverRepository;
        this.scheduleProvider = scheduleProvider;
    }

    public DriverScheduleResult getDriverSchedulesForDate(
            Long organizationId,
            LocalDate date) {

        List<Driver> activeDrivers =
                driverRepository
                        .findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
                                organizationId
                        );

        List<ShiftRecord> shifts =
                scheduleProvider.getSchedulesForDate(
                        organizationId,
                        date
                );

        Map<String, Driver> activeDriversByEmployeeId =
                activeDrivers.stream()
                        .collect(Collectors.toMap(
                                Driver::getEmployeeId,
                                Function.identity()
                        ));

        List<DriverSchedule> scheduledDrivers =
                shifts.stream()
                        .filter(shift ->
                                activeDriversByEmployeeId
                                        .containsKey(
                                                shift.getEmployeeId()
                                        )
                        )
                        .map(shift -> {
                            Driver driver =
                                    activeDriversByEmployeeId.get(
                                            shift.getEmployeeId()
                                    );

                            return new DriverSchedule(
                                    driver.getId(),
                                    driver.getEmployeeId(),
                                    driver.getName(),
                                    shift.getShiftDate(),
                                    shift.getStartTime(),
                                    shift.getEndTime()
                            );
                        })
                        .sorted(
                                Comparator
                                        .comparing(
                                                DriverSchedule::startTime
                                        )
                                        .thenComparing(
                                                DriverSchedule::driverName
                                        )
                        )
                        .toList();

        Set<String> scheduledEmployeeIds =
                scheduledDrivers.stream()
                        .map(
                                DriverSchedule::employeeId
                        )
                        .collect(
                                Collectors.toSet()
                        );

        List<Driver> driversWithoutSchedules =
                activeDrivers.stream()
                        .filter(driver ->
                                !scheduledEmployeeIds.contains(
                                        driver.getEmployeeId()
                                )
                        )
                        .toList();

        return new DriverScheduleResult(
                scheduledDrivers,
                driversWithoutSchedules
        );
    }
}