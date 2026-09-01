package technology.renatus.routepulse.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import technology.renatus.routepulse.model.OrganizationInvitation;

@Service
@Profile("prod")
public class SmtpInvitationEmailSender
        implements InvitationEmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpInvitationEmailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendManagerInvitation(
            OrganizationInvitation invitation,
            String invitationUrl) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromAddress);

        message.setTo(
                invitation.getInvitedEmail()
        );

        message.setSubject(
                "You're invited to manage "
                        + invitation
                                .getOrganization()
                                .getDisplayName()
                        + " in RoutePulse"
        );

        message.setText(
                "You have been invited to manage drivers for "
                + invitation
                        .getOrganization()
                        .getDisplayName()
                + " in RoutePulse.\n\n"
                + "Create your manager account using this link:\n\n"
                + invitationUrl
                + "\n\n"
                + "This link expires in 48 hours."
        );

        mailSender.send(message);
    }
}
