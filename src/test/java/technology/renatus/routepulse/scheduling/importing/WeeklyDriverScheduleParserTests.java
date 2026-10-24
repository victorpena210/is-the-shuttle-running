package technology.renatus.routepulse.scheduling.importing;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WeeklyDriverScheduleParserTests {
    private final DayforceCsvParser parser=new DayforceCsvParser();
    @Test void readsMergedUkgReportWithDatesAndBreakContinuationRows() throws Exception {
        var p=parser.parseSchedule(WeeklyScheduleFixture.file("Sample, Alex","9:00 AM - 12:00 PM","12:45 PM - 5:00 PM","9:00 AM - 2:00 PM"));
        assertThat(p.weeklyDriverReport()).isTrue();assertThat(p.shifts()).hasSize(3);
        assertThat(p.driverDates().get("Sample, Alex")).hasSize(7).contains(LocalDate.of(2026,11,2),LocalDate.of(2026,11,8));
        assertThat(p.shifts().get(2).getStartTime()).isEqualTo(LocalTime.of(12,45));
        assertThat(p.shifts().get(2).getShiftDate()).isEqualTo(LocalDate.of(2026,11,7));
    }
    @Test void allOffWeekStillHasCoverageToRemoveOldAvailability() throws Exception {
        var p=parser.parseSchedule(WeeklyScheduleFixture.file("Sample, Alex","OFF","",""));
        assertThat(p.shifts()).isEmpty();assertThat(p.driverDates().get("Sample, Alex")).hasSize(7);
    }
    @Test void acceptsOvernightAndMultipleRangesAndDeduplicates() throws Exception {
        var p=parser.parseSchedule(WeeklyScheduleFixture.file("Sample, Alex","9:00 AM - 12:00 PM\n12:45 PM – 5:00 PM","9:00 AM - 12:00 PM","11:00 PM - 1:00 AM"));
        assertThat(p.shifts()).hasSize(3);assertThat(p.shifts().get(2).getEndTime()).isEqualTo(LocalTime.of(1,0));
    }
    @Test void rejectsMalformedOrAmbiguousWorkHours() throws Exception {
        for(String value:List.of("9:00 - 5:00","9:00 AM - 9:00 AM","25:00 PM - 5:00 PM","Vacation - 8 hours"))
            assertThatThrownBy(()->parser.parseSchedule(WeeklyScheduleFixture.file("Sample, Alex",value,"","")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("worksheet 1, row 10");
    }
    @Test void rejectsUndatedOrNonconsecutiveWeekAndNamelessHours() {
        var heading=new ScheduleTableReader.Row(List.of("Employee","Mon","Tue","Wed","Thu","Fri","Sat","Sun"),"row 1");
        var dates=new ArrayList<String>();dates.add("");for(int d=2;d<=8;d++)dates.add("2026-11-0"+d);
        var dateRow=new ScheduleTableReader.Row(dates,"row 2");
        var work=new ScheduleTableReader.Row(List.of("","9:00 AM - 5:00 PM"),"row 3");
        assertThatThrownBy(()->WeeklyDriverScheduleParser.parse(List.of(List.of(heading,dateRow,work)))).hasMessageContaining("no employee name");
        dates.set(2,"2026-11-04");
        assertThatThrownBy(()->WeeklyDriverScheduleParser.parse(List.of(List.of(heading,dateRow,work)))).hasMessageContaining("consecutively");
        assertThatThrownBy(()->WeeklyDriverScheduleParser.parse(List.of(List.of(heading)))).hasMessageContaining("missing");
    }
}
