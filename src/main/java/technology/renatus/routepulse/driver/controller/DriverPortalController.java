package technology.renatus.routepulse.driver.controller;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
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
        model.addAttribute(
                "organizationName",
                driver.getOrganization().getDisplayName());
        model.addAttribute(
                "today",
                driverPortalService.currentOperationsDate());
        List<DriverAssignment> todayAssignments =
                driverPortalService.getTodayAssignments(authentication);

        model.addAttribute("todayAssignments", todayAssignments);
        model.addAttribute(
                "primaryAssignment",
                driverPortalService.selectPrimaryTodayAssignment(todayAssignments));

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
            case RESUME -> {
                String coverageMessage = result.coveragePerformed()
                        && result.scheduledDriver() != null
                                ? " " + result.scheduledDriver().getName()
                                        + " remains the scheduled driver."
                                : "";

                yield "Driving resumed by " + methodLabel
                        + ". Is the Shuttle Running opened a new actual driving session on the same "
                        + result.assignment().getPeriod()
                        + " assignment and preserved the earlier session."
                        + coverageMessage;
            }
            case START -> {
                if (result.coveragePerformed() && result.scheduledDriver() != null) {
                    yield "Driving started by " + methodLabel + ". "
                            + result.scheduledDriver().getName()
                            + " remains the scheduled driver for the "
                            + result.assignment().getPeriod()
                            + " shift, and Is the Shuttle Running recorded you separately as the actual driver.";
                }

                yield "Driving started by " + methodLabel
                        + ". Is the Shuttle Running recorded your actual start time.";
            }
            case TAKEOVER -> {
                String previousDriver = result.previousActualDriver() == null
                        ? "The previous driver's"
                        : result.previousActualDriver().getName() + "'s";
                String coverageMessage = result.coveragePerformed()
                        && result.scheduledDriver() != null
                                ? " " + result.scheduledDriver().getName()
                                        + " remains the scheduled driver."
                                : "";

                yield "Driving handoff recorded by " + methodLabel + ". "
                        + previousDriver
                        + " actual driving session ended and yours started."
                        + coverageMessage;
            }
            case PAUSE -> "Driving paused by " + methodLabel
                    + ". Is the Shuttle Running saved this driving segment. Tap the NFC tag or scan the QR code when you are ready to resume.";
            case END -> "Driving ended by " + methodLabel
                    + ". Is the Shuttle Running saved the final actual end time for this driving segment.";
        };
    }

    private String checkpointLabel(DrivingActionMethod method) {
        return method == DrivingActionMethod.NFC
                ? "NFC tap"
                : "QR scan";
    }
}
