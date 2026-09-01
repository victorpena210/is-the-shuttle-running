package technology.renatus.routepulse.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import technology.renatus.routepulse.model.EmailVerificationToken;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.EmailVerificationTokenRepository;

@Service
public class EmailVerificationService {

    private static final long EXPIRATION_HOURS = 24;

    private final EmailVerificationTokenRepository
            tokenRepository;

    private final SecureTokenService secureTokenService;

    private final VerificationEmailSender
            verificationEmailSender;

    private final String applicationBaseUrl;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            SecureTokenService secureTokenService,
            VerificationEmailSender verificationEmailSender,
            @Value("${app.base-url}")
            String applicationBaseUrl) {

        this.tokenRepository = tokenRepository;
        this.secureTokenService = secureTokenService;
        this.verificationEmailSender =
                verificationEmailSender;
        this.applicationBaseUrl = applicationBaseUrl;
    }

    @Transactional
    public void createAndSendVerificationEmail(
            UserAccount user) {

        String rawToken =
                secureTokenService.generateToken();

        String tokenHash =
                secureTokenService.hashToken(rawToken);

        LocalDateTime expiresAt =
                LocalDateTime.now()
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