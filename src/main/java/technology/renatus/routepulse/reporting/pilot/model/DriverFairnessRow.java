package technology.renatus.routepulse.reporting.pilot.model;

import java.util.Locale;

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

    public long differenceMinutes() {
        return actualMinutes - scheduledMinutes;
    }

    public double differenceHours() {
        return differenceMinutes() / 60.0;
    }

    /**
     * Keep display formatting out of Thymeleaf.  The fairness table is rendered
     * late in the response and a template-expression failure here can otherwise
     * leave the browser with a partially-rendered page plus the error template.
     */
    public String scheduledHoursLabel() {
        return formatHours(scheduledMinutes, false);
    }

    public String actualHoursLabel() {
        return formatHours(actualMinutes, false);
    }

    public String differenceHoursLabel() {
        return formatHours(differenceMinutes(), true);
    }

    public String differenceCssClass() {
        long difference = differenceMinutes();
        if (difference > 0) {
            return "analytics-table__positive";
        }
        if (difference < 0) {
            return "analytics-table__negative";
        }
        return "";
    }

    private static String formatHours(long minutes, boolean showPositiveSign) {
        double hours = minutes / 60.0;
        String sign = showPositiveSign && minutes > 0 ? "+" : "";
        return sign + String.format(Locale.ROOT, "%.1f", hours) + "h";
    }
}
