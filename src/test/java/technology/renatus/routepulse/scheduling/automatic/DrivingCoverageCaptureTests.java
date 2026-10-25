package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.Slot;
import technology.renatus.routepulse.scheduling.demand.ShuttleService;

class DrivingCoverageCaptureTests {
    @Test void snapshotsPreviousServiceDayAcrossDstWithoutWritingTheSchedule() {
        var repository=mock(AutomaticScheduleRepository.class);
        var service=new DrivingCoverageCapture(repository,"America/Chicago");
        var org=mock(Organization.class);when(org.getId()).thenReturn(7L);
        var driver=mock(Driver.class);when(driver.getId()).thenReturn(1L);when(driver.getOrganization()).thenReturn(org);
        var date=LocalDate.of(2026,10,31);var start=Instant.parse("2026-11-01T05:30:00Z");
        var day=new AutomaticScheduleDay(7L,date);
        var slot=new Slot(date.atTime(23,0),date.plusDays(1).atTime(3,0),2L,"Assigned","Assigned",ShuttleService.EMPLOYEE_PICKUP);
        day.update("OPEN","LIVE_API",LocalTime.NOON,LocalTime.of(3,0),LocalTime.of(9,0),LocalTime.of(19,0),start,"",List.of(slot));
        when(repository.findAllByOrganizationIdAndDateBetweenOrderByDate(7L,date,date.plusDays(1))).thenReturn(List.of(day));
        var run=DrivingSession.startUnscheduled(org,driver,start,mock(UserAccount.class),DrivingActionMethod.NFC);
        service.capture(run);
        assertThat(run.getCoveragePlan()).singleElement().satisfies(w->{
            assertThat(w.getScheduledDriverId()).isEqualTo(2L);assertThat(w.getServiceDate()).isEqualTo(date);
            assertThat(w.getStart()).isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
            assertThat(w.getEnd()).isEqualTo(Instant.parse("2026-11-01T09:00:00Z"));
        });
        day.update("OPEN","LIVE_API",LocalTime.NOON,LocalTime.of(3,0),LocalTime.of(9,0),LocalTime.of(19,0),start,"",List.of());
        assertThat(run.getCoveragePlan()).hasSize(1);
        verify(repository).findAllByOrganizationIdAndDateBetweenOrderByDate(7L,date,date.plusDays(1));
        verifyNoMoreInteractions(repository);
    }
}
