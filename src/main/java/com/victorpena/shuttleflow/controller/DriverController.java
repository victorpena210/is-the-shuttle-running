package com.victorpena.shuttleflow.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.victorpena.shuttleflow.service.CurrentOrganizationService;
import com.victorpena.shuttleflow.service.DriverService;

@Controller
public class DriverController {

    private final DriverService driverService;

    private final CurrentOrganizationService
            currentOrganizationService;

    public DriverController(
            DriverService driverService,
            CurrentOrganizationService
                    currentOrganizationService) {

        this.driverService = driverService;
        this.currentOrganizationService =
                currentOrganizationService;
    }

    @GetMapping("/drivers")
    public String showDrivers(
            Authentication authentication,
            Model model) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        model.addAttribute(
                "drivers",
                driverService.getAllDrivers(
                        organizationId
                )
        );

        return "drivers";
    }

    @PostMapping("/drivers")
    public String registerDriver(
            Authentication authentication,
            @RequestParam String employeeId,
            @RequestParam String name,
            @RequestParam(defaultValue = "false")
            boolean active,
            RedirectAttributes redirectAttributes) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {
            driverService.registerDriver(
                    organizationId,
                    employeeId,
                    name,
                    active
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Driver was successfully registered."
            );

        } catch (IllegalArgumentException exception) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/drivers";
    }

    @PostMapping("/drivers/{id}/status")
    public String updateDriverStatus(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam boolean active,
            RedirectAttributes redirectAttributes) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {
            driverService.updateActiveStatus(
                    organizationId,
                    id,
                    active
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Driver status was updated."
            );

        } catch (IllegalArgumentException exception) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/drivers";
    }
}