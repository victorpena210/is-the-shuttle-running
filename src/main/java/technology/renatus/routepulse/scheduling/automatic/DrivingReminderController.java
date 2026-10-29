package technology.renatus.routepulse.scheduling.automatic;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DrivingReminderController {
    private final DrivingReminderSettings settings;
    private final CurrentOrganizationService current;
    public DrivingReminderController(DrivingReminderSettings settings, CurrentOrganizationService current) {
        this.settings = settings; this.current = current;
    }
    @PostMapping("/drivers/{id}/driving-reminder-email")
    public String update(Authentication auth, @PathVariable Long id, @RequestParam String email, RedirectAttributes redirect) {
        try {
            settings.update(current.requireOrganizationId(auth), id, email, auth.getName());
            redirect.addFlashAttribute("successMessage", email.isBlank()
                    ? "Driving reminder emails turned off for this driver."
                    : "Driving reminder email saved. Reminders send five minutes before clock-in when driving is assigned.");
        } catch (IllegalArgumentException error) {
            redirect.addFlashAttribute("errorMessage", error.getMessage());
        }
        return "redirect:/drivers";
    }
    /** One email link works for both managers and drivers, including after signing in. */
    @GetMapping("/my-driving-assignments")
    public String assignments(Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            RedirectAttributes redirect) {
        if (date != null) redirect.addAttribute("date", date.toString());
        boolean manager = auth.getAuthorities().stream().anyMatch(a ->
                java.util.Set.of("ROLE_OWNER", "ROLE_ADMIN", "ROLE_DISPATCHER").contains(a.getAuthority()));
        return "redirect:" + (manager ? "/driving-schedule" : "/driver/driving-schedule");
    }
}
