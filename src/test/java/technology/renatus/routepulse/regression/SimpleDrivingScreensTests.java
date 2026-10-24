package technology.renatus.routepulse.regression;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.support.WebTemplateContext;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointPreviewAction;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.UnscheduledDrivingRecord;
import technology.renatus.routepulse.shared.web.RetiredPlanningRoutes;

class SimpleDrivingScreensTests {
    @Test void driverHomeRendersWithoutAnyScheduleData() throws Exception {
        for (boolean active : List.of(false, true)) {
            var model = model();
            model.put("drivingRecords", active ? List.of(record()) : List.of());
            if (active) model.put("activeSessionId", 1L);
            String html = render("driver/home",model,active ? "driver-active" : "driver-empty");
            assertThat(html).contains("My recent driving records").doesNotContain("My schedule","/driver/availability","primaryAssignment");
            assertThat(html).contains(active ? "Your shuttle session is active" : "Start a shuttle run");
        }
    }
    @Test void checkpointRendersStartPauseAndHandoffWithoutAssignment() throws Exception {
        for (var action : List.of(CheckpointPreviewAction.START,CheckpointPreviewAction.PAUSE_OR_END,CheckpointPreviewAction.TAKEOVER)) {
            var model=model();model.put("checkpointAction",action);model.put("checkpointMethod",DrivingActionMethod.NFC);
            model.put("checkpointLabel","NFC tap");model.put("checkpointCode","tag");model.put("formAction","/driver/tap");
            model.put("takeoverRequired",action==CheckpointPreviewAction.TAKEOVER);model.put("coverageRequired",false);
            model.put("currentActualDriver",model.get("driver"));
            String html=render("driver/scan",model,"checkpoint-"+action.name());
            assertThat(html).doesNotContain("Scheduled driver","Scheduled time","unscheduled driving");
            assertThat(html).contains(action==CheckpointPreviewAction.START ? "Confirm start driving"
                    : action==CheckpointPreviewAction.TAKEOVER ? "Confirm driving handoff" : "Pause driving");
        }
    }
    @Test void driverRegistrationAcceptsNameWithoutLookup() throws Exception {
        var model=model();model.put("drivers",List.of());model.put("canManageDrivingQr",true);
        String html=render("manager/drivers",model,"drivers");
        assertThat(html).contains("name=\"name\"").doesNotContain("uploaded schedules","employeeLookupResult");
    }
    @Test void actualRecordsRenderDriverTotalsAndActiveSession() throws Exception {
        var model=model();model.put("drivers",List.of());model.put("fromDate",LocalDate.of(2026,10,12));
        model.put("toDate",LocalDate.of(2026,10,12));model.put("operationsZone","America/Chicago");
        model.put("report",new technology.renatus.routepulse.reporting.workload.service.ActualDrivingRecordService.Report(
                List.of(new technology.renatus.routepulse.reporting.workload.service.ActualDrivingRecordService.SessionRow(record(),45)),
                List.of(new technology.renatus.routepulse.reporting.workload.service.ActualDrivingRecordService.DriverTotal(1L,"Victor Peña",1,2700)),45,1));
        String html=render("manager/driving-records",model,"records");
        assertThat(html).contains("Victor Peña","45","Time by driver","Manage active run").doesNotContain("Scheduled time");
    }

    @Test void retiredRoutesRedirectReadsAndRejectWritesBeforeHandler() throws Exception {
        var controller=new DummyController();
        var mvc=MockMvcBuilders.standaloneSetup(controller).addInterceptors(new RetiredPlanningRoutes()).build();
        mvc.perform(get("/driver/availability")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driver"));
        mvc.perform(get("/service-history")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/driving-records"));
        mvc.perform(post("/assignments/manual")).andExpect(status().isGone());
        assertThat(controller.calls).isZero();
    }
    @Controller static class DummyController {
        int calls;
        @RequestMapping({"/driver/availability","/service-history","/assignments/manual"}) @ResponseBody
        String mutate() { calls++; return "should not execute"; }
    }
    private Map<String,Object> model() {
        var org=new Organization("Test","Test Shuttle","test",OrganizationStatus.ACTIVE,null,null);
        var driver=new Driver(org,"100","Victor Peña",true);
        var model=new HashMap<String,Object>();model.put("driver",driver);model.put("organizationName","Test Shuttle");
        model.put("organizationSlug","test");model.put("today",LocalDate.of(2026,10,12));
        model.put("_csrf",new DefaultCsrfToken("X-CSRF","_csrf","test"));model.put("isOwner",true);
        return model;
    }
    private UnscheduledDrivingRecord record() {
        return new UnscheduledDrivingRecord(1L,"Victor Peña",ZonedDateTime.parse("2026-10-12T09:00:00-05:00[America/Chicago]"),
                null,"NFC",null,"Driving now",45,true);
    }
    private String render(String template,Map<String,Object> model,String name) throws Exception {
        var resolver=new FileTemplateResolver();resolver.setPrefix("src/main/resources/templates/");resolver.setSuffix(".html");resolver.setTemplateMode("HTML");
        var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
        String html=engine.process(template,WebTemplateContext.create(model));
        String qa=System.getProperty("screens.output");
        if (qa!=null) { Files.createDirectories(Path.of(qa));Files.writeString(Path.of(qa,name+".html"),html); }
        return html;
    }
}
