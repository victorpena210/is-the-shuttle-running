package technology.renatus.routepulse.regression;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.shared.time.TimestampDisplay;
import technology.renatus.routepulse.support.WebTemplateContext;
import technology.renatus.routepulse.attention.model.AttentionCenter;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliation;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliationStatus;
import technology.renatus.routepulse.assignments.service.DriverAssignmentService;
import technology.renatus.routepulse.assignments.service.ScheduleReconciliationService;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.model.ParkCalendarOverride;
import technology.renatus.routepulse.calendar.model.ParkOperatingDaySnapshot;
import technology.renatus.routepulse.calendar.repository.ParkOperatingDaySnapshotRepository;
import technology.renatus.routepulse.calendar.service.ParkCalendarOverrideService;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.calendar.sixflags.SixFlagsCalendarClient;
import technology.renatus.routepulse.calendar.sixflags.SixFlagsCalendarDate;
import technology.renatus.routepulse.calendar.sixflags.SixFlagsCalendarParser;
import technology.renatus.routepulse.calendar.sixflags.SixFlagsCalendarProvider;
import technology.renatus.routepulse.calendar.sixflags.SixFlagsCalendarResponse;
import technology.renatus.routepulse.calendar.sixflags.SixFlagsOperating;
import technology.renatus.routepulse.calendar.sixflags.SixFlagsOperatingItem;
import technology.renatus.routepulse.calendar.spi.OperatingCalendarProvider;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.dashboard.service.DashboardPlanningService;
import technology.renatus.routepulse.dashboard.view.DashboardModelAssembler;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.DriverService;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.service.RiderStatusService.RiderStatus;
import technology.renatus.routepulse.operations.domain.RiderOperationalStatus;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.organization.service.OrganizationActorService;
import technology.renatus.routepulse.reporting.pilot.model.DriverFairnessRow;
import technology.renatus.routepulse.reporting.pilot.model.PilotAnalyticsReport;
import technology.renatus.routepulse.reporting.workload.model.WorkloadSummary;
import technology.renatus.routepulse.reporting.workload.service.WorkloadCalculator;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.DriverScheduleResult;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.importing.CsvScheduleProvider;
import technology.renatus.routepulse.scheduling.importing.ScheduleProvider;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.scheduling.planning.MorningDrivingPlanningService;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;

/**
 * Behavior checks for the cleanup's risky boundaries. The JUnit wrapper runs these
 * in Maven; main also permits execution against locally available runtime jars.
 * No database, network requests, or production configuration are used.
 */
