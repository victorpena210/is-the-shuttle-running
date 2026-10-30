package technology.renatus.routepulse.pickup;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.calendar.*;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Service @Transactional(readOnly=true)
public class PickupStore {
    private final PickupAccess access;
    private final PickupRiderRepository riders;
    private final PickupShiftRepository shifts;
    private final OrganizationRepository organizations;
    private final CalendarLinkCipher cipher;
    private final ZoneId zone;
    public PickupStore(PickupAccess access,PickupRiderRepository riders,PickupShiftRepository shifts,
            OrganizationRepository organizations,CalendarLinkCipher cipher,@Value("${app.operations-zone:America/Chicago}") String zone) {
        this.access=access;this.riders=riders;this.shifts=shifts;this.organizations=organizations;this.cipher=cipher;this.zone=ZoneId.of(zone);
    }
    public void authorizeWrite(Authentication auth,Long id) {
        var scope=access.require(auth,true);if(id!=null) rider(scope.org(),id);
    }
    public Page page(Authentication auth,LocalDate selected) {
        var scope=access.require(auth,false);var now=Instant.now();var today=now.atZone(zone).toLocalDate();
        var date=selected==null?today:selected;
        if(date.isBefore(today.minusDays(7)) || date.isAfter(today.plusWeeks(6))) throw new IllegalArgumentException("Choose a date from the past week through the next six weeks.");
        var people=riders.findAllByOrganizationIdOrderByName(scope.org());
        // Include the next day to merge a contiguous segment crossing midnight before filtering clock-outs.
        var source=shifts.endingBetween(scope.org(),date.atStartOfDay(zone).toInstant(),date.plusDays(2).atStartOfDay(zone).toInstant());
        var rows=rows(source,date,zone,now);
        var roster=scope.manager()?people.stream().map(r->new Person(r.getId(),r.getName(),r.getLocationOverride(),r.isActive(),r.getLastSyncedAt().atZone(zone),r.stale(now))).toList():List.<Person>of();
        long warnings=people.stream().filter(PickupRider::isActive).filter(r->r.stale(now)).count();
        return new Page(scope.name(),scope.manager(),date,today,zone.getId(),rows,roster,warnings);
    }
    static List<Row> rows(List<PickupShift> source,LocalDate day,ZoneId zone,Instant now) {
        Map<Long,List<PickupShift>> grouped=new LinkedHashMap<>();
        source.forEach(s->grouped.computeIfAbsent(s.getRider().getId(),id->new ArrayList<>()).add(s));
        List<Row> result=new ArrayList<>();
        for(var entries:grouped.values()) {
            entries.sort(Comparator.comparing(PickupShift::getStartsAt).thenComparing(PickupShift::getEndsAt));
            PickupShift last=null;Instant end=null;
            for(var shift:entries) {
                if(last!=null && shift.getStartsAt().isAfter(end)) {addRow(result,last,end,day,zone,now);last=null;}
                if(last==null || shift.getEndsAt().isAfter(end)) {last=shift;end=shift.getEndsAt();}
            }
            if(last!=null) addRow(result,last,end,day,zone,now);
        }
        return result.stream().sorted(Comparator.comparing(Row::clockOut).thenComparing(Row::name)).toList();
    }
    private static void addRow(List<Row> rows,PickupShift shift,Instant end,LocalDate day,ZoneId zone,Instant now) {
        if(!end.atZone(zone).toLocalDate().equals(day)) return;
        var rider=shift.getRider();String location=rider.getLocationOverride().isBlank()?shift.getLocation():rider.getLocationOverride();
        rows.add(new Row(rider.getId(),rider.getName(),end.atZone(zone),location.isBlank()?"Location not provided — confirm with rider":location,
                !rider.getLocationOverride().isBlank(),rider.getLastSyncedAt().atZone(zone),rider.stale(now),end.isBefore(now)));
    }
    @Transactional
    public void connect(Authentication auth,Long id,String name,String location,String link,List<UkgCalendarParser.DetailedShift> parsed,Instant now) {
        var scope=access.require(auth,true);name=validated(name,120,true);location=validated(location,200,false);lock(scope.org());
        String fingerprint=cipher.fingerprint(link);var duplicate=riders.findByOrganizationIdAndFeedFingerprint(scope.org(),fingerprint).orElse(null);
        if(duplicate!=null && !duplicate.getId().equals(id)) throw new IllegalArgumentException("This calendar is already on the pickup list. Edit its existing rider instead.");
        var rider=id==null?riders.saveAndFlush(new PickupRider(scope.org(),name,location,fingerprint,now)):rider(scope.org(),id);
        rider.details(name,location);
        // Negative IDs give pickup links a separate authenticated-encryption scope from driver calendars.
        rider.connect(cipher.encrypt(scope.org(),-rider.getId(),link),fingerprint,now);replace(rider,parsed,now);
    }
    @Transactional
    public void details(Authentication auth,Long id,String name,String location) {
        var scope=access.require(auth,true);lock(scope.org());rider(scope.org(),id).details(validated(name,120,true),validated(location,200,false));
    }
    @Transactional
    public void active(Authentication auth,Long id,boolean active) {
        var scope=access.require(auth,true);lock(scope.org());rider(scope.org(),id).active(active,Instant.now());
    }
    public Snapshot snapshot(Authentication auth,Long id) {
        var scope=access.require(auth,true);var rider=rider(scope.org(),id);
        if(!rider.isActive()) throw new IllegalArgumentException("Resume this rider before refreshing their calendar.");
        if(rider.getLastAttemptAt().isAfter(Instant.now().minusSeconds(60))) throw new IllegalArgumentException("Wait one minute before refreshing this calendar again.");
        return snapshot(rider);
    }
    public List<Snapshot> due(Instant now) {return riders.findTop50ByActiveTrueAndNextSyncAtLessThanEqualOrderByNextSyncAtAsc(now).stream().map(this::snapshot).toList();}
    private Snapshot snapshot(PickupRider rider){return new Snapshot(rider.getId(),rider.getOrganizationId(),rider.getRevision(),rider.getEncryptedLink());}
    @Transactional
    public void apply(Snapshot expected,List<UkgCalendarParser.DetailedShift> parsed,Instant now) {
        lock(expected.org());var rider=riders.findByIdAndOrganizationId(expected.id(),expected.org()).orElse(null);
        if(rider==null || !rider.isActive() || rider.getRevision()!=expected.revision())return;
        if(parsed==null) rider.failed(now);else {rider.synced(now);replace(rider,parsed,now);}
    }
    private void replace(PickupRider rider,List<UkgCalendarParser.DetailedShift> parsed,Instant now) {
        shifts.deleteByRider_Id(rider.getId());
        var from=now.atZone(zone).toLocalDate().minusDays(7).atStartOfDay(zone).toInstant();
        var through=now.atZone(zone).toLocalDate().plusWeeks(6).plusDays(2).atStartOfDay(zone).toInstant();
        shifts.saveAll(parsed.stream().filter(s->!s.end().isBefore(from) && s.start().isBefore(through)).map(s->new PickupShift(rider,s)).toList());
    }
    private PickupRider rider(Long org,Long id){return riders.findByIdAndOrganizationId(id,org).orElseThrow(()->new AccessDeniedException("This rider is not available in your organization."));}
    private void lock(Long org){organizations.findByIdForUpdate(org).orElseThrow();}
    private static String validated(String value,int max,boolean required) {
        String text=value==null?"":value.strip();
        if((required && text.isBlank()) || text.length()>max || text.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Enter a name (up to 120 characters) and an optional pickup location (up to 200 characters).");
        return text;
    }
    public record Snapshot(Long id,Long org,long revision,String encryptedLink) { }
    public record Row(Long riderId,String name,ZonedDateTime clockOut,String location,boolean locationOverride,ZonedDateTime lastSynced,boolean stale,boolean past) { }
    public record Person(Long id,String name,String location,boolean active,ZonedDateTime lastSynced,boolean stale) { }
    public record Page(String organizationName,boolean manager,LocalDate date,LocalDate today,String zone,List<Row> rows,List<Person> roster,long warnings) { }
}
