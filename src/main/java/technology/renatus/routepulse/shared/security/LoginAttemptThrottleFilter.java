package technology.renatus.routepulse.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginAttemptThrottleFilter
        extends OncePerRequestFilter {

    private static final int LOGIN_ATTEMPT_LIMIT = 10;
    private static final Duration LOGIN_WINDOW =
            Duration.ofMinutes(15);

    private final PublicRequestThrottle throttle;

    public LoginAttemptThrottleFilter(
            PublicRequestThrottle throttle) {

        this.throttle = throttle;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        boolean loginPost = "POST".equalsIgnoreCase(
                request.getMethod()
        ) && "/login".equals(
                request.getServletPath()
        );

        if (loginPost
                && !throttle.tryAcquire(
                        "login",
                        request.getRemoteAddr(),
                        LOGIN_ATTEMPT_LIMIT,
                        LOGIN_WINDOW
                )) {

            response.sendRedirect(
                    request.getContextPath()
                            + "/login?rateLimited"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
