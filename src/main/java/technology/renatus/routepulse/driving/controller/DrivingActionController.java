package technology.renatus.routepulse.driving.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driving.service.DrivingActionService;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DrivingActionController {

    private final DrivingActionService drivingActionService;
    private final CurrentOrganizationService currentOrganizationService;

    public DrivingActionController(
            DrivingActionService drivingActionService,
            CurrentOrganizationService currentOrganizationService) {
        this.drivingActionService = drivingActionService;
        this.currentOrganizationService = currentOrganizationService;
    }

    @PostMapping(
            "/assignments/{assignmentId}/start"
    )
    public String startDriving(
            Authentication authentication,

            @PathVariable
            Long assignmentId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate assignmentDate,

            RedirectAttributes redirectAttributes) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {

            drivingActionService
                    .startDriving(
                            organizationId,
                            assignmentId,
                            authentication.getName()
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            "Driving started. RoutePulse recorded "
                                    + "the actual start time."
                    );

        } catch (
                IllegalArgumentException
                | IllegalStateException exception) {

            redirectAttributes
                    .addFlashAttribute(
                            "errorMessage",
                            exception.getMessage()
                    );
        }

        redirectAttributes.addAttribute(
                "date",
                assignmentDate.toString()
        );

        return "redirect:/app#coverage";
    }

    @PostMapping(
            "/assignments/{assignmentId}/end"
    )
    public String endDriving(
            Authentication authentication,

            @PathVariable
            Long assignmentId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate assignmentDate,

            RedirectAttributes redirectAttributes) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {

            drivingActionService
                    .endDriving(
                            organizationId,
                            assignmentId,
                            authentication.getName()
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            "Driving ended. RoutePulse recorded "
                                    + "the actual end time."
                    );

        } catch (
                IllegalArgumentException
                | IllegalStateException exception) {

            redirectAttributes
                    .addFlashAttribute(
                            "errorMessage",
                            exception.getMessage()
                    );
        }

        redirectAttributes.addAttribute(
                "date",
                assignmentDate.toString()
        );

        return "redirect:/app#coverage";
    }
}
