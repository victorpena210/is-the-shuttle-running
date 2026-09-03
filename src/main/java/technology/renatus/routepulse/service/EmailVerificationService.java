package technology.renatus.routepulse.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import technology.renatus.routepulse.model.EmailVerificationToken;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.EmailVerificationTokenRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;

@Service
public class EmailVerificationService {

    private static final long EXPIRATION_HOURS = 24;

    private final EmailVerificationTokenRepository
            tokenRepository;

    private final SecureTokenService secureTokenService;

    private final UserAccountRepository userAccountRepository;

    private final VerificationEmailSender
            verificationEmailSender;

    private final String applicationBaseUrl;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            SecureTokenService secureTokenService,
            UserAccountRepository userAccountRepository,
            VerificationEmailSender verificationEmailSender,
            @Value("${app.base-url}")
            String applicationBaseUrl) {

        this.tokenRepository = tokenRepository;
        this.secureTokenService = secureTokenService;
        this.userAccountRepository = userAccountRepository;
        this.verificationEmailSender =
                verificationEmailSender;
        this.applicationBaseUrl = applicationBaseUrl;
    }

    @Transactional
    public void createAndSendVerificationEmail(
            UserAccount user) {

        LocalDateTime now = LocalDateTime.now();

        tokenRepository
                .findAllByUser_IdAndUsedAtIsNull(
                        user.getId()
                )
                .forEach(token -> token.markUsed(now));

        String rawToken =
                secureTokenService.generateToken();

        String tokenHash =
                secureTokenService.hashToken(rawToken);

        LocalDateTime expiresAt =
                now
                        .plusHours(EXPIRATION_HOURS);

        EmailVerificationToken verificationToken =
                new EmailVerificationToken(
                        user,
                        tokenHash,
                        expiresAt
                );

        tokenRepository.saveAndFlush(
                verificationToken
        );

        String verificationUrl =
                UriComponentsBuilder
                        .fromUriString(applicationBaseUrl)
                        .path("/verify-email")
                        .queryParam("token", rawToken)
                        .build()
                        .encode()
                        .toUriString();

        verificationEmailSender
                .sendVerificationEmail(
                        user,
                        verificationUrl
                );
    }

    @Transactional
    public void resendVerificationEmail(String email) {

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
         * Always return normally. The public page must not
         * reveal whether an email is registered.
         */
        if (user == null || user.isEmailVerified()) {
            return;
        }

        createAndSendVerificationEmail(user);
    }

    @Transactional
    public VerificationResult verifyEmail(
            String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            return VerificationResult.INVALID;
        }

        String tokenHash =
                secureTokenService.hashToken(rawToken);

        EmailVerificationToken verificationToken =
                tokenRepository
                        .findByTokenHashForUpdate(tokenHash)
                        .orElse(null);

        if (verificationToken == null) {
            return VerificationResult.INVALID;
        }

        if (verificationToken.isUsed()) {
            return VerificationResult.ALREADY_USED;
        }

        LocalDateTime now = LocalDateTime.now();

        if (verificationToken.isExpired(now)) {
            return VerificationResult.EXPIRED;
        }

        verificationToken
                .getUser()
                .verifyEmail();

        verificationToken.markUsed(now);

        return VerificationResult.VERIFIED;
    }

    public enum VerificationResult {
        VERIFIED,
        INVALID,
        EXPIRED,
        ALREADY_USED
    }
}
