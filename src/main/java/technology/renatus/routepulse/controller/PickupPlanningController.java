package technology.renatus.routepulse.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.DriverSchedule;
import technology.renatus.routepulse.model.DriverScheduleResult;
import technology.renatus.routepulse.model.ParkOperatingDay;
import technology.renatus.routepulse.model.ParkOperatingStatus;
import technology.renatus.routepulse.model.PickupWindow;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.ScheduleImport;
import technology.renatus.routepulse.model.ShuttlePeriod;
import technology.renatus.routepulse.provider.CsvScheduleProvider;
import technology.renatus.routepulse.service.CurrentOrganizationService;
import technology.renatus.routepulse.service.DriverAssignmentService;
import technology.renatus.routepulse.service.DriverScheduleService;
import technology.renatus.routepulse.service.DriverService;
import technology.renatus.routepulse.service.ParkOperatingDayService;
import technology.renatus.routepulse.service.PickupPlanningService;

@Controller
public class PickupPlanningController {

    private final PickupPlanningService
            pickupPlanningService;

    private final CsvScheduleProvider
            csvScheduleProvider;

    private final DriverScheduleService
            driverScheduleService;

    private final ParkOperatingDayService
            parkOperatingDayService;

    private final DriverAssignmentService
            driverAssignmentService;

    private final DriverService driverService;

    private final CurrentOrganizationService
            currentOrganizationService;

    public PickupPlanningController(
            PickupPlanningService pickupPlanningService,
            CsvScheduleProvider csvScheduleProvider,
            DriverScheduleService driverScheduleService,
            ParkOperatingDayService parkOperatingDayService,
            DriverAssignmentService driverAssignmentService,
            DriverService driverService,
            CurrentOrganizationService
                    currentOrganizationService) {

        this.pickupPlanningService =
                pickupPlanningService;

        this.csvScheduleProvider =
                csvScheduleProvider;

        this.driverScheduleService =
                driverScheduleService;

        this.parkOperatingDayService =
                parkOperatingDayService;

        this.driverAssignmentService =
                driverAssignmentService;

        this.driverService =
                driverService;

        this.currentOrganizationService =
                currentOrganizationService;
    }

    @GetMapping("/app")
    public String showDashboard(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate date,
            @RequestParam(defaultValue = "false")
            boolean allowUnverifiedCalendar,
            Model model) {

        Organization organization =
                currentOrganizationService
                        .requireOrganization(
                                authentication
                        );

        Long organizationId = organization.getId();

        model.addAttribute(
                "organizationName",
                organization.getDisplayName()
        );

        boolean isOwner =
                authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority
                                        .getAuthority()
                                        .equals("ROLE_OWNER")
                        );

        model.addAttribute(
                "isOwner",
                isOwner
        );

        List<LocalDate> availableDates =
                csvScheduleProvider
                        .getAvailableDates(
                                organizationId
                        );

        LocalDate selectedDate = date;

        /*
         * If no date was explicitly selected,
         * default to the first date belonging
         * to the newest CSV import.
         */
        if (selectedDate == null
                && !availableDates.isEmpty()) {

            selectedDate =
                    csvScheduleProvider
                            .getFirstDateOfLatestImport(
                                    organizationId
                            );
        }

        /*
         * Before a CSV is uploaded,
         * default the picker to today's date.
         */
        if (selectedDate == null) {
            selectedDate =
                    driverAssignmentService
                            .getCurrentOperationsDate();
        }

        boolean selectedDateAvailable =
                !availableDates.isEmpty()
                        && availableDates.contains(
                                selectedDate
                        );


        List<PickupWindow> pickupWindows =
                List.of();

        List<DriverSchedule> driverSchedules =
                List.of();

        List<Driver> driversWithoutSchedules =
                List.of();

        List<DriverAssignment> driverAssignments =
                List.of();

        List<Driver> activeDrivers =
                driverService
                        .getActiveDrivers(
                                organizationId
                        );

        String assignmentMessage = null;

        boolean assignmentReviewRequired =
                false;

        ScheduleImport latestScheduleImport =
                null;

        ParkOperatingDay operatingDay =
                null;

        String calendarMessage =
                null;

        boolean calendarOverrideActive =
                false;

