package technology.renatus.routepulse.driving;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.domain.DrivingSessionEndReason;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.model.UnscheduledDrivingRecord;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.service.DrivingSessionService;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DrivingSessionDurationIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DrivingSessionRepository sessions;
    @Autowired DrivingSessionService service;
    private static final Instant START = Instant.parse("2026-11-01T04:30:00Z");

    @Test
    void endsAtFourElapsedHoursAcrossMidnightAndDaylightSavingAndIsIdempotent() {
        var org = createOrganization("Duration", "duration");
        var user = createUser("duration@example.com", true);
        var driver = driverRepository.save(new Driver(org, "100", "Test Driver", true));
        var run = sessions.saveAndFlush(DrivingSession.startUnscheduled(org, driver, START, user, DrivingActionMethod.NFC));
        Instant cutoff = START.plus(Duration.ofHours(4));

        // Waiting and confirmations never reset the total elapsed-time limit.
        run.confirmRunning(cutoff.minusSeconds(10), user);
        run.getMovement().acknowledge(cutoff.minusSeconds(10), true);
        assertThat(service.endOverdueSessions(org.getId(), cutoff.minusNanos(1))).isZero();
        assertThat(sessions.findOverdueOrganizationIds(cutoff.minus(DrivingSession.MAX_DURATION)))
                .contains(org.getId());
        assertThat(service.endOverdueSessions(org.getId(), cutoff)).isEqualTo(1);
        assertThat(service.endOverdueSessions(org.getId(), cutoff.plusSeconds(30))).isZero();
        entityManager.flush();
        entityManager.clear();

        var saved = sessions.findById(run.getId()).orElseThrow();
        assertThat(saved.getEndedAt()).isEqualTo(cutoff);
        assertThat(saved.getActualMinutes()).isEqualTo(240);
        assertThat(saved.getEndReason()).isEqualTo(DrivingSessionEndReason.DURATION_LIMIT);
        assertThat(saved.getEndMethod()).isEqualTo(DrivingActionMethod.AUTOMATIC);
        assertThat(saved.getEndedBy()).isNull();
        assertThat(saved.getStartedBy().getId()).isEqualTo(user.getId());
        assertThat(saved.getStartMethod()).isEqualTo(DrivingActionMethod.NFC);
        assertThat(saved.isInactivityReviewNeeded()).isFalse();
        assertThat(UnscheduledDrivingRecord.from(saved, java.time.ZoneId.of("America/Chicago")).status())
                .isEqualTo("Automatically ended after 4 hours");
    }

    @Test
    void catchesOverdueRunsAfterDowntimeWithoutClosingOtherOrganizationsOrRewritingHistory() {
        var org = createOrganization("Old Run", "old-run");
        var other = createOrganization("Other Run", "other-run");
        var user = createUser("catchup@example.com", true);
        var driver = driverRepository.save(new Driver(org, "100", "First Driver", true));
        var otherDriver = driverRepository.save(new Driver(other, "100", "Other Driver", true));
        var old = sessions.save(DrivingSession.startUnscheduled(org, driver, START, user, DrivingActionMethod.QR));
        var current = sessions.save(DrivingSession.startUnscheduled(other, otherDriver, START.plusSeconds(23 * 3600), user, DrivingActionMethod.NFC));
        var finished = DrivingSession.startUnscheduled(org, driver, START.minusSeconds(3600), user, DrivingActionMethod.NFC);
        finished.end(START.minusSeconds(1800), user, DrivingActionMethod.NFC);
        sessions.saveAndFlush(finished);

        Instant nextDay = START.plus(Duration.ofDays(1));
        assertThat(sessions.findOverdueOrganizationIds(nextDay.minus(DrivingSession.MAX_DURATION)))
                .contains(org.getId()).doesNotContain(other.getId());
        assertThat(service.endOverdueSessions(other.getId(), nextDay)).isZero();
        assertThat(service.endOverdueSessions(org.getId(), nextDay)).isEqualTo(1);
        assertThat(old.getEndedAt()).isEqualTo(START.plus(Duration.ofHours(4)));
        assertThat(current.isActive()).isTrue();
        assertThat(finished.getEndedAt()).isEqualTo(START.minusSeconds(1800));
        assertThat(finished.getEndReason()).isEqualTo(DrivingSessionEndReason.COMPLETED);
    }

    @Test
    void aNewStartClosesAnOverdueRunBeforeTheNextBackgroundCheck() {
        var org = createOrganization("Restart", "restart");
        var user = createUser("restart@example.com", true);
        var driver = driverRepository.save(new Driver(org, "100", "Test Driver", true));
        var old = sessions.saveAndFlush(DrivingSession.startUnscheduled(org, driver, START, user, DrivingActionMethod.NFC));
        Instant restartedAt = START.plus(Duration.ofHours(5));
        var current = service.startUnscheduledSession(driver, user, restartedAt, DrivingActionMethod.QR);
        assertThat(old.getActualMinutes()).isEqualTo(240);
        assertThat(old.isActive()).isFalse();
        assertThat(current.isActive()).isTrue();
        assertThat(current.getAutomaticEndAt()).isEqualTo(restartedAt.plus(Duration.ofHours(4)));
        assertThat(sessions.findActiveForUpdateByOrganization(org.getId())).containsExactly(current);
    }

    @Test
    void aLateManualStopIsRecordedAtTheAutomaticCutoff() {
        var org = createOrganization("Late Stop", "late-stop");
        var user = createUser("late-stop@example.com", true);
        var driver = driverRepository.save(new Driver(org, "100", "Test Driver", true));
        var run = sessions.saveAndFlush(DrivingSession.startUnscheduled(org, driver, START, user, DrivingActionMethod.NFC));
        service.endSession(null, run, user, START.plus(Duration.ofHours(6)), DrivingActionMethod.MANAGER);
        assertThat(run.getActualMinutes()).isEqualTo(240);
        assertThat(run.getEndReason()).isEqualTo(DrivingSessionEndReason.DURATION_LIMIT);
        assertThat(run.getEndedBy()).isNull();
    }
}
