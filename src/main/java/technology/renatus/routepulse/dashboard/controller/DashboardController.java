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
import technology.renatus.routepulse.driving.service.RiderStatusService;
import technology.renatus.routepulse.operations.service.OperationalStatusService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DashboardController {

    private final CurrentOrganizationService currentOrganizationService;
    private final DashboardPlanningService dashboardPlanningService;
    private final DashboardModelAssembler dashboardModelAssembler;
    private final AttentionCenterService attentionCenterService;
    private final OperationalStatusService operationalStatusService;
    private final RiderStatusService riderStatusService;

    public DashboardController(
            CurrentOrganizationService currentOrganizationService,
            DashboardPlanningService dashboardPlanningService,
            DashboardModelAssembler dashboardModelAssembler,
            AttentionCenterService attentionCenterService,
            OperationalStatusService operationalStatusService,
            RiderStatusService riderStatusService) {
        this.currentOrganizationService = currentOrganizationService;
        this.dashboardPlanningService = dashboardPlanningService;
        this.dashboardModelAssembler = dashboardModelAssembler;
        this.attentionCenterService = attentionCenterService;
        this.operationalStatusService = operationalStatusService;
        this.riderStatusService = riderStatusService;
    }

    @GetMapping("/app/attention")
    public String showAttentionCenter(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        Organization organization =
                currentOrganizationService.requireOrganization(authentication);

        DashboardPlan plan = dashboardPlanningService.getReadOnlyPlan(
                organization.getId(),
                date);

        model.addAttribute(
                "attentionCenter",
                attentionCenterService.build(organization.getId(), plan));

        return "fragments/dashboard/attention-center :: attentionCenter";
    }

    @GetMapping("/app")
    public String showDashboard(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        Organization organization = currentOrganizationService.requireOrganization(authentication);
        model.addAttribute("organizationName", organization.getDisplayName());
        model.addAttribute("organizationSlug", organization.getSlug());
        model.addAttribute("isOwner", authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER")));
        var operationalStatusUpdate = operationalStatusService.findLatest(organization.getId()).orElse(null);
        model.addAttribute("operationalStatusUpdate", operationalStatusUpdate);
        model.addAttribute("currentRiderStatus", riderStatusService.getStatus(organization.getSlug()));
        DashboardPlan plan = dashboardPlanningService.getPlan(
                organization.getId(),
                date);

        dashboardModelAssembler.populate(model, plan);
        model.addAttribute(
                "attentionCenter",
                attentionCenterService.build(organization.getId(), plan));
        return "manager/dashboard";
    }
}
