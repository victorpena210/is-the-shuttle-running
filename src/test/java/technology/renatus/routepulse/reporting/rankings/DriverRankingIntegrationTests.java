package technology.renatus.routepulse.reporting.rankings;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DriverRankingIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DriverRankingService rankings;
    @Autowired DrivingSessionRepository sessions;
    private Fixture fixture(String name) {
        var org=createOrganization(name,name);var owner=createUser(name+"-owner@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,owner,OrganizationRole.OWNER));
        var user=createUser(name+"-driver@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,user,OrganizationRole.DRIVER));
        var driver=driverRepository.saveAndFlush(new Driver(org,"PRIVATE-EMPLOYEE-100","Alex "+name,true));
        driver.linkUser(user);driverRepository.flush();
        var teammate=driverRepository.saveAndFlush(new Driver(org,"PRIVATE-EMPLOYEE-200","Blair "+name,true));
        return new Fixture(org,owner,user,driver,teammate);
    }
    private DrivingSession drove(Fixture f,Driver driver,int minutes) {
        Instant start=LocalDate.now(ZoneOffset.UTC).minusDays(1).atTime(9,0).toInstant(ZoneOffset.UTC);
        var session=DrivingSession.startUnscheduled(f.org(),driver,start,f.owner(),DrivingActionMethod.MANAGER);
        session.end(start.plusSeconds(minutes*60L),f.owner(),DrivingActionMethod.MANAGER);
        return sessions.saveAndFlush(session);
    }
    @Test void driversAndManagersSeeOnlyTheirTeamTotalsAndCannotOverrideOrganizationOrDriverScope() throws Exception {
        var own=fixture("visible-ranks");var foreign=fixture("PRIVATE-ORG");
        drove(own,own.driver(),60);drove(own,own.teammate(),120);drove(foreign,foreign.driver(),90);
        String driverPage=mockMvc.perform(get("/driver/rankings").param("period","all")
                .param("organizationId",foreign.org().getId().toString()).param("driverId",foreign.driver().getId().toString())
                .with(authentication(authenticationFor(own.user(),OrganizationRole.DRIVER))))
                .andExpect(status().isOk()).andExpect(model().attribute("driverPage","rankings"))
                .andReturn().getResponse().getContentAsString();
        assertThat(driverPage).contains("Alex visible-ranks","Blair visible-ranks","2 hr","1 hr","You","/driver/driving-schedule")
                .doesNotContain("PRIVATE-ORG","PRIVATE-EMPLOYEE",own.user().getEmail(),"/driving-records/sessions/","Refresh assignments");
        assertThat(driverPage.indexOf("Blair visible-ranks")).isLessThan(driverPage.indexOf("Alex visible-ranks"));
        mockMvc.perform(get("/driving-rankings").param("period","all")
                .with(authentication(authenticationFor(own.owner(),OrganizationRole.OWNER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Blair visible-ranks")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-ORG"))));
        assertThat(sessions.count()).isEqualTo(3);assertThat(assignmentRepository.count()).isZero();
    }
    @Test void correctedTimesUpdateRanksAndEmptyPeriodsRenderWithoutInventingDriving() throws Exception {
        var f=fixture("corrected-ranks");var run=drove(f,f.driver(),60);drove(f,f.teammate(),120);
        assertThat(rankings.build(f.org().getId(),DriverRankingService.Period.ALL).rows().get(0).driverId()).isEqualTo(f.teammate().getId());
        run.reviewTimes(run.getStartedAt(),run.getStartedAt().plusSeconds(180*60),f.owner(),Instant.now());sessions.flush();
        var report=rankings.build(f.org().getId(),DriverRankingService.Period.ALL);
        assertThat(report.rows().get(0).driverId()).isEqualTo(f.driver().getId());
        assertThat(report.rows().get(0).minutes()).isEqualTo(180);
        assertThat(sessions.count()).isEqualTo(2);
        sessions.deleteAll();sessions.flush();
        mockMvc.perform(get("/driver/rankings").param("period","all")
                .with(authentication(authenticationFor(f.user(),OrganizationRole.DRIVER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("No driving has been recorded in this period")));
        assertThat(rankings.build(f.org().getId(),DriverRankingService.Period.ALL).rows()).allSatisfy(r->assertThat(r.rank()).isZero());
    }
    @Test void navigationMakesAssignmentsDiscoverableAndRankingsStayBehindAccountAccess() throws Exception {
        var f=fixture("navigation-ranks");var driver=authenticationFor(f.user(),OrganizationRole.DRIVER);
        mockMvc.perform(get("/driver").with(authentication(driver))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("View team driving assignments")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/driver/rankings")));
        mockMvc.perform(get("/driver/driving-schedule").with(authentication(driver)))
                .andExpect(status().isOk()).andExpect(model().attribute("driverPage","assignments"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Team driving assignments")));
        mockMvc.perform(get("/driver/rankings")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/driving-rankings")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/driving-rankings").with(authentication(driver))).andExpect(status().isForbidden());
        mockMvc.perform(get("/driving-rankings").with(authentication(authenticationFor(f.user(),OrganizationRole.OWNER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/driver/rankings").param("period","invalid").with(authentication(driver)))
                .andExpect(status().isBadRequest());
    }
    private record Fixture(Organization org,UserAccount owner,UserAccount user,Driver driver,Driver teammate) {}
}
