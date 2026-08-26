package com.victorpena.shuttleflow.service;

import com.victorpena.shuttleflow.model.UserAccount;

public interface VerificationEmailSender {
	
    void sendVerificationEmail(
            UserAccount user,
            String verificationUrl
    );

}
