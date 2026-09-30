package technology.renatus.routepulse.organization.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.auth.service.SecureTokenService;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.email.InvitationEmailSender;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationInvitation;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.organization.repository.OrganizationInvitationRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

/** Creates or refreshes invitations and dispatches their email links. */
@Service
public class OrganizationInvitationService {

    private static final long INVITATION_HOURS = 48;
    private final String applicationBaseUrl;
    private final OrganizationInvitationRepository organizationInvitationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final SecureTokenService secureTokenService;
    private final InvitationEmailSender invitationEmailSender;
    private final InvitationAccessService invitationAccessService;

    public OrganizationInvitationService(
            OrganizationInvitationRepository organizationInvitationRepository,
            OrganizationRepository organizationRepository,
            UserAccountRepository userAccountRepository,
            SecureTokenService secureTokenService,
            InvitationEmailSender invitationEmailSender,
            InvitationAccessService invitationAccessService,
            @Value("${app.base-url}") String applicationBaseUrl) {
        this.organizationInvitationRepository = organizationInvitationRepository;
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.secureTokenService = secureTokenService;
        this.invitationEmailSender = invitationEmailSender;
        this.invitationAccessService = invitationAccessService;
        this.applicationBaseUrl = applicationBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public List<OrganizationInvitation>
            getInvitationsForOwner(
                    Authentication authentication) {

        OrganizationMembership ownerMembership =
                invitationAccessService.requireOwnerMembership(
                        authentication
                );

        return organizationInvitationRepository
                .findAllByOrganization_IdAndRoleOrderByCreatedAtDesc(
                        ownerMembership
                                .getOrganization()
                                .getId(),
                        OrganizationRole.ADMIN
                );
    }

    @Transactional
    public void createAndSendManagerInvitation(
            Authentication authentication,
            String email) {

        OrganizationMembership ownerMembership =
                invitationAccessService.requireOwnerMembership(
                        authentication
                );

        PreparedInvitation preparedInvitation =
                createOrRefreshInvitation(
                        ownerMembership
                                .getOrganization(),
                        ownerMembership
                                .getUser(),
                        null,
                        email,
                        OrganizationRole.ADMIN
                );

        invitationEmailSender
                .sendManagerInvitation(
                        preparedInvitation
                                .invitation(),
                        buildInvitationUrl(
                                preparedInvitation
                                        .rawToken()
                        )
                );
    }

    @Transactional
    public void createAndSendDriverInvitation(
            Authentication authentication,
            Driver driver) {

        OrganizationMembership actorMembership =
                requireDriverInvitationActor(
                        authentication,
                        driver
                );

        sendDriverInvitation(
                actorMembership,
                driver
        );
    }

    @Transactional
    public void replaceAndSendDriverInvitation(
            Authentication authentication,
            Driver driver) {

        OrganizationMembership actorMembership =
                requireDriverInvitationActor(
                        authentication,
                        driver
                );

        List<OrganizationInvitation> pendingInvitations =
                organizationInvitationRepository
                        .findAllByOrganization_IdAndDriver_IdAndAcceptedAtIsNull(
                                actorMembership
                                        .getOrganization()
                                        .getId(),
                                driver.getId()
                        );

        if (!pendingInvitations.isEmpty()) {
            organizationInvitationRepository
                    .deleteAll(
                            pendingInvitations
                    );

            // Force the old token/email row out before a replacement
            // invitation is inserted in the same transaction.
            organizationInvitationRepository.flush();
        }

        sendDriverInvitation(
                actorMembership,
                driver
        );
    }

    private OrganizationMembership
    requireDriverInvitationActor(
            Authentication authentication,
            Driver driver) {

        OrganizationMembership actorMembership =
                invitationAccessService
                        .requireOperationsMembership(
                                authentication
                        );

        if (driver == null
                || driver.getOrganization() == null
                || !driver
                        .getOrganization()
                        .getId()
                        .equals(
                                actorMembership
                                        .getOrganization()
                                        .getId()
                        )) {

            throw new IllegalArgumentException(
                    "That driver does not belong to this organization."
            );
        }

        if (driver.hasAccount()) {
            throw new IllegalArgumentException(
                    "That driver already has a Is the Shuttle Running account."
            );
        }

        if (driver.getEmail() == null
                || driver
                        .getEmail()
                        .isBlank()) {

            throw new IllegalArgumentException(
                    "The driver needs an email address before an invitation can be sent."
            );
        }

        return actorMembership;
    }

    private void sendDriverInvitation(
            OrganizationMembership actorMembership,
            Driver driver) {

        PreparedInvitation preparedInvitation =
                createOrRefreshInvitation(
                        actorMembership
                                .getOrganization(),
                        actorMembership
                                .getUser(),
                        driver,
                        driver.getEmail(),
                        OrganizationRole.DRIVER
                );

        invitationEmailSender
                .sendDriverInvitation(
                        preparedInvitation
                                .invitation(),
                        buildInvitationUrl(
                                preparedInvitation
                                        .rawToken()
                        )
                );
    }

    private PreparedInvitation
    createOrRefreshInvitation(
            Organization organization,
            UserAccount invitedBy,
            Driver driver,
            String email,
            OrganizationRole role) {

        Organization lockedOrganization =
                organizationRepository
                        .findByIdForUpdate(
                                organization.getId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The organization could not be found."
                                )
                        );

        String normalizedEmail =
                normalizeEmail(
                        email
                );
        if (userAccountRepository
                .existsByEmailIgnoreCase(
                        normalizedEmail
                )) {

            throw new IllegalArgumentException(
                    "That email address already has a Is the Shuttle Running account."
            );
        }

        String rawToken =
                secureTokenService
                        .generateToken();

        String tokenHash =
                secureTokenService
                        .hashToken(
                                rawToken
                        );

        Instant expiresAt =
                Instant.now()
                        .plus(INVITATION_HOURS, ChronoUnit.HOURS);

        OrganizationInvitation invitation =
                organizationInvitationRepository
                .findByOrganization_IdAndInvitedEmailIgnoreCase(
                        lockedOrganization.getId(),
                        normalizedEmail
                )
                        .map(existingInvitation -> {

                            if (existingInvitation
                                    .isAccepted()) {

                                throw new IllegalArgumentException(
                                        "That invitation has already been accepted."
                                );
                            }

                            if (existingInvitation
                                    .getRole()
                                    != role) {

                                throw new IllegalArgumentException(
                                        "That email already has a different pending Is the Shuttle Running invitation."
                                );
                            }

                            if (role
                                    == OrganizationRole.DRIVER) {

                                if (existingInvitation
                                        .getDriver()
                                        == null
                                        || !existingInvitation
                                                .getDriver()
                                                .getId()
                                                .equals(
                                                        driver.getId()
                                                )) {

                                    throw new IllegalArgumentException(
                                            "That email is already assigned to another pending driver invitation."
                                    );
                                }
                            }

                            existingInvitation.refresh(
                                    invitedBy,
                                    driver,
                                    role,
                                    tokenHash,
                                    expiresAt
                            );

                            return existingInvitation;
                        })
                        .orElseGet(() ->
                        new OrganizationInvitation(
                                lockedOrganization,
                                invitedBy,
                                driver,
                                normalizedEmail,
                                role,
                                tokenHash,
                                expiresAt
                        )
                );

        organizationInvitationRepository.save(
                invitation
        );

        return new PreparedInvitation(
                invitation,
                rawToken
        );
    }

    private String buildInvitationUrl(
            String rawToken) {

        return UriComponentsBuilder
                .fromUriString(
                        applicationBaseUrl
                )
                .path(
                        "/invitations/accept"
                )
                .queryParam(
                        "token",
                        rawToken
                )
                .build()
                .encode()
                .toUriString();
    }

    private record PreparedInvitation(
            OrganizationInvitation invitation,
            String rawToken) {
    }

    private String normalizeEmail(
            String email) {

        if (email == null
                || email.isBlank()) {

            throw new IllegalArgumentException(
                    "An email address is required."
            );
        }

        return email
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
