package technology.renatus.routepulse.organization.service;

import org.springframework.stereotype.Service;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;

/** Resolves an acting user and verifies membership before an audited change. */
@Service
public class OrganizationActorService {

    private final UserAccountRepository userAccountRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationActorService(
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository membershipRepository) {
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
    }

    public UserAccount loadActor(
            Long organizationId,
            String actorEmail) {

        if (actorEmail == null
                || actorEmail.isBlank()) {

            throw new IllegalStateException(
                    "The signed-in user is required for this action."
            );
        }

        UserAccount actor =
                userAccountRepository
                        .findByEmailIgnoreCase(
                                actorEmail.trim()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The signed-in user could not be found."
                                )
                        );

        if (!membershipRepository
                .existsByOrganization_IdAndUser_Id(
                        organizationId,
                        actor.getId()
                )) {

            throw new IllegalStateException(
                    "The signed-in user does not belong to this organization."
            );
        }

        return actor;
    }
}
