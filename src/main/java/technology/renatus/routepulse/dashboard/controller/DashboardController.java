package technology.renatus.routepulse.dashboard.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import technology.renatus.routepulse.attention.service.AttentionCenterService;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.dashboard.service.DashboardPlanningService;
import technology.renatus.routepulse.dashboard.view.DashboardModelAssembler;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DashboardController {

    private final CurrentOrganizationService currentOrganizationService;
    private final DashboardPlanningService dashboardPlanningService;
    private final DashboardModelAssembler dashboardModelAssembler;
    private final AttentionCenterService attentionCenterService;

    public DashboardController(
            CurrentOrganizationService currentOrganizationService,
            DashboardPlanningService dashboardPlanningService,
            DashboardModelAssembler dashboardModelAssembler,
            AttentionCenterService attentionCenterService) {
        this.currentOrganizationService = currentOrganizationService;
        this.dashboardPlanningService = dashboardPlanningService;
        this.dashboardModelAssembler = dashboardModelAssembler;
        this.attentionCenterService = attentionCenterService;
    }

    @GetMapping("/app")
    public String showDashboard(Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean allowUnverifiedCalendar, Model model) {
        Organization organization = currentOrganizationService.requireOrganization(authentication);
        model.addAttribute("organizationName", organization.getDisplayName());
        model.addAttribute("organizationSlug", organization.getSlug());
        model.addAttribute("isOwner", authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER")));
        DashboardPlan plan = dashboardPlanningService.getPlan(
                organization.getId(),
                date,
                allowUnverifiedCalendar
        );

        dashboardModelAssembler.populate(model, plan);
        model.addAttribute(
                "attentionCenter",
                attentionCenterService.build(organization.getId(), plan)
        );
        return "manager/dashboard";
    }
}