public final class CleanupRegressionChecks {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 7);
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    public static void main(String[] args) throws Exception {
        workloadAccounting();
        organizationActorIsolation();
        calendarFallbackAndCache();
        dashboardPlanningGate();
        renderDashboardStates();
        renderReportingStates();
        renderServiceHistoryStates();
        timestampDisplayUsesOperationsZone();
        System.out.println("PASS: 8 cleanup regression groups (workload, organization, calendar, planning, dashboard, reporting, service history, timestamps)");
    }

    public static void timestampDisplayUsesOperationsZone() {
        TimestampDisplay display = new TimestampDisplay("America/Chicago");
        equal(
                "Sep 10, 2026 10:00 AM",
                display.format(Instant.parse("2026-09-10T15:00:00Z"), "MMM d, yyyy h:mm a"),
                "summer UTC timestamp display"
        );
        equal(
                "Jan 10, 2026 9:00 AM",
                display.format(Instant.parse("2026-01-10T15:00:00Z"), "MMM d, yyyy h:mm a"),
                "winter UTC timestamp display"
        );
    }

    public static void workloadAccounting() throws Exception {
        Fixture f = fixture();
        WorkloadCalculator calculator = new WorkloadCalculator();
        WorkloadSummary all = calculator.calculate(DATE, DATE, null, f.drivers(), List.of(f.assignment()));
        equal(180L, all.getScheduledMinutes(), "scheduled minutes");
        equal(75L, all.getActualMinutes(), "completed driving minutes");
        equal(1, all.getShiftsDriven(), "two sessions must count as one shift");
        equal(0L, all.getDrivers().get(0).getActualMinutes(), "scheduled driver did not drive");
        equal(0L, all.getDrivers().get(1).getScheduledMinutes(), "covering driver was not scheduled");
        equal(1, all.getDrivers().get(0).getCoveredByAnotherDriver(), "coverage count");
        WorkloadSummary filtered = calculator.calculate(DATE, DATE, 2L, f.drivers(), List.of(f.assignment()));
        equal(1, filtered.getDrivers().size(), "driver filter");
        equal(0L, filtered.getScheduledMinutes(), "filtered scheduled time");
        equal(75L, filtered.getActualMinutes(), "filtered actual time");
        DrivingSession active = DrivingSession.start(f.assignment(), f.drivers().get(1),
                DATE.atTime(11, 0).atZone(ZoneId.of("America/Chicago")).toInstant(), f.actor(), DrivingActionMethod.QR);
        f.assignment().registerDrivingSession(active);
        WorkloadSummary inProgress = calculator.calculate(DATE, DATE, null, f.drivers(), List.of(f.assignment()));
        equal(75L, inProgress.getActualMinutes(), "an open session has no final duration");
        equal(1, inProgress.getShiftsDriven(), "an open extra scan is not another shift");
        equal(0L, calculator.calculate(DATE, DATE, null, List.of(), List.of()).getActualMinutes(), "empty report");
    }

    public static void organizationActorIsolation() throws Exception {
        UserAccount actor = fixture().actor();
        UserAccountRepository users = proxy(UserAccountRepository.class, (name, args) -> Optional.of(actor));
        OrganizationMembershipRepository memberships = proxy(OrganizationMembershipRepository.class,
                (name, args) -> Objects.equals(args[0], 10L));
        OrganizationActorService service = new OrganizationActorService(users, memberships);
        equal(actor, service.loadActor(10L, " manager@example.com "), "organization member");
        expectIllegalState(() -> service.loadActor(20L, "manager@example.com"));
        expectIllegalState(() -> service.loadActor(10L, " "));
    }

    public static void calendarFallbackAndCache() {
        FakeCalendar client = new FakeCalendar();
        Map<LocalDate, ParkOperatingDaySnapshot> snapshots = new HashMap<>();
        ParkOperatingDaySnapshotRepository repository = proxy(ParkOperatingDaySnapshotRepository.class,
                (name, args) -> {
                    if (name.equals("findByParkIdAndOperatingDate")) return Optional.ofNullable(snapshots.get(args[1]));
                    if (name.equals("save")) {
                        ParkOperatingDaySnapshot value = (ParkOperatingDaySnapshot) args[0];
                        snapshots.put(value.getOperatingDate(), value);
                        return value;
                    }
                    throw new AssertionError(name);
                });
        client.response = response(false);
        ParkOperatingDayService service = calendar(client, repository);
        equal(ParkOperatingStatus.OPEN, service.getOperatingDay(DATE).status(), "open calendar");
        equal(LocalTime.of(10, 30), service.getOperatingDay(DATE).openingTime(), "opening time");
        equal(1, client.calls, "monthly calendar cache");
        client.fail = true;
        ParkOperatingDay saved = calendar(client, repository).getOperatingDay(DATE);
        equal("PERSISTED_SNAPSHOT", saved.verificationSource(), "persisted fallback");
        equal(ParkOperatingStatus.UNKNOWN, calendar(client, repository).getOperatingDay(DATE.plusDays(2)).status(), "unknown date");
        client.fail = false;
        client.response = response(true);
        equal(ParkOperatingStatus.CLOSED, calendar(client, repository).getOperatingDay(DATE).status(), "closed calendar");
    }

    public static void dashboardPlanningGate() throws Exception {
        Fixture f = fixture();
        CsvScheduleProvider schedules = new CsvScheduleProvider(null, null, null, null) {
            @Override public List<LocalDate> getAvailableDates(Long organizationId) { return List.of(DATE); }
            @Override public LocalDate getFirstDateOfLatestImport(Long organizationId) { return DATE; }
            @Override public Optional<ScheduleImport> getLatestImportForDate(Long organizationId, LocalDate date) {
                return Optional.of(f.imported());
            }
            @Override public List<ScheduleImport> getRecentImports(Long organizationId) { return List.of(f.imported()); }
            @Override public boolean hasUploadedSchedule(Long organizationId) { return true; }
        };
        int[] assignmentCalls = {0};
        DriverAssignmentService assignments = new DriverAssignmentService(null, null, null, null, null, null, "UTC") {
            @Override public List<DriverAssignment> getOrCreateAssignments(Long organizationId, LocalDate date) {
                assignmentCalls[0]++;
                return List.of(f.assignment());
            }
            @Override public LocalDate getCurrentOperationsDate() { return DATE.plusDays(2); }
        };
        DriverScheduleService driverSchedules = new DriverScheduleService(null, null) {
            @Override public DriverScheduleResult getDriverSchedulesForDate(Long organizationId, LocalDate date) {
                return new DriverScheduleResult(List.of(), f.drivers());
            }
        };
        DriverService drivers = new DriverService(null, null, null) {
            @Override public List<Driver> getActiveDrivers(Long organizationId) { return f.drivers(); }
        };
        TransportationDemandService demandService = new TransportationDemandService(
                null, null, "09:00", "12:00", "17:00", "21:00", 15, 15) {
            @Override public TransportationDemandAnalysis getDemandForDate(Long organizationId, LocalDate date) {
                return demand();
            }
        };
        ScheduleReconciliationService reconciliationService = new ScheduleReconciliationService(null, null) {
            @Override
            public List<ScheduleReconciliation> reconcile(
                    Long organizationId,
                    List<DriverAssignment> existingAssignments,
                    List<DriverSchedule> currentDriverSchedules,
                    TransportationDemandAnalysis transportationDemand,
                    ScheduleImport latestScheduleImport) {
                return List.of();
            }
        };
        for (ParkOperatingStatus status : ParkOperatingStatus.values()) {
            for (boolean override : List.of(false, true)) {
                OperatingCalendarProvider provider = new OperatingCalendarProvider() {
                    @Override public ParkOperatingDay getOperatingDay(LocalDate date) {
                        return new ParkOperatingDay(date, status, null, null, false, "NONE", null);
                    }
                };
                ParkCalendarOverrideService overrideService = new ParkCalendarOverrideService(null, null, null) {
                    @Override
                    public Optional<ParkCalendarOverride> findLatest(Long organizationId, LocalDate operatingDate) {
                        if (!override || status == ParkOperatingStatus.OPEN) {
                            return Optional.empty();
                        }
                        return Optional.of(new ParkCalendarOverride(
                                organizationId, operatingDate, status, ParkOperatingStatus.OPEN,
                                "TEST", null, "Employee event", 1L, "Test Manager",
                                "manager@example.com", LocalDateTime.of(2026, 9, 7, 8, 4).atZone(ZoneId.of("America/Chicago")).toInstant()));
                    }
                };
                DashboardPlanningService service = new DashboardPlanningService(demandService, schedules,
                        driverSchedules, new ParkOperatingDayService(provider), overrideService, assignments, drivers,
                        reconciliationService);
                assignmentCalls[0] = 0;
                DashboardPlan plan = service.getPlan(10L, null);
                equal(DATE, plan.selectedDate(), "latest import date remains the default, including historical dates");
                check(plan.transportationDemand().hasAnyDemand(), "calendar status must not hide schedule demand");
                boolean allowed = status == ParkOperatingStatus.OPEN || (status != ParkOperatingStatus.OPEN && override);
                equal(allowed ? 1 : 0, assignmentCalls[0], "planning assignment gate " + status);
            }
        }
    }

    public static void renderDashboardStates() throws Exception {
        SpringTemplateEngine engine = engine();
        Fixture f = fixture();
        Field sessions = DriverAssignment.class.getDeclaredField("drivingSessions");
        sessions.setAccessible(true);
        sessions.set(f.assignment(), new ArrayList<DrivingSession>());
        List<DriverSchedule> schedules = List.of(new DriverSchedule(1L, "100", "Scheduled Driver", DATE,
                LocalTime.of(9, 0), LocalTime.of(17, 0)));
        for (ParkOperatingStatus status : ParkOperatingStatus.values()) {
            for (boolean override : List.of(false, true)) {
                boolean allowed = status == ParkOperatingStatus.OPEN || (status != ParkOperatingStatus.OPEN && override);
                ParkOperatingDay day = new ParkOperatingDay(DATE, status, LocalTime.of(10, 0),
                        LocalTime.of(21, 0), false, "LIVE_API", null);
                List<ScheduleReconciliation> reconciliations = allowed
                        ? List.of(new ScheduleReconciliation(
                                1L,
                                ShuttlePeriod.MORNING,
                                "Morning",
                                1L,
                                "Scheduled Driver",
                                1L,
                                2L,
                                "9:00 AM–5:00 PM",
                                "12:00 PM–8:00 PM",
                                "9:00 AM–12:00 PM",
                                ScheduleReconciliationStatus.REPLACEMENT_RECOMMENDED,
                                2L,
                                "Covering Driver",
                                "9:00 AM–5:00 PM",
                                "Scheduled Driver no longer fully covers the morning driving window."
                        ))
                        : List.of();
                ParkCalendarOverride calendarOverride = override && status != ParkOperatingStatus.OPEN
                        ? new ParkCalendarOverride(10L, DATE, status, ParkOperatingStatus.OPEN, "TEST",
                                "CALENDAR_TEST", "Employee event", 1L, "Test Manager",
                                "manager@example.com", LocalDateTime.of(2026, 9, 7, 8, 4).atZone(ZoneId.of("America/Chicago")).toInstant())
                        : null;
                DashboardPlan plan = new DashboardPlan(DATE, DATE, List.of(DATE), true, true, List.of(f.imported()),
                        demand(), schedules, List.of(f.drivers().get(1)), f.drivers(),
                        allowed ? List.of(f.assignment()) : List.of(), reconciliations, f.imported(), allowed, null, day,
                        "Calendar status", calendarOverride, calendarOverride != null);
                ExtendedModelMap model = managerModel();
                new DashboardModelAssembler().populate(model, plan);
                String html = render(engine, "manager/dashboard", model);
                check(html.contains("transportation-demand-heading"), "demand must remain available for " + status);
                equal(allowed, html.contains("id=\"driver-coverage-heading\""), "calendar coverage gate " + status);
                if (allowed) {
                    check(html.contains("assign-midday") && html.contains("assign-afternoon"), "all three shift fragments render");
                    check(html.contains("/assignments/manual"), "manual assignment action");
                    check(html.contains("/assignments/1/driver"), "driver-change action");
                    check(html.contains("/assignments/1/accept-schedule-recommendation"),
                            "schedule reconciliation accept action");
                    check(html.contains("/assignments/1/keep-current-after-schedule-change"),
                            "schedule reconciliation keep action");
                }
            }
        }
        ExtendedModelMap empty = managerModel();
        new DashboardModelAssembler().populate(empty, new DashboardPlan(DATE, DATE, List.of(), false,
                false, List.of(), TransportationDemandAnalysis.empty(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null, false, null, null, null, null, false));
        check(render(engine, "manager/dashboard", empty).contains("scheduleFile"), "empty dashboard still permits import");
    }

    public static void renderReportingStates() throws Exception {
        Fixture f = fixture();
        WorkloadCalculator calculator = new WorkloadCalculator();
        SpringTemplateEngine engine = engine();
        PilotAnalyticsReport pilotReport = new PilotAnalyticsReport(
                DATE,
                DATE.plusDays(6),
                true,
                525,
                0,
                1,
                1,
                100.0,
                0,
                0,
                0,
                0,
                0.0,
                0,
                8.75,
                1,
                List.of(new DriverFairnessRow(
                        1L,
                        "Irons, Jody L",
                        525,
                        0,
                        1,
                        0
                )),
                List.of(),
                "RoutePulse planned one assignment this week."
        );
        ExtendedModelMap pilotModel = managerModel();
        pilotModel.addAttribute("report", pilotReport);
        pilotModel.addAttribute("currentWeekStart", DATE);
        String fairnessHtml = render(engine, "fragments/pilot-analytics/fairness", pilotModel);
        check(fairnessHtml.contains("8.8h"), "pilot fairness scheduled hours");
        check(fairnessHtml.contains("-8.8h"), "pilot fairness difference hours");
        check(fairnessHtml.contains("analytics-table__negative"), "pilot fairness difference class");

        for (String view : List.of("summary", "records")) {
            for (Long driverId : new Long[] { null, 1L, 2L }) {
                ExtendedModelMap model = managerModel();
                WorkloadSummary summary = calculator.calculate(DATE, DATE, driverId, f.drivers(), List.of(f.assignment()));
                model.addAttribute("records", List.of(f.assignment()));
                model.addAttribute("workloadSummary", summary);
                model.addAttribute("selectedDriverSummary", driverId == null ? null : summary.getDrivers().get(0));
                model.addAttribute("drivers", f.drivers());
                model.addAttribute("selectedDriverId", driverId);
                model.addAttribute("selectedView", view);
                model.addAttribute("fromDate", DATE);
                model.addAttribute("toDate", DATE);
                model.addAttribute("makeupStatus", Map.of(1L, true));
                model.addAttribute("outcomes", AssignmentOutcome.values());
                String html = render(engine, "manager/driving-records", model);
                if (view.equals("records")) {
                    check(html.contains("/driving-records/1/outcome"), "management review form action");
                    check(html.contains("Scheduled Driver") && html.contains("Covering Driver"), "scheduled and actual driver details");
                } else {
                    check(html.contains("3.00 hrs") || driverId != null, "workload hours");
                }
            }
        }
    }

    public static void renderServiceHistoryStates() throws Exception {
        var calculator = new technology.renatus.routepulse.reporting.history.ServiceReviewCalculator(ZoneId.of("UTC"), 10, 15);
        var span = new technology.renatus.routepulse.reporting.history.ServiceReviewCalculator.Span(
                DATE.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC), DATE.atTime(9, 15).toInstant(java.time.ZoneOffset.UTC));
        for (String calendar : List.of("Open", "Park closed", "Calendar unverified")) {
            var input = new technology.renatus.routepulse.reporting.history.ServiceReviewCalculator.Input(
                    DATE, "morning", "Morning", calendar, "Uploaded schedule", List.of(span),
                    List.of(new technology.renatus.routepulse.reporting.history.ServiceReviewCalculator.Demand(span, 20)),
                    List.of(), List.of(), List.of());
            var row = calculator.calculate(input, null, "actual", span.end());
            var filters = technology.renatus.routepulse.reporting.history.ServiceReviewFilters.resolve(
                    DATE, DATE, null, null, null, null, null, DATE);
            ExtendedModelMap model = managerModel();
            model.addAttribute("drivers", List.of());
            model.addAttribute("selectedView", "review");
            model.addAttribute("fromDate", DATE);
            model.addAttribute("toDate", DATE);
            model.addAttribute("selectedDriverId", null);
            model.addAttribute("filters", filters);
            model.addAttribute("operationsZone", "UTC");
            model.addAttribute("report", technology.renatus.routepulse.reporting.history.ServiceReviewCalculator.summarize(List.of(row), span.end()));
            String html = render(engine(), "manager/service-history", model);
            check(html.contains("Recorded service time") && html.contains("Apply filters"), "service history and filters render");
            check(html.contains(calendar) && html.contains("View time windows and evidence"), "service review state renders");
            model.addAttribute("report", technology.renatus.routepulse.reporting.history.ServiceReviewCalculator.summarize(List.of(), span.end()));
            check(render(engine(), "manager/service-history", model).contains("No periods match these filters"), "empty service review state");
            model.addAttribute("filterError", "Choose a valid period filter.");
            String error = render(engine(), "manager/service-history", model);
            check(error.contains("Choose a valid period filter.") && !error.contains("periods shown"), "invalid filters do not show unfiltered data");
        }
    }

    private static ExtendedModelMap managerModel() {
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute("organizationName", "Test Organization");
        model.addAttribute("organizationSlug", "test");
        model.addAttribute("isOwner", true);
        model.addAttribute("canManageDrivingQr", true);
        model.addAttribute("attentionCenter", new AttentionCenter(List.of()));
        model.addAttribute("currentRiderStatus", new RiderStatus(
                "Test Organization",
                RiderOperationalStatus.NOT_RUNNING,
                "The shuttle is not running.",
                "No active driving session.",
                null,
                null,
                false,
                "stopped",
                List.of(),
                "Wednesday, September 30",
                "America/Chicago"
        ));
        model.addAttribute("timestampDisplay", new TimestampDisplay("America/Chicago"));
        return model;
    }

    private static SpringTemplateEngine engine() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(TEMPLATES.toAbsolutePath() + "/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
                return "";
            }
            @Override protected String processLink(IExpressionContext context, String link) { return link; }
        });
        return engine;
    }

    private static String render(SpringTemplateEngine engine, String template, Map<String, Object> model) {
        return engine.process(template, WebTemplateContext.create(model));
    }

    private static TransportationDemandAnalysis demand() {
        ScheduleProvider provider = (organizationId, date) -> List.of(
                new ShiftRecord("100", "Scheduled Driver", "Operations", "Team Member", DATE,
                        LocalTime.of(9, 0), LocalTime.of(17, 30), "Gate"),
                new ShiftRecord("200", "Covering Driver", "Operations", "Team Member", DATE,
                        LocalTime.of(13, 0), LocalTime.of(20, 0), "Gate"),
                new ShiftRecord("300", "Night Driver", "Operations", "Team Member", DATE,
                        LocalTime.of(2, 0), LocalTime.of(5, 0), "Gate"));
        MorningDrivingPlanningService morning = new MorningDrivingPlanningService(provider, "09:00", "12:00", 15, 15);
        return new TransportationDemandService(provider, morning, "09:00", "12:00", "17:00", "21:00", 15, 15)
                .getDemandForDate(10L, DATE);
    }

    private static Fixture fixture() throws Exception {
        Organization org = new Organization("Test", "Test", "test", OrganizationStatus.ACTIVE,
                DATE.atStartOfDay(ZoneId.of("America/Chicago")).toInstant(),
                DATE.plusDays(30).atStartOfDay(ZoneId.of("America/Chicago")).toInstant());
        id(org, 10L);
        UserAccount actor = new UserAccount("manager@example.com", "unused-test-hash", "Test", "Manager", true, true);
        id(actor, 20L);
        Driver scheduled = new Driver(org, "100", "Scheduled Driver", true);
        Driver covering = new Driver(org, "200", "Covering Driver", true);
        id(scheduled, 1L); id(covering, 2L);
        ScheduleImport imported = new ScheduleImport(org, actor, "abcdef123456", "test.csv", 2,
                DATE, DATE, DATE.toString(), DATE.atTime(8, 0).atZone(ZoneId.of("America/Chicago")).toInstant());
        id(imported, 30L);
        DriverAssignment assignment = DriverAssignment.automatic(DATE, ShuttlePeriod.MORNING, scheduled, imported);
        id(assignment, 1L);
        assignment.updateScheduledTimes(LocalTime.of(9, 0), LocalTime.of(12, 0));
        for (int i = 0; i < 2; i++) {
            Instant start = DATE.atTime(9 + i, 0)
                    .atZone(ZoneId.of("America/Chicago"))
                    .toInstant();
            DrivingSession session = DrivingSession.start(assignment, covering, start, actor, DrivingActionMethod.QR);
            id(session, (long) i + 1);
            session.end(start.plusSeconds((i == 0 ? 30 : 45) * 60L), actor, DrivingActionMethod.QR);
            assignment.registerDrivingSession(session);
        }
        return new Fixture(List.of(scheduled, covering), assignment, actor, imported);
    }

    private static ParkOperatingDayService calendar(FakeCalendar client, ParkOperatingDaySnapshotRepository repository) {
        return new ParkOperatingDayService(new SixFlagsCalendarProvider(client, repository, new SixFlagsCalendarParser(client)));
    }

    private static SixFlagsCalendarResponse response(boolean closed) {
        return new SixFlagsCalendarResponse(914, "SFFT", "Test Park", List.of(
                new SixFlagsCalendarDate("09/07/2026", closed, List.of(new SixFlagsOperating(24, "Main Park",
                        List.of(new SixFlagsOperatingItem("10:30", "22:00", false)))))));
    }

    private static final class FakeCalendar extends SixFlagsCalendarClient {
        private SixFlagsCalendarResponse response;
        private boolean fail;
        private int calls;
        private FakeCalendar() { super(RestClient.builder(), "http://localhost", "http://localhost", 914); }
        @Override public SixFlagsCalendarResponse getCalendarForMonth(LocalDate date) {
            calls++;
            if (fail) throw new RestClientException("Synthetic unavailable calendar");
            return response;
        }
    }

    private record Fixture(List<Driver> drivers, DriverAssignment assignment, UserAccount actor, ScheduleImport imported) {}

    private static void id(Object object, Long value) throws Exception {
        Field field = object.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(object, value);
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (object, method, args) -> handler.apply(method.getName(), args));
    }
    private static void expectIllegalState(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("An invalid organization actor was accepted");
    }
    private static void equal(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
