package technology.renatus.routepulse.calendar.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.service.ParkCalendarOverrideService;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class ParkCalendarController {

    private final ParkOperatingDayService parkOperatingDayService;
    private final ParkCalendarOverrideService parkCalendarOverrideService;
    private final CurrentOrganizationService currentOrganizationService;

    public ParkCalendarController(
            ParkOperatingDayService parkOperatingDayService,
            ParkCalendarOverrideService parkCalendarOverrideService,
            CurrentOrganizationService currentOrganizationService) {
        this.parkOperatingDayService = parkOperatingDayService;
        this.parkCalendarOverrideService = parkCalendarOverrideService;
        this.currentOrganizationService = currentOrganizationService;
    }

    @PostMapping("/park-calendar/confirm-open")
    public String confirmParkOpen(
            Authentication authentication,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate operatingDate,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {

        try {
            Organization organization =
                    currentOrganizationService.requireOrganization(authentication);
            ParkOperatingDay observedDay =
                    parkOperatingDayService.getOperatingDay(operatingDate);

            parkCalendarOverrideService.confirmParkOpen(
                    organization.getId(),
                    observedDay,
                    authentication.getName(),
                    reason);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Park operating status confirmed. The calendar override was recorded in the audit history, and RoutePulse can now generate coverage for this date.");

        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage());
        }

        redirectAttributes.addAttribute("date", operatingDate.toString());
        return "redirect:/app#coverage";
    }
}
