package technology.renatus.routepulse.pickup;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.scheduling.automatic.*;

/** One routing decision is shared by the schedule, checkpoint and emails. Never changes driving assignments. */
@Service @Transactional(readOnly=true)
public class PickupAlerts {
    private final PickupShiftRepository shifts;
    private final PickupOutcomeRepository outcomes;
    private final AutomaticScheduleRepository schedules;
    private final DrivingSessionRepository sessions;
    private final DriverRepository drivers;
    private final PickupAccess access;
    private final OrganizationRepository organizations;
    private final ZoneId zone;
    public PickupAlerts(PickupShiftRepository shifts,PickupOutcomeRepository outcomes,AutomaticScheduleRepository schedules,
            DrivingSessionRepository sessions,DriverRepository drivers,PickupAccess access,OrganizationRepository organizations,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.shifts=shifts;this.outcomes=outcomes;this.schedules=schedules;this.sessions=sessions;
        this.drivers=drivers;this.access=access;this.organizations=organizations;this.zone=ZoneId.of(zone);
    }
    public List<Alert> forPage(Authentication auth,LocalDate date) {
        var scope=access.require(auth,false);return forDates(scope.org(),date,date,Instant.now());
    }
    public List<Alert> forDates(Long org,LocalDate from,LocalDate to,Instant now) {
        var start=from.atStartOfDay(zone).toInstant();var end=to.plusDays(1).atStartOfDay(zone).toInstant();
        // Read one extra day to merge contiguous UKG segments crossing midnight.
        var source=shifts.endingBetween(org,start,to.plusDays(2).atStartOfDay(zone).toInstant());
        var saved=outcomes.findAllByOrganizationIdAndClockOutGreaterThanEqualAndClockOutLessThan(org,start,end);
        var slots=schedules.findAllByOrganizationIdAndDateBetweenOrderByDate(org,from.minusDays(1),to.plusDays(1)).stream()
                .map(d->d.view(zone,null)).filter(d->"OPEN".equals(d.status())).flatMap(d->d.slots().stream())
                .sorted(Comparator.comparing(AutomaticScheduleDay.Slot::getStart)
                        .thenComparing(AutomaticScheduleDay.Slot::getEnd)
                        .thenComparing(s->s.getDriverId()==null?Long.MAX_VALUE:s.getDriverId())).toList();
        var active=sessions.findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc(org).orElse(null);
        var eligible=new HashSet<Long>();
        for(var driver:drivers.findAllByOrganization_IdOrderByNameAsc(org))
            if(driver.isActive() && (driver.getUser()==null || driver.getUser().isEnabled())) eligible.add(driver.getId());
        var result=new ArrayList<Alert>();
        for(var day=from;!day.isAfter(to);day=day.plusDays(1)) {
            for(var row:PickupStore.rows(source,day,zone,now)) {
                var status=saved.stream().filter(s->s.getRiderId().equals(row.riderId()) && s.getClockOut().equals(row.clockOut().toInstant()))
                        .map(PickupOutcome::getOutcome).findFirst().orElse(PickupOutcome.Status.PENDING);
                result.add(route(row,slots,active,eligible,status,zone,now));
            }
        }
        return List.copyOf(result);
    }
    static Alert route(PickupStore.Row row,List<AutomaticScheduleDay.Slot> slots,DrivingSession active,Set<Long> eligible,
            PickupOutcome.Status status,ZoneId zone,Instant now) {
        var time=row.clockOut().toInstant();
        var related=slots.stream().filter(s->near(s,time,zone)).toList();
        // Half-open intervals give a handoff at exactly 9:00 to the incoming 9:00 driver.
        var covering=related.stream().filter(s->contains(s,time,zone) && s.getDriverId()!=null && eligible.contains(s.getDriverId())).findFirst().orElse(null);
        Long owner=covering==null?null:covering.getDriverId();String name=covering==null?null:covering.getDriverName();
        if(covering!=null && active!=null && active.isActive() && eligible.contains(active.getDriver().getId())
                && !active.getStartedAt().isAfter(now) && active.getAutomaticEndAt().isAfter(time)
                && contains(covering,now,zone) && active.getCoveragePlan().stream().anyMatch(w->
                    !now.isBefore(w.getStart()) && now.isBefore(w.getEnd()) && !time.isBefore(w.getStart()) && time.isBefore(w.getEnd()))) {
            owner=active.getDriver().getId();name=active.getDriver().getName();
        }
        var nearest=related.stream().filter(s->s.getDriverId()!=null && eligible.contains(s.getDriverId()))
                .min(Comparator.comparingLong(s->distance(s,time,zone))).orElse(null);
        var recipient=owner!=null?owner:nearest==null?null:nearest.getDriverId();
        boolean due=status==PickupOutcome.Status.PENDING && !now.isBefore(time.minusSeconds(900)) && now.isBefore(time.plusSeconds(1800));
        return new Alert(row,status,owner,name,recipient,related,covering,due);
    }
    private static boolean contains(AutomaticScheduleDay.Slot s,Instant t,ZoneId z) {
        return !t.isBefore(s.getStart().atZone(z).toInstant()) && t.isBefore(s.getEnd().atZone(z).toInstant());
    }
    private static long distance(AutomaticScheduleDay.Slot s,Instant t,ZoneId z) {
        if(contains(s,t,z)) return 0;
        return Math.min(Math.abs(Duration.between(t,s.getStart().atZone(z).toInstant()).getSeconds()),
                Math.abs(Duration.between(t,s.getEnd().atZone(z).toInstant()).getSeconds()));
    }
    private static boolean near(AutomaticScheduleDay.Slot s,Instant t,ZoneId z) { return distance(s,t,z)<=900; }
    public List<Alert> forDriver(Long org,Long driver,Instant now) {
        if(drivers.findByIdAndOrganization_Id(driver,org).filter(d->d.isActive() && (d.getUser()==null || d.getUser().isEnabled())).isEmpty()) return List.of();
        var today=now.atZone(zone).toLocalDate();
        return forDates(org,today,today.plusDays(1),now).stream().filter(a->a.pending() && a.relatedTo(driver)
                && a.row().clockOut().toInstant().isAfter(now.minusSeconds(1800))
                && a.row().clockOut().toInstant().isBefore(now.plusSeconds(86400))).toList();
    }
    public List<Alert> forCheckpoint(Long org,Long driver,Instant now) {
        var today=now.atZone(zone).toLocalDate();
        return forDates(org,today,today.plusDays(1),now).stream().filter(a->a.pending()
                && a.row().clockOut().toInstant().isAfter(now.minusSeconds(1800))
                && (a.relatedTo(driver) || !a.row().clockOut().toInstant().isAfter(now.plusSeconds(900)))).toList();
    }
    @Transactional
    public void outcome(Authentication auth,Long rider,Instant clockOut,PickupOutcome.Status status) {
        var scope=access.require(auth,false);var now=Instant.now();
        organizations.findByIdForUpdate(scope.org()).orElseThrow();
        var day=clockOut.atZone(zone).toLocalDate();var today=now.atZone(zone).toLocalDate();
        if(day.isBefore(today.minusDays(7)) || day.isAfter(today.plusWeeks(6))) throw new IllegalArgumentException("This pickup is outside the editable date range.");
        if(forDates(scope.org(),day,day,now).stream().noneMatch(a->a.row().riderId().equals(rider) && a.row().clockOut().toInstant().equals(clockOut)))
            throw new IllegalArgumentException("This pickup has changed. Refresh the list before updating it.");
        var outcome=outcomes.findByOrganizationIdAndRiderIdAndClockOut(scope.org(),rider,clockOut)
                .orElseGet(()->new PickupOutcome(scope.org(),rider,clockOut));
        outcome.update(status,auth.getName(),now);outcomes.save(outcome);
    }
    public record Alert(PickupStore.Row row,PickupOutcome.Status status,Long driverId,String driverName,Long reminderDriverId,
            List<AutomaticScheduleDay.Slot> related,AutomaticScheduleDay.Slot covering,boolean due) {
        public boolean pending() { return status==PickupOutcome.Status.PENDING; }
        public boolean needsCoverage() { return driverId==null; }
        public boolean relatedTo(Long driver) { return driver!=null && (driver.equals(driverId) || related.stream().anyMatch(s->driver.equals(s.getDriverId()))); }
        public boolean nearSlot(AutomaticScheduleDay.Slot slot) { return !slot.equals(covering); }
        public String coverageLabel() { return needsCoverage()?"Needs coverage":"Pickup driver: "+driverName; }
    }
}
