package technology.renatus.routepulse.driver.insights;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.model.PassengerRideConfirmation;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises actual repository scoping and HTTP authorization against MySQL. */
class DriverInsightsIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DriverInsightsService insights;
    @Autowired DrivingSessionRepository sessions;
    @Autowired PassengerRideConfirmationRepository confirmations;

    @Test
    void personalActivityCannotSelectAnotherDriverOrOrganization() throws Exception {
        var organization = createOrganization("Driver insights", "driver-insights");
        var elsewhere = createOrganization("Elsewhere", "elsewhere-insights");
        var account = createUser("driver-insights@example.com", true);
        membershipRepository.save(new OrganizationMembership(organization, account, OrganizationRole.DRIVER));
        var own = new Driver(organization, "own", "My Driver", true); own.linkUser(account);
        driverRepository.saveAndFlush(own);
        var colleague = driverRepository.saveAndFlush(new Driver(organization, "colleague", "PRIVATE COLLEAGUE", true));
        var foreign = driverRepository.saveAndFlush(new Driver(elsewhere, "foreign", "PRIVATE FOREIGN", true));
        LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        var start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        var overnight = DrivingSession.startUnscheduled(organization, own, start.minusSeconds(900), account, DrivingActionMethod.NFC);
        overnight.end(start.plusSeconds(900), account, DrivingActionMethod.NFC); sessions.saveAndFlush(overnight);
        confirmations.save(new PassengerRideConfirmation(overnight, "own-device", start, start.plusSeconds(10)));
        var assignment = assignmentRepository.saveAndFlush(DriverAssignment.automatic(date, ShuttlePeriod.MORNING, colleague));
        var covered = DrivingSession.start(assignment, own, start.plusSeconds(3600), account, DrivingActionMethod.NFC);
        covered.end(start.plusSeconds(5400), account, DrivingActionMethod.NFC); sessions.saveAndFlush(covered);
        for (Driver other : java.util.List.of(colleague, foreign)) {
            var otherSession = DrivingSession.startUnscheduled(other.getOrganization(), other, start.plusSeconds(6000), account, DrivingActionMethod.NFC);
            otherSession.end(start.plusSeconds(9600), account, DrivingActionMethod.NFC); sessions.saveAndFlush(otherSession);
            confirmations.save(new PassengerRideConfirmation(otherSession, "other-device", start.plusSeconds(6000), start.plusSeconds(6100)));
        }
        entityManager.flush(); entityManager.clear();
        var report = insights.analytics(authenticationFor(account, OrganizationRole.DRIVER), new DriverAnalyticsRange(date, date));
        assertThat(report.seconds()).isEqualTo(2700);
        assertThat(report.unscheduledSeconds()).isEqualTo(900);
        assertThat(report.confirmations()).isEqualTo(1);
        assertThat(report.sessions()).hasSize(2);
        assertThat(report.sessions()).extracting(DriverAnalyticsCalculator.SessionRow::type).containsExactly("Coverage", "Unscheduled");
        mockMvc.perform(get("/driver/analytics").with(user(account.getEmail()).roles("DRIVER"))
                .param("from", date.toString()).param("to", date.toString())
                .param("driverId", colleague.getId().toString()).param("organizationId", elsewhere.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driver"));
        for (String route : java.util.List.of("/driver/analytics", "/driver/demand")) {
            mockMvc.perform(get(route)).andExpect(status().is3xxRedirection());
            mockMvc.perform(get(route).with(user(account.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        }
        for (String route : java.util.List.of("/app", "/service-history", "/driving-records", "/pilot-analytics")) {
            mockMvc.perform(get(route).with(user(account.getEmail()).roles("DRIVER"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/upload").with(user(account.getEmail()).roles("DRIVER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/driver/analytics").with(user(account.getEmail()).roles("DRIVER"))
                .param("from", date.toString()).param("to", date.minusDays(1).toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driver"));
    }

    @Test
    void todayDemandUsesOnlyTheDriversOrganizationAndDoesNotCreateAssignments() throws Exception {
        var organization = createOrganization("Demand insights", "demand-insights");
        var elsewhere = createOrganization("Other demand", "other-demand-insights");
        var account = createUser("demand-insights@example.com", true);
        membershipRepository.save(new OrganizationMembership(organization, account, OrganizationRole.DRIVER));
        var driver = new Driver(organization, "own", "Own Driver", true); driver.linkUser(account);
        driverRepository.saveAndFlush(driver);
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        String csvDate = date.format(DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US));
        csvScheduleProvider.loadSchedule(elsewhere.getId(), account.getEmail(), csvFile("foreign.csv",
                "200,PRIVATE FOREIGN," + csvDate + " 9:00 AM," + csvDate + " 6:00 PM,Park/Ops,Team Member"));
        var auth = authenticationFor(account, OrganizationRole.DRIVER);
        assertThat(insights.todayDemand(auth).scheduleAvailable()).isFalse();
        csvScheduleProvider.loadSchedule(organization.getId(), account.getEmail(), csvFile("own.csv",
                "100,PRIVATE EMPLOYEE," + csvDate + " 9:00 AM," + csvDate + " 6:00 PM,Park/Ops,Team Member"));
        entityManager.flush();
        long before = assignmentRepository.count();
        var demand = insights.todayDemand(auth);
        assertThat(demand.date()).isEqualTo(date);
        assertThat(demand.scheduleAvailable()).isTrue();
        assertThat(demand.analysis().getMorning().getTotalCount()).isEqualTo(1);
        assertThat(demand.analysis().getEvening().getTotalCount()).isEqualTo(1);
        mockMvc.perform(get("/driver/demand").with(user(account.getEmail()).roles("DRIVER"))
                .param("organizationId", elsewhere.getId().toString()).param("date", date.minusDays(1).toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driver"));
        assertThat(assignmentRepository.count()).isEqualTo(before);
    }
}
