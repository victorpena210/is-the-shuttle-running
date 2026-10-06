package technology.renatus.routepulse.reporting.history;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Pure reporting logic. Never creates assignments or changes service outcomes. */
public final class ServiceReviewCalculator {
    public record Span(Instant start, Instant end) {
        public Span {
            if (!end.isAfter(start)) throw new IllegalArgumentException("A reporting window must end after it starts.");
        }
    }
    public record Activity(Long id, Long driverId, String driverName, boolean unscheduled, Instant start, Instant end) {}
    public record Ride(Long id, Long driverId, String driverName, Instant at) {}
    public record Assignment(Long id, Long driverId, String name, String outcome) {
        public boolean reviewed() { return Set.of("CANCELLED", "EXCUSED", "MISSED").contains(outcome); }
    }
    public record Demand(Span span, int count) {}
    public record Input(LocalDate date, String period, String title, String calendar, String demandSource,
                        List<Span> scopes, List<Demand> demand, List<Assignment> assignments,
                        List<Activity> activities, List<Ride> rides, String coverageExemption, Instant serviceNotBefore) {
        public Input(LocalDate date, String period, String title, String calendar, String demandSource,
                List<Span> scopes, List<Demand> demand, List<Assignment> assignments,
                List<Activity> activities, List<Ride> rides, String coverageExemption) {
            this(date, period, title, calendar, demandSource, scopes, demand, assignments, activities, rides, coverageExemption, null);
        }
        public Input(LocalDate date, String period, String title, String calendar, String demandSource,
                List<Span> scopes, List<Demand> demand, List<Assignment> assignments,
                List<Activity> activities, List<Ride> rides) {
            this(date, period, title, calendar, demandSource, scopes, demand, assignments, activities, rides, null);
        }
    }
    public record Window(String range, int demand, long confirmations, long seconds, String status, boolean needsReview) {
        public String drivingTime() { return duration(seconds); }
    }
    public record Row(LocalDate date, String period, String title, String timeRange, String calendar,
                      String demandSource, String scheduledDrivers, String actualDrivers, String state,
                      String evidence, String result, int demand, long confirmations, long seconds,
                      long unscheduledSeconds, int gaps, List<Window> windows, List<Span> scopes,
                      List<Activity> activities, List<Ride> rides) {
        public boolean needsReview() { return gaps > 0; }
        public String drivingTime() { return duration(seconds); }
        public String unscheduledTime() { return duration(unscheduledSeconds); }
        public String key() { return date + "-" + period; }
        public Row withDemandTotal(int total) {
            return new Row(date, period, title, timeRange, calendar, demandSource, scheduledDrivers, actualDrivers,
                    state, evidence, result, total, confirmations, seconds, unscheduledSeconds, gaps,
                    windows, scopes, activities, rides);
        }
    }
    public record Report(List<Row> rows, long seconds, long confirmations, long unscheduledSeconds, long reviewCount) {
        public String drivingTime() { return duration(seconds); }
        public String unscheduledTime() { return duration(unscheduledSeconds); }
    }

    private final ZoneId zone;
    private final int minimumDemand;
    private final int windowMinutes;
    public ServiceReviewCalculator(ZoneId zone, int minimumDemand, int windowMinutes) {
        if (minimumDemand < 1 || windowMinutes < 1 || 60 % windowMinutes != 0)
            throw new IllegalArgumentException("Invalid service-review window settings.");
        this.zone = zone;
        this.minimumDemand = minimumDemand;
        this.windowMinutes = windowMinutes;
    }

