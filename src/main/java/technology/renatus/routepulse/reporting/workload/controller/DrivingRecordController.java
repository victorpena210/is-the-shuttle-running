package technology.renatus.routepulse.reporting.workload.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.service.AssignmentReviewService;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.reporting.workload.model.DrivingRecordPage;
import technology.renatus.routepulse.reporting.workload.service.DrivingRecordPageService;
import technology.renatus.routepulse.reporting.workload.view.DrivingRecordModelAssembler;

@Controller
public class DrivingRecordController {

    private final AssignmentReviewService assignmentReviewService;
    private final CurrentOrganizationService currentOrganizationService;
    private final DrivingRecordPageService pageService;
    private final DrivingRecordModelAssembler modelAssembler;

    public DrivingRecordController(
            AssignmentReviewService assignmentReviewService,
            CurrentOrganizationService currentOrganizationService,
            DrivingRecordPageService pageService,
            DrivingRecordModelAssembler modelAssembler) {
        this.assignmentReviewService = assignmentReviewService;
        this.currentOrganizationService = currentOrganizationService;
        this.pageService = pageService;
        this.modelAssembler = modelAssembler;
    }

    @GetMapping("/driving-records")
    public String showDrivingRecords(
            Authentication authentication,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long driverId,
            @RequestParam(name = "view", required = false) String view,
            Model model) {

        DrivingRecordPage page = pageService.build(
                authentication,
                fromDate,
                toDate,
                driverId,
                view);
        modelAssembler.addTo(model, page);
        return "manager/driving-records";
    }

    @PostMapping("/driving-records/{assignmentId}/outcome")
    public String reviewOutcome(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @RequestParam AssignmentOutcome outcome,
            @RequestParam(defaultValue = "false") boolean requireMakeup,
            @RequestParam(required = false) String reason,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long driverId,
            RedirectAttributes redirectAttributes) {

        Long organizationId = currentOrganizationService.requireOrganizationId(authentication);

        try {
            assignmentReviewService.reviewAssignmentOutcome(
                    organizationId,
                    assignmentId,
                    outcome,
                    requireMakeup,
                    reason,
                    authentication.getName());

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    requireMakeup
                            ? "Outcome saved. The scheduled driver now has future make-up priority."
                            : "Outcome saved. No make-up priority is being applied.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        preserveFilters(redirectAttributes, fromDate, toDate, driverId);
        redirectAttributes.addAttribute("view", "records");
        return "redirect:/driving-records#record-" + assignmentId;
    }

    private void preserveFilters(
            RedirectAttributes redirectAttributes,
            LocalDate fromDate,
            LocalDate toDate,
            Long driverId) {
        if (fromDate != null) {
            redirectAttributes.addAttribute("from", fromDate);
        }
        if (toDate != null) {
            redirectAttributes.addAttribute("to", toDate);
        }
        if (driverId != null) {
            redirectAttributes.addAttribute("driverId", driverId);
        }
    }
}
