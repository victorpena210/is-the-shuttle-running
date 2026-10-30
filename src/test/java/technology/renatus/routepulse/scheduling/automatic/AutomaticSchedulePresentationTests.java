package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.*;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleHistory.RecordedRun;
import technology.renatus.routepulse.scheduling.demand.ShuttleService;
import technology.renatus.routepulse.support.WebTemplateContext;

class AutomaticSchedulePresentationTests {
    private final LocalDate today=LocalDate.of(2026,10,24);
    private final LocalDateTime now=today.atTime(18,53);
    private final ZoneId zone=ZoneId.of("America/Chicago");

    @Test void currentAndOvernightShiftsRemainUpcomingButEndedPlansDoNotCountAsCoverageGaps() {
        var ended=new Slot(now.minusHours(1),now,null,null,"No driver");
        var current=new Slot(now.minusMinutes(10),now.plusMinutes(20),1L,"Sample Driver","Assigned");
        var overnight=new Slot(today.atTime(23,0),today.plusDays(1).atTime(1,0),null,null,"No driver");
        var view=day(today,List.of(ended,current,overnight));
        assertThat(view.upcomingSlots(now)).containsExactly(current,overnight);
        assertThat(view.earlierSlots(now)).containsExactly(ended);
        assertThat(view.coverageGaps(now)).isEqualTo(1);
        assertThat(view.upcomingSlots(today.plusDays(1).atStartOfDay())).containsExactly(overnight);
        assertThat(view.coverageGaps(today.plusDays(1).atTime(1,0))).isZero();
    }

    @Test void compactManagerPageKeepsUpcomingWorkAheadOfActualHistoryAndPreservesControls() throws Exception {
        var tomorrow=today.plusDays(1);
        var next=day(tomorrow,List.of(new Slot(tomorrow.atTime(17,0),tomorrow.atTime(17,45),2L,"Another Driver","Assigned",ShuttleService.SCARE_ACTORS)));
        var unknown=new View(today.plusDays(2),"UNKNOWN","STALE_SNAPSHOT",LocalTime.of(17,0),LocalTime.of(22,0),null,null,
                now.atZone(zone),"Calendar needs verification",List.of());
        var closed=new View(today.plusDays(3),"CLOSED","LIVE_API",null,null,null,null,now.atZone(zone),"Park closed",List.of());
        String html=render(true,List.of(sampleDay(),next,unknown,closed),today,"manager");
        assertThat(html).contains("Upcoming assignments","Recorded driving","1 shift needs coverage","1 hr 6 min",
                "1 hr 49 min","2 hr 55 min logged","Hours confirmed by a manager","/driving-schedule/hours",
                "Review driving record for Sample Driver","Earlier assignments (1)")
                .doesNotContain("MANAGER_CONFIRMATION");
        assertThat(html.indexOf("Upcoming assignments")).isLessThan(html.indexOf("<section class=\"schedule-section recorded-driving\""));
        assertThat(openDates(html)).containsExactly(today.toString());
        assertThat(html).contains("5:45 PM is an estimate","continue until everyone is transported");
    }

    @Test void driverAndHistoryOnlyViewsKeepTheirOwnRecordsWithoutManagerControls() throws Exception {
        var history=new View(today.minusDays(7),"HISTORY","DRIVING_HISTORY",null,null,null,null,now.atZone(zone),
                "No saved schedule",List.of(),List.of(record(9,36,10,42,66)),false);
        String html=render(false,List.of(history),history.date(),"driver-history");
        assertThat(html).contains("No saved schedule","My recorded driving","Sample Driver","1 hr 6 min")
                .doesNotContain("Upcoming assignments","/driving-records/sessions/","/driving-schedule/hours","Refresh assignments");
        assertThat(openDates(html)).containsExactly(history.date().toString());
        String empty=render(false,List.of(),today,"empty");
        assertThat(empty).contains("No saved schedules or recorded driving for this week");
        assertThat(AutomaticScheduleHistory.formatDuration(45)).isEqualTo("45 min");
        assertThat(AutomaticScheduleHistory.formatDuration(120)).isEqualTo("2 hr");
    }

    @Test void managersAndDriversSeeTemporaryWardrobeClosureInstructionsOnlyForTheAssignedException() throws Exception {
        var date=today.plusDays(1);
        var instruction="Temporarily close wardrobe for scare-actor transport; reopen when you return.";
        var solo=day(date,List.of(new Slot(date.atTime(17,0),date.atTime(17,45),1L,"Solo Driver",instruction,ShuttleService.SCARE_ACTORS)));
        for(boolean manager:List.of(true,false)) {
            assertThat(render(manager,List.of(solo),date,"solo-actors-"+manager))
                    .contains("Solo Driver",instruction).doesNotContain("Needs coverage");
            var staffed=day(date,List.of(new Slot(date.atTime(17,0),date.atTime(17,45),1L,"Solo Driver","Automatically assigned",ShuttleService.SCARE_ACTORS)));
            assertThat(render(manager,List.of(staffed),date,"staffed-actors-"+manager)).doesNotContain(instruction,"Automatically assigned");
        }
    }

