package technology.renatus.routepulse.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import technology.renatus.routepulse.auth.dto.RegistrationForm;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.service.EmailVerificationService;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class RegistrationIntegrationTests extends MySqlIntegrationTestSupport {

    @Test
    void registrationCreatesAnOwnerAndEmailVerificationEnablesLogin() {
        RegistrationForm form = new RegistrationForm();

        form.setOrganizationLegalName(
                "Acme Operations LLC"
        );

        form.setOrganizationDisplayName(
                "Acme Operations"
        );

        form.setFirstName("Victor");
        form.setLastName("Pena");
        form.setEmail("  OWNER@EXAMPLE.COM  ");
        form.setPassword("a-strong-password");
        form.setConfirmPassword("a-strong-password");

        registrationService.registerOrganizationOwner(form);

        UserAccount owner = userAccountRepository
                .findByEmailIgnoreCase("owner@example.com")
                .orElseThrow();

        assertFalse(owner.isEmailVerified());
        assertTrue(owner.isEnabled());

        assertTrue(
                passwordEncoder.matches(
                        "a-strong-password",
                        owner.getPasswordHash()
                )
        );

        OrganizationMembership membership =
                membershipRepository
                        .findAllByUser_Id(owner.getId())
                        .get(0);

        assertEquals(
                OrganizationRole.OWNER,
                membership.getRole()
        );

        assertEquals(
                "acme-operations",
                membership.getOrganization().getSlug()
        );

        UserDetails beforeVerification =
                userDetailsService.loadUserByUsername(
                        "owner@example.com"
                );

        assertFalse(beforeVerification.isEnabled());

        String originalToken = queryParameter(
                verificationEmailSender.getLastUrl(),
                "token"
        );

        verificationEmailSender.clear();

        verificationService.resendVerificationEmail(
                "  OWNER@EXAMPLE.COM  "
        );

        String replacementToken = queryParameter(
                verificationEmailSender.getLastUrl(),
                "token"
        );

        assertEquals(
                EmailVerificationService
                        .VerificationResult
                        .ALREADY_USED,
                verificationService.verifyEmail(originalToken)
        );

        assertEquals(
                EmailVerificationService
                        .VerificationResult
                        .VERIFIED,
                verificationService.verifyEmail(replacementToken)
        );

        assertTrue(owner.isEmailVerified());

        UserDetails afterVerification =
                userDetailsService.loadUserByUsername(
                        "owner@example.com"
                );

        assertTrue(afterVerification.isEnabled());

        assertEquals(
                EmailVerificationService
                        .VerificationResult
                        .ALREADY_USED,
                verificationService.verifyEmail(replacementToken)
        );
    }
}
