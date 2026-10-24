package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.calendar.domain.*;
import technology.renatus.routepulse.calendar.repository.ParkOperatingDaySnapshotRepository;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleIdentityService;

/** Fetch the shared park calendar before acquiring the organization's planning lock. */
@Service
public class AutomaticScheduleService {
    private final ParkOperatingDayService calendar;
    private final ParkOperatingDaySnapshotRepository snapshots;
    private final AutomaticScheduleStore store;
    private final DriverScheduleIdentityService access;
    private final int parkId;
    private final ZoneId zone;
    public AutomaticScheduleService(ParkOperatingDayService calendar,ParkOperatingDaySnapshotRepository snapshots,
            AutomaticScheduleStore store,DriverScheduleIdentityService access,
            @Value("${sixflags.calendar.park-id:914}") int parkId,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.calendar=calendar; this.snapshots=snapshots; this.store=store; this.access=access; this.parkId=parkId; this.zone=ZoneId.of(zone);
    }
    public LocalDate today(){return LocalDate.now(zone);}
    public String zoneName(){return zone.getId();}
    public void refresh(Long org,String email){access.requireManager(org,email); refresh(org);}
    public void refresh(Long org) {
        Instant now=Instant.now(); var today=now.atZone(zone).toLocalDate();
        var days=new ArrayList<ParkOperatingDay>();
        for(int offset=0;offset<28;offset++) {
            var day=calendar.getOperatingDay(today.plusDays(offset));
            // Old persisted hours are useful for display, but cannot authorize fresh assignments indefinitely.
            if(day.isPersistedSnapshot() && snapshots.findByParkIdAndOperatingDate(parkId,day.date())
                    .filter(s->s.getFetchedAt().plus(Duration.ofHours(24)).isAfter(now)).isEmpty())
                day=new ParkOperatingDay(day.date(),ParkOperatingStatus.UNKNOWN,null,null,false,"STALE_SNAPSHOT","PARK_HOURS_STALE");
            days.add(day);
        }
        store.refresh(org,days,now);
    }
}
