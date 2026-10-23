package technology.renatus.routepulse.regression;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.driving.controller.RiderStatusController;
import technology.renatus.routepulse.driving.service.RiderStatusService;
import technology.renatus.routepulse.driving.service.RiderStatusService.RiderStatus;
import technology.renatus.routepulse.operations.domain.RiderOperationalStatus;
import technology.renatus.routepulse.support.WebTemplateContext;

/** Public status must stay read-only and reveal only the status and public driver name. */
public final class PublicRiderStatusChecks {
    private static int checks;

    public static void main(String[] args) { verify(); }

    public static void verify() {
        checks = 0;
        var resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        for (var operationalStatus : RiderOperationalStatus.values()) {
            boolean running = Set.of(RiderOperationalStatus.RUNNING, RiderOperationalStatus.WAITING_AT_PICKUP,
                    RiderOperationalStatus.SERVICE_ENDING_SOON).contains(operationalStatus);
            String driverName = operationalStatus == RiderOperationalStatus.NOT_RUNNING ? null : "Victor";
            var service = new RiderStatusService(null, null, null, null, "America/Chicago") {
                @Override public RiderStatus getStatus(String slug) {
                    check(slug.equals("test"), "controller preserves organization lookup");
                    return new RiderStatus("Test Shuttle", operationalStatus, "Private headline", "Private details",
                            "Private message", driverName, running, "unused", List.of(), "Private date", "Private zone");
                }
            };
            var controller = new RiderStatusController(service);
            var headers = new HashMap<String, String>();
            var response = (HttpServletResponse) Proxy.newProxyInstance(PublicRiderStatusChecks.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class}, (proxy, method, args) -> {
                        // Cookie creation, passenger persistence and other response writes are not allowed.
                        if (!method.getName().equals("setHeader")) throw new AssertionError(method.getName());
                        headers.put((String) args[0], (String) args[1]); return null;
                    });
            var model = new ExtendedModelMap();
            check(controller.showRiderStatus("test", response, model).equals("public/rider-status"), "public template selected");
            check(model.keySet().equals(Set.of("organizationName", "running", "driverName")), "only public fields enter the model");
            check(Boolean.valueOf(running).equals(model.get("running")), "binary status respects current operations");
            check(headers.get("Cache-Control").contains("no-store"), "status is never cached");
            String html = engine.process("public/rider-status", WebTemplateContext.create(model.asMap()));
            check(html.contains(running ? "The shuttle is running." : "The shuttle is not running."), "correct status rendered");
            check(driverName == null ? !html.contains("Driver:") : html.contains("Driver: Victor"), "current driver only");
            check(!html.contains("Private") && !html.contains("schedule") && !html.contains("<form")
                    && !html.contains("confirm-ride") && !html.contains("passenger-qr")
                    && !html.contains("Pickup") && !html.contains("Operational update"), "no schedules, extra details or ride confirmation");
            check(html.contains("http-equiv=\"refresh\"") && html.contains("content=\"20\""), "automatic refresh remains enabled");
            check(controller.confirmPassengerRide().getStatusCode() == HttpStatus.GONE, "old confirmation submission is retired");
            check(controller.getPassengerQrImage().getStatusCode() == HttpStatus.GONE, "old confirmation QR is retired");

            model.addAttribute("driverName", "<script>alert(1)</script>");
            String escaped = engine.process("public/rider-status", WebTemplateContext.create(model.asMap()));
            check(escaped.contains("&lt;script&gt;") && !escaped.contains("<script>alert(1)</script>"), "driver name is escaped");
        }
        var missingService = new RiderStatusService(null, null, null, null, "America/Chicago") {
            @Override public RiderStatus getStatus(String slug) { throw new RiderStatusNotFoundException("Not found"); }
        };
        var response = (HttpServletResponse) Proxy.newProxyInstance(PublicRiderStatusChecks.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (proxy, method, args) -> null);
        try {
            new RiderStatusController(missingService).showRiderStatus("missing", response, new ExtendedModelMap());
            throw new AssertionError("Unknown organization should not claim the shuttle is stopped");
        } catch (ResponseStatusException expected) {
            check(expected.getStatusCode() == HttpStatus.NOT_FOUND, "unknown organizations still return 404");
        }
        System.out.println("PASS: " + checks + " public rider-status checks");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
