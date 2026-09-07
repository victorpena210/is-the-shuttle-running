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
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.ScheduleImport;
import technology.renatus.routepulse.model.ShuttlePeriod;
import technology.renatus.routepulse.model.TransportationDemandAnalysis;
import technology.renatus.routepulse.provider.CsvScheduleProvider;
import technology.renatus.routepulse.service.CurrentOrganizationService;
import technology.renatus.routepulse.service.DriverAssignmentService;
import technology.renatus.routepulse.service.DriverScheduleService;
import technology.renatus.routepulse.service.DriverService;
import technology.renatus.routepulse.service.ParkOperatingDayService;
import technology.renatus.routepulse.service.TransportationDemandService;

@Controller
public class PickupPlanningController {

    private final TransportationDemandService
            transportationDemandService;

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
            TransportationDemandService transportationDemandService,
            CsvScheduleProvider csvScheduleProvider,
            DriverScheduleService driverScheduleService,
            ParkOperatingDayService parkOperatingDayService,
            DriverAssignmentService driverAssignmentService,
            DriverService driverService,
            CurrentOrganizationService
                    currentOrganizationService) {

        this.transportationDemandService =
                transportationDemandService;

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

        model.addAttribute(
                "organizationSlug",
                organization.getSlug()
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

        TransportationDemandAnalysis transportationDemand =
                TransportationDemandAnalysis.empty();

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
         * Only begin planning when the selected
         * date actually exists in the uploaded
         * employee schedule.
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
             * =====================================================
             * TRANSPORTATION DEMAND
             * =====================================================
             *
             * Demand belongs to the uploaded employee schedule, not
             * the Six Flags calendar. Always calculate it whenever
             * this date exists in the uploaded schedule.
             *
             * The park calendar only controls whether RoutePulse may
             * create driver coverage automatically.
             */
            transportationDemand =
                    transportationDemandService
                            .getDemandForDate(
                                    organizationId,
                                    selectedDate
                            );

            /*
             * =====================================================
             * PARK OPERATING CHECK
             * =====================================================
             *
             * RoutePulse checks the Six Flags operating calendar
             * before creating shuttle assignments.
             *
             * OPEN:
             *     Continue into driver scheduling.
             *
             * CLOSED:
             *     Stop here. No shuttle driver is scheduled.
             *
             * UNKNOWN:
             *     Do not automatically schedule anyone unless a
             *     manager explicitly chooses to continue.
             */
            operatingDay =
                    parkOperatingDayService
                            .getOperatingDay(
                                    selectedDate
                            );

            /*
             * =====================================================
             * PARK CLOSED
             * =====================================================
             */
            if (operatingDay.status()
                    == ParkOperatingStatus.CLOSED) {

                calendarMessage =
                        "Park closed — shuttle not scheduled";
            }

            /*
             * =====================================================
             * CALENDAR COULD NOT BE VERIFIED
             * =====================================================
             *
             * RoutePulse does not assume the park is open when
             * the Six Flags calendar cannot be reached.
             */
            else if (operatingDay.status()
                    == ParkOperatingStatus.UNKNOWN) {

                if (allowUnverifiedCalendar) {

                    calendarOverrideActive =
                            true;

                    calendarMessage =
                            "Calendar unverified — manager "
                                    + "continued with uploaded schedule";

                } else {

                    calendarMessage =
                            "Calendar could not be verified "
                                    + "— review required";
                }
            }

            /*
             * =====================================================
             * PARK OPEN
             * =====================================================
             *
             * Driver scheduling only happens inside this block.
             *
             * This is the important gate:
             *
             * PARK OPEN
             *     -> inspect employee schedule
             *     -> find eligible drivers
             *     -> assign coverage
             *
             * PARK CLOSED
             *     -> never enters this block
             *     -> no driver assignment is created
             */
            if (operatingDay.status()
                    == ParkOperatingStatus.OPEN
                    || calendarOverrideActive) {

                if (operatingDay.status()
                        == ParkOperatingStatus.OPEN) {

                    if (operatingDay.isManagerConfirmed()) {
                        calendarMessage =
                                "Open — manager confirmed after calendar verification failure";

                    } else if (operatingDay.isPersistedSnapshot()) {
                        calendarMessage =
                                operatingDay.buyout()
                                        ? "Open — saved Six Flags calendar · Private Buyout"
                                        : "Open — saved Six Flags calendar";

                    } else {
                        calendarMessage =
                                operatingDay.buyout()
                                        ? "Open — Private Buyout"
                                        : "Open — Public Operating Day";
                    }
                }

                /*
                 * =================================================
                 * CHECK WHICH DRIVERS ARE ACTUALLY SCHEDULED
                 * =================================================
                 */
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

                /*
                 * =================================================
                 * CREATE DRIVER ASSIGNMENTS
                 * =================================================
                 *
                 * DriverAssignmentService already handles:
                 *
                 * - eligible scheduled drivers
                 * - fair distribution
                 * - morning assignment
                 * - afternoon assignment
                 * - assignment history
                 * - source schedule import
                 *
                 * If no eligible driver is scheduled,
                 * it returns an empty list.
                 */
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

                /*
                 * =================================================
                 * PARK OPEN BUT NO DRIVER AVAILABLE
                 * =================================================
                 */
                if (driverAssignments.isEmpty()) {

                    assignmentMessage =
                            "Manual morning and afternoon "
                                    + "coverage required — no eligible "
                                    + "drivers are working this date.";
                }

                /*
                 * Only one eligible driver/assignment was created,
                 * so the remaining shuttle period needs coverage.
                 */
                else if (driverAssignments.size() == 1) {

                    assignmentMessage =
                            "One shuttle period still needs "
                                    + "manual coverage.";
                }
            }
        }

        DriverAssignment morningAssignment =
                driverAssignments.stream()
                        .filter(assignment ->
                                assignment.getPeriod()
                                        == ShuttlePeriod.MORNING
                        )
                        .findFirst()
                        .orElse(null);
        
        DriverAssignment middayAssignment =
                driverAssignments.stream()
                        .filter(assignment ->
                                assignment.getPeriod()
                                        == ShuttlePeriod.MIDDAY
                        )
                        .findFirst()
                        .orElse(null);

        DriverAssignment afternoonAssignment =
                driverAssignments.stream()
                        .filter(assignment ->
                                assignment.getPeriod()
                                        == ShuttlePeriod.AFTERNOON
                        )
                        .findFirst()
                        .orElse(null);

        boolean morningAssignmentExists =
                morningAssignment != null;

        boolean middayAssignmentExists =
                middayAssignment != null;

        boolean afternoonAssignmentExists =
                afternoonAssignment != null;

        int totalShiftCount = 3;

        int coveredShiftCount =
                (morningAssignmentExists ? 1 : 0)
                        + (middayAssignmentExists ? 1 : 0)
                        + (afternoonAssignmentExists ? 1 : 0);

        int needsAttentionShiftCount =
                totalShiftCount - coveredShiftCount;

        DriverSchedule morningAssignedDriverSchedule =
                findAssignedDriverSchedule(
                        driverSchedules,
                        morningAssignment
                );
        

		DriverSchedule middayAssignedDriverSchedule =
		        findAssignedDriverSchedule(
		                driverSchedules,
		                middayAssignment
		        );

        DriverSchedule afternoonAssignedDriverSchedule =
                findAssignedDriverSchedule(
                        driverSchedules,
                        afternoonAssignment
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
                "transportationDemand",
                transportationDemand
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
                "calendarDiagnosticCode",
                operatingDay == null
                        ? null
                        : operatingDay.diagnosticCode()
        );

        model.addAttribute(
                "calendarConfirmationRequired",
                selectedDateAvailable
                        && operatingDay != null
                        && operatingDay.status()
                                == ParkOperatingStatus.UNKNOWN
                        && !calendarOverrideActive
        );

        /*
         * The schedule/coverage UI is only displayed when:
         *
         * 1. The date exists in the uploaded schedule.
         * 2. We successfully checked the operating day.
         * 3. The park is open, or a manager explicitly
         *    continued after an UNKNOWN calendar response.
         *
         * A CLOSED park therefore cannot display or create
         * shuttle coverage.
         */
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

        model.addAttribute(
                "morningAssignment",
                morningAssignment
        );

        model.addAttribute(
                "afternoonAssignment",
                afternoonAssignment
        );

        model.addAttribute(
                "coveredShiftCount",
                coveredShiftCount
        );

        model.addAttribute(
                "needsAttentionShiftCount",
                needsAttentionShiftCount
        );

        model.addAttribute(
                "morningAssignedDriverSchedule",
                morningAssignedDriverSchedule
        );

        model.addAttribute(
                "afternoonAssignedDriverSchedule",
                afternoonAssignedDriverSchedule
        );
        
        model.addAttribute(
                "totalShiftCount",
                totalShiftCount
        );

        model.addAttribute(
                "middayAssignmentExists",
                middayAssignmentExists
        );

        model.addAttribute(
                "middayAssignment",
                middayAssignment
        );

        model.addAttribute(
                "middayAssignedDriverSchedule",
                middayAssignedDriverSchedule
        );

        return "index";
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


    private DriverSchedule findAssignedDriverSchedule(
            List<DriverSchedule> schedules,
            DriverAssignment assignment) {

        if (assignment == null
                || assignment.getScheduledDriver() == null
                || schedules == null
                || schedules.isEmpty()) {

            return null;
        }

        Long driverId =
                assignment
                        .getScheduledDriver()
                        .getId();

        List<DriverSchedule> matchingSchedules =
                schedules.stream()
                        .filter(schedule ->
                                schedule.driverId().equals(driverId)
                        )
                        .toList();

        if (matchingSchedules.isEmpty()) {
            return null;
        }

        if (assignment.getScheduledStartTime() == null
                || assignment.getScheduledEndTime() == null) {

            return matchingSchedules.get(0);
        }

        return matchingSchedules.stream()
                .filter(schedule ->
                        schedule.overlaps(
                                assignment.getScheduledStartTime(),
                                assignment.getScheduledEndTime()
                        )
                )
                .max((left, right) ->
                        Long.compare(
                                left.overlapMinutes(
                                        assignment.getScheduledStartTime(),
                                        assignment.getScheduledEndTime()
                                ),
                                right.overlapMinutes(
                                        assignment.getScheduledStartTime(),
                                        assignment.getScheduledEndTime()
                                )
                        )
                )
                .orElse(matchingSchedules.get(0));
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

        return "redirect:/app#coverage";
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

        return "redirect:/app#coverage";
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