    public Row calculate(Input input, Long driverId, String driverRole, Instant now) {
        if (driverId != null && "scheduled".equals(driverRole)
                && input.assignments().stream().noneMatch(a -> driverId.equals(a.driverId()))) return null;
        List<Span> scopes = merge(input.scopes());
        List<Activity> allActivities = input.activities().stream()
                .filter(a -> overlap(a, scopes, now) > 0).toList();
        List<Ride> allRides = input.rides().stream().filter(r -> inside(r.at(), scopes) && !r.at().isAfter(now)).toList();
        boolean actualFilter = driverId != null && "actual".equals(driverRole);
        List<Activity> activities = allActivities.stream()
                .filter(a -> !actualFilter || driverId.equals(a.driverId())).toList();
        List<Ride> rides = allRides.stream().filter(r -> !actualFilter || driverId.equals(r.driverId())).toList();
        if (actualFilter && activities.isEmpty() && rides.isEmpty()) return null;

        boolean operating = "Open".equals(input.calendar()) || "Manager confirmed open".equals(input.calendar());
        boolean knownDemand = !"No schedule available".equals(input.demandSource());
        boolean reviewed = !input.assignments().isEmpty() && input.assignments().stream().allMatch(Assignment::reviewed);
        boolean archived = "Archived schedule totals".equals(input.demandSource());
        String exemption = input.coverageExemption();
        if ("midday".equals(input.period()) && input.assignments().isEmpty()) exemption = "Optional midday—not assigned";
        boolean evaluate = operating && knownDemand && !reviewed && !archived && exemption == null;
        // Include every activity window, even where forecast demand is zero.
        TreeMap<Instant, Span> buckets = new TreeMap<>();
        for (Span scope : scopes) {
            for (Instant start = scope.start(); start.isBefore(scope.end()); start = start.plusSeconds(windowMinutes * 60L)) {
                Instant end = min(start.plusSeconds(windowMinutes * 60L), scope.end());
                buckets.put(start, new Span(start, end));
            }
        }
        input.demand().forEach(d -> buckets.put(d.span().start(), d.span()));
        List<Window> windows = new ArrayList<>();
        int gaps = 0;
        for (Span span : buckets.values()) {
            int demand = input.demand().stream().filter(d -> d.span().start().equals(span.start())).mapToInt(Demand::count).sum();
            List<Span> scope = List.of(span);
            long seconds = serviceSeconds(activities, scope, now);
            long confirmations = rides.stream().filter(r -> inside(r.at(), scope)).count();
            boolean completed = !span.end().isAfter(now);
            // Coverage flags always assess all recorded service, including substitute/unscheduled drivers.
            Instant reviewStart = input.serviceNotBefore() == null ? span.start() : max(span.start(), input.serviceNotBefore());
            List<Span> reviewScope = reviewStart.isBefore(span.end()) ? List.of(new Span(reviewStart, span.end())) : List.of();
            boolean gap = evaluate && completed && !reviewScope.isEmpty() && demand >= minimumDemand
                    && serviceSeconds(allActivities, reviewScope, now) == 0
                    && allRides.stream().noneMatch(r -> inside(r.at(), reviewScope));
            if (gap) gaps++;
            String status = !operating ? "Not evaluated: " + input.calendar().toLowerCase(Locale.ROOT)
                    : !knownDemand ? "No schedule to compare"
                    : archived ? "Archived demand totals only"
                    : reviewed ? "Outcome already reviewed"
                    : exemption != null ? (seconds > 0 || confirmations > 0 ? evidence(seconds, confirmations) : exemption)
                    : reviewScope.isEmpty() ? "Before wardrobe closes—driving not required"
                    : !completed ? (now.isBefore(reviewStart) ? "Upcoming" : "In progress")
                    : gap ? "No recorded service evidence"
                    : seconds > 0 || confirmations > 0 ? evidence(seconds, confirmations)
                    : demand < minimumDemand ? "Below review threshold" : "Service recorded by another driver";
            if (demand > 0 || seconds > 0 || confirmations > 0)
                windows.add(new Window(timeRange(span), demand, confirmations, seconds, status, gap));
        }
        long seconds = serviceSeconds(activities, scopes, now);
        long unscheduled = serviceSeconds(activities.stream().filter(Activity::unscheduled).toList(), scopes, now);
        Instant first = scopes.get(0).start();
        if (input.serviceNotBefore() != null && input.serviceNotBefore().isAfter(first)) first = input.serviceNotBefore();
        Instant last = scopes.get(scopes.size() - 1).end();
        String state = !operating ? input.calendar() + " · coverage not evaluated"
                : !knownDemand ? "No schedule available"
                : archived ? "Archived demand · window coverage not evaluated"
                : reviewed ? "Outcome reviewed: " + input.assignments().stream().map(a -> a.outcome().toLowerCase(Locale.ROOT)).distinct().collect(Collectors.joining(", "))
                : exemption != null ? exemption
                : now.isBefore(first) ? "Upcoming"
                : now.isBefore(last) ? "In progress"
                : input.assignments().isEmpty() && !"off-hours".equals(input.period()) ? "No scheduled assignment"
                : "Period ended";
        TreeSet<String> actualNames = new TreeSet<>();
        activities.forEach(a -> actualNames.add(a.driverName()));
        rides.forEach(r -> actualNames.add(r.driverName()));
        String scheduled = input.assignments().stream().map(Assignment::name).distinct().collect(Collectors.joining(", "));
        return new Row(input.date(), input.period(), input.title(), scopes.stream().map(this::timeRange).collect(Collectors.joining(" / ")),
                input.calendar(), input.demandSource(), scheduled.isBlank() ? "No scheduled assignment" : scheduled,
                actualNames.isEmpty() ? "No recorded activity" : String.join(", ", actualNames), state,
                evidence(seconds, rides.size()), result(seconds, rides.size()),
                input.demand().stream().mapToInt(Demand::count).sum(), rides.size(), seconds, unscheduled, gaps,
                List.copyOf(windows), scopes, activities, rides);
    }

