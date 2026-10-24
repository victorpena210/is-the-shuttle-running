package technology.renatus.routepulse.driver.calendar;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleWindow;

@Service @Transactional(readOnly=true)
public class DriverCalendarAvailability {
    private final DriverCalendarConnectionRepository connections;private final DriverCalendarShiftRepository shifts;private final ZoneId zone;
    public DriverCalendarAvailability(DriverCalendarConnectionRepository connections,DriverCalendarShiftRepository shifts,
            @Value("${app.operations-zone:America/Chicago}") String zone) {this.connections=connections;this.shifts=shifts;this.zone=ZoneId.of(zone);}
    /** Connected calendars replace CSV availability, including explicit days off. */
    public void apply(Long org,LocalDate date,Instant now,Map<Long,List<DriverScheduleWindow>> work) {
        apply(org,date,date.plusDays(1).atStartOfDay(),now,work);
    }
    /** Include post-midnight starts when the park's operating day ends on the following date. */
    public void apply(Long org,LocalDate date,LocalDateTime until,Instant now,Map<Long,List<DriverScheduleWindow>> work) {
        var rows=shifts.overlapping(org,date.atStartOfDay(zone).toInstant(),until.atZone(zone).toInstant())
                .stream().collect(Collectors.groupingBy(s -> s.getConnection().getId()));
        for(var c:connections.findAllByOrganizationId(org)) {
            if(date.isBefore(c.getCoverageFrom())) continue;
            var windows=new ArrayList<DriverScheduleWindow>();
            if(c.fresh(now)&&date.isBefore(c.getCoverageUntil())) for(var shift:rows.getOrDefault(c.getId(),List.of())) {
                // Transient adapter to the existing coverage calculator; never persisted as a CSV row.
                windows.add(new DriverScheduleWindow(c.getDriver(),null,shift.getStartsAt().atZone(zone).toLocalDateTime(),shift.getEndsAt().atZone(zone).toLocalDateTime()));
            }
            work.put(c.getDriver().getId(),windows);
        }
    }
    public List<State> states(Long org) {return connections.findAllByOrganizationId(org).stream().map(c -> new State(c.getDriver().getId(),c.getDriver().getName(),
            c.getLastSyncedAt().atZone(zone),!c.fresh(Instant.now()),c.getLastError()!=null)).toList();}
    public record State(Long driverId,String name,ZonedDateTime lastSynced,boolean stale,boolean retrying) { }
}
