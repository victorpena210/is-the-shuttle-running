package technology.renatus.routepulse.organization.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.organization.model.OrganizationInvitation;

@Service
@Profile("dev")
public class DevInvitationEmailSender
        implements InvitationEmailSender {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    DevInvitationEmailSender.class
            );

    @Override
    public void sendManagerInvitation(
            OrganizationInvitation invitation,
            String invitationUrl) {

        logInvitation(
                "MANAGER",
                invitation,
                invitationUrl
        );
    }

    @Override
    public void sendDriverInvitation(
            OrganizationInvitation invitation,
            String invitationUrl) {

        logInvitation(
                "DRIVER",
                invitation,
                invitationUrl
        );
    }

    private void logInvitation(
            String invitationType,
            OrganizationInvitation invitation,
            String invitationUrl) {

        logger.info(
                "\n\n"
                + "========================================\n"
                + "DEVELOPMENT {} INVITATION\n"
                + "Organization: {}\n"
                + "Email: {}\n"
                + "Invitation URL: {}\n"
                + "========================================\n",
                invitationType,
                invitation
                        .getOrganization()
                        .getDisplayName(),
                invitation.getInvitedEmail(),
                invitationUrl
        );
    }
}
