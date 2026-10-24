package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.model.DrivingSession;

class AutomaticScheduleHistoryTests {
    private final ZoneId zone=ZoneId.of("America/Chicago");
    private DrivingSession session(Instant start,Instant end) {
        var driver=mock(Driver.class);when(driver.getId()).thenReturn(1L);when(driver.getName()).thenReturn("Sample Driver");
        var session=mock(DrivingSession.class);when(session.getDriver()).thenReturn(driver);
        when(session.getId()).thenReturn(1L);when(session.getStartedAt()).thenReturn(start);
        when(session.getEndedAt()).thenReturn(end);when(session.isActive()).thenReturn(end==null);
        when(session.getAutomaticEndAt()).thenReturn(start.plusSeconds(14400));
        return session;
    }
    @Test void recordsKeepTheirActualTimesAndReflectCorrectionsWithoutFillingTheOpeningWindow() {
        var day=LocalDate.of(2026,10,24);var start=day.atTime(9,20).atZone(zone).toInstant();
        var run=session(start,day.atTime(10,50).atZone(zone).toInstant());var now=day.atTime(18,0).atZone(zone).toInstant();
        var first=AutomaticScheduleHistory.forDay(List.of(run),day,zone,now).get(0);
        assertThat(first.start().toLocalTime()).isEqualTo(LocalTime.of(9,20));
        assertThat(first.end().toLocalTime()).isEqualTo(LocalTime.of(10,50));assertThat(first.minutes()).isEqualTo(90);
        when(run.getEndedAt()).thenReturn(day.atTime(12,10).atZone(zone).toInstant());when(run.getTimeReviewedAt()).thenReturn(now);
        var corrected=AutomaticScheduleHistory.forDay(List.of(run),day,zone,now).get(0);
        assertThat(corrected.minutes()).isEqualTo(170);assertThat(corrected.state()).isEqualTo("Manager reviewed");
    }
    @Test void overnightRecordsSplitAtLocalMidnightAndCountTheRepeatedDstHour() {
        var day=LocalDate.of(2026,10,31);
        var run=session(Instant.parse("2026-11-01T04:30:00Z"),Instant.parse("2026-11-01T08:30:00Z"));
        var now=Instant.parse("2026-11-02T00:00:00Z");
        var first=AutomaticScheduleHistory.forDay(List.of(run),day,zone,now).get(0);
        var second=AutomaticScheduleHistory.forDay(List.of(run),day.plusDays(1),zone,now).get(0);
        assertThat(first.minutes()).isEqualTo(30);assertThat(second.minutes()).isEqualTo(210);
        assertThat(second.start().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(second.end().toLocalTime()).isEqualTo(LocalTime.of(2,30));
        assertThat(second.continuedFromPreviousDay()).isTrue();assertThat(first.continuedFromPreviousDay()).isFalse();
    }
    @Test void activeRunsShowOnlyElapsedTimeAndOverdueRunsStopAtTheExistingLimit() {
        var day=LocalDate.of(2026,10,24);var start=day.atTime(17,0).atZone(zone).toInstant();
        var run=session(start,null);var now=start.plusSeconds(3600);
        var active=AutomaticScheduleHistory.forDay(List.of(run),day,zone,now).get(0);
        assertThat(active.minutes()).isEqualTo(60);assertThat(active.inProgress()).isTrue();
        var late=start.plusSeconds(18000);when(run.hasReachedDurationLimit(late)).thenReturn(true);
        var capped=AutomaticScheduleHistory.forDay(List.of(run),day,zone,late).get(0);
        assertThat(capped.minutes()).isEqualTo(240);assertThat(capped.inProgress()).isFalse();
        assertThat(capped.state()).isEqualTo("Duration limit reached");
        assertThat(AutomaticScheduleHistory.forDay(List.of(run),day.plusDays(1),zone,late)).isEmpty();
        var reviewed=session(start,late);when(reviewed.getTimeReviewedAt()).thenReturn(late);
        var approved=AutomaticScheduleHistory.forDay(List.of(reviewed),day,zone,late).get(0);
        assertThat(approved.minutes()).isEqualTo(300);assertThat(approved.state()).isEqualTo("Manager reviewed");
    }
}
