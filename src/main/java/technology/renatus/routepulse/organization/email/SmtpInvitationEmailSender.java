package technology.renatus.routepulse.organization.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.organization.model.OrganizationInvitation;

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

    @Override
    public void sendDriverInvitation(
            OrganizationInvitation invitation,
            String invitationUrl) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromAddress);

        message.setTo(
                invitation.getInvitedEmail()
        );

        message.setSubject(
                "You're invited to RoutePulse as a driver for "
                        + invitation
                        .getOrganization()
                        .getDisplayName()
        );

        String driverName =
                invitation.getDriver() == null
                        ? "Driver"
                        : invitation
                                .getDriver()
                                .getName();

        message.setText(
                "Hi "
                + driverName
                + ",\n\n"
                + "You have been registered as a shuttle driver for "
                + invitation
                        .getOrganization()
                        .getDisplayName()
                + " in RoutePulse.\n\n"
                + "Create your driver account using this link:\n\n"
                + invitationUrl
                + "\n\n"
                + "After signing in, you will be able to see your own assignments and use the shuttle QR code to record when you start and stop driving.\n\n"
                + "This link expires in 48 hours."
        );

        mailSender.send(message);
    }
}
