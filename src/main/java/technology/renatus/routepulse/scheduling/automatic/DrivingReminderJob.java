package technology.renatus.routepulse.scheduling.automatic;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Component
@Profile("prod")
public class DrivingReminderJob {
    private final OrganizationRepository organizations;
    private final DrivingReminderStore store;
    private final DrivingReminderEmailSender sender;
    private final boolean enabled;
    public DrivingReminderJob(OrganizationRepository organizations, DrivingReminderStore store,
            DrivingReminderEmailSender sender, @Value("${app.driving-reminders.enabled:true}") boolean enabled) {
        this.organizations = organizations; this.store = store; this.sender = sender; this.enabled = enabled;
    }
    @Scheduled(cron = "0 * * * * *")
    public void sendDue() {
        if (!enabled) return;
        for (var org : organizations.findAll()) {
            try {
                // Claim one at a time, so a failure does not strand other drivers' reminders.
                while (true) {
                    var next = store.claimNext(org.getId(), Instant.now());
                    if (next.isEmpty()) break;
                    var reminder = next.get();
                    try {
                        sender.send(reminder);
                    } catch (RuntimeException error) {
                        store.failed(reminder.deliveryId(), definitelyNotSent(error));
                        // SMTP exception messages can contain recipients; keep them out of application logs.
                        LoggerFactory.getLogger(getClass()).warn("Driving reminder {} send failed ({})",
                                reminder.deliveryId(), error.getClass().getSimpleName());
                        continue;
                    }
                    // If SMTP succeeded but this write fails, leave SENDING for review, never resend.
                    store.sent(reminder.deliveryId(), Instant.now());
                    LoggerFactory.getLogger(getClass()).info("Driving reminder {} accepted by mail server", reminder.deliveryId());
                }
            } catch (RuntimeException error) {
                LoggerFactory.getLogger(getClass()).error("Driving reminder job failed for organization {} ({})",
                        org.getId(), error.getClass().getSimpleName());
            }
        }
    }

    static boolean definitelyNotSent(Throwable error) {
        for (int depth = 0; error != null && depth < 20; depth++, error = error.getCause()) {
            if (error instanceof MailAuthenticationException || error instanceof ConnectException
                    || error instanceof UnknownHostException
                    || error.getClass().getSimpleName().equals("MailConnectException")) return true;
        }
        return false; // Timeouts/disconnects after submission can mean SMTP already accepted the email.
    }
}
