package technology.renatus.routepulse.scheduling.planning;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.DriverScheduleResult;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.importing.ScheduleProvider;

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
