package technology.renatus.routepulse.controller;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import technology.renatus.routepulse.service.RiderStatusService;
import technology.renatus.routepulse.service.RiderStatusService.RiderStatus;
import technology.renatus.routepulse.service.RiderStatusService.RiderStatusNotFoundException;

@Controller
public class RiderStatusController {

    private final RiderStatusService riderStatusService;

    public RiderStatusController(
            RiderStatusService riderStatusService) {

        this.riderStatusService = riderStatusService;
    }

    @GetMapping("/ride/{organizationSlug}")
    public String showRiderStatus(
            @PathVariable String organizationSlug,
            HttpServletResponse response,
            Model model) {

        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate, max-age=0"
        );
        response.setHeader("Pragma", "no-cache");

        try {
            RiderStatus status = riderStatusService
                    .getStatus(organizationSlug);

            model.addAttribute("status", status);

            return "rider-status";

        } catch (RiderStatusNotFoundException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage()
            );
        }
    }
}
