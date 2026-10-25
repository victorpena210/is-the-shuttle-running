package technology.renatus.routepulse.reporting.rankings;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleIdentityService;

@Controller
public class DriverRankingController {
    private final DriverRankingService rankings;
    private final CurrentOrganizationService organizations;
    private final DriverPortalAccessService drivers;
    private final DriverScheduleIdentityService access;
    public DriverRankingController(DriverRankingService rankings,CurrentOrganizationService organizations,
            DriverPortalAccessService drivers,DriverScheduleIdentityService access) {
        this.rankings=rankings;this.organizations=organizations;this.drivers=drivers;this.access=access;
    }
    @GetMapping("/driving-rankings")
    public String manager(Authentication auth,@RequestParam(defaultValue="week") String period,Model model) {
        var org=organizations.requireOrganization(auth);access.requireManager(org.getId(),auth.getName());
        return page(org,period,true,null,model);
    }
    @GetMapping("/driver/rankings")
    public String driver(Authentication auth,@RequestParam(defaultValue="week") String period,Model model) {
        var driver=drivers.requireCurrentDriver(auth);drivers.requireActiveDriver(driver);
        return page(driver.getOrganization(),period,false,driver.getId(),model);
    }
    private String page(Organization org,String period,boolean manager,Long driverId,Model model) {
        DriverRankingService.Period selected;
        try {selected=DriverRankingService.Period.parse(period);}
        catch(IllegalArgumentException error) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,error.getMessage());}
        model.addAttribute("report",rankings.build(org.getId(),selected));
        model.addAttribute("periods",DriverRankingService.Period.values());
        model.addAttribute("organizationName",org.getDisplayName());model.addAttribute("managerView",manager);
        model.addAttribute("currentDriverId",driverId);model.addAttribute("driverPage","rankings");
        model.addAttribute("rankingsUrl",manager?"/driving-rankings":"/driver/rankings");
        return "reporting/driver-rankings";
    }
}
