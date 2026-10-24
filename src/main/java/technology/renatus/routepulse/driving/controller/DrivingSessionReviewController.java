package technology.renatus.routepulse.driving.controller;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driving.service.DrivingSessionReviewService;
import technology.renatus.routepulse.driving.service.DrivingSessionReviewService.ReviewForm;

@Controller
@RequestMapping("/driving-records")
public class DrivingSessionReviewController {
    private final DrivingSessionReviewService reviews;
    public DrivingSessionReviewController(DrivingSessionReviewService reviews) { this.reviews = reviews; }

    @GetMapping("/reviews")
    public String pending(Authentication auth, Model model) {
        model.addAttribute("pendingReviewCount", reviews.pendingCount(auth));
        model.addAttribute("pendingReviews", reviews.pending(auth));
        return "manager/session-reviews";
    }

    @GetMapping("/reviews/count")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> count(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("count", reviews.pendingCount(auth)));
    }

    @GetMapping("/sessions/{id}/review")
    public String show(Authentication auth, @PathVariable Long id, Model model) {
        var page = page(auth, id);
        model.addAttribute("review", page);
        model.addAttribute("form", page.form());
        return "manager/session-review";
    }

    @PostMapping("/sessions/{id}/review")
    public String save(Authentication auth, @PathVariable Long id, @ModelAttribute("form") ReviewForm form,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        try {
            if (binding.hasErrors()) throw new IllegalArgumentException("Some fields are invalid. Reload the page and try again.");
            reviews.review(auth, id, form);
            redirect.addFlashAttribute("successMessage", "APPROVED".equals(form.action())
                    ? "Session approved. The alert has been cleared."
                    : "Corrected times saved. Totals are updated and the review is recorded.");
            return "redirect:/driving-records/sessions/" + id + "/review";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute("review", page(auth, id));
            return "manager/session-review";
        }
    }

    private DrivingSessionReviewService.ReviewPage page(Authentication auth, Long id) {
        try { return reviews.page(auth, id); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage()); }
    }
}
