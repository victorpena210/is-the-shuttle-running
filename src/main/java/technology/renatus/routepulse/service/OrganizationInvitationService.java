package technology.renatus.routepulse.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import technology.renatus.routepulse.dataTransferObject.InvitationAcceptanceForm;
import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.OrganizationInvitation;
import technology.renatus.routepulse.model.OrganizationMembership;
import technology.renatus.routepulse.model.OrganizationRole;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.OrganizationInvitationRepository;
import technology.renatus.routepulse.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;

@Service
public class OrganizationInvitationService {

    private static final long INVITATION_HOURS = 48;

    private final OrganizationInvitationRepository
            organizationInvitationRepository;

    private final OrganizationMembershipRepository
            organizationMembershipRepository;

    private final UserAccountRepository
            userAccountRepository;

    private final CurrentOrganizationService
            currentOrganizationService;

    private final SecureTokenService
            secureTokenService;

    private final PasswordEncoder
            passwordEncoder;

    private final InvitationEmailSender
            invitationEmailSender;

    private final String applicationBaseUrl;

    public OrganizationInvitationService(
            OrganizationInvitationRepository
                    organizationInvitationRepository,
            OrganizationMembershipRepository
                    organizationMembershipRepository,
            UserAccountRepository
                    userAccountRepository,
            CurrentOrganizationService
                    currentOrganizationService,
            SecureTokenService
                    secureTokenService,
            PasswordEncoder
                    passwordEncoder,
            InvitationEmailSender
                    invitationEmailSender,
            @Value("${app.base-url}")
            String applicationBaseUrl) {

        this.organizationInvitationRepository =
                organizationInvitationRepository;

        this.organizationMembershipRepository =
                organizationMembershipRepository;

        this.userAccountRepository =
                userAccountRepository;

        this.currentOrganizationService =
                currentOrganizationService;

        this.secureTokenService =
                secureTokenService;

        this.passwordEncoder =
                passwordEncoder;

        this.invitationEmailSender =
                invitationEmailSender;

        this.applicationBaseUrl =
                applicationBaseUrl.replaceAll(
                        "/+$",
                        ""
                );
    }

    @Transactional(readOnly = true)
    public List<OrganizationInvitation>
            getInvitationsForOwner(
                    Authentication authentication) {

        OrganizationMembership ownerMembership =
                requireOwnerMembership(
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
                requireOwnerMembership(
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
                requireOperationsMembership(
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
                    "That driver already has a RoutePulse account."
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

    @Transactional(readOnly = true)
    public OrganizationInvitation
            getInvitationForAcceptance(
                    String rawToken) {

        String tokenHash =
                secureTokenService.hashToken(
                        rawToken
                );

        OrganizationInvitation invitation =
                organizationInvitationRepository
                        .findByTokenHash(
                                tokenHash
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "This invitation link is invalid."
                                )
                        );

        requireUsableInvitation(
                invitation
        );

        return invitation;
    }

    @Transactional
    public void acceptInvitation(
            InvitationAcceptanceForm form) {

        if (form.getPassword() == null
                || !form
                        .getPassword()
                        .equals(
                                form.getConfirmPassword()
                        )) {

            throw new IllegalArgumentException(
                    "The passwords do not match."
            );
        }

        String tokenHash =
                secureTokenService.hashToken(
                        form.getToken()
                );

        OrganizationInvitation invitation =
                organizationInvitationRepository
                        .findByTokenHashForUpdate(
                                tokenHash
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "This invitation link is invalid."
                                )
                        );

        requireUsableInvitation(
                invitation
        );

        String email =
                normalizeEmail(
                        invitation.getInvitedEmail()
                );

        if (userAccountRepository
                .existsByEmailIgnoreCase(
                        email
                )) {

            throw new IllegalArgumentException(
                    "An account already exists with this email address."
            );
        }

        if (invitation.getRole()
                == OrganizationRole.DRIVER) {

            requireValidDriverInvitation(
                    invitation
            );
        }

        String passwordHash =
                passwordEncoder.encode(
                        form.getPassword()
                );

        UserAccount user =
                new UserAccount(
                        email,
                        passwordHash,
                        form.getFirstName()
                                .trim(),
                        form.getLastName()
                                .trim(),
                        true,
                        true
                );

        userAccountRepository.save(
                user
        );

        OrganizationMembership membership =
                new OrganizationMembership(
                        invitation
                                .getOrganization(),
                        user,
                        invitation
                                .getRole()
                );

        organizationMembershipRepository.save(
                membership
        );

        if (invitation.getRole()
                == OrganizationRole.DRIVER) {

            invitation
                    .getDriver()
                    .linkUser(
                            user
                    );
        }

        invitation.markAccepted(
                LocalDateTime.now()
        );
    }

    private PreparedInvitation
            createOrRefreshInvitation(
                    Organization organization,
                    UserAccount invitedBy,
                    Driver driver,
                    String email,
                    OrganizationRole role) {

        String normalizedEmail =
                normalizeEmail(
                        email
                );

        if (userAccountRepository
                .existsByEmailIgnoreCase(
                        normalizedEmail
                )) {

            throw new IllegalArgumentException(
                    "That email address already has a RoutePulse account."
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

        LocalDateTime expiresAt =
                LocalDateTime.now()
                        .plusHours(
                                INVITATION_HOURS
                        );

        OrganizationInvitation invitation =
                organizationInvitationRepository
                        .findByOrganization_IdAndInvitedEmailIgnoreCase(
                                organization.getId(),
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
                                        "That email already has a different pending RoutePulse invitation."
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
                                        organization,
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

    private OrganizationMembership
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

    private OrganizationMembership
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

    private OrganizationMembership
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

    private void requireValidDriverInvitation(
            OrganizationInvitation invitation) {

        Driver driver =
                invitation.getDriver();

        if (driver == null) {

            throw new IllegalArgumentException(
                    "This driver invitation is not linked to a driver record."
            );
        }

        if (!driver
                .getOrganization()
                .getId()
                .equals(
                        invitation
                                .getOrganization()
                                .getId()
                )) {

            throw new IllegalArgumentException(
                    "This driver invitation does not match the organization."
            );
        }

        if (driver.hasAccount()) {

            throw new IllegalArgumentException(
                    "This driver already has a RoutePulse account."
            );
        }

        if (driver.getEmail() == null
                || !driver
                        .getEmail()
                        .equalsIgnoreCase(
                                invitation
                                        .getInvitedEmail()
                        )) {

            throw new IllegalArgumentException(
                    "This driver invitation no longer matches the driver's email address."
            );
        }
    }

    private void requireUsableInvitation(
            OrganizationInvitation invitation) {

        LocalDateTime now =
                LocalDateTime.now();

        if (invitation.isAccepted()) {

            throw new IllegalArgumentException(
                    "This invitation has already been accepted."
            );
        }

        if (invitation.isExpired(now)) {

            throw new IllegalArgumentException(
                    "This invitation has expired."
            );
        }
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