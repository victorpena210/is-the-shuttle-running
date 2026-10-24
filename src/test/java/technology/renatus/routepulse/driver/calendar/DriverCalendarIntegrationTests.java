package technology.renatus.routepulse.driver.calendar;

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
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleWindow;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DriverCalendarIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DriverCalendarStore store; @Autowired DriverCalendarService service;
    @Autowired DriverCalendarConnectionRepository connections; @Autowired DriverCalendarShiftRepository shifts;
    @Autowired DriverCalendarAvailability availability; @Autowired CalendarLinkCipher cipher;
    @MockitoBean UkgCalendarClient client;
    private static final String LINK="https://cedarfair.prd.mykronos.com/api/calendar_sync/fictional_private_token_0123456789";
    private static final String OTHER=LINK+"other";
    private final Instant now=Instant.now();
    private final LocalDate day=LocalDate.now(ZoneOffset.UTC).plusDays(1);
    private List<UkgCalendarParser.Shift> sample(){return List.of(new UkgCalendarParser.Shift(day.atTime(8,0).toInstant(ZoneOffset.UTC),day.atTime(20,0).toInstant(ZoneOffset.UTC)));}
    @BeforeEach void stubFeed(){when(client.normalize(anyString())).thenAnswer(a->new UkgCalendarClient().normalize(a.getArgument(0)));when(client.fetch(anyString())).thenReturn(sample());}
    private Fixture fixture(String slug){
        var org=createOrganization(slug,slug);var manager=createUser(slug+"@example.com",true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org,manager,OrganizationRole.OWNER));
        var user=createUser(slug+"-driver@example.com",true);membershipRepository.saveAndFlush(new OrganizationMembership(org,user,OrganizationRole.DRIVER));
        var a=new Driver(org,"100","Alex",true);a.linkUser(user);driverRepository.saveAndFlush(a);
        var b=driverRepository.saveAndFlush(new Driver(org,"200","Blair",true));
        return new Fixture(org,manager,user,a,b);
    }
    private Authentication manager(Fixture f){return authenticationFor(f.manager(),OrganizationRole.OWNER);}
    private Authentication driver(Fixture f){return authenticationFor(f.user(),OrganizationRole.DRIVER);}
    private DriverCalendarStore.Snapshot snapshot(Fixture f){return store.due(Instant.now().plusSeconds(1800)).stream().filter(s->s.org().equals(f.org().getId())).findFirst().orElseThrow();}

    @Test void ownerCanConnectSeparateDriversAndOnlyEncryptedLinksReachTheDatabase()throws Exception{
        var f=fixture("connect-separate");
        mockMvc.perform(post("/drivers/{id}/calendar",f.alex().getId()).param("subscriptionUrl",LINK).with(authentication(manager(f))).with(csrf()))
                .andExpect(redirectedUrl("/drivers/"+f.alex().getId()+"/calendar")).andExpect(flash().attributeExists("successMessage"));
        service.connect(manager(f),f.blair().getId(),OTHER);entityManager.flush();entityManager.clear();
        assertThat(connections.count()).isEqualTo(2);assertThat(shifts.count()).isEqualTo(2);
        for(var c:connections.findAll())assertThat(c.getEncryptedLink()).doesNotContain("fictional_private_token").startsWith("v1.");
        var c=connections.findByDriver_IdAndOrganizationId(f.alex().getId(),f.org().getId()).orElseThrow();
        assertThat(cipher.decrypt(f.org().getId(),f.alex().getId(),c.getEncryptedLink())).isEqualTo(LINK);
        mockMvc.perform(get("/drivers/{id}/calendar",f.alex().getId()).with(authentication(manager(f)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Connected to UKG")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fictional_private_token"))));
        assertThat(assignmentRepository.count()).isZero();assertThat(uploadedShiftRepository.count()).isZero();
    }
    @Test void driverCanConnectOnlyTheirOwnCalendarAndSavedPermissionsOverrideStaleSessionRoles()throws Exception{
        var f=fixture("own-calendar");var other=fixture("foreign-calendar");
        mockMvc.perform(post("/driver/calendar").param("subscriptionUrl",LINK).with(authentication(driver(f))).with(csrf()))
                .andExpect(redirectedUrl("/driver/calendar"));
        assertThatThrownBy(()->service.connect(driver(f),f.blair().getId(),OTHER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.connect(manager(f),other.alex().getId(),OTHER)).isInstanceOf(AccessDeniedException.class);
        mockMvc.perform(get("/drivers/{id}/calendar",f.blair().getId())
                .with(authentication(authenticationFor(f.user(),OrganizationRole.OWNER)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/driver/calendar").with(authentication(driver(f)))).andExpect(status().isOk());
        assertThat(connections.count()).isOne();
    }
    @Test void allCalendarMutationsRequireAuthenticationAndCsrf()throws Exception{
        var f=fixture("calendar-csrf");
        for(String action:List.of("","/refresh","/disconnect")){
            mockMvc.perform(post("/driver/calendar"+action).param("subscriptionUrl",LINK).with(authentication(driver(f)))).andExpect(status().isForbidden());
            mockMvc.perform(post("/drivers/"+f.alex().getId()+"/calendar"+action).param("subscriptionUrl",LINK).with(authentication(manager(f)))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/driver/calendar")).andExpect(status().is3xxRedirection());
        verify(client,never()).fetch(anyString());assertThat(connections.count()).isZero();
    }
    @Test void calendarOverridesCsvIncludingDaysOffAndRefreshReplacesRemovedShifts(){
        var f=fixture("calendar-priority");service.connect(driver(f),null,LINK);
        Map<Long,List<DriverScheduleWindow>> work=new HashMap<>();
        work.put(f.alex().getId(),List.of(new DriverScheduleWindow(f.alex(),null,day.atTime(9,0),day.atTime(23,0))));
        availability.apply(f.org().getId(),day,now,work);
        assertThat(work.get(f.alex().getId())).singleElement().extracting(DriverScheduleWindow::getStartsAt).isEqualTo(day.atTime(8,0));
        entityManager.flush();var snapshot=snapshot(f);store.apply(snapshot,List.of(),null,now.plusSeconds(901));entityManager.flush();
        availability.apply(f.org().getId(),day,now.plusSeconds(902),work);
        assertThat(work.get(f.alex().getId())).isEmpty();assertThat(shifts.count()).isZero();
        assertThat(store.page(driver(f),null).connected()).isTrue();
    }
    @Test void failedRefreshPreservesLastSnapshotAndStaleCalendarsCannotFallBackToCsv(){
        var f=fixture("calendar-stale");store.connect(driver(f),null,LINK,sample(),now.minusSeconds(2*86400));entityManager.flush();
        var snapshot=snapshot(f);store.apply(snapshot,null,"UKG is temporarily unavailable.",now);entityManager.flush();
        assertThat(shifts.count()).isOne();assertThat(store.page(driver(f),null).stale()).isTrue();
        Map<Long,List<DriverScheduleWindow>> work=new HashMap<>();work.put(f.alex().getId(),List.of(new DriverScheduleWindow(f.alex(),null,day.atTime(9,0),day.atTime(20,0))));
        availability.apply(f.org().getId(),day,now,work);assertThat(work.get(f.alex().getId())).isEmpty();
        assertThat(connections.findAll().get(0).getLastError()).contains("temporarily");
    }
    @Test void failedReplacementLeavesTheExistingConnectionAndHoursUntouched(){
        var f=fixture("calendar-invalid");service.connect(driver(f),null,LINK);var original=connections.findAll().get(0).getEncryptedLink();
        when(client.fetch(OTHER)).thenThrow(new IllegalArgumentException("Unsupported calendar."));
        assertThatThrownBy(()->service.connect(driver(f),null,OTHER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(connections.findAll().get(0).getEncryptedLink()).isEqualTo(original);assertThat(shifts.count()).isOne();
    }
    @Test void reconnectAndDisconnectCannotBeUndoneByAnOlderInFlightRefresh(){
        var f=fixture("calendar-race");store.connect(driver(f),null,LINK,sample(),now.minusSeconds(1800));entityManager.flush();
        var old=snapshot(f);store.connect(driver(f),null,OTHER,List.of(),now);entityManager.flush();entityManager.clear();
        store.apply(old,sample(),null,now.plusSeconds(1));entityManager.flush();assertThat(shifts.count()).isZero();
        var current=snapshot(f);store.disconnect(driver(f),null);entityManager.flush();
        store.apply(current,sample(),null,now.plusSeconds(2));assertThat(connections.count()).isZero();assertThat(shifts.count()).isZero();
    }
    @Test void scheduledRefreshUsesSavedConnectionAndDisconnectRestoresCsvAvailability(){
        var f=fixture("calendar-job");store.connect(driver(f),null,LINK,List.of(),now.minusSeconds(1800));entityManager.flush();
        service.refreshDue();entityManager.flush();assertThat(shifts.count()).isOne();verify(client).fetch(LINK);
        store.disconnect(driver(f),null);entityManager.flush();
        assertThat(connections.count()).isZero();assertThat(shifts.count()).isZero();
        Map<Long,List<DriverScheduleWindow>> work=new HashMap<>();work.put(f.alex().getId(),List.of(new DriverScheduleWindow(f.alex(),null,day.atTime(9,0),day.atTime(20,0))));
        availability.apply(f.org().getId(),day,now,work);assertThat(work.get(f.alex().getId())).hasSize(1);
    }
    @Test void calendarCannotExposeAnotherOrganizationsAvailabilityAndOutsideSixWeeksIsUnknown(){
        var f=fixture("calendar-scope");var other=fixture("other-scope");service.connect(manager(other),other.alex().getId(),OTHER);
        Map<Long,List<DriverScheduleWindow>> work=new HashMap<>();availability.apply(f.org().getId(),day,now,work);assertThat(work).isEmpty();
        service.connect(driver(f),null,LINK);var future=day.plusWeeks(7);
        work.put(f.alex().getId(),List.of(new DriverScheduleWindow(f.alex(),null,future.atTime(9,0),future.atTime(20,0))));
        availability.apply(f.org().getId(),future,now,work);assertThat(work.get(f.alex().getId())).isEmpty();
        assertThat(availability.states(f.org().getId())).extracting(DriverCalendarAvailability.State::driverId).containsExactly(f.alex().getId());
    }
    @Test void oneCalendarCannotBeMistakenlyAttachedToTwoDrivers(){
        var f=fixture("calendar-duplicate");service.connect(manager(f),f.alex().getId(),LINK);
        assertThatThrownBy(()->service.connect(manager(f),f.blair().getId(),LINK)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("another driver");
        assertThat(connections.count()).isOne();assertThat(shifts.count()).isOne();
    }
    private record Fixture(Organization org,UserAccount manager,UserAccount user,Driver alex,Driver blair){}
}
