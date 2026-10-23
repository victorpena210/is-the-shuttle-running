package technology.renatus.routepulse.reporting.workload.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driving.service.DrivingActionService;
import technology.renatus.routepulse.driver.service.DriverService;
import technology.renatus.routepulse.reporting.workload.service.ActualDrivingRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DrivingRecordController {

    private final ActualDrivingRecordService actualRecords;
    private final DriverService drivers;
    private final DrivingActionService drivingActionService;
    private final CurrentOrganizationService currentOrganizationService;

    public DrivingRecordController(
            ActualDrivingRecordService actualRecords,
            DriverService drivers,
            DrivingActionService drivingActionService,
            CurrentOrganizationService currentOrganizationService) {
        this.actualRecords = actualRecords;
        this.drivers = drivers;
        this.drivingActionService = drivingActionService;
        this.currentOrganizationService = currentOrganizationService;
    }

    @GetMapping({"/app", "/driving-records"})
    public String showDrivingRecords(
            Authentication authentication,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long driverId,
            Model model) {

        var organization = currentOrganizationService.requireOrganization(authentication);
        LocalDate to = toDate == null ? actualRecords.today() : toDate;
        LocalDate from = fromDate == null ? to.minusDays(30) : fromDate;
        model.addAttribute("organizationName", organization.getDisplayName());
        model.addAttribute("drivers", drivers.getAllDrivers(organization.getId()));
        model.addAttribute("fromDate", from);
        model.addAttribute("toDate", to);
        model.addAttribute("selectedDriverId", driverId);
        model.addAttribute("operationsZone", actualRecords.zoneName());
        try {
            model.addAttribute("report", actualRecords.build(organization.getId(), from, to, driverId));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("filterError", exception.getMessage());
        }
        return "manager/driving-records";
    }

    /** Old outcome forms must never change assignments or create make-up priority. */
    @PostMapping("/driving-records/{assignmentId}/outcome")
    public String reviewOutcome() {
        throw new ResponseStatusException(HttpStatus.GONE, "Shift assignments have been retired.");
    }

    @PostMapping("/driving-records/unscheduled/{sessionId}/end")
    public String endUnscheduledDriving(Authentication authentication, @PathVariable Long sessionId,
            RedirectAttributes redirectAttributes) {
        Long organizationId = currentOrganizationService.requireOrganizationId(authentication);
        try {
            drivingActionService.endUnscheduledDriving(organizationId, sessionId, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Driving ended. The end time and your account were recorded.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/driving-records";
    }

}
