package technology.renatus.routepulse.driving.movement;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.assignments.domain.*;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.driving.domain.*;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.service.*;
import technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy;
import technology.renatus.routepulse.operations.domain.*;
import technology.renatus.routepulse.operations.service.OperationalStatusService;
import technology.renatus.routepulse.organization.domain.*;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.organization.repository.*;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.shared.time.TimestampDisplay;
import technology.renatus.routepulse.support.WebTemplateContext;

class MovementMonitoringTests {
    private static final Instant START=Instant.parse("2026-10-06T15:00:00Z");
    private static final ShuttleRoute ROUTE=ShuttleRoute.parse("29.4,-98.5\n29.42,-98.5");
    private static Instant at(long seconds) { return START.plusSeconds(seconds); }
    private static LocationSample point(long second,double latitude,double accuracy,Double speed) {
        return new LocationSample(latitude,-98.5,accuracy,speed,at(second));
    }
    private static MovementState stationary(long through) {
        var state=new MovementState();state.start(START);
        feed(state,0,through);return state;
    }
    private static void feed(MovementState state,long from,long through) {
        for (long s=from;s<=through;s+=30) { state.accept(point(s,29.405,8,0.0),ROUTE,at(s));state.evaluate(at(s)); }
    }
    @Test void stationaryServiceNeverPromptsOrTimesOut() {
        var state=stationary(570);assertNull(state.getPromptedAt());assertFalse(state.timedOut(at(599)));
        feed(state,600,600);assertNull(state.getPromptedAt());assertFalse(state.timedOut(at(1199)));
        feed(state,630,1200);assertFalse(state.timedOut(at(1200)));assertNull(state.timeoutAt());
        feed(state,1230,10800);assertNull(state.getPromptedAt());assertFalse(state.timedOut(at(10800)));
    }
    @Test void waitingNeverRequiresAnotherCheckIn() {
        var state=stationary(600);state.acknowledge(at(600),true);
        assertTrue(state.waiting());assertNull(state.getPromptedAt());
        feed(state,630,1170);assertNull(state.getPromptedAt());
        feed(state,1200,1200);assertNull(state.getPromptedAt());assertFalse(state.timedOut(at(1200)));
        feed(state,1230,1800);assertFalse(state.timedOut(at(1800)));
    }
    @Test void missingLocationsNeverCloseAndRecoveryStartsNewObservation() {
        var state=stationary(1110);state.evaluate(at(1200));
        assertFalse(state.fresh(at(1200)));assertFalse(state.timedOut(at(1200)));assertNull(state.getPromptedAt());
        feed(state,1230,1230);assertEquals(at(1230),state.getStationarySince());assertFalse(state.timedOut(at(1230)));
    }
    @Test void dataGapResetsEvenIfJobDidNotRunDuringOutage() {
        var state=stationary(900);feed(state,1200,1200);
        assertEquals(at(1200),state.getStationarySince());assertNull(state.getPromptedAt());assertFalse(state.timedOut(at(1200)));
    }
    @Test void noPermissionOrNoFixDoesNotProduceInactivity() {
        var state=new MovementState();state.start(START);state.evaluate(at(3600));
        assertFalse(state.fresh(at(3600)));assertFalse(state.timedOut(at(3600)));assertNull(state.getStationarySince());
    }
    @Test void poorAccuracyBreaksContinuity() {
        var state=stationary(1170);state.accept(point(1200,29.405,100,0.0),ROUTE,at(1200));state.evaluate(at(1200));
        assertFalse(state.timedOut(at(1200)));assertFalse(state.fresh(at(1200)));
        feed(state,1230,1230);assertEquals(at(1230),state.getStationarySince());
    }
    @Test void accurateRouteMovementConfirmsAndClearsStationaryPrompt() {
        var state=stationary(600);
        assertTrue(state.accept(point(630,29.406,8,4.0),ROUTE,at(630)));
        assertEquals(at(630),state.getMovingAt());assertNull(state.getStationarySince());assertNull(state.getPromptedAt());
    }
    @Test void walkingOrSpeedAloneCannotProveRouteMovement() {
        var state=stationary(600);
        assertFalse(state.accept(point(615,29.405,8,3.0),ROUTE,at(615)));
        assertNull(state.getMovingAt());assertFalse(state.timedOut(at(1200)));
    }
    @Test void routeIsOptionalAndStationaryServiceNeverTimesOut() {
        var state=new MovementState();state.start(START);var noRoute=ShuttleRoute.parse("");
        state.accept(point(0,29.405,8,0.0),noRoute,START);
        assertFalse(state.accept(point(30,29.406,8,4.0),noRoute,at(30)));assertNull(state.getMovingAt());
        state=new MovementState();state.start(START);
        for (int t=0;t<=1200;t+=30) {state.accept(point(t,29.405,8,0.0),noRoute,at(t));state.evaluate(at(t));}
        assertFalse(state.timedOut(at(1200)));
    }
    @Test void offRouteAndImpossibleJumpsAreUnconfirmedNotStationary() {
        var state=stationary(1170);
        assertFalse(state.accept(new LocationSample(29.405,-98.51,8,0.0,at(1200)),ROUTE,at(1200)));
        assertFalse(state.fresh(at(1200)));assertFalse(state.timedOut(at(1200)));
        state=stationary(600);
        assertFalse(state.accept(point(601,29.419,8,0.0),ROUTE,at(601)));assertFalse(state.fresh(at(601)));
    }
    @Test void gpsJitterDoesNotConfirmMovement() {
        var state=stationary(0);
        for (int s=30;s<=600;s+=30) assertFalse(state.accept(point(s,29.405+(s%60==0 ? .00008 : -.00008),12,0.0),ROUTE,at(s)));
        state.evaluate(at(600));assertNull(state.getPromptedAt());assertNull(state.getMovingAt());
    }
    @Test void duplicateAndOutOfOrderFixesCannotKeepFeedAlive() {
        var state=stationary(600);
        state.accept(point(600,29.405,8,0.0),ROUTE,at(630));
        state.accept(point(599,29.405,8,0.0),ROUTE,at(640));
        assertEquals(at(600),state.getReceivedAt());state.evaluate(at(690));assertFalse(state.fresh(at(690)));
    }
    @Test void invalidSamplesAreRejected() {
        var state=new MovementState();state.start(START);
        for (LocationSample sample:List.of(new LocationSample(Double.NaN,0,8,null,START),
                new LocationSample(91,0,8,null,START),new LocationSample(29,-181,8,null,START),
                new LocationSample(29,-98,0,null,START),new LocationSample(29,-98,8,-1.0,START),
                new LocationSample(29,-98,8,null,at(-46)),new LocationSample(29,-98,8,null,at(6))))
            assertThrows(IllegalArgumentException.class,()->state.accept(sample,ROUTE,START));
        assertNull(state.getReceivedAt());
    }
    @Test void routeValidationRejectsBadOrOverlongRoutes() {
        assertThrows(IllegalArgumentException.class,()->ShuttleRoute.parse("29,-98"));
        assertThrows(IllegalArgumentException.class,()->ShuttleRoute.parse("29,-98\n29,-98"));
        assertThrows(IllegalArgumentException.class,()->ShuttleRoute.parse("NaN,-98\n29,-98"));
        assertThrows(IllegalArgumentException.class,()->ShuttleRoute.parse("29,-98\n39,-98"));
        assertTrue(ROUTE.contains(29.405,-98.5,8));assertFalse(ROUTE.contains(29.405,-98.51,8));
    }
    @Test void delayedSchedulerDoesNotCreatePrompt() {
        var state=new MovementState();state.start(START);
        for (int s=0;s<=1200;s+=30) state.accept(point(s,29.405,8,0.0),ROUTE,at(s));
        state.evaluate(at(1200));assertFalse(state.timedOut(at(1200)));assertNull(state.timeoutAt());
    }
    @Test void lostLocationDoesNotDowngradeActiveService() {
        var f=new Fixture();var s=f.session;var m=s.getMovement();m.start(START);feed(m,0,600);
        m.loseSignal();s.confirmRunning(at(630),f.user);assertFalse(m.fresh(at(630)));assertFalse(m.timedOut(at(1200)));
        var policy=new ServiceFreshnessPolicy();assertTrue(policy.isFresh(s,at(630)));
    }
    @Test void schedulerNeverClosesStationaryOrDisconnectedSession() throws Exception {
        var f=new Fixture();var s=f.session;s.getMovement().start(START);feed(s.getMovement(),0,1200);
        // An old persisted prompt must not restore the removed cutoff.
        var prompt=MovementState.class.getDeclaredField("promptedAt");prompt.setAccessible(true);prompt.set(s.getMovement(),at(600));
        assertNull(MovementMonitoringService.snapshot(s,at(1200)).promptedAt());
        assertNull(MovementMonitoringService.snapshot(s,at(1200)).timeoutAt());
        f.monitor.evaluateOrganization(1L,at(1200));
        assertTrue(s.isActive());assertNull(s.getEndReason());assertNull(s.getEndedAt());
        assertNull(s.getMovement().getPromptedAt());assertFalse(s.isInactivityReviewNeeded());
        f.monitor.evaluateOrganization(1L,at(10800));
        assertTrue(s.isActive());assertEquals(RiderOperationalStatus.RUNNING,f.riderAt(at(10800)).operationalStatus());
        assertTrue(f.riderAt(at(10800)).confirmationAvailable());
    }
    @Test void optionalControlsWorkWithoutLocationAndWaitingSurvivesSignalLoss() throws Exception {
        var f=new Fixture(Instant.now().minusSeconds(60));
        f.monitor.respond(f.auth,10L,MovementMonitoringService.Response.WAITING);
        assertFalse(f.session.getMovement().enabled());
        assertEquals("WAITING",f.monitor.signalLost(f.auth,10L).state());
        assertEquals(RiderOperationalStatus.WAITING_AT_PICKUP,f.riderAt(Instant.now()).operationalStatus());
        f.monitor.respond(f.auth,10L,MovementMonitoringService.Response.RUNNING);
        assertTrue(f.session.isActive());assertFalse(f.session.getMovement().waiting());
        assertEquals(RiderOperationalStatus.RUNNING,f.riderAt(Instant.now()).operationalStatus());
    }
    @Test void enablingLocationDoesNotShortenOrExtendDurationLimit() throws Exception {
        var f=new Fixture(Instant.now().minusSeconds(60));f.monitor.enable(f.auth,10L);
        assertEquals("LOCATION_UNAVAILABLE",f.monitor.status(f.auth,10L).state());
        assertEquals(RiderOperationalStatus.RUNNING,f.riderAt(Instant.now()).operationalStatus());
        f.monitor.signalLost(f.auth,10L);
        assertEquals(RiderOperationalStatus.RUNNING,f.riderAt(Instant.now()).operationalStatus());
        assertEquals(RiderOperationalStatus.NOT_RUNNING,f.riderAt(f.session.getAutomaticEndAt()).operationalStatus());
    }
    @Test void scheduledTimeoutDoesNotAwardCompletionOrMakeupCredit() {
        var f=new Fixture();var a=DriverAssignment.automatic(LocalDate.of(2026,10,6),ShuttlePeriod.MORNING,f.driver);a.updateScheduledTimes(LocalTime.of(9,0),LocalTime.NOON);
        var s=DrivingSession.start(a,f.driver,START,f.user,DrivingActionMethod.NFC);a.registerDrivingSession(s);a.markDrivingInProgress();
        // The real service would dereference the absent workload service if it incorrectly awarded credit.
        var service=new DrivingSessionService(null,null,null,null,null);
        service.endSession(a,s,null,at(1200),DrivingActionMethod.AUTOMATIC,DrivingSessionEndReason.INACTIVITY_TIMEOUT);
        assertEquals(AssignmentOutcome.PENDING,a.getAssignmentOutcome());assertTrue(s.isInactivityReviewNeeded());
    }
    @Test void driverWaitingBreakAndEndUseExistingSessionLifecycle() {
        var f=new Fixture(Instant.now().minusSeconds(60));f.monitor.enable(f.auth,10L);
        f.monitor.respond(f.auth,10L,MovementMonitoringService.Response.WAITING);
        assertTrue(f.session.isActive());assertTrue(f.session.getMovement().waiting());
        f.monitor.respond(f.auth,10L,MovementMonitoringService.Response.BREAK);
        assertFalse(f.session.isActive());assertEquals(DrivingSessionEndReason.PAUSE,f.session.getEndReason());assertEquals(f.user,f.session.getEndedBy());
        assertThrows(IllegalStateException.class,()->f.monitor.respond(f.auth,10L,MovementMonitoringService.Response.WAITING));
        var endedFixture=new Fixture(Instant.now().minusSeconds(60));endedFixture.monitor.respond(endedFixture.auth,10L,MovementMonitoringService.Response.END);
        assertEquals(DrivingSessionEndReason.COMPLETED,endedFixture.session.getEndReason());
    }
    @Test void onlyActiveDriverInCurrentOrganizationCanPost() {
        var f=new Fixture();f.selectedDriver=f.other;
        assertThrows(AccessDeniedException.class,()->f.monitor.enable(f.auth,10L));
        f.selectedDriver=f.driver;assertThrows(IllegalStateException.class,()->f.monitor.enable(f.auth,99L));
        f.driver.setActive(false);assertThrows(IllegalStateException.class,()->f.monitor.enable(f.auth,10L));
        f.driver.setActive(true);f.currentOrganizationId=2L;
        assertThrows(IllegalStateException.class,()->f.monitor.enable(f.auth,10L));
    }
    @Test void endedSessionRejectsLateGpsOrStaleHandoffPage() {
        var f=new Fixture(Instant.now().minusSeconds(60));f.session.end(Instant.now(),f.user,DrivingActionMethod.NFC,DrivingSessionEndReason.HANDOFF);
        assertThrows(IllegalStateException.class,()->f.monitor.location(f.auth,10L,
                new LocationSample(29.405,-98.5,8,0.0,Instant.now())));
        assertEquals("ENDED",f.monitor.status(f.auth,10L).state());
    }
    @Test void onlyManagersCanSaveRouteOrReviewOwnOrganizationSessions() {
        var f=new Fixture(Instant.now().minusSeconds(60));f.role=OrganizationRole.DRIVER;
        assertThrows(AccessDeniedException.class,()->f.monitor.saveRoute(f.auth,ROUTE.normalized()));
        f.role=OrganizationRole.ADMIN;f.monitor.saveRoute(f.auth,ROUTE.normalized());assertEquals(ROUTE.normalized(),f.org.getMovementRoute());
        assertThrows(IllegalStateException.class,()->f.monitor.review(f.auth,99L,"Checked"));
        f.session.end(Instant.now(),null,DrivingActionMethod.AUTOMATIC,DrivingSessionEndReason.INACTIVITY_TIMEOUT);
        f.monitor.review(f.auth,10L,"Spoke with driver");assertFalse(f.session.isInactivityReviewNeeded());
    }
    @Test void statusSnapshotNeverDisclosesCoordinates() {
        var f=new Fixture();String text=MovementMonitoringService.snapshot(f.session,START).toString();
        assertFalse(text.contains("latitude"));assertFalse(text.contains("longitude"));assertFalse(text.contains("29.4"));
    }
    @Test void riderSeesWaitingEvenWhenLocationStops() throws Exception {
        var f=new Fixture();var m=f.session.getMovement();m.start(START);feed(m,0,600);
        f.session.confirmRunning(at(600),f.user);m.acknowledge(at(600),true);
        var status=f.riderAt(at(600));assertEquals(RiderOperationalStatus.WAITING_AT_PICKUP,status.operationalStatus());assertTrue(status.running());
        f.monitor.evaluateOrganization(1L,at(10800));
        status=f.riderAt(at(10800));assertEquals(RiderOperationalStatus.WAITING_AT_PICKUP,status.operationalStatus());assertEquals(at(600),status.lastConfirmedAt());
        assertTrue(status.confirmationAvailable());assertNull(status.confirmationDueAt());
    }
    @Test void automaticCloseIsNotAServiceConfirmation() throws Exception {
        var f=new Fixture();f.session.end(at(1200),null,DrivingActionMethod.AUTOMATIC,DrivingSessionEndReason.INACTIVITY_TIMEOUT);
        var status=f.riderAt(at(1200));assertEquals(RiderOperationalStatus.CONFIRMATION_NEEDED,status.operationalStatus());
        assertEquals(START,status.lastConfirmedAt());assertNull(status.activeSessionId());
    }
    @Test void driverBreakIsReflectedOnRiderPage() throws Exception {
        var f=new Fixture();f.session.end(at(600),f.user,DrivingActionMethod.DRIVER,DrivingSessionEndReason.PAUSE);
        assertEquals(RiderOperationalStatus.TEMPORARILY_PAUSED,f.riderAt(at(610)).operationalStatus());
    }
    @Test void realTemplatesRenderControlsAndEscapedReviewData() throws Exception {
        var f=new Fixture();var resolver=new FileTemplateResolver();resolver.setPrefix("src/main/resources/templates/");resolver.setSuffix(".html");resolver.setTemplateMode("HTML");
        var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
        var model=new HashMap<String,Object>();model.put("driver",f.driver);model.put("organizationName","Test");model.put("today",LocalDate.of(2026,10,6));
        model.put("driverPage","assignment");model.put("activeSessionId",10L);model.put("confirmationDueAt",at(7200));model.put("confirmationOverdue",false);
        model.put("unscheduledRecords",List.of());model.put("unscheduledActive",false);model.put("todayAssignments",List.of());
        model.put("timestampDisplay",new TimestampDisplay("America/Chicago"));
        model.put("_csrf",Map.of("token","test-token","headerName","X-CSRF-TOKEN","parameterName","_csrf"));
        String html=engine.process("driver/home",WebTemplateContext.create(model));
        assertTrue(html.contains("data-session-id=\"10\""));assertTrue(html.contains("Waiting for passengers"));assertTrue(html.contains("Use these controls only while safely parked"));
        assertTrue(html.contains("No repeat check-ins are required"));assertFalse(html.contains("Next confirmation due"));
        assertFalse(html.contains("id=\"movement-prompt\""));
        assertTrue(html.contains("test-token"));assertTrue(html.contains("/js/pages/driver-movement.js"));
        f.session.end(at(1200),null,DrivingActionMethod.AUTOMATIC,DrivingSessionEndReason.INACTIVITY_TIMEOUT);
        model.put("movementRoute",ROUTE.normalized());model.put("reviews",List.of(f.session));
        html=engine.process("manager/movement",WebTemplateContext.create(model));
        assertTrue(html.contains("Inactivity timeout — needs review"));assertTrue(html.contains("/operations/movement/review"));
        // Save real rendered pages for the optional local browser interaction check.
        if (System.getProperty("movement.render-dir")!=null) {
            var dir=java.nio.file.Path.of(System.getProperty("movement.render-dir"));java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("manager.html"),html);
            java.nio.file.Files.writeString(dir.resolve("driver.html"),engine.process("driver/home",WebTemplateContext.create(model)));
        }
    }
    @Test void motionAtTimeoutBoundaryCancelsAutomaticClose() {
        var state=stationary(1170);
        assertTrue(state.accept(point(1200,29.406,8,4.0),ROUTE,at(1200)));
        state.evaluate(at(1200));assertFalse(state.timedOut(at(1200)));assertNull(state.getPromptedAt());
    }
    @Test void ormMappingsAndMonitoringQueriesCompileWithMySqlDialect() throws Exception {
        var registry=new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect","org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access",false)
                .applySetting("hibernate.hbm2ddl.auto","none").build();
        try {
            var sources=new org.hibernate.boot.MetadataSources(registry);
            var root=java.nio.file.Path.of("src/main/java");
            try (var paths=java.nio.file.Files.walk(root)) {
                for (var path:paths.filter(p->p.toString().endsWith(".java")).toList()) {
                    String name=root.relativize(path).toString().replace(java.io.File.separatorChar,'.').replaceAll("\\.java$", "");
                    Class<?> type=Class.forName(name);
                    if(type.isAnnotationPresent(jakarta.persistence.Entity.class)) sources.addAnnotatedClass(type);
                }
            }
            try (var factory=sources.buildMetadata().buildSessionFactory()) {
                var impl=factory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class);
                for (var method:DrivingSessionRepository.class.getMethods()) {
                    var query=method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
                    if (query!=null && (method.getName().equals("findMonitoredOrganizationIds") || method.getName().equals("findForReview")))
                        assertNotNull(impl.getQueryEngine().getHqlTranslator().translate(query.value(),null));
                    if(method.getName().contains("InactivityReviewedAt"))
                        assertNotNull(new org.springframework.data.repository.query.parser.PartTree(method.getName(),DrivingSession.class));
                }
            }
        } finally { org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry); }
    }
    private static void id(Object o,long value) {try {var field=o.getClass().getDeclaredField("id");field.setAccessible(true);field.set(o,value);} catch(Exception e) {throw new RuntimeException(e);} }
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,BiFunction<String,Object[],Object> h) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(o,m,a)->h.apply(m.getName(),a));
    }
    private static class Fixture {
        final Organization org=new Organization("Test","Test","test",OrganizationStatus.ACTIVE,null,null);
        final UserAccount user=new UserAccount("driver@example.com","hash","Driver","One",true,true);
        final Driver driver=new Driver(org,"A","Driver One",true),other=new Driver(org,"B","Driver Two",true);
        Driver selectedDriver=driver;OrganizationRole role=OrganizationRole.DRIVER;long currentOrganizationId=1L;
        final Authentication auth=new UsernamePasswordAuthenticationToken("driver@example.com","password",List.of());
        DrivingSession session;final MovementMonitoringService monitor;
        Fixture() { this(START); }
        Fixture(Instant startedAt) {
            id(org,1);id(user,2);id(driver,3);id(other,4);driver.linkUser(user);org.updateMovementRoute(ROUTE.normalized());
            session=DrivingSession.startUnscheduled(org,driver,startedAt,user,DrivingActionMethod.NFC);id(session,10);
            var access=new DriverPortalAccessService(null,null,null) {
                @Override public Driver requireCurrentDriver(Authentication a) {
                    if (currentOrganizationId!=1L) throw new IllegalStateException("Organization access denied");return selectedDriver;
                }
                @Override public UserAccount requireCurrentUser(Authentication a) {return user;}
            };
            var current=new CurrentOrganizationService(null,null) {@Override public Organization requireOrganization(Authentication a) {return org;}};
            var memberships=proxy(OrganizationMembershipRepository.class,(m,a)->Optional.of(new OrganizationMembership(org,user,role)));
            var sessions=new DrivingSessionService(null,repository(),null,organizations(),null) {
                @Override public DrivingSession endSession(DriverAssignment a,DrivingSession s,UserAccount u,Instant t,DrivingActionMethod method,DrivingSessionEndReason reason) {
                    s.end(t,u,method,reason);return s;
                }
            };
            monitor=new MovementMonitoringService(access,current,memberships,sessions,repository());
        }
        OrganizationRepository organizations() {return proxy(OrganizationRepository.class,(m,a)->Optional.of(org));}
        DrivingSessionRepository repository() {return proxy(DrivingSessionRepository.class,(m,a)->switch(m) {
            case "findForReview" -> Objects.equals(a[0],10L)&&Objects.equals(a[1],1L) ? Optional.of(session) : Optional.empty();
            case "findActiveForUpdateByOrganization" -> session.isActive()?List.of(session):List.of();
            case "findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc" -> session.isActive()?Optional.of(session):Optional.empty();
            case "findFirstByOrganization_IdAndEndedAtIsNotNullOrderByEndedAtDesc" -> session.isActive()?Optional.empty():Optional.of(session);
            default -> throw new UnsupportedOperationException(m);
        });}
        RiderStatusService.RiderStatus riderAt(Instant now) throws Exception {
            var operations=new OperationalStatusService(null,null,null) {@Override public Optional<technology.renatus.routepulse.operations.model.OperationalStatusUpdate> findLatest(Long id){return Optional.empty();}};
            var rider=new RiderStatusService(organizations(),repository(),operations,
                    new ServiceFreshnessPolicy(),"America/Chicago");
            var method=RiderStatusService.class.getDeclaredMethod("getStatus",String.class,Instant.class);method.setAccessible(true);
            return (RiderStatusService.RiderStatus)method.invoke(rider,"test",now);
        }
    }
}
