package technology.renatus.routepulse.pickup;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import technology.renatus.routepulse.driver.calendar.UkgCalendarParser;
import technology.renatus.routepulse.support.WebTemplateContext;

class PickupTests {
    private final ZoneId zone=ZoneId.of("America/Chicago");
    private byte[] calendar(String details){return ("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:test\r\nSUMMARY:ESS-Shift [07:45]\r\nDTSTART:20261101T143000Z\r\nDTEND:20261101T221500Z\r\n"+details+"END:VEVENT\r\nEND:VCALENDAR\r\n").getBytes(StandardCharsets.UTF_8);}
    @Test void extractsOnlyWorkLocationFromUkgDescriptionAndPreservesDriverParsing(){
        byte[] feed=calendar("DESCRIPTION:SF/SFFT/SFFT/FB/A1/3370 Test Hall/Training\\n\\n<a href=\"https://private.example\">Private notes</a>\r\n");
        var details=UkgCalendarParser.parseDetails(feed).get(0);
        assertThat(details.location()).isEqualTo("3370 Test Hall");
        assertThat(details.end().atZone(zone).getHour()).isEqualTo(16);
        assertThat(UkgCalendarParser.parse(feed)).containsExactly(new UkgCalendarParser.Shift(details.start(),details.end()));
        assertThat(UkgCalendarParser.parseDetails(calendar("DESCRIPTION:Private medical or personal notes\r\n")).get(0).location()).isEmpty();
        assertThat(UkgCalendarParser.parseDetails(calendar("LOCATION:Main\\, gate\r\nDESCRIPTION:SF/A/B/Other/Job\r\n")).get(0).location()).isEqualTo("Main, gate");
    }
    private PickupRider rider(String name){var r=new PickupRider(1L,name,"","fingerprint",Instant.parse("2026-10-31T12:00:00Z"));ReflectionTestUtils.setField(r,"id",1L);return r;}
    private PickupShift shift(PickupRider r,String start,String end,String location){return new PickupShift(r,new UkgCalendarParser.DetailedShift(Instant.parse(start),Instant.parse(end),location));}
    @Test void mergesContiguousSegmentsAtMidnightButKeepsSplitShiftsAndFinalLocation(){
        var rider=rider("Alex");var now=Instant.parse("2026-10-31T12:00:00Z");
        var shifts=List.of(shift(rider,"2026-11-01T02:00:00Z","2026-11-01T05:00:00Z","First hall"),
                shift(rider,"2026-11-01T05:00:00Z","2026-11-01T06:00:00Z","Final hall"),
                shift(rider,"2026-11-01T14:00:00Z","2026-11-01T22:00:00Z","Main gate"));
        assertThat(PickupStore.rows(shifts,LocalDate.of(2026,10,31),zone,now)).isEmpty();
        var rows=PickupStore.rows(shifts,LocalDate.of(2026,11,1),zone,now);
        assertThat(rows).hasSize(2);assertThat(rows.get(0).clockOut().getHour()).isEqualTo(1);
        assertThat(rows).extracting(PickupStore.Row::location).containsExactly("Final hall","Main gate");
        rider.details("Alex","North entrance");
        assertThat(PickupStore.rows(shifts,LocalDate.of(2026,11,1),zone,now)).allSatisfy(r->{assertThat(r.location()).isEqualTo("North entrance");assertThat(r.locationOverride()).isTrue();});
    }
    @Test void missingLocationsAndStaleSchedulesAreExplicit(){
        var rider=rider("Alex");var shift=shift(rider,"2026-11-01T14:00:00Z","2026-11-01T22:00:00Z","");
        var row=PickupStore.rows(List.of(shift),LocalDate.of(2026,11,1),zone,Instant.parse("2026-11-02T12:00:00Z")).get(0);
        assertThat(row.location()).contains("confirm with rider");assertThat(row.stale()).isTrue();assertThat(row.past()).isTrue();
    }
    @Test void realTemplatesRenderForDriverManagerAndEmptyStatesAndEscapeNames(){
        var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setTemplateMode("HTML");
        var engine=new SpringTemplateEngine();engine.setTemplateResolver(resolver);
        var date=LocalDate.of(2026,10,31);var time=date.atTime(16,15).atZone(zone);
        for(boolean manager:List.of(false,true)) for(boolean empty:List.of(false,true)) {
            var row=new PickupStore.Row(1L,"Alex <script>alert(1)</script>",time,"West hall",false,time,false,false);
            var person=new PickupStore.Person(1L,"Alex","",true,time,false);
            var page=new PickupStore.Page("Test park",manager,date,date,zone.getId(),empty?List.of():List.of(row),manager?List.of(person):List.of(),0);
            var model=new HashMap<String,Object>();model.put("page",page);model.put("managerView",manager);model.put("organizationName","Test park");
            model.put("pageUrl",manager?"/pickup-assistance":"/driver/pickup-assistance");model.put("driverPage","pickup-assistance");
            model.put("pickupAlerts",empty?List.of():List.of(PickupAlerts.route(row,List.of(),null,Set.of(),PickupOutcome.Status.PENDING,zone,time.minusMinutes(15).toInstant())));
            String html=engine.process("pickup/index",WebTemplateContext.create(model));
            assertThat(html).contains("Pickup assistance","Clock-out date","Check driver coverage").doesNotContain("<script>alert(1)</script>");
            if(manager)assertThat(html).contains("First name","Add rider","Pause rider");else assertThat(html).doesNotContain("name=\"subscriptionUrl\"","Add rider","Pause rider");
            if(empty)assertThat(html).contains("No scheduled clock-outs");else assertThat(html).contains("4:15 PM","West hall","&lt;script&gt;");
        }
    }
}
