package technology.renatus.routepulse.reporting.workload.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class WorkloadSummary {

    private final LocalDate fromDate;
    private final LocalDate toDate;

    private final long scheduledMinutes;
    private final long actualMinutes;

    private final int scheduledShifts;
    private final int shiftsDriven;

    private final List<DriverWorkloadRow> drivers;

    public WorkloadSummary(
            LocalDate fromDate,
            LocalDate toDate,
            long scheduledMinutes,
            long actualMinutes,
            int scheduledShifts,
            int shiftsDriven,
            List<DriverWorkloadRow> drivers) {

        this.fromDate = fromDate;
        this.toDate = toDate;
        this.scheduledMinutes = scheduledMinutes;
        this.actualMinutes = actualMinutes;
        this.scheduledShifts = scheduledShifts;
        this.shiftsDriven = shiftsDriven;

        this.drivers =
                List.copyOf(
                        drivers
                );
    }

    public static WorkloadSummary empty(
            LocalDate fromDate,
            LocalDate toDate) {

        return new WorkloadSummary(
                fromDate,
                toDate,
                0,
                0,
                0,
                0,
                new ArrayList<>()
        );
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
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

    public List<DriverWorkloadRow> getDrivers() {
        return drivers;
    }
}
