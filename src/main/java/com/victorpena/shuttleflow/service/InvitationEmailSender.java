package com.victorpena.shuttleflow.service;

import com.victorpena.shuttleflow.model.OrganizationInvitation;

public interface InvitationEmailSender {

    void sendManagerInvitation(
            OrganizationInvitation invitation,
            String invitationUrl
    );
}