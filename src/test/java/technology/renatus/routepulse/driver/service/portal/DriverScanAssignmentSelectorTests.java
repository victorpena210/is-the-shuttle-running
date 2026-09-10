package technology.renatus.routepulse.driver.service.portal;

import static org.assertj.core.api.Assertions.assertThat;
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

class DriverScanAssignmentSelectorTests {

    private final DriverScanAssignmentSelector selector =
            new DriverScanAssignmentSelector();

    @Test
    void resumesEndedAssignmentWhileStillInsideItsScheduledWindow() {
        Driver driver = driver(10L);
        DriverAssignment morning = assignment(
                ShuttlePeriod.MORNING,
                driver,
                driver,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                true);
        DriverAssignment afternoon = assignment(
                ShuttlePeriod.AFTERNOON,
                driver,
                driver,
                LocalTime.of(17, 0),
                LocalTime.of(21, 0),
                false);

        DriverAssignment selected = selector.selectStartAssignment(
                driver,
                List.of(morning, afternoon),
                LocalDateTime.of(2026, 9, 9, 10, 18));

        assertThat(selected).isSameAs(morning);
    }

    @Test
    void movesToNextAssignmentAfterEarlierScheduledWindowHasEnded() {
        Driver driver = driver(10L);
        DriverAssignment morning = assignment(
                ShuttlePeriod.MORNING,
                driver,
                driver,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                true);
        DriverAssignment afternoon = assignment(
                ShuttlePeriod.AFTERNOON,
                driver,
                driver,
                LocalTime.of(17, 0),
                LocalTime.of(21, 0),
                false);

        DriverAssignment selected = selector.selectStartAssignment(
                driver,
                List.of(morning, afternoon),
                LocalDateTime.of(2026, 9, 9, 17, 5));

        assertThat(selected).isSameAs(afternoon);
    }


    @Test
    void startsScanningDriversFreshScheduledAssignmentInsteadOfSomeoneElsesPausedShift() {
        Driver scanningDriver = driver(10L);
        Driver morningDriver = driver(20L);

        DriverAssignment pausedMorning = assignment(
                ShuttlePeriod.MORNING,
                morningDriver,
                morningDriver,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                true);
        DriverAssignment freshMidday = assignment(
                ShuttlePeriod.MIDDAY,
                scanningDriver,
                scanningDriver,
                LocalTime.of(10, 0),
                LocalTime.of(13, 0),
                false);

        DriverAssignment selected = selector.selectStartAssignment(
                scanningDriver,
                List.of(pausedMorning, freshMidday),
                LocalDateTime.of(2026, 9, 9, 10, 18));

        assertThat(selected).isSameAs(freshMidday);
    }

    @Test
    void prefersTheEndedAssignmentMostRecentlyDrivenByScanningDriver() {
        Driver scanningDriver = driver(10L);
        Driver anotherDriver = driver(20L);

        DriverAssignment first = assignment(
                ShuttlePeriod.MORNING,
                anotherDriver,
                scanningDriver,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                true);
        DriverAssignment second = assignment(
                ShuttlePeriod.MIDDAY,
                anotherDriver,
                anotherDriver,
                LocalTime.of(10, 0),
                LocalTime.of(13, 0),
                true);

        DriverAssignment selected = selector.selectStartAssignment(
                scanningDriver,
                List.of(first, second),
                LocalDateTime.of(2026, 9, 9, 10, 18));

        assertThat(selected).isSameAs(first);
    }

    @Test
    void keepsMiddayAssignmentSelectableBetweenDetailedWindowsSoPolicyCanGiveNextStart() {
        Driver driver = driver(10L);
        DriverAssignment midday = assignment(
                ShuttlePeriod.MIDDAY,
                driver,
                driver,
                LocalTime.of(12, 0),
                LocalTime.of(14, 30),
                true);

        when(midday.hasPlannedDrivingWindows()).thenReturn(true);
        when(midday.hasRemainingPlannedDrivingWindowAtOrAfter(LocalTime.of(12, 45))).thenReturn(true);
        when(midday.getPlannedDrivingWindows()).thenReturn(List.of(
                new PlannedDrivingWindow(LocalTime.of(12, 0), LocalTime.of(12, 30)),
                new PlannedDrivingWindow(LocalTime.of(13, 15), LocalTime.of(13, 30))
        ));

        DriverAssignment selected = selector.selectStartAssignment(
                driver,
                List.of(midday),
                LocalDateTime.of(2026, 9, 9, 12, 45));

        assertThat(selected).isSameAs(midday);
    }

    private Driver driver(Long id) {
        Driver driver = mock(Driver.class);
        when(driver.getId()).thenReturn(id);
        return driver;
    }

    private DriverAssignment assignment(
            ShuttlePeriod period,
            Driver scheduledDriver,
            Driver finalDriver,
            LocalTime start,
            LocalTime end,
            boolean hasSessions) {

        DriverAssignment assignment = mock(DriverAssignment.class);
        when(assignment.getPeriod()).thenReturn(period);
        when(assignment.getScheduledDriver()).thenReturn(scheduledDriver);
        when(assignment.getFinalDriver()).thenReturn(finalDriver);
        when(assignment.getScheduledStartTime()).thenReturn(start);
        when(assignment.getScheduledEndTime()).thenReturn(end);
        when(assignment.isDriving()).thenReturn(false);
        when(assignment.hasDrivingSessions()).thenReturn(hasSessions);
        return assignment;
    }
}
