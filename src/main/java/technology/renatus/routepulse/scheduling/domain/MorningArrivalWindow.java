package technology.renatus.routepulse.scheduling.domain;

import java.time.LocalTime;

/**
 * One 15-minute block of scheduled employee clock-ins used to estimate
 * morning shuttle arrival demand.
 */
public record MorningArrivalWindow(
        LocalTime windowStart,
        LocalTime windowEnd,
        int teamMemberCount) {
}
