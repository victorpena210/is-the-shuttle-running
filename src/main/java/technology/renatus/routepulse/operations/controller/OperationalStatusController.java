package technology.renatus.routepulse.operations.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.operations.domain.OperationalStatusOverride;
import technology.renatus.routepulse.operations.service.OperationalStatusService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class OperationalStatusController {

    private final CurrentOrganizationService currentOrganizationService;
    private final OperationalStatusService operationalStatusService;

    public OperationalStatusController(
            CurrentOrganizationService currentOrganizationService,
            OperationalStatusService operationalStatusService) {
        this.currentOrganizationService = currentOrganizationService;
        this.operationalStatusService = operationalStatusService;
    }

    @PostMapping("/operations/status")
    public String updateStatus(
            Authentication authentication,
            @RequestParam OperationalStatusOverride status,
            @RequestParam(required = false) String message,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            RedirectAttributes redirectAttributes) {

        try {
            Organization organization = currentOrganizationService.requireOrganization(authentication);
            operationalStatusService.update(
                    organization.getId(),
                    status,
                    message,
                    authentication.getName());

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    status == OperationalStatusOverride.AUTOMATIC
                            ? "Live shuttle status returned to automatic mode."
                            : "Live shuttle status updated for riders.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        if (date != null) {
            redirectAttributes.addAttribute("date", date.toString());
        }
        return "redirect:/app#live-status";
    }
}
