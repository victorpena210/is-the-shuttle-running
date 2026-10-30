package technology.renatus.routepulse.scheduling.automatic;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import technology.renatus.routepulse.calendar.domain.*;
import technology.renatus.routepulse.calendar.service.*;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.wardrobe.WardrobeHoursService;

class AutomaticScheduleControllerTests {
    private final LocalDate date=LocalDate.of(2026,10,24);
    private final AutomaticScheduleService schedules=mock(AutomaticScheduleService.class);
    private final ParkCalendarOverrideService overrides=mock(ParkCalendarOverrideService.class);
    private final Authentication auth=mock(Authentication.class);
    private final ParkOperatingDay stale=new ParkOperatingDay(date,ParkOperatingStatus.UNKNOWN,
            LocalTime.of(11,0),LocalTime.of(23,0),false,"STALE_SNAPSHOT",null);

    private MockMvc mvc() {
        var current=mock(CurrentOrganizationService.class);
        when(auth.getName()).thenReturn("manager@example.com");
        when(current.requireOrganizationId(auth)).thenReturn(1L);
        when(schedules.today()).thenReturn(date);
        when(schedules.operatingDay(date)).thenReturn(stale);
        return MockMvcBuilders.standaloneSetup(new AutomaticScheduleController(schedules,
                mock(AutomaticScheduleStore.class),current,mock(DriverPortalAccessService.class),
                mock(WardrobeHoursService.class),mock(ParkOperatingDayService.class),overrides,mock(technology.renatus.routepulse.pickup.PickupAlerts.class))).build();
    }
    @Test void confirmedHoursRedirectWithTheIsoDateExpectedByTheAssignmentsPage() throws Exception {
        mvc().perform(post("/driving-schedule/confirm-open").principal(auth).locale(Locale.US)
                .param("date",date.toString()).param("reason","Verified official hours 11 AM–11 PM"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"))
                .andExpect(redirectedUrl("/driving-schedule?date=2026-10-24"));
        verify(overrides).confirmParkOpen(1L,stale,"manager@example.com","Verified official hours 11 AM–11 PM");
        verify(schedules).refresh(1L,"manager@example.com");
    }
    @Test void wardrobeEditsAlsoRedirectWithAnIsoDateInsteadOfLocaleFormatting() throws Exception {
        mvc().perform(post("/driving-schedule/hours").principal(auth).locale(Locale.US)
                .param("date",date.toString()).param("opening","09:00").param("closing","19:00"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"))
                .andExpect(redirectedUrl("/driving-schedule?date=2026-10-24"));
        verify(schedules).refresh(1L,"manager@example.com");
    }
}
