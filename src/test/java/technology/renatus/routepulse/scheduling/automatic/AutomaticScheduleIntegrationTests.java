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
import technology.renatus.routepulse.calendar.domain.*;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.calendar.*;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.scheduling.demand.*;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.transportation.TransportationPeriodType;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;
import technology.renatus.routepulse.wardrobe.*;

class AutomaticScheduleIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired AutomaticScheduleStore store;
    @Autowired AutomaticScheduleRepository plans;
    @Autowired DriverScheduleWindowRepository work;
    @Autowired DriverCalendarConnectionRepository connections;
    @Autowired DriverCalendarShiftRepository calendarShifts;
    @Autowired DrivingSessionRepository sessions;
    @Autowired WardrobeHoursService wardrobe;
    @Autowired DemandForecastRepository demand;
    private final LocalDate day=LocalDate.now(ZoneOffset.UTC).plusDays(1);
    private final Instant now=Instant.now();
    private ParkOperatingDay open(LocalDate date,int hour) {
        return new ParkOperatingDay(date,ParkOperatingStatus.OPEN,LocalTime.of(hour,0),LocalTime.of(23,0),false,"LIVE_API",null);
    }
    private Fixture fixture(String slug) {
        var org=createOrganization(slug,slug);var manager=createUser(slug+"@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,manager,OrganizationRole.OWNER));
        var a=driverRepository.saveAndFlush(new Driver(org,"100","Alex "+slug,true));
        var b=driverRepository.saveAndFlush(new Driver(org,"200","Blair "+slug,true));
        var source=scheduleImportRepository.saveAndFlush(new ScheduleImport(org,manager,UUID.randomUUID().toString(),"sample.csv",4,day,day.plusDays(1),day+","+day.plusDays(1),now));
        for(var date:List.of(day,day.plusDays(1))) for(var driver:List.of(a,b))
            work.saveAndFlush(new DriverScheduleWindow(driver,source,date.atTime(8,0),date.atTime(23,0)));
        return new Fixture(org,manager,a,b,source);
    }
    private List<AutomaticScheduleDay.View> views(Fixture f) {return store.manager(f.org().getId(),day,day.plusDays(1),f.manager().getEmail());}

    @Test void openingRulesPersistAndRotateWithoutTouchingDrivingRecords() {
        var f=fixture("opening");
        var days=List.of(open(day,11),open(day.plusDays(1),17));
        store.refresh(f.org().getId(),days,now);entityManager.flush();entityManager.clear();
        var result=views(f);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).slots()).singleElement().satisfies(s->{
            assertThat(s.getStart()).isEqualTo(day.atTime(9,0));assertThat(s.getEnd()).isEqualTo(day.atTime(12,0));});
        assertThat(result.get(1).slots()).singleElement().satisfies(s->{
            assertThat(s.getStart()).isEqualTo(day.plusDays(1).atTime(15,0));assertThat(s.getEnd()).isEqualTo(day.plusDays(1).atTime(19,0));});
        assertThat(result.get(0).slots().get(0).getDriverId()).isNotEqualTo(result.get(1).slots().get(0).getDriverId());
        var before=result.stream().map(AutomaticScheduleDay.View::slots).toList();
        store.refresh(f.org().getId(),days,now);entityManager.flush();entityManager.clear();
        assertThat(views(f).stream().map(AutomaticScheduleDay.View::slots).toList()).isEqualTo(before);
        assertThat(sessions.count()).isZero();assertThat(assignmentRepository.count()).isZero();
    }
    @Test void closedUnknownAndMissingCounterDoNotCreateAssignments() {
        var f=fixture("unavailable");f.b().setActive(false);driverRepository.flush();
        store.refresh(f.org().getId(),List.of(open(day,12)),now);
        assertThat(views(f).get(0).slots()).isNotEmpty().allSatisfy(s->assertThat(s.getDriverId()).isNull());
        for(var state:List.of(ParkOperatingStatus.CLOSED,ParkOperatingStatus.UNKNOWN)) {
            store.refresh(f.org().getId(),List.of(new ParkOperatingDay(day,state,null,null,false,"LIVE_API",null)),now);
            assertThat(views(f).get(0).slots()).isEmpty();
        }
    }
    @Test void ukgDaysOffAndStaleCalendarsOverrideUploadedAvailability() {
        var f=fixture("ukg");
        var fresh=connections.saveAndFlush(new DriverCalendarConnection(f.a(),"test-encrypted","a".repeat(64),now,ZoneOffset.UTC));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        assertThat(views(f).get(0).slots()).allSatisfy(s->assertThat(s.getDriverId()).isNull());
        calendarShifts.saveAndFlush(new DriverCalendarShift(fresh,new UkgCalendarParser.Shift(day.atTime(8,0).toInstant(ZoneOffset.UTC),day.atTime(23,0).toInstant(ZoneOffset.UTC))));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        assertThat(views(f).get(0).slots()).isNotEmpty().allSatisfy(s->assertThat(s.getDriverId()).isNotNull());
        calendarShifts.deleteAll();calendarShifts.flush();connections.deleteAll();connections.flush();
        var stale=connections.saveAndFlush(new DriverCalendarConnection(f.a(),"test-encrypted","b".repeat(64),now.minusSeconds(90000),ZoneOffset.UTC));
        calendarShifts.saveAndFlush(new DriverCalendarShift(stale,new UkgCalendarParser.Shift(day.atTime(8,0).toInstant(ZoneOffset.UTC),day.atTime(23,0).toInstant(ZoneOffset.UTC))));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        assertThat(views(f).get(0).slots()).allSatisfy(s->assertThat(s.getDriverId()).isNull());
    }
    @Test void laterPickupsFollowDemandAfterCounterClosingAndRespectWorkEnd() {
        var f=fixture("pickup-demand");
        for(var time:List.of(LocalTime.of(14,0),LocalTime.of(19,15),LocalTime.of(23,45)))
            demand.saveAndFlush(new DemandForecastWindow(f.org(),f.source(),day,day,
                    time.getHour()<17?TransportationPeriodType.MIDDAY:TransportationPeriodType.EVENING,time,0,5));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        var slots=views(f).get(0).slots();
        assertThat(slots).noneMatch(s->s.getStart().equals(day.atTime(14,0)));
        assertThat(slots).anySatisfy(s->{assertThat(s.getStart()).isEqualTo(day.atTime(19,15));assertThat(s.getDriverId()).isEqualTo(f.b().getId());});
        assertThat(slots).anySatisfy(s->{assertThat(s.getStart()).isEqualTo(day.atTime(23,45));assertThat(s.getDriverId()).isNull();});
        assertThat(sessions.count()).isZero();
    }
    @Test void exactWardrobeOverrideDoesNotProduceOverlaps() {
        var f=fixture("exact-hours");
        wardrobe.save(f.org().getId(),WardrobeHoursRule.Scope.DAY,"Special opening",day,day,LocalTime.of(9,10),LocalTime.of(19,0),null,f.manager().getEmail());
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        var slots=views(f).get(0).slots();assertThat(slots).singleElement().satisfies(s->{
            assertThat(s.getStart()).isEqualTo(day.atTime(9,10));assertThat(s.getEnd()).isEqualTo(day.atTime(12,10));});
    }
    @Test void managersSeeOnlyTheirOrganizationDriversSeeOnlyThemselvesAndWritesNeedCsrf() throws Exception {
        var f=fixture("visible");var other=fixture("PRIVATE-ORG");
        store.refresh(f.org().getId(),List.of(open(day,11)),now);store.refresh(other.org().getId(),List.of(open(day,11)),now);
        var user=createUser("assigned-driver@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(f.org(),user,OrganizationRole.DRIVER));
        f.a().linkUser(user);driverRepository.flush();
        var manager=authenticationFor(f.manager(),OrganizationRole.OWNER);var driver=authenticationFor(user,OrganizationRole.DRIVER);
        mockMvc.perform(get("/driving-schedule").param("date",day.toString()).param("organizationId",other.org().getId().toString()).with(authentication(manager)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Alex visible")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-ORG"))));
        mockMvc.perform(get("/driver/driving-schedule").param("date",day.toString()).param("driverId",f.b().getId().toString()).with(authentication(driver)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Alex visible")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Blair visible"))));
        mockMvc.perform(get("/driving-schedule").with(authentication(driver))).andExpect(status().isForbidden());
        mockMvc.perform(get("/driving-schedule").with(authentication(authenticationFor(user,OrganizationRole.OWNER)))).andExpect(status().isForbidden());
        mockMvc.perform(post("/driving-schedule/refresh").with(authentication(manager))).andExpect(status().isForbidden());
        mockMvc.perform(post("/driving-schedule/refresh").with(authentication(driver)).with(csrf())).andExpect(status().isForbidden());
        assertThat(sessions.count()).isZero();
    }
    private record Fixture(Organization org,UserAccount manager,Driver a,Driver b,ScheduleImport source){}
}
