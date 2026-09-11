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
        return "public/employee-shuttle-management-software";
    }

    @GetMapping("/workforce-transportation-planning")
    public String workforceTransportationPlanning() {
        return "public/workforce-transportation-planning";
    }

    @GetMapping("/shuttle-driver-scheduling")
    public String shuttleDriverScheduling() {
        return "public/shuttle-driver-scheduling";
    }

    @GetMapping("/shuttle-operations-verification")
    public String shuttleOperationsVerification() {
        return "public/shuttle-operations-verification";
    }

    @GetMapping("/employee-shuttle-software-vs-spreadsheets")
    public String employeeShuttleSoftwareVsSpreadsheets() {
        return "public/employee-shuttle-software-vs-spreadsheets";
    }
}
