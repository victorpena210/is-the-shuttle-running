package technology.renatus.routepulse.reporting.workload.service;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.model.UnscheduledDrivingRecord;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driver.service.DriverService;

/** Reports every actual session, including historical sessions linked to assignments. */
@Service
@Transactional(readOnly = true)
public class ActualDrivingRecordService {
    private final DrivingSessionRepository sessions;
    private final DriverService drivers;
    private final ZoneId zone;
    public ActualDrivingRecordService(DrivingSessionRepository sessions, DriverService drivers,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.sessions = sessions; this.drivers = drivers; this.zone = ZoneId.of(zone);
    }
    public LocalDate today() { return LocalDate.now(zone); }
    public String zoneName() { return zone.getId(); }

    public Report build(Long organizationId, LocalDate from, LocalDate to, Long driverId) {
        if (from == null || to == null || to.isBefore(from))
            throw new IllegalArgumentException("Choose an end date on or after the start date.");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 366)
            throw new IllegalArgumentException("Choose a range of up to one year.");
        if (driverId != null) drivers.getDriver(organizationId, driverId);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now();
        List<DrivingSession> matching = sessions.findOverlappingForServiceReview(organizationId, start, end)
                .stream().filter(s -> driverId == null || driverId.equals(s.getDriver().getId()))
                .sorted(Comparator.comparing(DrivingSession::getStartedAt).reversed()).toList();
        var totals = new LinkedHashMap<Long, DriverTotal>();
        var rows = new ArrayList<SessionRow>();
        for (var session : matching) {
            long seconds = secondsWithin(session, start, end, now);
            rows.add(new SessionRow(UnscheduledDrivingRecord.from(session, zone), seconds / 60));
            var driver = session.getDriver();
            var previous = totals.getOrDefault(driver.getId(), new DriverTotal(driver.getId(), driver.getName(), 0, 0));
            totals.put(driver.getId(), new DriverTotal(driver.getId(), driver.getName(),
                    previous.sessions() + 1, previous.seconds() + seconds));
        }
        var summary = totals.values().stream().sorted(Comparator.comparing(DriverTotal::name)).toList();
        return new Report(List.copyOf(rows), summary, summary.stream().mapToLong(DriverTotal::seconds).sum() / 60,
                matching.stream().filter(DrivingSession::isActive).count());
    }
    static long secondsWithin(DrivingSession session, Instant start, Instant end, Instant now) {
        Instant actualStart = session.getStartedAt().isBefore(start) ? start : session.getStartedAt();
        Instant actualEnd = session.getEndedAt() == null ? now : session.getEndedAt();
        if (actualEnd.isAfter(end)) actualEnd = end;
        return Math.max(0, Duration.between(actualStart, actualEnd).getSeconds());
    }
    public record SessionRow(UnscheduledDrivingRecord record, long minutesInRange) {}
    public record DriverTotal(Long id, String name, long sessions, long seconds) {
        public long minutes() { return seconds / 60; }
    }
    public record Report(List<SessionRow> rows, List<DriverTotal> drivers, long minutes, long activeSessions) {}
}
