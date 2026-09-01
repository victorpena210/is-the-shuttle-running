package technology.renatus.routepulse.service;

import technology.renatus.routepulse.model.UserAccount;

public interface VerificationEmailSender {
	
    void sendVerificationEmail(
            UserAccount user,
            String verificationUrl
    );

}
