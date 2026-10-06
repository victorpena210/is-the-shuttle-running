package technology.renatus.routepulse.reporting.history;

import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.assignments.service.planning.MorningStaffingPolicy;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.repository.ParkCalendarOverrideRepository;
import technology.renatus.routepulse.calendar.repository.ParkOperatingDaySnapshotRepository;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;
import technology.renatus.routepulse.scheduling.importing.CsvScheduleProvider;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;
import technology.renatus.routepulse.scheduling.retention.repository.HistoricalDemandSnapshotRepository;
import static technology.renatus.routepulse.reporting.history.ServiceReviewCalculator.*;

@Service
public class ServiceHistoryService {
    private final DrivingSessionRepository sessions;
    private final PassengerRideConfirmationRepository confirmations;
    private final DriverAssignmentRepository assignments;
    private final DriverRepository drivers;
    private final CsvScheduleProvider schedules;
    private final TransportationDemandService demandService;
    private final HistoricalDemandSnapshotRepository snapshots;
    private final ParkOperatingDaySnapshotRepository calendars;
    private final ParkCalendarOverrideRepository overrides;
    private final ParkOperatingDayService calendarService;
    private final ZoneId zone;
    private final int parkId;
    private final int windowMinutes;
    private final LocalTime morningStart, middayStart, eveningStart, operatingEnd;
    private final ServiceReviewCalculator calculator;
    private final DriverScheduleService driverScheduleService;
    private final technology.renatus.routepulse.wardrobe.WardrobeHoursService wardrobeHoursService;

    public ServiceHistoryService(DrivingSessionRepository sessions, PassengerRideConfirmationRepository confirmations,
            DriverAssignmentRepository assignments, DriverRepository drivers, CsvScheduleProvider schedules,
            TransportationDemandService demandService, HistoricalDemandSnapshotRepository snapshots,
            ParkOperatingDaySnapshotRepository calendars, ParkCalendarOverrideRepository overrides,
            ParkOperatingDayService calendarService,
            DriverScheduleService driverScheduleService,
            technology.renatus.routepulse.wardrobe.WardrobeHoursService wardrobeHoursService,
            @Value("${app.operations-zone:America/Chicago}") String zone,
            @Value("${sixflags.calendar.park-id:914}") int parkId,
            @Value("${app.service-verification.minimum-demand-for-review:10}") int minimumDemand,
            @Value("${app.transportation-demand.window-minutes:15}") int windowMinutes,
            @Value("${app.transportation-demand.operating-start:09:00}") String morningStart,
            @Value("${app.transportation-demand.midday-start:12:00}") String middayStart,
            @Value("${app.transportation-demand.evening-start:17:00}") String eveningStart,
            @Value("${app.transportation-demand.operating-end:21:00}") String operatingEnd) {
        this.driverScheduleService = driverScheduleService;
        this.wardrobeHoursService = wardrobeHoursService;
        this.sessions = sessions; this.confirmations = confirmations; this.assignments = assignments;
        this.drivers = drivers; this.schedules = schedules; this.demandService = demandService;
        this.snapshots = snapshots; this.calendars = calendars; this.overrides = overrides;
        this.calendarService = calendarService; this.zone = ZoneId.of(zone); this.parkId = parkId;
        this.windowMinutes = windowMinutes; this.morningStart = LocalTime.parse(morningStart);
        this.middayStart = LocalTime.parse(middayStart); this.eveningStart = LocalTime.parse(eveningStart);
        this.operatingEnd = LocalTime.parse(operatingEnd);
        this.calculator = new ServiceReviewCalculator(this.zone, minimumDemand, windowMinutes);
    }
    public LocalDate today() { return LocalDate.now(zone); }
    public String zoneName() { return zone.getId(); }

