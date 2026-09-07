package technology.renatus.routepulse.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import technology.renatus.routepulse.model.AssignmentOutcome;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.service.CurrentOrganizationService;
import technology.renatus.routepulse.service.DriverService;
import technology.renatus.routepulse.service.DrivingRecordService;
import technology.renatus.routepulse.service.DrivingRecordService.DriverWorkloadRow;
import technology.renatus.routepulse.service.DrivingRecordService.WorkloadSummary;

@Controller
public class DrivingRecordController {

    private final DrivingRecordService drivingRecordService;
    private final DriverService driverService;
    private final CurrentOrganizationService currentOrganizationService;

    public DrivingRecordController(
            DrivingRecordService drivingRecordService,
            DriverService driverService,
            CurrentOrganizationService currentOrganizationService) {

        this.drivingRecordService =
                drivingRecordService;

        this.driverService =
                driverService;

        this.currentOrganizationService =
                currentOrganizationService;
    }

    @GetMapping("/driving-records")
    public String showDrivingRecords(
            Authentication authentication,

            @RequestParam(
                    name = "from",
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate fromDate,

            @RequestParam(
                    name = "to",
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate toDate,

            @RequestParam(
                    required = false
            )
            Long driverId,

            @RequestParam(
                    name = "view",
                    required = false
            )
            String view,

            Model model) {

        Organization organization =
                currentOrganizationService
                        .requireOrganization(
                                authentication
                        );

        Long organizationId =
                organization.getId();

        LocalDate currentOperationsDate =
                drivingRecordService
                        .getCurrentOperationsDate();

        /*
         * Default to the previous 30 days.
         */
        if (fromDate == null
                && toDate == null) {

            toDate =
                    currentOperationsDate;

            fromDate =
                    currentOperationsDate
                            .minusDays(30);

        } else if (fromDate == null) {

            fromDate = toDate;

        } else if (toDate == null) {

            toDate = fromDate;
        }

        /*
         * Driver summary is the new default page.
         */
        String selectedView =
                "records".equalsIgnoreCase(view)
                        ? "records"
                        : "summary";

        List<DriverAssignment> records =
                List.of();

        WorkloadSummary workloadSummary =
                WorkloadSummary.empty(
                        fromDate,
                        toDate
                );

        String filterError = null;

        try {

            records =
                    drivingRecordService
                            .getDrivingRecords(
                                    organizationId,
                                    fromDate,
                                    toDate,
                                    driverId
                            );

            workloadSummary =
                    drivingRecordService
                            .getWorkloadSummary(
                                    organizationId,
                                    fromDate,
                                    toDate,
                                    driverId
                            );

        } catch (IllegalArgumentException exception) {

            filterError =
                    exception.getMessage();
        }

        /*
         * When a single driver is selected,
         * the workload report contains one row.
         */
        DriverWorkloadRow selectedDriverSummary =
                driverId != null
                        && !workloadSummary
                                .getDrivers()
                                .isEmpty()
                        ? workloadSummary
                                .getDrivers()
                                .get(0)
                        : null;

        boolean isOwner =
                authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(
                                authority ->
                                        authority
                                                .getAuthority()
                                                .equals(
                                                        "ROLE_OWNER"
                                                )
                        );

        model.addAttribute(
                "organizationName",
                organization.getDisplayName()
        );

        model.addAttribute(
                "isOwner",
                isOwner
        );

        model.addAttribute(
                "drivers",
                driverService
                        .getAllDrivers(
                                organizationId
                        )
        );

        model.addAttribute(
                "records",
                records
        );

        model.addAttribute(
                "workloadSummary",
                workloadSummary
        );

        model.addAttribute(
                "selectedDriverSummary",
                selectedDriverSummary
        );

        model.addAttribute(
                "makeupStatus",
                drivingRecordService
                        .getMakeupStatusMap(
                                organizationId,
                                records
                        )
        );

        model.addAttribute(
                "outcomes",
                AssignmentOutcome.values()
        );

        model.addAttribute(
                "fromDate",
                fromDate
        );

        model.addAttribute(
                "toDate",
                toDate
        );

        model.addAttribute(
                "selectedDriverId",
                driverId
        );

        model.addAttribute(
                "selectedView",
                selectedView
        );

        model.addAttribute(
                "filterError",
                filterError
        );

        return "driving-records";
    }

    @PostMapping(
            "/driving-records/{assignmentId}/outcome"
    )
    public String reviewOutcome(
            Authentication authentication,

            @PathVariable
            Long assignmentId,

            @RequestParam
            AssignmentOutcome outcome,

            @RequestParam(
                    defaultValue = "false"
            )
            boolean requireMakeup,

            @RequestParam(
                    required = false
            )
            String reason,

            @RequestParam(
                    name = "from",
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate fromDate,

            @RequestParam(
                    name = "to",
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate toDate,

            @RequestParam(
                    required = false
            )
            Long driverId,

            RedirectAttributes redirectAttributes) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {

            drivingRecordService
                    .reviewAssignmentOutcome(
                            organizationId,
                            assignmentId,
                            outcome,
                            requireMakeup,
                            reason,
                            authentication.getName()
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            requireMakeup
                                    ? "Outcome saved. The scheduled driver now has future make-up priority."
                                    : "Outcome saved. No make-up priority is being applied."
                    );

        } catch (
                IllegalArgumentException
                | IllegalStateException exception
        ) {

            redirectAttributes
                    .addFlashAttribute(
                            "errorMessage",
                            exception.getMessage()
                    );
        }

        if (fromDate != null) {

            redirectAttributes
                    .addAttribute(
                            "from",
                            fromDate
                    );
        }

        if (toDate != null) {

            redirectAttributes
                    .addAttribute(
                            "to",
                            toDate
                    );
        }

        if (driverId != null) {

            redirectAttributes
                    .addAttribute(
                            "driverId",
                            driverId
                    );
        }

        /*
         * Management review is located on the
         * Individual records tab.
         */
        redirectAttributes
                .addAttribute(
                        "view",
                        "records"
                );

        return "redirect:/driving-records#record-"
                + assignmentId;
    }
}