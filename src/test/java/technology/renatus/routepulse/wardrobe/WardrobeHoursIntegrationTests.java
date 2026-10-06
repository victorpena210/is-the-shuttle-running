package technology.renatus.routepulse.wardrobe;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import technology.renatus.routepulse.assignments.domain.*;
import technology.renatus.routepulse.calendar.domain.*;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WardrobeHoursIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired WardrobeHoursService hours;
    @Autowired WardrobeHoursRepository rules;
    @MockitoBean ParkOperatingDayService operatingDays;

    @Test void savedSeasonAndDayOverrideReplanRealAssignments() throws Exception {
        var organization = createOrganization("Seasonal Wardrobe", "seasonal-wardrobe");
        var manager = createUser("hours-manager@example.com", true);
        membershipRepository.save(new OrganizationMembership(organization, manager, OrganizationRole.ADMIN));
        driverRepository.save(new Driver(organization, "100", "First", true));
        driverRepository.save(new Driver(organization, "200", "Second", true));
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        String day = date.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
        when(operatingDays.getOperatingDay(any())).thenAnswer(invocation -> new ParkOperatingDay(invocation.getArgument(0),
                ParkOperatingStatus.OPEN, LocalTime.of(9,0),LocalTime.of(21,0),false,"TEST",null));
        csvScheduleProvider.loadSchedule(organization.getId(), manager.getEmail(), csvFile("summer.csv",
                "100,First,"+day+" 9:00 AM,"+day+" 9:00 PM,Park/Ops/Wardrobe,Staff",
                "200,Second,"+day+" 9:00 AM,"+day+" 9:00 PM,Park/Ops/Wardrobe,Staff",
                "300,Rider,"+day+" 9:00 AM,"+day+" 5:30 PM,Park/Ops/Rides,Operator",
                "400,Late Rider,"+day+" 1:00 PM,"+day+" 8:00 PM,Park/Ops/Rides,Operator"));
        assignmentService.updateWardrobeHours(organization.getId(), WardrobeHoursRule.Scope.SEASON, "Summer",
                date,date.plusDays(30),LocalTime.of(17,0),manager.getEmail());
        var summer = assignmentFor(assignmentService.getExistingAssignments(organization.getId(),date),ShuttlePeriod.AFTERNOON);
        assertEquals(LocalTime.of(17,30),summer.getScheduledStartTime());
        Long id=summer.getId();
        assignmentService.updateWardrobeHours(organization.getId(), WardrobeHoursRule.Scope.SEASON, "Fright Fest",
                date,date.plusDays(30),LocalTime.of(19,0),manager.getEmail());
        var fright = assignmentFor(assignmentService.getExistingAssignments(organization.getId(),date),ShuttlePeriod.AFTERNOON);
        assertEquals(id,fright.getId());
        assertEquals(LocalTime.of(20,0),fright.getScheduledStartTime());
        assertEquals("Fright Fest",hours.getHours(organization.getId(),date).label());
        mockMvc.perform(post("/wardrobe-hours/day").with(user(manager.getEmail()).roles("ADMIN")).with(csrf())
                .param("selectedDate",date.toString()).param("closingTime","18:00"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        assertEquals(LocalTime.of(18,0),hours.getHours(organization.getId(),date).closingTime());
        assertTrue(hours.getHours(organization.getId(),date).dailyOverride());
        assertEquals(LocalTime.of(20,0), assignmentFor(assignmentService.getExistingAssignments(organization.getId(),date),
                ShuttlePeriod.AFTERNOON).getScheduledStartTime());
        mockMvc.perform(post("/wardrobe-hours/day/reset").with(user(manager.getEmail()).roles("ADMIN")).with(csrf())
                .param("selectedDate",date.toString())).andExpect(status().is3xxRedirection());
        assertEquals(LocalTime.of(19,0),hours.getHours(organization.getId(),date).closingTime());
        assertFalse(hours.getHours(organization.getId(),date).dailyOverride());
        assertEquals(4,rules.findAllByOrganizationIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByCreatedAtDescIdDesc(
                organization.getId(),date,date).size());
        assertThrows(IllegalStateException.class, () -> assignmentService.updateScheduledTimes(organization.getId(),id,
                LocalTime.of(18,0),LocalTime.of(20,0)));
    }

    @Test void hoursAreOrganizationScopedAndManagerOnly() throws Exception {
        var a=createOrganization("Hours A","hours-a"); var b=createOrganization("Hours B","hours-b");
        var manager=createUser("manager-a@example.com",true);
        var driver=createUser("driver-a@example.com",true);
        membershipRepository.save(new OrganizationMembership(a,manager,OrganizationRole.ADMIN));
        membershipRepository.save(new OrganizationMembership(a,driver,OrganizationRole.DRIVER));
        LocalDate date=LocalDate.now(ZoneOffset.UTC).plusDays(1);
        hours.save(a.getId(),WardrobeHoursRule.Scope.SEASON,"Summer",date,date.plusDays(30),LocalTime.of(17,0),manager.getEmail());
        assertEquals(LocalTime.of(17,0),hours.getHours(a.getId(),date).closingTime());
        assertEquals(LocalTime.of(19,0),hours.getHours(b.getId(),date).closingTime());
        mockMvc.perform(post("/wardrobe-hours/season").with(user(driver.getEmail()).roles("DRIVER")).with(csrf())
                .param("label","Summer").param("from",date.toString()).param("to",date.toString())
                .param("selectedDate",date.toString()).param("closingTime","17:00")).andExpect(status().isForbidden());
        mockMvc.perform(post("/wardrobe-hours/day").with(user(manager.getEmail()).roles("ADMIN"))
                .param("selectedDate",date.toString()).param("closingTime","18:00")).andExpect(status().isForbidden());
        assertThrows(IllegalStateException.class, () -> hours.save(b.getId(),WardrobeHoursRule.Scope.SEASON,
                "Foreign",date,date,LocalTime.of(17,0),manager.getEmail()));
    }
}
