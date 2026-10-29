package technology.renatus.routepulse.scheduling.automatic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class DrivingReminderEmailSender {
    private final JavaMailSender mail;
    private final String from;
    private final String baseUrl;
    public DrivingReminderEmailSender(JavaMailSender mail, @Value("${app.mail.from}") String from,
            @Value("${app.base-url}") String baseUrl) {
        this.mail = mail; this.from = from; this.baseUrl = baseUrl;
    }
    public void send(DrivingReminder reminder) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(reminder.recipient());
        message.setSubject(reminder.subject());
        message.setText(reminder.body(baseUrl));
        mail.send(message);
    }
}
