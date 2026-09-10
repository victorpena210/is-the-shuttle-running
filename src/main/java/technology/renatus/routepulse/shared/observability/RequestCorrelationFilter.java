package technology.renatus.routepulse.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds an opaque request ID to logs and responses.
 *
 * The value contains no account, employee, or organization information. It is
 * useful when a manager reports an error and the developer needs to match the
 * browser response to the matching Railway log lines.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String RESPONSE_HEADER = "X-RoutePulse-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        request.setAttribute("routePulseRequestId", requestId);
        response.setHeader(RESPONSE_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
