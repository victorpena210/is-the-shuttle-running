package technology.renatus.routepulse.driving.service;

import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.assignments.domain.*;
import technology.renatus.routepulse.assignments.model.*;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.attention.model.AttentionItem;
import technology.renatus.routepulse.attention.service.checks.CoverageAttentionChecker;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.calendar.domain.*;
import technology.renatus.routepulse.dashboard.model.*;
import technology.renatus.routepulse.dashboard.view.DashboardModelAssembler;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.*;
import technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy;
import technology.renatus.routepulse.operations.domain.*;
import technology.renatus.routepulse.operations.model.OperationalStatusUpdate;
import technology.renatus.routepulse.operations.service.*;
import technology.renatus.routepulse.organization.domain.*;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.organization.repository.*;
import technology.renatus.routepulse.scheduling.domain.*;
import technology.renatus.routepulse.shared.time.TimestampDisplay;
import technology.renatus.routepulse.support.WebTemplateContext;
import technology.renatus.routepulse.wardrobe.WardrobeHoursService;

/** Runs without a database; the JUnit wrapper includes these checks in Maven. */
public final class ReliabilityChecks {
    private static int checks;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 9);
    private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z"); // Friday 10 PM CDT
    public static void main(String[] args) { verify(); }
    public static void verify() {
        checks = 0;
        coverage(); freshness(); statusAndTemplates(); authorization();
        System.out.println("PASS: " + checks + " reliability checks (coverage, freshness, rider status, templates, authorization)");
    }

    private static void coverage() {
        Fixture f = new Fixture();
        var a = assignment(f.driver, ShuttlePeriod.AFTERNOON, "19:00", "23:00");
        var plan = plan(f, List.of(a), List.of(window("19:00", "19:15"), window("23:30", "23:45")));
        var assessment = CoverageAssessment.assess(plan, ShuttlePeriod.AFTERNOON);
        equal("PARTIAL", assessment.state(), "11 PM assignment does not cover 11:30 demand");
        equal(List.of("11:30 PM–11:45 PM"), assessment.gaps(), "exact late gap");
        var model = new ExtendedModelMap(); new DashboardModelAssembler().populate(model, plan);
        equal(0, model.get("coveredShiftCount"), "headline excludes partial periods");
        List<AttentionItem> alerts = new ArrayList<>(); new CoverageAttentionChecker().addAlerts(plan, alerts);
        check(alerts.stream().anyMatch(i -> i.detail().contains("11:30 PM")), "gap reaches attention center");
        var b = assignment(f.other, ShuttlePeriod.MIDDAY, "22:30", "23:59");
        equal(true, CoverageAssessment.assess(plan(f, List.of(a,b), plan.eveningDrivingDemand().getWindows()), ShuttlePeriod.AFTERNOON).fullyCovered(),
                "union of different-driver assignments across labels covers demand");
        for (var outcome : List.of(AssignmentOutcome.CANCELLED, AssignmentOutcome.EXCUSED, AssignmentOutcome.MISSED)) {
            b.updateDerivedOutcome(outcome);
            equal(false, CoverageAssessment.assess(plan(f, List.of(a,b), plan.eveningDrivingDemand().getWindows()), ShuttlePeriod.AFTERNOON).fullyCovered(),
                    outcome + " is excluded");
        }
        a.updateScheduledTimes(LocalTime.of(19,0), LocalTime.of(23,59));
        a.replacePlannedDrivingWindows(List.of(new PlannedDrivingWindow(LocalTime.of(19,0),LocalTime.of(20,0)),
                new PlannedDrivingWindow(LocalTime.of(20,15),LocalTime.of(23,59))));
        equal(List.of("8:00 PM–8:15 PM"), CoverageAssessment.assess(plan(f,List.of(a),List.of(window("20:00","20:15"))),ShuttlePeriod.AFTERNOON).gaps(),
                "protected break does not claim coverage");
        equal(true, CoverageAssessment.assess(plan(f,List.of(a),List.of(window("19:00","20:00"),window("20:15","20:30"))),ShuttlePeriod.AFTERNOON).fullyCovered(),
                "demand-free break is not a gap");
        f.driver.setActive(false);
        equal(false, CoverageAssessment.assess(plan(f,List.of(a),List.of(window("19:00","19:15"))),ShuttlePeriod.AFTERNOON).fullyCovered(), "inactive driver is excluded");
        f.driver.setActive(true);
        var invalid = assignment(f.driver, ShuttlePeriod.AFTERNOON, "18:00", "23:59");
        equal("REVIEW", CoverageAssessment.assess(plan(f,List.of(invalid),List.of(window("19:00","19:15"))),ShuttlePeriod.AFTERNOON).state(), "assignment outside uploaded work shift needs review");
        a.updateScheduledTimes(LocalTime.of(23,30),LocalTime.of(23,59));
        equal(true,CoverageAssessment.assess(plan(f,List.of(a),List.of(window("23:30","23:59")),LocalTime.MIDNIGHT),ShuttlePeriod.AFTERNOON).fullyCovered(),"midnight clock-out is next-day end, not zero coverage");
        a.updateScheduledTimes(LocalTime.of(19,0),LocalTime.of(23,59));
        var duplicate = assignment(f.driver,ShuttlePeriod.MIDDAY,"22:00","23:00");
        equal("REVIEW",CoverageAssessment.assess(plan(f,List.of(a,duplicate),List.of(window("22:15","22:30")),LocalTime.MIDNIGHT),ShuttlePeriod.AFTERNOON).state(),"same-driver overlapping duties need review");
        a.updateScheduledTimes(LocalTime.of(19,0),LocalTime.of(23,40));
        equal(List.of("11:40 PM–11:45 PM"),CoverageAssessment.assess(plan(f,List.of(a),List.of(window("23:30","23:45"))),ShuttlePeriod.AFTERNOON).gaps(),"partial demand bucket is clipped to exact minute");
    }

    private static void freshness() {
        Fixture f = new Fixture();
        var policy = new ServiceFreshnessPolicy();
        var a = assignment(f.driver,ShuttlePeriod.AFTERNOON,"19:00","23:00");
        var session = DrivingSession.start(a,f.driver,NOW.minusSeconds(3600),f.user,DrivingActionMethod.MANAGER);
        equal(true, policy.isFresh(session,NOW), "recent session is fresh");
        equal(true, policy.isFresh(session,NOW.plusSeconds(3600)), "two-hour boundary does not expire service");
        session.confirmRunning(NOW,f.user);
        equal(null, policy.confirmationDueAt(session), "no scheduled-end confirmation deadline");
        equal(true,policy.isFresh(session,NOW.plusSeconds(86400)),"open session remains active beyond scheduled end");
        session.confirmRunning(Instant.parse("2026-10-10T04:10:00Z"),f.user);
        equal(true,policy.isFresh(session,Instant.parse("2026-10-10T04:30:00Z")),"explicit late-service confirmation extends service");
        var overnight = assignment(f.driver,ShuttlePeriod.AFTERNOON,"23:00","01:00");
        var nightSession = DrivingSession.start(overnight,f.driver,Instant.parse("2026-10-10T04:30:00Z"),f.user,DrivingActionMethod.MANAGER);
        equal(null,policy.confirmationDueAt(nightSession),"overnight sessions have no confirmation deadline");
        var unscheduled = DrivingSession.startUnscheduled(f.org,f.driver,NOW.minusSeconds(7200),f.user,DrivingActionMethod.MANAGER);
        equal(true,policy.isFresh(unscheduled,NOW),"unscheduled sessions stay active");
        unscheduled.end(NOW,f.user,DrivingActionMethod.DRIVER);
        equal(false,policy.isFresh(unscheduled,NOW),"explicit end stops service");
        equal(false,policy.isFresh(null,NOW),"missing session cannot claim running");
        var expired = new OperationalStatusUpdate(1L,OperationalStatusOverride.TEMPORARILY_PAUSED,"Old",2L,"Manager","test@example.com",NOW.minusSeconds(7200));
        equal(true,expired.isExpired(NOW),"legacy override expires without migration backfill");
        equal(false,expired.isExpired(NOW.minusSeconds(1)),"expiry boundary exclusive");
    }

    private static void statusAndTemplates() {
        Fixture f = new Fixture();
        f.active = DrivingSession.startUnscheduled(f.org,f.driver,NOW.minusSeconds(10800),f.user,DrivingActionMethod.MANAGER); id(f.active,10L);
        var service = f.riderService();
        var stale = service.getStatus("test",NOW);
        equal(RiderOperationalStatus.RUNNING,stale.operationalStatus(),"open session keeps running after three hours");
        equal(true,stale.confirmationAvailable(),"passenger confirmation stays available for active service");
        equal(null,stale.confirmationDueAt(),"rider status has no check-in deadline");
        equal(f.active.getStartedAt(),stale.lastConfirmedAt(),"page refresh does not advance confirmation");
        service.getStatus("test",NOW.plusSeconds(20));
        equal(f.active.getStartedAt(),f.active.getLastConfirmedAt(),"GET is read only");
        f.active.confirmRunning(NOW,f.user);
        equal(RiderOperationalStatus.RUNNING,service.getStatus("test",NOW).operationalStatus(),"fresh confirmation resumes accurate status");
        f.update = update(OperationalStatusOverride.TEMPORARILY_PAUSED,NOW.minusSeconds(60));
        equal(RiderOperationalStatus.TEMPORARILY_PAUSED,service.getStatus("test",NOW).operationalStatus(),"live manager pause wins");
        f.update = update(OperationalStatusOverride.TEMPORARILY_PAUSED,NOW.minusSeconds(7300));
        f.active = DrivingSession.startUnscheduled(f.org,f.driver,NOW.minusSeconds(3600),f.user,DrivingActionMethod.MANAGER); id(f.active,11L);
        equal(RiderOperationalStatus.RUNNING,service.getStatus("test",NOW).operationalStatus(),"expired override returns to active session without check-in");
        f.active.confirmRunning(NOW,f.user);
        equal(RiderOperationalStatus.RUNNING,service.getStatus("test",NOW).operationalStatus(),"new evidence supersedes expired override");
        f.update = update(OperationalStatusOverride.SERVICE_ENDING_SOON,NOW.minusSeconds(60));
        f.active = null;
        equal(RiderOperationalStatus.CONFIRMATION_NEEDED,service.getStatus("test",NOW).operationalStatus(),"ending-soon override cannot invent active service");
        f.update = null;
        equal(RiderOperationalStatus.NOT_RUNNING,service.getStatus("test",NOW).operationalStatus(),"no active run means not running");
        rejects(() -> service.getStatus("other",NOW),"public lookup is organization-scoped");

        f.org.updateRiderInformation("Wait at gate <script>alert(1)</script>","Dispatch: 555-0100");
        f.active = DrivingSession.startUnscheduled(f.org,f.driver,NOW.minusSeconds(60),f.user,DrivingActionMethod.MANAGER); id(f.active,12L);
        f.assignments = List.of(assignment(f.driver,ShuttlePeriod.AFTERNOON,"23:00","23:59"));
        var running = service.getStatus("test",NOW);
        check(running.nextPlannedWindow()==null,"old assignments do not publish planned service");
        var engine = engine();
        var model = new HashMap<String,Object>();
        for (var status : List.of(running,stale)) {
            model.put("organizationName", status.organizationName());
            model.put("running", status.running());
            model.put("driverName", status.driverName());
            String html = engine.process("public/rider-status",WebTemplateContext.create(model));
            check(html.contains("The shuttle is running.") && html.contains("Driver: " + status.driverName()),
                    "public page shows running and the current driver");
            check(!html.contains("confirm-ride") && !html.contains("Confirm my ride") && !html.contains("<form"),
                    "public page has no passenger confirmation");
            check(!html.contains("pickup-heading") && !html.contains("Last operational confirmation:")
                    && !html.contains("Wait at gate") && !html.contains("Dispatch:"),
                    "public page omits pickup, confirmation and operational details");
        }
        f.active = DrivingSession.startUnscheduled(f.org,f.driver,Instant.now().minusSeconds(10800),f.user,DrivingActionMethod.MANAGER); id(f.active,13L);
        var passenger = new PassengerRideService(f.organizations(),f.sessions(),proxy(PassengerRideConfirmationRepository.class,(m,a)-> {
            if (m.startsWith("exists")) return false;
            if (m.equals("saveAndFlush")) return a[0];
            throw new AssertionError("Unexpected passenger repository call: " + m);
        }),service,30);
        equal(PassengerRideService.ConfirmationResult.RECORDED,passenger.confirmRide("test","device"),"ride POST works after three hours without driver check-in");
        f.active = null;
        equal(PassengerRideService.ConfirmationResult.NO_ACTIVE_SHUTTLE,passenger.confirmRide("test","device"),"ride POST requires an active session");
    }

    private static void authorization() {
        Fixture f = new Fixture();
        f.active = DrivingSession.startUnscheduled(f.org,f.driver,Instant.now().minusSeconds(3600),f.user,DrivingActionMethod.MANAGER); id(f.active,10L);
        var users = proxy(UserAccountRepository.class,(m,a)->Optional.of(f.user));
        var memberships = proxy(OrganizationMembershipRepository.class,(m,a)-> ((Long)a[0])==1L ? Optional.of(new OrganizationMembership(f.org,f.user,f.role)) : Optional.empty());
        var sessions = new DrivingSessionService(null,f.sessions(),null,f.organizations()) {
            @Override public DrivingSession endSession(DriverAssignment a,DrivingSession s,UserAccount user,Instant at,DrivingActionMethod method) { s.end(at,user,method); return s; }
        };
        var service = new ServiceConfirmationService(users,memberships,f.organizations(),sessions);
        f.role = OrganizationRole.VIEWER;
        rejects(()->service.confirm(1L,10L,"test@example.com",false),"viewer cannot confirm");
        f.role = OrganizationRole.DRIVER;
        rejects(()->service.updateRiderInformation(1L,"Gate","Desk","test@example.com"),"driver cannot change public instructions");
        rejects(()->service.confirm(1L,10L,"test@example.com",false),"driver cannot use manager action");
        service.confirm(1L,10L,"test@example.com",true); checks++;
        f.driver.linkUser(f.user);
        f.active = DrivingSession.startUnscheduled(f.org,f.other,Instant.now().minusSeconds(3600),f.user,DrivingActionMethod.MANAGER); id(f.active,11L);
        rejects(()->service.confirm(1L,11L,"test@example.com",true),"driver cannot confirm somebody else's session");
        f.role = OrganizationRole.ADMIN;
        rejects(()->service.confirm(2L,11L,"test@example.com",false),"cross-organization write rejected");
        rejects(()->service.confirm(1L,10L,"test@example.com",false),"stale session form rejected");
        rejects(()->service.close(1L,11L,Instant.now().plusSeconds(100),"test@example.com"),"future end rejected");
        rejects(()->service.close(1L,11L,Instant.now().minusSeconds(7200),"test@example.com"),"end before start rejected");
        service.close(1L,11L,Instant.now().minusSeconds(30),"test@example.com");
        equal(false,f.active.isActive(),"manager closes session at actual time");
        service.updateRiderInformation(1L,"  Gate A  ","Desk","test@example.com");
        equal("Gate A",f.org.getRiderPickupInstructions(),"manager saves organization information");
        rejects(()->service.updateRiderInformation(1L,"x".repeat(1501),"Desk","test@example.com"),"oversized pickup text rejected");
        var updates = proxy(technology.renatus.routepulse.operations.repository.OperationalStatusUpdateRepository.class,(m,a)->a[0]);
        var operations = new OperationalStatusService(updates,users,memberships);
        var temporary = operations.update(1L,OperationalStatusOverride.TEMPORARILY_PAUSED,"Break","test@example.com",30);
        equal(1800L,Duration.between(temporary.getUpdatedAt(),temporary.getExpiresAt()).getSeconds(),"selected override duration is persisted");
        rejects(()->operations.update(1L,OperationalStatusOverride.DELAYED,null,"test@example.com",721),"unbounded override rejected");
        rejects(()->operations.update(1L,OperationalStatusOverride.DELAYED,null,"test@example.com",0),"zero-duration override rejected");
    }

    private static DashboardPlan plan(Fixture f,List<DriverAssignment> assignments,List<TransportationDemandWindow> windows) {
        return plan(f,assignments,windows,LocalTime.of(23,59));
    }
    private static DashboardPlan plan(Fixture f,List<DriverAssignment> assignments,List<TransportationDemandWindow> windows,LocalTime shiftEnd) {
        var empty = TransportationDemandAnalysis.empty();
        var demand = new TransportationDemandPeriod("evening","Evening","Evening","","","","",windows,LocalTime.of(19,0),LocalTime.of(23,59),windows.size(),null,null,0);
        return new DashboardPlan(DAY,DAY,List.of(DAY),true,true,List.of(),
                new TransportationDemandAnalysis(empty.getMorning(),empty.getMidday(),demand,List.of(),0,null,null,0,null),
                List.of(new DriverSchedule(3L,"A","Test Driver",DAY,LocalTime.of(19,0),shiftEnd),
                        new DriverSchedule(4L,"B","Other Driver",DAY,LocalTime.of(19,0),shiftEnd)),
                List.of(),List.of(f.driver,f.other),assignments,List.of(),null,false,null,
                new ParkOperatingDay(DAY,ParkOperatingStatus.OPEN,LocalTime.of(17,0),LocalTime.of(23,0),false,"TEST",null),null,null,false,null,WardrobeHoursService.DEFAULT);
    }
    private static DriverAssignment assignment(Driver driver,ShuttlePeriod period,String from,String to) {
        var a = DriverAssignment.automatic(DAY,period,driver); id(a,driver.getId()*10+period.ordinal());
        a.updateScheduledTimes(LocalTime.parse(from),LocalTime.parse(to)); return a;
    }
    private static TransportationDemandWindow window(String from,String to) { return new TransportationDemandWindow(LocalTime.parse(from),LocalTime.parse(to),0,1,1,null); }
    private static OperationalStatusUpdate update(OperationalStatusOverride status,Instant at) { return new OperationalStatusUpdate(1L,status,"Temporary update",2L,"Manager","test@example.com",at); }
    private static SpringTemplateEngine engine() {
        var resolver=new FileTemplateResolver(); resolver.setPrefix("src/main/resources/templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        var engine=new SpringTemplateEngine(); engine.setTemplateResolver(resolver); return engine;
    }
    private static void equal(Object expected,Object actual,String label) { checks++; if(!Objects.equals(expected,actual))throw new AssertionError(label+": expected "+expected+", got "+actual); }
    private static void check(boolean value,String label) { equal(true,value,label); }
    private static void rejects(Runnable action,String label) { try {action.run();} catch(IllegalArgumentException|IllegalStateException|RiderStatusService.RiderStatusNotFoundException ex) {checks++;return;} throw new AssertionError(label); }
    private static void id(Object target,long value) { try {var f=target.getClass().getDeclaredField("id");f.setAccessible(true);f.set(target,value);}catch(Exception ex){throw new RuntimeException(ex);} }
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,BiFunction<String,Object[],Object> handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(o,m,a)->handler.apply(m.getName(),a));
    }
    private static final class Fixture {
        final Organization org=new Organization("Test","Test","test",OrganizationStatus.ACTIVE,null,null);
        final UserAccount user=new UserAccount("test@example.com","hash","Test","Manager",true,true);
        final Driver driver=new Driver(org,"A","Test Driver","test@example.com",true);
        final Driver other=new Driver(org,"B","Other Driver","other@example.com",true);
        DrivingSession active; OperationalStatusUpdate update; List<DriverAssignment> assignments=List.of(); OrganizationRole role=OrganizationRole.ADMIN;
        Fixture(){id(org,1);id(user,2);id(driver,3);id(other,4);driver.linkUser(user);}
        OrganizationRepository organizations(){return proxy(OrganizationRepository.class,(m,a)-> (m.equals("findBySlug")?"test".equals(a[0]):Objects.equals(a[0],1L))?Optional.of(org):Optional.empty());}
        DrivingSessionRepository sessions(){return proxy(DrivingSessionRepository.class,(m,a)-> switch(m){
            case "findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc" -> Optional.ofNullable(active);
            case "findFirstByOrganization_IdAndEndedAtIsNotNullOrderByEndedAtDesc" -> Optional.empty();
            case "findActiveForUpdateByOrganization" -> active==null?List.of():List.of(active);
            default -> throw new UnsupportedOperationException(m);
        });}
        OperationalStatusService operations(){return new OperationalStatusService(null,null,null){@Override public Optional<OperationalStatusUpdate> findLatest(Long org){return Optional.ofNullable(update);}};}
        RiderStatusService riderService(){return new RiderStatusService(organizations(),sessions(),
                operations(),new ServiceFreshnessPolicy(),"America/Chicago");}
    }
}
