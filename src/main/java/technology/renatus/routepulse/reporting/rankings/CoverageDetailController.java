package technology.renatus.routepulse.reporting.rankings;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleIdentityService;

@Controller
public class CoverageDetailController {
    private final CoverageDetailService details;
    private final CurrentOrganizationService organizations;
    private final DriverPortalAccessService drivers;
    private final DriverScheduleIdentityService access;
    public CoverageDetailController(CoverageDetailService details,CurrentOrganizationService organizations,
            DriverPortalAccessService drivers,DriverScheduleIdentityService access) {
        this.details=details;this.organizations=organizations;this.drivers=drivers;this.access=access;
    }
    @GetMapping("/driving-rankings/coverage/{driverId}")
    public String manager(Authentication auth,@PathVariable Long driverId,
            @RequestParam(defaultValue="week") String period,Model model) {
        var org=organizations.requireOrganization(auth);access.requireManager(org.getId(),auth.getName());
        return page(org,driverId,period,true,model);
    }
    @GetMapping("/driver/rankings/coverage")
    public String driver(Authentication auth,@RequestParam(defaultValue="week") String period,Model model) {
        var driver=drivers.requireCurrentDriver(auth);drivers.requireActiveDriver(driver);
        return page(driver.getOrganization(),driver.getId(),period,false,model);
    }
    @PostMapping("/driving-rankings/coverage/{driverId}/sessions/{sessionId}/review")
    public String review(Authentication auth,@PathVariable Long driverId,@PathVariable Long sessionId,
            @RequestParam(defaultValue="week") String period,@RequestParam long revision,
            @RequestParam String action,@RequestParam String note,RedirectAttributes redirect) {
        var selected=parse(period);
        var org=organizations.requireOrganization(auth);
        try {
            details.review(org.getId(),driverId,auth.getName(),sessionId,revision,action,note);
            redirect.addFlashAttribute("successMessage","RESOLVED".equals(action)?"Coverage review resolved. Your note is saved.":
                    "Coverage flagged for review. Your note is saved; credited time has not changed.");
        } catch(IllegalArgumentException|IllegalStateException e) {redirect.addFlashAttribute("errorMessage",e.getMessage());}
        return "redirect:/driving-rankings/coverage/"+driverId+"?period="+selected.getKey()+"#coverage-reviews";
    }
    private String page(Organization org,Long driverId,String period,boolean manager,Model model) {
        model.addAttribute("report",details.build(org.getId(),driverId,parse(period),manager));
        model.addAttribute("periods",DriverRankingService.Period.values());
        model.addAttribute("organizationName",org.getDisplayName());model.addAttribute("managerView",manager);
        model.addAttribute("driverPage","rankings");
        model.addAttribute("rankingsUrl",manager?"/driving-rankings":"/driver/rankings");
        model.addAttribute("detailsUrl",manager?"/driving-rankings/coverage/"+driverId:"/driver/rankings/coverage");
        return "reporting/coverage-details";
    }
    private DriverRankingService.Period parse(String value) {
        try {return DriverRankingService.Period.parse(value);}
        catch(IllegalArgumentException e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());}
    }
}
