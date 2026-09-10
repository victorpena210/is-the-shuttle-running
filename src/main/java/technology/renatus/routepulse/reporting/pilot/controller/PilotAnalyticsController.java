package technology.renatus.routepulse.reporting.pilot.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.reporting.pilot.model.PilotAnalyticsReport;
import technology.renatus.routepulse.reporting.pilot.service.PilotAnalyticsService;

@Controller
public class PilotAnalyticsController {

    private final CurrentOrganizationService currentOrganizationService;
    private final PilotAnalyticsService pilotAnalyticsService;

    public PilotAnalyticsController(
            CurrentOrganizationService currentOrganizationService,
            PilotAnalyticsService pilotAnalyticsService) {
        this.currentOrganizationService = currentOrganizationService;
        this.pilotAnalyticsService = pilotAnalyticsService;
    }

    @GetMapping("/pilot-analytics")
    public String showPilotAnalytics(
            Authentication authentication,
            @RequestParam(name = "week", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            Model model) {

        Organization organization = currentOrganizationService
                .requireOrganization(authentication);

        PilotAnalyticsReport report = pilotAnalyticsService.build(
                organization.getId(),
                week
        );

        model.addAttribute("organizationName", organization.getDisplayName());
        model.addAttribute("report", report);
        model.addAttribute("currentWeekStart", pilotAnalyticsService.currentWeekStart());

        return "manager/pilot-analytics";
    }
}
