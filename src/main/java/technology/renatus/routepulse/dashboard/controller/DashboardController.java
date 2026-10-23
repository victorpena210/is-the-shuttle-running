package technology.renatus.routepulse.dashboard.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import technology.renatus.routepulse.driving.service.RiderStatusService;
import technology.renatus.routepulse.operations.service.OperationalStatusService;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DashboardController {
    private final CurrentOrganizationService organizations;
    private final OperationalStatusService operations;
    private final RiderStatusService riders;

    public DashboardController(CurrentOrganizationService organizations,
            OperationalStatusService operations, RiderStatusService riders) {
        this.organizations = organizations;
        this.operations = operations;
        this.riders = riders;
    }

    @GetMapping("/app/live-status")
    public String showDashboard(Authentication authentication, Model model) {
        var organization = organizations.requireOrganization(authentication);
        model.addAttribute("organizationName", organization.getDisplayName());
        model.addAttribute("organizationSlug", organization.getSlug());
        model.addAttribute("operationalStatusUpdate", operations.findLatest(organization.getId()).orElse(null));
        model.addAttribute("currentRiderStatus", riders.getStatus(organization.getSlug()));
        return "manager/dashboard";
    }
}
