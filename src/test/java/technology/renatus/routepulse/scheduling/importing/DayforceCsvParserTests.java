package technology.renatus.routepulse.scheduling.importing;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DayforceCsvParserTests {
    private final DayforceCsvParser parser = new DayforceCsvParser();
    private MockMultipartFile file(String text) { return new MockMultipartFile("scheduleFile", "schedule.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8)); }

    @Test void acceptsFullReorderedHeadingsPreambleBomAndQuotedNamesAndDeduplicates() {
        String headings = "\uFEFFScheduled Shift End Date Time,Employee Full Name,Employee ID,Scheduled Shift Start Date Time,Primary Job,Primary location (path)\n";
        String row = "10/25/2026 12:00 am,\"Pena, Test\",100,10/24/2026 11:00 pm,Staff,Park/Ops/Area/Department/Gate\n";
        var shifts = parser.parse(file("Dayforce report\nGenerated today\n\n" + headings + row + row));
        assertThat(shifts).hasSize(1);
        assertThat(shifts.get(0).getShiftDate()).isEqualTo(LocalDate.of(2026,10,24));
        assertThat(shifts.get(0).getStartTime()).isEqualTo(LocalTime.of(23,0));
        assertThat(shifts.get(0).getEndTime()).isEqualTo(LocalTime.MIDNIGHT);
    }
    @Test void skipsUnscheduledRowsAndAcceptsOnlyTheRequiredFourColumns() {
        var shifts = parser.parse(file("Employee ID,Employee Name,Shift Start,Shift End\n100,A,,\n200,B,10/24/2026 9:00 AM,10/24/2026 5:00 PM\n"));
        assertThat(shifts).hasSize(1); assertThat(shifts.get(0).getEmployeeId()).isEqualTo("200");
    }
    @Test void rejectsInvalidDatesPartialTimesAndUnrepresentableMultiDayShifts() {
        for (String row : java.util.List.of("100,A,2/29/2026 9:00 AM,3/1/2026 5:00 PM",
                "100,A,10/24/2026 9:00 AM,", "100,A,10/24/2026 9:00 AM,10/24/2026 8:00 AM",
                "100,A,10/24/2026 9:00 AM,10/26/2026 5:00 PM"))
            assertThatThrownBy(() -> parser.parse(file("Employee ID,Employee Name,Shift Start,Shift End\n" + row)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("record 2");
    }
    @Test void rejectsMissingOrAmbiguousHeadersAndEmptyFiles() {
        for (String text : java.util.List.of("", "Not a schedule", "Employee ID,Employee Name,Shift Start\n",
                "Employee ID,Employee Name,Shift Start,Shift End,Shift End\n"))
            assertThatThrownBy(() -> parser.parse(file(text))).isInstanceOf(IllegalStateException.class);
    }
}
