package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class DrivingReminderTests {
    private final ZoneId zone = ZoneId.of("America/Chicago");

    @Test void usesFiveMinutesBeforeWorkClockInAcrossSummerWinterAndMidnight() {
        for (var clockIn : List.of(LocalDateTime.of(2026, 10, 29, 15, 0),
                LocalDateTime.of(2026, 11, 3, 9, 0), LocalDateTime.of(2026, 10, 30, 0, 3))) {
            var start = clockIn.atZone(zone).toInstant();
            assertThat(DrivingReminder.isDue(clockIn, zone, start.minusSeconds(301))).isFalse();
            assertThat(DrivingReminder.isDue(clockIn, zone, start.minusSeconds(300))).isTrue();
            assertThat(DrivingReminder.isDue(clockIn, zone, start.minusSeconds(1))).isTrue();
            assertThat(DrivingReminder.isDue(clockIn, zone, start)).isFalse();
            assertThat(DrivingReminder.isDue(clockIn, zone, start.plusSeconds(3600))).isFalse();
        }
        assertThat(LocalDateTime.of(2026, 10, 29, 15, 0).atZone(zone).toInstant())
                .isEqualTo(Instant.parse("2026-10-29T20:00:00Z"));
    }

    @Test void emailContainsClockInAndAllOwnAssignmentsWithOneRecipient() {
        var day = LocalDate.of(2026, 10, 29);
        var notice = new DrivingReminder(1L, "driver@example.com", "Driver", "Test team", day.atTime(15, 0),
                day.atTime(23, 0), zone, List.of(
                    new DrivingReminder.Assignment(day.atTime(17, 0), day.atTime(17, 45), "Scare-actor transport", true),
                    new DrivingReminder.Assignment(day.atTime(21, 0), day.plusDays(1).atTime(0, 30), "Employee pickups", false)));
        var mail = mock(JavaMailSender.class);
        new DrivingReminderEmailSender(mail, "shuttle@example.com", "https://shuttle.example/",
                "", "Victor - Shuttle Assignments <victor@istheshuttlerunning.com>").send(notice);
        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captured.capture());
        var message = captured.getValue();
        assertThat(message.getTo()).containsExactly("driver@example.com");
        assertThat(message.getCc()).isNull();
        assertThat(message.getFrom()).isEqualTo("shuttle@example.com");
        assertThat(message.getText()).contains("3:00 PM–11:00 PM", "5:00 PM–5:45 PM", "9:00 PM–12:30 AM",
                "Friday, October 30, 2026", "estimated end", "America/Chicago",
                "https://shuttle.example/my-driving-assignments?date=2026-10-29",
                "Please do not reply. Contact your supervisor with questions.");
        assertThat(message.getReplyTo()).isNull();
    }

    @Test void resendIsOptInAndDoesNotReconfigureAccountEmail() {
        var existing = new JavaMailSenderImpl();
        existing.setHost("existing.example");
        existing.setPassword("existing-test-secret");
        assertThat(DrivingReminderEmailSender.reminderMailSender(existing, "")).isSameAs(existing);
        assertThat(DrivingReminderEmailSender.reminderMailSender(existing, "  ")).isSameAs(existing);
        var resend = (JavaMailSenderImpl) DrivingReminderEmailSender.reminderMailSender(existing, "test-only-api-key");
        assertThat(resend).isNotSameAs(existing);
        assertThat(resend.getHost()).isEqualTo("smtp.resend.com");
        assertThat(resend.getPort()).isEqualTo(587);
        assertThat(resend.getUsername()).isEqualTo("resend");
        assertThat(resend.getPassword()).isEqualTo("test-only-api-key");
        assertThat(resend.getJavaMailProperties()).containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true");
        assertThat(existing.getHost()).isEqualTo("existing.example");
        assertThat(existing.getPassword()).isEqualTo("existing-test-secret");
    }

    @Test void retriesOnlyKnownPreSubmissionFailuresAndCapsAttempts() {
        assertThat(DrivingReminderJob.definitelyNotSent(new MailAuthenticationException("test"))).isTrue();
        assertThat(DrivingReminderJob.definitelyNotSent(new MailSendException("test", new ConnectException()))).isTrue();
        assertThat(DrivingReminderJob.definitelyNotSent(new MailSendException("test", new SocketTimeoutException()))).isFalse();
        assertThat(DrivingReminderJob.definitelyNotSent(new IllegalStateException())).isFalse();
        var now = Instant.parse("2026-10-29T19:55:00Z");
        var delivery = new DrivingReminderDelivery(1L, 2L, now.plusSeconds(300));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(delivery.canAttempt(now)).isTrue();
            delivery.claim("driver@example.com", now);
            assertThat(delivery.canAttempt(now.plusSeconds(120))).isFalse();
            delivery.fail(true);
            assertThat(delivery.canAttempt(now.plusSeconds(59))).isFalse();
            now = now.plusSeconds(60);
        }
        assertThat(delivery.canAttempt(now)).isFalse();
        delivery.fail(false);
        assertThat(delivery.canAttempt(now.plusSeconds(600))).isFalse();
        delivery.complete(now);
        assertThat(delivery.canAttempt(now.plusSeconds(600))).isFalse();
    }

    @Test void validatesOneExplicitRecipientAndAllowsDisabling() {
        assertThat(DrivingReminderSettings.normalize(" Driver@Example.com ")).isEqualTo("driver@example.com");
        assertThat(DrivingReminderSettings.normalize(" ")).isNull();
        for (var invalid : List.of("not-an-email", "a@example.com,b@example.com", "Name <a@example.com>",
                "a@example.com\r\nBcc: b@example.com", "team:a@example.com;"))
            assertThatThrownBy(() -> DrivingReminderSettings.normalize(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
}
