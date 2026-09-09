package technology.renatus.routepulse.driver.service.portal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
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
