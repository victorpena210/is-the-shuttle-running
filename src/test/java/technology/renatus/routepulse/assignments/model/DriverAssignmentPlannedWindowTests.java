package technology.renatus.routepulse.assignments.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.model.Organization;

class DriverAssignmentPlannedWindowTests {

    @Test
    void protectedBreakIsExcludedFromScheduledDrivingMinutes() {
        DriverAssignment assignment = assignment(ShuttlePeriod.MORNING);
        assignment.updateScheduledTimes(LocalTime.of(9, 0), LocalTime.of(12, 0));
        assignment.replacePlannedDrivingWindows(List.of(
                new PlannedDrivingWindow(LocalTime.of(9, 0), LocalTime.of(10, 30)),
                new PlannedDrivingWindow(LocalTime.of(10, 45), LocalTime.of(12, 0))
        ));

        assertThat(assignment.getScheduledMinutes()).isEqualTo(165);
        assertThat(assignment.getPlannedBreakWindows()).hasSize(1);
        assertThat(assignment.getPlannedBreakWindows().get(0).getMinutes()).isEqualTo(15);
    }

    @Test
    void middayScheduledMinutesCountOnlyExplicitDrivingRuns() {
        DriverAssignment assignment = assignment(ShuttlePeriod.MIDDAY);
        assignment.updateScheduledTimes(LocalTime.of(12, 0), LocalTime.of(14, 30));
        assignment.replacePlannedDrivingWindows(List.of(
                new PlannedDrivingWindow(LocalTime.of(12, 0), LocalTime.of(12, 30)),
                new PlannedDrivingWindow(LocalTime.of(13, 15), LocalTime.of(13, 30)),
                new PlannedDrivingWindow(LocalTime.of(14, 0), LocalTime.of(14, 15))
        ));

        assertThat(assignment.getScheduledMinutes()).isEqualTo(60);
        assertThat(assignment.getScheduledHours()).isEqualTo(1.0);
    }

    private DriverAssignment assignment(ShuttlePeriod period) {
        Organization organization = mock(Organization.class);
        Driver driver = mock(Driver.class);
        when(driver.getOrganization()).thenReturn(organization);

        return DriverAssignment.automatic(
                LocalDate.of(2026, 9, 10),
                period,
                driver
        );
    }
}
