package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.calendar.DriverCalendarAvailability;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.demand.*;

class DailyStaffScheduleServiceTests {
    @Test void combinesDuplicateHoursKeepsSplitShiftsAndIncludesOnlyTheRequestedRosterAcrossMidnight() {
        var drivers=mock(DriverRepository.class);
        var schedules=mock(DriverScheduleWindowRepository.class);
        var calendars=mock(DriverCalendarAvailability.class);
        var service=new DailyStaffScheduleService(drivers,schedules,calendars);
        var org=mock(Organization.class);when(org.getId()).thenReturn(1L);
        var alex=driver(org,1L,"Alex");var blair=driver(org,2L,"Blair");var disabled=driver(org,3L,"Disabled");
        var user=mock(UserAccount.class);when(user.isEnabled()).thenReturn(false);when(disabled.getUser()).thenReturn(user);
        var unlisted=driver(org,4L,"Unlisted");
        when(drivers.findAllByOrganization_IdAndActiveTrueOrderByNameAsc(1L)).thenReturn(List.of(alex,blair,disabled));
        var day=LocalDate.of(2026,10,25);var now=Instant.parse("2026-10-25T15:00:00Z");
        when(schedules.overlapping(1L,day.atStartOfDay(),day.plusDays(2).atStartOfDay())).thenReturn(List.of(
                new DriverScheduleWindow(alex,null,day.atTime(9,0),day.atTime(12,0)),
                new DriverScheduleWindow(alex,null,day.atTime(10,0),day.atTime(13,0)),
                new DriverScheduleWindow(alex,null,day.atTime(13,0),day.atTime(14,0)),
                new DriverScheduleWindow(alex,null,day.atTime(16,0),day.atTime(19,0)),
                new DriverScheduleWindow(blair,null,day.atTime(22,0),day.plusDays(1).atTime(2,0)),
                new DriverScheduleWindow(disabled,null,day.atTime(9,0),day.atTime(19,0)),
                new DriverScheduleWindow(unlisted,null,day.atTime(9,0),day.atTime(19,0)),
                new DriverScheduleWindow(alex,null,day.minusDays(1).atTime(22,0),day.atStartOfDay())));
        var result=service.byDay(1L,day,day.plusDays(1),null,now);
        assertThat(result.get(day)).extracting(DailyStaffScheduleService.WorkShift::name).containsExactly("Alex","Alex","Blair");
        assertThat(result.get(day).get(0).start()).isEqualTo(day.atTime(9,0));
        assertThat(result.get(day).get(0).end()).isEqualTo(day.atTime(14,0));
        assertThat(result.get(day.plusDays(1))).containsExactly(result.get(day).get(2));
        assertThat(service.byDay(1L,day,day.plusDays(1),1L,now).get(day))
                .allSatisfy(s->assertThat(s.driverId()).isEqualTo(1L)).hasSize(2);
        verify(schedules,times(2)).overlapping(1L,day.atStartOfDay(),day.plusDays(2).atStartOfDay());
        verifyNoMoreInteractions(schedules);
    }
    private Driver driver(Organization org,Long id,String name) {
        var driver=mock(Driver.class);when(driver.getId()).thenReturn(id);when(driver.getName()).thenReturn(name);
        when(driver.getOrganization()).thenReturn(org);return driver;
    }
}
