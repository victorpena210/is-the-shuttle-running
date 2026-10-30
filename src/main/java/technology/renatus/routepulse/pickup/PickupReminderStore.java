package technology.renatus.routepulse.pickup;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleRepository;

@Service
public class PickupReminderStore {
    private final PickupAlerts alerts;
    private final PickupReminderRepository deliveries;
    private final DriverRepository drivers;
    private final OrganizationRepository organizations;
    private final AutomaticScheduleRepository schedules;
    private final ZoneId zone;
    public PickupReminderStore(PickupAlerts alerts,PickupReminderRepository deliveries,DriverRepository drivers,
            OrganizationRepository organizations,AutomaticScheduleRepository schedules,@Value("${app.operations-zone:America/Chicago}") String zone) {
        this.alerts=alerts;this.deliveries=deliveries;this.drivers=drivers;this.organizations=organizations;this.schedules=schedules;this.zone=ZoneId.of(zone);
    }
    @Transactional
    public Optional<PickupReminder> claimNext(Long org,Instant now) {
        var organization=organizations.findByIdForUpdate(org).orElse(null);
        if(organization==null || !organization.hasApplicationAccess(now)) return Optional.empty();
        for(var alert:due(org,now)) {
            var driver=drivers.findByIdAndOrganization_Id(alert.reminderDriverId(),org).orElse(null);
            if(driver==null || !driver.isActive() || driver.getDrivingReminderEmail()==null || driver.getDrivingReminderEmail().isBlank()
                    || driver.getUser()!=null && !driver.getUser().isEnabled()) continue;
            var delivery=deliveries.findByOrganizationIdAndRiderIdAndClockOutAndDriverId(org,alert.row().riderId(),alert.row().clockOut().toInstant(),driver.getId())
                    .orElseGet(()->new PickupReminderDelivery(org,alert.row().riderId(),alert.row().clockOut().toInstant(),driver.getId()));
            if(!delivery.canAttempt(now)) continue;
            delivery.claim(now);deliveries.saveAndFlush(delivery);
            return Optional.of(new PickupReminder(delivery.getId(),org,driver.getDrivingReminderEmail(),driver.getName(),alert));
        }
        return Optional.empty();
    }
    private List<PickupAlerts.Alert> due(Long org,Instant now) {
        var from=now.atZone(zone).toLocalDate();var to=now.plusSeconds(900).atZone(zone).toLocalDate();
        var fresh=schedules.findAllByOrganizationIdAndDateBetweenOrderByDate(org,from.minusDays(1),to.plusDays(1)).stream()
                .map(d->d.view(zone,null)).filter(d->"OPEN".equals(d.status()) && !d.refreshedAt().toInstant().isBefore(now.minusSeconds(600)))
                .flatMap(d->d.slots().stream()).toList();
        return alerts.forDates(org,from,to,now).stream().filter(a->!a.row().stale() && a.reminderDriverId()!=null
                && !now.isBefore(a.row().clockOut().toInstant().minusSeconds(900)) && now.isBefore(a.row().clockOut().toInstant())
                && (a.covering()!=null ? fresh.contains(a.covering()) : a.related().stream().anyMatch(s->a.reminderDriverId().equals(s.getDriverId()) && fresh.contains(s)))).toList();
    }
    /** Recheck calendar changes and the actual covering driver immediately before submission. */
    @Transactional
    public boolean stillDue(PickupReminder reminder,Instant now) {
        var org=organizations.findByIdForUpdate(reminder.organizationId()).orElseThrow();
        var delivery=deliveries.findById(reminder.deliveryId()).orElseThrow();
        var match=org.hasApplicationAccess(now) && due(org.getId(),now).stream().anyMatch(a->a.row().riderId().equals(delivery.getRiderId())
                && a.row().clockOut().toInstant().equals(delivery.getClockOut()) && a.reminderDriverId().equals(delivery.getDriverId())
                && a.row().location().equals(reminder.alert().row().location()));
        var driver=drivers.findByIdAndOrganization_Id(delivery.getDriverId(),org.getId()).orElse(null);
        match=match && driver!=null && driver.isActive() && (driver.getUser()==null || driver.getUser().isEnabled())
                && reminder.recipient().equals(driver.getDrivingReminderEmail());
        if(!match) delivery.cancel();
        return match && delivery.getStatus()==PickupReminderDelivery.Status.SENDING;
    }
    @Transactional public void sent(Long id,Instant now) { deliveries.findById(id).orElseThrow().sent(now); }
    @Transactional public void failed(Long id,boolean definitelyNotSent) { deliveries.findById(id).orElseThrow().failed(definitelyNotSent); }
}
