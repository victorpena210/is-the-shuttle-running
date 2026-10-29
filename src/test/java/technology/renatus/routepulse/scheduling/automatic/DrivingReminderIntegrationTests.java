package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import technology.renatus.routepulse.driver.calendar.*;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.scheduling.demand.*;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DrivingReminderIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DrivingReminderStore reminders;
    @Autowired DrivingReminderRepository deliveries;
    @Autowired DrivingReminderSettings settings;
    @Autowired AutomaticScheduleRepository plans;
    @Autowired DriverScheduleWindowRepository work;
    @Autowired DriverCalendarConnectionRepository connections;
    @Autowired DriverCalendarShiftRepository calendarShifts;

    private final LocalDate day = LocalDate.now(ZoneOffset.UTC).plusDays(1);
    private Instant due() { return day.atTime(8, 55).toInstant(ZoneOffset.UTC); }

    private Fixture fixture(String slug) {
        var org = createOrganization(slug, slug);
        var manager = createUser(slug + "@example.com", true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org, manager, OrganizationRole.OWNER));
        var driver = driverRepository.saveAndFlush(new Driver(org, "1", "Assigned Driver", "login@example.com", true));
        driver.changeDrivingReminderEmail("work@example.com");
        var other = driverRepository.saveAndFlush(new Driver(org, "2", "Other Driver", true));
        var source = scheduleImportRepository.saveAndFlush(new ScheduleImport(org, manager, slug, "work.csv", 1,
                day, day, day.toString(), due()));
        work.saveAndFlush(new DriverScheduleWindow(driver, source, day.atTime(9, 0), day.atTime(23, 0)));
        var plan = new AutomaticScheduleDay(org.getId(), day);
        plan.update("OPEN", "LIVE_API", LocalTime.of(11, 0), LocalTime.of(23, 0), LocalTime.of(9, 0), LocalTime.of(19, 0),
                due(), "Test plan", List.of(
                    new AutomaticScheduleDay.Slot(day.atTime(17, 0), day.atTime(17, 45), driver.getId(), driver.getName(), "assigned", ShuttleService.SCARE_ACTORS),
                    new AutomaticScheduleDay.Slot(day.atTime(21, 0), day.atTime(23, 0), driver.getId(), driver.getName(), "assigned"),
                    new AutomaticScheduleDay.Slot(day.atTime(9, 0), day.atTime(12, 0), other.getId(), "PRIVATE-OTHER-DRIVER", "assigned")));
        plans.saveAndFlush(plan);
        return new Fixture(org, driver, manager.getEmail(), plan);
    }

    @Test void sendsBeforeWorkNotDrivingAndKeepsDurableDuplicateProtection() {
        var f = fixture("email-clock-in");
        assertThat(reminders.claimNext(f.org.getId(), due().minusSeconds(1))).isEmpty();
        var notice = reminders.claimNext(f.org.getId(), due()).orElseThrow();
        assertThat(notice.recipient()).isEqualTo("work@example.com");
        assertThat(notice.assignments()).hasSize(2);
        assertThat(notice.body("https://example.com")).doesNotContain("PRIVATE-OTHER-DRIVER");
        entityManager.flush(); entityManager.clear();
        assertThat(reminders.claimNext(f.org.getId(), due().plusSeconds(60))).isEmpty();
        reminders.sent(notice.deliveryId(), due().plusSeconds(2));
        entityManager.flush(); entityManager.clear();
        assertThat(reminders.claimNext(f.org.getId(), due().plusSeconds(120))).isEmpty();
        assertThat(deliveries.findById(notice.deliveryId()).orElseThrow().getStatus()).isEqualTo(DrivingReminderDelivery.Status.SENT);
    }

    @Test void neverSendsEmptyClosedStaleInactiveOrPastShiftReminders() {
        var f = fixture("email-skips");
        f.driver.changeDrivingReminderEmail(null);
        assertThat(reminders.claimNext(f.org.getId(), due())).isEmpty();
        f.driver.changeDrivingReminderEmail("work@example.com"); f.driver.setActive(false);
        assertThat(reminders.claimNext(f.org.getId(), due())).isEmpty();
        f.driver.setActive(true);
        assertThat(reminders.claimNext(f.org.getId(), due().plusSeconds(300))).isEmpty();
        f.plan.update("OPEN", "LIVE_API", null, null, null, null, due().minusSeconds(601), "stale", f.plan.slots());
        assertThat(reminders.claimNext(f.org.getId(), due())).isEmpty();
        f.plan.update("CLOSED", "LIVE_API", null, null, null, null, due(), "closed", f.plan.slots());
        assertThat(reminders.claimNext(f.org.getId(), due())).isEmpty();
        f.plan.update("OPEN", "LIVE_API", null, null, null, null, due(), "no assignments", List.of());
        assertThat(reminders.claimNext(f.org.getId(), due())).isEmpty();
        assertThat(deliveries.count()).isZero();
    }

    @Test void knownFailuresRetryWithinWindowButUncertainDeliveryDoesNotReplay() {
        var f = fixture("email-retry");
        var first = reminders.claimNext(f.org.getId(), due()).orElseThrow();
        reminders.failed(first.deliveryId(), true);
        assertThat(reminders.claimNext(f.org.getId(), due().plusSeconds(59))).isEmpty();
        var retry = reminders.claimNext(f.org.getId(), due().plusSeconds(60)).orElseThrow();
        assertThat(retry.deliveryId()).isEqualTo(first.deliveryId());
        reminders.failed(retry.deliveryId(), false);
        assertThat(reminders.claimNext(f.org.getId(), due().plusSeconds(120))).isEmpty();
        assertThat(deliveries.count()).isEqualTo(1);
    }

    @Test void connectedUkgClockInReplacesUploadedHoursAndDaysOffSuppressEmail() {
        var f = fixture("email-ukg");
        var connection = connections.saveAndFlush(new DriverCalendarConnection(f.driver, "test-encrypted", "a".repeat(64), due(), ZoneOffset.UTC));
        assertThat(reminders.claimNext(f.org.getId(), due())).isEmpty();
        calendarShifts.saveAndFlush(new DriverCalendarShift(connection, new UkgCalendarParser.Shift(
                day.atTime(15, 0).toInstant(ZoneOffset.UTC), day.atTime(23, 0).toInstant(ZoneOffset.UTC))));
        assertThat(reminders.claimNext(f.org.getId(), due())).isEmpty();
        var afternoon = day.atTime(14, 55).toInstant(ZoneOffset.UTC);
        f.plan.update("OPEN", "LIVE_API", null, null, null, null, afternoon, "current", f.plan.slots());
        assertThat(reminders.claimNext(f.org.getId(), afternoon).orElseThrow().workStart()).isEqualTo(day.atTime(15, 0));
    }

    @Test void organizationIsolationAndManagerOnlySettingsLeaveLoginAddressAlone() throws Exception {
        var f = fixture("email-settings");
        var other = fixture("email-outsider");
        settings.update(f.org.getId(), f.driver.getId(), "NEW@example.com", f.manager);
        assertThat(f.driver.getDrivingReminderEmail()).isEqualTo("new@example.com");
        assertThat(f.driver.getEmail()).isEqualTo("login@example.com");
        assertThatThrownBy(() -> settings.update(f.org.getId(), other.driver.getId(), "new@example.com", f.manager))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.update(other.org.getId(), other.driver.getId(), "new@example.com", f.manager))
                .isInstanceOf(AccessDeniedException.class);
        var driverUser = createUser("driver-reminder@example.com", true);
        membershipRepository.saveAndFlush(new OrganizationMembership(f.org, driverUser, OrganizationRole.DRIVER));
        mockMvc.perform(post("/drivers/{id}/driving-reminder-email", f.driver.getId()).param("email", "new@example.com")
                .with(csrf()).with(authentication(authenticationFor(driverUser, OrganizationRole.DRIVER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/my-driving-assignments").param("date", day.toString())
                .with(authentication(authenticationFor(driverUser, OrganizationRole.DRIVER))))
                .andExpect(redirectedUrl("/driver/driving-schedule?date=" + day));
        var owner = userAccountRepository.findByEmailIgnoreCase(f.manager).orElseThrow();
        mockMvc.perform(get("/drivers").with(authentication(authenticationFor(owner, OrganizationRole.OWNER))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Save reminder email")));
        mockMvc.perform(post("/drivers/{id}/driving-reminder-email", f.driver.getId()).param("email", "")
                .with(csrf()).with(authentication(authenticationFor(owner, OrganizationRole.OWNER))))
                .andExpect(redirectedUrl("/drivers"));
        assertThat(f.driver.getDrivingReminderEmail()).isNull();
        assertThat(reminders.claimNext(f.org.getId(), due())).isEmpty();
        assertThat(reminders.claimNext(other.org.getId(), due()).orElseThrow().recipient()).isEqualTo("work@example.com");
    }

    private record Fixture(Organization org, Driver driver, String manager, AutomaticScheduleDay plan) { }
}
