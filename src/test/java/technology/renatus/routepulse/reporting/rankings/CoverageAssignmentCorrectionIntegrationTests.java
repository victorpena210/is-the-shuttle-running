package technology.renatus.routepulse.reporting.rankings;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.*;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class CoverageAssignmentCorrectionIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DrivingSessionRepository sessions;
    @Autowired DriverRankingService rankings;
    @Autowired CoverageDetailService details;
    private static final LocalDate DATE=LocalDate.of(2026,10,25);
    private static final Instant WINDOW_START=Instant.parse("2026-10-25T22:00:00Z");
    private static final Instant START=Instant.parse("2026-10-25T21:54:14Z");
    private static final Instant END=Instant.parse("2026-10-25T22:34:10Z");
    private static final Instant NOW=Instant.parse("2026-10-27T14:00:00Z");

    @Test void ownScareActorAssignmentLosesFalseCreditButKeepsDrivingTimeAndOriginalSnapshot() {
        // The page adds a sentence-ending period; it may not be part of the saved name.
        var f=fixture("sfft","Irons, Jody L");
        var run=drove(f,f.victor(),START,END,window(f.jody(),DATE,"SCARE_ACTORS",WINDOW_START));
        assertThat(rankings.build(f.org().getId(),DriverRankingService.Period.MONTH,NOW).coverageMinutes()).isEqualTo(34);
        assertThat(details.build(f.org().getId(),f.victor().getId(),DriverRankingService.Period.ALL,true,NOW).windows()).hasSize(1);

        applyCorrection();

        var stored=sessions.findById(run.getId()).orElseThrow();
        assertThat(stored.getStartedAt()).isEqualTo(START);
        assertThat(stored.getEndedAt()).isEqualTo(END);
        assertThat(stored.getActualMinutes()).isEqualTo(39);
        assertThat(stored.getCoveragePlan()).singleElement().satisfies(w->{
            assertThat(w.getScheduledDriverId()).isEqualTo(f.jody().getId());
            assertThat(w.getEffectiveScheduledDriverId()).isEqualTo(f.victor().getId());
            assertThat(w.getAssignmentCorrectionReason()).contains("Owner-confirmed", "his own assignment");
        });
        for(var period:List.of(DriverRankingService.Period.MONTH,DriverRankingService.Period.ALL)) {
            var report=rankings.build(f.org().getId(),period,NOW);
            assertThat(report.minutes()).isEqualTo(39);
            assertThat(report.coverageMinutes()).isZero();
            assertThat(report.coverageRows()).allSatisfy(r->{assertThat(r.minutes()).isZero();assertThat(r.shifts()).isZero();});
            var detail=details.build(f.org().getId(),f.victor().getId(),period,true,NOW);
            assertThat(detail.windows()).isEmpty();assertThat(detail.minutes()).isZero();assertThat(detail.shifts()).isZero();
        }
        // Time reviews must retain the assignment correction, just as they retain the snapshot.
        stored.reviewTimes(START,END.minusSeconds(60),f.owner(),NOW);sessions.flush();entityManager.clear();
        applyCorrection(); // The data repair is safe to replay.
        assertThat(rankings.build(f.org().getId(),DriverRankingService.Period.ALL,NOW).minutes()).isEqualTo(38);
        assertThat(rankings.build(f.org().getId(),DriverRankingService.Period.ALL,NOW).coverageMinutes()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from driver_workload_events",Long.class)).isZero();
    }

    @Test void repairIncludesResumedRunsButDoesNotChangeOtherOrganizationsDatesServicesOrDrivers() {
        var f=fixture("sfft");var other=fixture("other-park");
        var target=window(f.jody(),DATE,"SCARE_ACTORS",WINDOW_START);
        var first=drove(f,f.victor(),START,WINDOW_START.plusSeconds(600),target);
        var resumed=drove(f,f.victor(),WINDOW_START.plusSeconds(900),END,target);
        // A second window in this same session is still genuine coverage.
        var mixed=drove(f,f.victor(),START,END,target,window(f.jody(),DATE,"EMPLOYEE_PICKUP",WINDOW_START));
        var controls=List.of(
                drove(other,other.victor(),START,END,window(other.jody(),DATE,"SCARE_ACTORS",WINDOW_START)),
                drove(f,f.victor(),START.minusSeconds(86400),END.minusSeconds(86400),
                        window(f.jody(),DATE.minusDays(1),"SCARE_ACTORS",WINDOW_START.minusSeconds(86400))),
                drove(f,f.victor(),START,END,window(f.jody(),DATE,"MORNING_OPENING",WINDOW_START)),
                drove(f,f.victor(),START,END,window(f.jody(),DATE,"SCARE_ACTORS",WINDOW_START.plusSeconds(60))),
                drove(f,f.jody(),START,END,window(f.victor(),DATE,"SCARE_ACTORS",WINDOW_START)),
                drove(f,f.victor(),START,END,window(f.victor(),DATE,"SCARE_ACTORS",WINDOW_START)));

        applyCorrection();

        for(var run:List.of(first,resumed)) {
            var stored=sessions.findById(run.getId()).orElseThrow();
            assertThat(DrivingCoverageCalculator.matches(List.of(stored),Instant.EPOCH,NOW)).isEmpty();
            assertThat(stored.getCoveragePlan().get(0).getEffectiveScheduledDriverId()).isEqualTo(f.victor().getId());
        }
        assertThat(DrivingCoverageCalculator.matches(List.of(sessions.findById(mixed.getId()).orElseThrow()),Instant.EPOCH,NOW))
                .singleElement().satisfies(m->assertThat(m.window().getServiceKey()).isEqualTo("EMPLOYEE_PICKUP"));
        for(var run:controls) assertThat(sessions.findById(run.getId()).orElseThrow().getCoveragePlan())
                .allSatisfy(w->{
                    assertThat(w.getEffectiveScheduledDriverId()).isEqualTo(w.getScheduledDriverId());
                    assertThat(w.getAssignmentCorrectionReason()).isNull();
                });
        assertThat(rankings.build(other.org().getId(),DriverRankingService.Period.ALL,NOW).coverageMinutes()).isEqualTo(34);
        assertThat(jdbcTemplate.queryForObject("select count(*) from driving_coverage_windows where corrected_driver_id is not null",Long.class))
                .isEqualTo(3);
    }

    private void applyCorrection() {
        entityManager.flush();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V50__correct_oct25_scare_actor_responsibility.sql")).execute(jdbcTemplate.getDataSource());
        entityManager.clear();
    }
    private Fixture fixture(String slug) {
        return fixture(slug,"Irons, Jody L.");
    }
    private Fixture fixture(String slug,String jodyName) {
        var org=createOrganization(slug,slug);var owner=createUser(slug+"@example.com",true);
        var victor=driverRepository.saveAndFlush(new Driver(org,"victor","Pena, Victor J",true));
        var jody=driverRepository.saveAndFlush(new Driver(org,"jody",jodyName,true));
        return new Fixture(org,owner,victor,jody);
    }
    private DrivingCoverageWindow window(Driver assigned,LocalDate date,String service,Instant start) {
        return new DrivingCoverageWindow(date,service,assigned.getId(),start,start.plusSeconds(2700));
    }
    private DrivingSession drove(Fixture f,Driver driver,Instant start,Instant end,DrivingCoverageWindow... windows) {
        var run=DrivingSession.startUnscheduled(f.org(),driver,start,f.owner(),DrivingActionMethod.MANAGER);
        run.captureCoveragePlan(List.of(windows));run.end(end,f.owner(),DrivingActionMethod.MANAGER);
        return sessions.saveAndFlush(run);
    }
    private record Fixture(Organization org,UserAccount owner,Driver victor,Driver jody) {}
}
