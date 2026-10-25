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
import technology.renatus.routepulse.calendar.repository.ParkOperatingDaySnapshotRepository;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.calendar.service.ParkCalendarOverrideService;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.calendar.*;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
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
    @Autowired ParkOperatingDaySnapshotRepository snapshots;
    @Autowired DriverScheduleIdentityService access;
    @Autowired ParkCalendarOverrideService overrides;
    // Fixed, post-event dates keep ordinary scheduling checks independent of the day CI runs.
    private final LocalDate day=LocalDate.of(2026,11,2);
    private final Instant now=day.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    private ParkOperatingDay open(LocalDate date,int hour) {
        return new ParkOperatingDay(date,ParkOperatingStatus.OPEN,LocalTime.of(hour,0),LocalTime.of(23,0),false,"LIVE_API",null);
    }
    private Fixture fixture(String slug) {
        return fixture(slug,day,now);
    }
    private Fixture fixture(String slug,LocalDate first,Instant refreshedAt) {
        var org=createOrganization(slug,slug);var manager=createUser(slug+"@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,manager,OrganizationRole.OWNER));
        var a=driverRepository.saveAndFlush(new Driver(org,"100","Alex "+slug,true));
        var b=driverRepository.saveAndFlush(new Driver(org,"200","Blair "+slug,true));
        var source=scheduleImportRepository.saveAndFlush(new ScheduleImport(org,manager,UUID.randomUUID().toString(),"sample.csv",4,first,first.plusDays(1),first+","+first.plusDays(1),refreshedAt));
        for(var date:List.of(first,first.plusDays(1))) for(var driver:List.of(a,b))
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
        assertThat(result.get(0).slots()).hasSize(2);
        assertThat(result.get(0).slots().get(0)).satisfies(s->{
            assertThat(s.getStart()).isEqualTo(day.atTime(9,0));assertThat(s.getEnd()).isEqualTo(day.atTime(12,0));});
        assertThat(result.get(1).slots()).hasSize(2);
        assertThat(result.get(1).slots().get(0)).satisfies(s->{
            assertThat(s.getStart()).isEqualTo(day.plusDays(1).atTime(15,0));assertThat(s.getEnd()).isEqualTo(day.plusDays(1).atTime(17,0));
            assertThat(s.getService()).isEqualTo(ShuttleService.MIDDAY_OPENING);});
        assertThat(result.get(0).slots().get(0).getDriverId()).isNotEqualTo(result.get(0).slots().get(1).getDriverId());
        var before=result.stream().map(AutomaticScheduleDay.View::slots).toList();
        store.refresh(f.org().getId(),days,now);entityManager.flush();entityManager.clear();
        assertThat(views(f).stream().map(AutomaticScheduleDay.View::slots).toList()).isEqualTo(before);
        assertThat(sessions.count()).isZero();assertThat(assignmentRepository.count()).isZero();
    }
    @Test void wholeDayBalancesSoleActorDriverAndKeepsHistoricalWorkloadAfterRefresh() {
        var date=LocalDate.of(2026,10,31);var before=date.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var f=fixture("whole-day",date,before);
        work.deleteAll();work.flush();
        work.saveAndFlush(new DriverScheduleWindow(f.a(),f.source(),date.atTime(9,0),date.atTime(19,0)));
        work.saveAndFlush(new DriverScheduleWindow(f.b(),f.source(),date.atTime(9,0),date.atTime(15,0)));
        var park=open(date,11);
        store.refresh(f.org().getId(),List.of(park),before);entityManager.flush();entityManager.clear();
        var first=plans.findByOrganizationIdAndDate(f.org().getId(),date).orElseThrow().slots();
        assertThat(first).extracting(AutomaticScheduleDay.Slot::getDriverId).containsExactly(f.b().getId(),f.a().getId(),null);
        assertThat(first.get(1).getReason()).contains("Temporarily close wardrobe");
        store.refresh(f.org().getId(),List.of(park),before);entityManager.flush();entityManager.clear();
        assertThat(plans.findByOrganizationIdAndDate(f.org().getId(),date).orElseThrow().slots()).isEqualTo(first);
        assertThat(sessions.count()).isZero();
        var history=DrivingSession.startUnscheduled(f.org(),f.b(),before.minus(Duration.ofDays(1)),f.manager(),DrivingActionMethod.MANAGER);
        history.end(before.minus(Duration.ofDays(1)).plusSeconds(4*3600),f.manager(),DrivingActionMethod.MANAGER);
        sessions.saveAndFlush(history);
        store.refresh(f.org().getId(),List.of(park),before);entityManager.flush();entityManager.clear();
        assertThat(plans.findByOrganizationIdAndDate(f.org().getId(),date).orElseThrow().slots())
                .extracting(AutomaticScheduleDay.Slot::getDriverId).containsExactly(f.a().getId(),f.a().getId(),null);
        assertThat(sessions.count()).isEqualTo(1);assertThat(assignmentRepository.count()).isZero();
    }
    @Test void refreshRestoresTheSeparateEveningAssignmentWhileTheMorningRunIsActive() {
        var date=LocalDate.of(2026,10,25);var before=date.atTime(8,0).toInstant(ZoneOffset.UTC);
        var f=fixture("separate-evening",date,before);
        work.deleteAll();work.flush();
        work.saveAndFlush(new DriverScheduleWindow(f.a(),f.source(),date.atTime(9,0),date.atTime(23,0)));
        work.saveAndFlush(new DriverScheduleWindow(f.b(),f.source(),date.atTime(9,0),date.atTime(19,0)));
        var park=new ParkOperatingDay(date,ParkOperatingStatus.OPEN,LocalTime.NOON,LocalTime.of(22,0),false,"LIVE_API",null);
        store.refresh(f.org().getId(),List.of(park),before);
        var initial=plans.findByOrganizationIdAndDate(f.org().getId(),date).orElseThrow().slots();
        assertThat(initial).filteredOn(s->s.getStart().equals(date.atTime(19,0))).singleElement()
                .satisfies(s->assertThat(s.getDriverId()).isEqualTo(f.a().getId()));
        var run=sessions.saveAndFlush(DrivingSession.startUnscheduled(f.org(),f.a(),
                date.atTime(10,24).toInstant(ZoneOffset.UTC),f.manager(),DrivingActionMethod.MANAGER));
        // Reproduce the saved coverage gap left by the old all-day cutoff check.
        var saved=plans.findByOrganizationIdAndDate(f.org().getId(),date).orElseThrow();
        saved.update("OPEN","LIVE_API",LocalTime.NOON,LocalTime.of(22,0),LocalTime.of(9,0),LocalTime.of(19,0),
                before,"Saved plan",initial.stream().map(s->s.getStart().equals(date.atTime(19,0))
                        ?new AutomaticScheduleDay.Slot(s.getStart(),s.getEnd(),null,null,"No available driver covers this full window",s.getService()):s).toList());
        plans.flush();
        for(var time:List.of(LocalTime.of(11,42),LocalTime.of(11,47))) {
            store.refresh(f.org().getId(),List.of(park),date.atTime(time).toInstant(ZoneOffset.UTC));
            var refreshed=plans.findByOrganizationIdAndDate(f.org().getId(),date).orElseThrow().slots();
            assertThat(refreshed).filteredOn(s->s.getStart().equals(date.atTime(19,0))).singleElement().satisfies(s->{
                assertThat(s.getEnd()).isEqualTo(date.atTime(23,0));assertThat(s.getDriverId()).isEqualTo(f.a().getId());
            });
            assertThat(refreshed).filteredOn(AutomaticScheduleDay.Slot::isScareActorTransport).singleElement()
                    .satisfies(s->assertThat(s.getDriverId()).isEqualTo(f.b().getId()));
        }
        assertThat(run.isActive()).isTrue();assertThat(run.getEndedAt()).isNull();
        assertThat(run.getAutomaticEndAt()).isEqualTo(date.atTime(14,24).toInstant(ZoneOffset.UTC));
        assertThat(sessions.count()).isEqualTo(1);assertThat(assignmentRepository.count()).isZero();
    }
    @Test void eveningAssignmentRunsUntilElevenWhenClosingStaffWorkPastTenOClockParkClose() {
        var date=LocalDate.of(2026,10,29);var before=date.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var f=fixture("later-work-finish",date,before);
        work.deleteAll();work.flush();
        for(var driver:List.of(f.a(),f.b()))
            work.saveAndFlush(new DriverScheduleWindow(driver,f.source(),date.atTime(15,0),date.atTime(23,0)));
        var park=new ParkOperatingDay(date,ParkOperatingStatus.OPEN,LocalTime.of(17,0),LocalTime.of(22,0),false,"LIVE_API",null);
        for(int refresh=0;refresh<2;refresh++) {
            store.refresh(f.org().getId(),List.of(park),before.plusSeconds(refresh*300));
            var view=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0);
            assertThat(view.parkClosing()).isEqualTo(LocalTime.of(22,0));
            assertThat(view.slots()).hasSize(3);
            assertThat(view.slots().subList(0,2)).allSatisfy(s->assertThat(s.getDriverId()).isEqualTo(f.a().getId()));
            assertThat(view.slots().get(2)).satisfies(s->{
                assertThat(s.getStart()).isEqualTo(date.atTime(19,0));assertThat(s.getEnd()).isEqualTo(date.atTime(23,0));
                assertThat(s.getDriverId()).isEqualTo(f.b().getId());assertThat(s.getService()).isEqualTo(ShuttleService.EMPLOYEE_PICKUP);
            });
        }
        assertThat(sessions.count()).isZero();assertThat(assignmentRepository.count()).isZero();
    }
    @Test void laterCalendarWorkFinishIncludesOvernightReliefWithoutExtendingIntoItsNextDayShift() {
        var f=fixture("later-overnight");var finish=day.plusDays(1).atTime(0,40);
        for(var driver:List.of(f.a(),f.b())) {
            var connection=connections.saveAndFlush(new DriverCalendarConnection(driver,"test-encrypted",driver==f.a()?"e".repeat(64):"f".repeat(64),now,ZoneOffset.UTC));
            calendarShifts.saveAndFlush(new DriverCalendarShift(connection,new UkgCalendarParser.Shift(
                    day.atTime(9,0).toInstant(ZoneOffset.UTC),finish.toInstant(ZoneOffset.UTC))));
        }
        var relief=driverRepository.saveAndFlush(new Driver(f.org(),"300","Overnight Relief",true));
        var connection=connections.saveAndFlush(new DriverCalendarConnection(relief,"test-encrypted","g".repeat(64),now,ZoneOffset.UTC));
        calendarShifts.saveAndFlush(new DriverCalendarShift(connection,new UkgCalendarParser.Shift(
                day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),day.plusDays(1).atTime(3,0).toInstant(ZoneOffset.UTC))));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        var view=views(f).get(0);
        assertThat(view.parkClosing()).isEqualTo(LocalTime.of(23,0));
        assertThat(view.slots()).allSatisfy(s->{assertThat(s.getDriverId()).isNotNull();assertThat(s.getEnd()).isBeforeOrEqualTo(finish);});
        assertThat(view.slots()).anySatisfy(s->{
            assertThat(s.getStart()).isEqualTo(day.plusDays(1).atStartOfDay());assertThat(s.getEnd()).isEqualTo(finish);
            assertThat(s.getDriverId()).isEqualTo(relief.getId());
        });
        assertThat(demand.count()).isZero();assertThat(sessions.count()).isZero();
    }
    @Test void shorterCalendarHoursLeaveCoverageUntilParkCloseAndOverrideLongerUploads() {
        var f=fixture("shorter-closing");fixture("other-closing-org");
        var inactive=driverRepository.saveAndFlush(new Driver(f.org(),"300","Inactive Late Worker",false));
        work.saveAndFlush(new DriverScheduleWindow(inactive,f.source(),day.atTime(9,0),day.plusDays(1).atTime(2,0)));
        for(var driver:List.of(f.a(),f.b())) {
            var connection=connections.saveAndFlush(new DriverCalendarConnection(driver,"test-encrypted",driver==f.a()?"e".repeat(64):"f".repeat(64),now,ZoneOffset.UTC));
            calendarShifts.saveAndFlush(new DriverCalendarShift(connection,new UkgCalendarParser.Shift(
                    day.atTime(9,0).toInstant(ZoneOffset.UTC),day.atTime(21,30).toInstant(ZoneOffset.UTC))));
        }
        var park=new ParkOperatingDay(day,ParkOperatingStatus.OPEN,LocalTime.of(11,0),LocalTime.of(22,0),false,"LIVE_API",null);
        store.refresh(f.org().getId(),List.of(park),now);
        var slots=views(f).get(0).slots();
        assertThat(slots).anySatisfy(s->{
            assertThat(s.getStart()).isEqualTo(day.atTime(19,0));assertThat(s.getEnd()).isEqualTo(day.atTime(21,30));
            assertThat(s.getDriverId()).isEqualTo(f.b().getId());
        });
        assertThat(slots).anySatisfy(s->{
            assertThat(s.getStart()).isEqualTo(day.atTime(21,30));assertThat(s.getEnd()).isEqualTo(day.atTime(22,0));
            assertThat(s.getDriverId()).isNull();
        });
        assertThat(slots).noneMatch(s->s.getEnd().isAfter(day.atTime(22,0)));
        assertThat(sessions.count()).isZero();
    }
    @Test void closedUnknownAndMissingCounterDoNotCreateAssignments() {
        var f=fixture("unavailable");f.b().setActive(false);driverRepository.flush();
        store.refresh(f.org().getId(),List.of(open(day,12)),now);
        assertThat(views(f).get(0).slots()).filteredOn(s->s.getStart().isBefore(day.atTime(19,0)))
                .isNotEmpty().allSatisfy(s->assertThat(s.getDriverId()).isNull());
        assertThat(views(f).get(0).slots()).anySatisfy(s->{
            assertThat(s.getStart()).isEqualTo(day.atTime(19,0));assertThat(s.getDriverId()).isEqualTo(f.a().getId());});
        for(var state:List.of(ParkOperatingStatus.CLOSED,ParkOperatingStatus.UNKNOWN)) {
            store.refresh(f.org().getId(),List.of(new ParkOperatingDay(day,state,null,null,false,"LIVE_API",null)),now);
            assertThat(views(f).get(0).slots()).isEmpty();
        }
    }
    @Test void ukgDaysOffAndStaleCalendarsOverrideUploadedAvailability() {
        var f=fixture("ukg");
        var fresh=connections.saveAndFlush(new DriverCalendarConnection(f.a(),"test-encrypted","a".repeat(64),now,ZoneOffset.UTC));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        assertThat(views(f).get(0).slots()).filteredOn(s->s.getStart().isBefore(day.atTime(19,0)))
                .allSatisfy(s->assertThat(s.getDriverId()).isNull());
        assertThat(views(f).get(0).slots()).noneMatch(s->f.a().getId().equals(s.getDriverId()));
        calendarShifts.saveAndFlush(new DriverCalendarShift(fresh,new UkgCalendarParser.Shift(day.atTime(8,0).toInstant(ZoneOffset.UTC),day.atTime(23,0).toInstant(ZoneOffset.UTC))));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        assertThat(views(f).get(0).slots()).isNotEmpty().allSatisfy(s->assertThat(s.getDriverId()).isNotNull());
        calendarShifts.deleteAll();calendarShifts.flush();connections.deleteAll();connections.flush();
        var stale=connections.saveAndFlush(new DriverCalendarConnection(f.a(),"test-encrypted","b".repeat(64),now.minusSeconds(90000),ZoneOffset.UTC));
        calendarShifts.saveAndFlush(new DriverCalendarShift(stale,new UkgCalendarParser.Shift(day.atTime(8,0).toInstant(ZoneOffset.UTC),day.atTime(23,0).toInstant(ZoneOffset.UTC))));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        assertThat(views(f).get(0).slots()).filteredOn(s->s.getStart().isBefore(day.atTime(19,0)))
                .allSatisfy(s->assertThat(s.getDriverId()).isNull());
        assertThat(views(f).get(0).slots()).noneMatch(s->f.a().getId().equals(s.getDriverId()));
    }
    @Test void laterPickupsFollowDemandAfterCounterClosingAndRespectWorkEnd() {
        var f=fixture("pickup-demand");
        for(var time:List.of(LocalTime.of(14,0),LocalTime.of(19,15),LocalTime.of(23,45)))
            demand.saveAndFlush(new DemandForecastWindow(f.org(),f.source(),day,day,
                    time.getHour()<17?TransportationPeriodType.MIDDAY:TransportationPeriodType.EVENING,time,0,5));
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        var slots=views(f).get(0).slots();
        assertThat(slots).noneMatch(s->s.getStart().equals(day.atTime(14,0)));
        assertThat(slots).anySatisfy(s->{assertThat(s.overlaps(day.atTime(19,15),day.atTime(19,30))).isTrue();assertThat(s.getDriverId()).isEqualTo(f.b().getId());});
        assertThat(slots).anySatisfy(s->{assertThat(s.getStart()).isEqualTo(day.atTime(23,45));assertThat(s.getDriverId()).isNull();});
        assertThat(sessions.count()).isZero();
    }
    @Test void exactWardrobeOverrideDoesNotProduceOverlaps() {
        var f=fixture("exact-hours");
        wardrobe.save(f.org().getId(),WardrobeHoursRule.Scope.DAY,"Special opening",day,day,LocalTime.of(9,10),LocalTime.of(19,0),null,f.manager().getEmail());
        store.refresh(f.org().getId(),List.of(open(day,11)),now);
        var slots=views(f).get(0).slots();assertThat(slots).filteredOn(s->s.getService()==ShuttleService.MORNING_OPENING).singleElement().satisfies(s->{
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
                .andExpect(status().isOk()).andExpect(model().attribute("driverPage","assignments"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/driver/rankings")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alex visible")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Blair visible"))));
        mockMvc.perform(get("/driving-schedule").with(authentication(driver))).andExpect(status().isForbidden());
        mockMvc.perform(get("/driving-schedule").with(authentication(authenticationFor(user,OrganizationRole.OWNER)))).andExpect(status().isForbidden());
        mockMvc.perform(post("/driving-schedule/refresh").with(authentication(manager))).andExpect(status().isForbidden());
        mockMvc.perform(post("/driving-schedule/refresh").with(authentication(driver)).with(csrf())).andExpect(status().isForbidden());
        assertThat(sessions.count()).isZero();
    }
    @Test void lateFrightFestOpeningUsesMiddayAndOneDriverWithoutOverlappingTheActorRun() throws Exception {
        var date=LocalDate.of(2026,10,31);var before=date.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var f=fixture("fright-late",date,before);
        store.refresh(f.org().getId(),List.of(open(date,17)),before);entityManager.flush();entityManager.clear();
        var slots=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).slots();
        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).getStart()).isEqualTo(date.atTime(15,0));
        assertThat(slots.get(0).getEnd()).isEqualTo(date.atTime(17,0));
        assertThat(slots.get(0).getService()).isEqualTo(ShuttleService.MIDDAY_OPENING);
        assertThat(slots.get(1).isScareActorTransport()).isTrue();
        assertThat(slots.get(1).getStart()).isEqualTo(date.atTime(17,0));
        assertThat(slots.get(1).getEnd()).isEqualTo(date.atTime(17,45));
        assertThat(slots.subList(0,2)).allSatisfy(s->assertThat(s.getDriverId()).isEqualTo(slots.get(0).getDriverId()));
        assertThat(slots).noneMatch(s->s.overlaps(date.atTime(17,45),date.atTime(19,0)));
        assertThat(slots.get(2).getStart()).isEqualTo(date.atTime(19,0));
        assertThat(slots.get(2).getEnd()).isEqualTo(date.atTime(23,0));
        assertThat(slots.get(2).getDriverId()).isNotEqualTo(slots.get(0).getDriverId());
        store.refresh(f.org().getId(),List.of(open(date,17)),before);
        assertThat(store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).slots()).isEqualTo(slots);
        mockMvc.perform(get("/driving-schedule").param("date",date.toString())
                .with(authentication(authenticationFor(f.manager(),OrganizationRole.OWNER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Midday")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("5:45 PM does not automatically end your driving session")));
        assertThat(sessions.count()).isZero();
    }
    @Test void elevenAndNoonOpeningsUseNineToNoonAndRequireASeparateCounterWorker() {
        var f=fixture("daytime-opening");
        for(int parkHour:List.of(11,12)) {
            store.refresh(f.org().getId(),List.of(open(day,parkHour)),now);
            assertThat(views(f).get(0).slots()).filteredOn(s->s.getService()==ShuttleService.MORNING_OPENING)
                    .singleElement().satisfies(s->{
                        assertThat(s.getStart()).isEqualTo(day.atTime(9,0));
                        assertThat(s.getEnd()).isEqualTo(day.atTime(12,0));
                        assertThat(s.getDriverId()).isNotNull();
                    });
        }
        f.b().setActive(false);driverRepository.flush();
        store.refresh(f.org().getId(),List.of(open(day,12)),now);
        assertThat(views(f).get(0).slots()).filteredOn(s->s.getService()==ShuttleService.MORNING_OPENING)
                .singleElement().satisfies(s->{
                    assertThat(s.getDriverId()).isNull();assertThat(s.getReason()).contains("wardrobe counter");
                });
    }
    @Test void lateOpeningSelectsOneDriverThroughScareActorsWhenTheCounterChangesAtFive() {
        var date=LocalDate.of(2026,10,30);var before=date.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var f=fixture("friday-full-run",date,before);
        var c=driverRepository.saveAndFlush(new Driver(f.org(),"300","Casey",true));
        work.deleteAll();work.flush();
        work.saveAndFlush(new DriverScheduleWindow(f.a(),f.source(),date.atTime(15,0),date.atTime(17,0)));
        work.saveAndFlush(new DriverScheduleWindow(f.b(),f.source(),date.atTime(15,0),date.atTime(23,0)));
        work.saveAndFlush(new DriverScheduleWindow(c,f.source(),date.atTime(17,0),date.atTime(23,0)));
        store.refresh(f.org().getId(),List.of(open(date,17)),before);
        var slots=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).slots();
        assertThat(slots).hasSize(3);
        assertThat(slots.subList(0,2)).allSatisfy(s->assertThat(s.getDriverId()).isEqualTo(f.b().getId()));
        assertThat(slots.get(0).getStart()).isEqualTo(date.atTime(15,0));
        assertThat(slots.get(1).getEnd()).isEqualTo(date.atTime(17,45));
        assertThat(slots).noneMatch(s->s.overlaps(date.atTime(17,45),date.atTime(19,0)));
        c.setActive(false);driverRepository.flush();
        store.refresh(f.org().getId(),List.of(open(date,17)),before);
        assertThat(store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).slots())
                .filteredOn(AutomaticScheduleDay.Slot::isScareActorTransport).singleElement().satisfies(s->{
                    assertThat(s.getDriverId()).isEqualTo(f.b().getId());
                    assertThat(s.getReason()).contains("Temporarily close wardrobe","reopen when you return");
                });
    }
    @Test void morningFrightFestOpeningRequiresActorTransportWithoutEmployeeDemandAndExpiresAfterNovemberFirst() {
        var date=LocalDate.of(2026,11,1);var before=date.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var f=fixture("fright-morning",date,before);
        store.refresh(f.org().getId(),List.of(open(date,11),open(date.plusDays(1),11)),before);
        var days=store.manager(f.org().getId(),date,date.plusDays(1),f.manager().getEmail());
        assertThat(days.get(0).slots()).hasSize(3);
        var actors=days.get(0).slots().get(1);
        assertThat(actors.getService()).isEqualTo(ShuttleService.SCARE_ACTORS);
        assertThat(actors.getStart()).isEqualTo(date.atTime(17,0));
        assertThat(actors.getEnd()).isEqualTo(date.atTime(17,45));
        // 3h morning + 45m actors versus 4h evening balances the whole day.
        assertThat(days.get(0).slots()).extracting(AutomaticScheduleDay.Slot::getDriverId)
                .containsExactly(f.a().getId(),f.a().getId(),f.b().getId());
        assertThat(actors.getDriverId()).isEqualTo(f.a().getId());
        assertThat(actors.getReason()).isEqualTo("Automatically assigned");
        assertThat(days.get(1).slots()).hasSize(2).allSatisfy(s->assertThat(s.isScareActorTransport()).isFalse());
        for(var status:List.of(ParkOperatingStatus.CLOSED,ParkOperatingStatus.UNKNOWN)) {
            store.refresh(f.org().getId(),List.of(new ParkOperatingDay(date,status,null,null,false,"LIVE_API",null)),before);
            assertThat(store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).slots()).isEmpty();
        }
        assertThat(sessions.count()).isZero();
    }
    @Test void actorDriverContinuesWithTemporaryClosureWhenTheCounterWorkerLeaves() {
        var date=LocalDate.of(2026,10,31);var before=date.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var f=fixture("fright-coverage",date,before);
        work.deleteAll();work.flush();
        work.saveAndFlush(new DriverScheduleWindow(f.a(),f.source(),date.atTime(8,0),date.atTime(17,30)));
        work.saveAndFlush(new DriverScheduleWindow(f.b(),f.source(),date.atTime(8,0),date.atTime(23,0)));
        store.refresh(f.org().getId(),List.of(open(date,11)),before);
        var actors=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).slots().stream()
                .filter(AutomaticScheduleDay.Slot::isScareActorTransport).toList();
        assertThat(actors).hasSize(2);
        assertThat(actors.get(0).getDriverId()).isEqualTo(f.b().getId());
        assertThat(actors.get(0).getReason()).isEqualTo("Automatically assigned");
        assertThat(actors.get(0).getEnd()).isEqualTo(date.atTime(17,30));
        assertThat(actors.get(1).getStart()).isEqualTo(date.atTime(17,30));
        assertThat(actors.get(1).getEnd()).isEqualTo(date.atTime(17,45));
        assertThat(actors.get(1).getDriverId()).isEqualTo(f.b().getId());
        assertThat(actors.get(1).getReason()).contains("Temporarily close wardrobe","reopen when you return");
    }
    @Test void saturdaySoloActorDriverReturnsToWardrobeAndIsNotAssignedPastWorkEnd() {
        var date=LocalDate.of(2026,10,31);var before=date.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var f=fixture("solo-saturday",date,before);
        work.deleteAll();work.flush();
        work.saveAndFlush(new DriverScheduleWindow(f.a(),f.source(),date.atTime(9,0),date.atTime(19,0)));
        work.saveAndFlush(new DriverScheduleWindow(f.b(),f.source(),date.atTime(9,0),date.atTime(15,0)));
        for(int refresh=0;refresh<2;refresh++) {
            store.refresh(f.org().getId(),List.of(open(date,11)),before.plusSeconds(refresh*300));
            entityManager.flush();entityManager.clear();
            var view=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0);
            assertThat(view.slots()).hasSize(3);
            assertThat(view.slots()).filteredOn(AutomaticScheduleDay.Slot::isScareActorTransport).singleElement().satisfies(s->{
                assertThat(s.getStart()).isEqualTo(date.atTime(17,0));assertThat(s.getEnd()).isEqualTo(date.atTime(17,45));
                assertThat(s.getDriverId()).isEqualTo(f.a().getId());
                assertThat(s.getReason()).contains("Temporarily close wardrobe","reopen when you return");
            });
            assertThat(view.slots()).filteredOn(s->s.getService()==ShuttleService.MORNING_OPENING).singleElement()
                    .satisfies(s->assertThat(s.getDriverId()).isNotNull());
            assertThat(view.slots()).filteredOn(s->s.getStart().equals(date.atTime(19,0))).singleElement()
                    .satisfies(s->assertThat(s.getDriverId()).isNull());
            assertThat(view.slots()).noneMatch(s->s.overlaps(date.atTime(17,45),date.atTime(19,0)));
            assertThat(view.wardrobeClosing()).isEqualTo(LocalTime.of(19,0));
        }
        // A solo worker still stays at the counter during ordinary opening service.
        var counter=driverRepository.findById(f.b().getId()).orElseThrow();counter.setActive(false);driverRepository.flush();
        store.refresh(f.org().getId(),List.of(open(date,11)),before.plusSeconds(600));
        var solo=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).slots();
        assertThat(solo).filteredOn(s->s.getService()==ShuttleService.MORNING_OPENING).singleElement()
                .satisfies(s->{assertThat(s.getDriverId()).isNull();assertThat(s.getReason()).contains("wardrobe counter");});
        assertThat(solo).filteredOn(AutomaticScheduleDay.Slot::isScareActorTransport).singleElement()
                .satisfies(s->assertThat(s.getDriverId()).isEqualTo(f.a().getId()));
        assertThat(sessions.count()).isZero();assertThat(assignmentRepository.count()).isZero();
    }
    @Test void soloActorExceptionStillRespectsWorkEndAndActiveRunLimit() {
        var date=LocalDate.of(2026,10,31);var before=date.atTime(8,0).toInstant(ZoneOffset.UTC);
        var f=fixture("solo-limits",date,before);f.b().setActive(false);driverRepository.flush();
        work.deleteAll();work.flush();
        var shift=work.saveAndFlush(new DriverScheduleWindow(f.a(),f.source(),date.atTime(9,0),date.atTime(17,30)));
        store.refresh(f.org().getId(),List.of(open(date,11)),before);
        assertActorCutoff(f,date);
        work.delete(shift);work.flush();
        work.saveAndFlush(new DriverScheduleWindow(f.a(),f.source(),date.atTime(9,0),date.atTime(19,0)));
        var run=sessions.saveAndFlush(DrivingSession.startUnscheduled(f.org(),f.a(),date.atTime(13,30).toInstant(ZoneOffset.UTC),
                f.manager(),DrivingActionMethod.MANAGER));
        store.refresh(f.org().getId(),List.of(open(date,11)),date.atTime(16,30).toInstant(ZoneOffset.UTC));
        assertActorCutoff(f,date);
        assertThat(run.isActive()).isTrue();assertThat(run.getEndedAt()).isNull();
        assertThat(run.getAutomaticEndAt()).isEqualTo(date.atTime(17,30).toInstant(ZoneOffset.UTC));
    }
    private void assertActorCutoff(Fixture f,LocalDate date) {
        var actors=store.manager(f.org().getId(),date,date,f.manager().getEmail()).get(0).slots().stream()
                .filter(AutomaticScheduleDay.Slot::isScareActorTransport).toList();
        assertThat(actors).hasSize(2);
        assertThat(actors.get(0).getDriverId()).isEqualTo(f.a().getId());
        assertThat(actors.get(0).getStart()).isEqualTo(date.atTime(17,0));
        assertThat(actors.get(0).getEnd()).isEqualTo(date.atTime(17,30));
        assertThat(actors.get(1).getDriverId()).isNull();
        assertThat(actors.get(1).getStart()).isEqualTo(date.atTime(17,30));
        assertThat(actors.get(1).getEnd()).isEqualTo(date.atTime(17,45));
        assertThat(actors.get(1).getReason()).contains("No available driver");
    }
    @Test void backgroundJobPlansFromTwoUkgCalendarsWithoutAnyUploadForActiveAndValidTrialOrganizations() {
        var instant=Instant.now();var tomorrow=instant.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);
        var planner=new AutomaticScheduleService(new ParkOperatingDayService(date->open(date,17)),snapshots,store,access,914,"UTC");
        var expected=new ArrayList<Organization>();
        for(var status:List.of(OrganizationStatus.ACTIVE,OrganizationStatus.TRIAL)) {
            var org=organizationRepository.saveAndFlush(new Organization("Calendar only "+status,"Calendar only "+status,
                    "calendar-only-"+status.name().toLowerCase(),status,instant.minusSeconds(60),instant.plusSeconds(86400)));
            expected.add(org);
            for(int i=0;i<2;i++) {
                var driver=driverRepository.saveAndFlush(new Driver(org,"calendar-"+i,"Calendar Driver "+i,true));
                var connection=connections.saveAndFlush(new DriverCalendarConnection(driver,"test-encrypted",String.valueOf(i).repeat(64),instant,ZoneOffset.UTC));
                calendarShifts.saveAndFlush(new DriverCalendarShift(connection,new UkgCalendarParser.Shift(
                        tomorrow.atTime(14,0).toInstant(ZoneOffset.UTC),tomorrow.atTime(22,0).toInstant(ZoneOffset.UTC))));
            }
        }
        var expired=organizationRepository.saveAndFlush(new Organization("Expired trial","Expired trial","expired-calendar-trial",
                OrganizationStatus.TRIAL,instant.minusSeconds(86400),instant.minusSeconds(60)));
        new AutomaticScheduleJob(organizationRepository,planner,true).refresh();entityManager.flush();entityManager.clear();
        for(var org:expected) {
            assertThat(plans.findAllByOrganizationIdAndDateBetweenOrderByDate(org.getId(),tomorrow,tomorrow.plusDays(26))).hasSize(27);
            var slots=plans.findByOrganizationIdAndDate(org.getId(),tomorrow).orElseThrow().slots();
            assertThat(slots).anySatisfy(s->{assertThat(s.getStart()).isEqualTo(tomorrow.atTime(15,0));assertThat(s.getDriverId()).isNotNull();});
            assertThat(slots).anySatisfy(s->{assertThat(s.getStart()).isEqualTo(tomorrow.atTime(19,0));assertThat(s.getEnd()).isEqualTo(tomorrow.atTime(22,0));assertThat(s.getDriverId()).isNotNull();});
            assertThat(slots).anySatisfy(s->{assertThat(s.getStart()).isEqualTo(tomorrow.atTime(22,0));assertThat(s.getEnd()).isEqualTo(tomorrow.atTime(23,0));assertThat(s.getDriverId()).isNull();});
        }
        assertThat(plans.findByOrganizationIdAndDate(expired.getId(),tomorrow)).isEmpty();
        assertThat(scheduleImportRepository.count()).isZero();assertThat(work.count()).isZero();assertThat(demand.count()).isZero();
        assertThat(sessions.count()).isZero();
    }
    @Test void calendarOnlyServiceIncludesAfterMidnightStartsAndStopsAtParkClosing() {
        var f=fixture("overnight-calendar");work.deleteAll();work.flush();
        var first=connections.saveAndFlush(new DriverCalendarConnection(f.a(),"test-encrypted","c".repeat(64),now,ZoneOffset.UTC));
        var second=connections.saveAndFlush(new DriverCalendarConnection(f.b(),"test-encrypted","d".repeat(64),now,ZoneOffset.UTC));
        calendarShifts.saveAndFlush(new DriverCalendarShift(first,new UkgCalendarParser.Shift(day.atTime(14,0).toInstant(ZoneOffset.UTC),day.plusDays(1).atTime(0,30).toInstant(ZoneOffset.UTC))));
        calendarShifts.saveAndFlush(new DriverCalendarShift(second,new UkgCalendarParser.Shift(day.plusDays(1).atTime(0,30).toInstant(ZoneOffset.UTC),day.plusDays(1).atTime(2,0).toInstant(ZoneOffset.UTC))));
        store.refresh(f.org().getId(),List.of(new ParkOperatingDay(day,ParkOperatingStatus.OPEN,LocalTime.of(17,0),LocalTime.of(1,0),false,"LIVE_API",null)),now);
        var slots=views(f).get(0).slots();
        assertThat(slots).anySatisfy(s->{assertThat(s.getStart()).isEqualTo(day.plusDays(1).atTime(0,30));
            assertThat(s.getEnd()).isEqualTo(day.plusDays(1).atTime(1,0));assertThat(s.getDriverId()).isEqualTo(f.b().getId());});
        assertThat(slots).noneMatch(s->s.getEnd().isAfter(day.plusDays(1).atTime(1,0)));
        assertThat(demand.count()).isZero();assertThat(sessions.count()).isZero();
    }
    @Test void confirmingStaleOpenHoursRestoresCoverageOnlyForTheConfirmingOrganization() {
        var f=fixture("stale-confirm");var other=fixture("stale-other");
        var stale=new ParkOperatingDay(day,ParkOperatingStatus.UNKNOWN,LocalTime.of(11,0),LocalTime.of(23,0),false,"STALE_SNAPSHOT","PARK_HOURS_STALE");
        store.refresh(f.org().getId(),List.of(stale),now);
        assertThat(views(f).get(0).slots()).isEmpty();
        assertThat(views(f).get(0).parkOpening()).isEqualTo(LocalTime.of(11,0));
        overrides.confirmParkOpen(f.org().getId(),stale,f.manager().getEmail(),"Verified official calendar: 11 AM to 11 PM.");
        store.refresh(f.org().getId(),List.of(stale),now);store.refresh(other.org().getId(),List.of(stale),now);
        var confirmed=views(f).get(0);
        assertThat(confirmed.status()).isEqualTo("OPEN");assertThat(confirmed.source()).isEqualTo("MANAGER_CONFIRMATION");
        assertThat(confirmed.wardrobeOpening()).isEqualTo(LocalTime.of(9,0));
        assertThat(confirmed.slots()).anySatisfy(s->{assertThat(s.getStart()).isEqualTo(day.atTime(19,0));
            assertThat(s.getEnd()).isEqualTo(day.atTime(23,0));assertThat(s.getDriverId()).isNotNull();});
        assertThat(views(other).get(0).slots()).isEmpty();assertThat(sessions.count()).isZero();
    }
    private record Fixture(Organization org,UserAccount manager,Driver a,Driver b,ScheduleImport source){}
}
