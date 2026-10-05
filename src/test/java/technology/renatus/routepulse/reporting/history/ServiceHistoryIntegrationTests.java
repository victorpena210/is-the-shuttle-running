package technology.renatus.routepulse.reporting.history;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.PassengerRideConfirmation;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.driving.service.DrivingSessionService;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ServiceHistoryIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired ServiceHistoryService history;
    @Autowired DrivingSessionService sessions;
    @Autowired PassengerRideConfirmationRepository confirmations;

    @Test
    void includesOvernightUnscheduledActivityAndKeepsOrganizationBoundaries() throws Exception {
        var org = createOrganization("Service review", "service-review-test");
        var other = createOrganization("Other review", "other-review-test");
        var admin = createUser("service-review-admin@example.com", true);
        membershipRepository.save(new OrganizationMembership(org, admin, OrganizationRole.ADMIN));
        var driver = driverRepository.saveAndFlush(new Driver(org, "review-100", "Review Driver", true));
        LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant midnight = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        var session = sessions.startUnscheduledSession(driver, admin, midnight.minusSeconds(900), DrivingActionMethod.NFC);
        sessions.endSession(null, session, admin, midnight.plusSeconds(900), DrivingActionMethod.NFC);
        confirmations.save(new PassengerRideConfirmation(session, "review-device-hash", midnight, midnight.plusSeconds(300)));
        entityManager.flush();
        var filters = ServiceReviewFilters.resolve(date, date, null, null, null, null, null, date);
        var report = history.build(org.getId(), filters);
        assertThat(report.seconds()).isEqualTo(900);
        assertThat(report.unscheduledSeconds()).isEqualTo(900);
        assertThat(report.confirmations()).isEqualTo(1);
        assertThat(report.reviewCount()).isZero(); // no available forecast/calendar is not a service failure
        assertThat(history.build(other.getId(), filters).seconds()).isZero();
        assertThat(history.build(other.getId(), filters).confirmations()).isZero();
        var foreignDriver = ServiceReviewFilters.resolve(date, date, null, null, driver.getId(), "actual", null, date);
        assertThatThrownBy(() -> history.build(other.getId(), foreignDriver)).isInstanceOf(IllegalArgumentException.class);
        mockMvc.perform(get("/service-history").with(user(admin.getEmail()).roles("ADMIN"))
                .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk()).andExpect(view().name("manager/service-history"))
                .andExpect(content().string(containsString("Review Driver")))
                .andExpect(content().string(containsString("Unscheduled service time")));
        mockMvc.perform(get("/service-history").with(user(admin.getEmail()).roles("DRIVER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/service-history")).andExpect(status().is3xxRedirection());
    }
}
