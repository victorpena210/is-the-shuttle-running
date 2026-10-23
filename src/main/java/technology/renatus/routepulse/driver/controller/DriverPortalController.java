package technology.renatus.routepulse.driver.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.DriverPortalService;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointAction;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointPreview;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointResult;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;

@Controller
public class DriverPortalController {

    private final DriverPortalService driverPortalService;

    public DriverPortalController(
            DriverPortalService driverPortalService) {

        this.driverPortalService = driverPortalService;
    }

    @GetMapping("/driver")
    public String showDriverHome(
            Authentication authentication,
            Model model) {

        Driver driver = driverPortalService
                .requireCurrentDriver(authentication);

        model.addAttribute("driver", driver);
        var active = driverPortalService.getActiveConfirmation(authentication);
        if (active != null) {
            model.addAttribute("activeSessionId", active.sessionId());
        }
        model.addAttribute("driverPage", "driving");
        model.addAttribute(
                "organizationName",
                driver.getOrganization().getDisplayName());
        model.addAttribute(
                "today",
                driverPortalService.currentOperationsDate());
        model.addAttribute("drivingRecords", driverPortalService.getRecentDrivingRecords(authentication));
        model.addAttribute("organizationSlug", driver.getOrganization().getSlug());

        return "driver/home";
    }

    @GetMapping("/driver/scan")
    public String showQrCheckpoint(
            Authentication authentication,
            @RequestParam(name = "code", required = false) String code,
            Model model) {

        return showCheckpoint(
                authentication,
                code,
                DrivingActionMethod.QR,
                "/driver/scan",
                model);
    }

    @GetMapping("/driver/tap")
    public String showNfcCheckpoint(
            Authentication authentication,
            @RequestParam(name = "code", required = false) String code,
            Model model) {

        return showCheckpoint(
                authentication,
                code,
                DrivingActionMethod.NFC,
                "/driver/tap",
                model);
    }

    @PostMapping("/driver/scan")
    public String recordQrCheckpoint(
            Authentication authentication,
            @RequestParam String code,
            @RequestParam(name = "action", required = false) CheckpointAction action,
            RedirectAttributes redirectAttributes) {

        return recordCheckpoint(
                authentication,
                code,
                DrivingActionMethod.QR,
                action,
                redirectAttributes);
    }

    @PostMapping("/driver/tap")
    public String recordNfcCheckpoint(
            Authentication authentication,
            @RequestParam String code,
            @RequestParam(name = "action", required = false) CheckpointAction action,
            RedirectAttributes redirectAttributes) {

        return recordCheckpoint(
                authentication,
                code,
                DrivingActionMethod.NFC,
                action,
                redirectAttributes);
    }

    private String showCheckpoint(
            Authentication authentication,
            String code,
            DrivingActionMethod method,
            String formAction,
            Model model) {

        try {
            CheckpointPreview preview = driverPortalService
                    .getCheckpointPreview(authentication, code, method);

            model.addAttribute("checkpointCode", code);
            model.addAttribute("checkpointMethod", method);
            model.addAttribute("checkpointLabel", checkpointLabel(method));
            model.addAttribute("formAction", formAction);
            model.addAttribute("driver", preview.driver());
            model.addAttribute("assignment", preview.assignment());
            model.addAttribute("checkpointAction", preview.action());
            model.addAttribute(
                    "coverageRequired",
                    preview.coverageRequired());
            model.addAttribute(
                    "takeoverRequired",
                    preview.takeoverRequired());
            model.addAttribute(
                    "scheduledDriver",
                    preview.scheduledDriver());
            model.addAttribute(
                    "currentActualDriver",
                    preview.currentActualDriver());
            model.addAttribute(
                    "organizationName",
                    preview.driver()
                            .getOrganization()
                            .getDisplayName());

        } catch (IllegalArgumentException | IllegalStateException exception) {
            model.addAttribute("checkpointMethod", method);
            model.addAttribute("checkpointLabel", checkpointLabel(method));
            model.addAttribute("scanError", exception.getMessage());
        }

        return "driver/scan";
    }

    private String recordCheckpoint(
            Authentication authentication,
            String code,
            DrivingActionMethod method,
            CheckpointAction action,
            RedirectAttributes redirectAttributes) {

        try {
            CheckpointResult result = driverPortalService
                    .recordCheckpoint(authentication, code, method, action);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    successMessage(result));

        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage());
        }

        return "redirect:/driver";
    }

    private String successMessage(CheckpointResult result) {
        String methodLabel = checkpointLabel(result.method());

        return switch (result.action()) {
            case START_UNSCHEDULED, START, RESUME -> "Driving started by " + methodLabel
                    + ". Your name and start time have been recorded.";
            case TAKEOVER -> "Driving handoff recorded. The previous driver's session ended and yours started.";
            case PAUSE -> "Driving paused. Your driving time was saved. Tap or scan again to resume.";
            case END -> "Driving ended. Your driving time was saved.";
        };
    }

    private String checkpointLabel(DrivingActionMethod method) {
        return method == DrivingActionMethod.NFC
                ? "NFC tap"
                : "QR scan";
    }
}
