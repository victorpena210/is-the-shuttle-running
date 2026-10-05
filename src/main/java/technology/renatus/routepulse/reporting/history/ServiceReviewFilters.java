package technology.renatus.routepulse.reporting.history;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

public record ServiceReviewFilters(LocalDate from, LocalDate to, String period, Long driverId,
                                   String driverRole, String result) {
    public static ServiceReviewFilters resolve(LocalDate from, LocalDate to, String range, String period,
            Long driverId, String driverRole, String result, LocalDate today) {
        if (range != null && !range.isBlank() && !"custom".equals(range)) {
            switch (range) {
                case "today" -> { from = today; to = today; }
                case "yesterday" -> { from = today.minusDays(1); to = from; }
                case "week" -> { from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); to = today; }
                default -> throw new IllegalArgumentException("Choose Today, Yesterday, This week or a custom date range.");
            }
        }
        if (from == null && to == null) { from = today; to = today; }
        else if (from == null) from = to;
        else if (to == null) to = from;
        if (to.isBefore(from)) throw new IllegalArgumentException("The end date must be on or after the start date.");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 30)
            throw new IllegalArgumentException("Choose a range of up to 31 days for service review.");
        period = valid(period, "all", Set.of("all", "morning", "midday", "evening", "off-hours"), "period");
        driverRole = valid(driverRole, "actual", Set.of("actual", "scheduled"), "driver role");
        result = valid(result, "all", Set.of("all", "needs-review", "both", "driving-only", "rider-only", "none"), "result");
        return new ServiceReviewFilters(from, to, period, driverId, driverRole, result);
    }
    private static String valid(String value, String fallback, Set<String> choices, String label) {
        if (value == null || value.isBlank()) return fallback;
        if (!choices.contains(value)) throw new IllegalArgumentException("Choose a valid " + label + " filter.");
        return value;
    }
}
