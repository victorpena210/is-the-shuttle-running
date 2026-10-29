package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Service
public class DrivingReminderStore {
    private final OrganizationRepository organizations;
    private final DriverRepository drivers;
    private final DailyStaffScheduleService staff;
    private final AutomaticScheduleRepository schedules;
    private final DrivingReminderRepository deliveries;
    private final ZoneId zone;

    public DrivingReminderStore(OrganizationRepository organizations, DriverRepository drivers,
            DailyStaffScheduleService staff, AutomaticScheduleRepository schedules, DrivingReminderRepository deliveries,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.organizations = organizations; this.drivers = drivers; this.staff = staff;
        this.schedules = schedules; this.deliveries = deliveries; this.zone = ZoneId.of(zone);
    }

    /** Same organization lock as the planner serializes replicas and captures a coherent current plan.
     * SMTP happens only after this short transaction has committed. */
    @Transactional
    public Optional<DrivingReminder> claimNext(Long org, Instant now) {
        var organization = organizations.findByIdForUpdate(org).orElse(null);
        if (organization == null || !organization.hasApplicationAccess(now)) return Optional.empty();
        var from = now.atZone(zone).toLocalDate();
        var to = now.plusSeconds(300).atZone(zone).toLocalDate();
        var work = staff.byDay(org, from, to, null, now);
        for (var day : schedules.findAllByOrganizationIdAndDateBetweenOrderByDate(org, from, to)) {
            var plan = day.view(zone, null);
            // A stale/closed/unverified plan cannot trigger an email claiming someone is assigned.
            if (!"OPEN".equals(plan.status()) || plan.refreshedAt().toInstant().isBefore(now.minusSeconds(600))) continue;
            for (var shift : work.getOrDefault(plan.date(), List.of())) {
                if (!shift.start().toLocalDate().equals(plan.date()) || !DrivingReminder.isDue(shift.start(), zone, now)) continue;
                var driver = drivers.findByIdAndOrganization_Id(shift.driverId(), org).orElse(null);
                if (driver == null || !driver.isActive() || driver.getDrivingReminderEmail() == null
                        || driver.getDrivingReminderEmail().isBlank() || driver.getUser() != null && !driver.getUser().isEnabled()) continue;
                var own = plan.slots().stream().filter(s -> driver.getId().equals(s.getDriverId()))
                        .sorted(Comparator.comparing(AutomaticScheduleDay.Slot::getStart)).toList();
                if (own.stream().noneMatch(s -> s.overlaps(shift.start(), shift.end()))) continue;
                var startsAt = shift.start().atZone(zone).toInstant();
                var delivery = deliveries.findByOrganizationIdAndDriverIdAndWorkStartsAt(org, driver.getId(), startsAt)
                        .orElseGet(() -> new DrivingReminderDelivery(org, driver.getId(), startsAt));
                if (!delivery.canAttempt(now)) continue;
                delivery.claim(driver.getDrivingReminderEmail(), now);
                deliveries.saveAndFlush(delivery);
                return Optional.of(new DrivingReminder(delivery.getId(), driver.getDrivingReminderEmail(), driver.getName(),
                        organization.getDisplayName(), shift.start(), shift.end(), zone,
                        own.stream().map(s -> new DrivingReminder.Assignment(s.getStart(), s.getEnd(),
                                s.getServiceLabel(), s.isScareActorTransport())).toList()));
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void sent(Long id, Instant now) { deliveries.findById(id).orElseThrow().complete(now); }

    @Transactional
    public void failed(Long id, boolean definitelyNotSent) { deliveries.findById(id).orElseThrow().fail(definitelyNotSent); }
}
