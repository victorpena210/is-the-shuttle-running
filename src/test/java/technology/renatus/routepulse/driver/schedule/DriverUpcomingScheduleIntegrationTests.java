package technology.renatus.routepulse.driver.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.schedule.DriverUpcomingScheduleService.DateRange;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DriverUpcomingScheduleIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DriverUpcomingScheduleService schedule;

    @Test void upcomingAssignmentsArePersonalScopedAndReadOnly() throws Exception {
        var organization = createOrganization("Upcoming", "upcoming-schedule");
        var foreignOrg = createOrganization("Foreign", "foreign-upcoming");
        var account = createUser("upcoming-driver@example.com", true);
        membershipRepository.save(new OrganizationMembership(organization, account, OrganizationRole.DRIVER));
        var own = new Driver(organization, "100", "Own Driver", true); own.linkUser(account);
        driverRepository.saveAndFlush(own);
        var colleague = driverRepository.saveAndFlush(new Driver(organization, "200", "PRIVATE COLLEAGUE", true));
        var foreign = driverRepository.saveAndFlush(new Driver(foreignOrg, "300", "PRIVATE FOREIGN", true));
        LocalDate today = schedule.today();
        save(own, today.plusDays(1), ShuttlePeriod.AFTERNOON, 20);
        save(own, today.plusDays(2), ShuttlePeriod.MIDDAY, 15);
        save(colleague, today.plusDays(1), ShuttlePeriod.MORNING, 9);
        save(foreign, today.plusDays(1), ShuttlePeriod.MORNING, 10);
        var cancelled = save(own, today.plusDays(3), ShuttlePeriod.AFTERNOON, 19);
        cancelled.updateDerivedOutcome(AssignmentOutcome.CANCELLED);
        assignmentRepository.saveAndFlush(cancelled);
        entityManager.clear();
        long before = assignmentRepository.count();
        var view = schedule.upcoming(authenticationFor(account, OrganizationRole.DRIVER), new DateRange(today, today.plusDays(6)));
        assertThat(view.assignments()).hasSize(2);
        assertThat(view.assignments()).extracting(DriverUpcomingScheduleService.AssignmentRow::period).containsExactly("Evening", "Midday");
        mockMvc.perform(get("/driver/schedule").with(user(account.getEmail()).roles("DRIVER"))
                .param("from", today.toString()).param("to", today.plusDays(6).toString())
                .param("driverId", colleague.getId().toString()).param("organizationId", foreignOrg.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driver"));
        assertThat(assignmentRepository.count()).isEqualTo(before);
        var narrowed = schedule.upcoming(authenticationFor(account, OrganizationRole.DRIVER), new DateRange(today.plusDays(2), today.plusDays(2)));
        assertThat(narrowed.assignments()).hasSize(1);
        assertThat(narrowed.assignments().get(0).period()).isEqualTo("Midday");
        mockMvc.perform(get("/driver/schedule")).andExpect(status().is3xxRedirection());
        for (String role : java.util.List.of("ADMIN", "VIEWER")) {
            mockMvc.perform(get("/driver/schedule").with(user(account.getEmail()).roles(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/driver/schedule").with(user(account.getEmail()).roles("DRIVER"))
                .param("from", today.toString()).param("to", today.plusDays(93).toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driver"));
    }

    private DriverAssignment save(Driver driver, LocalDate date, ShuttlePeriod period, int start) {
        var assignment = DriverAssignment.automatic(date, period, driver);
        assignment.updateScheduledTimes(LocalTime.of(start,0), LocalTime.of(start + 1,0));
        return assignmentRepository.saveAndFlush(assignment);
    }
}
