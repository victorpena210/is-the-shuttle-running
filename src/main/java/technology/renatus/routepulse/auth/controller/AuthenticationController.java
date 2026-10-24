package technology.renatus.routepulse.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthenticationController {

    private final int rememberMeDays;

    public AuthenticationController(
            @Value("${app.security.remember-me.days:90}") int rememberMeDays) {

        this.rememberMeDays = rememberMeDays;
    }

    @GetMapping("/login")
    public String showLoginPage(
            Authentication authentication,
            Model model) {

        // A bookmark or home-screen shortcut may still point at /login.
        // Both session and remember-me authentication should resume the app.
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/post-login";
        }

        model.addAttribute("rememberMeDays", rememberMeDays);

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
