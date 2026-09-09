package technology.renatus.routepulse.assignments.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.service.DriverAssignmentService;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DriverAssignmentController {

    private final DriverAssignmentService driverAssignmentService;
    private final CurrentOrganizationService currentOrganizationService;

    public DriverAssignmentController(
            DriverAssignmentService driverAssignmentService,
            CurrentOrganizationService currentOrganizationService) {
        this.driverAssignmentService = driverAssignmentService;
        this.currentOrganizationService = currentOrganizationService;
    }

    @PostMapping("/assignments/manual")
    public String createManualAssignment(
            Authentication authentication,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate assignmentDate,

            @RequestParam
            ShuttlePeriod period,

            @RequestParam
            Long driverId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.TIME
            )
            LocalTime scheduledStartTime,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.TIME
            )
            LocalTime scheduledEndTime,

            @RequestParam(required = false)
            String changeReason,

            RedirectAttributes redirectAttributes) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {

            driverAssignmentService
                    .createManualAssignment(
                            organizationId,
                            assignmentDate,
                            period,
                            driverId,
                            scheduledStartTime,
                            scheduledEndTime,
                            changeReason
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            "Manual shuttle assignment created."
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
            "/assignments/{assignmentId}/driver"
    )
    public String updateAssignedDriver(
            Authentication authentication,

            @PathVariable
            Long assignmentId,

            @RequestParam
            Long finalDriverId,

            @RequestParam(required = false)
            String changeReason,

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

            driverAssignmentService
                    .changeAssignedDriver(
                            organizationId,
                            assignmentId,
                            finalDriverId,
                            changeReason,
                            authentication.getName()
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            "Driver assignment updated and "
                                    + "the change was recorded."
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
            "/assignments/{assignmentId}/schedule"
    )
    public String updateAssignmentSchedule(
            Authentication authentication,

            @PathVariable
            Long assignmentId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.TIME
            )
            LocalTime scheduledStartTime,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.TIME
            )
            LocalTime scheduledEndTime,

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

            driverAssignmentService
                    .updateScheduledTimes(
                            organizationId,
                            assignmentId,
                            scheduledStartTime,
                            scheduledEndTime
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            "Driving assignment "
                                    + "schedule updated."
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

    @PostMapping("/assignments/confirm-schedule")
    public String confirmAssignmentsAgainstLatestImport(
            Authentication authentication,
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
            driverAssignmentService
                    .confirmAssignmentsAgainstLatestImport(
                            organizationId,
                            assignmentDate,
                            authentication.getName()
                    );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Driver coverage was confirmed against the latest schedule import."
            );

        } catch (IllegalArgumentException
                | IllegalStateException exception) {

            redirectAttributes.addFlashAttribute(
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
    @PostMapping("/assignments/{assignmentId}/accept-schedule-recommendation")
    public String acceptScheduleRecommendation(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @RequestParam Long recommendedDriverId,
            @RequestParam Long expectedScheduleImportId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate assignmentDate,
            RedirectAttributes redirectAttributes) {

        Long organizationId = currentOrganizationService
                .requireOrganizationId(authentication);

        try {
            driverAssignmentService.acceptScheduleRecommendation(
                    organizationId,
                    assignmentId,
                    recommendedDriverId,
                    expectedScheduleImportId,
                    authentication.getName()
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "RoutePulse recommendation accepted and coverage was reconciled against the latest schedule."
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        redirectAttributes.addAttribute("date", assignmentDate.toString());
        return "redirect:/app#coverage";
    }

    @PostMapping("/assignments/{assignmentId}/keep-current-after-schedule-change")
    public String keepCurrentAssignmentAfterScheduleChange(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @RequestParam Long expectedScheduleImportId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate assignmentDate,
            RedirectAttributes redirectAttributes) {

        Long organizationId = currentOrganizationService
                .requireOrganizationId(authentication);

        try {
            driverAssignmentService.keepCurrentAssignmentAfterScheduleChange(
                    organizationId,
                    assignmentId,
                    expectedScheduleImportId,
                    authentication.getName()
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Current assignment kept and confirmed against the latest schedule."
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        redirectAttributes.addAttribute("date", assignmentDate.toString());
        return "redirect:/app#coverage";
    }

}
