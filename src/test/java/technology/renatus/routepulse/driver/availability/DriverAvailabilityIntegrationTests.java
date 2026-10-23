package technology.renatus.routepulse.driver.availability;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Test void retiredScheduleUpdatesCannotChangeAvailability() throws Exception {
        var org=createOrganization("Own","availability-own");
        var account=createUser("availability@example.com",true);
        membershipRepository.save(new OrganizationMembership(org,account,OrganizationRole.DRIVER));
        long before = revisions.count();
        mockMvc.perform(get("/driver/availability").with(user(account.getEmail()).roles("DRIVER")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driver"));
        mockMvc.perform(post("/driver/availability/confirm").with(user(account.getEmail()).roles("DRIVER")).with(csrf()))
                .andExpect(status().isGone());
        assertThat(revisions.count()).isEqualTo(before);
        mockMvc.perform(get("/schedule-updates").with(user(account.getEmail()).roles("DRIVER"))).andExpect(status().isForbidden());
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
