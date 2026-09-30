package technology.renatus.routepulse.organization.service;

import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.auth.service.SecureTokenService;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.dto.InvitationAcceptanceForm;
import technology.renatus.routepulse.organization.model.OrganizationInvitation;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.organization.repository.OrganizationInvitationRepository;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;

/** Validates invitation tokens and creates the invited account in one transaction. */
@Service
public class InvitationAcceptanceService {

    private final OrganizationInvitationRepository organizationInvitationRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final UserAccountRepository userAccountRepository;
    private final SecureTokenService secureTokenService;
    private final PasswordEncoder passwordEncoder;

    public InvitationAcceptanceService(
            OrganizationInvitationRepository organizationInvitationRepository,
            OrganizationMembershipRepository organizationMembershipRepository,
            UserAccountRepository userAccountRepository,
            SecureTokenService secureTokenService,
            PasswordEncoder passwordEncoder) {
        this.organizationInvitationRepository = organizationInvitationRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.secureTokenService = secureTokenService;
        this.passwordEncoder = passwordEncoder;
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
                Instant.now()
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
                    "This driver already has a Is the Shuttle Running account."
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

        Instant now =
                Instant.now();

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
