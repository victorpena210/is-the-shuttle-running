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
    private final technology.renatus.routepulse.scheduling.demand.DriverSuggestionService suggestions;
    private final technology.renatus.routepulse.driver.calendar.DriverCalendarAvailability calendars;
    public TransportationDemandController(CurrentOrganizationService current, DriverPortalAccessService drivers, DemandForecastService forecasts,
            technology.renatus.routepulse.scheduling.demand.DriverSuggestionService suggestions,
            technology.renatus.routepulse.driver.calendar.DriverCalendarAvailability calendars) {
        this.current = current; this.drivers = drivers; this.forecasts = forecasts; this.suggestions = suggestions;
        this.calendars = calendars;
    }
    @GetMapping("/transportation-demand")
    public String manager(Authentication auth, @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        return page(current.requireOrganization(auth), date, true, auth.getName(), model);
    }
    @GetMapping("/driver/demand")
    public String driver(Authentication auth, @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        return page(drivers.requireCurrentDriver(auth).getOrganization(), date, false, auth.getName(), model);
    }
    @GetMapping("/upload")
    public String uploadBookmark() { return "redirect:/transportation-demand#upload"; }
    private String page(Organization organization, LocalDate date, boolean manager, String email, Model model) {
        model.addAttribute("organizationName", organization.getDisplayName());
        var forecast = forecasts.page(organization.getId(), date == null ? forecasts.today() : date);
        model.addAttribute("forecast", forecast);
        if (manager) model.addAttribute("suggestions", suggestions.page(organization.getId(), forecast, email));
        if (manager) model.addAttribute("calendarStates", calendars.states(organization.getId()));
        model.addAttribute("managerView", manager);
        model.addAttribute("demandUrl", manager ? "/transportation-demand" : "/driver/demand");
        model.addAttribute("operationsZone", forecasts.zoneName());
        model.addAttribute("today", forecasts.today());
        return "demand/transportation";
    }
}
