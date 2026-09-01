package technology.renatus.routepulse.controller;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import technology.renatus.routepulse.dataTransferObject.RegistrationForm;
import technology.renatus.routepulse.service.RegistrationService;

@Controller
public class RegistrationController {

    private final RegistrationService
            registrationService;

    public RegistrationController(
            RegistrationService registrationService) {

        this.registrationService =
                registrationService;
    }

    @GetMapping("/register")
    public String showRegistrationPage(
            Model model) {

        model.addAttribute(
                "registrationForm",
                new RegistrationForm()
        );

        return "register";
    }

    @PostMapping("/register")
    public String registerOrganizationOwner(
            @Valid
            @ModelAttribute("registrationForm")
            RegistrationForm registrationForm,
            BindingResult bindingResult) {

        if (!registrationForm
                .getPassword()
                .equals(
                        registrationForm
                                .getConfirmPassword()
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

            registrationService
                    .registerOrganizationOwner(
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