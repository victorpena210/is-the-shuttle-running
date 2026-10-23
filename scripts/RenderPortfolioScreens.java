import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.support.WebTemplateContext;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointPreviewAction;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.UnscheduledDrivingRecord;
import technology.renatus.routepulse.reporting.workload.service.ActualDrivingRecordService;

/** Render current application templates with fictional, fixed data. No database is used. */
public class RenderPortfolioScreens {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "target/portfolio-screens" : args[0]);
        Files.createDirectories(output);
        var resolver = new FileTemplateResolver();
        resolver.setPrefix("src/main/resources/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var org = new Organization("Demo", "Sample Shuttle", "sample", OrganizationStatus.ACTIVE, null, null);
        var jordan = new Driver(org, "DEMO-01", "Jordan Lee", true);
        var alex = new Driver(org, "DEMO-02", "Alex Rivera", true);
        var idField = Driver.class.getDeclaredField("id");
        idField.setAccessible(true); idField.set(jordan, 1L); idField.set(alex, 2L);
        var base = new HashMap<String,Object>();
        base.put("organizationName", "Sample Shuttle");
        base.put("organizationSlug", "sample");
        base.put("_csrf", new DefaultCsrfToken("X-CSRF", "_csrf", "fictional-preview-only"));
        base.put("driver", jordan);
        base.put("today", LocalDate.of(2026,10,23));
        var rider = new HashMap<>(base);
        rider.put("running", true); rider.put("driverName", "Jordan");
        render(engine, output, "rider", "public/rider-status", rider);
        var checkpoint = new HashMap<>(base);
        checkpoint.put("checkpointMethod", DrivingActionMethod.NFC);
        checkpoint.put("checkpointLabel", "NFC tap");
        checkpoint.put("checkpointCode", "fictional-preview-only");
        checkpoint.put("formAction", "/driver/tap");
        checkpoint.put("checkpointAction", CheckpointPreviewAction.PAUSE_OR_END);
        checkpoint.put("takeoverRequired", false);
        render(engine, output, "driver", "driver/scan", checkpoint);
        var history = new HashMap<>(base);
        history.put("drivers", List.of(jordan, alex));
        history.put("fromDate", LocalDate.of(2026,10,23));
        history.put("toDate", LocalDate.of(2026,10,23));
        history.put("operationsZone", "America/Chicago");
        var rows = List.of(
            new ActualDrivingRecordService.SessionRow(record(3L,"Alex Rivera", "11:00", "11:40", 40),40),
            new ActualDrivingRecordService.SessionRow(record(2L,"Jordan Lee", "09:45", "10:30", 45),45),
            new ActualDrivingRecordService.SessionRow(record(1L,"Jordan Lee", "09:00", "09:30", 30),30));
        history.put("report", new ActualDrivingRecordService.Report(rows, List.of(
            new ActualDrivingRecordService.DriverTotal(2L,"Alex Rivera",1,2400),
            new ActualDrivingRecordService.DriverTotal(1L,"Jordan Lee",2,4500)),115,0));
        render(engine, output, "history", "manager/driving-records", history);
    }
    private static UnscheduledDrivingRecord record(Long id, String name, String from, String to, long minutes) {
        var zone = ZoneId.of("America/Chicago");
        return new UnscheduledDrivingRecord(id,name,LocalDateTime.parse("2026-10-23T"+from).atZone(zone),
            LocalDateTime.parse("2026-10-23T"+to).atZone(zone),"NFC","NFC","Completed",minutes,false);
    }
    private static void render(SpringTemplateEngine engine, Path output, String name, String template, Map<String,Object> model) throws Exception {
        Files.writeString(output.resolve(name+".html"), engine.process(template, WebTemplateContext.create(model)));
    }
}
