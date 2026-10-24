package technology.renatus.routepulse.scheduling.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.scheduling.demand.DemandForecastService;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;

@Controller
public class ScheduleImportController {

    private final DemandForecastService forecasts;
    private final CurrentOrganizationService currentOrganizationService;

    public ScheduleImportController(
            DemandForecastService forecasts,
            CurrentOrganizationService currentOrganizationService) {
        this.forecasts = forecasts;
        this.currentOrganizationService = currentOrganizationService;
    }

    @PostMapping({"/upload", "/transportation-demand/upload"})
    public String uploadSchedule(
            Authentication authentication,

            @RequestParam("scheduleFile")
            MultipartFile scheduleFile,

            RedirectAttributes redirectAttributes) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {

            ScheduleImport scheduleImport =
                    forecasts
                            .upload(
                                    organizationId,
                                    authentication.getName(),
                                    scheduleFile
                            );

            redirectAttributes.addAttribute(
                    "date",
                    scheduleImport
                            .getFirstShiftDate()
                            .toString()
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    scheduleImport.isWeeklyDriverReport()
                            ? scheduleImport.getRowCount() + " work segments imported from the weekly report. Transportation demand uses each employee’s first start and final end; breaks are preserved for matched drivers. Counts cover only employees in this report."
                            : scheduleImport.getRowCount()
                            + " unique shifts processed. Transportation demand is ready in 15-minute intervals."
            );

        } catch (IllegalStateException | IllegalArgumentException exception) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/transportation-demand";
    }
}
