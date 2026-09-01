package technology.renatus.routepulse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import technology.renatus.routepulse.model.UserAccount;

@Service
@Profile("dev")
public class DevVerificationEmailSender
        implements VerificationEmailSender {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    DevVerificationEmailSender.class
            );

    @Override
    public void sendVerificationEmail(
            UserAccount user,
            String verificationUrl) {

        logger.info(
                "\n\n"
                + "=======x=================================\n"
                + "DEVELOPMENT EMAIL VERIFICATION\n"
                + "Email: {}\n"
                + "Verification URL: {}\n"
                + "========================================\n",
                user.getEmail(),
                verificationUrl
        );
    }
}