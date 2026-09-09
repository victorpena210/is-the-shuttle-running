package technology.renatus.routepulse.marketing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.marketing.dto.DemoRequestForm;

@Service
public class DemoRequestService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DemoRequestService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;
    private final String fromAddress;
    private final String recipientAddress;

    public DemoRequestService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            Environment environment,
            @Value("${app.mail.from:victor@renatus.technology}")
            String fromAddress,
            @Value("${app.demo-request.recipient:victor@renatus.technology}")
            String recipientAddress) {

        this.mailSenderProvider = mailSenderProvider;
        this.environment = environment;
        this.fromAddress = fromAddress;
        this.recipientAddress = recipientAddress;
    }

    public void submit(DemoRequestForm form) {

        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            LOGGER.info(
                    "RoutePulse demo request received from {} <{}> for {}.",
                    form.getName(),
                    form.getEmail(),
                    form.getOrganizationName()
            );
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientAddress);
        message.setReplyTo(form.getEmail());
        message.setSubject(
                "RoutePulse demo request — "
                        + form.getOrganizationName()
        );

        message.setText(
                "A new RoutePulse demo request was submitted.\n\n"
                + "Name: " + form.getName() + "\n"
                + "Email: " + form.getEmail() + "\n"
                + "Organization: " + form.getOrganizationName() + "\n"
                + "Role: " + valueOrNotProvided(form.getRole()) + "\n\n"
                + "What they want help with:\n"
                + valueOrNotProvided(form.getMessage())
        );

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (mailSender == null) {
            throw new IllegalStateException(
                    "Production mail is not configured."
            );
        }

        mailSender.send(message);
    }

    private String valueOrNotProvided(String value) {
        if (value == null || value.isBlank()) {
            return "Not provided";
        }
        return value.trim();
    }
}
