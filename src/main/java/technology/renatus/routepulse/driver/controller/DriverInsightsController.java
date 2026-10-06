package technology.renatus.routepulse.driver.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import technology.renatus.routepulse.driver.insights.DriverAnalyticsRange;
import technology.renatus.routepulse.driver.insights.DriverInsightsService;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;

@Controller
public class DriverInsightsController {
    private final DriverPortalAccessService access;
    private final DriverInsightsService insights;

    public DriverInsightsController(DriverPortalAccessService access, DriverInsightsService insights) {
        this.access = access; this.insights = insights;
    }

    @GetMapping("/driver/demand")
    public String demand(Authentication authentication, Model model) {
        populate(authentication, model, "demand");
        model.addAttribute("demandView", insights.todayDemand(authentication));
        return "driver/demand";
    }

    @GetMapping("/driver/analytics")
    public String analytics(Authentication authentication,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "range", required = false) String range, Model model) {
        populate(authentication, model, "analytics");
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        try {
            var dates = DriverAnalyticsRange.resolve(from, to, range, insights.today());
            model.addAttribute("from", dates.from());
            model.addAttribute("to", dates.to());
            model.addAttribute("report", insights.analytics(authentication, dates));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("filterError", exception.getMessage());
        }
        return "driver/analytics";
    }

    private void populate(Authentication authentication, Model model, String activePage) {
        var driver = access.requireCurrentDriver(authentication);
        model.addAttribute("organizationName", driver.getOrganization().getDisplayName());
        model.addAttribute("driverName", driver.getName());
        model.addAttribute("driverPage", activePage);
        model.addAttribute("today", insights.today());
        model.addAttribute("operationsZone", insights.zoneName());
    }
}
