package technology.renatus.routepulse.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import technology.renatus.routepulse.auth.dto.ResendVerificationForm;
import technology.renatus.routepulse.auth.service.EmailVerificationService;
import technology.renatus.routepulse.auth.service.EmailVerificationService.VerificationResult;
import technology.renatus.routepulse.shared.security.PublicRequestThrottle;

@Controller
public class EmailVerificationController {

    private final EmailVerificationService
            emailVerificationService;

    private final PublicRequestThrottle throttle;

    public EmailVerificationController(
            EmailVerificationService emailVerificationService,
            PublicRequestThrottle throttle) {

        this.emailVerificationService =
                emailVerificationService;

        this.throttle = throttle;
    }

    @GetMapping("/resend-verification")
    public String showResendVerificationPage(
            Model model) {

        if (!model.containsAttribute(
                "resendVerificationForm"
        )) {
            model.addAttribute(
                    "resendVerificationForm",
                    new ResendVerificationForm()
            );
        }

        return "auth/resend-verification";
    }

    @PostMapping("/resend-verification")
    public String resendVerificationEmail(
            @Valid
            @ModelAttribute("resendVerificationForm")
            ResendVerificationForm form,
            BindingResult bindingResult,
            HttpServletRequest request) {

        if (bindingResult.hasErrors()) {
            return "auth/resend-verification";
        }

        if (throttle.tryAcquire(
                "verification-resend",
                request.getRemoteAddr(),
                5,
                Duration.ofHours(1)
        )) {
            emailVerificationService
                    .resendVerificationEmail(
                            form.getEmail()
                    );
        }

        return "redirect:/resend-verification?requested";
    }

    @GetMapping("/verify-email")
    public String verifyEmail(
            @RequestParam(
                    name = "token",
                    required = false
            )
            String token,
            HttpServletResponse response) {

        response.setHeader(
                "Referrer-Policy",
                "no-referrer"
        );

        VerificationResult result =
                emailVerificationService
                        .verifyEmail(token);

        return switch (result) {

            case VERIFIED ->
                    "redirect:/login?verified";

            case EXPIRED ->
                    "redirect:/login?verificationExpired";

            case ALREADY_USED ->
                    "redirect:/login?verificationUsed";

            case INVALID ->
                    "redirect:/login?verificationError";
        };
    }
}
