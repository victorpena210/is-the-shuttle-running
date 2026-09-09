package technology.renatus.routepulse.auth.service;

import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import technology.renatus.routepulse.auth.email.PasswordResetEmailSender;
import technology.renatus.routepulse.auth.model.PasswordResetToken;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.PasswordResetTokenRepository;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;

@Service
public class PasswordResetService {

    private static final long EXPIRATION_HOURS = 1;

    private final PasswordResetTokenRepository
            passwordResetTokenRepository;

    private final UserAccountRepository
            userAccountRepository;

    private final SecureTokenService secureTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailSender emailSender;
    private final String applicationBaseUrl;

    public PasswordResetService(
            PasswordResetTokenRepository
                    passwordResetTokenRepository,
            UserAccountRepository userAccountRepository,
            SecureTokenService secureTokenService,
            PasswordEncoder passwordEncoder,
            PasswordResetEmailSender emailSender,
            @Value("${app.base-url}")
            String applicationBaseUrl) {

        this.passwordResetTokenRepository =
                passwordResetTokenRepository;

        this.userAccountRepository =
                userAccountRepository;

        this.secureTokenService = secureTokenService;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.applicationBaseUrl =
                applicationBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestPasswordReset(String email) {

        if (email == null || email.isBlank()) {
            return;
        }

        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        UserAccount user = userAccountRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElse(null);

        /*
         * Return normally when the account does not exist.
         * The public response must be identical so attackers
         * cannot use this page to discover registered emails.
         */
        if (user == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        passwordResetTokenRepository
                .findAllByUser_IdAndUsedAtIsNull(
                        user.getId()
                )
                .forEach(token -> token.markUsed(now));

        String rawToken =
                secureTokenService.generateToken();

        PasswordResetToken resetToken =
                new PasswordResetToken(
                        user,
                        secureTokenService.hashToken(rawToken),
                        now.plusHours(EXPIRATION_HOURS)
                );

        passwordResetTokenRepository.saveAndFlush(
                resetToken
        );

        String resetUrl = UriComponentsBuilder
                .fromUriString(applicationBaseUrl)
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        emailSender.sendPasswordResetEmail(
                user,
                resetUrl
        );
    }

    @Transactional(readOnly = true)
    public TokenStatus inspectToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            return TokenStatus.INVALID;
        }

        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByTokenHash(
                                secureTokenService
                                        .hashToken(rawToken)
                        )
                        .orElse(null);

        return determineStatus(
                token,
                LocalDateTime.now()
        );
    }

    @Transactional
    public ResetResult resetPassword(
            String rawToken,
            String password,
            String confirmPassword) {

        if (password == null
                || password.length() < 12
                || password.length() > 72
                || !password.equals(confirmPassword)) {

            throw new IllegalArgumentException(
                    "Enter matching passwords between 12 and 72 characters."
            );
        }

        if (rawToken == null || rawToken.isBlank()) {
            return ResetResult.INVALID;
        }

        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByTokenHashForUpdate(
                                secureTokenService
                                        .hashToken(rawToken)
                        )
                        .orElse(null);

        TokenStatus status = determineStatus(
                token,
                LocalDateTime.now()
        );

        if (status == TokenStatus.INVALID) {
            return ResetResult.INVALID;
        }

        if (status == TokenStatus.EXPIRED) {
            return ResetResult.EXPIRED;
        }

        if (status == TokenStatus.USED) {
            return ResetResult.USED;
        }

        UserAccount user = token.getUser();

        user.changePasswordHash(
                passwordEncoder.encode(password)
        );

        LocalDateTime usedAt = LocalDateTime.now();

        passwordResetTokenRepository
                .findAllByUser_IdAndUsedAtIsNull(
                        user.getId()
                )
                .forEach(otherToken ->
                        otherToken.markUsed(usedAt)
                );

        return ResetResult.RESET;
    }

    private TokenStatus determineStatus(
            PasswordResetToken token,
            LocalDateTime now) {

        if (token == null) {
            return TokenStatus.INVALID;
        }

        if (token.isUsed()) {
            return TokenStatus.USED;
        }

        if (token.isExpired(now)) {
            return TokenStatus.EXPIRED;
        }

        return TokenStatus.VALID;
    }

    public enum TokenStatus {
        VALID,
        INVALID,
        EXPIRED,
        USED
    }

    public enum ResetResult {
        RESET,
        INVALID,
        EXPIRED,
        USED
    }
}
