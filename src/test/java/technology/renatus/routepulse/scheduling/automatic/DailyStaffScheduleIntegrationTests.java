package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.driver.calendar.*;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.scheduling.demand.*;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DailyStaffScheduleIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DailyStaffScheduleService staff;
    @Autowired AutomaticScheduleStore store;
    @Autowired AutomaticScheduleRepository plans;
    @Autowired DriverScheduleWindowRepository work;
    @Autowired DriverCalendarConnectionRepository connections;
    @Autowired DriverCalendarShiftRepository shifts;

    @Test void showsUkgHoursAndCounterStaffWithoutAssignmentsWhileKeepingPersonalAndOrganizationIsolation() throws Exception {
        var now=Instant.now();var day=now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);
        var org=createOrganization("Staff roster","staff-roster");var manager=createUser("roster-manager@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,manager,OrganizationRole.OWNER));
        var alex=driverRepository.saveAndFlush(new Driver(org,"1","Alex UKG",true));
        var blair=driverRepository.saveAndFlush(new Driver(org,"2","Blair Counter",true));
        var inactive=driverRepository.saveAndFlush(new Driver(org,"3","INACTIVE-STAFF",false));
        var source=scheduleImportRepository.saveAndFlush(new ScheduleImport(org,manager,"roster-file","roster.csv",3,day,day,day.toString(),now));
        for(var driver:List.of(alex,blair,inactive)) work.saveAndFlush(new DriverScheduleWindow(driver,source,day.atTime(8,0),day.atTime(23,0)));
        var connection=connections.saveAndFlush(new DriverCalendarConnection(alex,"test-encrypted","a".repeat(64),now,ZoneOffset.UTC));
        shifts.saveAndFlush(new DriverCalendarShift(connection,new UkgCalendarParser.Shift(day.atTime(9,0).toInstant(ZoneOffset.UTC),day.atTime(18,0).toInstant(ZoneOffset.UTC))));
        var other=createOrganization("Other team","other-roster");
        var outsider=driverRepository.saveAndFlush(new Driver(other,"1","PRIVATE-STAFF",true));
        var privateConnection=connections.saveAndFlush(new DriverCalendarConnection(outsider,"test-encrypted","b".repeat(64),now,ZoneOffset.UTC));
        shifts.saveAndFlush(new DriverCalendarShift(privateConnection,new UkgCalendarParser.Shift(day.atTime(9,0).toInstant(ZoneOffset.UTC),day.atTime(18,0).toInstant(ZoneOffset.UTC))));
        entityManager.clear();
        var view=store.manager(org.getId(),day,day,manager.getEmail()).get(0);
        assertThat(view.savedPlan()).isFalse();assertThat(view.slots()).isEmpty();
        assertThat(view.workShifts()).extracting(DailyStaffScheduleService.WorkShift::name).containsExactly("Blair Counter","Alex UKG");
        assertThat(view.workShifts().get(1).start()).isEqualTo(day.atTime(9,0));
        assertThat(view.workShifts().get(1).end()).isEqualTo(day.atTime(18,0));
        mockMvc.perform(get("/driving-schedule").param("date",day.toString())
                .with(authentication(authenticationFor(manager,OrganizationRole.OWNER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Who’s working")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Blair Counter")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alex UKG")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-STAFF"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("INACTIVE-STAFF"))));
        var user=createUser("roster-driver@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,user,OrganizationRole.DRIVER));
        var linked=driverRepository.findById(alex.getId()).orElseThrow();linked.linkUser(user);driverRepository.flush();
        mockMvc.perform(get("/driver/driving-schedule").param("date",day.toString()).param("driverId",blair.getId().toString())
                .with(authentication(authenticationFor(user,OrganizationRole.DRIVER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("My work schedule")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alex UKG")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Blair Counter"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-STAFF"))));
        assertThat(plans.count()).isZero();assertThat(assignmentRepository.count()).isZero();
    }

    @Test void emptyAndStaleCalendarsReplaceUploadsWithoutClaimingPeopleAreWorking() {
        var now=Instant.now();var day=now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);
        var org=createOrganization("Missing work hours","missing-work-hours");var manager=createUser("missing-work@example.com",true);
        var source=scheduleImportRepository.saveAndFlush(new ScheduleImport(org,manager,"missing-work-file","work.csv",2,day,day,day.toString(),now));
        for(int i=0;i<2;i++) {
            var driver=driverRepository.saveAndFlush(new Driver(org,"d"+i,"Driver "+i,true));
            work.saveAndFlush(new DriverScheduleWindow(driver,source,day.atTime(8,0),day.atTime(23,0)));
            var connection=connections.saveAndFlush(new DriverCalendarConnection(driver,"test-encrypted",String.valueOf(i).repeat(64),
                    i==0?now:now.minusSeconds(90000),ZoneOffset.UTC));
            if(i==1) shifts.saveAndFlush(new DriverCalendarShift(connection,new UkgCalendarParser.Shift(day.atTime(9,0).toInstant(ZoneOffset.UTC),day.atTime(18,0).toInstant(ZoneOffset.UTC))));
        }
        assertThat(staff.byDay(org.getId(),day,day,null,now).get(day)).isEmpty();
        assertThat(work.count()).isEqualTo(2);
    }
}
