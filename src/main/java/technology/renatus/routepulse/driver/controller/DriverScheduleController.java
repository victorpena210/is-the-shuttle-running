package technology.renatus.routepulse.driver.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import technology.renatus.routepulse.driver.schedule.DriverUpcomingScheduleService;
import technology.renatus.routepulse.driver.schedule.DriverUpcomingScheduleService.DateRange;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;

@Controller
public class DriverScheduleController {
    private final DriverPortalAccessService access;
    private final DriverUpcomingScheduleService schedule;

    public DriverScheduleController(DriverPortalAccessService access, DriverUpcomingScheduleService schedule) {
        this.access = access; this.schedule = schedule;
    }

    @GetMapping("/driver/schedule")
    public String schedule(Authentication authentication,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        var driver = access.requireCurrentDriver(authentication);
        model.addAttribute("organizationName", driver.getOrganization().getDisplayName());
        model.addAttribute("driverName", driver.getName());
        model.addAttribute("driverPage", "schedule");
        model.addAttribute("today", schedule.today());
        model.addAttribute("operationsZone", schedule.zoneName());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        try {
            var dates = DateRange.resolve(from, to, schedule.today());
            model.addAttribute("from", dates.from());
            model.addAttribute("to", dates.to());
            model.addAttribute("scheduleView", schedule.upcoming(authentication, dates));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("filterError", exception.getMessage());
        }
        return "driver/schedule";
    }
}
