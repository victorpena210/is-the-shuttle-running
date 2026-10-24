package technology.renatus.routepulse.scheduling.demand;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.insights.DriverAnalyticsCalculator;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.wardrobe.WardrobeHoursService;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;

/** Read-only recommendations. A suggestion never assigns a driver or changes a session. */
@Service
@Transactional(readOnly = true)
public class DriverSuggestionService {
    private final DriverRepository drivers;
    private final DriverScheduleWindowRepository schedules;
    private final DrivingSessionRepository sessions;
    private final WardrobeHoursService wardrobe;
    private final DriverScheduleIdentityService access;
    private final technology.renatus.routepulse.driver.calendar.DriverCalendarAvailability calendars;
    private final ZoneId zone;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    public DriverSuggestionService(DriverRepository drivers, DriverScheduleWindowRepository schedules,
            DrivingSessionRepository sessions, WardrobeHoursService wardrobe, DriverScheduleIdentityService access,
            technology.renatus.routepulse.driver.calendar.DriverCalendarAvailability calendars,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.drivers = drivers; this.schedules = schedules; this.sessions = sessions;
        this.wardrobe = wardrobe; this.access = access; this.zone = ZoneId.of(zone);
        this.calendars = calendars;
    }

    public Suggestions page(Long org, DemandForecastService.Forecast forecast, String email) {
        access.requireManager(org, email);
        return page(org, forecast, Instant.now());
    }

    Suggestions page(Long org, DemandForecastService.Forecast forecast, Instant now) {
        return calculate(org, forecast, now, wardrobe.getSavedHours(org, forecast.date()), false, false, null);
    }

    /** Shared availability/counter checks for saved, calendar-based assignments. */
    public Suggestions forAssignments(Long org, DemandForecastService.Forecast forecast, Instant now,
            WardrobeHoursService.Hours hours) {
        return forAssignments(org, forecast, now, hours, false);
    }

    public Suggestions forAssignments(Long org, DemandForecastService.Forecast forecast, Instant now,
            WardrobeHoursService.Hours hours, boolean scareActors) {
        return forAssignments(org, forecast, now, hours, scareActors, null);
    }

    public Suggestions forAssignments(Long org, DemandForecastService.Forecast forecast, Instant now,
            WardrobeHoursService.Hours hours, boolean scareActors, ParkOperatingDay park) {
        LocalDateTime parkClosing = null;
        if (park != null && park.openingTime() != null && park.closingTime() != null) {
            parkClosing = forecast.date().atTime(park.closingTime());
            if (!park.closingTime().isAfter(park.openingTime())) parkClosing = parkClosing.plusDays(1);
        }
        return calculate(org, forecast, now, hours, true, scareActors, parkClosing);
    }

