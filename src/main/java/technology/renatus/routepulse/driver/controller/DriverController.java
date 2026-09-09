package technology.renatus.routepulse.driver.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driver.service.DriverOnboardingService;
import technology.renatus.routepulse.driver.service.DriverService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Controller
public class DriverController {

    private final DriverService
            driverService;

    private final DriverOnboardingService
            driverOnboardingService;

    private final CurrentOrganizationService
            currentOrganizationService;

    public DriverController(
            DriverService driverService,
            DriverOnboardingService driverOnboardingService,
            CurrentOrganizationService
                    currentOrganizationService) {

        this.driverService =
                driverService;

        this.driverOnboardingService =
                driverOnboardingService;

        this.currentOrganizationService =
                currentOrganizationService;
    }

    @GetMapping("/drivers")
    public String showDrivers(
            Authentication authentication,
            Model model) {

        Organization organization =
                currentOrganizationService
                        .requireOrganization(
                                authentication
                        );

        Long organizationId =
                organization.getId();

        model.addAttribute(
                "organizationName",
                organization.getDisplayName()
        );

        model.addAttribute(
                "isOwner",
                authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority
                                        .getAuthority()
                                        .equals(
                                                "ROLE_OWNER"
                                        )
                        )
        );

        model.addAttribute(
                "drivers",
                driverService
                        .getAllDrivers(
                                organizationId
                        )
        );

        return "manager/drivers";
    }

    @PostMapping("/drivers")
    public String registerDriver(
            Authentication authentication,
            @RequestParam
            String employeeId,
            @RequestParam
            String email,
            @RequestParam(
                    defaultValue = "false"
            )
            boolean active,
            RedirectAttributes
                    redirectAttributes) {

        try {

            driverOnboardingService
                    .registerAndInviteDriver(
                            authentication,
                            employeeId,
                            email,
                            active
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            "Driver was registered and an invitation email was sent."
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

        return "redirect:/drivers";
    }

    @GetMapping("/drivers/lookup")
    @ResponseBody
    public DriverLookupResponse
            lookupDriver(
                    Authentication authentication,
                    @RequestParam
                    String employeeId) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {

            String name =
                    driverService
                            .findEmployeeName(
                                    organizationId,
                                    employeeId
                            );

            return new DriverLookupResponse(
                    true,
                    employeeId.trim(),
                    name,
                    "Employee found."
            );

        } catch (
                IllegalArgumentException exception) {

            return new DriverLookupResponse(
                    false,
                    employeeId == null
                            ? ""
                            : employeeId.trim(),
                    null,
                    exception.getMessage()
            );
        }
    }

    @PostMapping(
            "/drivers/{id}/resend-invitation"
    )
    public String resendDriverInvitation(
            Authentication authentication,
            @PathVariable
            Long id,
            RedirectAttributes
                    redirectAttributes) {

        try {

            driverOnboardingService
                    .resendDriverInvitation(
                            authentication,
                            id
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            "A new driver invitation email was sent."
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

        return "redirect:/drivers";
    }

    @PostMapping(
            "/drivers/{id}/status"
    )
    public String updateDriverStatus(
            Authentication authentication,
            @PathVariable
            Long id,
            @RequestParam
            boolean active,
            RedirectAttributes
                    redirectAttributes) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        try {

            driverService
                    .updateActiveStatus(
                            organizationId,
                            id,
                            active
                    );

            redirectAttributes
                    .addFlashAttribute(
                            "successMessage",
                            "Driver status was updated."
                    );

        } catch (
                IllegalArgumentException exception) {

            redirectAttributes
                    .addFlashAttribute(
                            "errorMessage",
                            exception.getMessage()
                    );
        }

        return "redirect:/drivers";
    }

    public record DriverLookupResponse(
            boolean found,
            String employeeId,
            String name,
            String message) {
    }
}
