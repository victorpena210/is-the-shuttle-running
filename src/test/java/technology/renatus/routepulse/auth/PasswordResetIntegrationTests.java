package technology.renatus.routepulse.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.service.PasswordResetService;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class PasswordResetIntegrationTests extends MySqlIntegrationTestSupport {

    @Test
    void passwordResetChangesThePasswordAndInvalidatesTheToken() {
        UserAccount user = createUser(
                "reset@example.com",
                true
        );

        passwordResetService.requestPasswordReset(
                "  RESET@EXAMPLE.COM  "
        );

        String rawToken = queryParameter(
                passwordResetEmailSender.getLastUrl(),
                "token"
        );

        assertEquals(
                PasswordResetService.TokenStatus.VALID,
                passwordResetService.inspectToken(rawToken)
        );

        assertEquals(
                PasswordResetService.ResetResult.RESET,
                passwordResetService.resetPassword(
                        rawToken,
                        "a-new-strong-password",
                        "a-new-strong-password"
                )
        );

        assertTrue(
                passwordEncoder.matches(
                        "a-new-strong-password",
                        user.getPasswordHash()
                )
        );

        assertEquals(
                PasswordResetService.TokenStatus.USED,
                passwordResetService.inspectToken(rawToken)
        );

        assertEquals(
                PasswordResetService.ResetResult.USED,
                passwordResetService.resetPassword(
                        rawToken,
                        "another-strong-password",
                        "another-strong-password"
                )
        );

        passwordResetEmailSender.clear();

        passwordResetService.requestPasswordReset(
                "missing@example.com"
        );

        assertNull(
                passwordResetEmailSender.getLastUrl()
        );
    }
}
