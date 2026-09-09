package technology.renatus.routepulse.reporting.workload.model;

import technology.renatus.routepulse.driver.model.Driver;

public final class DriverWorkloadRow {

    private final Driver driver;

    private final long scheduledMinutes;
    private final long actualMinutes;

    private final int scheduledShifts;
    private final int shiftsDriven;

    private final int coveredByAnotherDriver;
    private final int coveredForAnotherDriver;

    public DriverWorkloadRow(
            Driver driver,
            long scheduledMinutes,
            long actualMinutes,
            int scheduledShifts,
            int shiftsDriven,
            int coveredByAnotherDriver,
            int coveredForAnotherDriver) {

        this.driver = driver;
        this.scheduledMinutes = scheduledMinutes;
        this.actualMinutes = actualMinutes;
        this.scheduledShifts = scheduledShifts;
        this.shiftsDriven = shiftsDriven;
        this.coveredByAnotherDriver =
                coveredByAnotherDriver;
        this.coveredForAnotherDriver =
                coveredForAnotherDriver;
    }

    public Driver getDriver() {
        return driver;
    }

    public long getScheduledMinutes() {
        return scheduledMinutes;
    }

    public double getScheduledHours() {

        return scheduledMinutes
                / 60.0;
    }

    public long getActualMinutes() {
        return actualMinutes;
    }

    public double getActualHours() {

        return actualMinutes
                / 60.0;
    }

    public long getDifferenceMinutes() {

        return actualMinutes
                - scheduledMinutes;
    }

    public double getDifferenceHours() {

        return getDifferenceMinutes()
                / 60.0;
    }

    public int getScheduledShifts() {
        return scheduledShifts;
    }

    public int getShiftsDriven() {
        return shiftsDriven;
    }

    public int getCoveredByAnotherDriver() {
        return coveredByAnotherDriver;
    }

    public int getCoveredForAnotherDriver() {
        return coveredForAnotherDriver;
    }
}
