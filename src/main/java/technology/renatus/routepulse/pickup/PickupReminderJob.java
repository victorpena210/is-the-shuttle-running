package technology.renatus.routepulse.pickup;

import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.automatic.*;

@Component @Profile("prod")
public class PickupReminderJob {
    private final OrganizationRepository organizations;
    private final PickupReminderStore store;
    private final DrivingReminderEmailSender sender;
    private final boolean enabled;
    public PickupReminderJob(OrganizationRepository organizations,PickupReminderStore store,DrivingReminderEmailSender sender,
            @Value("${app.pickup-reminders.enabled:true}") boolean enabled) {
        this.organizations=organizations;this.store=store;this.sender=sender;this.enabled=enabled;
    }
    @Scheduled(cron="20 * * * * *")
    public void sendDue() {
        if(!enabled) return;
        for(var org:organizations.findAll()) {
            try {
                while(true) {
                    var next=store.claimNext(org.getId(),Instant.now());
                    if(next.isEmpty()) break;
                    var reminder=next.get();
                    if(!store.stillDue(reminder,Instant.now())) continue;
                    try { sender.send(reminder); }
                    catch(RuntimeException error) {
                        store.failed(reminder.deliveryId(),DrivingReminderJob.definitelyNotSent(error));
                        LoggerFactory.getLogger(getClass()).warn("Pickup reminder {} send failed ({})",reminder.deliveryId(),error.getClass().getSimpleName());
                        continue;
                    }
                    store.sent(reminder.deliveryId(),Instant.now());
                }
            } catch(RuntimeException error) {
                LoggerFactory.getLogger(getClass()).error("Pickup reminder job failed for organization {} ({})",org.getId(),error.getClass().getSimpleName());
            }
        }
    }
}
