package com.victorpena.shuttleflow.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.victorpena.shuttleflow.model.EmailVerificationToken;
import com.victorpena.shuttleflow.model.UserAccount;
import com.victorpena.shuttleflow.repository.EmailVerificationTokenRepository;

@Service
public class EmailVerificationService {

    private static final long EXPIRATION_HOURS = 24;

    private final EmailVerificationTokenRepository
            tokenRepository;

    private final SecureTokenService secureTokenService;

    private final JavaMailSender mailSender;

    private final String applicationBaseUrl;

    private final String fromAddress;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            SecureTokenService secureTokenService,
            JavaMailSender mailSender,
            @Value("${app.base-url}")
            String applicationBaseUrl,
            @Value("${app.mail.from}")
            String fromAddress) {

        this.tokenRepository = tokenRepository;
        this.secureTokenService = secureTokenService;
        this.mailSender = mailSender;
        this.applicationBaseUrl = applicationBaseUrl;
        this.fromAddress = fromAddress;
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

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(user.getEmail());

        message.setSubject(
                "Verify your ShuttleFlow email address"
        );

        message.setText(
                "Hello " + user.getFirstName() + ",\n\n"
                + "Verify your ShuttleFlow email address by "
                + "opening this link:\n\n"
                + verificationUrl + "\n\n"
                + "This link expires in 24 hours. If you did "
                + "not create this account, you can ignore "
                + "this email."
        );

        mailSender.send(message);
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