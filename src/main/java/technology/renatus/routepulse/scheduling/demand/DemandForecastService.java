package technology.renatus.routepulse.scheduling.demand;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.repository.*;
import technology.renatus.routepulse.organization.service.OrganizationActorService;
import technology.renatus.routepulse.scheduling.importing.DayforceCsvParser;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.repository.ScheduleImportRepository;
import technology.renatus.routepulse.scheduling.planning.transportation.*;

/** Demand only: importing a schedule never creates or modifies driver assignments or sessions. */
@Service
@Transactional(readOnly = true)
public class DemandForecastService {
    private final DemandForecastRepository windows;
    private final ScheduleImportRepository imports;
    private final DayforceCsvParser parser;
    private final OrganizationRepository organizations;
    private final OrganizationActorService actors;
    private final OrganizationMembershipRepository memberships;
    private final TransportationDemandBucketizer periods;
    private final ZoneId zone;
    private final LocalTime morningStart, middayStart, eveningStart, operatingEnd;
    private final int historyMonths;

    public DemandForecastService(DemandForecastRepository windows, ScheduleImportRepository imports,
            DayforceCsvParser parser, OrganizationRepository organizations, OrganizationActorService actors,
            OrganizationMembershipRepository memberships,
            @Value("${app.operations-zone:America/Chicago}") String zone,
            @Value("${app.transportation-demand.operating-start:09:00}") String morning,
            @Value("${app.transportation-demand.midday-start:12:00}") String midday,
            @Value("${app.transportation-demand.evening-start:17:00}") String evening,
            @Value("${app.transportation-demand.operating-end:23:59}") String end,
            @Value("${app.retention.forecast-history-months:12}") int historyMonths) {
        this.windows = windows; this.imports = imports; this.parser = parser; this.organizations = organizations;
        this.actors = actors; this.memberships = memberships; this.zone = ZoneId.of(zone);
        morningStart = LocalTime.parse(morning); middayStart = LocalTime.parse(midday);
        eveningStart = LocalTime.parse(evening); operatingEnd = LocalTime.parse(end);
        periods = new TransportationDemandBucketizer(morningStart, middayStart, eveningStart, operatingEnd, 15);
        this.historyMonths = Math.max(1, historyMonths);
    }
    public LocalDate today() { return LocalDate.now(zone); }
    public String zoneName() { return zone.getId(); }

    @Transactional
    public ScheduleImport upload(Long organizationId, String email, MultipartFile file) {
        var actor = actors.loadActor(organizationId, email);
        var membership = memberships.findByOrganization_IdAndUser_Id(organizationId, actor.getId()).orElseThrow(
                () -> new AccessDeniedException("Organization access is required."));
        if (!actor.isEnabled() || !Set.of(OrganizationRole.OWNER, OrganizationRole.ADMIN, OrganizationRole.DISPATCHER)
                .contains(membership.getRole())) throw new AccessDeniedException("Manager access is required to upload demand.");
        var shifts = parser.parse(file);
        var dates = shifts.stream().map(s -> s.getShiftDate()).distinct().sorted().toList();
        if (dates.size() > 90) throw new IllegalStateException("Upload no more than 90 work dates at a time.");
        var organization = organizations.findByIdForUpdate(organizationId).orElseThrow(
                () -> new IllegalStateException("The organization could not be found."));
        var source = imports.save(new ScheduleImport(organization, actor, UUID.randomUUID().toString(),
                filename(file), shifts.size(), dates.get(0), dates.get(dates.size() - 1),
                dates.stream().map(LocalDate::toString).collect(Collectors.joining(",")), Instant.now()));
        var counts = new HashMap<Key, People>();
        for (var shift : shifts) {
            LocalDate endDate = shift.getEndTime().isAfter(shift.getStartTime())
                    ? shift.getShiftDate() : shift.getShiftDate().plusDays(1);
            add(counts, shift.getShiftDate(), shift.getShiftDate(), shift.getStartTime(), true, shift.getEmployeeId());
            add(counts, shift.getShiftDate(), endDate, shift.getEndTime(), false, shift.getEmployeeId());
        }
        // Delete and insert under the organization lock in one transaction. Re-uploading a date
        // replaces its entire forecast, including overnight departures on the following date.
        windows.replaceDates(organizationId, dates);
        windows.saveAll(counts.entrySet().stream().map(entry -> {
            var key = entry.getKey(); var people = entry.getValue();
            return new DemandForecastWindow(organization, source, key.sourceDate(), key.eventDate(), key.period(),
                    key.start(), people.ins.size(), people.outs.size());
        }).toList());
        return source;
    }

    private void add(Map<Key, People> counts, LocalDate sourceDate, LocalDate eventDate, LocalTime time,
            boolean arrival, String employeeId) {
        var key = new Key(sourceDate, eventDate, periods.periodFor(time, arrival), floor(time));
        var people = counts.computeIfAbsent(key, ignored -> new People());
        (arrival ? people.ins : people.outs).add(employeeId);
    }