    private Suggestions calculate(Long org, DemandForecastService.Forecast forecast, Instant now,
            WardrobeHoursService.Hours hours, boolean openingCoverage, boolean scareActors, LocalDateTime parkClosing) {
        var date = forecast.date();
        var horizon = date.plusDays(1).atStartOfDay();
        if (parkClosing != null && parkClosing.isAfter(horizon)) horizon = parkClosing;
        // Historical previews use only history known before that day; future previews use today's history.
        var asOf = date.isBefore(now.atZone(zone).toLocalDate()) ? date.atStartOfDay(zone).toInstant() : now;
        var since = asOf.minus(Duration.ofDays(30));
        var roster = drivers.findAllByOrganization_IdAndActiveTrueOrderByNameAsc(org).stream()
                .filter(d -> d.getUser() == null || d.getUser().isEnabled()).toList();
        var work = schedules.overlapping(org, date.atStartOfDay(), horizon).stream()
                .filter(w -> roster.stream().anyMatch(d -> d.getId().equals(w.getDriver().getId())))
                .collect(Collectors.groupingBy(w -> w.getDriver().getId()));
        calendars.apply(org,date,horizon,now,work);
        work.keySet().removeIf(id -> roster.stream().noneMatch(d -> d.getId().equals(id)));
        var history = sessions.findOverlappingForServiceReview(org, since, asOf).stream()
                .collect(Collectors.groupingBy(s -> s.getDriver().getId()));
        var last = sessions.lastDriven(org, asOf).stream().collect(Collectors.toMap(
                DrivingSessionRepository.LastDriven::getDriverId, DrivingSessionRepository.LastDriven::getStartedAt));
        var stats = roster.stream().map(d -> new Candidate(d.getId(), d.getName(), d.getEmployeeId(),
                seconds(history.getOrDefault(d.getId(), List.of()), since, asOf),
                seconds(history.getOrDefault(d.getId(), List.of()), date.atStartOfDay(zone).toInstant(), asOf) > 0,
                last.containsKey(d.getId()) ? last.get(d.getId()).atZone(zone) : null)).sorted(ORDER).toList();
        var opening = hours.openingStartTime();
        // An afternoon-start roster supplies opening service on late-opening days.
        var earliest = work.values().stream().flatMap(List::stream).map(DriverScheduleWindow::getStartsAt)
                .filter(t -> t.toLocalDate().equals(date)).min(Comparator.naturalOrder()).orElse(null);
        if (!hours.usesOpeningWindow() && earliest != null && !earliest.toLocalTime().isBefore(LocalTime.NOON)
                && (hours.closingTime().equals(LocalTime.MIDNIGHT) || earliest.toLocalTime().isBefore(hours.closingTime())))
            opening = earliest.toLocalTime();
        var openingStart = date.atTime(opening);
        var closing = date.atTime(hours.closingTime());
        if (hours.closingTime().equals(LocalTime.MIDNIGHT)) closing = closing.plusDays(1);
        var openingEnd = opening.isBefore(LocalTime.NOON) ? earlier(openingStart.plusHours(3), closing) : closing;
        var groups = new ArrayList<Period>();
        var periods = new ArrayList<>(forecast.periods()); periods.add(forecast.offHours());
        if (openingCoverage) {
            // One chronological timeline: noon can appear in two demand display groups.
            var timeline = new TreeMap<LocalDateTime, DemandForecastService.Window>();
            for (var period : periods) for (var w : period.windows())
                timeline.merge(w.start(), w, (a,b) -> new DemandForecastService.Window(a.start(), a.end(),
                        a.clockIns()+b.clockIns(), a.clockOuts()+b.clockOuts()));
            // Split at exact manager-set opening/closing times as well as quarter-hours.
            // This keeps a 9:10 opening from overlapping the existing 9:15 demand bucket.
            var boundaries = new TreeSet<LocalDateTime>();
            for (var time=date.atStartOfDay(); !time.isAfter(horizon); time=time.plusMinutes(15)) boundaries.add(time);
            boundaries.add(horizon);
            boundaries.add(openingStart); boundaries.add(openingEnd); boundaries.add(closing);
            if (parkClosing != null) boundaries.add(parkClosing);
            // Availability can begin/end between quarter-hours; don't discard usable minutes.
            for (var shifts : work.values()) for (var shift : shifts) {
                if (shift.getStartsAt().isAfter(date.atStartOfDay()) && shift.getStartsAt().isBefore(horizon)) boundaries.add(shift.getStartsAt());
                if (shift.getEndsAt().isAfter(date.atStartOfDay()) && shift.getEndsAt().isBefore(horizon)) boundaries.add(shift.getEndsAt());
            }
            var points = new ArrayList<>(boundaries);
            var serviceWindows = new ArrayList<DemandForecastService.Window>();
            for (int i=0; i<points.size()-1; i++) {
                var from=points.get(i); var to=points.get(i+1);
                var entry=timeline.floorEntry(from);
                var w=entry==null?null:entry.getValue();
                boolean demand=w!=null && from.isBefore(w.end());
                serviceWindows.add(new DemandForecastService.Window(from,to,
                        demand?w.clockIns():0,demand?w.clockOuts():0));
            }
            periods = new ArrayList<>(List.of(new DemandForecastService.Period("service", "Shuttle service", "",
                    List.copyOf(serviceWindows), 0, 0)));
        }
        for (var period : periods) {
            var rows = new ArrayList<Window>();
            for (var window : period.windows()) {
                boolean openingWindow = !window.start().isBefore(openingStart) && window.end().compareTo(openingEnd) <= 0;
                boolean actorWindow = scareActors && !window.start().isBefore(date.atTime(17,0))
                        && !window.end().isAfter(date.atTime(17,45));
                boolean parkWindow = parkClosing != null && !window.start().isBefore(closing) && !window.end().isAfter(parkClosing);
                if (window.movements() == 0 && !(openingCoverage && openingWindow) && !actorWindow && !parkWindow) continue;
                if (openingCoverage && window.start().isBefore(openingStart) && !actorWindow) continue;
                if (openingCoverage && !openingWindow && window.start().isBefore(closing) && !actorWindow) continue;
                var eligible = stats.stream().filter(c -> covers(work.getOrDefault(c.driverId(), List.of()), window.start(), window.end()))
                        .filter(c -> withinSessionLimit(history.getOrDefault(c.driverId(), List.of()), window.end(), now)).toList();
                String reason = "";
                if (window.start().isBefore(openingStart) && !actorWindow) reason = "Before wardrobe opening";
                else if (!window.start().isBefore(openingStart) && window.start().isBefore(closing)) {
                    if (window.end().isAfter(openingEnd) && !actorWindow) reason = "Later service waits until wardrobe closes";
                    else {
                        // Counter coverage is another distinct, active roster member, not another CSV row.
                        var counter = stats.stream().filter(c -> covers(work.getOrDefault(c.driverId(), List.of()), window.start(), window.end()))
                                .filter(c -> !currentlyDriving(history.getOrDefault(c.driverId(), List.of()), window.start(), window.end(), now)).toList();
                        eligible = eligible.stream().filter(c -> counter.stream().anyMatch(other -> !other.driverId().equals(c.driverId()))).toList();
                        if (eligible.isEmpty()) reason = "Another scheduled roster member is needed at the wardrobe counter";
                    }
                }
                if (!reason.isEmpty()) eligible = List.of();
                if (eligible.isEmpty() && reason.isEmpty()) reason = "No available driver covers this full window";
                var service = actorWindow ? ShuttleService.SCARE_ACTORS : openingWindow
                        ? (opening.isBefore(LocalTime.NOON) ? ShuttleService.MORNING_OPENING : ShuttleService.MIDDAY_OPENING)
                        : ShuttleService.EMPLOYEE_PICKUP;
                append(rows, new Window(window.start(), window.end(), eligible, reason, service));
            }
            groups.add(new Period(period.key(), period.title(), List.copyOf(rows)));
        }
        var missing = stats.stream().filter(c -> work.getOrDefault(c.driverId(),List.of()).isEmpty()).toList();
        return new Suggestions(List.copyOf(groups), stats, missing, asOf.atZone(zone),
                openingStart, openingEnd, closing, hours.label(), work.values().stream().allMatch(List::isEmpty));
    }

