package technology.renatus.routepulse.regression;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.MappingMatch;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.dashboard.controller.DashboardController;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.DriverService;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.domain.DrivingSessionEndReason;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.reporting.workload.controller.DrivingRecordController;
import technology.renatus.routepulse.reporting.workload.service.ActualDrivingRecordService;
import technology.renatus.routepulse.shared.web.RetiredPlanningRoutes;
import technology.renatus.routepulse.support.WebTemplateContext;

/** Runs with or without JUnit; no database or planning services are needed. */
public final class DrivingHistoryChecks {
    private static int checks;

    public static void main(String[] args) throws Exception { verify(); }

    public static void verify() throws Exception {
        checks = 0;
        historyAndTemplates();
        retiredRoutes();
        System.out.println("PASS: " + checks + " driving-history checks");
    }

    private static void historyAndTemplates() throws Exception {
        var date = LocalDate.of(2026, 10, 23);
        var org = new Organization("Test", "Test Shuttle", "test", OrganizationStatus.ACTIVE, null, null);
        var driver = new Driver(org, "100", "Test Driver", true);
        setId(org, 1L); setId(driver, 2L);
        var user = new UserAccount("driver@example.com", "unused", "Test", "Driver", true, true);
        var assignment = DriverAssignment.automatic(date, ShuttlePeriod.MORNING, driver);
        var oldRun = DrivingSession.start(assignment, driver, Instant.parse("2026-10-23T14:00:00Z"), user, DrivingActionMethod.NFC);
        oldRun.end(Instant.parse("2026-10-23T14:30:00Z"), user, DrivingActionMethod.NFC, DrivingSessionEndReason.PAUSE);
        var run = DrivingSession.startUnscheduled(org, driver, Instant.parse("2026-10-23T14:45:00Z"), user, DrivingActionMethod.QR);
        run.end(Instant.parse("2026-10-23T15:30:00Z"), user, DrivingActionMethod.QR);
        var queries = new int[1];
        var repository = (DrivingSessionRepository) Proxy.newProxyInstance(DrivingHistoryChecks.class.getClassLoader(),
                new Class<?>[] {DrivingSessionRepository.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("findOverlappingForServiceReview")) throw new AssertionError(method.getName());
                    equal(1L, args[0], "history query is scoped to the signed-in organization");
                    equal(Instant.parse("2026-10-23T05:00:00Z"), args[1], "local start boundary");
                    equal(Instant.parse("2026-10-24T05:00:00Z"), args[2], "local end boundary");
                    queries[0]++;
                    return List.of(oldRun, run);
                });
        var drivers = new DriverService(null, null, null) {
            @Override public List<Driver> getAllDrivers(Long id) {
                equal(1L, id, "driver list is organization-scoped"); return List.of(driver);
            }
            @Override public Driver getDriver(Long orgId, Long driverId) {
                if (!Objects.equals(orgId, 1L) || !Objects.equals(driverId, 2L)) throw new IllegalArgumentException("Driver was not found.");
                return driver;
            }
        };
        var records = new ActualDrivingRecordService(repository, drivers, "America/Chicago");
        var organizations = new CurrentOrganizationService(null, null) {
            @Override public Organization requireOrganization(Authentication auth) { return org; }
        };
        var controller = new DrivingRecordController(records, drivers, null, organizations);
        var model = new ExtendedModelMap();
        equal("manager/driving-records", controller.showDrivingRecords(null, date, date, 2L, model), "history is the manager home view");
        var report = (ActualDrivingRecordService.Report) model.get("report");
        equal(75L, report.minutes(), "old and new runs count actual time, excluding the break");
        equal(2, report.rows().size(), "assignment-linked history remains visible");
        equal(45L, report.rows().get(0).minutesInRange(), "newest driving segment appears first");
        equal(75L, report.drivers().get(0).minutes(), "driver total includes both runs");
        var mapping = DrivingRecordController.class.getMethod("showDrivingRecords", Authentication.class,
                LocalDate.class, LocalDate.class, Long.class, Model.class).getAnnotation(GetMapping.class);
        check(List.of(mapping.value()).containsAll(List.of("/app", "/driving-records")), "home and bookmarked records URL share the log");
        var liveMapping = DashboardController.class.getMethod("showDashboard", Authentication.class, Model.class).getAnnotation(GetMapping.class);
        equal(List.of("/app/live-status"), List.of(liveMapping.value()), "live controls have a separate route");

