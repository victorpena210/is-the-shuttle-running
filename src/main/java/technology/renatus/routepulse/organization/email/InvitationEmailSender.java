package technology.renatus.routepulse.organization.email;

import technology.renatus.routepulse.organization.model.OrganizationInvitation;

public interface InvitationEmailSender {

    void sendManagerInvitation(
            OrganizationInvitation invitation,
            String invitationUrl
    );

    /*
     * Default implementation preserves compatibility
     * with existing test email senders that only know
     * about manager invitations.
     *
     * Production and development email senders override
     * this method with the driver-specific email wording.
     */
    default void sendDriverInvitation(
            OrganizationInvitation invitation,
            String invitationUrl) {

        sendManagerInvitation(
                invitation,
                invitationUrl
        );
    }
}
