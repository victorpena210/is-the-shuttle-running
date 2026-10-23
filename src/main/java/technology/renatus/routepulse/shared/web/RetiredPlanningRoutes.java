package technology.renatus.routepulse.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Keeps old bookmarks useful and prevents old forms from changing retired planning data. */
@Configuration
public class RetiredPlanningRoutes implements WebMvcConfigurer, HandlerInterceptor {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/upload", "/assignments/**", "/park-calendar/**", "/wardrobe-hours/**",
                "/schedule-updates", "/schedule-updates/**", "/driver/availability", "/driver/availability/**",
                "/driver/schedule", "/driver/demand", "/driver/analytics", "/drivers/lookup",
                "/app/attention", "/service-history", "/pilot-analytics", "/driving-records/*/outcome");
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws java.io.IOException {
        if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            String destination = path.startsWith("/driver/") ? "/driver"
                    : path.equals("/service-history") || path.equals("/pilot-analytics") ? "/driving-records" : "/app";
            response.sendRedirect(request.getContextPath() + destination);
        } else {
            response.sendError(410, "Schedule planning has been retired. Use driving sessions and live status.");
        }
        return false;
    }

}