        var templateModel = new HashMap<String, Object>(model.asMap());
        String html = render(templateModel);
        check(html.contains("Driving history") && html.contains("Test Driver") && html.contains("75"), "history renders dates, driver and totals");
        check(html.contains("/app/live-status") && !html.contains("/assignments"), "history links to live status without assignment controls");
        templateModel.put("report", new ActualDrivingRecordService.Report(List.of(), List.of(), 0, 0));
        check(render(templateModel).contains("No driving sessions match these filters"), "empty history renders successfully");

        var invalid = new ExtendedModelMap();
        controller.showDrivingRecords(null, date.plusDays(1), date, null, invalid);
        check(invalid.containsAttribute("filterError") && !invalid.containsAttribute("report"), "invalid dates show an error instead of querying");
        templateModel.remove("report"); templateModel.put("filterError", invalid.get("filterError"));
        check(render(templateModel).contains("Choose an end date"), "filter errors render without a report");
        var foreign = new ExtendedModelMap();
        controller.showDrivingRecords(null, date, date, 99L, foreign);
        check(foreign.containsAttribute("filterError") && !foreign.containsAttribute("report"), "foreign driver filter does not expose history");
        equal(1, queries[0], "invalid filters never reach the history repository");
        try {
            controller.reviewOutcome(); throw new AssertionError("Old assignment review should fail");
        } catch (ResponseStatusException expected) {
            equal(410, expected.getStatusCode().value(), "old outcome form cannot create make-up priority");
        }
    }

    private static void retiredRoutes() throws Exception {
        var retirement = new RetiredPlanningRoutes();
        var registry = new InterceptorRegistry() {
            List<Object> registered() { return getInterceptors(); }
        };
        retirement.addInterceptors(registry);
        var interceptor = (MappedInterceptor) registry.registered().get(0);
        for (String path : List.of("/upload", "/assignments/manual", "/assignments/confirm-schedule",
                "/park-calendar/refresh", "/wardrobe-hours/day", "/schedule-updates",
                "/driver/availability", "/driver/availability/confirm", "/driver/schedule",
                "/driving-records/12/outcome")) {
            var result = new HashMap<String, Object>();
            var request = request("POST", path);
            ServletRequestPathUtils.parseAndCache(request);
            check(interceptor.matches(request), "retired route is registered: " + path);
            equal(false, retirement.preHandle(request, response(result), new Object()), "retired write stops before its handler");
            equal(410, result.get("status"), "retired write returns Gone");
        }
        for (String path : List.of("/app", "/app/live-status", "/driving-records", "/driver/scan", "/driver/tap", "/drivers")) {
            var request = request("GET", path); ServletRequestPathUtils.parseAndCache(request);
            check(!interceptor.matches(request), "active route remains available: " + path);
        }
        var result = new HashMap<String, Object>();
        retirement.preHandle(request("GET", "/service-history"), response(result), new Object());
        equal("/driving-records", result.get("redirect"), "old history bookmark opens the log");
    }

    private static HttpServletRequest request(String method, String path) {
        var attributes = new HashMap<String, Object>();
        return (HttpServletRequest) Proxy.newProxyInstance(DrivingHistoryChecks.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class},
                (proxy, called, args) -> switch (called.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> path;
                    case "getContextPath", "getServletPath" -> "";
                    case "getPathInfo" -> null;
                    case "getHttpServletMapping" -> new HttpServletMapping() {
                        public String getMatchValue() { return ""; }
                        public String getPattern() { return "/"; }
                        public String getServletName() { return "dispatcher"; }
                        public MappingMatch getMappingMatch() { return MappingMatch.DEFAULT; }
                    };
                    case "getAttribute" -> attributes.get(args[0]);
                    case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
                    default -> throw new AssertionError(called.getName());
                });
    }

    private static HttpServletResponse response(Map<String, Object> result) {
        return (HttpServletResponse) Proxy.newProxyInstance(DrivingHistoryChecks.class.getClassLoader(), new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendRedirect")) result.put("redirect", args[0]);
                    else if (method.getName().equals("sendError")) result.put("status", args[0]);
                    else throw new AssertionError(method.getName());
                    return null;
                });
    }

    private static String render(Map<String, Object> model) {
        var resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        return engine.process("manager/driving-records", WebTemplateContext.create(model));
    }

    private static void setId(Object object, Long id) throws Exception {
        var field = object.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(object, id);
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual, String message) { check(Objects.equals(expected, actual), message + ": " + actual); }
}
