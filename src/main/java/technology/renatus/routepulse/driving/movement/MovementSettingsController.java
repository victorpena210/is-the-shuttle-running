package technology.renatus.routepulse.driving.movement;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driving.domain.DrivingSessionEndReason;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
@RequestMapping("/operations/movement")
public class MovementSettingsController {
    private final MovementMonitoringService monitoring;
    private final CurrentOrganizationService current;
    private final DrivingSessionRepository repository;
    public MovementSettingsController(MovementMonitoringService monitoring,CurrentOrganizationService current,DrivingSessionRepository repository) {
        this.monitoring=monitoring; this.current=current; this.repository=repository;
    }
    @GetMapping public String settings(Authentication auth,Model model) {
        var organization=current.requireOrganization(auth);
        model.addAttribute("movementRoute",organization.getMovementRoute());
        model.addAttribute("reviews",repository.findTop50ByOrganization_IdAndEndReasonAndInactivityReviewedAtIsNullOrderByEndedAtDesc(
                organization.getId(),DrivingSessionEndReason.INACTIVITY_TIMEOUT));
        return "manager/movement";
    }
    @PostMapping("/route") public String route(Authentication auth,@RequestParam(defaultValue="") String route,RedirectAttributes flash) {
        try { monitoring.saveRoute(auth,route); flash.addFlashAttribute("successMessage","Shuttle route saved."); }
        catch (IllegalArgumentException|IllegalStateException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/operations/movement";
    }
    @PostMapping("/review") public String review(Authentication auth,@RequestParam Long sessionId,@RequestParam String note,RedirectAttributes flash) {
        try { monitoring.review(auth,sessionId,note); flash.addFlashAttribute("successMessage","Review recorded. The original timeout remains in the driving history."); }
        catch (IllegalArgumentException|IllegalStateException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/operations/movement";
    }
}
