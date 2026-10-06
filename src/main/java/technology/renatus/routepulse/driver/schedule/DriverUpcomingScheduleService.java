package technology.renatus.routepulse.driver.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/** Read-only personal schedule: viewing it never generates or changes assignments. */
@Service
@Transactional(readOnly = true)
public class DriverUpcomingScheduleService {
    public record DateRange(LocalDate from, LocalDate to) {
        public static DateRange resolve(LocalDate from, LocalDate to, LocalDate today) {
            if (from == null && to == null) return new DateRange(today, today.plusDays(27));
            if (from == null || to == null) throw new IllegalArgumentException("Choose both a start and end date.");
            if (from.isBefore(today)) throw new IllegalArgumentException("Choose today or a future start date.");
            if (to.isBefore(from)) throw new IllegalArgumentException("The end date must be on or after the start date.");
            if (ChronoUnit.DAYS.between(from, to) >= 93) throw new IllegalArgumentException("Choose up to 93 days at a time.");
            return new DateRange(from, to);
        }
    }
    public record TimeWindow(LocalTime start, LocalTime end) { }
    public record AssignmentRow(LocalDate date, String period, List<TimeWindow> windows,
            List<TimeWindow> breaks, String status) { }
    public record ScheduleView(DateRange dates, List<AssignmentRow> assignments, int uploadedDays) { }

    private final DriverPortalAccessService access;
    private final DriverAssignmentRepository assignments;
    private final UploadedShiftRepository shifts;
    private final ZoneId zone;

    public DriverUpcomingScheduleService(DriverPortalAccessService access, DriverAssignmentRepository assignments,
            UploadedShiftRepository shifts, @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.access = access; this.assignments = assignments; this.shifts = shifts;
        this.zone = ZoneId.of(operationsZone);
    }

    public LocalDate today() { return LocalDate.now(zone); }
    public String zoneName() { return zone.getId(); }

    public ScheduleView upcoming(Authentication authentication, DateRange dates) {
        DateRange.resolve(dates.from(), dates.to(), today());
        var driver = access.requireCurrentDriver(authentication);
        Long organizationId = driver.getOrganization().getId();
        var rows = assignments.findAllByOrganization_IdAndScheduledDriver_IdAndAssignmentDateBetween(
                        organizationId, driver.getId(), dates.from(), dates.to()).stream()
                .filter(a -> a.getAssignmentOutcome() == AssignmentOutcome.PENDING
                        || a.getAssignmentOutcome() == AssignmentOutcome.IN_PROGRESS)
                .sorted(Comparator.comparing(DriverAssignment::getAssignmentDate)
                        .thenComparing(DriverAssignment::getScheduledStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(a -> a.getPeriod().ordinal()))
                .map(DriverUpcomingScheduleService::row).toList();
        int uploadedDays = (int) shifts.findAvailableDates(organizationId).stream().distinct()
                .filter(d -> !d.isBefore(dates.from()) && !d.isAfter(dates.to())).count();
        return new ScheduleView(dates, rows, uploadedDays);
    }

    private static AssignmentRow row(DriverAssignment assignment) {
        List<TimeWindow> windows = assignment.getPlannedDrivingWindows().stream()
                .map(w -> new TimeWindow(w.getStartTime(), w.getEndTime())).toList();
        if (windows.isEmpty() && assignment.getScheduledStartTime() != null && assignment.getScheduledEndTime() != null) {
            windows = List.of(new TimeWindow(assignment.getScheduledStartTime(), assignment.getScheduledEndTime()));
        }
        var breaks = assignment.getPeriod() == ShuttlePeriod.MIDDAY ? List.<TimeWindow>of()
                : assignment.getPlannedBreakWindows().stream()
                    .map(w -> new TimeWindow(w.getStartTime(), w.getEndTime())).toList();
        String period = switch (assignment.getPeriod()) {
            case MORNING -> "Morning"; case MIDDAY -> "Midday"; case AFTERNOON -> "Evening";
        };
        return new AssignmentRow(assignment.getAssignmentDate(), period, windows, breaks,
                assignment.getAssignmentOutcome() == AssignmentOutcome.IN_PROGRESS ? "In progress" : "Scheduled");
    }
}
