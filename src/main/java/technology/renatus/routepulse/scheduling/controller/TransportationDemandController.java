package technology.renatus.routepulse.scheduling.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.scheduling.demand.DemandForecastService;

@Controller
public class TransportationDemandController {
    private final CurrentOrganizationService current;
    private final DriverPortalAccessService drivers;
    private final DemandForecastService forecasts;
    public TransportationDemandController(CurrentOrganizationService current, DriverPortalAccessService drivers, DemandForecastService forecasts) {
        this.current = current; this.drivers = drivers; this.forecasts = forecasts;
    }
    @GetMapping("/transportation-demand")
    public String manager(Authentication auth, @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        return page(current.requireOrganization(auth), date, true, model);
    }
    @GetMapping("/driver/demand")
    public String driver(Authentication auth, @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        return page(drivers.requireCurrentDriver(auth).getOrganization(), date, false, model);
    }
    @GetMapping("/upload")
    public String uploadBookmark() { return "redirect:/transportation-demand#upload"; }
    private String page(Organization organization, LocalDate date, boolean manager, Model model) {
        model.addAttribute("organizationName", organization.getDisplayName());
        model.addAttribute("forecast", forecasts.page(organization.getId(), date == null ? forecasts.today() : date));
        model.addAttribute("managerView", manager);
        model.addAttribute("demandUrl", manager ? "/transportation-demand" : "/driver/demand");
        model.addAttribute("operationsZone", forecasts.zoneName());
        model.addAttribute("today", forecasts.today());
        return "demand/transportation";
    }
}
