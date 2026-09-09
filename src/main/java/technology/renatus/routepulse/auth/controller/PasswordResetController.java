package technology.renatus.routepulse.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import technology.renatus.routepulse.auth.dto.ForgotPasswordForm;
import technology.renatus.routepulse.auth.dto.ResetPasswordForm;
import technology.renatus.routepulse.auth.service.PasswordResetService;
import technology.renatus.routepulse.auth.service.PasswordResetService.ResetResult;
import technology.renatus.routepulse.auth.service.PasswordResetService.TokenStatus;
import technology.renatus.routepulse.shared.security.PublicRequestThrottle;

@Controller
public class PasswordResetController {

    private final PasswordResetService
            passwordResetService;

    private final PublicRequestThrottle throttle;

    public PasswordResetController(
            PasswordResetService passwordResetService,
            PublicRequestThrottle throttle) {

        this.passwordResetService =
                passwordResetService;

        this.throttle = throttle;
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage(
            Model model) {

        if (!model.containsAttribute(
                "forgotPasswordForm"
        )) {
            model.addAttribute(
                    "forgotPasswordForm",
                    new ForgotPasswordForm()
            );
        }

        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestPasswordReset(
            @Valid
            @ModelAttribute("forgotPasswordForm")
            ForgotPasswordForm form,
            BindingResult bindingResult,
            HttpServletRequest request) {

        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        if (throttle.tryAcquire(
                "password-reset",
                request.getRemoteAddr(),
                5,
                Duration.ofHours(1)
        )) {
            passwordResetService.requestPasswordReset(
                    form.getEmail()
            );
        }

        return "redirect:/forgot-password?requested";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(
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

        if (passwordResetService.inspectToken(token)
                != TokenStatus.VALID) {

            return "redirect:/login?passwordResetError";
        }

        ResetPasswordForm form =
                new ResetPasswordForm();

        form.setToken(token);

        model.addAttribute(
                "resetPasswordForm",
                form
        );

        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @Valid
            @ModelAttribute("resetPasswordForm")
            ResetPasswordForm form,
            BindingResult bindingResult,
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
            return redisplayResetForm(form);
        }

        ResetResult result =
                passwordResetService.resetPassword(
                        form.getToken(),
                        form.getPassword(),
                        form.getConfirmPassword()
                );

        if (result == ResetResult.RESET) {
            return "redirect:/login?passwordReset";
        }

        return "redirect:/login?passwordResetError";
    }

    private String redisplayResetForm(
            ResetPasswordForm form) {

        if (passwordResetService
                .inspectToken(form.getToken())
                != TokenStatus.VALID) {

            return "redirect:/login?passwordResetError";
        }

        return "auth/reset-password";
    }
}
