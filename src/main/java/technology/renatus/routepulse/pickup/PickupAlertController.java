package technology.renatus.routepulse.pickup;

import java.time.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;

@Controller
public class PickupAlertController {
    private final PickupAlerts alerts;private final PickupAccess access;private final DriverPortalAccessService drivers;
    public PickupAlertController(PickupAlerts alerts,PickupAccess access,DriverPortalAccessService drivers) { this.alerts=alerts;this.access=access;this.drivers=drivers; }
    @GetMapping("/my-pickup-assistance")
    public String link(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,RedirectAttributes redirect) {
        var scope=access.require(auth,false);if(date!=null) redirect.addAttribute("date",date.toString());
        return "redirect:"+(scope.manager()?"/pickup-assistance":"/driver/pickup-assistance");
    }
    @GetMapping("/driver/pickup-assistance/upcoming")
    public String upcoming(Authentication auth,Model model,HttpServletResponse response) {
        access.require(auth,false);var driver=drivers.requireCurrentDriver(auth);drivers.requireActiveDriver(driver);
        model.addAttribute("pickupAlerts",alerts.forDriver(driver.getOrganization().getId(),driver.getId(),Instant.now()));
        response.setHeader("Cache-Control","no-store");
        return "pickup/upcoming :: upcoming";
    }
    @PostMapping({"/pickup-assistance/outcome","/driver/pickup-assistance/outcome"})
    public String outcome(Authentication auth,@RequestParam Long riderId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant clockOut,
            @RequestParam PickupOutcome.Status outcome,@RequestParam(defaultValue="list") String returnTo,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,RedirectAttributes redirect) {
        var scope=access.require(auth,false);
        try { alerts.outcome(auth,riderId,clockOut,outcome);redirect.addFlashAttribute("successMessage",outcome.getLabel()+" saved."); }
        catch(IllegalArgumentException error) { redirect.addFlashAttribute("errorMessage",error.getMessage()); }
        if("home".equals(returnTo) && !scope.manager()) return "redirect:/driver";
        if(date!=null) redirect.addAttribute("date",date.toString());
        if("schedule".equals(returnTo)) return "redirect:"+(scope.manager()?"/driving-schedule":"/driver/driving-schedule");
        return "redirect:"+(scope.manager()?"/pickup-assistance":"/driver/pickup-assistance");
    }
}
