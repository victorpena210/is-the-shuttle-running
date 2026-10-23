package technology.renatus.routepulse.reporting.workload.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driver.service.DriverService;
import technology.renatus.routepulse.driver.model.Driver;

class ActualDrivingRecordServiceTests {
    @Test void crossingMidnightCountsOnlyTimeInsideRange() {
        var session = mock(DrivingSession.class);
        when(session.getStartedAt()).thenReturn(Instant.parse("2026-10-10T23:00:00Z"));
        when(session.getEndedAt()).thenReturn(Instant.parse("2026-10-11T02:00:00Z"));
        assertThat(ActualDrivingRecordService.secondsWithin(session, Instant.parse("2026-10-11T00:00:00Z"),
                Instant.parse("2026-10-12T00:00:00Z"), Instant.parse("2026-10-12T00:00:00Z"))).isEqualTo(7200);
    }
    @Test void openSessionCountsOnlyElapsedTime() {
        var session = mock(DrivingSession.class);
        when(session.getStartedAt()).thenReturn(Instant.parse("2026-10-11T09:00:00Z"));
        assertThat(ActualDrivingRecordService.secondsWithin(session, Instant.parse("2026-10-11T00:00:00Z"),
                Instant.parse("2026-10-12T00:00:00Z"), Instant.parse("2026-10-11T09:25:00Z"))).isEqualTo(1500);
    }
    @Test void invalidOrForeignDriverCannotExposeRecords() {
        var repo = mock(DrivingSessionRepository.class); var drivers = mock(DriverService.class);
        var service = new ActualDrivingRecordService(repo, drivers, "America/Chicago");
        doThrow(new IllegalArgumentException("Driver not found")).when(drivers).getDriver(1L, 99L);
        assertThatThrownBy(() -> service.build(1L, LocalDate.of(2026,10,1), LocalDate.of(2026,10,12), 99L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
        assertThatThrownBy(() -> service.build(1L, LocalDate.of(2026,10,12), LocalDate.of(2026,10,1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void totalsIncludeHistoricalAssignedRunsAndExcludeBreaks() throws Exception {
        var repo=mock(DrivingSessionRepository.class); var drivers=mock(DriverService.class);
        var org=new technology.renatus.routepulse.organization.model.Organization("Test","Test","test",
                technology.renatus.routepulse.organization.domain.OrganizationStatus.ACTIVE,null,null);
        var driver=new Driver(org,"100","Victor",true);
        var id=Driver.class.getDeclaredField("id");id.setAccessible(true);id.set(driver,10L);
        var user=new technology.renatus.routepulse.auth.model.UserAccount("test@example.com","unused","Test","User",true,true);
        var assignment=technology.renatus.routepulse.assignments.model.DriverAssignment.automatic(LocalDate.of(2026,10,11),
                technology.renatus.routepulse.assignments.domain.ShuttlePeriod.MORNING,driver);
        var first=DrivingSession.start(assignment,driver,Instant.parse("2026-10-11T09:00:00Z"),user,
                technology.renatus.routepulse.driving.domain.DrivingActionMethod.NFC);
        first.end(Instant.parse("2026-10-11T09:30:00Z"),user,technology.renatus.routepulse.driving.domain.DrivingActionMethod.NFC,
                technology.renatus.routepulse.driving.domain.DrivingSessionEndReason.PAUSE);
        var second=DrivingSession.startUnscheduled(org,driver,Instant.parse("2026-10-11T09:45:00Z"),user,
                technology.renatus.routepulse.driving.domain.DrivingActionMethod.QR);
        second.end(Instant.parse("2026-10-11T10:30:00Z"),user,technology.renatus.routepulse.driving.domain.DrivingActionMethod.QR);
        when(repo.findOverlappingForServiceReview(eq(1L),any(),any())).thenReturn(List.of(first,second));
        var report=new ActualDrivingRecordService(repo,drivers,"UTC").build(1L,LocalDate.of(2026,10,11),LocalDate.of(2026,10,11),null);
        assertThat(report.minutes()).isEqualTo(75);assertThat(report.rows()).hasSize(2);
        assertThat(report.drivers().get(0).minutes()).isEqualTo(75);
        assertThat(report.drivers().get(0).sessions()).isEqualTo(2);
    }

    @Test void queryUsesLocalDayBoundariesAcrossDaylightSaving() {
        var repo = mock(DrivingSessionRepository.class); var drivers = mock(DriverService.class);
        when(repo.findOverlappingForServiceReview(any(),any(),any())).thenReturn(List.of());
        new ActualDrivingRecordService(repo, drivers, "America/Chicago")
                .build(1L, LocalDate.of(2026,11,1), LocalDate.of(2026,11,1), null);
        verify(repo).findOverlappingForServiceReview(1L, Instant.parse("2026-11-01T05:00:00Z"), Instant.parse("2026-11-02T06:00:00Z"));
    }
}
