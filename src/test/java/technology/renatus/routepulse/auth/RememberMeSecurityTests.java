package technology.renatus.routepulse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import technology.renatus.routepulse.auth.controller.AuthenticationController;
import technology.renatus.routepulse.driver.controller.DriverPortalController;
import technology.renatus.routepulse.driver.service.DriverPortalService;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointAction;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.shared.security.PublicRequestThrottle;
import technology.renatus.routepulse.shared.security.RememberMeConfig;
import technology.renatus.routepulse.shared.security.SecurityConfig;

/** Real security filter chain and controllers, with no MySQL/Docker requirement. */
@SpringJUnitConfig(RememberMeSecurityTests.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "app.security.remember-me.key=test-only-signing-key-at-least-32-characters",
        "app.security.remember-me.days=90",
        "app.security.remember-me.secure-cookie=true"
})
class RememberMeSecurityTests {

    private static final String EMAIL = "driver@example.com";
    private static final String COOKIE = "shuttle-remember-me";
    @Autowired WebApplicationContext context;
    @Autowired InMemoryUserDetailsManager users;
    @Autowired DriverPortalService portal;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users.updateUser(User.withUsername(EMAIL).password("{noop}test-password")
                .roles("DRIVER").build());
        reset(portal);
        when(portal.getCheckpointPreview(any(), eq("tag-code"), eq(DrivingActionMethod.NFC)))
                .thenThrow(new IllegalStateException("No driving window is available."));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void remembersDriverWithoutOldSessionAndKeepsNfcGetReadOnly() throws Exception {
        Cookie remembered = signIn(true).getResponse().getCookie(COOKIE);
        assertThat(remembered).isNotNull();
        assertThat(remembered.getMaxAge()).isEqualTo(90 * 24 * 60 * 60);
        assertThat(remembered.isHttpOnly()).isTrue();
        assertThat(remembered.getSecure()).isTrue();
        assertThat(remembered.getAttribute("SameSite")).isEqualTo("Lax");

        // Deliberately send only the persistent cookie, no previous session.
        mvc.perform(get("/driver/tap").secure(true).queryParam("code", "tag-code").cookie(remembered))
                .andExpect(status().isOk())
                .andExpect(model().attribute("checkpointMethod", DrivingActionMethod.NFC));
        verify(portal).getCheckpointPreview(
                org.mockito.ArgumentMatchers.argThat(auth -> EMAIL.equals(auth.getName())),
                eq("tag-code"), eq(DrivingActionMethod.NFC));
        verify(portal, never()).recordCheckpoint(any(), any(), any(), any());
    }

