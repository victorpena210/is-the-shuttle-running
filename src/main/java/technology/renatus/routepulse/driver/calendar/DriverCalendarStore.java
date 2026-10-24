package technology.renatus.routepulse.driver.calendar;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.repository.*;
import technology.renatus.routepulse.organization.service.*;

@Service @Transactional(readOnly=true)
public class DriverCalendarStore {
    private final DriverRepository drivers; private final DriverCalendarConnectionRepository connections;
    private final DriverCalendarShiftRepository shifts; private final CurrentOrganizationService current;
    private final OrganizationActorService actors; private final OrganizationMembershipRepository memberships;
    private final OrganizationRepository organizations; private final CalendarLinkCipher cipher; private final ZoneId zone;
    public DriverCalendarStore(DriverRepository drivers,DriverCalendarConnectionRepository connections,DriverCalendarShiftRepository shifts,
            CurrentOrganizationService current,OrganizationActorService actors,OrganizationMembershipRepository memberships,
            OrganizationRepository organizations,CalendarLinkCipher cipher,@Value("${app.operations-zone:America/Chicago}") String zone) {
        this.drivers=drivers;this.connections=connections;this.shifts=shifts;this.current=current;this.actors=actors;
        this.memberships=memberships;this.organizations=organizations;this.cipher=cipher;this.zone=ZoneId.of(zone);
    }
    public Driver authorize(Authentication auth, Long id) {
        Long org=current.requireOrganizationId(auth); var actor=actors.loadActor(org,auth.getName());
        var role=memberships.findByOrganization_IdAndUser_Id(org,actor.getId()).orElseThrow(() -> new AccessDeniedException("Organization access is required.")).getRole();
        boolean manager=Set.of(OrganizationRole.OWNER,OrganizationRole.ADMIN,OrganizationRole.DISPATCHER).contains(role);
        if (!actor.isEnabled() || (!manager && role!=OrganizationRole.DRIVER)) throw new AccessDeniedException("Driver access is required.");
        var driver=(id==null ? drivers.findByOrganization_IdAndUser_Id(org,actor.getId()) : drivers.findByIdAndOrganization_Id(id,org))
                .orElseThrow(() -> new AccessDeniedException("This driver profile is not available to this account."));
        if (!manager && (driver.getUser()==null || !driver.getUser().getId().equals(actor.getId()))) throw new AccessDeniedException("You can only connect your own calendar.");
        driver.getOrganization().getDisplayName(); return driver;
    }
    public Page page(Authentication auth,Long id) {
        var driver=authorize(auth,id);var c=connections.findByDriver_IdAndOrganizationId(driver.getId(),driver.getOrganization().getId()).orElse(null);
        return new Page(driver.getId(),driver.getName(),driver.getOrganization().getDisplayName(),c!=null,
                c==null?null:c.getLastSyncedAt().atZone(zone),c==null?null:c.getNextSyncAt().atZone(zone),
                c==null?null:c.getLastError(),c!=null&&!c.fresh(Instant.now()),
                c==null?List.of():shifts.findByConnection_IdOrderByStartsAt(c.getId()).stream()
                        .map(s -> new Hours(s.getStartsAt().atZone(zone),s.getEndsAt().atZone(zone))).toList(),zone.getId());
    }
    @Transactional
    public void connect(Authentication auth,Long id,String link,List<UkgCalendarParser.Shift> parsed,Instant now) {
        var driver=authorize(auth,id);Long org=driver.getOrganization().getId();lock(org);
        var c=connections.findByDriver_IdAndOrganizationId(driver.getId(),org).orElse(null);
        String fingerprint=cipher.fingerprint(link);
        if(connections.existsByOrganizationIdAndFeedFingerprintAndDriver_IdNot(org,fingerprint,driver.getId()))
            throw new IllegalArgumentException("This UKG link is already connected to another driver. Each driver needs their own subscription link.");
        String encrypted=cipher.encrypt(org,driver.getId(),link);
        if(c==null) c=connections.saveAndFlush(new DriverCalendarConnection(driver,encrypted,fingerprint,now,zone)); else c.reconnect(encrypted,fingerprint,now,zone);
        replace(c,parsed);
    }
    public Snapshot snapshot(Authentication auth,Long id) {
        var driver=authorize(auth,id);
        var c=connections.findByDriver_IdAndOrganizationId(driver.getId(),driver.getOrganization().getId())
                .orElseThrow(() -> new IllegalArgumentException("Connect a UKG calendar first."));
        if(c.getLastAttemptAt().isAfter(Instant.now().minusSeconds(60))) throw new IllegalArgumentException("The calendar was just checked. Wait one minute before refreshing again.");
        return snapshot(c);
    }
    public List<Snapshot> due(Instant now) {
        return connections.findTop50ByNextSyncAtLessThanEqualOrderByNextSyncAtAsc(now).stream().map(this::snapshot).toList();
    }
    private Snapshot snapshot(DriverCalendarConnection c) { return new Snapshot(c.getId(),c.getOrganizationId(),c.getDriver().getId(),c.getRevision(),c.getEncryptedLink()); }
    @Transactional
    public void apply(Snapshot expected,List<UkgCalendarParser.Shift> parsed,String error,Instant now) {
        lock(expected.org());var c=connections.findById(expected.id()).orElse(null);
        if(c==null || !c.getOrganizationId().equals(expected.org()) || c.getRevision()!=expected.revision()) return;
        if(error!=null) c.failed(now,error); else {c.synced(now,zone);replace(c,parsed);}
    }
    @Transactional
    public void disconnect(Authentication auth,Long id) {
        var driver=authorize(auth,id);Long org=driver.getOrganization().getId();lock(org);
        connections.findByDriver_IdAndOrganizationId(driver.getId(),org).ifPresent(c -> {shifts.clear(c.getId());connections.delete(c);});
    }
    private void replace(DriverCalendarConnection c,List<UkgCalendarParser.Shift> parsed) {
        shifts.clear(c.getId());
        var start=c.getCoverageFrom().atStartOfDay(zone).toInstant();var end=c.getCoverageUntil().atStartOfDay(zone).toInstant();
        shifts.saveAll(parsed.stream().filter(s -> s.start().isBefore(end)&&s.end().isAfter(start))
                .map(s -> new DriverCalendarShift(c,s)).toList());
    }
    private void lock(Long org) {organizations.findByIdForUpdate(org).orElseThrow(() -> new IllegalStateException("Organization was not found."));}
    // No plaintext URL is returned from any page or status DTO.
    public record Snapshot(Long id,Long org,Long driver,long revision,String encryptedLink) { }
    public record Hours(ZonedDateTime start,ZonedDateTime end) { }
    public record Page(Long driverId,String driverName,String organizationName,boolean connected,ZonedDateTime lastSynced,
            ZonedDateTime nextSync,String error,boolean stale,List<Hours> shifts,String zone) { }
}
