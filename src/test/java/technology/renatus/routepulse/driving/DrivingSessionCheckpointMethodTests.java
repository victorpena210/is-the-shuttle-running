package technology.renatus.routepulse.driving;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.domain.DrivingSessionEndReason;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.model.Organization;

class DrivingSessionCheckpointMethodTests {

    @Test
    void recordsNfcPauseAsItsOwnPhysicalMethodAndEndReason() {
        Fixture fixture = fixture();
        Instant startedAt = Instant.parse("2026-09-10T14:00:00Z");

        DrivingSession session = DrivingSession.start(
                fixture.assignment(),
                fixture.driver(),
                startedAt,
                fixture.user(),
                DrivingActionMethod.NFC);

        session.end(
                startedAt.plusSeconds(15 * 60),
                fixture.user(),
                DrivingActionMethod.NFC,
                DrivingSessionEndReason.PAUSE);

        assertEquals(DrivingActionMethod.NFC, session.getStartMethod());
        assertEquals(DrivingActionMethod.NFC, session.getEndMethod());
        assertEquals(DrivingSessionEndReason.PAUSE, session.getEndReason());
    }

    @Test
    void legacyEndOverloadStillDefaultsToCompleted() {
        Fixture fixture = fixture();
        Instant startedAt = Instant.parse("2026-09-10T14:00:00Z");

        DrivingSession session = DrivingSession.start(
                fixture.assignment(),
                fixture.driver(),
                startedAt,
                fixture.user(),
                DrivingActionMethod.QR);

        session.end(
                startedAt.plusSeconds(15 * 60),
                fixture.user(),
                DrivingActionMethod.QR);

        assertEquals(DrivingSessionEndReason.COMPLETED, session.getEndReason());
    }

    private Fixture fixture() {
        Organization organization = new Organization(
                "Checkpoint Test",
                "Checkpoint Test",
                "checkpoint-test",
                OrganizationStatus.ACTIVE,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"));

        Driver driver = new Driver(
                organization,
                "100",
                "Test Driver",
                true);

        UserAccount user = new UserAccount(
                "driver@example.com",
                "unused-test-hash",
                "Test",
                "Driver",
                true,
                true);

        DriverAssignment assignment = DriverAssignment.automatic(
                LocalDate.of(2026, 9, 10),
                ShuttlePeriod.MORNING,
                driver);

        return new Fixture(driver, user, assignment);
    }

    private record Fixture(
            Driver driver,
            UserAccount user,
            DriverAssignment assignment) {
    }
}
