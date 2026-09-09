package technology.renatus.routepulse.attention.service.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.attention.model.AttentionItem;
import technology.renatus.routepulse.attention.model.AttentionSeverity;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;

class LiveOperationsAttentionCheckerTests {

    private static final LocalDate OPERATING_DATE = LocalDate.of(2026, 9, 9);

    @Test
    void warnsAsSoonAsScheduledStartPassesButBeforeGraceExpires() {
        LiveOperationsAttentionChecker checker =
                new LiveOperationsAttentionChecker(10, 10);

        DriverAssignment assignment = assignmentAt(LocalTime.of(9, 0));
        List<AttentionItem> items = new ArrayList<>();

        checker.addAlerts(
                planWith(assignment),
                LocalDateTime.of(OPERATING_DATE, LocalTime.of(9, 5)),
                items
        );

        assertThat(items).hasSize(1);
        AttentionItem item = items.get(0);
        assertThat(item.severity()).isEqualTo(AttentionSeverity.WARNING);
        assertThat(item.title()).isEqualTo("Morning shuttle has not started");
        assertThat(item.detail())
                .contains("5 minutes past the planned start")
                .contains("urgent at 9:10 AM");
    }

    @Test
    void becomesUrgentWhenConfiguredGracePeriodExpires() {
        LiveOperationsAttentionChecker checker =
                new LiveOperationsAttentionChecker(10, 10);

        DriverAssignment assignment = assignmentAt(LocalTime.of(9, 0));
        List<AttentionItem> items = new ArrayList<>();

        checker.addAlerts(
                planWith(assignment),
                LocalDateTime.of(OPERATING_DATE, LocalTime.of(9, 10)),
                items
        );

        assertThat(items).hasSize(1);
        AttentionItem item = items.get(0);
        assertThat(item.severity()).isEqualTo(AttentionSeverity.URGENT);
        assertThat(item.detail())
                .contains("10 minutes past the planned start")
                .contains("manager review is required");
    }

    @Test
    void clearsNoStartAlertAsSoonAsAnyDrivingSessionExists() {
        LiveOperationsAttentionChecker checker =
                new LiveOperationsAttentionChecker(10, 10);

        DriverAssignment assignment = assignmentAt(LocalTime.of(9, 0));
        when(assignment.hasDrivingSessions()).thenReturn(true);
        when(assignment.getAssignmentOutcome()).thenReturn(AssignmentOutcome.IN_PROGRESS);

        List<AttentionItem> items = new ArrayList<>();

        checker.addAlerts(
                planWith(assignment),
                LocalDateTime.of(OPERATING_DATE, LocalTime.of(9, 15)),
                items
        );

        assertThat(items).isEmpty();
    }

    @Test
    void doesNotEvaluateNoStartConditionBeforeScheduledTime() {
        LiveOperationsAttentionChecker checker =
                new LiveOperationsAttentionChecker(10, 10);

        DriverAssignment assignment = assignmentAt(LocalTime.of(9, 0));
        List<AttentionItem> items = new ArrayList<>();

        checker.addAlerts(
                planWith(assignment),
                LocalDateTime.of(OPERATING_DATE, LocalTime.of(8, 59)),
                items
        );

        assertThat(items).isEmpty();
    }

    private DriverAssignment assignmentAt(LocalTime start) {
        DriverAssignment assignment = mock(DriverAssignment.class);
        Driver driver = mock(Driver.class);

        when(assignment.getId()).thenReturn(42L);
        when(assignment.getAssignmentDate()).thenReturn(OPERATING_DATE);
        when(assignment.getPeriod()).thenReturn(ShuttlePeriod.MORNING);
        when(assignment.getScheduledStartTime()).thenReturn(start);
        when(assignment.getScheduledEndTime()).thenReturn(LocalTime.of(12, 0));
        when(assignment.getAssignmentOutcome()).thenReturn(AssignmentOutcome.PENDING);
        when(assignment.hasDrivingSessions()).thenReturn(false);
        when(assignment.isDriving()).thenReturn(false);
        when(assignment.getScheduledDriver()).thenReturn(driver);
        when(driver.getName()).thenReturn("Maria");

        return assignment;
    }

    private DashboardPlan planWith(DriverAssignment assignment) {
        return new DashboardPlan(
                OPERATING_DATE,
                OPERATING_DATE,
                List.of(OPERATING_DATE),
                true,
                true,
                List.of(),
                TransportationDemandAnalysis.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(assignment),
                null,
                false,
                null,
                new ParkOperatingDay(
                        OPERATING_DATE,
                        ParkOperatingStatus.OPEN,
                        LocalTime.of(11, 0),
                        LocalTime.of(20, 0),
                        false,
                        "TEST",
                        null
                ),
                "Open",
                false
        );
    }
}
