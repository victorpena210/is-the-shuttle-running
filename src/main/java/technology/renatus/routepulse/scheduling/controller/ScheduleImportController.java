package technology.renatus.routepulse.scheduling.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.scheduling.importing.CsvScheduleProvider;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;

@Controller
public class ScheduleImportController {

    private final CsvScheduleProvider csvScheduleProvider;
    private final CurrentOrganizationService currentOrganizationService;

    public ScheduleImportController(
            CsvScheduleProvider csvScheduleProvider,
            CurrentOrganizationService currentOrganizationService) {
        this.csvScheduleProvider = csvScheduleProvider;
        this.currentOrganizationService = currentOrganizationService;
    }

    @PostMapping("/upload")
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
                    csvScheduleProvider
                            .loadSchedule(
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
                    scheduleImport.getRowCount()
                            + " shifts were successfully uploaded. "
                            + "Import "
                            + scheduleImport.getShortVersionId()
                            + "."
            );

        } catch (IllegalStateException exception) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/app#schedules";
    }
}
