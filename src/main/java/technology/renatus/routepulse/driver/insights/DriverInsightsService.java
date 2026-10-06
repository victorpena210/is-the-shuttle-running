package technology.renatus.routepulse.driver.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.service.ParkCalendarOverrideService;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.importing.CsvScheduleProvider;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;

@Service
public class DriverInsightsService {
    public record DemandView(LocalDate date, boolean scheduleAvailable, Instant uploadedAt,
            String calendar, TransportationDemandAnalysis analysis) { }

    private final DriverPortalAccessService access;
    private final DrivingSessionRepository sessions;
    private final PassengerRideConfirmationRepository confirmations;
    private final CsvScheduleProvider schedules;
    private final TransportationDemandService demand;
    private final ParkOperatingDayService calendar;
    private final ParkCalendarOverrideService overrides;
    private final ZoneId zone;

    public DriverInsightsService(DriverPortalAccessService access, DrivingSessionRepository sessions,
            PassengerRideConfirmationRepository confirmations, CsvScheduleProvider schedules,
            TransportationDemandService demand, ParkOperatingDayService calendar,
            ParkCalendarOverrideService overrides,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.access = access; this.sessions = sessions; this.confirmations = confirmations;
        this.schedules = schedules; this.demand = demand; this.calendar = calendar;
        this.overrides = overrides; this.zone = ZoneId.of(zone);
    }

    public LocalDate today() { return LocalDate.now(zone); }
    public String zoneName() { return zone.getId(); }

    public DemandView todayDemand(Authentication authentication) {
        var driver = access.requireCurrentDriver(authentication);
        Long organizationId = driver.getOrganization().getId();
        LocalDate date = today();
        boolean available = schedules.getAvailableDates(organizationId).contains(date);
        var latestImport = schedules.getLatestImportForDate(organizationId, date);
        var override = overrides.findLatest(organizationId, date);
        String calendarLabel;
        if (override.isPresent() && override.get().getOverrideStatus() == ParkOperatingStatus.OPEN) {
            calendarLabel = "Manager confirmed open";
        } else {
            calendarLabel = switch (calendar.getOperatingDay(date).status()) {
                case OPEN -> "Open";
                case CLOSED -> "Park closed";
                case UNKNOWN -> "Calendar unverified";
            };
        }
        return new DemandView(date, available, latestImport.map(i -> i.getUploadedAt()).orElse(null),
                calendarLabel, available ? demand.getDemandForDate(organizationId, date) : TransportationDemandAnalysis.empty());
    }

    /** Identity comes exclusively from the authenticated account, never request parameters. */
    @Transactional(readOnly = true)
    public DriverAnalyticsCalculator.Report analytics(Authentication authentication, DriverAnalyticsRange range) {
        var driver = access.requireCurrentDriver(authentication);
        Long organizationId = driver.getOrganization().getId();
        Instant now = Instant.now();
        Instant start = range.from().atStartOfDay(zone).toInstant();
        Instant end = range.to().plusDays(1).atStartOfDay(zone).toInstant();
        if (end.isAfter(now)) end = now;
        List<DriverAnalyticsCalculator.Session> activity = sessions.findPersonalAnalyticsSessions(
                organizationId, driver.getId(), start, end).stream().map(s -> {
                    String type = s.isUnscheduled() ? "Unscheduled"
                            : Objects.equals(s.getAssignment().getScheduledDriver().getId(), driver.getId())
                                    ? "Scheduled" : "Coverage";
                    String period = s.isUnscheduled() ? "—" : s.getAssignment().getPeriodLabel();
                    return new DriverAnalyticsCalculator.Session(s.getStartedAt(), s.getEndedAt(), type,
                            period, s.isUnscheduled(), status(s));
                }).toList();
        var rides = confirmations.findPersonalConfirmationTimes(organizationId, driver.getId(), start, end);
        return DriverAnalyticsCalculator.calculate(range, zone, now, activity, rides);
    }

    private static String status(DrivingSession session) {
        if (session.isActive()) return "Recording in progress";
        if (session.getEndReason() == null) return "Ended";
        return switch (session.getEndReason()) {
            case PAUSE -> "Paused"; case HANDOFF -> "Handed off"; case COMPLETED -> "Ended";
        };
    }
}
