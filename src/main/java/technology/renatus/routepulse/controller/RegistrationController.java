package technology.renatus.routepulse.controller;

import java.time.Duration;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import technology.renatus.routepulse.dataTransferObject.RegistrationForm;
import technology.renatus.routepulse.security.PublicRequestThrottle;
import technology.renatus.routepulse.service.RegistrationService;

@Controller
public class RegistrationController {

    private final RegistrationService registrationService;
    private final boolean registrationEnabled;
    private final PublicRequestThrottle throttle;

    public RegistrationController(
            RegistrationService registrationService,
            PublicRequestThrottle throttle,
            @Value("${app.registration.enabled:true}")
            boolean registrationEnabled) {

        this.registrationService = registrationService;
        this.registrationEnabled = registrationEnabled;
        this.throttle = throttle;
    }

    @GetMapping("/register")
    public String redirectLegacyRegistrationUrl() {
        return "redirect:/organization/register";
    }

    @GetMapping("/organization/register")
    public String showRegistrationPage(Model model) {

        if (!registrationEnabled) {
            return "redirect:/login?registrationClosed";
        }

        model.addAttribute(
                "registrationForm",
                new RegistrationForm()
        );

        return "register";
    }

    @PostMapping({"/register", "/organization/register"})
    public String registerOrganizationOwner(
            @Valid
            @ModelAttribute("registrationForm")
            RegistrationForm registrationForm,
            BindingResult bindingResult,
            HttpServletRequest request) {

        if (!registrationEnabled) {
            return "redirect:/login?registrationClosed";
        }

        if (!throttle.tryAcquire(
                "registration",
                request.getRemoteAddr(),
                5,
                Duration.ofHours(1)
        )) {
            bindingResult.reject(
                    "registration.rateLimited",
                    "Too many registration attempts were received. Try again later."
            );
        }

        if (!Objects.equals(
                registrationForm.getPassword(),
                registrationForm.getConfirmPassword()
        )) {
            bindingResult.rejectValue(
                    "confirmPassword",
                    "password.mismatch",
                    "The passwords do not match."
            );
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            registrationService.registerOrganizationOwner(
                    registrationForm
            );
        } catch (IllegalArgumentException exception) {
            bindingResult.reject(
                    "registration.failed",
                    exception.getMessage()
            );
            return "register";
        }

        return "redirect:/login?registered";
    }
}
