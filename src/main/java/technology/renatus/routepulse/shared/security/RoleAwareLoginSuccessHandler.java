package technology.renatus.routepulse.shared.security;

import java.io.IOException;
import java.net.URI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/** Keeps driver deep links while discarding saved destinations reserved for managers. */
public final class RoleAwareLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    private final RequestCache requests;

    public RoleAwareLoginSuccessHandler(RequestCache requests) {
        this.requests = requests;
        setRequestCache(requests);
        setDefaultTargetUrl("/post-login");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws ServletException, IOException {
        SavedRequest saved = requests.getRequest(request, response);
        boolean driver = hasRole(authentication, "DRIVER");
        boolean manager = hasRole(authentication, "OWNER") || hasRole(authentication, "ADMIN")
                || hasRole(authentication, "DISPATCHER");
        if (driver && !manager && saved != null && !isDriverGet(saved, request.getContextPath())) {
            requests.removeRequest(request, response);
        }
        // With no eligible saved URL, /post-login selects the account's normal home.
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private static boolean isDriverGet(SavedRequest saved, String contextPath) {
        if (!"GET".equals(saved.getMethod())) return false;
        try {
            String path = URI.create(saved.getRedirectUrl()).normalize().getRawPath();
            String portal = contextPath + "/driver";
            return portal.equals(path) || (path != null && path.startsWith(portal + "/"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
