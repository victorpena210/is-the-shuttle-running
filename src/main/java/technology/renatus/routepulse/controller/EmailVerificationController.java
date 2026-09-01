package technology.renatus.routepulse.controller;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import technology.renatus.routepulse.service.EmailVerificationService;
import technology.renatus.routepulse.service.EmailVerificationService.VerificationResult;

@Controller
public class EmailVerificationController {

    private final EmailVerificationService
            emailVerificationService;

    public EmailVerificationController(
            EmailVerificationService emailVerificationService) {

        this.emailVerificationService =
                emailVerificationService;
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