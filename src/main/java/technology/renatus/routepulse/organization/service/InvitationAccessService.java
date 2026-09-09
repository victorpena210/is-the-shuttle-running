package technology.renatus.routepulse.organization.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;

/** Checks the roles allowed to issue manager or driver invitations. */
@Service
public class InvitationAccessService {

    private final CurrentOrganizationService currentOrganizationService;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;

    public InvitationAccessService(
            CurrentOrganizationService currentOrganizationService,
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository organizationMembershipRepository) {
        this.currentOrganizationService = currentOrganizationService;
        this.userAccountRepository = userAccountRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
    }

    public OrganizationMembership
            requireOwnerMembership(
                    Authentication authentication) {

        OrganizationMembership membership =
                requireMembership(
                        authentication
                );

        if (membership.getRole()
                != OrganizationRole.OWNER) {

            throw new IllegalArgumentException(
                    "Only the organization owner can invite managers."
            );
        }

        return membership;
    }

    public OrganizationMembership
            requireOperationsMembership(
                    Authentication authentication) {

        OrganizationMembership membership =
                requireMembership(
                        authentication
                );

        OrganizationRole role =
                membership.getRole();

        if (role != OrganizationRole.OWNER
                && role != OrganizationRole.ADMIN
                && role != OrganizationRole.DISPATCHER) {

            throw new IllegalArgumentException(
                    "Only an owner, admin, or dispatcher can invite drivers."
            );
        }

        return membership;
    }

    public OrganizationMembership
            requireMembership(
                    Authentication authentication) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        UserAccount user =
                userAccountRepository
                        .findByEmailIgnoreCase(
                                authentication
                                        .getName()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The signed-in account could not be found."
                                )
                        );

        return organizationMembershipRepository
                .findByOrganization_IdAndUser_Id(
                        organizationId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "The signed-in account does not belong to this organization."
                        )
                );
    }
}
