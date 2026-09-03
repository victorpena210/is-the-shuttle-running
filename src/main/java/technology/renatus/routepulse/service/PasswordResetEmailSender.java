package technology.renatus.routepulse.service;

import technology.renatus.routepulse.model.UserAccount;

public interface PasswordResetEmailSender {

    void sendPasswordResetEmail(
            UserAccount user,
            String resetUrl
    );
}
