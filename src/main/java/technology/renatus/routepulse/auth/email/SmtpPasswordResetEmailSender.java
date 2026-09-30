package technology.renatus.routepulse.auth.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.auth.model.UserAccount;

@Service
@Profile("prod")
public class SmtpPasswordResetEmailSender
        implements PasswordResetEmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpPasswordResetEmailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from}")
            String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordResetEmail(
            UserAccount user,
            String resetUrl) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject(
                "Reset your Is the Shuttle Running password"
        );

        message.setText(
                "Hello " + user.getFirstName() + ",\n\n"
                + "A password reset was requested for your "
                + "Is the Shuttle Running account. Open this link to choose "
                + "a new password:\n\n"
                + resetUrl
                + "\n\nThis link expires in one hour and can "
                + "only be used once. If you did not request this "
                + "change, you can ignore this email."
        );

        mailSender.send(message);
    }
}
