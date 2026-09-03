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
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.OrganizationInvitation;
import technology.renatus.routepulse.model.OrganizationMembership;
import technology.renatus.routepulse.model.OrganizationRole;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.OrganizationInvitationRepository;
import technology.renatus.routepulse.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.repository.OrganizationRepository;
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
            UserAccountRepository userAccountRepository,
            CurrentOrganizationService
                    currentOrganizationService,
            SecureTokenService secureTokenService,
            PasswordEncoder passwordEncoder,
            InvitationEmailSender invitationEmailSender,
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
                .findAllByOrganization_IdOrderByCreatedAtDesc(
                        ownerMembership
                                .getOrganization()
                                .getId()
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

        String normalizedEmail =
                normalizeEmail(email);

        if (userAccountRepository
                .existsByEmailIgnoreCase(
                        normalizedEmail
                )) {

            throw new IllegalArgumentException(
                    "That email address already has a RoutePulse account."
            );
        }

        Organization organization =
                ownerMembership.getOrganization();

        UserAccount owner =
                ownerMembership.getUser();

        String rawToken =
                secureTokenService.generateToken();

        String tokenHash =
                secureTokenService.hashToken(
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

                            existingInvitation.refresh(
                                    owner,
                                    tokenHash,
                                    expiresAt
                            );

                            return existingInvitation;
                        })
                        .orElseGet(() ->
                                new OrganizationInvitation(
                                        organization,
                                        owner,
                                        normalizedEmail,
                                        OrganizationRole.ADMIN,
                                        tokenHash,
                                        expiresAt
                                )
                        );

        organizationInvitationRepository.save(
                invitation
        );

        String invitationUrl =
                UriComponentsBuilder
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

        invitationEmailSender
                .sendManagerInvitation(
                        invitation,
                        invitationUrl
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
                || !form.getPassword()
                        .equals(form.getConfirmPassword())) {

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

        String passwordHash =
                passwordEncoder.encode(
                        form.getPassword()
                );

        UserAccount manager =
                new UserAccount(
                        email,
                        passwordHash,
                        form.getFirstName().trim(),
                        form.getLastName().trim(),
                        true,
                        true
                );

        userAccountRepository.save(
                manager
        );

        OrganizationMembership membership =
                new OrganizationMembership(
                        invitation.getOrganization(),
                        manager,
                        OrganizationRole.ADMIN
                );

        organizationMembershipRepository.save(
                membership
        );

        invitation.markAccepted(
                LocalDateTime.now()
        );
    }

    private OrganizationMembership
            requireOwnerMembership(
                    Authentication authentication) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        UserAccount user =
                userAccountRepository
                        .findByEmailIgnoreCase(
                                authentication.getName()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The signed-in account could not be found."
                                )
                        );

        OrganizationMembership membership =
                organizationMembershipRepository
                        .findByOrganization_IdAndUser_Id(
                                organizationId,
                                user.getId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The signed-in account does not belong to this organization."
                                )
                        );

        if (membership.getRole()
                != OrganizationRole.OWNER) {

            throw new IllegalArgumentException(
                    "Only the organization owner can invite managers."
            );
        }

        return membership;
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