        /*
         * Load the selected uploaded date even when the Six Flags
         * operating calendar cannot be verified. This lets managers
         * inspect exactly what RoutePulse imported before deciding
         * whether to continue with shuttle planning.
         */
        if (selectedDateAvailable) {


            latestScheduleImport =
                    csvScheduleProvider
                            .getLatestImportForDate(
                                    organizationId,
                                    selectedDate
                            )
                            .orElse(null);

            /*
             * TEMPORARILY DISABLED FOR ROUTEPULSE DEMO
             *
             * This is the live Six Flags operating-calendar check.
             * Leave this code here so it can be re-enabled later.
             *
             * operatingDay =
             *         parkOperatingDayService
             *                 .getOperatingDay(
             *                         selectedDate
             *                 );
             */

            /*
             * Temporary fallback while the live calendar check is disabled:
             * treat any date that exists in the uploaded schedule as OPEN.
             */
            operatingDay =
                    new ParkOperatingDay(
                            selectedDate,
                            ParkOperatingStatus.OPEN,
                            null,
                            null,
                            false
                    );

            if (operatingDay.status()
                    == ParkOperatingStatus.CLOSED) {

                calendarMessage =
                        "Park closed — shuttle not scheduled";

            } else if (operatingDay.status()
                    == ParkOperatingStatus.UNKNOWN) {

                if (allowUnverifiedCalendar) {
                    calendarOverrideActive = true;

                    calendarMessage =
                            "Calendar unverified — manager "
                                    + "continued with uploaded schedule";

                } else {
                    calendarMessage =
                            "Calendar could not be verified "
                                    + "— review required";
                }
            }

            if (operatingDay.status()
                    == ParkOperatingStatus.OPEN
                    || calendarOverrideActive) {

                if (operatingDay.status()
                        == ParkOperatingStatus.OPEN) {

                    calendarMessage =
                            operatingDay.buyout()
                                    ? "Open — Private Buyout"
                                    : "Open — Public Operating Day";
                }

                pickupWindows =
                        pickupPlanningService
                                .getPickupWindowsForDate(
                                        organizationId,
                                        selectedDate
                                );

                DriverScheduleResult result =
                        driverScheduleService
                                .getDriverSchedulesForDate(
                                        organizationId,
                                        selectedDate
                                );

                driverSchedules =
                        result.scheduledDrivers();

                driversWithoutSchedules =
                        result.driversWithoutSchedules();

                driverAssignments =
                        driverAssignmentService
                                .getOrCreateAssignments(
                                        organizationId,
                                        selectedDate
                                );

                Long latestScheduleImportId =
                        latestScheduleImport == null
                                ? null
                                : latestScheduleImport.getId();

                assignmentReviewRequired =
                        driverAssignments.stream()
                                .anyMatch(assignment ->
                                        assignment
                                                .requiresReviewAgainst(
                                                        latestScheduleImportId
                                                )
                                );

                if (driverAssignments.isEmpty()) {

                    assignmentMessage =
                            "Manual morning and afternoon "
                                    + "coverage required — no eligible "
                                    + "drivers are working this date.";

                } else if (driverAssignments.size() == 1) {

                    assignmentMessage =
                            "One shuttle period still needs "
                                    + "manual coverage.";
                }
            }
        }

        boolean morningAssignmentExists =
                driverAssignments.stream()
                        .anyMatch(assignment ->
                                assignment.getPeriod()
                                        == ShuttlePeriod.MORNING
                        );

        boolean afternoonAssignmentExists =
                driverAssignments.stream()
                        .anyMatch(assignment ->
                                assignment.getPeriod()
                                        == ShuttlePeriod.AFTERNOON
                        );

        model.addAttribute(
                "selectedDate",
                selectedDate
        );

        model.addAttribute(
                "currentOperationsDate",
                driverAssignmentService
                        .getCurrentOperationsDate()
        );

        model.addAttribute(
                "availableDates",
                availableDates
        );
        
        model.addAttribute(
                "selectedDateAvailable",
                selectedDateAvailable
        );

        model.addAttribute(
                "scheduleUploaded",
                csvScheduleProvider
                        .hasUploadedSchedule(
                                organizationId
                        )
        );

        model.addAttribute(
                "recentScheduleImports",
                csvScheduleProvider
                        .getRecentImports(
                                organizationId
                        )
        );

        model.addAttribute(
                "generated",
                selectedDateAvailable
        );


        model.addAttribute(
                "pickupWindows",
                pickupWindows
        );

        model.addAttribute(
                "driverSchedules",
                driverSchedules
        );

        model.addAttribute(
                "driversWithoutSchedules",
                driversWithoutSchedules
        );

        model.addAttribute(
                "activeDrivers",
                activeDrivers
        );

        model.addAttribute(
                "operatingDay",
                operatingDay
        );

        model.addAttribute(
                "calendarMessage",
                calendarMessage
        );

        model.addAttribute(
                "calendarOverrideActive",
                calendarOverrideActive
        );

        model.addAttribute(
                "showSchedule",
                selectedDateAvailable
                        && operatingDay != null
                        && (operatingDay
                                .shouldScheduleShuttle()
                                || calendarOverrideActive)
        );

        model.addAttribute(
                "driverAssignments",
                driverAssignments
        );

        model.addAttribute(
                "assignmentMessage",
                assignmentMessage
        );

        model.addAttribute(
                "assignmentReviewRequired",
                assignmentReviewRequired
        );

        model.addAttribute(
                "latestScheduleImport",
                latestScheduleImport
        );

        model.addAttribute(
                "morningAssignmentExists",
                morningAssignmentExists
        );

        model.addAttribute(
                "afternoonAssignmentExists",
                afternoonAssignmentExists
        );

        return "index";
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

        return "redirect:/app";
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

        return "redirect:/app";
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

        return "redirect:/app";
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

        return "redirect:/app";
    }

    /*
     * Records the exact time that actual
     * shuttle driving begins.
     */
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

            driverAssignmentService
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

        return "redirect:/app";
    }

    /*
     * Records the exact time that actual
     * shuttle driving ends.
     */
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

            driverAssignmentService
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

        return "redirect:/app";
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

        return "redirect:/app";
    }
}
