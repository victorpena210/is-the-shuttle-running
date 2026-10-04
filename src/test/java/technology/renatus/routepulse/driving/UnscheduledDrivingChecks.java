package technology.renatus.routepulse.driving;

import java.time.*;
import java.util.List;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverScanAssignmentSelector;
import technology.renatus.routepulse.driving.domain.*;
import technology.renatus.routepulse.driving.model.*;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.reporting.workload.service.WorkloadCalculator;

/** Framework-independent domain checks, also run by the JUnit suite. */
public final class UnscheduledDrivingChecks {
    public static void main(String[] args) throws Exception {
        runAll();
        System.out.println("Unscheduled driving domain checks passed: audit data, duration, lifecycle, organization isolation, reporting, selector and scheduled regression.");
    }

    static void runAll() throws Exception {
        Instant start = Instant.parse("2026-10-04T15:00:00Z");
        Organization org = new Organization("Test", "Test", "test", OrganizationStatus.ACTIVE, null, null);
        id(org, 1L);
        Driver driver = new Driver(org, "100", "Test Driver", true);
        id(driver, 10L);
        UserAccount user = new UserAccount("driver@example.com", "hash", "Test", "Driver", true, true);
        id(user, 100L);
        DrivingSession session = DrivingSession.startUnscheduled(org, driver, start, user, DrivingActionMethod.NFC);
        require(session.isUnscheduled() && session.getAssignment() == null, "Unscheduled session must not invent an assignment");
        require(session.getOrganization() == org && session.getDriver() == driver && session.getStartedBy() == user,
                "The organization, actual driver and actor must be preserved");
        require(session.getStartMethod() == DrivingActionMethod.NFC && session.isActive(), "NFC start must remain active");
        require(session.getActualMinutes() == 0, "Active duration must not be reported as final");
        expect(IllegalStateException.class, () -> session.end(start.minusSeconds(1), user, DrivingActionMethod.NFC));
        session.end(start.plusSeconds(2700), user, DrivingActionMethod.QR, DrivingSessionEndReason.PAUSE);
        require(session.getActualMinutes() == 45 && session.getEndReason() == DrivingSessionEndReason.PAUSE,
                "Pause must retain its completed duration");
        require(session.getEndMethod() == DrivingActionMethod.QR && session.getEndedBy() == user, "End audit must be recorded");
        expect(IllegalStateException.class, () -> session.end(start.plusSeconds(3600), user, DrivingActionMethod.NFC));
        var record = UnscheduledDrivingRecord.from(session, ZoneId.of("America/Chicago"));
        require(record.startedAt().getHour() == 10 && record.actualMinutes() == 45 && record.status().equals("Paused"),
                "History must use the operations time zone and actual duration");
        var confirmation = new PassengerRideConfirmation(session, "test-device-hash", start, start.plusSeconds(60));
        require(confirmation.getAssignment() == null && confirmation.getOrganization() == org
                && confirmation.getDriver() == driver, "Unscheduled passenger confirmation must retain organization and driver");
        Organization other = new Organization("Other", "Other", "other", OrganizationStatus.ACTIVE, null, null);
        id(other, 2L);
        expect(IllegalArgumentException.class, () -> DrivingSession.startUnscheduled(other, driver, start, user, DrivingActionMethod.NFC));
        LocalDate date = LocalDate.of(2026, 10, 4);
        var summary = new WorkloadCalculator().calculate(date, date, null, List.of(driver), List.of(), List.of(session));
        require(summary.getActualMinutes() == 45 && summary.getScheduledMinutes() == 0
                && summary.getScheduledShifts() == 0 && summary.getShiftsDriven() == 0,
                "Unscheduled work adds actual time without fabricating scheduled assignments or shifts");
        var selector = new DriverScanAssignmentSelector();
        expect(DriverScanAssignmentSelector.NoStartableAssignmentException.class,
                () -> selector.selectStartAssignment(driver, List.of(), date.atTime(10, 0)));
        DriverAssignment assignment = DriverAssignment.automatic(date, ShuttlePeriod.MORNING, driver);
        DrivingSession scheduled = DrivingSession.start(assignment, driver, start, user, DrivingActionMethod.QR);
        require(!scheduled.isUnscheduled() && scheduled.getAssignment() == assignment, "Scheduled sessions must keep their assignment");
        require(selector.selectStartAssignment(driver, List.of(assignment), date.atTime(10, 0)) == assignment,
                "An available scheduled assignment must still be selected");
    }

    private static void id(Object target, Long value) throws Exception {
        var field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try { action.run(); } catch (RuntimeException exception) {
            if (type.isInstance(exception)) return;
            throw new AssertionError("Unexpected exception", exception);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