    @Transactional
    public Report build(Long organizationId, ServiceReviewFilters filters) {
        if (filters.driverId() != null && drivers.findByIdAndOrganization_Id(filters.driverId(), organizationId).isEmpty())
            throw new IllegalArgumentException("Choose a driver from your organization.");
        Instant now = Instant.now();
        Instant start = filters.from().atStartOfDay(zone).toInstant();
        Instant end = filters.to().plusDays(1).atStartOfDay(zone).toInstant();
        List<Activity> activity = sessions.findOverlappingForServiceReview(organizationId, start, end).stream()
                .map(s -> new Activity(s.getId(), s.getDriver().getId(), s.getDriver().getName(),
                        s.isUnscheduled(), s.getStartedAt(), s.getEndedAt())).toList();
        List<Ride> rides = confirmations.findAllForServiceVerification(organizationId, start, end).stream()
                .map(r -> new Ride(r.getId(), r.getDriver().getId(), r.getDriverNameSnapshot(), r.getSubmittedAt())).toList();
        List<DriverAssignment> planned = assignments.findAllByOrganization_IdAndAssignmentDateBetween(
                organizationId, filters.from(), filters.to());
        Set<LocalDate> available = new HashSet<>(schedules.getAvailableDates(organizationId));
        List<Row> rows = new ArrayList<>();
        for (LocalDate date = filters.from(); !date.isAfter(filters.to()); date = date.plusDays(1)) {
            Map<String, List<Demand>> demand = new LinkedHashMap<>();
            for (String key : List.of("morning", "midday", "evening", "off-hours")) demand.put(key, new ArrayList<>());
            String source = "No schedule available";
            Map<String, Integer> archivedTotals = new HashMap<>();
            if (available.contains(date)) {
                TransportationDemandAnalysis analysis = demandService.getDemandForDate(organizationId, date);
                addDemand(demand.get("morning"), analysis.getMorning().getWindows(), date);
                addDemand(demand.get("midday"), analysis.getMidday().getWindows(), date);
                addDemand(demand.get("evening"), analysis.getEvening().getWindows(), date);
                addDemand(demand.get("off-hours"), analysis.getOffHoursWindows(), date);
                source = "Uploaded schedule";
            } else {
                var snapshot = snapshots.findByOrganization_IdAndOperatingDate(organizationId, date);
                if (snapshot.isPresent()) {
                    source = "Archived schedule totals";
                    var saved = snapshot.get();
                    archivedTotals.put("morning", saved.getMorningDemandCount());
                    archivedTotals.put("midday", saved.getMiddayDemandCount());
                    archivedTotals.put("evening", saved.getEveningDemandCount());
                    archivedTotals.put("off-hours", saved.getOffHoursMovementCount());
                    // Retained windows combine arrivals/departures at period boundaries.
                    // Use authoritative period totals; do not reconstruct precise historical demand timing.
                }
            }
            String calendar = calendar(organizationId, date);
            LocalTime closingTime = wardrobeHoursService.getHours(organizationId, date).closingTime();
            LocalDate staffingDate = date;
            boolean morningStaffed = !available.contains(date) || MorningStaffingPolicy
                    .hasMorningStaffing(driverScheduleService.getDriverSchedulesForDate(organizationId, date).scheduledDrivers(),
                            planned.stream().filter(a -> a.getAssignmentDate().equals(staffingDate)).toList());
            for (String key : demand.keySet()) {
                if (!"all".equals(filters.period()) && !key.equals(filters.period())) continue;
                List<Demand> windows = demand.get(key).stream().filter(d -> d.count() > 0).toList();
                LocalDate day = date;
                List<DriverAssignment> scheduledAssignments = planned.stream()
                        .filter(a -> a.getAssignmentDate().equals(day) && key.equals(periodKey(a))).toList();
                List<Assignment> periodAssignments = scheduledAssignments.stream()
                        .map(a -> new Assignment(a.getId(), a.getScheduledDriver() == null ? null : a.getScheduledDriver().getId(),
                                a.getScheduledDriver() == null ? "Unassigned" : a.getScheduledDriver().getName(), a.getAssignmentOutcome().name())).toList();
                List<Span> scopes = new ArrayList<>(scopes(date, key));
                windows.forEach(d -> scopes.add(d.span()));
                scheduledAssignments.forEach(a -> addAssignmentScopes(scopes, day, a));
                Instant eveningNotBefore = null;
                if ("evening".equals(key)) {
                    eveningNotBefore = (closingTime.equals(LocalTime.MIDNIGHT) ? day.plusDays(1) : day)
                            .atTime(closingTime).atZone(zone).toInstant();
                    // An explicitly preserved earlier assignment still has its promised service reviewed.
                    for (var assignment : scheduledAssignments) {
                        if (assignment.getScheduledStartTime() != null) {
                            Instant promised = day.atTime(assignment.getScheduledStartTime()).atZone(zone).toInstant();
                            if (promised.isBefore(eveningNotBefore)) eveningNotBefore = promised;
                        }
                    }
                }
                Row row = calculator.calculate(new Input(date, key, title(key), calendar, source, scopes, windows,
                        periodAssignments, activity, rides,
                        "morning".equals(key) && periodAssignments.isEmpty() && !morningStaffed
                                ? "Morning not assigned—wardrobe office coverage required" : null, eveningNotBefore), filters.driverId(), filters.driverRole(), now);
                if (row == null) continue;
                if (archivedTotals.containsKey(key)) row = row.withDemandTotal(archivedTotals.get(key));
                if ("off-hours".equals(key) && row.demand() == 0 && row.seconds() == 0 && row.confirmations() == 0) continue;
                if (ServiceReviewCalculator.matches(row, filters.result())) rows.add(row);
            }
        }
        return ServiceReviewCalculator.summarize(rows, now);
    }
    private String calendar(Long organizationId, LocalDate date) {
        var override = overrides.findFirstByOrganizationIdAndOperatingDateOrderByConfirmedAtDescIdDesc(organizationId, date);
        if (override.isPresent() && override.get().getOverrideStatus() == ParkOperatingStatus.OPEN) return "Manager confirmed open";
        var stored = calendars.findByParkIdAndOperatingDate(parkId, date);
        ParkOperatingStatus status;
        if (date.isBefore(today())) status = stored.map(s -> s.getOperatingStatus()).orElse(ParkOperatingStatus.UNKNOWN);
        else status = calendarService.getOperatingDay(date).status();
        return switch (status) { case OPEN -> "Open"; case CLOSED -> "Park closed"; case UNKNOWN -> "Calendar unverified"; };
    }
    private void addDemand(List<Demand> target, List<TransportationDemandWindow> windows, LocalDate date) {
        windows.stream().filter(w -> w.getDemandCount() > 0).forEach(w ->
                target.add(new Demand(span(date, w.getWindowStart(), w.getWindowEnd()), w.getDemandCount())));
    }
    private Span span(LocalDate date, LocalTime start, LocalTime end) {
        return new Span(date.atTime(start).atZone(zone).toInstant(),
                (end.isAfter(start) ? date : date.plusDays(1)).atTime(end).atZone(zone).toInstant());
    }
    private List<Span> scopes(LocalDate date, String key) {
        return switch (key) {
            case "morning" -> List.of(span(date, morningStart, middayStart));
            case "midday" -> List.of(span(date, middayStart, eveningStart));
            case "evening" -> List.of(span(date, eveningStart, operatingEnd));
            default -> {
                List<Span> spans = new ArrayList<>();
                if (!morningStart.equals(LocalTime.MIDNIGHT)) spans.add(span(date, LocalTime.MIDNIGHT, morningStart));
                spans.add(span(date, operatingEnd, LocalTime.MIDNIGHT));
                yield spans;
            }
        };
    }
    private void addAssignmentScopes(List<Span> scopes, LocalDate date, DriverAssignment assignment) {
        if (assignment.hasPlannedDrivingWindows()) {
            assignment.getPlannedDrivingWindows().forEach(w -> addAssignmentScope(scopes, date, w.getStartTime(), w.getEndTime()));
        } else addAssignmentScope(scopes, date, assignment.getScheduledStartTime(), assignment.getScheduledEndTime());
    }
    private void addAssignmentScope(List<Span> scopes, LocalDate date, LocalTime start, LocalTime end) {
        if (start == null || end == null) return;
        Span planned = span(date, start, end);
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        scopes.add(new Span(planned.start(), planned.end().isAfter(dayEnd) ? dayEnd : planned.end()));
    }
    private String periodKey(DriverAssignment a) {
        return switch (a.getPeriod()) { case MORNING -> "morning"; case MIDDAY -> "midday"; case AFTERNOON -> "evening"; };
    }
    private String title(String key) {
        return switch (key) { case "morning" -> "Morning"; case "midday" -> "Midday"; case "evening" -> "Evening"; default -> "Off-hours"; };
    }
}
