package com.victorpena.shuttleflow.controller;

import java.time.LocalDate;

import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.victorpena.shuttleflow.model.Driver;
import com.victorpena.shuttleflow.model.DriverSchedule;
import com.victorpena.shuttleflow.model.DriverScheduleResult;
import com.victorpena.shuttleflow.model.ParkOperatingDay;
import com.victorpena.shuttleflow.model.ParkOperatingStatus;
import com.victorpena.shuttleflow.model.PickupWindow;
import com.victorpena.shuttleflow.provider.CsvScheduleProvider;
import com.victorpena.shuttleflow.service.DriverScheduleService;
import com.victorpena.shuttleflow.service.ParkOperatingDayService;
import com.victorpena.shuttleflow.service.PickupPlanningService;
import com.victorpena.shuttleflow.model.DriverAssignment;
import com.victorpena.shuttleflow.service.DriverAssignmentService;
@Controller
public class PickupPlanningController {

    private final PickupPlanningService pickupPlanningService;
    private final CsvScheduleProvider csvScheduleProvider;
    private final DriverScheduleService driverScheduleService;
    private final ParkOperatingDayService parkOperatingDayService;
    private final DriverAssignmentService driverAssignmentService;

    public PickupPlanningController(
            PickupPlanningService pickupPlanningService,
            CsvScheduleProvider csvScheduleProvider,
            DriverScheduleService driverScheduleService,
            ParkOperatingDayService parkOperatingDayService,
            DriverAssignmentService driverAssignmentService) {

        this.pickupPlanningService = pickupPlanningService;
        this.csvScheduleProvider = csvScheduleProvider;
        this.driverScheduleService = driverScheduleService;
        this.parkOperatingDayService = parkOperatingDayService;
        this.driverAssignmentService = driverAssignmentService;
    }

    @GetMapping("/")
    public String showDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            Model model) {

        List<LocalDate> availableDates =
                csvScheduleProvider.getAvailableDates();

        LocalDate selectedDate = date;

        /*
         * After uploading, default the date picker to the
         * first date found inside the CSV.
         */
        if (selectedDate == null && !availableDates.isEmpty()) {
            selectedDate = availableDates.get(0);
        }

        /*
         * Before a CSV is uploaded, default to today's date.
         */
        if (selectedDate == null) {
            selectedDate = LocalDate.now();
        }

        List<PickupWindow> pickupWindows = List.of();
        List<DriverSchedule> driverSchedules = List.of();
        List<Driver> driversWithoutSchedules = List.of();
        List<DriverAssignment> driverAssignments = List.of();
       
        String assignmentMessage = null;
        ParkOperatingDay operatingDay = null;
        String calendarMessage = null;

        if (date != null) {

            operatingDay =
                    parkOperatingDayService.getOperatingDay(date);

            if (operatingDay.status()
                    == ParkOperatingStatus.CLOSED) {

                calendarMessage =
                        "Park closed — shuttle not scheduled";

            } else if (operatingDay.status()
                    == ParkOperatingStatus.UNKNOWN) {

                calendarMessage =
                        "Calendar could not be verified — review required";

            } else {

                /*
                 * This is the OPEN branch.
                 *
                 * Both public operating days and private buyouts
                 * reach this branch and schedule the shuttle.
                 */
                calendarMessage = operatingDay.buyout()
                        ? "Open — Private Buyout"
                        : "Open — Public Operating Day";

                pickupWindows =
                        pickupPlanningService
                                .getPickupWindowsForDate(date);

                DriverScheduleResult result =
                      driverScheduleService
                                .getDriverSchedulesForDate(date);

                driverSchedules = result.scheduledDrivers();

                driversWithoutSchedules =
                        result.driversWithoutSchedules();
                
                driverAssignments =
                        driverAssignmentService
                                .getOrCreateAssignments(date);

                if (driverAssignments.isEmpty()) {

                    assignmentMessage =
                            "Manual morning and afternoon coverage required — "
                            + "no eligible drivers are working this date.";

                } else if (driverAssignments.size() == 1) {

                    assignmentMessage =
                            "Morning driver assigned — manual afternoon "
                            + "coverage required.";
                }
            }
        }

        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("availableDates", availableDates);
        model.addAttribute(
                "scheduleUploaded",
                csvScheduleProvider.hasUploadedSchedule()
        );
        model.addAttribute("generated", date != null);
        model.addAttribute("pickupWindows", pickupWindows);
        model.addAttribute("driverSchedules", driverSchedules);
        model.addAttribute(
                "driversWithoutSchedules",
                driversWithoutSchedules
                
        );
        
        model.addAttribute("operatingDay", operatingDay);
        model.addAttribute("calendarMessage", calendarMessage);

        model.addAttribute(
                "showSchedule",
                operatingDay != null
                        && operatingDay.shouldScheduleShuttle()
                       
        );
  
        model.addAttribute(
                "driverAssignments",
                driverAssignments
        );

        model.addAttribute(
                "assignmentMessage",
                assignmentMessage
        );
        return "index";
    }

    @PostMapping("/upload")
    public String uploadSchedule(
            @RequestParam("scheduleFile")
            MultipartFile scheduleFile,
            RedirectAttributes redirectAttributes) {

        try {
            int shiftCount =
                    csvScheduleProvider.loadSchedule(scheduleFile);
            LocalDate firstAvailableDate =
                    csvScheduleProvider.getAvailableDates().get(0);

            redirectAttributes.addAttribute(
                    "date",
                    firstAvailableDate.toString()
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    shiftCount + " shifts were successfully uploaded."
            );

        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/";
    }
}