package technology.renatus.routepulse.wardrobe;

import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.assignments.service.DriverAssignmentService;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class WardrobeHoursController {
    private final DriverAssignmentService assignments;
    private final CurrentOrganizationService organizations;
    public WardrobeHoursController(DriverAssignmentService assignments, CurrentOrganizationService organizations) {
        this.assignments = assignments; this.organizations = organizations;
    }
    @PostMapping("/wardrobe-hours/season")
    public String saveSeason(Authentication authentication, @RequestParam String label,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime openingTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime closingTime,
            @RequestParam(required = false) Integer dayOfWeek, RedirectAttributes redirect) {
        return save(authentication, WardrobeHoursRule.Scope.SEASON, label, from, to, openingTime, closingTime, dayOfWeek, selectedDate, redirect);
    }
    @PostMapping("/wardrobe-hours/day")
    public String saveDay(Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime openingTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime closingTime,
            RedirectAttributes redirect) {
        return save(authentication, WardrobeHoursRule.Scope.DAY, "Day override", selectedDate, selectedDate,
                openingTime, closingTime, null, selectedDate, redirect);
    }
    @PostMapping("/wardrobe-hours/day/reset")
    public String resetDay(Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedDate,
            RedirectAttributes redirect) {
        return save(authentication, WardrobeHoursRule.Scope.DAY, "Use seasonal hours", selectedDate, selectedDate,
                null, null, null, selectedDate, redirect);
    }
    private String save(Authentication authentication, WardrobeHoursRule.Scope scope, String label,
            LocalDate from, LocalDate to, LocalTime opening, LocalTime closing, Integer dayOfWeek, LocalDate selectedDate, RedirectAttributes redirect) {
        try {
            Long organizationId = organizations.requireOrganizationId(authentication);
            assignments.updateWardrobeHours(organizationId, scope, label, from, to, opening, closing, dayOfWeek, authentication.getName());
            redirect.addFlashAttribute("successMessage", "Wardrobe hours saved and upcoming automatic assignments recalculated. Review any preserved assignments flagged below.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        redirect.addAttribute("date", selectedDate.toString());
        return "redirect:/app#coverage";
    }
}
