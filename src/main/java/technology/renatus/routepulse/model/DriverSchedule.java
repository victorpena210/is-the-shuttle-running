package technology.renatus.routepulse.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record DriverSchedule(
        Long driverId,
        String employeeId,
        String driverName,
        LocalDate shiftDate,
        LocalTime startTime,
        LocalTime endTime) {
}