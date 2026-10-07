package technology.renatus.routepulse.driver.availability;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DriverAvailabilityIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired AvailabilityUpdateService updates;
    @Autowired DriverAvailabilityRepository revisions;

    @Test void driverUpdatesOnlySelfWithCsrfConfirmationAndStaleProtection() throws Exception {
        var org=createOrganization("Own","availability-own");
        var foreignOrg=createOrganization("Foreign","availability-foreign");
        var account=createUser("availability@example.com",true);
        membershipRepository.save(new OrganizationMembership(org,account,OrganizationRole.DRIVER));
        var own=new Driver(org,"100","Own driver",true);own.linkUser(account);driverRepository.saveAndFlush(own);
        var foreign=driverRepository.saveAndFlush(new Driver(foreignOrg,"200","Private foreign driver",true));
        var date=updates.today().plusDays(1);
        var duty=DriverAssignment.automatic(date,ShuttlePeriod.MIDDAY,own);
        duty.updateScheduledTimes(LocalTime.of(15,0),LocalTime.of(18,0));assignmentRepository.saveAndFlush(duty);
        var session=new MockHttpSession();
        var response=mockMvc.perform(get("/driver/availability").session(session).with(user(account.getEmail()).roles("DRIVER")))
                .andExpect(status().isOk()).andReturn();
        String token=((AvailabilityForm)response.getModelAndView().getModel().get("availabilityForm")).getToken();
        mockMvc.perform(post("/driver/availability/confirm").session(session).with(user(account.getEmail()).roles("DRIVER"))
                .param("token",token).param("confirmed","true")).andExpect(status().isForbidden());
        mockMvc.perform(post("/driver/availability/confirm").session(session).with(user(account.getEmail()).roles("DRIVER")).with(csrf())
                .param("token",token).param("confirmed","true").param("rows[0].date",date.toString())
                .param("rows[0].start","17:00").param("rows[0].end","21:00")
                .param("driverId",foreign.getId().toString()).param("organizationId",foreignOrg.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driver/availability"));
        entityManager.flush();entityManager.clear();
        assertThat(revisions.latest(org.getId(),date,date)).hasSize(1);
        assertThat(revisions.latest(foreignOrg.getId(),date,date)).isEmpty();
        var saved=assignmentRepository.findById(duty.getId()).orElseThrow();
        assertThat(saved.isAvailabilityUpdateHold()).isTrue();
        assertThat(saved.getScheduledStartTime()).isEqualTo(LocalTime.of(15,0));
        mockMvc.perform(post("/driver/availability/confirm").session(session).with(user(account.getEmail()).roles("DRIVER")).with(csrf())
                .param("token",token).param("confirmed","true").param("rows[0].date",date.toString())
                .param("rows[0].start","18:00").param("rows[0].end","21:00"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        assertThat(revisions.latest(org.getId(),date,date).get(0).getWorkWindows()).isEqualTo("17:00-21:00");
        mockMvc.perform(get("/driver/availability")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/schedule-updates").with(user(account.getEmail()).roles("DRIVER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/driver/availability").with(user(account.getEmail()).roles("VIEWER"))).andExpect(status().isForbidden());
    }

    @Test void managerCannotRevertAnotherOrganizationsUpdate() {
        var own=createOrganization("Own manager","availability-manager");
        var foreign=createOrganization("Foreign manager","availability-other-manager");
        var driver=driverRepository.saveAndFlush(new Driver(foreign,"300","Other",true));
        var date=updates.today().plusDays(1);
        var revision=revisions.saveAndFlush(new DriverAvailabilityRevision(driver,date,"15:00-21:00","","",false,"MANUAL",null,"other@example.com"));
        assertThatThrownBy(()->updates.useLatestImport(own.getId(),revision.getId(),"manager@example.com"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
    }
}
