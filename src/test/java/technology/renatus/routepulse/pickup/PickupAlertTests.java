package technology.renatus.routepulse.pickup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.model.*;
import technology.renatus.routepulse.scheduling.automatic.*;

class PickupAlertTests {
    private final ZoneId zone=ZoneId.of("America/Chicago");
    private final LocalDate day=LocalDate.of(2026,10,30);
    private AutomaticScheduleDay.Slot slot(int start,int end,Long driver) {
        return new AutomaticScheduleDay.Slot(day.atTime(start,0),day.atTime(end,0),driver,"Driver "+driver,"Assigned");
    }
    private PickupStore.Row row(LocalDateTime time) { return new PickupStore.Row(5L,"Alex",time.atZone(zone),"Main gate",false,time.atZone(zone),false,false); }
    private PickupAlerts.Alert route(LocalDateTime time,List<AutomaticScheduleDay.Slot> slots) {
        return PickupAlerts.route(row(time),slots,null,Set.of(1L,2L),PickupOutcome.Status.PENDING,zone,time.atZone(zone).toInstant().minusSeconds(900));
    }
    @Test void handoffHasOneOwnerAndNearbyDriverSeesHeadsUp() {
        var first=slot(19,21,1L);var next=slot(21,23,2L);
        var alert=route(day.atTime(21,0),List.of(first,next));
        assertThat(alert.driverId()).isEqualTo(2L);assertThat(alert.reminderDriverId()).isEqualTo(2L);
        assertThat(alert.related()).containsExactly(first,next);
        assertThat(alert.nearSlot(first)).isTrue();assertThat(alert.nearSlot(next)).isFalse();
    }
    @Test void nearbyTimesNeverExtendAssignmentAndFifteenMinutesIsInclusive() {
        var slot=slot(19,21,1L);
        for(var time:List.of(day.atTime(18,45),day.atTime(21,0),day.atTime(21,15))) {
            var alert=route(time,List.of(slot));assertThat(alert.needsCoverage()).isTrue();
            assertThat(alert.reminderDriverId()).isEqualTo(1L);
            assertThat(PickupReminder.summary(alert)).contains("Needs coverage","has not been extended");
        }
        assertThat(route(day.atTime(18,44,59),List.of(slot)).related()).isEmpty();
        assertThat(route(day.atTime(21,15,1),List.of(slot)).reminderDriverId()).isNull();
        assertThat(route(day.atTime(20,0),List.of(slot)).driverId()).isEqualTo(1L);
    }
    @Test void activeSubstituteOwnsOnlyTheCurrentCoveredWindow() {
        var first=slot(19,21,1L);var next=slot(21,23,1L);var time=day.atTime(20,15).atZone(zone).toInstant();
        var active=mock(DrivingSession.class);var substitute=mock(Driver.class);
        when(active.isActive()).thenReturn(true);when(active.getStartedAt()).thenReturn(time.minusSeconds(3600));
        when(active.getAutomaticEndAt()).thenReturn(time.plusSeconds(7200));when(active.getDriver()).thenReturn(substitute);
        when(substitute.getId()).thenReturn(2L);when(substitute.getName()).thenReturn("Covering driver");
        when(active.getCoveragePlan()).thenReturn(List.of(new DrivingCoverageWindow(day,"EMPLOYEE_PICKUP",1L,
                first.getStart().atZone(zone).toInstant(),first.getEnd().atZone(zone).toInstant())));
        var alert=PickupAlerts.route(row(day.atTime(20,15)),List.of(first,next),active,Set.of(1L,2L),PickupOutcome.Status.PENDING,zone,time.minusSeconds(900));
        assertThat(alert.driverId()).isEqualTo(2L);assertThat(alert.coverageLabel()).contains("Covering driver");
        var handoff=PickupAlerts.route(row(day.atTime(21,0)),List.of(first,next),active,Set.of(1L,2L),PickupOutcome.Status.PENDING,zone,day.atTime(20,45).atZone(zone).toInstant());
        assertThat(handoff.driverId()).isEqualTo(1L);
        when(active.getAutomaticEndAt()).thenReturn(time);
        assertThat(PickupAlerts.route(row(day.atTime(20,15)),List.of(first),active,Set.of(1L,2L),PickupOutcome.Status.PENDING,zone,time.minusSeconds(900)).driverId()).isEqualTo(1L);
    }
    @Test void unassignedAndInactiveDriversRemainCoverageGaps() {
        var missing=slot(19,21,null);
        assertThat(route(day.atTime(20,0),List.of(missing)).needsCoverage()).isTrue();
        assertThat(route(day.atTime(20,0),List.of(missing)).reminderDriverId()).isNull();
        var disabled=PickupAlerts.route(row(day.atTime(20,0)),List.of(slot(19,21,1L)),null,Set.of(),PickupOutcome.Status.PENDING,zone,Instant.now());
        assertThat(disabled.needsCoverage()).isTrue();
    }
    @Test void overnightAndDaylightSavingTimesUseInstantsAndResolvedPickupsDoNotAlert() {
        for(var date:List.of(day,LocalDate.of(2026,11,1))) {
            var slot=new AutomaticScheduleDay.Slot(date.atTime(23,0),date.plusDays(1).atTime(1,0),1L,"Driver","Assigned");
            var row=row(date.plusDays(1).atTime(0,5));var due=row.clockOut().toInstant().minusSeconds(900);
            assertThat(PickupAlerts.route(row,List.of(slot),null,Set.of(1L),PickupOutcome.Status.PENDING,zone,due).driverId()).isEqualTo(1L);
            assertThat(PickupAlerts.route(row,List.of(slot),null,Set.of(1L),PickupOutcome.Status.PENDING,zone,due.minusSeconds(1)).due()).isFalse();
            assertThat(PickupAlerts.route(row,List.of(slot),null,Set.of(1L),PickupOutcome.Status.PICKED_UP,zone,due).due()).isFalse();
            assertThat(PickupAlerts.route(row,List.of(slot),null,Set.of(1L),PickupOutcome.Status.NOT_NEEDED,zone,due).due()).isFalse();
        }
    }
    @Test void emailsIncludePickupDetailsAndUncertainSubmissionsNeverReplay() {
        var alert=route(day.atTime(20,0),List.of(slot(19,21,1L)));
        var reminder=new DrivingReminder(1L,"driver@example.com","Driver","Test",day.atTime(19,0),day.atTime(23,0),zone,List.of(),List.of(alert));
        assertThat(reminder.body("https://example.com")).contains("Upcoming pickup assistance","Alex","Main gate","Scheduled off","not confirmed pickup requests");
        var delivery=new PickupReminderDelivery(1L,5L,alert.row().clockOut().toInstant(),1L);var now=Instant.now();
        assertThat(delivery.canAttempt(now)).isTrue();delivery.claim(now);assertThat(delivery.canAttempt(now)).isFalse();
        delivery.failed(true);assertThat(delivery.canAttempt(now.plusSeconds(59))).isFalse();assertThat(delivery.canAttempt(now.plusSeconds(60))).isTrue();
        delivery.failed(false);assertThat(delivery.canAttempt(now.plusSeconds(120))).isFalse();
        delivery.sent(now);assertThat(delivery.canAttempt(now.plusSeconds(120))).isFalse();
    }
}
