package technology.renatus.routepulse.scheduling.domain;

import java.util.List;
import technology.renatus.routepulse.driver.model.Driver;

public record DriverScheduleResult(
        List<DriverSchedule> scheduledDrivers,
        List<Driver> driversWithoutSchedules) {
}
