package technology.renatus.routepulse.driver.service.portal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.PlannedDrivingWindow;
import technology.renatus.routepulse.driver.model.Driver;

class DriverQrStartPolicyTests {

    private final DriverQrStartPolicy policy = new DriverQrStartPolicy();

    @Test
    void blocksScheduledDriverBeforeScheduledStart() {
        Driver scheduledDriver = driver(10L);
        DriverAssignment assignment = assignment(scheduledDriver, LocalTime.of(9, 0));

        assertThatThrownBy(() -> policy.requireStartAllowed(
                scheduledDriver,
                assignment,
                LocalDateTime.of(2026, 9, 9, 8, 45)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Your driving assignment begins at 9:00 AM. Start when you clock in.");
    }

    @Test
    void allowsScheduledDriverAtScheduledStart() {
        Driver scheduledDriver = driver(10L);
        DriverAssignment assignment = assignment(scheduledDriver, LocalTime.of(9, 0));

        assertThatCode(() -> policy.requireStartAllowed(
                scheduledDriver,
                assignment,
                LocalDateTime.of(2026, 9, 9, 9, 0)
        )).doesNotThrowAnyException();
    }

    @Test
    void allowsScheduledDriverAfterScheduledStart() {
        Driver scheduledDriver = driver(10L);
        DriverAssignment assignment = assignment(scheduledDriver, LocalTime.of(9, 0));

        assertThatCode(() -> policy.requireStartAllowed(
                scheduledDriver,
                assignment,
                LocalDateTime.of(2026, 9, 9, 9, 12)
        )).doesNotThrowAnyException();
    }

    @Test
    void allowsDifferentEligibleDriverToUseExistingFillInOverride() {
        Driver scheduledDriver = driver(10L);
        Driver fillInDriver = driver(20L);
        DriverAssignment assignment = assignment(scheduledDriver, LocalTime.of(9, 0));

        assertThatCode(() -> policy.requireStartAllowed(
                fillInDriver,
                assignment,
                LocalDateTime.of(2026, 9, 9, 8, 45)
        )).doesNotThrowAnyException();
    }

    @Test
    void blocksScheduledDriverDuringProtectedBreakAndGivesResumeTime() {
        Driver scheduledDriver = driver(10L);
        DriverAssignment assignment = assignment(scheduledDriver, LocalTime.of(9, 0));
        when(assignment.hasPlannedDrivingWindows()).thenReturn(true);
        when(assignment.getPeriod()).thenReturn(ShuttlePeriod.MORNING);
        when(assignment.isInsidePlannedDrivingWindow(LocalTime.of(10, 35))).thenReturn(false);
        when(assignment.getPlannedDrivingWindows()).thenReturn(List.of(
                new PlannedDrivingWindow(LocalTime.of(9, 0), LocalTime.of(10, 30)),
                new PlannedDrivingWindow(LocalTime.of(10, 45), LocalTime.of(12, 0))
        ));
        when(assignment.getPlannedBreakWindows()).thenReturn(List.of(
                new PlannedDrivingWindow(LocalTime.of(10, 30), LocalTime.of(10, 45))
        ));

        assertThatThrownBy(() -> policy.requireStartAllowed(
                scheduledDriver,
                assignment,
                LocalDateTime.of(2026, 9, 9, 10, 35)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This is your planned break. Resume driving at 10:45 AM.");
    }

    @Test
    void blocksMiddayDriverBetweenSporadicWindowsAndGivesNextStart() {
        Driver scheduledDriver = driver(10L);
        DriverAssignment assignment = assignment(scheduledDriver, LocalTime.of(12, 0));
        when(assignment.hasPlannedDrivingWindows()).thenReturn(true);
        when(assignment.getPeriod()).thenReturn(ShuttlePeriod.MIDDAY);
        when(assignment.getScheduledStartTime()).thenReturn(LocalTime.of(12, 0));
        when(assignment.isInsidePlannedDrivingWindow(LocalTime.of(12, 45))).thenReturn(false);
        when(assignment.getPlannedDrivingWindows()).thenReturn(List.of(
                new PlannedDrivingWindow(LocalTime.of(12, 0), LocalTime.of(12, 30)),
                new PlannedDrivingWindow(LocalTime.of(13, 15), LocalTime.of(13, 30))
        ));
        when(assignment.getPlannedBreakWindows()).thenReturn(List.of(
                new PlannedDrivingWindow(LocalTime.of(12, 30), LocalTime.of(13, 15))
        ));

        assertThatThrownBy(() -> policy.requireStartAllowed(
                scheduledDriver,
                assignment,
                LocalDateTime.of(2026, 9, 9, 12, 45)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Midday driving is not scheduled right now. Your next driving window begins at 1:15 PM.");
    }

    @Test
    void allowsScheduledDriverInsideDetailedMiddayWindow() {
        Driver scheduledDriver = driver(10L);
        DriverAssignment assignment = assignment(scheduledDriver, LocalTime.of(12, 0));
        when(assignment.hasPlannedDrivingWindows()).thenReturn(true);
        when(assignment.isInsidePlannedDrivingWindow(LocalTime.of(13, 20))).thenReturn(true);

        assertThatCode(() -> policy.requireStartAllowed(
                scheduledDriver,
                assignment,
                LocalDateTime.of(2026, 9, 9, 13, 20)
        )).doesNotThrowAnyException();
    }

    private Driver driver(Long id) {
        Driver driver = mock(Driver.class);
        when(driver.getId()).thenReturn(id);
        return driver;
    }

    private DriverAssignment assignment(Driver scheduledDriver, LocalTime startTime) {
        DriverAssignment assignment = mock(DriverAssignment.class);
        when(assignment.getScheduledDriver()).thenReturn(scheduledDriver);
        when(assignment.getScheduledStartTime()).thenReturn(startTime);
        return assignment;
    }
}
