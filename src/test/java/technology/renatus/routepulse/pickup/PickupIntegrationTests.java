package technology.renatus.routepulse.pickup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.calendar.*;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class PickupIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired PickupStore store;@Autowired PickupCalendarService calendars;@Autowired PickupRiderRepository riders;
    @Autowired PickupShiftRepository shifts;@Autowired CalendarLinkCipher cipher;
    @Autowired PickupAlerts alerts;
    @Autowired PickupOutcomeRepository outcomes;
    @Autowired PickupReminderStore reminders;
    @Autowired PickupReminderRepository deliveries;
    @Autowired technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleRepository plans;
    @Autowired technology.renatus.routepulse.driving.repository.DrivingSessionRepository sessions;
    @MockitoBean UkgCalendarClient client;
    private static final String LINK="https://cedarfair.prd.mykronos.com/api/calendar_sync/fictional_pickup_token_0123456789";
    private final LocalDate day=LocalDate.now(ZoneOffset.UTC).plusDays(1);
    private final Instant now=Instant.now();
    private List<UkgCalendarParser.DetailedShift> sample(){return List.of(new UkgCalendarParser.DetailedShift(day.atTime(9,0).toInstant(ZoneOffset.UTC),day.atTime(17,0).toInstant(ZoneOffset.UTC),"Test hall"));}
    @BeforeEach void feed(){when(client.normalize(anyString())).thenAnswer(a->new UkgCalendarClient().normalize(a.getArgument(0)));when(client.fetchDetails(anyString())).thenReturn(sample());}
    private Fixture fixture(String label){
        var org=createOrganization(label,label);var owner=createUser(label+"-owner@example.com",true);var user=createUser(label+"-driver@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,owner,OrganizationRole.OWNER));
        membershipRepository.saveAndFlush(new OrganizationMembership(org,user,OrganizationRole.DRIVER));
        var driver=new Driver(org,"employee-"+label,"Driver",true);driver.linkUser(user);driverRepository.saveAndFlush(driver);
        return new Fixture(org,owner,user,driver);
    }
    private Authentication manager(Fixture f){return authenticationFor(f.owner(),OrganizationRole.OWNER);}
    private Authentication driver(Fixture f){return authenticationFor(f.user(),OrganizationRole.DRIVER);}
    private PickupRider connected(Fixture f){calendars.connect(manager(f),null,"Alex","",LINK);entityManager.flush();return riders.findAllByOrganizationIdOrderByName(f.org().getId()).get(0);}
    private PickupStore.Snapshot snapshot(Fixture f){return store.due(now.plusSeconds(3600)).stream().filter(s->s.org().equals(f.org().getId())).findFirst().orElseThrow();}
    @Test void managerAddsRiderAndDriverSeesOnlyScopedOperationalDetails()throws Exception{
        var f=fixture("pickup-own");var foreign=fixture("pickup-other");
        mockMvc.perform(post("/pickup-assistance/save").param("name","Alex").param("subscriptionUrl",LINK).with(authentication(manager(f))).with(csrf()))
                .andExpect(redirectedUrl("/pickup-assistance")).andExpect(flash().attributeExists("successMessage"));
        calendars.connect(manager(foreign),null,"ForeignPerson","PRIVATE-LOCATION",LINK);entityManager.flush();entityManager.clear();
        var rider=riders.findAllByOrganizationIdOrderByName(f.org().getId()).get(0);
        assertThat(rider.getEncryptedLink()).startsWith("v1.").doesNotContain("fictional");
        assertThat(cipher.decrypt(f.org().getId(),-rider.getId(),rider.getEncryptedLink())).isEqualTo(LINK);
        assertThatThrownBy(()->cipher.decrypt(f.org().getId(),rider.getId(),rider.getEncryptedLink())).isInstanceOf(IllegalStateException.class);
        String html=mockMvc.perform(get("/driver/pickup-assistance").param("date",day.toString()).param("organizationId",foreign.org().getId().toString()).with(authentication(driver(f))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Alex","Test hall","5:00 PM").doesNotContain("ForeignPerson","PRIVATE-LOCATION","fictional_pickup","subscriptionUrl","Add rider");
        mockMvc.perform(get("/pickup-assistance").with(authentication(manager(f)))).andExpect(status().isOk());
        assertThat(assignmentRepository.count()).isZero();assertThat(uploadedShiftRepository.count()).isZero();
    }
    @Test void accessRequiresSavedRoleActiveProfileAndCsrfAndPreventsCrossOrgWrites()throws Exception{
        var f=fixture("pickup-access");var other=fixture("pickup-foreign");var rider=connected(f);
        mockMvc.perform(get("/pickup-assistance")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/driver/pickup-assistance")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/pickup-assistance").with(authentication(driver(f)))).andExpect(status().isForbidden());
        mockMvc.perform(post("/pickup-assistance/save").param("name","Alex").param("subscriptionUrl",LINK).with(authentication(manager(f)))).andExpect(status().isForbidden());
        mockMvc.perform(post("/pickup-assistance/save").param("name","Alex").param("subscriptionUrl",LINK).with(authentication(driver(f))).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get("/pickup-assistance").with(authentication(authenticationFor(f.user(),OrganizationRole.OWNER)))).andExpect(status().isOk());
        // A stale OWNER authority cannot bypass the current DRIVER role for writes.
        assertThatThrownBy(()->store.details(authenticationFor(f.user(),OrganizationRole.OWNER),rider.getId(),"Changed","")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->store.details(manager(other),rider.getId(),"Changed","")).isInstanceOf(AccessDeniedException.class);
        for(String action:List.of("/refresh","/active"))mockMvc.perform(post("/pickup-assistance/"+rider.getId()+action).param("active","false").with(authentication(manager(f)))).andExpect(status().isForbidden());
        var viewer=createUser("pickup-viewer@example.com",true);membershipRepository.saveAndFlush(new OrganizationMembership(f.org(),viewer,OrganizationRole.VIEWER));
        assertThatThrownBy(()->store.page(authenticationFor(viewer,OrganizationRole.DRIVER),day)).isInstanceOf(AccessDeniedException.class);
        f.driver().setActive(false);driverRepository.flush();
        assertThatThrownBy(()->store.page(driver(f),day)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void refreshReplacesCancelledShiftsFailurePreservesAndPausePreventsLateResults(){
        var f=fixture("pickup-sync");store.connect(manager(f),null,"Alex","",LINK,sample(),now.minusSeconds(1800));entityManager.flush();
        var rider=riders.findAllByOrganizationIdOrderByName(f.org().getId()).get(0);var old=snapshot(f);
        store.apply(old,null,now);entityManager.flush();assertThat(shifts.count()).isOne();assertThat(store.page(driver(f),day).warnings()).isEqualTo(1);
        var fresh=snapshot(f);store.apply(fresh,List.of(),now.plusSeconds(1));entityManager.flush();assertThat(shifts.count()).isZero();
        var current=snapshot(f);store.active(manager(f),rider.getId(),false);entityManager.flush();
        store.apply(current,sample(),now.plusSeconds(2));entityManager.flush();assertThat(shifts.count()).isZero();assertThat(store.page(driver(f),day).rows()).isEmpty();
        assertThat(store.due(now.plusSeconds(10000)).stream().filter(s->s.org().equals(f.org().getId()))).isEmpty();
        store.active(manager(f),rider.getId(),true);entityManager.flush();calendars.refreshDue();entityManager.flush();
        assertThat(shifts.count()).isOne();assertThat(store.page(driver(f),day).rows()).hasSize(1);
    }
    @Test void duplicateFeedRejectedAndReconnectInvalidatesOldRefresh(){
        var f=fixture("pickup-reconnect");var rider=connected(f);
        assertThatThrownBy(()->calendars.connect(manager(f),null,"Blair","",LINK)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already");
        var old=snapshot(f);when(client.fetchDetails(LINK+"new")).thenReturn(List.of());
        calendars.connect(manager(f),rider.getId(),"Alex","North gate",LINK+"new");entityManager.flush();entityManager.clear();
        store.apply(old,sample(),now.plusSeconds(2));entityManager.flush();assertThat(shifts.count()).isZero();
        assertThat(riders.findById(rider.getId()).orElseThrow().getLocationOverride()).isEqualTo("North gate");
    }
    @Test void dateSelectionUsesClockOutDateAndManagerOverridesPersistAcrossRefresh()throws Exception{
        var f=fixture("pickup-date");var rider=connected(f);store.details(manager(f),rider.getId(),"Alex","Accessible entrance");entityManager.flush();
        assertThat(store.page(driver(f),day).rows()).singleElement().extracting(PickupStore.Row::location).isEqualTo("Accessible entrance");
        assertThat(store.page(driver(f),day.minusDays(1)).rows()).isEmpty();
        mockMvc.perform(get("/driver/pickup-assistance").param("date",day.plusWeeks(8).toString()).with(authentication(driver(f)))).andExpect(status().isBadRequest());
        var overnight=List.of(new UkgCalendarParser.DetailedShift(day.minusDays(1).atTime(23,0).toInstant(ZoneOffset.UTC),day.atTime(1,0).toInstant(ZoneOffset.UTC),"Other hall"));
        store.apply(snapshot(f),overnight,now.plusSeconds(1));entityManager.flush();
        assertThat(store.page(driver(f),day).rows()).singleElement().satisfies(r->{assertThat(r.clockOut().getHour()).isEqualTo(1);assertThat(r.location()).isEqualTo("Accessible entrance");});
    }
    private Instant due(){return day.atTime(16,45).toInstant(ZoneOffset.UTC);}
    private void plan(Fixture f) throws Exception {
        var type=technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.class;
        var constructor=type.getDeclaredConstructor(Long.class,LocalDate.class);constructor.setAccessible(true);
        var plan=constructor.newInstance(f.org().getId(),day);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(plan,"update","OPEN","LIVE_API",null,null,null,null,due(),"Test",List.of(
                new technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.Slot(day.atTime(16,0),day.atTime(18,0),f.driver().getId(),f.driver().getName(),"Assigned")));
        plans.saveAndFlush(plan);
        f.driver().changeDrivingReminderEmail("pickup-driver@example.com");
    }
    @Test void completionIsScopedCsrfProtectedAndSurvivesCalendarReplacement() throws Exception {
        var f=fixture("pickup-outcome");var foreign=fixture("pickup-outcome-other");var rider=connected(f);
        var time=sample().get(0).end();
        mockMvc.perform(post("/driver/pickup-assistance/outcome").with(authentication(driver(f)))
                .param("riderId",rider.getId().toString()).param("clockOut",time.toString()).param("outcome","PICKED_UP"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/driver/pickup-assistance/outcome").with(authentication(driver(f))).with(csrf())
                .param("riderId",rider.getId().toString()).param("clockOut",time.toString()).param("outcome","PICKED_UP").param("returnTo","home"))
                .andExpect(redirectedUrl("/driver"));
        assertThat(alerts.forDates(f.org().getId(),day,day,now)).singleElement().satisfies(a->assertThat(a.status()).isEqualTo(PickupOutcome.Status.PICKED_UP));
        store.connect(manager(f),rider.getId(),"Alex","",LINK,sample(),now.plusSeconds(1));entityManager.flush();entityManager.clear();
        assertThat(alerts.forDates(f.org().getId(),day,day,now)).singleElement().satisfies(a->assertThat(a.pending()).isFalse());
        assertThatThrownBy(()->alerts.outcome(driver(foreign),rider.getId(),time,PickupOutcome.Status.NOT_NEEDED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->alerts.outcome(driver(f),rider.getId(),time.plusSeconds(60),PickupOutcome.Status.PICKED_UP)).isInstanceOf(IllegalArgumentException.class);
        alerts.outcome(driver(f),rider.getId(),time,PickupOutcome.Status.PENDING);
        assertThat(alerts.forDates(f.org().getId(),day,day,now).get(0).pending()).isTrue();
        mockMvc.perform(get("/my-pickup-assistance").param("date",day.toString()).with(authentication(driver(f))))
                .andExpect(redirectedUrl("/driver/pickup-assistance?date="+day));
    }
    @Test void remindersAreDurableFollowSubstitutesAndStopAfterCompletion() throws Exception {
        var f=fixture("pickup-delivery");var rider=connected(f);rider.synced(due().minusSeconds(60));plan(f);
        assertThat(reminders.claimNext(f.org().getId(),due().minusSeconds(1))).isEmpty();
        var first=reminders.claimNext(f.org().getId(),due()).orElseThrow();
        assertThat(first.recipient()).isEqualTo("pickup-driver@example.com");
        assertThat(reminders.stillDue(first,due())).isTrue();reminders.sent(first.deliveryId(),due());
        assertThat(reminders.claimNext(f.org().getId(),due().plusSeconds(30))).isEmpty();
        store.connect(manager(f),rider.getId(),"Alex","",LINK,sample(),due());entityManager.flush();
        assertThat(reminders.claimNext(f.org().getId(),due().plusSeconds(30))).isEmpty();
        var covering=driverRepository.saveAndFlush(new Driver(f.org(),"covering","Covering driver",true));covering.changeDrivingReminderEmail("covering@example.com");
        var session=technology.renatus.routepulse.driving.model.DrivingSession.startUnscheduled(f.org(),covering,due().minusSeconds(600),f.owner(),technology.renatus.routepulse.driving.domain.DrivingActionMethod.QR);
        session.captureCoveragePlan(List.of(new technology.renatus.routepulse.driving.model.DrivingCoverageWindow(day,"EMPLOYEE_PICKUP",f.driver().getId(),day.atTime(16,0).toInstant(ZoneOffset.UTC),day.atTime(18,0).toInstant(ZoneOffset.UTC))));
        sessions.saveAndFlush(session);
        var substitute=reminders.claimNext(f.org().getId(),due().plusSeconds(60)).orElseThrow();
        assertThat(substitute.recipient()).isEqualTo("covering@example.com");
        alerts.outcome(driver(f),rider.getId(),sample().get(0).end(),PickupOutcome.Status.NOT_NEEDED);
        assertThat(reminders.stillDue(substitute,due().plusSeconds(61))).isFalse();
        assertThat(reminders.claimNext(f.org().getId(),due().plusSeconds(120))).isEmpty();
        assertThat(deliveries.count()).isEqualTo(2);
    }
    @Test void changedCancelledStaleAndPausedCalendarsSuppressReminders() throws Exception {
        var f=fixture("pickup-suppress");var rider=connected(f);rider.synced(due().minusSeconds(60));plan(f);
        var notice=reminders.claimNext(f.org().getId(),due()).orElseThrow();
        store.connect(manager(f),rider.getId(),"Alex","",LINK,List.of(),due());entityManager.flush();
        assertThat(reminders.stillDue(notice,due().plusSeconds(1))).isFalse();
        assertThat(reminders.claimNext(f.org().getId(),due().plusSeconds(60))).isEmpty();
        store.connect(manager(f),rider.getId(),"Alex","",LINK,sample(),due());rider.failed(due());entityManager.flush();
        assertThat(reminders.claimNext(f.org().getId(),due().plusSeconds(60))).isEmpty();
        rider.synced(due());store.active(manager(f),rider.getId(),false);entityManager.flush();
        assertThat(reminders.claimNext(f.org().getId(),due().plusSeconds(60))).isEmpty();
        assertThat(reminders.claimNext(f.org().getId(),sample().get(0).end())).isEmpty();
    }
    private record Fixture(Organization org,UserAccount owner,UserAccount user,Driver driver) { }
}
