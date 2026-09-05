package technology.renatus.routepulse.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ManagerNavigationModelAdvice {

    @ModelAttribute("isOwner")
    public boolean isOwner(
            Authentication authentication) {

        return hasAnyRole(
                authentication,
                "ROLE_OWNER"
        );
    }

    @ModelAttribute("canManageDrivingQr")
    public boolean canManageDrivingQr(
            Authentication authentication) {

        return hasAnyRole(
                authentication,
                "ROLE_OWNER",
                "ROLE_ADMIN"
        );
    }

    private boolean hasAnyRole(
            Authentication authentication,
            String... roles) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication
                .getAuthorities()
                .stream()
                .anyMatch(authority -> {

                    String granted =
                            authority.getAuthority();

                    for (String role : roles) {
                        if (role.equals(granted)) {
                            return true;
                        }
                    }

                    return false;
                });
    }
}