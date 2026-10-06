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
    @Autowired technology.renatus.routepulse.reporting.history.ServiceHistoryService history;
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

    @Test void fridayOpeningCrossesFiveAndDoesNotDuplicateMiddayOrReporting() throws Exception {
        var org = createOrganization("Friday wardrobe", "friday-wardrobe");
        var manager = createUser("friday-hours@example.com", true);
        membershipRepository.save(new OrganizationMembership(org, manager, OrganizationRole.ADMIN));
        driverRepository.save(new Driver(org, "100", "First", true));
        driverRepository.save(new Driver(org, "200", "Second", true));
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(1)
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        String day = date.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
        when(operatingDays.getOperatingDay(any())).thenAnswer(invocation -> new ParkOperatingDay(invocation.getArgument(0),
                ParkOperatingStatus.OPEN, LocalTime.of(17,0), LocalTime.of(23,0), false, "TEST", null));
        csvScheduleProvider.loadSchedule(org.getId(), manager.getEmail(), csvFile("friday.csv",
                "100,First,"+day+" 3:00 PM,"+day+" 11:00 PM,Park/Ops/Wardrobe,Staff",
                "200,Second,"+day+" 3:00 PM,"+day+" 11:00 PM,Park/Ops/Wardrobe,Staff",
                "300,Early Rider,"+day+" 9:00 AM,"+day+" 5:00 PM,Park/Ops/Rides,Operator",
                "400,Late Arrival,"+day+" 5:45 PM,"+day+" 10:30 PM,Park/Ops/Rides,Operator"));
        // Calendar-derived hours work before any wardrobe rule is saved.
        assertEquals(LocalTime.of(15,0), hours.getHours(org.getId(), date).openingTime());
        var automaticOpening = assignmentFor(assignmentService.getOrCreateAssignments(org.getId(),date), ShuttlePeriod.MIDDAY);
        assertEquals(LocalTime.of(15,0), automaticOpening.getScheduledStartTime());
        assertEquals(LocalTime.of(18,0), automaticOpening.getScheduledEndTime());
        assignmentService.updateWardrobeHours(org.getId(), WardrobeHoursRule.Scope.SEASON, "Regular",
                date,date.plusDays(30),LocalTime.of(9,0),LocalTime.of(19,0),null,manager.getEmail());
        mockMvc.perform(post("/wardrobe-hours/season").with(user(manager.getEmail()).roles("ADMIN")).with(csrf())
                .param("label","Fridays").param("from",date.toString()).param("to",date.plusDays(30).toString())
                .param("selectedDate",date.toString()).param("openingTime","15:00").param("closingTime","19:00").param("dayOfWeek","5"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        assertEquals(LocalTime.of(15,0), hours.getHours(org.getId(),date).openingTime());
        assertEquals(LocalTime.of(15,0), hours.getHours(org.getId(),date.plusDays(1)).openingTime()); // mocked calendar is 5 PM on every date
        var assigned = assignmentService.getExistingAssignments(org.getId(),date);
        var opening = assignmentFor(assigned,ShuttlePeriod.MIDDAY);
        assertEquals(LocalTime.of(15,0), opening.getScheduledStartTime());
        assertEquals(LocalTime.of(18,0), opening.getScheduledEndTime());
        assertEquals("Midday", opening.getPeriodLabel());
        assertTrue(opening.getPlannedDrivingWindows().stream().anyMatch(w ->
                w.getStartTime().isBefore(LocalTime.of(17,0)) && w.getEndTime().isAfter(LocalTime.of(17,0))));
        assertTrue(assigned.stream().noneMatch(a -> a.getPeriod() == ShuttlePeriod.MORNING));
        assertEquals(LocalTime.of(22,30), assignmentFor(assigned,ShuttlePeriod.AFTERNOON).getScheduledStartTime());
        var filters = technology.renatus.routepulse.reporting.history.ServiceReviewFilters.resolve(
                date,date,null,null,null,null,null,date);
        var report = history.build(org.getId(),filters);
        var openingRow = report.rows().stream().filter(r -> r.period().equals("midday")).findFirst().orElseThrow();
        assertEquals("Opening coverage",openingRow.title());
        assertEquals(4,openingRow.demand()); // two staff arrivals, 5 PM departure, 5:45 PM arrival
        assertTrue(report.rows().stream().noneMatch(r -> r.period().equals("morning")));
        assertEquals(8,report.rows().stream().mapToInt(r -> r.demand()).sum()); // each movement appears once
        mockMvc.perform(get("/app").with(user(manager.getEmail()).roles("ADMIN")).param("date",date.toString()))
                .andExpect(status().isOk());
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
