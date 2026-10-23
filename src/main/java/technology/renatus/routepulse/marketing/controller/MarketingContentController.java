package technology.renatus.routepulse.marketing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public, indexable RoutePulse product education pages.
 *
 * These routes intentionally stay separate from the authenticated application
 * so search engines and prospective customers can understand the product
 * without creating an account.
 */
@Controller
public class MarketingContentController {

    @GetMapping("/employee-shuttle-management-software")
    public String employeeShuttleManagementSoftware() {
        return "redirect:/#how-it-works";
    }

    @GetMapping("/workforce-transportation-planning")
    public String workforceTransportationPlanning() {
        return "redirect:/#how-it-works";
    }

    @GetMapping("/shuttle-driver-scheduling")
    public String shuttleDriverScheduling() {
        return "redirect:/#how-it-works";
    }

    @GetMapping("/shuttle-operations-verification")
    public String shuttleOperationsVerification() {
        return "redirect:/#how-it-works";
    }

    @GetMapping("/employee-shuttle-software-vs-spreadsheets")
    public String employeeShuttleSoftwareVsSpreadsheets() {
        return "redirect:/#how-it-works";
    }
}
