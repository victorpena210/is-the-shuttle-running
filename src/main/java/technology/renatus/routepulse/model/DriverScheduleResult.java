package technology.renatus.routepulse.model;

import java.util.List;

public record DriverScheduleResult(
        List<DriverSchedule> scheduledDrivers,
        List<Driver> driversWithoutSchedules) {
}