package technology.renatus.routepulse.assignments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.PlannedDrivingWindow;
import technology.renatus.routepulse.assignments.service.planning.AssignmentWindowPlanner;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.PickupPriority;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;

class AssignmentWindowPlannerTests {

    private final AssignmentWindowPlanner planner = new AssignmentWindowPlanner();

    @Test
    void reservesOneFifteenMinuteBreakInsideContinuousMorningPlan() {
        DriverAssignment assignment = mock(DriverAssignment.class);
        DriverSchedule schedule = schedule(LocalTime.of(9, 0), LocalTime.of(13, 0));

        boolean applied = planner.applyContinuousPlanWithBreak(
                assignment,
                schedule,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                List.of(
                        demand(LocalTime.of(9, 0)),
                        demand(LocalTime.of(9, 15)),
                        demand(LocalTime.of(9, 30)),
                        demand(LocalTime.of(10, 0)),
                        demand(LocalTime.of(10, 15)),
                        demand(LocalTime.of(10, 30))
                ),
                15
        );

        assertThat(applied).isTrue();
        verify(assignment).updateScheduledTimes(LocalTime.of(9, 0), LocalTime.of(12, 0));

        ArgumentCaptor<List<PlannedDrivingWindow>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignment).replacePlannedDrivingWindows(captor.capture());

        assertThat(captor.getValue())
                .extracting(PlannedDrivingWindow::getStartTime, PlannedDrivingWindow::getEndTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(9, 0), LocalTime.of(9, 45)),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(10, 0), LocalTime.of(12, 0))
                );
    }

    @Test
    void keepsContinuousPlanWhenThereIsNoDemandFreeGap() {
        DriverAssignment assignment = mock(DriverAssignment.class);
        DriverSchedule schedule = schedule(LocalTime.of(9, 0), LocalTime.of(13, 0));

        planner.applyContinuousPlanWithBreak(
                assignment,
                schedule,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                List.of(
                        demand(LocalTime.of(9, 0)),
                        demand(LocalTime.of(9, 15)),
                        demand(LocalTime.of(9, 30)),
                        demand(LocalTime.of(9, 45))
                ),
                15
        );

        ArgumentCaptor<List<PlannedDrivingWindow>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignment).replacePlannedDrivingWindows(captor.capture());

        assertThat(captor.getValue())
                .extracting(PlannedDrivingWindow::getStartTime, PlannedDrivingWindow::getEndTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(9, 0), LocalTime.of(10, 0))
                );
    }

    @Test
    void middayCanUseMultipleWorkRowsForTheSameDriver() {
        DriverAssignment assignment = mock(DriverAssignment.class);

        boolean applied = planner.applyMiddayPlan(
                assignment,
                List.of(
                        schedule(LocalTime.of(12, 0), LocalTime.of(12, 30)),
                        schedule(LocalTime.of(13, 0), LocalTime.of(14, 0))
                ),
                List.of(
                        demand(LocalTime.of(12, 0)),
                        demand(LocalTime.of(13, 15))
                )
        );

        assertThat(applied).isTrue();
        verify(assignment).updateScheduledTimes(LocalTime.of(12, 0), LocalTime.of(13, 30));

        ArgumentCaptor<List<PlannedDrivingWindow>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignment).replacePlannedDrivingWindows(captor.capture());

        assertThat(captor.getValue())
                .extracting(PlannedDrivingWindow::getStartTime, PlannedDrivingWindow::getEndTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(12, 0), LocalTime.of(12, 15)),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(13, 15), LocalTime.of(13, 30))
                );
    }

    @Test
    void middayMergesAdjacentDemandButKeepsSporadicRunsSeparate() {
        DriverAssignment assignment = mock(DriverAssignment.class);
        DriverSchedule schedule = schedule(LocalTime.of(11, 0), LocalTime.of(17, 0));

        boolean applied = planner.applyMiddayPlan(
                assignment,
                schedule,
                List.of(
                        demand(LocalTime.of(12, 0)),
                        demand(LocalTime.of(12, 15)),
                        demand(LocalTime.of(13, 15)),
                        demand(LocalTime.of(14, 0)),
                        demand(LocalTime.of(14, 15))
                )
        );

        assertThat(applied).isTrue();
        verify(assignment).updateScheduledTimes(LocalTime.of(12, 0), LocalTime.of(14, 30));

        ArgumentCaptor<List<PlannedDrivingWindow>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignment).replacePlannedDrivingWindows(captor.capture());

        assertThat(captor.getValue())
                .extracting(PlannedDrivingWindow::getStartTime, PlannedDrivingWindow::getEndTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(12, 0), LocalTime.of(12, 30)),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(13, 15), LocalTime.of(13, 30)),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(14, 0), LocalTime.of(14, 30))
                );
    }

    private DriverSchedule schedule(LocalTime start, LocalTime end) {
        return new DriverSchedule(
                1L,
                "100",
                "Driver",
                LocalDate.of(2026, 9, 10),
                start,
                end
        );
    }

    private TransportationDemandWindow demand(LocalTime start) {
        return new TransportationDemandWindow(
                start,
                start.plusMinutes(15),
                1,
                0,
                1,
                PickupPriority.LOW
        );
    }
}
