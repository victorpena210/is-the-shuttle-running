package technology.renatus.routepulse.auth.email;

import technology.renatus.routepulse.auth.model.UserAccount;

public interface VerificationEmailSender {
	
    void sendVerificationEmail(
            UserAccount user,
            String verificationUrl
    );

}
