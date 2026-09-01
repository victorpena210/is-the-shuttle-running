package technology.renatus.routepulse.service;

import technology.renatus.routepulse.model.OrganizationInvitation;

public interface InvitationEmailSender {

    void sendManagerInvitation(
            OrganizationInvitation invitation,
            String invitationUrl
    );
}