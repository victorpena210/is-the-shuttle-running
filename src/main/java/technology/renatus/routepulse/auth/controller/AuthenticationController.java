package technology.renatus.routepulse.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthenticationController {

    private final boolean registrationEnabled;

    public AuthenticationController(
            @Value(
                    "${app.registration.enabled:true}"
            )
            boolean registrationEnabled) {

        this.registrationEnabled =
                registrationEnabled;
    }

    @GetMapping("/login")
    public String showLoginPage(
            Model model) {

        model.addAttribute(
                "registrationEnabled",
                registrationEnabled
        );

        return "auth/login";
    }

    @GetMapping("/post-login")
    public String routeAfterLogin(
            Authentication authentication) {

        boolean driver =
                authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority
                                        .getAuthority()
                                        .equals(
                                                "ROLE_DRIVER"
                                        )
                        );

        boolean manager =
                authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority
                                        .getAuthority()
                                        .equals(
                                                "ROLE_OWNER"
                                        )
                                || authority
                                        .getAuthority()
                                        .equals(
                                                "ROLE_ADMIN"
                                        )
                                || authority
                                        .getAuthority()
                                        .equals(
                                                "ROLE_DISPATCHER"
                                        )
                        );

        if (driver
                && !manager) {

            return "redirect:/driver";
        }

        return "redirect:/app";
    }
}
