package technology.renatus.routepulse.driver.insights;

import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.service.ParkCalendarOverrideService;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.importing.CsvScheduleProvider;
import technology.renatus.routepulse.scheduling.importing.ScheduleProvider;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.MorningDrivingPlanningService;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;
import technology.renatus.routepulse.shared.time.TimestampDisplay;
import technology.renatus.routepulse.support.WebTemplateContext;
import static technology.renatus.routepulse.driver.insights.DriverAnalyticsCalculator.*;

/** Runs in Maven and directly against the app's runtime dependencies. */
public final class DriverInsightsChecks {
    private static final ZoneId ZONE = ZoneId.of("America/Chicago");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
    private static int assertions;

    public static void main(String[] args) throws Exception { verify(); }
    public static void verify() throws Exception {
        assertions = 0;
        calculations();
        ranges();
        scopedService();
        rendering();
        System.out.println("PASS: " + assertions + " driver insights checks");
    }

    private static void calculations() {
        var range = new DriverAnalyticsRange(DATE, DATE);
        Instant midnight = DATE.atStartOfDay(ZONE).toInstant();
        Instant now = midnight.plusSeconds(12 * 3600);
        var overnight = new Session(midnight.minusSeconds(1800), midnight.plusSeconds(1800), "Unscheduled", "—", true, "Ended");
        var first = new Session(midnight.plusSeconds(3600), midnight.plusSeconds(5400), "Coverage", "Morning", false, "Paused");
        var resumed = new Session(midnight.plusSeconds(6300), midnight.plusSeconds(7200), "Coverage", "Morning", false, "Ended");
        var active = new Session(now.minusSeconds(120), null, "Unscheduled", "—", true, "Recording in progress");
        var future = new Session(now.plusSeconds(60), null, "Unscheduled", "—", true, "Recording in progress");
        var report = calculate(range, ZONE, now, List.of(overnight, first, resumed, active, future),
                List.of(midnight.minusSeconds(1), midnight, now.minusSeconds(1), now, midnight.plusSeconds(86400)));
        equal(4620L, report.seconds(), "range clips overnight and active time; pause excluded");
        equal(1920L, report.unscheduledSeconds(), "unscheduled subset");
        equal(2L, report.confirmations(), "QR timestamps respect report boundaries");
        equal(4, report.sessions().size(), "future session excluded");
        equal(1L, report.activeDays(), "days with driving");
        equal(1L, report.ongoingSessions(), "active session count");
        equal("1 h 17 min", report.drivingTime(), "duration label");
        equal(report.drivingTime(), report.averagePerActiveDay(), "average uses active days");
        var duplicates = calculate(range, ZONE, now, List.of(first, first, resumed), List.of());
        equal(2700L, duplicates.seconds(), "overlap union");
        equal(0L, duplicates.confirmations(), "optional QR not required");
        var twoDays = calculate(new DriverAnalyticsRange(DATE.minusDays(1), DATE), ZONE, now, List.of(overnight), List.of());
        equal(3600L, twoDays.seconds(), "overnight full duration");
        equal(2L, twoDays.activeDays(), "overnight split days");
        equal(1, twoDays.sessions().size(), "overnight session counted once");
        equal(1800L, twoDays.days().get(0).seconds(), "today part");
        equal(1800L, twoDays.days().get(1).seconds(), "yesterday part");
        equal("30 min", twoDays.averagePerActiveDay(), "average active-day time");
        var empty = calculate(range, ZONE, now, List.of(), List.of());
        equal(false, empty.hasActivity(), "empty activity"); equal("—", empty.averagePerActiveDay(), "no division by zero");
        equal(1, empty.days().size(), "zero day included");
        equal(true, calculate(range, ZONE, now, List.of(), List.of(midnight)).hasActivity(), "QR without session retained");
        equal("<1 min", duration(30), "subminute label"); equal("0 min", duration(0), "zero label");
        for (LocalDate dst : List.of(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1))) {
            Instant start = dst.atStartOfDay(ZONE).toInstant(), end = dst.plusDays(1).atStartOfDay(ZONE).toInstant();
            long expected = dst.getMonthValue() == 3 ? 23 * 3600L : 25 * 3600L;
            var full = new Session(start, end, "Unscheduled", "—", true, "Ended");
            equal(expected, calculate(new DriverAnalyticsRange(dst, dst), ZONE, end, List.of(full), List.of()).seconds(), "DST elapsed duration");
        }
    }

    private static void ranges() {
        equal(DATE.minusDays(29), DriverAnalyticsRange.resolve(null, null, null, DATE).from(), "default last 30 days");
        equal(DATE, DriverAnalyticsRange.resolve(null, null, "today", DATE).from(), "today preset");
        equal(DATE, DriverAnalyticsRange.resolve(null, null, "week", DATE.plusDays(3)).from(), "Monday week start");
        equal(DATE, DriverAnalyticsRange.resolve(null, DATE, "custom", DATE).from(), "single supplied date");
        equal(DATE.minusDays(92), DriverAnalyticsRange.resolve(DATE.minusDays(92), DATE, "custom", DATE).from(), "93 inclusive days accepted");
        rejects(() -> DriverAnalyticsRange.resolve(DATE.minusDays(93), DATE, "custom", DATE));
        rejects(() -> DriverAnalyticsRange.resolve(DATE, DATE.minusDays(1), "custom", DATE));
        rejects(() -> DriverAnalyticsRange.resolve(DATE, DATE.plusDays(1), "custom", DATE));
        rejects(() -> DriverAnalyticsRange.resolve(null, null, "custom", DATE));
        rejects(() -> DriverAnalyticsRange.resolve(null, null, "bad", DATE));
    }

    private static void scopedService() throws Exception {
        var org = new Organization("Test", "Test", "test", OrganizationStatus.ACTIVE, null, null);
        id(org, 10L);
        var driver = new Driver(org, "100", "Own Driver", true); id(driver, 20L);
        var actor = new UserAccount("driver@example.com", "unused", "Own", "Driver", true, true); id(actor, 30L);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated("driver@example.com", "unused", List.of());
        var access = new DriverPortalAccessService(null, null, null) {
            @Override public Driver requireCurrentDriver(Authentication input) { equal(auth, input, "authenticated identity forwarded"); return driver; }
        };
        LocalDate recordedDate = LocalDate.now(ZONE).minusDays(1);
        Instant start = recordedDate.atStartOfDay(ZONE).toInstant();
        var record = DrivingSession.startUnscheduled(org, driver, start, actor, DrivingActionMethod.NFC);
        record.end(start.plusSeconds(60), actor, DrivingActionMethod.NFC);
        var sessions = proxy(DrivingSessionRepository.class, (method, args) -> {
            equal("findPersonalAnalyticsSessions", method, "only personal session query");
            equal(10L, args[0], "session organization"); equal(20L, args[1], "session driver");
            equal(start, args[2], "range timezone"); return List.of(record);
        });
        var confirmations = proxy(PassengerRideConfirmationRepository.class, (method, args) -> {
            equal("findPersonalConfirmationTimes", method, "only personal QR query");
            equal(10L, args[0], "QR organization"); equal(20L, args[1], "QR driver"); return List.of(start);
        });
        var service = new DriverInsightsService(access, sessions, confirmations, null, null, null, null, ZONE.getId());
        equal(60L, service.analytics(auth, new DriverAnalyticsRange(recordedDate, recordedDate)).seconds(), "service maps personal activity");

        boolean[] available = {true};
        var schedules = new CsvScheduleProvider(null, null, null) {
            @Override public List<LocalDate> getAvailableDates(Long organizationId) { equal(10L, organizationId, "demand organization"); return available[0] ? List.of(LocalDate.now(ZONE)) : List.of(); }
            @Override public Optional<ScheduleImport> getLatestImportForDate(Long organizationId, LocalDate date) { return Optional.empty(); }
        };
        var overrides = new ParkCalendarOverrideService(null, null, null) {
            @Override public Optional<technology.renatus.routepulse.calendar.model.ParkCalendarOverride> findLatest(Long orgId, LocalDate date) { equal(10L, orgId, "calendar organization"); return Optional.empty(); }
        };
        var calendar = new ParkOperatingDayService(date -> new ParkOperatingDay(date, ParkOperatingStatus.CLOSED, null, null, false, "TEST", null));
        var demandService = new TransportationDemandService((orgId, date) -> List.of(),
                new MorningDrivingPlanningService((orgId, date) -> List.of(), "09:00", "12:00", 15, 15), "09:00", "12:00", "17:00", "21:00", 15, 15);
        var demandView = new DriverInsightsService(access, null, null, schedules, demandService, calendar, overrides, ZONE.getId());
        equal("Park closed", demandView.todayDemand(auth).calendar(), "closed calendar preserved");
        equal(true, demandView.todayDemand(auth).scheduleAvailable(), "available schedule distinguished");
        available[0] = false;
        equal(false, demandView.todayDemand(auth).scheduleAvailable(), "missing schedule distinguished");
    }

    private static void rendering() {
        var resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/"); resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8"); resolver.setCacheable(false);
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) { return ""; }
            @Override protected String processLink(IExpressionContext context, String link) { return link; }
        });
        Map<String, Object> model = new HashMap<>();
        model.put("organizationName", "Test Organization"); model.put("driverName", "Own Driver");
        model.put("operationsZone", ZONE.getId()); model.put("today", DATE);
        model.put("timestampDisplay", new TimestampDisplay(ZONE.getId())); model.put("driverPage", "analytics");
        model.put("from", DATE); model.put("to", DATE);
        Instant now = DATE.atTime(12, 0).atZone(ZONE).toInstant();
        var range = new DriverAnalyticsRange(DATE, DATE);
        for (List<Session> sessions : List.of(List.<Session>of(), List.of(new Session(now.minusSeconds(600), null, "Coverage", "Morning", false, "Recording in progress")))) {
            model.put("report", calculate(range, ZONE, now, sessions, List.of()));
            String html = engine.process("driver/analytics", WebTemplateContext.create(model));
            check(html.contains("My analytics") && html.contains("Daily activity"), "analytics renders");
            check(html.contains("My driving") && html.contains("My records"), "current driver navigation");
            check(!html.contains("/driving-records") && !html.contains("/service-history"), "no manager report links");
            check(html.contains(sessions.isEmpty() ? "No recorded activity" : "Still recording"), "analytics empty/active state");
        }
        model.remove("report"); model.put("filterError", "Choose a valid date range.");
        String error = engine.process("driver/analytics", WebTemplateContext.create(model));
        check(error.contains("Choose a valid date range.") && !error.contains("Daily activity"), "invalid filters hide results");
        model.put("driverPage", "demand");
        ScheduleProvider provider = (orgId, date) -> List.of(
                new ShiftRecord("100", "PRIVATE EMPLOYEE", "Operations", "Team Member", DATE, LocalTime.of(9, 0), LocalTime.of(17, 30), "Gate"),
                new ShiftRecord("200", "PRIVATE EMPLOYEE", "Operations", "Team Member", DATE, LocalTime.of(2, 0), LocalTime.of(5, 0), "Gate"));
        var morning = new MorningDrivingPlanningService(provider, "09:00", "12:00", 15, 15);
        var demand = new TransportationDemandService(provider, morning, "09:00", "12:00", "17:00", "21:00", 15, 15).getDemandForDate(10L, DATE);
        for (String calendar : List.of("Open", "Manager confirmed open", "Park closed", "Calendar unverified")) {
            model.put("demandView", new DriverInsightsService.DemandView(DATE, true, now, calendar, demand));
            String html = engine.process("driver/demand", WebTemplateContext.create(model));
            check(html.contains(calendar) && html.contains("Busiest operating window") && html.contains("Off-hours movements"), "demand and calendar render");
            check(!html.contains("PRIVATE EMPLOYEE"), "only aggregate demand exposed");
            check(html.contains("My driving") && html.contains("My records"), "current driver navigation");
        }
        model.put("demandView", new DriverInsightsService.DemandView(DATE, false, null, "Open", TransportationDemandAnalysis.empty()));
        String missing = engine.process("driver/demand", WebTemplateContext.create(model));
        check(missing.contains("No schedule available for today") && !missing.contains("Morning Arrivals"), "missing demand renders without false zero totals");
        model.put("demandView", new DriverInsightsService.DemandView(DATE, true, null, "Open", TransportationDemandAnalysis.empty()));
        check(engine.process("driver/demand", WebTemplateContext.create(model)).contains("No demand recorded in this period"), "zero-demand schedule renders");
    }

    private static void id(Object object, Long id) throws Exception {
        var field = object.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(object, id);
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (object, method, args) -> handler.apply(method.getName(), args));
    }
    private static void equal(Object expected, Object actual, String label) { check(Objects.equals(expected, actual), label + ": expected " + expected + ", got " + actual); }
    private static void check(boolean condition, String label) { assertions++; if (!condition) throw new AssertionError(label); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Invalid date range accepted");
    }
}