    public static Report summarize(List<Row> rows, Instant now) {
        // Periods may overlap at demand boundaries. Union their actual intervals and deduplicate scans.
        List<Span> active = new ArrayList<>();
        List<Span> unscheduled = new ArrayList<>();
        Set<Long> rideIds = new HashSet<>();
        for (Row row : rows) {
            for (Activity activity : row.activities()) {
                List<Span> clips = clips(activity, row.scopes(), now);
                active.addAll(clips);
                if (activity.unscheduled()) unscheduled.addAll(clips);
            }
            row.rides().forEach(r -> rideIds.add(r.id()));
        }
        return new Report(List.copyOf(rows), seconds(active), rideIds.size(), seconds(unscheduled),
                rows.stream().filter(Row::needsReview).count());
    }
    public static boolean matches(Row row, String result) {
        return "all".equals(result) || ("needs-review".equals(result) ? row.needsReview() : result.equals(row.result()));
    }
    private static String result(long seconds, long rides) {
        if (seconds > 0 && rides > 0) return "both";
        if (seconds > 0) return "driving-only";
        if (rides > 0) return "rider-only";
        return "none";
    }
    private static String evidence(long seconds, long rides) {
        return switch (result(seconds, rides)) {
            case "both" -> "Driving and rider evidence";
            case "driving-only" -> "Driving only";
            case "rider-only" -> "Rider confirmations only";
            default -> "No recorded evidence";
        };
    }
    public static String duration(long seconds) {
        if (seconds > 0 && seconds < 60) return "<1 min";
        long minutes = seconds / 60;
        return minutes < 60 ? minutes + " min" : (minutes / 60) + " hr " + (minutes % 60) + " min";
    }
    private String timeRange(Span span) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
        ZonedDateTime start = span.start().atZone(zone), end = span.end().atZone(zone);
        return start.format(format) + "–" + end.format(format)
                + (!start.toLocalDate().equals(end.toLocalDate()) ? " (next day)" : "");
    }
    public static long serviceSeconds(List<Activity> activities, List<Span> scopes, Instant now) {
        return seconds(activities.stream().flatMap(a -> clips(a, scopes, now).stream()).toList());
    }
    private static long overlap(Activity activity, List<Span> scopes, Instant now) {
        return clips(activity, scopes, now).stream().mapToLong(s -> Duration.between(s.start(), s.end()).getSeconds()).sum();
    }
    private static List<Span> clips(Activity activity, List<Span> scopes, Instant now) {
        Instant end = activity.end() == null ? now : min(activity.end(), now);
        List<Span> clips = new ArrayList<>();
        for (Span scope : scopes) {
            Instant start = max(activity.start(), scope.start()), stop = min(end, scope.end());
            if (stop.isAfter(start)) clips.add(new Span(start, stop));
        }
        return clips;
    }
    private static boolean inside(Instant at, List<Span> scopes) {
        return scopes.stream().anyMatch(s -> !at.isBefore(s.start()) && at.isBefore(s.end()));
    }
    private static long seconds(List<Span> spans) {
        return merge(spans).stream().mapToLong(s -> Duration.between(s.start(), s.end()).getSeconds()).sum();
    }
    public static List<Span> merge(List<Span> spans) {
        List<Span> sorted = spans.stream().sorted(Comparator.comparing(Span::start)).toList();
        List<Span> merged = new ArrayList<>();
        for (Span span : sorted) {
            if (merged.isEmpty() || span.start().isAfter(merged.get(merged.size() - 1).end())) merged.add(span);
            else {
                Span previous = merged.remove(merged.size() - 1);
                merged.add(new Span(previous.start(), max(previous.end(), span.end())));
            }
        }
        return List.copyOf(merged);
    }
    private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
}
