package technology.renatus.routepulse.scheduling.automatic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class DrivingReminderEmailSender {
    private final JavaMailSender mail;
    private final String from;
    private final String baseUrl;
    public DrivingReminderEmailSender(JavaMailSender mail, @Value("${app.mail.from}") String from,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.driving-reminders.resend.api-key:}") String resendApiKey,
            @Value("${app.driving-reminders.resend.from}") String resendFrom) {
        this.mail = reminderMailSender(mail, resendApiKey);
        this.from = resendApiKey.isBlank() ? from : resendFrom;
        this.baseUrl = baseUrl;
    }

    // Keep this sender private to reminders. Registering a second JavaMailSender
    // bean would prevent Boot from creating the shared account-email sender.
    static JavaMailSender reminderMailSender(JavaMailSender existing, String apiKey) {
        if (apiKey.isBlank()) return existing;
        var resend = new JavaMailSenderImpl();
        resend.setHost("smtp.resend.com");
        resend.setPort(587);
        resend.setUsername("resend");
        resend.setPassword(apiKey);
        resend.setDefaultEncoding("UTF-8");
        var properties = resend.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        return resend;
    }
    public void send(DrivingReminder reminder) {
        send(reminder.recipient(), reminder.subject(), reminder.body(baseUrl));
    }
    public void send(technology.renatus.routepulse.pickup.PickupReminder reminder) {
        send(reminder.recipient(), reminder.subject(), reminder.body(baseUrl));
    }
    private void send(String recipient, String subject, String body) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
    }
}
