package technology.renatus.routepulse.reporting.history;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.driver.service.DriverService;

@Controller
public class ServiceHistoryController {
    private final CurrentOrganizationService organizations;
    private final DriverService drivers;
    private final ServiceHistoryService history;
    public ServiceHistoryController(CurrentOrganizationService organizations, DriverService drivers, ServiceHistoryService history) {
        this.organizations = organizations; this.drivers = drivers; this.history = history;
    }
    @GetMapping("/service-history")
    public String review(Authentication authentication,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String range, @RequestParam(required = false) String period,
            @RequestParam(required = false) Long driverId, @RequestParam(required = false) String driverRole,
            @RequestParam(required = false) String result, Model model) {
        var org = organizations.requireOrganization(authentication);
        var today = history.today();
        ServiceReviewFilters filters;
        var report = new ServiceReviewCalculator.Report(List.of(), 0, 0, 0, 0);
        try {
            filters = ServiceReviewFilters.resolve(from, to, range, period, driverId, driverRole, result, today);
            report = history.build(org.getId(), filters);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("filterError", exception.getMessage());
            filters = ServiceReviewFilters.resolve(null, null, null, null, null, null, null, today);
        }
        model.addAttribute("organizationName", org.getDisplayName());
        model.addAttribute("drivers", drivers.getAllDrivers(org.getId()));
        model.addAttribute("selectedView", "review");
        model.addAttribute("fromDate", filters.from());
        model.addAttribute("toDate", filters.to());
        model.addAttribute("selectedDriverId", filters.driverId());
        model.addAttribute("filters", filters);
        model.addAttribute("report", report);
        model.addAttribute("operationsZone", history.zoneName());
        return "manager/service-history";
    }
}
