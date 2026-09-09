package technology.renatus.routepulse.organization.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import technology.renatus.routepulse.organization.dto.InvitationAcceptanceForm;
import technology.renatus.routepulse.organization.dto.ManagerInvitationForm;
import technology.renatus.routepulse.organization.model.OrganizationInvitation;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.organization.service.InvitationAcceptanceService;
import technology.renatus.routepulse.organization.service.OrganizationInvitationService;

@Controller
public class OrganizationInvitationController {

    private final InvitationAcceptanceService invitationAcceptanceService;

    private final OrganizationInvitationService
            organizationInvitationService;

    private final CurrentOrganizationService
            currentOrganizationService;

    public OrganizationInvitationController(
            InvitationAcceptanceService invitationAcceptanceService,
            OrganizationInvitationService
                    organizationInvitationService,
            CurrentOrganizationService
                    currentOrganizationService) {

        this.invitationAcceptanceService = invitationAcceptanceService;

        this.organizationInvitationService =
                organizationInvitationService;

        this.currentOrganizationService =
                currentOrganizationService;
    }

    /*
     * OWNER PAGE
     *
     * Shows the form for inviting managers
     * and the organization's existing invitations.
     */
    @GetMapping("/organization/invitations")
    public String showInvitationsPage(
            Authentication authentication,
            Model model) {

        model.addAttribute(
                "organizationName",
                currentOrganizationService
                        .requireOrganization(authentication)
                        .getDisplayName()
        );

        model.addAttribute(
                "managerInvitationForm",
                new ManagerInvitationForm()
        );

        model.addAttribute(
                "invitations",
                organizationInvitationService
                        .getInvitationsForOwner(
                                authentication
                        )
        );

        return "manager/invitations";
    }

    /*
     * OWNER ACTION
     *
     * Handles submitting an email address
     * for a new manager invitation.
     */
    @PostMapping("/organization/invitations")
    public String sendManagerInvitation(
            Authentication authentication,
            @Valid
            @ModelAttribute("managerInvitationForm")
            ManagerInvitationForm form,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {

            model.addAttribute(
                    "invitations",
                    organizationInvitationService
                            .getInvitationsForOwner(
                                    authentication
                            )
            );

            return "manager/invitations";
        }

        try {

            organizationInvitationService
                    .createAndSendManagerInvitation(
                            authentication,
                            form.getEmail()
                    );

        } catch (IllegalArgumentException exception) {

            bindingResult.reject(
                    "invitation.failed",
                    exception.getMessage()
            );

            model.addAttribute(
                    "invitations",
                    organizationInvitationService
                            .getInvitationsForOwner(
                                    authentication
                            )
            );

            return "manager/invitations";

        } catch (DataIntegrityViolationException exception) {

            bindingResult.reject(
                    "invitation.failed",
                    "That manager invitation already exists. Refresh the page and try again."
            );

            model.addAttribute(
                    "invitations",
                    organizationInvitationService
                            .getInvitationsForOwner(
                                    authentication
                            )
            );

            return "manager/invitations";
        }

        return "redirect:/organization/invitations?sent";
    }

    /*
     * PUBLIC INVITATION PAGE
     *
     * The invited manager reaches this endpoint
     * by clicking the secret link in their email.
     */
    @GetMapping("/invitations/accept")
    public String showAcceptInvitationPage(
            @RequestParam(
                    name = "token",
                    required = false
            )
            String token,
            Model model,
            HttpServletResponse response) {

        response.setHeader(
                "Referrer-Policy",
                "no-referrer"
        );

        if (token == null
                || token.isBlank()) {

            return "redirect:/login?invitationError";
        }

        try {

            OrganizationInvitation invitation =
                    invitationAcceptanceService
                            .getInvitationForAcceptance(
                                    token
                            );

            InvitationAcceptanceForm form =
                    new InvitationAcceptanceForm();

            form.setToken(token);

            model.addAttribute(
                    "invitation",
                    invitation
            );

            model.addAttribute(
                    "invitationAcceptanceForm",
                    form
            );

            return "auth/accept-invitation";

        } catch (IllegalArgumentException exception) {

            return "redirect:/login?invitationError";
        }
    }

    /*
     * PUBLIC INVITATION ACCEPTANCE
     *
     * Creates the manager's account and attaches
     * them to the organization as ADMIN.
     */
    @PostMapping("/invitations/accept")
    public String acceptInvitation(
            @Valid
            @ModelAttribute("invitationAcceptanceForm")
            InvitationAcceptanceForm form,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response) {

        response.setHeader(
                "Referrer-Policy",
                "no-referrer"
        );

        if (!Objects.equals(
                form.getPassword(),
                form.getConfirmPassword()
        )) {

            bindingResult.rejectValue(
                    "confirmPassword",
                    "password.mismatch",
                    "The passwords do not match."
            );
        }

        if (bindingResult.hasErrors()) {

            return redisplayAcceptanceForm(
                    form,
                    model
            );
        }

        try {

            invitationAcceptanceService
                    .acceptInvitation(
                            form
                    );

        } catch (IllegalArgumentException exception) {

            bindingResult.reject(
                    "invitation.failed",
                    exception.getMessage()
            );

            return redisplayAcceptanceForm(
                    form,
                    model
            );
        }

        return "redirect:/login?invitationAccepted";
    }

    private String redisplayAcceptanceForm(
            InvitationAcceptanceForm form,
            Model model) {

        try {

            OrganizationInvitation invitation =
                    invitationAcceptanceService
                            .getInvitationForAcceptance(
                                    form.getToken()
                            );

            model.addAttribute(
                    "invitation",
                    invitation
            );

            return "auth/accept-invitation";

        } catch (IllegalArgumentException exception) {

            return "redirect:/login?invitationError";
        }
    }
}
