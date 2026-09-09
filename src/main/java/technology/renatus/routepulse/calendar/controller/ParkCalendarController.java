package technology.renatus.routepulse.calendar.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;

@Controller
public class ParkCalendarController {

    private final ParkOperatingDayService parkOperatingDayService;

    public ParkCalendarController(
            ParkOperatingDayService parkOperatingDayService) {
        this.parkOperatingDayService = parkOperatingDayService;
    }

    @PostMapping("/park-calendar/confirm-open")
    public String confirmParkOpen(
            Authentication authentication,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate operatingDate,

            RedirectAttributes redirectAttributes) {

        try {
            parkOperatingDayService
                    .confirmOpenForDate(
                            operatingDate,
                            authentication.getName()
                    );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Park opening confirmed. RoutePulse can now generate "
                            + "driver coverage from the uploaded schedule."
            );

        } catch (IllegalStateException exception) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        redirectAttributes.addAttribute(
                "date",
                operatingDate.toString()
        );

        return "redirect:/app#coverage";
    }
}
