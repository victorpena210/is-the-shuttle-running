package technology.renatus.routepulse.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.scheduling.model.UploadedShift;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class SecurityIntegrationTests extends MySqlIntegrationTestSupport {

    @Test
void httpSecurityProtectsOrganizationRoutesAndPostRequests()
        throws Exception {

    Organization organization = createOrganization(
            "HTTP Security Organization",
            "http-security-organization"
    );

    UserAccount owner = createUser(
            "http-owner@example.com",
            true
    );

    UserAccount admin = createUser(
            "http-admin@example.com",
            true
    );

    membershipRepository.save(
            new OrganizationMembership(
                    organization,
                    owner,
                    OrganizationRole.OWNER
            )
    );

    membershipRepository.save(
            new OrganizationMembership(
                    organization,
                    admin,
                    OrganizationRole.ADMIN
            )
    );

    uploadedShiftRepository.save(
            new UploadedShift(
                    organization,
                    null,
                    "100",
                    "Protected Driver",
                    "Transportation",
                    "Shuttle Driver",
                    LocalDate.of(2026, 9, 4),
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    "Employee entrance",
                    Instant.now()
            )
    );

    /*
     * The RoutePulse landing page is intentionally public.
     * Anonymous visitors should be able to view the homepage
     * and submit a demo request.
     */
    mockMvc.perform(
            get("/")
    ).andExpect(
            status().isOk()
    ).andExpect(
            header().exists("X-RoutePulse-Request-Id")
    );

    /*
     * Health probes must remain public so Railway/GitHub/external uptime
     * monitors can detect an outage before a manager has to report it.
     */
    mockMvc.perform(
            get("/actuator/health/liveness")
    ).andExpect(
            status().isOk()
    );

    mockMvc.perform(
            get("/actuator/health/readiness")
    ).andExpect(
            status().isOk()
    );

    /*
     * Authenticated POST without CSRF must still be rejected.
     */
    mockMvc.perform(
            post("/drivers")
                    .with(
                            user(owner.getEmail())
                                    .roles("OWNER")
                    )
                    .param("employeeId", "100")
                    .param("email", "protected-driver@example.com")
                    .param("active", "true")
    ).andExpect(
            status().isForbidden()
    );

    /*
     * Owner with a valid CSRF token is allowed to
     * register the driver.
     */
    mockMvc.perform(
            post("/drivers")
                    .with(
                            user(owner.getEmail())
                                    .roles("OWNER")
                    )
                    .with(csrf())
                    .param("employeeId", "100")
                    .param("email", "protected-driver@example.com")
                    .param("active", "true")
    ).andExpect(
            status().is3xxRedirection()
    );

    assertTrue(
            driverRepository
                    .existsByOrganization_IdAndEmployeeId(
                            organization.getId(),
                            "100"
                    )
    );

    Driver registeredDriver =
            driverRepository
                    .findAllByOrganization_IdOrderByNameAsc(
                            organization.getId()
                    )
                    .get(0);

    assertEquals(
            "Protected Driver",
            registeredDriver.getName()
    );

    /*
     * ADMIN is still not allowed to use owner-only
     * organization invitation management.
     */
    mockMvc.perform(
            get("/organization/invitations")
                    .with(
                            user(admin.getEmail())
                                    .roles("ADMIN")
                    )
    ).andExpect(
            status().isForbidden()
    );

    /*
     * Password recovery and verification recovery
     * pages remain public.
     */
    mockMvc.perform(
            get("/forgot-password")
    ).andExpect(
            status().isOk()
    );

    mockMvc.perform(
            get("/resend-verification")
    ).andExpect(
            status().isOk()
    );
}
}
