package technology.renatus.routepulse.driving.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import technology.renatus.routepulse.driving.service.RiderStatusService;
import technology.renatus.routepulse.driving.service.RiderStatusService.RiderStatusNotFoundException;

/** Read-only public status. Passenger confirmation is no longer part of the app. */
@Controller
public class RiderStatusController {
    private final RiderStatusService riderStatusService;

    public RiderStatusController(RiderStatusService riderStatusService) {
        this.riderStatusService = riderStatusService;
    }

    @GetMapping("/ride/{organizationSlug}")
    public String showRiderStatus(@PathVariable String organizationSlug,
            HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        try {
            var status = riderStatusService.getStatus(organizationSlug);
            model.addAttribute("organizationName", status.organizationName());
            model.addAttribute("running", status.running());
            model.addAttribute("driverName", status.driverName());
            return "public/rider-status";
        } catch (RiderStatusNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    /** Reject submissions from previously loaded pages without recording a ride. */
    @PostMapping("/ride/{organizationSlug}/confirm")
    public ResponseEntity<Void> confirmPassengerRide() {
        return retiredConfirmation();
    }

    @GetMapping("/ride/{organizationSlug}/passenger-qr.png")
    public ResponseEntity<Void> getPassengerQrImage() {
        return retiredConfirmation();
    }

    private ResponseEntity<Void> retiredConfirmation() {
        return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore()).build();
    }
}