    @Test
    void signedOutTapReturnsToOriginalCheckpointAfterLogin() throws Exception {
        // Saved-request redirects use the URL query string. param() alone only
        // populates the mock parameter map; queryParam() models the NFC link.
        MvcResult tap = mvc.perform(get("/driver/tap").secure(true).queryParam("code", "tag-code"))
                .andExpect(status().is3xxRedirection()).andReturn();
        MvcResult login = mvc.perform(post("/login").secure(true).with(csrf())
                        .session((MockHttpSession) tap.getRequest().getSession(false))
                        .param("email", EMAIL).param("password", "test-password")
                        .param("remember-me", "true"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(login.getResponse().getRedirectedUrl())
                .contains("/driver/tap", "code=tag-code");
    }

    @Test
    void persistenceRequiresOptInAndLoginDisplaysConfiguredDuration() throws Exception {
        assertThat(signIn(false).getResponse().getCookie(COOKIE)).isNull();
        mvc.perform(get("/login")).andExpect(model().attribute("rememberMeDays", 90));
    }

    @Test
    void driverLoginDiscardsSavedManagerDestinationsAndUsesDriverHome() throws Exception {
        for (String path : java.util.List.of("/app", "/driving-records", "/driving-qr")) {
            MvcResult visit = mvc.perform(get(path).secure(true))
                    .andExpect(status().is3xxRedirection()).andReturn();
            MvcResult login = signInAfter(visit);
            assertThat(login.getResponse().getRedirectedUrl()).isEqualTo("/post-login");
            var session = (MockHttpSession) login.getRequest().getSession(false);
            assertThat(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNull();
            mvc.perform(get("/post-login").secure(true).session(session))
                    .andExpect(redirectedUrl("/driver"));
            // Redirecting to the right home must not grant manager access.
            mvc.perform(get(path).secure(true).session(session)).andExpect(status().isForbidden());
        }
    }

    @Test
    void driverLoginKeepsSavedPersonalScheduleFilters() throws Exception {
        MvcResult visit = mvc.perform(get("/driver/schedule").secure(true)
                        .queryParam("from", "2026-10-10").queryParam("to", "2026-10-16"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(signInAfter(visit).getResponse().getRedirectedUrl())
                .contains("/driver/schedule", "from=2026-10-10", "to=2026-10-16");
    }

    @Test
    void managerLoginKeepsSavedDashboardDate() throws Exception {
        users.updateUser(User.withUsername(EMAIL).password("{noop}test-password").roles("ADMIN").build());
        MvcResult visit = mvc.perform(get("/app").secure(true).queryParam("date", "2026-10-10"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(signInAfter(visit).getResponse().getRedirectedUrl()).contains("/app", "date=2026-10-10");
    }

    private MvcResult signInAfter(MvcResult visit) throws Exception {
        return mvc.perform(post("/login").secure(true).with(csrf())
                        .session((MockHttpSession) visit.getRequest().getSession(false))
                        .param("email", EMAIL).param("password", "test-password"))
                .andExpect(status().is3xxRedirection()).andReturn();
    }

    @Test
    void passwordChangeInvalidatesOldRememberedSignIn() throws Exception {
        Cookie remembered = signIn(true).getResponse().getCookie(COOKIE);
        users.updateUser(User.withUsername(EMAIL).password("{noop}new-password").roles("DRIVER").build());
        mvc.perform(get("/driver/tap").secure(true).queryParam("code", "tag-code").cookie(remembered))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().maxAge(COOKIE, 0));
        verify(portal, never()).getCheckpointPreview(any(), any(), any());
    }

    @Test
    void disabledAccountCannotRestoreSignIn() throws Exception {
        Cookie remembered = signIn(true).getResponse().getCookie(COOKIE);
        users.updateUser(User.withUsername(EMAIL).password("{noop}test-password")
                .roles("DRIVER").disabled(true).build());
        mvc.perform(get("/driver/tap").secure(true).queryParam("code", "tag-code").cookie(remembered))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().maxAge(COOKIE, 0));
        verify(portal, never()).getCheckpointPreview(any(), any(), any());
    }

    @Test
    void logoutClearsPersistentCookie() throws Exception {
        Cookie remembered = signIn(true).getResponse().getCookie(COOKIE);
        mvc.perform(post("/logout").secure(true).with(csrf()).cookie(remembered))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge(COOKIE, 0));
    }

    @Test
    void rememberedDriverCannotUseManagerCheckpointManagement() throws Exception {
        Cookie remembered = signIn(true).getResponse().getCookie(COOKIE);
        mvc.perform(get("/driving-qr").secure(true).cookie(remembered))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void nfcPostStillRequiresCsrf() throws Exception {
        MvcResult login = signIn(true);
        mvc.perform(post("/driver/tap").secure(true)
                        .session((MockHttpSession) login.getRequest().getSession(false))
                        .param("code", "tag-code").param("action", "START"))
                .andExpect(status().isForbidden());
        verify(portal, never()).recordCheckpoint(any(), any(), any(), any());
    }

    @Test
    void rememberedDriverCanSubmitConfirmedNfcStart() throws Exception {
        Cookie remembered = signIn(true).getResponse().getCookie(COOKIE);
        // A service validation error is sufficient to verify authenticated dispatch
        // through the actual controller; successful session persistence is covered
        // by DriverCheckpointServiceTests and DrivingSessionIntegrationTests.
        when(portal.recordCheckpoint(any(), eq("tag-code"), eq(DrivingActionMethod.NFC), eq(CheckpointAction.START)))
                .thenThrow(new IllegalStateException("No driving window is available."));
        mvc.perform(post("/driver/tap").secure(true).with(csrf()).cookie(remembered)
                        .param("code", "tag-code").param("action", "START"))
                .andExpect(redirectedUrl("/driver"));
        verify(portal).recordCheckpoint(
                org.mockito.ArgumentMatchers.argThat(auth -> EMAIL.equals(auth.getName())),
                eq("tag-code"), eq(DrivingActionMethod.NFC), eq(CheckpointAction.START));
    }

    private MvcResult signIn(boolean remember) throws Exception {
        var request = post("/login").secure(true).with(csrf())
                .param("email", EMAIL).param("password", "test-password");
        if (remember) request.param("remember-me", "true");
        return mvc.perform(request).andExpect(redirectedUrl("/post-login")).andReturn();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, RememberMeConfig.class,
            AuthenticationController.class, DriverPortalController.class})
    static class TestConfig {
        @Bean InMemoryUserDetailsManager users() {
            return new InMemoryUserDetailsManager(User.withUsername(EMAIL)
                    .password("{noop}test-password").roles("DRIVER").build());
        }
        @Bean PublicRequestThrottle throttle() { return new PublicRequestThrottle(); }
        @Bean DriverPortalService portal() { return mock(DriverPortalService.class); }
        @Bean InternalResourceViewResolver views() {
            return new InternalResourceViewResolver("/WEB-INF/views/", ".html");
        }
    }
}
