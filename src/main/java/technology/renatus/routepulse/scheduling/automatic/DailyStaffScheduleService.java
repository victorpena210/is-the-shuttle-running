package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.calendar.DriverCalendarAvailability;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleWindow;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleWindowRepository;

/** Saved roster work hours, using the same calendar precedence as automatic planning. */
@Service
@Transactional(readOnly = true)
public class DailyStaffScheduleService {
    private final DriverRepository drivers;
    private final DriverScheduleWindowRepository schedules;
    private final DriverCalendarAvailability calendars;

    public DailyStaffScheduleService(DriverRepository drivers, DriverScheduleWindowRepository schedules,
            DriverCalendarAvailability calendars) {
        this.drivers = drivers;
        this.schedules = schedules;
        this.calendars = calendars;
    }

    public Map<LocalDate, List<WorkShift>> byDay(Long org, LocalDate from, LocalDate to, Long personalDriver, Instant now) {
        var roster = drivers.findAllByOrganization_IdAndActiveTrueOrderByNameAsc(org).stream()
                .filter(d -> d.getUser() == null || d.getUser().isEnabled())
                .filter(d -> personalDriver == null || personalDriver.equals(d.getId())).toList();
        var uploaded = schedules.overlapping(org, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        var result = new LinkedHashMap<LocalDate, List<WorkShift>>();
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var start = date.atStartOfDay();
            var end = date.plusDays(1).atStartOfDay();
            var work = uploaded.stream().filter(w -> w.getStartsAt().isBefore(end) && w.getEndsAt().isAfter(start))
                    .collect(Collectors.groupingBy(w -> w.getDriver().getId()));
            // A connected UKG calendar replaces uploads, including days without a shift and stale calendars.
            calendars.apply(org, date, now, work);
            var rows = new ArrayList<WorkShift>();
            for (var driver : roster) {
                WorkShift previous = null;
                for (var window : work.getOrDefault(driver.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(DriverScheduleWindow::getStartsAt)).toList()) {
                    if (!window.getStartsAt().isBefore(end) || !window.getEndsAt().isAfter(start)) continue;
                    if (previous != null && !window.getStartsAt().isAfter(previous.end())) {
                        if (window.getEndsAt().isAfter(previous.end()))
                            previous = new WorkShift(driver.getId(), driver.getName(), previous.start(), window.getEndsAt());
                    } else {
                        if (previous != null) rows.add(previous);
                        previous = new WorkShift(driver.getId(), driver.getName(), window.getStartsAt(), window.getEndsAt());
                    }
                }
                if (previous != null) rows.add(previous);
            }
            rows.sort(Comparator.comparing(WorkShift::start).thenComparing(WorkShift::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(WorkShift::driverId));
            result.put(date, List.copyOf(rows));
        }
        return Collections.unmodifiableMap(result);
    }

    public record WorkShift(Long driverId, String name, LocalDateTime start, LocalDateTime end) { }
}
