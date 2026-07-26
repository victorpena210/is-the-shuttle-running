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
import com.victorpena.shuttleflow.model.PickupWindow;
import com.victorpena.shuttleflow.provider.CsvScheduleProvider;
import com.victorpena.shuttleflow.service.PickupPlanningService;

@Controller
public class PickupPlanningController {

    private final PickupPlanningService pickupPlanningService;
    private final CsvScheduleProvider csvScheduleProvider;

    public PickupPlanningController(
            PickupPlanningService pickupPlanningService,
            CsvScheduleProvider csvScheduleProvider) {

        this.pickupPlanningService = pickupPlanningService;
        this.csvScheduleProvider = csvScheduleProvider;
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

        List<PickupWindow> pickupWindows;
        List<DriverSchedule> driverSchedules = List.of();
        List<Driver> driversWithoutSchedules = List.of();

        /*
         * Only generate the table after the Generate button
         * submits a date.
         */
        if (date == null) {
            pickupWindows = List.of();
        } else {
            pickupWindows =
                    pickupPlanningService.getPickupWindowsForDate(date);

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