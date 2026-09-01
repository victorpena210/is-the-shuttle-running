package technology.renatus.routepulse.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import technology.renatus.routepulse.model.UserAccount;

@Service
@Profile("prod")
public class SmtpVerificationEmailSender
        implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpVerificationEmailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendVerificationEmail(
            UserAccount user,
            String verificationUrl) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(user.getEmail());

        message.setSubject(
                "Verify your RoutePulse email address"
        );

        message.setText(
                "Hello " + user.getFirstName() + ",\n\n"
                + "Verify your RoutePulse email address by "
                + "opening this link:\n\n"
                + verificationUrl + "\n\n"
                + "This link expires in 24 hours. If you did "
                + "not create this account, you can ignore "
                + "this email."
        );

        mailSender.send(message);
    }
}
