//package com.victorpena.shuttleflow.controller;
//
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.servlet.mvc.support.RedirectAttributes;
//
//import com.victorpena.shuttleflow.service.DriverService;
//
//@Controller
//public class DriverController {
//
//    private final DriverService driverService;
//
//    public DriverController(DriverService driverService) {
//        this.driverService = driverService;
//    }
//
//    @GetMapping("/drivers")
//    public String showDrivers(Model model) {
//
//        model.addAttribute(
//                "drivers",
//                driverService.getAllDrivers()
//        );
//
//        return "drivers";
//    }
//
//    @PostMapping("/drivers")
//    public String registerDriver(
//            @RequestParam String employeeId,
//            @RequestParam String name,
//            @RequestParam(defaultValue = "false")
//            boolean active,
//            RedirectAttributes redirectAttributes) {
//
//        try {
//            driverService.registerDriver(
//                    employeeId,
//                    name,
//                    active
//            );
//
//            redirectAttributes.addFlashAttribute(
//                    "successMessage",
//                    "Driver was successfully registered."
//            );
//
//        } catch (IllegalArgumentException exception) {
//
//            redirectAttributes.addFlashAttribute(
//                    "errorMessage",
//                    exception.getMessage()
//            );
//        }
//
//        return "redirect:/drivers";
//    }
//
//    @PostMapping("/drivers/{id}/status")
//    public String updateDriverStatus(
//            @PathVariable Long id,
//            @RequestParam boolean active,
//            RedirectAttributes redirectAttributes) {
//
//        try {
//            driverService.updateActiveStatus(id, active);
//
//            redirectAttributes.addFlashAttribute(
//                    "successMessage",
//                    "Driver status was updated."
//            );
//
//        } catch (IllegalArgumentException exception) {
//
//            redirectAttributes.addFlashAttribute(
//                    "errorMessage",
//                    exception.getMessage()
//            );
//        }
//
//        return "redirect:/drivers";
//    }
//}