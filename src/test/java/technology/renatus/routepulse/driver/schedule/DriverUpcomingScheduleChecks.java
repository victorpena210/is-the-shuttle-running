package technology.renatus.routepulse.driver.schedule;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.assignments.domain.*;
import technology.renatus.routepulse.assignments.model.*;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.driver.controller.DriverScheduleController;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;
import technology.renatus.routepulse.support.WebTemplateContext;
import static technology.renatus.routepulse.driver.schedule.DriverUpcomingScheduleService.*;

/** Real service/controller/template checks; repositories reject any write or unscoped read. */
public final class DriverUpcomingScheduleChecks {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static int checks;
    public static void main(String[] args) { verify(); }
    public static void verify() {
        checks = 0;
        equal(new DateRange(TODAY, TODAY.plusDays(27)), DateRange.resolve(null, null, TODAY), "default four weeks");
        equal(new DateRange(TODAY, TODAY), DateRange.resolve(TODAY, TODAY, TODAY), "single date");
        equal(TODAY.plusDays(92), DateRange.resolve(TODAY, TODAY.plusDays(92), TODAY).to(), "93 days inclusive");
        rejects(() -> DateRange.resolve(TODAY, TODAY.plusDays(93), TODAY), "94 days rejected");
        rejects(() -> DateRange.resolve(TODAY, null, TODAY), "missing end rejected");
        rejects(() -> DateRange.resolve(null, TODAY, TODAY), "missing start rejected");
        rejects(() -> DateRange.resolve(TODAY.minusDays(1), TODAY, TODAY), "past start rejected");
        rejects(() -> DateRange.resolve(TODAY.plusDays(1), TODAY, TODAY), "reversed range rejected");

        var organization = id(new Organization("Own", "Own Organization", "own", OrganizationStatus.ACTIVE, null, null), 1L);
        var driver = id(new Driver(organization, "100", "Own Driver", true), 11L);
        var other = id(new Driver(organization, "200", "PRIVATE OTHER", true), 22L);
        var foreignOrg = id(new Organization("Foreign", "PRIVATE FOREIGN", "foreign", OrganizationStatus.ACTIVE, null, null), 2L);
        var foreign = id(new Driver(foreignOrg, "300", "PRIVATE FOREIGN", true), 33L);
        Authentication auth = new UsernamePasswordAuthenticationToken("driver@example.com", "unused");
        var access = new DriverPortalAccessService(null, null, null) {
            @Override public Driver requireCurrentDriver(Authentication actual) {
                if (actual != auth) throw new IllegalStateException("Authentication required");
                return driver;
            }
        };
        List<DriverAssignment> stored = new ArrayList<>();
        var evening = assignment(driver, TODAY.plusDays(2), ShuttlePeriod.AFTERNOON, 19, 21);
        evening.replacePlannedDrivingWindows(List.of(new PlannedDrivingWindow(LocalTime.of(19,0), LocalTime.of(20,0)),
                new PlannedDrivingWindow(LocalTime.of(20,15), LocalTime.of(21,0))));
        stored.add(evening);
        stored.add(assignment(driver, TODAY.plusDays(1), ShuttlePeriod.MIDDAY, 13, 14));
        var noTime = DriverAssignment.automatic(TODAY.plusDays(3), ShuttlePeriod.MORNING, driver);
        stored.add(noTime);
        for (var outcome : List.of(AssignmentOutcome.CANCELLED, AssignmentOutcome.EXCUSED, AssignmentOutcome.COMPLETED_AS_ASSIGNED)) {
            var excluded = assignment(driver, TODAY.plusDays(1), ShuttlePeriod.AFTERNOON, 18, 19);
            excluded.updateDerivedOutcome(outcome); stored.add(excluded);
        }
        stored.add(assignment(driver, TODAY.minusDays(1), ShuttlePeriod.MORNING, 9, 10));
        stored.add(assignment(driver, TODAY.plusDays(28), ShuttlePeriod.MORNING, 9, 10));
        stored.add(assignment(other, TODAY.plusDays(1), ShuttlePeriod.MORNING, 9, 10));
        stored.add(assignment(foreign, TODAY.plusDays(1), ShuttlePeriod.MORNING, 9, 10));
        int[] reads = {0};
        var repository = (DriverAssignmentRepository) Proxy.newProxyInstance(DriverAssignmentRepository.class.getClassLoader(),
                new Class<?>[]{DriverAssignmentRepository.class}, (object, method, values) -> {
                    if (!method.getName().equals("findAllByOrganization_IdAndScheduledDriver_IdAndAssignmentDateBetween"))
                        throw new AssertionError("Unexpected repository operation: " + method.getName());
                    equal(1L, values[0], "organization comes from identity");
                    equal(11L, values[1], "driver comes from identity"); reads[0]++;
                    return stored.stream().filter(a -> a.getOrganization().getId().equals(values[0])
                            && a.getScheduledDriver().getId().equals(values[1])
                            && !a.getAssignmentDate().isBefore((LocalDate) values[2])
                            && !a.getAssignmentDate().isAfter((LocalDate) values[3])).toList();
                });
        var shifts = (UploadedShiftRepository) Proxy.newProxyInstance(UploadedShiftRepository.class.getClassLoader(),
                new Class<?>[]{UploadedShiftRepository.class}, (object, method, values) -> {
                    if (!method.getName().equals("findAvailableDates")) throw new AssertionError("Unexpected shift mutation");
                    equal(1L, values[0], "uploaded dates scoped to organization");
                    return List.of(TODAY.plusDays(1), TODAY.plusDays(1), TODAY.plusDays(28));
                });
        var service = new DriverUpcomingScheduleService(access, repository, shifts, "America/Chicago") {
            @Override public LocalDate today() { return TODAY; }
        };
        var view = service.upcoming(auth, DateRange.resolve(null, null, TODAY));
        equal(3, view.assignments().size(), "only own upcoming active assignments");
        equal("Midday", view.assignments().get(0).period(), "ordered by date");
        equal("Evening", view.assignments().get(1).period(), "evening label");
        equal(2, view.assignments().get(1).windows().size(), "detailed windows retained");
        equal(new TimeWindow(LocalTime.of(20,0), LocalTime.of(20,15)), view.assignments().get(1).breaks().get(0), "break retained");
        equal(0, view.assignments().get(0).breaks().size(), "midday gaps are not protected breaks");
        equal(0, view.assignments().get(2).windows().size(), "unset times supported");
        equal(1, view.uploadedDays(), "available dates deduplicated and range limited");
        rejects(() -> service.upcoming(null, view.dates()), "missing identity rejected");
        var controller = new DriverScheduleController(access, service);
        var model = new ExtendedModelMap();
        equal("driver/schedule", controller.schedule(auth, null, null, model), "schedule route");
        equal("schedule", model.get("driverPage"), "active navigation");
        var resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/"); resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8"); resolver.setCacheable(false);
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        String html = engine.process("driver/schedule", WebTemplateContext.create(model));
        check(html.contains("My schedule") && html.contains("Evening driving") && html.contains("Protected break"), "schedule renders");
        check(html.contains("7:00 PM") && html.contains("Times not set"), "windows and missing time render");
        check(!html.contains("PRIVATE OTHER") && !html.contains("PRIVATE FOREIGN"), "no other driver information");
        check(html.contains("aria-current=\"page\""), "selected navigation rendered");
        for (int days : List.of(0, 2)) {
            model.put("scheduleView", new ScheduleView(view.dates(), List.of(), days));
            String empty = engine.process("driver/schedule", WebTemplateContext.create(model));
            check(empty.contains(days == 0 ? "No work schedule has been uploaded" : "A work schedule is available"), "honest empty state");
        }
        int before = reads[0];
        model = new ExtendedModelMap();
        controller.schedule(auth, TODAY.minusDays(1), TODAY, model);
        equal(before, reads[0], "invalid filter does not query assignments");
        check(model.containsKey("filterError") && !model.containsKey("scheduleView"), "invalid filter model");
        check(engine.process("driver/schedule", WebTemplateContext.create(model)).contains("Choose today or a future start date"), "validation renders");
        System.out.println("PASS: " + checks + " upcoming-schedule checks");
    }
    private static DriverAssignment assignment(Driver driver, LocalDate date, ShuttlePeriod period, int start, int end) {
        var result = DriverAssignment.automatic(date, period, driver);
        result.updateScheduledTimes(LocalTime.of(start,0), LocalTime.of(end,0)); return result;
    }
    private static <T> T id(T value, Long id) {
        try { var f = value.getClass().getDeclaredField("id"); f.setAccessible(true); f.set(value, id); return value; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void equal(Object expected, Object actual, String label) { check(Objects.equals(expected,actual), label + ": " + actual); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static void rejects(Runnable action, String label) {
        checks++; try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError(label);
    }
}
