package technology.renatus.routepulse.scheduling.automatic;

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
import technology.renatus.routepulse.driving.domain.*;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.service.DrivingSessionService;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.reporting.rankings.DriverRankingService;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.Slot;
import technology.renatus.routepulse.scheduling.demand.ShuttleService;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DrivingCoverageIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DrivingSessionService lifecycle;
    @Autowired DrivingSessionRepository sessions;
    @Autowired AutomaticScheduleRepository schedules;
    @Autowired DriverRankingService rankings;
    private static final LocalDate DATE=LocalDate.of(2026,1,10);
    private static final Instant START=DATE.atTime(9,0).toInstant(ZoneOffset.UTC);
    private Fixture fixture(String name) {
        var org=createOrganization(name,name);var owner=createUser(name+"@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,owner,OrganizationRole.OWNER));
        var assigned=driverRepository.saveAndFlush(new Driver(org,name+"-1","Assigned "+name,true));
        var helper=driverRepository.saveAndFlush(new Driver(org,name+"-2","Helper "+name,true));
        var day=new AutomaticScheduleDay(org.getId(),DATE);
        day.update("OPEN","LIVE_API",LocalTime.NOON,LocalTime.of(22,0),LocalTime.of(9,0),LocalTime.of(19,0),START.minusSeconds(3600),"",
                List.of(new Slot(DATE.atTime(9,0),DATE.atTime(12,0),assigned.getId(),assigned.getName(),"Assigned",ShuttleService.MORNING_OPENING)));
        schedules.saveAndFlush(day);return new Fixture(org,owner,assigned,helper,day);
    }
    @Test void nfcStartPauseResumePersistsCoverageAndDoesNotChangePlanningOrWorkloadEvents() throws Exception {
        var f=fixture("coverage");var foreign=fixture("FOREIGN");
        var first=lifecycle.startUnscheduledSession(f.helper(),f.owner(),START,DrivingActionMethod.NFC);
        lifecycle.endSession(null,first,f.owner(),START.plusSeconds(1800),DrivingActionMethod.NFC,DrivingSessionEndReason.PAUSE);
        var second=lifecycle.startUnscheduledSession(f.helper(),f.owner(),START.plusSeconds(2700),DrivingActionMethod.QR);
        lifecycle.endSession(null,second,f.owner(),START.plusSeconds(3600),DrivingActionMethod.QR);
        sessions.flush();entityManager.clear();
        var stored=sessions.findById(first.getId()).orElseThrow();
        assertThat(stored.isCoveragePlanCaptured()).isTrue();
        assertThat(stored.getCoveragePlan()).singleElement().satisfies(w->assertThat(w.getScheduledDriverId()).isEqualTo(f.assigned().getId()));
        var report=rankings.build(f.org().getId(),DriverRankingService.Period.ALL);
        assertThat(report.coverageRows().get(0).driverId()).isEqualTo(f.helper().getId());
        assertThat(report.coverageRows().get(0).minutes()).isEqualTo(45);
        assertThat(report.coverageRows().get(0).shifts()).isEqualTo(1);
        assertThat(report.minutes()).isEqualTo(45);
        assertThat(rankings.build(foreign.org().getId(),DriverRankingService.Period.ALL).coverageMinutes()).isZero();
        assertThat(schedules.findByOrganizationIdAndDate(f.org().getId(),DATE).orElseThrow().slots().get(0).getDriverId()).isEqualTo(f.assigned().getId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from driver_workload_events",Long.class)).isZero();
        mockMvc.perform(get("/driving-rankings").param("period","all")
                .with(authentication(authenticationFor(f.owner(),OrganizationRole.OWNER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Covered for others")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("45 min")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("FOREIGN"))));
    }
    @Test void handoffCapturesTheAssignedDriverAndScheduleEditsCannotRewriteCoverage() {
        var f=fixture("handoff");
        var original=lifecycle.startUnscheduledSession(f.assigned(),f.owner(),START,DrivingActionMethod.NFC);
        var covering=lifecycle.takeOverActiveSession(original,f.helper(),f.owner(),START.plusSeconds(1800),DrivingActionMethod.NFC);
        lifecycle.endSession(null,covering,f.owner(),START.plusSeconds(3600),DrivingActionMethod.NFC);
        f.day().update("OPEN","LIVE_API",LocalTime.NOON,LocalTime.of(22,0),LocalTime.of(9,0),LocalTime.of(19,0),START.plusSeconds(7200),"Changed plan",List.of());
        schedules.flush();sessions.flush();entityManager.clear();
        var report=rankings.build(f.org().getId(),DriverRankingService.Period.ALL);
        assertThat(report.coverageRows().get(0).driverId()).isEqualTo(f.helper().getId());
        assertThat(report.coverageMinutes()).isEqualTo(30);
        var corrected=sessions.findById(covering.getId()).orElseThrow();
        corrected.reviewTimes(START.plusSeconds(1800),START.plusSeconds(2700),f.owner(),Instant.now());sessions.flush();
        assertThat(rankings.build(f.org().getId(),DriverRankingService.Period.ALL).coverageMinutes()).isEqualTo(15);
    }
    private record Fixture(Organization org,UserAccount owner,Driver assigned,Driver helper,AutomaticScheduleDay day) {}
}