    public Forecast page(Long organizationId, LocalDate date) {
        var records = windows.forDate(organizationId, date);
        var byPeriod = new EnumMap<TransportationPeriodType, TreeMap<LocalTime, int[]>>(TransportationPeriodType.class);
        for (var type : TransportationPeriodType.values()) byPeriod.put(type, new TreeMap<>());
        // Include empty quarter-hours so managers can see quiet stretches, too.
        for (int minute = 0; minute < 1440; minute++) for (boolean arrival : List.of(true, false)) {
            LocalTime time = LocalTime.of(minute / 60, minute % 60);
            byPeriod.get(periods.periodFor(time, arrival)).putIfAbsent(floor(time), new int[2]);
        }
        for (var record : records) {
            var counts = byPeriod.get(record.getPeriod()).computeIfAbsent(record.getWindowStart(), ignored -> new int[2]);
            counts[0] += record.getClockIns(); counts[1] += record.getClockOuts();
        }
        var summaries = new EnumMap<TransportationPeriodType, Period>(TransportationPeriodType.class);
        for (var type : TransportationPeriodType.values()) {
            var rows = byPeriod.get(type).entrySet().stream().map(e -> new Window(date.atTime(e.getKey()),
                    date.atTime(e.getKey()).plusMinutes(15), e.getValue()[0], e.getValue()[1])).toList();
            summaries.put(type, new Period(type.key(), type.title(), range(type), rows,
                    rows.stream().mapToInt(Window::clockIns).sum(), rows.stream().mapToInt(Window::clockOuts).sum()));
        }
        var all = new TreeMap<LocalTime, int[]>();
        for (var record : records) {
            var count = all.computeIfAbsent(record.getWindowStart(), ignored -> new int[2]);
            count[0] += record.getClockIns(); count[1] += record.getClockOuts();
        }
        var peak = all.entrySet().stream().sorted(Comparator.<Map.Entry<LocalTime, int[]>>comparingInt(
                e -> e.getValue()[0] + e.getValue()[1]).reversed()).findFirst()
                .map(e -> new Window(date.atTime(e.getKey()), date.atTime(e.getKey()).plusMinutes(15), e.getValue()[0], e.getValue()[1]))
                .orElse(null);
        var latest = records.stream().map(DemandForecastWindow::getSourceImport)
                .max(Comparator.comparing(ScheduleImport::getUploadedAt)).orElse(null);
        return new Forecast(date, !records.isEmpty(), windows.availableDates(organizationId),
                List.of(summaries.get(TransportationPeriodType.MORNING), summaries.get(TransportationPeriodType.MIDDAY),
                        summaries.get(TransportationPeriodType.EVENING)), summaries.get(TransportationPeriodType.OFF_HOURS),
                records.stream().mapToInt(DemandForecastWindow::getClockIns).sum(),
                records.stream().mapToInt(DemandForecastWindow::getClockOuts).sum(), peak,
                latest == null ? null : latest.getUploadedAt().atZone(zone), latest == null ? null : latest.getOriginalFilename());
    }

    @Scheduled(cron = "${app.retention.cron:0 15 2 * * *}", zone = "${app.operations-zone:America/Chicago}")
    @Transactional
    public void purgeExpiredForecasts() {
        // Preserve the prior work date's overnight contribution at the retention boundary.
        windows.purgeBefore(today().minusMonths(historyMonths).minusDays(1));
    }

    private LocalTime floor(LocalTime time) { return time.withMinute(time.getMinute() / 15 * 15).withSecond(0).withNano(0); }
    private String range(TransportationPeriodType type) {
        var format = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
        return switch (type) {
            case MORNING -> morningStart.format(format) + " – " + middayStart.format(format);
            case MIDDAY -> middayStart.format(format) + " – " + eveningStart.format(format);
            case EVENING -> eveningStart.format(format) + " – " + operatingEnd.format(format);
            case OFF_HOURS -> "Outside " + morningStart.format(format) + " – " + operatingEnd.format(format);
        };
    }
    private String filename(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "schedule.csv" : file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        return name.isBlank() ? "schedule.csv" : name.substring(0, Math.min(name.length(), 255));
    }
    private record Key(LocalDate sourceDate, LocalDate eventDate, TransportationPeriodType period, LocalTime start) {}
    private static final class People { final Set<String> ins = new HashSet<>(), outs = new HashSet<>(); }
    public record Window(LocalDateTime start, LocalDateTime end, int clockIns, int clockOuts) {
        public int movements() { return clockIns + clockOuts; }
        public boolean endsNextDay() { return end.toLocalDate().isAfter(start.toLocalDate()); }
    }
    public record Period(String key, String title, String range, List<Window> windows, int clockIns, int clockOuts) {}
    public record Forecast(LocalDate date, boolean available, List<LocalDate> dates, List<Period> periods, Period offHours,
            int clockIns, int clockOuts, Window peak, ZonedDateTime uploadedAt, String filename) {}
}