    @Test void driversSeeTheFullTeamPlanWithTheirOwnRowsMarkedAndCoverageGapsVisible() throws Exception {
        var date=today.plusDays(1);
        var team=day(date,List.of(
                new Slot(date.atTime(9,0),date.atTime(12,0),1L,"My Driver","Assigned",ShuttleService.MORNING_OPENING),
                new Slot(date.atTime(17,0),date.atTime(17,45),2L,"Other Driver","Assigned",ShuttleService.SCARE_ACTORS),
                new Slot(date.atTime(19,0),date.atTime(23,0),null,null,"No available driver covers this full window")));
        String html=render(false,List.of(team),date,"team-driver");
        assertThat(html).contains("Team driving assignments","Team assignments","My Driver","Other Driver",
                "Needs coverage","1 shift needs coverage","3 shifts","Scare-actor transport")
                .doesNotContain("My driving assignments","Refresh assignments","/driving-schedule/hours","/driving-schedule/confirm-open");
        assertThat(Pattern.compile(">You</span>").matcher(html).results().count()).isEqualTo(1);
        String manager=render(true,List.of(team),date,"team-manager");
        assertThat(manager).doesNotContain(">You</span>");
    }

    private View sampleDay() {
        return day(today,List.of(
                new Slot(today.atTime(9,0),today.atTime(12,0),1L,"Sample Driver","Assigned",ShuttleService.MORNING_OPENING),
                new Slot(today.atTime(19,0),today.atTime(21,0),1L,"Sample Driver","Assigned"),
                new Slot(today.atTime(21,0),today.atTime(23,0),null,null,"No available driver covers this full window")))
                .withHistory(List.of(record(9,36,10,42,66),record(17,3,18,52,109)));
    }
    @Test void workSchedulesAppearBeforeAssignmentsWithSplitShiftsAndOvernightLabels() throws Exception {
        var shifts=List.of(new DailyStaffScheduleService.WorkShift(1L,"Alex <script>",today.atTime(9,0),today.atTime(13,0)),
                new DailyStaffScheduleService.WorkShift(1L,"Alex <script>",today.atTime(15,0),today.atTime(19,0)),
                new DailyStaffScheduleService.WorkShift(2L,"Overnight Driver",today.minusDays(1).atTime(22,0),today.atTime(6,0)),
                new DailyStaffScheduleService.WorkShift(3L,"Late Driver",today.atTime(19,0),today.plusDays(1).atTime(1,0)));
        var view=sampleDay().withWorkShifts(shifts);
        String manager=render(true,List.of(view),today,"roster");
        assertThat(manager).contains("Who’s working","Saved work hours","Alex &lt;script&gt;","Started previous day","Ends next day",
                "datetime=\"2026-10-24T13:00\"","datetime=\"2026-10-24T15:00\"")
                .doesNotContain("Alex <script>");
        assertThat(manager.indexOf("Who’s working")).isLessThan(manager.indexOf("Upcoming assignments"));
        assertThat(view.withHistory(List.of()).workShifts()).isEqualTo(shifts);
        assertThat(render(false,List.of(view.withWorkShifts(List.of(shifts.get(0)))),today,"personal-work"))
                .contains("My work schedule").doesNotContain("Who’s working","Overnight Driver","Late Driver");
        assertThat(render(true,List.of(sampleDay()),today,"no-work"))
                .contains("No saved work hours for this day.").doesNotContain("Everyone is off");
    }
    private RecordedRun record(int sh,int sm,int eh,int em,long minutes) {
        return new RecordedRun(1L,1L,"Sample Driver",today.atTime(sh,sm).atZone(zone),today.atTime(eh,em).atZone(zone),minutes,false,false,"Recorded");
    }
    private View day(LocalDate date,List<Slot> slots) {
        return new View(date,"OPEN","MANAGER_CONFIRMATION",LocalTime.of(11,0),LocalTime.of(23,0),
                LocalTime.of(9,0),LocalTime.of(19,0),now.atZone(zone),"Saved plan",slots);
    }
    private List<String> openDates(String html) {
        var result=new ArrayList<String>();var matcher=Pattern.compile("<details[^>]+>").matcher(html);
        while(matcher.find()) {
            var tag=matcher.group();var date=Pattern.compile("data-date=\"([^\"]+)\"").matcher(tag);
            if(date.find() && Pattern.compile("\\sopen(?:=|\\s|>)").matcher(tag).find()) result.add(date.group(1));
        }
        return result;
    }
    private String render(boolean manager,List<View> days,LocalDate from,String name) throws Exception {
        var model=new HashMap<String,Object>();
        model.put("managerView",manager);model.put("organizationName","Sample Shuttle");model.put("days",days);
        model.put("today",today);model.put("now",now);model.put("from",from);model.put("operationsZone",zone.getId());
        model.put("scheduleUrl",manager?"/driving-schedule":"/driver/driving-schedule");
        model.put("isOwner",manager);model.put("canManageDrivingQr",manager);model.put("currentDriverId",manager?null:1L);
        var resolver=new FileTemplateResolver();resolver.setPrefix("src/main/resources/templates/");resolver.setSuffix(".html");resolver.setTemplateMode("HTML");
        var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
        String html=engine.process("demand/driving-schedule",WebTemplateContext.create(model));
        String output=System.getProperty("screens.output");
        if(output!=null) { Files.createDirectories(Path.of(output));Files.writeString(Path.of(output,"assignments-"+name+".html"),html); }
        return html;
    }
}
