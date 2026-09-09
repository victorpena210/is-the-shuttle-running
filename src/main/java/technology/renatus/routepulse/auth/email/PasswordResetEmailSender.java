package technology.renatus.routepulse.auth.email;

import technology.renatus.routepulse.auth.model.UserAccount;

public interface PasswordResetEmailSender {

    void sendPasswordResetEmail(
            UserAccount user,
            String resetUrl
    );
}
