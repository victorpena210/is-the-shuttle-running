package technology.renatus.routepulse.reporting.rankings;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.*;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class CoverageDetailIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired CoverageDetailService details;
    @Autowired CoverageReviewRepository reviews;
    @Autowired DriverRankingService rankings;
    @Autowired DrivingSessionRepository sessions;
    private final Instant now=Instant.parse("2026-10-26T22:00:00Z");
    private final Instant start=Instant.parse("2026-10-24T14:00:00Z");
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
    private DrivingSession drove(Fixture f,Instant began,int seconds,DrivingCoverageWindow... windows) {
        var s=DrivingSession.startUnscheduled(f.org(),f.driver(),began,f.owner(),DrivingActionMethod.MANAGER);
        s.captureCoveragePlan(List.of(windows));s.end(began.plusSeconds(seconds),f.owner(),DrivingActionMethod.MANAGER);
        return sessions.saveAndFlush(s);
    }
    private DrivingCoverageWindow window(Fixture f) {
        return new DrivingCoverageWindow(LocalDate.of(2026,10,24),"MORNING_OPENING",f.teammate().getId(),start,start.plusSeconds(10800));
    }
    @Test void monthlyDetailsExplain34MinutesAndOneShiftWhileWeekIsEmpty() {
        var f=fixture("coverage-month");var s=drove(f,start.plusSeconds(60),2040,window(f));
        var report=details.build(f.org().getId(),f.driver().getId(),DriverRankingService.Period.MONTH,true,now);
        assertThat(report.minutes()).isEqualTo(34);assertThat(report.shifts()).isEqualTo(1);
        assertThat(report.windows()).singleElement().satisfies(w->{
            assertThat(w.scheduledDriver()).isEqualTo("Blair coverage-month");
            assertThat(w.date()).isEqualTo(LocalDate.of(2026,10,24));
            assertThat(w.service()).isEqualTo("Morning opening");assertThat(w.full()).isFalse();
            assertThat(w.start().toInstant()).isEqualTo(start);assertThat(w.end().toInstant()).isEqualTo(start.plusSeconds(10800));
            assertThat(w.contributions()).singleElement().satisfies(c->{
                assertThat(c.sessionId()).isEqualTo(s.getId());assertThat(c.coverageStart().toInstant()).isEqualTo(start.plusSeconds(60));
                assertThat(c.coverageEnd().toInstant()).isEqualTo(start.plusSeconds(2100));assertThat(c.durationLabel()).isEqualTo("34 min");
            });
        });
        assertThat(details.build(f.org().getId(),f.driver().getId(),DriverRankingService.Period.WEEK,true,now).windows()).isEmpty();
        assertThat(rankings.build(f.org().getId(),DriverRankingService.Period.MONTH,now).coverageMinutes()).isEqualTo(report.minutes());
    }
    @Test void resumesDuplicateWindowsAndCorrectionsShareTheTotalsCalculation() {
        var f=fixture("coverage-resume");var w=window(f);
        var a=drove(f,start,30,w,w);drove(f,start.plusSeconds(60),30,w);
        var report=details.build(f.org().getId(),f.driver().getId(),DriverRankingService.Period.MONTH,true,now);
        assertThat(report.minutes()).isEqualTo(1);assertThat(report.shifts()).isEqualTo(1);
        assertThat(report.windows()).singleElement().satisfies(r->{
            assertThat(r.seconds()).isEqualTo(60);assertThat(r.contributions()).hasSize(2);
        });
        a.reviewTimes(start,start.plusSeconds(10800),f.owner(),now);sessions.flush();
        var full=details.build(f.org().getId(),f.driver().getId(),DriverRankingService.Period.MONTH,true,now);
        assertThat(full.minutes()).isEqualTo(180);assertThat(full.windows().get(0).full()).isTrue();
        assertThat(rankings.build(f.org().getId(),DriverRankingService.Period.MONTH,now).coverageMinutes()).isEqualTo(full.minutes());
    }
    @Test void overnightBoundaryKeepsOriginalHoursAndShowsOnlySelectedPeriodOverlap() {
        var f=fixture("coverage-overnight");var began=Instant.parse("2026-09-30T23:30:00Z");
        drove(f,began,3600,new DrivingCoverageWindow(LocalDate.of(2026,9,30),"EMPLOYEE_PICKUP",f.teammate().getId(),began,began.plusSeconds(7200)));
        var report=details.build(f.org().getId(),f.driver().getId(),DriverRankingService.Period.MONTH,true,now);
        assertThat(report.minutes()).isEqualTo(30);assertThat(report.windows().get(0).date()).isEqualTo(LocalDate.of(2026,9,30));
        assertThat(report.windows().get(0).contributions().get(0).coverageStart().toInstant()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }
    @Test void pagesRenderWithPeriodLinksAndDriversCanOnlySeeTheirOwnDetails() throws Exception {
        var f=fixture("coverage-page");var foreign=fixture("PRIVATE-ORG");var s=drove(f,start,2040,window(f));
        var owner=authenticationFor(f.owner(),OrganizationRole.OWNER);var driver=authenticationFor(f.user(),OrganizationRole.DRIVER);
        String path="/driving-rankings/coverage/"+f.driver().getId();
        String html=mockMvc.perform(get(path).param("period","all").with(authentication(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("34 min","Blair coverage-page","Morning opening","Credited overlap:","Recorded driving session:",
                "period=all","Flag for review","/driving-records/sessions/"+s.getId()+"/review").doesNotContain("PRIVATE-EMPLOYEE","PRIVATE-ORG");
        String own=mockMvc.perform(get("/driver/rankings/coverage").param("period","all")
                .param("driverId",foreign.driver().getId().toString()).param("organizationId",foreign.org().getId().toString())
                .with(authentication(driver))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(own).contains("Alex coverage-page","Blair coverage-page","34 min")
                .doesNotContain("PRIVATE-ORG","PRIVATE-EMPLOYEE","Flag for review","/driving-records/sessions/");
        mockMvc.perform(get("/driving-rankings/coverage/"+foreign.driver().getId()).with(authentication(owner))).andExpect(status().isNotFound());
        mockMvc.perform(get(path).with(authentication(driver))).andExpect(status().isForbidden());
        mockMvc.perform(get(path).with(authentication(authenticationFor(f.user(),OrganizationRole.OWNER)))).andExpect(status().isForbidden());
        mockMvc.perform(get(path)).andExpect(status().is3xxRedirection());
        mockMvc.perform(get(path).param("period","bad").with(authentication(owner))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/driver/rankings").param("period","month").with(authentication(driver)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/driver/rankings/coverage?period=month")));
    }
    @Test void flagsAreAuditedAndDoNotChangeCreditAndRemainResolvableAfterTimeCorrection() throws Exception {
        var f=fixture("coverage-review");var s=drove(f,start,2040,window(f));
        var auth=authenticationFor(f.owner(),OrganizationRole.OWNER);
        String path="/driving-rankings/coverage/"+f.driver().getId()+"/sessions/"+s.getId()+"/review";
        mockMvc.perform(post(path).with(authentication(auth)).with(csrf()).param("period","month")
                .param("revision","0").param("action","FLAGGED").param("note","Check original assignment <script>alert(1)</script>"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driving-rankings/coverage/"+f.driver().getId()+"?period=month#coverage-reviews"));
        var r=reviews.history(f.org().getId(),f.driver().getId());assertThat(r).hasSize(1);
        assertThat(details.build(f.org().getId(),f.driver().getId(),DriverRankingService.Period.MONTH,true,now).minutes()).isEqualTo(34);
        String page=mockMvc.perform(get("/driving-rankings/coverage/"+f.driver().getId()).param("period","all").with(authentication(auth)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Flagged for review","Resolve review","&lt;script&gt;").doesNotContain("<script>alert(1)</script>");
        assertThatThrownBy(()->details.review(f.org().getId(),f.driver().getId(),f.owner().getEmail(),s.getId(),0,"RESOLVED","Checked"))
                .isInstanceOf(IllegalStateException.class);
        s.reviewTimes(start.plusSeconds(20000),start.plusSeconds(22040),f.owner(),now);sessions.flush();
        var removed=details.build(f.org().getId(),f.driver().getId(),DriverRankingService.Period.WEEK,true,now);
        assertThat(removed.windows()).isEmpty();assertThat(removed.reviews()).singleElement().satisfies(review->assertThat(review.flagged()).isTrue());
        details.review(f.org().getId(),f.driver().getId(),f.owner().getEmail(),s.getId(),r.get(0).getId(),"RESOLVED","Corrected the recorded session times.");
        assertThat(reviews.history(f.org().getId(),f.driver().getId())).extracting(CoverageReview::getAction).containsExactly("RESOLVED","FLAGGED");
    }
    @Test void reviewWritesRequireManagerCsrfSameOrganizationAndValidReason() throws Exception {
        var f=fixture("coverage-secure");var other=fixture("coverage-foreign");var s=drove(f,start,2040,window(f));
        var foreign=drove(other,start,2040,window(other));var auth=authenticationFor(f.owner(),OrganizationRole.OWNER);
        String prefix="/driving-rankings/coverage/"+f.driver().getId()+"/sessions/";
        var request=post(prefix+s.getId()+"/review").param("revision","0").param("action","FLAGGED").param("note","Check");
        mockMvc.perform(request.with(authentication(auth))).andExpect(status().isForbidden());
        mockMvc.perform(post(prefix+s.getId()+"/review").with(csrf()).with(authentication(authenticationFor(f.user(),OrganizationRole.DRIVER)))
                .param("revision","0").param("action","FLAGGED").param("note","Check")).andExpect(status().isForbidden());
        mockMvc.perform(post(prefix+foreign.getId()+"/review").with(csrf()).with(authentication(auth))
                .param("revision","0").param("action","FLAGGED").param("note","Check")).andExpect(status().isNotFound());
        mockMvc.perform(post(prefix+s.getId()+"/review").with(csrf()).with(authentication(auth))
                .param("revision","0").param("action","FLAGGED").param("note"," "))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        assertThat(reviews.count()).isZero();
    }
    private record Fixture(Organization org,UserAccount owner,UserAccount user,Driver driver,Driver teammate) {}
}
