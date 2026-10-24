package technology.renatus.routepulse.scheduling.controller;

import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleIdentityService;
import technology.renatus.routepulse.wardrobe.*;

@Controller
public class DriverSuggestionSettingsController {
    private final CurrentOrganizationService current;
    private final DriverScheduleIdentityService identities;
    private final WardrobeHoursService wardrobe;
    public DriverSuggestionSettingsController(CurrentOrganizationService current, DriverScheduleIdentityService identities, WardrobeHoursService wardrobe) {
        this.current = current; this.identities = identities; this.wardrobe = wardrobe;
    }
    @PostMapping("/drivers/{id}/employee-id")
    public String employeeId(Authentication auth, @PathVariable Long id, @RequestParam String employeeId, RedirectAttributes redirect) {
        try {
            identities.update(current.requireOrganizationId(auth), id, employeeId, auth.getName());
            redirect.addFlashAttribute("successMessage", "Employee ID saved. Re-upload the schedule to refresh this driver's availability.");
        } catch (IllegalArgumentException | IllegalStateException exception) { redirect.addFlashAttribute("errorMessage", exception.getMessage()); }
        return "redirect:/drivers";
    }
    @PostMapping("/transportation-demand/wardrobe-hours")
    public String hours(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime openingTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime closingTime, RedirectAttributes redirect) {
        try {
            wardrobe.save(current.requireOrganizationId(auth), WardrobeHoursRule.Scope.DAY, "Day override", date, date,
                    openingTime, closingTime, null, auth.getName());
            redirect.addFlashAttribute("successMessage", "Wardrobe hours saved. Driver suggestions now use these hours.");
        } catch (IllegalArgumentException | IllegalStateException exception) { redirect.addFlashAttribute("errorMessage", exception.getMessage()); }
        redirect.addAttribute("date", date);
        return "redirect:/transportation-demand#driver-suggestions";
    }
}
