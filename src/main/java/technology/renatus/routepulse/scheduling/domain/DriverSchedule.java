package technology.renatus.routepulse.scheduling.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One uploaded work shift for a registered RoutePulse driver.
 *
 * The shift times are also used as the hard availability boundary for an
 * automatically generated driving assignment. Demand can begin before the
 * driver clocks in or continue after the driver clocks out, but RoutePulse
 * will only schedule that driver for the portion that overlaps this shift.
 *
 * An end time earlier than the start time is treated as a next-day clock-out.
 * That allows a shift such as 9:00 AM - 12:00 AM to remain valid.
 */
public record DriverSchedule(
        Long driverId,
        String employeeId,
        String driverName,
        LocalDate shiftDate,
        LocalTime startTime,
        LocalTime endTime) {

    /**
     * Returns true when this driver's work shift overlaps the requested
     * driving window at all.
     */
    public boolean overlaps(
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        return clipTo(
                requestedStart,
                requestedEnd
        ) != null;
    }

    /**
     * Clips a requested driving window to this driver's actual work shift.
     *
     * Examples:
     *
     * demand 8:45 AM - 12:15 PM + shift 9:00 AM - 4:00 PM
     * becomes 9:00 AM - 12:15 PM.
     *
     * demand 8:45 AM - 12:15 PM + shift 8:00 AM - 12:00 PM
     * becomes 8:45 AM - 12:00 PM.
     */
    public DrivingWindow clipTo(
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        if (!isValidWindow(
                requestedStart,
                requestedEnd
        ) || !isValidShiftWindow()) {
            return null;
        }

        int requestedStartMinute =
                toMinuteOfDay(requestedStart);

        int requestedEndMinute =
                normalizeEndMinute(
                        requestedStartMinute,
                        toMinuteOfDay(requestedEnd)
                );

        int shiftStartMinute =
                toMinuteOfDay(startTime);

        int shiftEndMinute =
                normalizeEndMinute(
                        shiftStartMinute,
                        toMinuteOfDay(endTime)
                );

        int overlapStart =
                Math.max(
                        requestedStartMinute,
                        shiftStartMinute
                );

        int overlapEnd =
                Math.min(
                        requestedEndMinute,
                        shiftEndMinute
                );

        if (overlapEnd <= overlapStart) {
            return null;
        }

        return new DrivingWindow(
                fromAbsoluteMinute(overlapStart),
                fromAbsoluteMinute(overlapEnd),
                overlapEnd - overlapStart
        );
    }

    /**
     * Number of minutes of the requested window this work shift can cover.
     * A zero result means there is no usable overlap.
     */
    /**
     * Returns true only when this work shift covers the entire requested
     * driving window. Partial overlap is not enough for reconciled coverage.
     */
    public boolean covers(
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        DrivingWindow window = clipTo(
                requestedStart,
                requestedEnd
        );

        if (window == null) {
            return false;
        }

        int requestedStartMinute = toMinuteOfDay(requestedStart);
        int requestedEndMinute = normalizeEndMinute(
                requestedStartMinute,
                toMinuteOfDay(requestedEnd)
        );

        return window.minutes() == requestedEndMinute - requestedStartMinute;
    }

    public long overlapMinutes(
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        DrivingWindow window =
                clipTo(
                        requestedStart,
                        requestedEnd
                );

        return window == null
                ? 0
                : window.minutes();
    }

    private boolean isValidShiftWindow() {
        return startTime != null
                && endTime != null
                && !endTime.equals(startTime);
    }

    private boolean isValidWindow(
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        return requestedStart != null
                && requestedEnd != null
                && !requestedEnd.equals(requestedStart);
    }

    private int normalizeEndMinute(
            int startMinute,
            int endMinute) {

        return endMinute <= startMinute
                ? endMinute + (24 * 60)
                : endMinute;
    }

    private int toMinuteOfDay(
            LocalTime time) {

        return (time.getHour() * 60)
                + time.getMinute();
    }

    private LocalTime fromAbsoluteMinute(
            int minute) {

        int normalized =
                Math.floorMod(
                        minute,
                        24 * 60
                );

        return LocalTime.of(
                normalized / 60,
                normalized % 60
        );
    }

    public record DrivingWindow(
            LocalTime startTime,
            LocalTime endTime,
            long minutes) {
    }
}