    private boolean withinSessionLimit(List<DrivingSession> history, LocalDateTime end, Instant now) {
        return history.stream().filter(s -> s.isActive() && !s.hasReachedDurationLimit(now))
                .noneMatch(s -> end.atZone(zone).toInstant().isAfter(s.getAutomaticEndAt())
                        && end.toLocalDate().equals(now.atZone(zone).toLocalDate()));
    }
    private boolean currentlyDriving(List<DrivingSession> history, LocalDateTime start, LocalDateTime end, Instant now) {
        return history.stream().anyMatch(s -> s.isActive() && !s.hasReachedDurationLimit(now)
                && end.atZone(zone).toInstant().isAfter(now)
                && start.atZone(zone).toInstant().isBefore(s.getAutomaticEndAt()));
    }
    static boolean covers(List<DriverScheduleWindow> work, LocalDateTime start, LocalDateTime end) {
        var cursor = start;
        for (var shift : work.stream().sorted(Comparator.comparing(DriverScheduleWindow::getStartsAt)).toList()) {
            if (shift.getStartsAt().isAfter(cursor)) break;
            if (shift.getEndsAt().isAfter(cursor)) cursor = shift.getEndsAt();
            if (!cursor.isBefore(end)) return true;
        }
        return false;
    }
    /** Union corrected intervals; an overdue open session contributes at most four hours. */
    static long seconds(List<DrivingSession> sessions, Instant from, Instant to) {
        if (!to.isAfter(from)) return 0;
        var spans = sessions.stream().map(s -> new Span(later(from, s.getStartedAt()),
                earlier(to, s.isActive() ? s.getAutomaticEndAt() : s.getEndedAt())))
                .filter(s -> s.end().isAfter(s.start())).sorted(Comparator.comparing(Span::start)).toList();
        Instant start = null, end = null; long total = 0;
        for (var span : spans) {
            if (end == null || span.start().isAfter(end)) {
                if (end != null) total += Duration.between(start, end).getSeconds();
                start = span.start(); end = span.end();
            } else end = later(end, span.end());
        }
        return total + (end == null ? 0 : Duration.between(start, end).getSeconds());
    }
    private static void append(List<Window> rows, Window next) {
        if (!rows.isEmpty()) {
            var prior = rows.get(rows.size() - 1);
            if (prior.end().equals(next.start()) && prior.candidates().equals(next.candidates()) && prior.reason().equals(next.reason())
                    && prior.service() == next.service()
                    && Duration.between(prior.start(), next.end()).compareTo(DrivingSession.MAX_DURATION) <= 0) {
                rows.set(rows.size() - 1, new Window(prior.start(), next.end(), prior.candidates(), prior.reason(), prior.service())); return;
            }
        }
        rows.add(next);
    }
    static final Comparator<Candidate> ORDER = Comparator.comparing(Candidate::droveToday)
            .thenComparingLong(Candidate::seconds).thenComparing(Candidate::lastDriven, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Candidate::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Candidate::driverId);
    private static Instant earlier(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
    private static Instant later(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
    private static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }
    private record Span(Instant start, Instant end) {}
    public record Candidate(Long driverId, String name, String employeeId, long seconds, boolean droveToday, ZonedDateTime lastDriven) {
        public String drivingTime() { return DriverAnalyticsCalculator.duration(seconds); }
    }
    public record Window(LocalDateTime start, LocalDateTime end, List<Candidate> candidates, String reason, ShuttleService service) {
        public Window(LocalDateTime start, LocalDateTime end, List<Candidate> candidates, String reason) {
            this(start, end, candidates, reason, ShuttleService.EMPLOYEE_PICKUP);
        }
        public String timeLabel() { return start.format(TIME) + " – " + end.format(TIME)
                + (end.toLocalDate().isAfter(start.toLocalDate()) ? " (next day)" : ""); }
        public Candidate recommended() { return candidates.isEmpty() ? null : candidates.get(0); }
        public List<Candidate> alternatives() { return candidates.stream().skip(1).toList(); }
    }
    public record Period(String key, String title, List<Window> windows) {}
    public record Suggestions(List<Period> periods, List<Candidate> roster, List<Candidate> unmatched,
            ZonedDateTime asOf, LocalDateTime openingStart, LocalDateTime openingEnd, LocalDateTime closing,
            String hoursLabel, boolean noSchedules) {}
}
