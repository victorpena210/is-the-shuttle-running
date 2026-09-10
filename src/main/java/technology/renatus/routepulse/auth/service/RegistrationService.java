package technology.renatus.routepulse.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.dto.RegistrationForm;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Service
public class RegistrationService {

    private final UserAccountRepository
            userAccountRepository;

    private final OrganizationRepository
            organizationRepository;

    private final OrganizationMembershipRepository
            organizationMembershipRepository;

    private final PasswordEncoder
            passwordEncoder;

    private final EmailVerificationService
            emailVerificationService;

    public RegistrationService(
            UserAccountRepository userAccountRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository
                    organizationMembershipRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService
                    emailVerificationService) {

        this.userAccountRepository =
                userAccountRepository;

        this.organizationRepository =
                organizationRepository;

        this.organizationMembershipRepository =
                organizationMembershipRepository;

        this.passwordEncoder =
                passwordEncoder;

        this.emailVerificationService =
                emailVerificationService;
    }

    @Transactional
    public void registerOrganizationOwner(
            RegistrationForm form) {

        String normalizedEmail = form
                .getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userAccountRepository
                .existsByEmailIgnoreCase(normalizedEmail)) {

            throw new IllegalArgumentException(
                    "An account already exists with that email address."
            );
        }

        if (form.getPassword() == null
                || !form.getPassword()
                        .equals(form.getConfirmPassword())) {

            throw new IllegalArgumentException(
                    "The passwords do not match."
            );
        }

        String organizationSlug =
                createUniqueOrganizationSlug(
                        form.getOrganizationDisplayName()
                );

        Instant trialStartedAt =
                Instant.now();

        Instant trialEndsAt =
                trialStartedAt.plus(90, ChronoUnit.DAYS);

        Organization organization =
                new Organization(
                        form.getOrganizationLegalName().trim(),
                        form.getOrganizationDisplayName().trim(),
                        organizationSlug,
                        OrganizationStatus.TRIAL,
                        trialStartedAt,
                        trialEndsAt
                );

        organizationRepository.save(
                organization
        );

        String passwordHash =
                passwordEncoder.encode(
                        form.getPassword()
                );

        UserAccount userAccount =
                new UserAccount(
                        normalizedEmail,
                        passwordHash,
                        form.getFirstName().trim(),
                        form.getLastName().trim(),
                        false,
                        true
                );

        userAccountRepository.save(
                userAccount
        );

        OrganizationMembership membership =
                new OrganizationMembership(
                        organization,
                        userAccount,
                        OrganizationRole.OWNER
                );

        organizationMembershipRepository.save(
                membership
        );

        emailVerificationService
                .createAndSendVerificationEmail(
                        userAccount
                );
    }

    private String createUniqueOrganizationSlug(
            String organizationName) {

        String baseSlug = organizationName
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        if (baseSlug.isBlank()) {
            baseSlug = "organization";
        }

        String availableSlug = baseSlug;
        int suffix = 2;

        while (organizationRepository
                .existsBySlug(availableSlug)) {

            availableSlug =
                    baseSlug + "-" + suffix;

            suffix++;
        }

        return availableSlug;
    }
}
