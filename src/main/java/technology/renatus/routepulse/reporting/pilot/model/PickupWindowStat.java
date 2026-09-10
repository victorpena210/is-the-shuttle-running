package technology.renatus.routepulse.reporting.pilot.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Aggregated pickup demand for the same clock window across a reporting week. */
public record PickupWindowStat(
        LocalTime windowStart,
        LocalTime windowEnd,
        int scheduledDepartures) {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a");

    public String timeLabel() {
        return windowStart.format(TIME_FORMAT)
                + "–"
                + windowEnd.format(TIME_FORMAT);
    }
}
