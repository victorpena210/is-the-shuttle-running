package com.victorpena.shuttleflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.victorpena.shuttleflow.model.OrganizationInvitation;

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

        logger.info(
                "\n\n"
                + "========================================\n"
                + "DEVELOPMENT MANAGER INVITATION\n"
                + "Organization: {}\n"
                + "Email: {}\n"
                + "Invitation URL: {}\n"
                + "========================================\n",
                invitation
                        .getOrganization()
                        .getDisplayName(),
                invitation.getInvitedEmail(),
                invitationUrl
        );
    }
}