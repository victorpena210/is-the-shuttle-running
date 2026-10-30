package technology.renatus.routepulse.scheduling.demand;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.driving.model.*;

class DrivingDutyHistoryTests {
    private static final LocalDate DAY=LocalDate.of(2026,10,29);
    private static Instant at(int hour,int minute) {return DAY.atTime(hour,minute).toInstant(ZoneOffset.UTC);}
    private static DrivingCoverageWindow window(String service,int start,int end) {
        return new DrivingCoverageWindow(DAY,service,999L,at(start,0),at(end,0));
    }
    @Test void usesOnlyActualOverlapAndNeverCreditsTheRestOfACapturedFourHourWindow() {
        var run=mock(DrivingSession.class);
        when(run.getStartedAt()).thenReturn(at(15,30));when(run.getEndedAt()).thenReturn(at(17,0));
        when(run.getCoveragePlan()).thenReturn(List.of(window("MIDDAY_OPENING",15,17),window("SCARE_ACTORS",17,18)));
        assertThat(DrivingDutyHistory.lastServices(List.of(run),DAY.plusDays(1),at(23,0)))
                .containsExactlyEntriesOf(Map.of(ShuttleService.MIDDAY_OPENING,DAY));
        assertThat(DrivingDutyHistory.lastServices(List.of(run),DAY,at(23,0))).isEmpty();
        when(run.getEndedAt()).thenReturn(at(18,0));
        assertThat(DrivingDutyHistory.lastServices(List.of(run),DAY.plusDays(1),at(23,0)))
                .containsEntry(ShuttleService.SCARE_ACTORS,DAY);
    }
    @Test void activeSessionsCountOnlyElapsedDrivingAndRespectTheAutomaticLimit() {
        var run=mock(DrivingSession.class);
        when(run.isActive()).thenReturn(true);when(run.getStartedAt()).thenReturn(at(15,30));
        when(run.getAutomaticEndAt()).thenReturn(at(19,30));
        when(run.getCoveragePlan()).thenReturn(List.of(window("MIDDAY_OPENING",15,17),window("SCARE_ACTORS",17,18),window("EMPLOYEE_PICKUP",20,23)));
        assertThat(DrivingDutyHistory.lastServices(List.of(run),DAY.plusDays(1),at(16,0)))
                .containsExactlyEntriesOf(Map.of(ShuttleService.MIDDAY_OPENING,DAY));
        assertThat(DrivingDutyHistory.lastServices(List.of(run),DAY.plusDays(1),at(23,0)))
                .containsOnlyKeys(ShuttleService.MIDDAY_OPENING,ShuttleService.SCARE_ACTORS);
    }
}
