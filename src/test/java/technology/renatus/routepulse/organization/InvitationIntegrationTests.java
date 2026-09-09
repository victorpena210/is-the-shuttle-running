package technology.renatus.routepulse.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.dto.InvitationAcceptanceForm;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationInvitation;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class InvitationIntegrationTests extends MySqlIntegrationTestSupport {

    @Test
    void ownerCanInviteAndCreateAManagerAccount() {
        Organization organization = createOrganization(
                "Invitation Organization",
                "invitation-organization"
        );

        UserAccount owner = createUser(
                "owner@example.com",
                true
        );

        membershipRepository.save(
                new OrganizationMembership(
                        organization,
                        owner,
                        OrganizationRole.OWNER
                )
        );

        Authentication ownerAuthentication =
                authenticationFor(
                        owner,
                        OrganizationRole.OWNER
                );

        invitationService.createAndSendManagerInvitation(
                ownerAuthentication,
                "  MANAGER@EXAMPLE.COM  "
        );

        String rawToken = queryParameter(
                invitationEmailSender.getLastUrl(),
                "token"
        );

        OrganizationInvitation invitation =
                invitationAcceptanceService
                        .getInvitationForAcceptance(rawToken);

        assertEquals(
                "manager@example.com",
                invitation.getInvitedEmail()
        );

        assertEquals(
                OrganizationRole.ADMIN,
                invitation.getRole()
        );

        assertFalse(invitation.isAccepted());

        InvitationAcceptanceForm form =
                new InvitationAcceptanceForm();

        form.setToken(rawToken);
        form.setFirstName("New");
        form.setLastName("Manager");
        form.setPassword("manager-password");
        form.setConfirmPassword("manager-password");

        invitationAcceptanceService.acceptInvitation(form);

        UserAccount manager = userAccountRepository
                .findByEmailIgnoreCase("manager@example.com")
                .orElseThrow();

        OrganizationMembership managerMembership =
                membershipRepository
                        .findByOrganization_IdAndUser_Id(
                                organization.getId(),
                                manager.getId()
                        )
                        .orElseThrow();

        assertTrue(manager.isEnabled());
        assertTrue(manager.isEmailVerified());

        assertTrue(
                passwordEncoder.matches(
                        "manager-password",
                        manager.getPasswordHash()
                )
        );

        assertEquals(
                OrganizationRole.ADMIN,
                managerMembership.getRole()
        );

        assertTrue(invitation.isAccepted());
        assertNotNull(invitation.getAcceptedAt());

        assertThrows(
                IllegalArgumentException.class,
                () -> invitationAcceptanceService
                        .getInvitationForAcceptance(rawToken)
        );
    }
}
