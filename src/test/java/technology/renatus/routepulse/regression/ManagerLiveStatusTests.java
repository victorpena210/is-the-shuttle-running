package technology.renatus.routepulse.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.dashboard.controller.DashboardController;
import technology.renatus.routepulse.driving.controller.RiderStatusController;
import technology.renatus.routepulse.driving.service.RiderStatusService;
import technology.renatus.routepulse.driving.service.RiderStatusService.RiderStatus;
import technology.renatus.routepulse.operations.domain.RiderOperationalStatus;
import technology.renatus.routepulse.operations.service.OperationalStatusService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.support.WebTemplateContext;

class ManagerLiveStatusTests {
    @Test void managerShowsTheSameCardAsPassengersForEveryStatusAndStaysOutsideHiddenDashboardViews() {
        var resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var organizations = mock(CurrentOrganizationService.class);
        var organization = mock(Organization.class);
        var authentication = mock(Authentication.class);
        when(organizations.requireOrganization(authentication)).thenReturn(organization);
        when(organization.getId()).thenReturn(7L);
        when(organization.getSlug()).thenReturn("test-shuttle");
        when(organization.getDisplayName()).thenReturn("Test Shuttle");
        var operations = mock(OperationalStatusService.class);
        when(operations.findLatest(7L)).thenReturn(Optional.empty());
        var riders = mock(RiderStatusService.class);
        var manager = new DashboardController(organizations, operations, riders);
        var passenger = new RiderStatusController(riders);

        for (var state : RiderOperationalStatus.values()) {
            String name = state == RiderOperationalStatus.NOT_RUNNING ? null : "Alex <script>";
            var status = new RiderStatus("Test Shuttle", state, "Private headline", "Private detail",
                    "Private message", name, false, "unused", List.of(), "Private date", "America/Chicago");
            when(riders.getStatus("test-shuttle")).thenReturn(status);
            var managerModel = new ExtendedModelMap();
            assertThat(manager.showDashboard(authentication, managerModel)).isEqualTo("manager/dashboard");
            var passengerModel = new ExtendedModelMap();
            passenger.showRiderStatus("test-shuttle", new MockHttpServletResponse(), passengerModel);
            String managerHtml = engine.process("manager/dashboard", WebTemplateContext.create(managerModel));
            String passengerHtml = engine.process("public/rider-status", WebTemplateContext.create(passengerModel));
            assertThat(card(managerHtml)).isEqualTo(card(passengerHtml).replace("<h1 ", "<h2 ").replace("</h1>", "</h2>"));
            assertThat(managerHtml).contains("What passengers see", "data-live-rider-card", "data-refresh-url=\"/app/live-status\"",
                    "/js/pages/live-status.js", "/ride/test-shuttle", "/operations/status")
                    .doesNotContain("data-dashboard-section", "data-plan-container", "Private", "Alex <script>");
            assertThat(card(managerHtml)).contains(status.running() ? "The shuttle is running." : "The shuttle is not running.");
            if (name == null) assertThat(card(managerHtml)).doesNotContain("Driver:");
            else assertThat(card(managerHtml)).contains("Driver: Alex &lt;script&gt;");
        }
        verify(organizations, times(RiderOperationalStatus.values().length)).requireOrganization(authentication);
        verify(riders, times(2 * RiderOperationalStatus.values().length)).getStatus("test-shuttle");
        verifyNoMoreInteractions(riders);
    }

    private String card(String html) {
        int start = html.indexOf("<section class=\"rider-status-card");
        assertThat(start).isGreaterThanOrEqualTo(0);
        return html.substring(start, html.indexOf("</section>", start) + "</section>".length())
                .replaceAll("\\s+", " ");
    }
}
