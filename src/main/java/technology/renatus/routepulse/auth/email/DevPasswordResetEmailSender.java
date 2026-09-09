package technology.renatus.routepulse.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.auth.model.UserAccount;

@Service
@Profile("dev")
public class DevPasswordResetEmailSender
        implements PasswordResetEmailSender {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    DevPasswordResetEmailSender.class
            );

    @Override
    public void sendPasswordResetEmail(
            UserAccount user,
            String resetUrl) {

        logger.info(
                "\n\n"
                + "========================================\n"
                + "DEVELOPMENT PASSWORD RESET\n"
                + "Email: {}\n"
                + "Reset URL: {}\n"
                + "========================================\n",
                user.getEmail(),
                resetUrl
        );
    }
}
