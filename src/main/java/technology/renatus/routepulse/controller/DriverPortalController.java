package technology.renatus.routepulse.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.service.DriverPortalService;
import technology.renatus.routepulse.service.DriverPortalService.ScanPreview;
import technology.renatus.routepulse.service.DriverPortalService.ScanResult;

@Controller
public class DriverPortalController {

    private final DriverPortalService
            driverPortalService;

    public DriverPortalController(
            DriverPortalService driverPortalService) {

        this.driverPortalService =
                driverPortalService;
    }

    @GetMapping("/driver")
    public String showDriverHome(
            Authentication authentication,
            Model model) {

        Driver driver =
                driverPortalService
                        .requireCurrentDriver(
                                authentication
                        );

        model.addAttribute(
                "driver",
                driver
        );

        model.addAttribute(
                "organizationName",
                driver
                        .getOrganization()
                        .getDisplayName()
        );

        model.addAttribute(
                "today",
                driverPortalService
                        .currentOperationsDate()
        );

        model.addAttribute(
                "todayAssignments",
                driverPortalService
                        .getTodayAssignments(
                                authentication
                        )
        );

        return "driver-home";
    }

    @GetMapping("/driver/scan")
    public String showQrScan(
            Authentication authentication,
            @RequestParam(
                    name = "code",
                    required = false
            )
            String code,
            Model model) {

        try {

            ScanPreview preview =
                    driverPortalService
                            .getScanPreview(
                                    authentication,
                                    code
                            );

            model.addAttribute(
                    "qrCode",
                    code
            );

            model.addAttribute(
                    "driver",
                    preview.driver()
            );

            model.addAttribute(
                    "assignment",
                    preview.assignment()
            );

            model.addAttribute(
                    "scanAction",
                    preview.action()
            );

            model.addAttribute(
                    "overrideRequired",
                    preview.overrideRequired()
            );

            model.addAttribute(
                    "scheduledDriver",
                    preview.scheduledDriver()
            );

            model.addAttribute(
                    "organizationName",
                    preview
                            .driver()
                            .getOrganization()
                            .getDisplayName()
            );

        } catch (
                IllegalArgumentException
                | IllegalStateException exception) {

            model.addAttribute(
                    "scanError",
                    exception.getMessage()
            );
        }

        return "driver-scan";
    }

    @PostMapping("/driver/scan")
    public String recordQrScan(
            Authentication authentication,
            @RequestParam
            String code,
            RedirectAttributes
                    redirectAttributes) {

        try {

            ScanResult result =
                    driverPortalService
                            .recordQrScan(
                                    authentication,
                                    code
                            );

            if (result.action()
                    == DriverPortalService
                            .ScanAction
                            .START) {

                if (result.overridePerformed()
                        && result.previousDriver()
                        != null) {

                    redirectAttributes
                            .addFlashAttribute(
                                    "successMessage",
                                    "Driving started. You automatically took over the "
                                            + result.assignment()
                                                    .getPeriod()
                                            + " shift from "
                                            + result.previousDriver()
                                                    .getName()
                                            + ". RoutePulse recorded you as the actual driver and saved your actual start time."
                            );

                } else {

                    redirectAttributes
                            .addFlashAttribute(
                                    "successMessage",
                                    "Driving started. RoutePulse recorded your actual start time."
                            );
                }

            } else {

                redirectAttributes
                        .addFlashAttribute(
                                "successMessage",
                                "Driving ended. RoutePulse recorded your actual end time."
                        );
            }

        } catch (
                IllegalArgumentException
                | IllegalStateException exception) {

            redirectAttributes
                    .addFlashAttribute(
                            "errorMessage",
                            exception.getMessage()
                    );
        }

        return "redirect:/driver";
    }
}