package technology.renatus.routepulse.operations.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.operations.service.ServiceConfirmationService;

@Controller
public class ServiceConfirmationController {
    private final CurrentOrganizationService organizations;
    private final ServiceConfirmationService service;
    private final ZoneId zone;

    public ServiceConfirmationController(CurrentOrganizationService organizations, ServiceConfirmationService service,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.organizations = organizations; this.service = service; this.zone = ZoneId.of(zone);
    }

    @PostMapping("/operations/confirm-running")
    public String confirm(Authentication auth, @RequestParam Long sessionId, RedirectAttributes flash) {
        return confirm(auth, sessionId, flash, false);
    }

    @PostMapping("/driver/confirm-running")
    public String driverConfirm(Authentication auth, @RequestParam Long sessionId, RedirectAttributes flash) {
        return confirm(auth, sessionId, flash, true);
    }

    private String confirm(Authentication auth, Long sessionId, RedirectAttributes flash, boolean driver) {
        try {
            service.confirm(organizations.requireOrganization(auth).getId(), sessionId, auth.getName(), driver);
            flash.addFlashAttribute("successMessage", "Service confirmed. Any active manager status override still applies.");
        } catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("errorMessage", ex.getMessage()); }
        return driver ? "redirect:/driver" : "redirect:/app#live-status";
    }

    @PostMapping("/operations/close-session")
    public String close(Authentication auth, @RequestParam Long sessionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endedAt, RedirectAttributes flash) {
        try {
            // Reject ambiguous/nonexistent wall times instead of silently choosing a DST offset.
            var offsets = zone.getRules().getValidOffsets(endedAt);
            if (offsets.size() != 1) throw new IllegalArgumentException("This local time is ambiguous or does not exist because of a clock change. Review the driving record before closing it.");
            service.close(organizations.requireOrganization(auth).getId(), sessionId, endedAt.toInstant(offsets.get(0)), auth.getName());
            flash.addFlashAttribute("successMessage", "Session closed at the actual end time. Any manager status override still applies.");
        } catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("errorMessage", ex.getMessage()); }
        return "redirect:/app#live-status";
    }

    @PostMapping("/operations/rider-information")
    public String riderInformation(Authentication auth, @RequestParam(required = false) String pickupInstructions,
            @RequestParam(required = false) String helpContact, RedirectAttributes flash) {
        try {
            service.updateRiderInformation(organizations.requireOrganization(auth).getId(), pickupInstructions, helpContact, auth.getName());
            flash.addFlashAttribute("successMessage", "Public pickup and help information updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("errorMessage", ex.getMessage()); }
        return "redirect:/app#live-status";
    }
}
