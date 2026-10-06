package technology.renatus.routepulse.driver.insights;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** An inclusive date range in the operations timezone. */
public record DriverAnalyticsRange(LocalDate from, LocalDate to) {
    public DriverAnalyticsRange {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Choose an end date on or after the start date.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= 93) {
            throw new IllegalArgumentException("Choose a range of 93 days or fewer.");
        }
    }

    public static DriverAnalyticsRange resolve(LocalDate from, LocalDate to, String preset, LocalDate today) {
        String range = preset == null ? "" : preset;
        return switch (range) {
            case "today" -> new DriverAnalyticsRange(today, today);
            case "week" -> new DriverAnalyticsRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today);
            case "month" -> new DriverAnalyticsRange(today.minusDays(29), today);
            case "", "custom" -> {
                if (from == null && to == null) {
                    if ("custom".equals(range)) throw new IllegalArgumentException("Choose a start and end date.");
                    yield new DriverAnalyticsRange(today.minusDays(29), today);
                }
                LocalDate start = from == null ? to : from;
                LocalDate end = to == null ? start : to;
                if (end.isAfter(today)) throw new IllegalArgumentException("Choose dates through today to view recorded activity.");
                yield new DriverAnalyticsRange(start, end);
            }
            default -> throw new IllegalArgumentException("Choose Today, This week, Last 30 days, or a custom date range.");
        };
    }
}
