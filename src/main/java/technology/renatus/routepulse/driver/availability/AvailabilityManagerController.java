package technology.renatus.routepulse.driver.availability;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class AvailabilityManagerController {
    private final CurrentOrganizationService organizations;
    private final AvailabilityReviewService review;
    private final AvailabilityUpdateService updates;
    public AvailabilityManagerController(CurrentOrganizationService organizations, AvailabilityReviewService review, AvailabilityUpdateService updates) {
        this.organizations=organizations; this.review=review; this.updates=updates;
    }
    @GetMapping("/schedule-updates")
    public String page(Authentication auth, Model model) {
        var org=organizations.requireOrganization(auth);
        model.addAttribute("organizationName",org.getDisplayName());
        model.addAttribute("updates",review.updates(org.getId(),null));
        model.addAttribute("conflicts",review.conflicts(org.getId(),null));
        return "manager/schedule-updates";
    }
    @PostMapping("/schedule-updates/{id}/use-import")
    public String useImport(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        try { updates.useLatestImport(organizations.requireOrganizationId(auth),id,auth.getName());
            redirect.addFlashAttribute("successMessage","The latest imported hours are now in use. Review any affected assignments below.");
        } catch (IllegalArgumentException ex) { redirect.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/schedule-updates";
    }
    @PostMapping("/schedule-updates/assignments/{id}/keep")
    public String keep(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        try { review.keepCoveredAssignment(organizations.requireOrganizationId(auth),id,auth.getName());
            redirect.addFlashAttribute("successMessage","Coverage confirmed. This assignment is preserved as a manager decision.");
        } catch (IllegalArgumentException | IllegalStateException ex) { redirect.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/schedule-updates";
    }
}
