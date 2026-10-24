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
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class AutomaticScheduleHistoryIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired AutomaticScheduleStore store;
    @Autowired AutomaticScheduleRepository plans;
    @Autowired DrivingSessionRepository sessions;
    private final LocalDate date=LocalDate.now(ZoneOffset.UTC).minusDays(1);
    private Fixture fixture(String name) {
        var org=createOrganization(name,name);var manager=createUser(name+"@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,manager,OrganizationRole.OWNER));
        var driver=driverRepository.saveAndFlush(new Driver(org,"100","Recorded "+name,true));
        return new Fixture(org,manager,driver);
    }
    private DrivingSession drove(Fixture f,int startHour,int endHour) {
        var run=DrivingSession.startUnscheduled(f.org(),f.driver(),date.atTime(startHour,20).toInstant(ZoneOffset.UTC),f.manager(),DrivingActionMethod.MANAGER);
        run.end(date.atTime(endHour,50).toInstant(ZoneOffset.UTC),f.manager(),DrivingActionMethod.MANAGER);
        return sessions.saveAndFlush(run);
    }
    @Test void recordedMorningAppearsBesideSavedEveningWithoutChangingPlansOrCreatingSessions() throws Exception {
        var f=fixture("morning-history");var run=drove(f,9,10);
        var plan=new AutomaticScheduleDay(f.org().getId(),date);
        var evening=new AutomaticScheduleDay.Slot(date.atTime(19,0),date.atTime(21,0),f.driver().getId(),f.driver().getName(),"Automatically assigned");
        plan.update("OPEN","LIVE_API",LocalTime.of(11,0),LocalTime.of(23,0),LocalTime.of(9,0),LocalTime.of(19,0),
                Instant.now(),"Saved plan",List.of(evening));plans.saveAndFlush(plan);
        var page=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0);
        assertThat(page.openingStart()).isEqualTo(date.atTime(9,0));assertThat(page.openingEnd()).isEqualTo(date.atTime(12,0));
        assertThat(page.openingPlanMissing()).isTrue();assertThat(page.slots()).containsExactly(evening);
        assertThat(page.recordedRuns()).singleElement().satisfies(r->{
            assertThat(r.start().toLocalTime()).isEqualTo(LocalTime.of(9,20));assertThat(r.end().toLocalTime()).isEqualTo(LocalTime.of(10,50));
        });
        mockMvc.perform(get("/driving-schedule").param("date",date.toString())
                .with(authentication(authenticationFor(f.manager(),OrganizationRole.OWNER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Opening shuttle:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("9:00 AM–12:00 PM")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Recorded driving")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("9:20 AM")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No opening assignment was saved")));
        run.reviewTimes(run.getStartedAt(),date.atTime(11,5).toInstant(ZoneOffset.UTC),f.manager(),Instant.now());sessions.flush();
        assertThat(store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).recordedRuns().get(0).end().toLocalTime())
                .isEqualTo(LocalTime.of(11,5));
        assertThat(plans.count()).isEqualTo(1);assertThat(sessions.count()).isEqualTo(1);assertThat(assignmentRepository.count()).isZero();
        assertThat(plans.findByOrganizationIdAndDate(f.org().getId(),date).orElseThrow().slots()).containsExactly(evening);
    }
    @Test void daysWithoutPlansStillShowHistoryAndRespectOrganizationAndDriverPrivacy() throws Exception {
        var f=fixture("own-history");var other=fixture("PRIVATE-HISTORY");drove(f,9,10);drove(other,9,10);
        var second=driverRepository.saveAndFlush(new Driver(f.org(),"200","OTHER-DRIVER",true));
        var secondRun=DrivingSession.startUnscheduled(f.org(),second,date.atTime(17,0).toInstant(ZoneOffset.UTC),f.manager(),DrivingActionMethod.MANAGER);
        secondRun.end(date.atTime(17,45).toInstant(ZoneOffset.UTC),f.manager(),DrivingActionMethod.MANAGER);sessions.saveAndFlush(secondRun);
        var managerPage=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0);
        assertThat(managerPage.savedPlan()).isFalse();assertThat(managerPage.slots()).isEmpty();
        assertThat(managerPage.recordedRuns()).hasSize(2).noneMatch(r->r.driverName().contains("PRIVATE"));
        var user=createUser("history-driver@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(f.org(),user,OrganizationRole.DRIVER));
        f.driver().linkUser(user);driverRepository.flush();
        mockMvc.perform(get("/driver/driving-schedule").param("date",date.toString()).param("driverId",second.getId().toString())
                .with(authentication(authenticationFor(user,OrganizationRole.DRIVER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Recorded own-history")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("OTHER-DRIVER"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-HISTORY"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Review driving record"))));
        assertThat(plans.count()).isZero();assertThat(sessions.count()).isEqualTo(3);
    }
    @Test void missingHistoryIsExplicitAndNeverInventsAPastDriverOrFutureRecord() {
        var f=fixture("empty-history");
        var past=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0);
        assertThat(past.savedPlan()).isFalse();assertThat(past.recordedRuns()).isEmpty();assertThat(past.slots()).isEmpty();
        assertThat(store.manager(f.org().getId(),date.plusDays(2),date.plusDays(2),f.manager().getEmail())).isEmpty();
        assertThat(plans.count()).isZero();assertThat(sessions.count()).isZero();
    }
    private record Fixture(Organization org,UserAccount manager,Driver driver) {}
}
