package technology.renatus.routepulse.reporting.rankings;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;

class DriverRankingServiceTests {
    private final DrivingSessionRepository sessions=mock(DrivingSessionRepository.class);
    private final DriverRepository drivers=mock(DriverRepository.class);
    private final DriverRankingService service=new DriverRankingService(sessions,drivers,"America/Chicago");
    private final Instant now=Instant.parse("2026-10-24T23:00:00Z");
    private Driver driver(long id,String name,boolean active) {
        var d=mock(Driver.class);when(d.getId()).thenReturn(id);when(d.getName()).thenReturn(name);when(d.isActive()).thenReturn(active);return d;
    }
    private DrivingSession run(Driver d,String start,String end) {
        var s=mock(DrivingSession.class);var began=Instant.parse(start);
        when(s.getDriver()).thenReturn(d);when(s.getStartedAt()).thenReturn(began);
        when(s.isActive()).thenReturn(end==null);when(s.getAutomaticEndAt()).thenReturn(began.plus(Duration.ofHours(4)));
        when(s.getEndedAt()).thenReturn(end==null?null:Instant.parse(end));return s;
    }
    @Test void ranksWholeMinutesWithSharedRanksAndZeroDriversWithoutCountingBreakGaps() {
        var alex=driver(1,"Alex",true);var blair=driver(2,"Blair",true);var casey=driver(3,"Casey",true);var zero=driver(4,"No minutes",true);
        when(drivers.findAllByOrganization_IdAndActiveTrueOrderByNameAsc(1L)).thenReturn(List.of(alex,blair,casey,zero));
        var recorded=List.of(
                run(alex,"2026-10-24T14:00:00Z","2026-10-24T14:30:00Z"),
                run(alex,"2026-10-24T15:00:00Z","2026-10-24T15:45:30Z"),
                run(blair,"2026-10-24T14:00:00Z","2026-10-24T15:15:45Z"),
                run(casey,"2026-10-24T14:00:00Z","2026-10-24T14:30:00Z"));
        when(sessions.findForRankings(eq(1L),any(),any())).thenReturn(recorded);
        var report=service.build(1L,DriverRankingService.Period.WEEK,now);
        assertThat(report.rows()).extracting(DriverRankingService.Row::name).containsExactly("Alex","Blair","Casey","No minutes");
        assertThat(report.rows()).extracting(DriverRankingService.Row::rank).containsExactly(1,1,3,0);
        assertThat(report.rows()).extracting(DriverRankingService.Row::minutes).containsExactly(75L,75L,30L,0L);
        assertThat(report.rows().get(0).segments()).isEqualTo(2);
        assertThat(report.rows().get(0).durationLabel()).isEqualTo("1 hr 15 min");
        assertThat(report.durationLabel()).isEqualTo("3 hr");
    }
    @Test void activeRunsUseElapsedTimeAndCapOverdueSessionsWhileReviewedClosedRunsKeepTheirTimes() {
        var d=driver(1,"Alex",true);
        var active=run(d,"2026-10-24T22:30:00Z",null);
        var overdue=run(d,"2026-10-24T12:00:00Z",null);
        var reviewed=run(d,"2026-10-23T12:00:00Z","2026-10-23T17:00:00Z");
        when(sessions.findForRankings(eq(1L),any(),any())).thenReturn(List.of(active,overdue,reviewed));
        var report=service.build(1L,DriverRankingService.Period.WEEK,now);
        assertThat(report.rows()).singleElement().satisfies(r->{assertThat(r.minutes()).isEqualTo(570);assertThat(r.drivingNow()).isTrue();});
        assertThat(DriverRankingService.secondsWithin(reviewed,Instant.parse("2026-10-23T16:00:00Z"),now)).isEqualTo(3600);
        assertThat(DriverRankingService.secondsWithin(overdue,Instant.parse("2026-10-24T18:00:00Z"),now)).isZero();
        assertThat(DriverRankingService.secondsWithin(run(d,"2026-10-25T12:00:00Z",null),now,now)).isZero();
    }
    @Test void localWeekAndMonthBoundariesHandleDaylightSavingAndOvernightTime() {
        when(sessions.findForRankings(any(),any(),any())).thenReturn(List.of());
        var fall=Instant.parse("2026-11-01T08:30:00Z");
        var week=service.build(1L,DriverRankingService.Period.WEEK,fall);
        assertThat(week.from()).isEqualTo(LocalDate.of(2026,10,26));
        verify(sessions).findForRankings(1L,Instant.parse("2026-10-26T05:00:00Z"),fall);
        var month=service.build(1L,DriverRankingService.Period.MONTH,fall);
        assertThat(month.from()).isEqualTo(LocalDate.of(2026,11,1));
        verify(sessions).findForRankings(1L,Instant.parse("2026-11-01T05:00:00Z"),fall);
        var overnight=run(driver(1,"Alex",true),"2026-11-01T04:30:00Z","2026-11-01T08:30:00Z");
        assertThat(DriverRankingService.secondsWithin(overnight,Instant.parse("2026-11-01T05:00:00Z"),fall)).isEqualTo(12600);
    }
    @Test void allTimeIncludesInactiveDriversWithRecordedTimeAndEmptyPeriodsStayUnranked() {
        var former=driver(1,"Former driver",false);var active=driver(2,"Active driver",true);
        when(drivers.findAllByOrganization_IdAndActiveTrueOrderByNameAsc(1L)).thenReturn(List.of(active));
        when(sessions.firstRecordedStart(1L)).thenReturn(Optional.of(Instant.parse("2024-09-01T04:00:00Z")));
        var historical=run(former,"2024-09-01T04:00:00Z","2024-09-01T05:00:00Z");
        when(sessions.findForRankings(eq(1L),any(),any())).thenReturn(List.of(historical));
        var report=service.build(1L,DriverRankingService.Period.ALL,now);
        assertThat(report.from()).isEqualTo(LocalDate.of(2024,8,31));
        assertThat(report.rows().get(0).active()).isFalse();assertThat(report.rows().get(0).rank()).isEqualTo(1);
        when(sessions.firstRecordedStart(1L)).thenReturn(Optional.empty());
        when(sessions.findForRankings(eq(1L),any(),any())).thenReturn(List.of());
        assertThat(service.build(1L,DriverRankingService.Period.ALL,now).rows()).singleElement().satisfies(r->assertThat(r.rank()).isZero());
        assertThatThrownBy(()->DriverRankingService.Period.parse("other")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void coverageRanksAreSeparateFromDrivingRanksWithTiesAndNoPlanningWrites() {
        var alex=driver(1,"Alex",true);var blair=driver(2,"Blair",true);var casey=driver(3,"Casey",true);
        when(drivers.findAllByOrganization_IdAndActiveTrueOrderByNameAsc(1L)).thenReturn(List.of(alex,blair,casey));
        var a=run(alex,"2026-10-24T14:00:00Z","2026-10-24T15:00:00Z");
        var b=run(blair,"2026-10-24T14:00:00Z","2026-10-24T15:30:00Z");
        var c=run(casey,"2026-10-24T14:00:00Z","2026-10-24T17:00:00Z");
        var window=new technology.renatus.routepulse.driving.model.DrivingCoverageWindow(LocalDate.of(2026,10,24),
                "MORNING_OPENING",3L,Instant.parse("2026-10-24T14:00:00Z"),Instant.parse("2026-10-24T15:00:00Z"));
        for(var session:List.of(a,b)) {
            when(session.isCoveragePlanCaptured()).thenReturn(true);when(session.getCoveragePlan()).thenReturn(List.of(window));
        }
        when(sessions.findForRankings(eq(1L),any(),any())).thenReturn(List.of(a,b,c));
        var report=service.build(1L,DriverRankingService.Period.WEEK,now);
        assertThat(report.rows()).extracting(DriverRankingService.Row::name).containsExactly("Casey","Blair","Alex");
        assertThat(report.coverageRows()).extracting(DriverRankingService.CoverageRow::name).containsExactly("Alex","Blair","Casey");
        assertThat(report.coverageRows()).extracting(DriverRankingService.CoverageRow::rank).containsExactly(1,1,0);
        assertThat(report.coverageMinutes()).isEqualTo(120);assertThat(report.coverageDurationLabel()).isEqualTo("2 hr");
        assertThat(report.minutes()).isEqualTo(330);
        verify(sessions).findForRankings(eq(1L),any(),any());verifyNoMoreInteractions(sessions);
    }
}
