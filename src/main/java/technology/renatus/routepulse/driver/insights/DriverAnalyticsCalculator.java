package technology.renatus.routepulse.driver.insights;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Calculates personal activity from already-authorized inputs, without exposing other drivers. */
public final class DriverAnalyticsCalculator {
    private DriverAnalyticsCalculator() { }

    public record Session(Instant startedAt, Instant endedAt, String type, String period,
            boolean unscheduled, String status) { }
    public record SessionRow(Instant startedAt, Instant endedAt, String type, String period,
            String status, long seconds) {
        public String drivingTime() { return duration(seconds); }
    }
    public record Day(LocalDate date, long seconds, long unscheduledSeconds, long sessions, long confirmations) {
        public String drivingTime() { return duration(seconds); }
        public String unscheduledTime() { return duration(unscheduledSeconds); }
    }
    public record Report(long seconds, long unscheduledSeconds, long confirmations, long activeDays,
            long ongoingSessions, List<Day> days, List<SessionRow> sessions, Instant asOf) {
        public String drivingTime() { return duration(seconds); }
        public String unscheduledTime() { return duration(unscheduledSeconds); }
        public String averagePerActiveDay() { return activeDays == 0 ? "—" : duration(seconds / activeDays); }
        public boolean hasActivity() { return !sessions.isEmpty() || confirmations > 0; }
    }
    private record Span(Instant start, Instant end) { }

    public static Report calculate(DriverAnalyticsRange range, ZoneId zone, Instant now,
            List<Session> input, List<Instant> confirmations) {
        Instant start = range.from().atStartOfDay(zone).toInstant();
        Instant end = earlier(range.to().plusDays(1).atStartOfDay(zone).toInstant(), now);
        List<Session> included = input.stream()
                .filter(s -> s.startedAt().isBefore(end)
                        && (s.endedAt() == null || s.endedAt().isAfter(start))
                        && (s.endedAt() == null || !s.endedAt().isBefore(s.startedAt())))
                .sorted(Comparator.comparing(Session::startedAt).reversed()).toList();
        List<Instant> rides = confirmations.stream().filter(t -> !t.isBefore(start) && t.isBefore(end)).toList();
        List<SessionRow> rows = included.stream().map(s -> new SessionRow(s.startedAt(), s.endedAt(),
                s.type(), s.period(), s.status(), seconds(List.of(s), start, end))).toList();
        List<Day> days = new ArrayList<>();
        for (LocalDate date = range.to(); !date.isBefore(range.from()); date = date.minusDays(1)) {
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            Instant dayEnd = earlier(date.plusDays(1).atStartOfDay(zone).toInstant(), end);
            long daySeconds = seconds(included, dayStart, dayEnd);
            long unscheduled = seconds(included.stream().filter(Session::unscheduled).toList(), dayStart, dayEnd);
            long daySessions = included.stream().filter(s -> s.startedAt().isBefore(dayEnd)
                    && (s.endedAt() == null || s.endedAt().isAfter(dayStart))).count();
            long count = rides.stream().filter(t -> !t.isBefore(dayStart) && t.isBefore(dayEnd)).count();
            days.add(new Day(date, daySeconds, unscheduled, daySessions, count));
        }
        return new Report(seconds(included, start, end),
                seconds(included.stream().filter(Session::unscheduled).toList(), start, end),
                rides.size(), days.stream().filter(d -> d.seconds() > 0).count(),
                included.stream().filter(s -> s.endedAt() == null).count(),
                List.copyOf(days), rows, now);
    }

    /** Union intervals so duplicate/overlapping records never inflate a driver's time. */
    private static long seconds(List<Session> sessions, Instant start, Instant end) {
        if (!end.isAfter(start)) return 0;
        List<Span> spans = sessions.stream().map(s -> new Span(later(start, s.startedAt()),
                        earlier(end, s.endedAt() == null ? end : s.endedAt())))
                .filter(s -> s.end().isAfter(s.start())).sorted(Comparator.comparing(Span::start)).toList();
        long total = 0;
        Instant previousStart = null, previousEnd = null;
        for (Span span : spans) {
            if (previousEnd == null || span.start().isAfter(previousEnd)) {
                if (previousEnd != null) total += Duration.between(previousStart, previousEnd).getSeconds();
                previousStart = span.start(); previousEnd = span.end();
            } else previousEnd = later(previousEnd, span.end());
        }
        return total + (previousEnd == null ? 0 : Duration.between(previousStart, previousEnd).getSeconds());
    }

    private static Instant earlier(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
    private static Instant later(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
    public static String duration(long seconds) {
        if (seconds == 0) return "0 min";
        if (seconds < 60) return "<1 min";
        long minutes = seconds / 60;
        return minutes < 60 ? minutes + " min" : minutes / 60 + " h " + minutes % 60 + " min";
    }
}
