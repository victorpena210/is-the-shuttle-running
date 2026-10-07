package technology.renatus.routepulse.driver.availability;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.domain.*;
import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;

/** Runs independently of Maven/Docker, and from JUnit in the normal suite. */
public class DriverAvailabilityChecks {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        run();
        if (args.length > 0) {
            var read = new ScheduleScreenshotReader("tesseract").read(file(Path.of(args[0])));
            var parsed = ScheduleScreenshotParser.parse(read.text(),2026);
            check(parsed.rows().stream().anyMatch(r -> r.date().equals("2026-10-09") && r.start().equals("15:00") && r.end().equals("21:00")),"real OCR recognizes Friday shift: " + read.text());
            check(parsed.rows().stream().anyMatch(r -> r.date().equals("2026-10-10") && r.off()),"real OCR recognizes explicit off day");
        }
        System.out.println("Driver availability checks passed (" + assertions + " assertions).");
    }
    public static void run() throws Exception {
        parser(); validation(); service();
        rejects(() -> new ScheduleScreenshotReader("not-installed").read(bytesFile("not a picture".getBytes())),"reject non-image bytes");
    }
    private static void parser() {
        var result=ScheduleScreenshotParser.parse("Friday October 9, 2026\n3:00 PM - 9:00 PM\nSaturday October 10, 2026\nOFF",2026);
        check(result.rows().size()==2,"two dated entries");
        check(result.rows().get(0).start().equals("15:00") && result.rows().get(0).end().equals("21:00"),"12-hour conversion");
        check(result.rows().get(1).off(),"only explicit off becomes off");
        check(ScheduleScreenshotParser.parse("10/09/2026 3:00 - 9:00",2026).rows().get(0).start().isEmpty(),"missing AM/PM stays blank");
        check(ScheduleScreenshotParser.parse("15:00-21:00",2026).rows().get(0).date().isEmpty(),"missing date stays blank");
        check(ScheduleScreenshotParser.parse("2026-10-09 15:00-21:00",2026).rows().get(0).start().equals("15:00"),"ISO date and 24-hour times");
        check(ScheduleScreenshotParser.parse("Dec 31, 2026\n9 PM - 12 AM\nJan 1, 2027\nOFF",2026).rows().get(1).date().equals("2027-01-01"),"explicit next year retained");
        check(ScheduleScreenshotParser.parse("10/9 - 10/15\n3 PM - 9 PM",2026).rows().get(0).date().isEmpty(),"range is not guessed as a date");
        check(ScheduleScreenshotParser.parse("October 9, 2026",2026).rows().isEmpty(),"missing hours are not a day off");
        check(ScheduleScreenshotParser.parse("10/9 3 PM - 9 PM\n10/9 3 PM - 9 PM",2026).rows().size()==1,"duplicate screenshot rows deduplicated");
    }
    private static AvailabilityForm form(LocalDate date,String start,String end,boolean off) {
        var f=new AvailabilityForm(); f.setConfirmed(true);
        f.getRows().add(new AvailabilityForm.Row(new ScheduleScreenshotParser.Row(date.toString(),start,end,off))); return f;
    }
    private static void validation() {
        LocalDate day=LocalDate.of(2026,10,9);
        check(form(day,"15:00","21:00",false).validated(day).size()==1,"only explicitly supplied date changes");
        check(form(day,"","",true).validated(day).get(day).isEmpty(),"off requires explicit selection");
        rejects(() -> form(day,"","21:00",false).validated(day),"missing time");
        rejects(() -> form(day.minusDays(1),"15:00","21:00",false).validated(day),"past edits blocked");
        rejects(() -> form(day,"15:00","15:00",false).validated(day),"zero length blocked");
        rejects(() -> form(day,"09:00","08:00",false).validated(day),"23-hour typo blocked");
        var f=form(day,"21:00","00:00",false);
        check(f.validated(day).size()==1,"overnight valid");
        f.getRows().add(new AvailabilityForm.Row(new ScheduleScreenshotParser.Row(day.toString(),"22:00","23:00",false)));
        rejects(() -> f.validated(day),"overlap rejected");
        var split=form(day,"09:00","12:00",false);
        split.getRows().add(new AvailabilityForm.Row(new ScheduleScreenshotParser.Row(day.toString(),"15:00","18:00",false)));
        check(split.validated(day).get(day).size()==2,"split shift kept");
        split.getRows().add(new AvailabilityForm.Row(new ScheduleScreenshotParser.Row(day.toString(),"","",true)));
        rejects(() -> split.validated(day),"off and working conflict");
        var confirmation=form(day,"15:00","21:00",false); confirmation.setConfirmed(false);
        rejects(() -> confirmation.validated(day),"confirmation required");
        var overnight=form(day,"21:00","03:00",false);
        overnight.getRows().add(new AvailabilityForm.Row(new ScheduleScreenshotParser.Row(day.plusDays(1).toString(),"02:00","06:00",false)));
        rejects(() -> overnight.validated(day),"cross-midnight overlap blocked");
    }
    private static void service() throws Exception {
        var org=new Organization("Test","Test","test",technology.renatus.routepulse.organization.domain.OrganizationStatus.ACTIVE,null,null); id(org,1L);
        var driver=new Driver(org,"100","Own driver",true); id(driver,10L);
        var other=new Driver(org,"200","Other driver",true); id(other,20L);
        LocalDate day=LocalDate.now(ZoneId.of("America/Chicago")).plusDays(1);
        List<DriverAvailabilityRevision> saved=new ArrayList<>();
        var revisionRepo=proxy(DriverAvailabilityRepository.class,(method,args) -> switch(method) {
            case "version" -> saved.isEmpty()?0L:saved.get(saved.size()-1).getId();
            case "latest" -> saved.isEmpty()?List.of():List.of(saved.get(saved.size()-1));
            case "save" -> {var r=(DriverAvailabilityRevision)args[0]; id(r,(long)saved.size()+1); saved.add(r); yield r;}
            default -> null;
        });
        var drivers=proxy(DriverRepository.class,(method,args)->switch(method) {
            case "findAllByOrganization_IdAndActiveTrueOrderByNameAsc" -> List.of(driver,other);
            case "findByIdAndOrganization_Id" -> Optional.of(driver);
            default -> null;
        });
        var organizationRepo=proxy(OrganizationRepository.class,(method,args)->Optional.of(org));
        var imported=proxy(UploadedShiftRepository.class,(method,args)->List.of());
        var assignment=DriverAssignment.automatic(day,ShuttlePeriod.MIDDAY,driver); id(assignment,100L);
        assignment.updateScheduledTimes(LocalTime.of(15,0),LocalTime.of(18,0));
        var assignments=proxy(DriverAssignmentRepository.class,(method,args)->List.of(assignment));
        var updates=new AvailabilityUpdateService(revisionRepo,organizationRepo,drivers,imported,assignments,"America/Chicago");
        updates.confirm(driver,0,form(day,"17:00","21:00",false),"MANUAL",null,"driver@example.com");
        check(saved.size()==1 && saved.get(0).getOrganizationId().equals(1L),"revision tied to resolved organization");
        check(assignment.isAvailabilityUpdateHold(),"assignment held after update");
        check(assignment.getScheduledStartTime().equals(LocalTime.of(15,0)),"assignment time preserved");
        check(!assignment.canAutomaticallyRebalance(day.atStartOfDay()),"automatic rebalance blocked");
        rejects(() -> updates.confirm(driver,0,form(day,"18:00","21:00",false),"MANUAL",null,"driver@example.com"),"stale/replayed form rejected");
        var schedules=new DriverScheduleService(drivers,(organization,date)->List.of(
                new ShiftRecord("100","Own driver","HR","Wardrobe",day,LocalTime.of(9,0),LocalTime.of(12,0),"A"),
                new ShiftRecord("200","Other driver","HR","Wardrobe",day,LocalTime.of(9,0),LocalTime.of(21,0),"A")),revisionRepo);
        var effective=schedules.getDriverSchedulesForDate(1L,day).scheduledDrivers();
        check(effective.size()==2,"other drivers retained");
        check(effective.stream().anyMatch(s->s.driverId().equals(10L)&&s.startTime().equals(LocalTime.of(17,0))),"driver confirmation overrides conflicting import");
        check(!AvailabilityReviewService.safelyCovered(assignment,effective,List.of(assignment),LocalTime.of(19,0)),"3–6 duty conflicts with 5–9 work hours");
        updates.confirm(driver,1,form(day,"","",true),"MANUAL",null,"driver@example.com");
        var off=schedules.getDriverSchedulesForDate(1L,day);
        check(off.scheduledDrivers().size()==1&&off.driversWithoutSchedules().contains(driver),"explicit off removes only own availability");
        var fallback=new DriverAvailabilityRevision(driver,day,"","","",true,"MANAGER_IMPORT",null,"manager@example.com"); id(fallback,3L);saved.add(fallback);
        check(schedules.getDriverSchedulesForDate(1L,day).scheduledDrivers().stream().anyMatch(s->s.driverId().equals(10L)&&s.startTime().equals(LocalTime.of(9,0))),"manager reversion restores import");
    }
    interface Call { Object invoke(String method,Object[] args) throws Throwable; }
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,Call call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->call.invoke(m.getName(),a));
    }
    static void id(Object entity,Long value) throws Exception {var field=entity.getClass().getDeclaredField("id");field.setAccessible(true);field.set(entity,value);}
    static void check(boolean condition,String message) {assertions++;if(!condition)throw new AssertionError(message);}
    static void rejects(Runnable action,String message) {try{action.run();}catch(IllegalArgumentException ex){assertions++;return;}throw new AssertionError(message);}
    private static MultipartFile file(Path path) throws IOException {return bytesFile(Files.readAllBytes(path));}
    private static MultipartFile bytesFile(byte[] bytes) {return new MultipartFile() {
        public String getName(){return "screenshots";}public String getOriginalFilename(){return "schedule.png";}
        public String getContentType(){return "image/png";}public boolean isEmpty(){return bytes.length==0;}
        public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
        public void transferTo(File target)throws IOException{Files.write(target.toPath(),bytes);}
    };}
}
