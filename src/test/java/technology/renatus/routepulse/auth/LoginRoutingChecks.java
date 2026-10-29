package technology.renatus.routepulse.auth;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.*;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.springframework.ui.ExtendedModelMap;
import technology.renatus.routepulse.auth.controller.AuthenticationController;
import technology.renatus.routepulse.shared.security.RoleAwareLoginSuccessHandler;
import technology.renatus.routepulse.support.WebTemplateContext;

/** Runs the actual Spring saved-request handlers without a database or test-library mocks. */
public final class LoginRoutingChecks {
    private static int checks;
    public static void main(String[] args) throws Exception { verify(); }
    public static void verify() throws Exception {
        checks = 0;
        var driver = authentication("DRIVER");
        var original = new SavedRequestAwareAuthenticationSuccessHandler();
        original.setDefaultTargetUrl("/post-login");
        var oldCache = cache("GET", "/app", null, "");
        original.setRequestCache(oldCache);
        equal("https://localhost/app?continue", run(original, driver, ""), "reproduce old manager-page redirect");
        for (String path : List.of("/app", "/driving-records", "/drivers", "/driving-qr", "/organization/invitations", "/driver-tools", "/driver/../app")) {
            var cache = cache("GET", path, "date=2026-10-10", "");
            equal("/post-login", run(new RoleAwareLoginSuccessHandler(cache), driver, ""), "driver leaves manager URL " + path);
            equal(null, cache.saved, "discard inaccessible saved request");
        }
        for (String path : List.of("/driver", "/driver/schedule", "/driver/demand", "/driver/analytics", "/driver/tap", "/driver/scan", "/my-driving-assignments")) {
            var cache = cache("GET", path, "code=tag-code&from=2026-10-10", "");
            String expected = cache.saved.getRedirectUrl();
            equal(expected, run(new RoleAwareLoginSuccessHandler(cache), driver, ""), "keep driver deep link and query " + path);
            check(cache.saved != null, "saved driver request available on return");
        }
        var postCache = cache("POST", "/driver/tap", "code=tag-code", "");
        equal("/post-login", run(new RoleAwareLoginSuccessHandler(postCache), driver, ""), "do not redirect to saved driver POST");
        var contextCache = cache("GET", "/shuttle/driver/tap", "code=tag-code", "/shuttle");
        equal(contextCache.saved.getRedirectUrl(), run(new RoleAwareLoginSuccessHandler(contextCache), driver, "/shuttle"), "context path driver link");
        equal("/post-login", run(new RoleAwareLoginSuccessHandler(cache("GET", "/shuttle/app", null, "/shuttle")), driver, "/shuttle"), "context path manager URL");
        equal("/post-login", run(new RoleAwareLoginSuccessHandler(new Cache()), driver, ""), "normal login uses role router");
        for (Authentication manager : List.of(authentication("OWNER"), authentication("ADMIN"), authentication("DISPATCHER"), authentication("DRIVER", "ADMIN"))) {
            var cache = cache("GET", "/app", "date=2026-10-10", "");
            equal(cache.saved.getRedirectUrl(), run(new RoleAwareLoginSuccessHandler(cache), manager, ""), "manager saved date preserved");
        }
        var controller = new AuthenticationController(90);
        equal("redirect:/driver", controller.routeAfterLogin(driver), "driver home selected");
        equal("redirect:/app", controller.routeAfterLogin(authentication("ADMIN")), "manager home selected");
        verifyLoginPage(controller);
        var resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/"); resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8"); resolver.setCacheable(false);
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        String html = engine.process("error", WebTemplateContext.create(Map.of("status", 403)));
        check(html.contains("href=\"/post-login\""), "error recovery uses the role router");
        check(!html.contains("href=\"/app\""), "error recovery does not repeat the manager-page failure");
        System.out.println("PASS: " + checks + " login-routing checks");
    }

    private static void verifyLoginPage(AuthenticationController controller) throws Exception {
        var anonymous = new AnonymousAuthenticationToken("test-key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        for (Authentication signedOut : Arrays.asList(null, anonymous,
                new UsernamePasswordAuthenticationToken("test@example.com", "unused"))) {
            var model = new ExtendedModelMap();
            equal("auth/login", controller.showLoginPage(signedOut, model), "signed-out visitor gets login form");
            equal(90, model.get("rememberMeDays"), "sign-in duration remains visible");
        }
        for (String role : List.of("DRIVER", "OWNER", "ADMIN", "DISPATCHER")) {
            var full = authentication(role);
            var remembered = new RememberMeAuthenticationToken("test-key", "test@example.com",
                    full.getAuthorities());
            for (Authentication signedIn : List.of(full, remembered)) {
                equal("redirect:/post-login", controller.showLoginPage(signedIn, new ExtendedModelMap()),
                        "already signed-in " + role + " skips login form (" + signedIn.getClass().getSimpleName() + ")");
                equal(role.equals("DRIVER") ? "redirect:/driver" : "redirect:/app",
                        controller.routeAfterLogin(signedIn), "return to the correct home");
            }
        }
    }

    private static String run(SavedRequestAwareAuthenticationSuccessHandler handler, Authentication auth, String context) throws Exception {
        AtomicReference<String> target = new AtomicReference<>();
        handler.setRedirectStrategy((request, response, url) -> target.set(url));
        var response = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) ->
                        method.getName().equals("isCommitted") ? false : null);
        handler.onAuthenticationSuccess(request("POST", context + "/login", null, context), response, auth);
        return target.get();
    }
    private static Authentication authentication(String... roles) {
        return new UsernamePasswordAuthenticationToken("test@example.com", "unused",
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }
    private static Cache cache(String method, String path, String query, String context) {
        var cache = new Cache(); cache.saveRequest(request(method, path, query, context), null); return cache;
    }
    private static HttpServletRequest request(String verb, String path, String query, String context) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getQueryString" -> query;
                    case "getParameterMap" -> Map.of();
                    case "getHeaderNames" -> Collections.emptyEnumeration();
                    case "getLocales" -> Collections.enumeration(List.of(Locale.US));
                    case "getMethod" -> verb;
                    case "getContextPath" -> context;
                    case "getServletPath" -> path.substring(context.length());
                    case "getRequestURI" -> path;
                    case "getRequestURL" -> new StringBuffer("https://localhost" + path);
                    case "getScheme" -> "https";
                    case "getServerName" -> "localhost";
                    case "getServerPort" -> 443;
                    case "isSecure" -> true;
                    default -> null;
                });
    }
    private static final class Cache implements RequestCache {
        SavedRequest saved;
        public void saveRequest(HttpServletRequest request, HttpServletResponse response) { saved = new DefaultSavedRequest(request, "continue"); }
        public SavedRequest getRequest(HttpServletRequest request, HttpServletResponse response) { return saved; }
        public void removeRequest(HttpServletRequest request, HttpServletResponse response) { saved = null; }
        public HttpServletRequest getMatchingRequest(HttpServletRequest request, HttpServletResponse response) { return null; }
    }
    private static void equal(Object expected, Object actual, String label) { check(Objects.equals(expected, actual), label + ": " + actual); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
