package technology.renatus.routepulse.reporting.pilot.model;

/** One driver's planned-vs-actual contribution to the weekly fairness view. */
public record DriverFairnessRow(
        Long driverId,
        String driverName,
        long scheduledMinutes,
        long actualMinutes,
        int scheduledAssignments,
        int assignmentsDriven) {

    public double scheduledHours() {
        return scheduledMinutes / 60.0;
    }

    public double actualHours() {
        return actualMinutes / 60.0;
    }

    public double differenceHours() {
        return (actualMinutes - scheduledMinutes) / 60.0;
    }
}
