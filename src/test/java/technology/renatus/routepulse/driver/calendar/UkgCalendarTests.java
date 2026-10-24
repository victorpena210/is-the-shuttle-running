package technology.renatus.routepulse.driver.calendar;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.Test;

class UkgCalendarTests {
    private byte[] calendar(String body){return ("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"+body+"END:VCALENDAR\r\n").getBytes(StandardCharsets.UTF_8);}
    private String event(String uid,int version,String start,String end,String extra){return "BEGIN:VEVENT\r\nUID:"+uid+"\r\nSEQUENCE:"+version+"\r\nSUMMARY:ESS-Shift [08:00]\r\nDTSTART"+start+"\r\nDTEND"+end+"\r\n"+extra+"END:VEVENT\r\n";}
    @Test void parsesRealUkgShapeWithTimeZoneDefinitionsFoldedDescriptionsAndOvernightUtcShifts(){
        String zone="BEGIN:VTIMEZONE\r\nTZID:America/Chicago\r\nBEGIN:STANDARD\r\nRRULE:FREQ=YEARLY\r\nDTSTART:19701101T020000\r\nEND:STANDARD\r\nEND:VTIMEZONE\r\n";
        String shift=event("one",0,":20261024T220000Z",":20261025T020000Z","DESCRIPTION:First line\r\n second line\r\n");
        var parsed=UkgCalendarParser.parse(calendar(zone+shift+shift));
        assertThat(parsed).hasSize(1);assertThat(parsed.get(0).start()).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
        assertThat(parsed.get(0).end().atZone(ZoneId.of("America/Chicago")).getHour()).isEqualTo(21);
    }
    @Test void mostRecentUidVersionWinsAndCancellationRemovesTheShift(){
        var first=event("one",0,":20261024T140000Z",":20261024T220000Z","");
        var second=event("one",1,":20261024T150000Z",":20261024T230000Z","");
        assertThat(UkgCalendarParser.parse(calendar(second+first))).singleElement().extracting(UkgCalendarParser.Shift::start).isEqualTo(Instant.parse("2026-10-24T15:00:00Z"));
        var cancelled="BEGIN:VEVENT\r\nUID:one\r\nSEQUENCE:2\r\nSTATUS:CANCELLED\r\nEND:VEVENT\r\n";
        assertThat(UkgCalendarParser.parse(calendar(first+second+cancelled))).isEmpty();
    }
    @Test void malformedOrUnsupportedCalendarsNeverBecomeEmptyAvailability(){
        String event=event("one",0,":20261024T140000Z",":20261024T220000Z","");
        for(byte[] invalid:new byte[][]{"<html>login</html>".getBytes(),calendar(event.replace("DTEND", "NOEND")),
                calendar(event.replace("ESS-Shift [08:00]","ESS-Vacation")),calendar(event.replace("END:VEVENT","RRULE:FREQ=DAILY\r\nEND:VEVENT")),
                calendar(event.replace("20261024T220000Z","20261023T220000Z"))}) {
            assertThatThrownBy(()->UkgCalendarParser.parse(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(UkgCalendarParser.parse(calendar(""))).isEmpty();
        assertThatThrownBy(()->UkgCalendarParser.parse(new byte[1_048_577])).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void ianaTimesRespectDstAndAmbiguousOrFloatingTimesAreRejected(){
        var parsed=UkgCalendarParser.parse(calendar(event("a",0,";TZID=America/Chicago:20261102T090000",";TZID=America/Chicago:20261102T170000","")));
        assertThat(parsed.get(0).start()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
        for(String start:new String[]{";TZID=America/Chicago:20261101T013000",":20261102T090000",";TZID=Unknown:20261102T090000"})
            assertThatThrownBy(()->UkgCalendarParser.parse(calendar(event("a",0,start,":20261102T230000Z","")))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void onlyTheExactUkgSubscriptionEndpointIsAccepted(){
        var client=new UkgCalendarClient();String path="/api/calendar_sync/fictional_token_0123456789";
        assertThat(client.normalize("webcal://"+UkgCalendarClient.HOST+path)).isEqualTo("https://"+UkgCalendarClient.HOST+path);
        for(String prefix:new String[]{"http://","file://","https://localhost/","https://127.0.0.1/","https://evil.example/","https://user:pass@"})
            assertThatThrownBy(()->client.normalize(prefix+UkgCalendarClient.HOST+path)).isInstanceOf(IllegalArgumentException.class);
        for(String suffix:new String[]{"?token=private","#fragment","/extra","%2fextra"})
            assertThatThrownBy(()->client.normalize("https://"+UkgCalendarClient.HOST+path+suffix)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->client.normalize("https://"+UkgCalendarClient.HOST+":443"+path)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->client.normalize("https://"+UkgCalendarClient.HOST+".evil.example"+path)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void subscriptionTokensAreEncryptedAndBoundToTheirOrganizationAndDriver(){
        var cipher=new CalendarLinkCipher("test-only-secret");String link="https://example.test/private-token";
        String a=cipher.encrypt(1L,2L,link),b=cipher.encrypt(1L,2L,link);
        assertThat(a).doesNotContain("private-token").isNotEqualTo(b);assertThat(cipher.decrypt(1L,2L,a)).isEqualTo(link);
        assertThatThrownBy(()->cipher.decrypt(2L,2L,a)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->cipher.decrypt(1L,3L,a)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new CalendarLinkCipher("different-key").decrypt(1L,2L,a)).isInstanceOf(IllegalStateException.class);
    }
}
