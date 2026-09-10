package technology.renatus.routepulse.assignments.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.service.planning.DriverScheduleSelector;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.PickupPriority;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;

class DriverScheduleSelectorTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);
    private final DriverScheduleSelector selector = new DriverScheduleSelector();

    @Test
    void middayPrefersRealDemandCoverageBeforeFairness() {
        DriverSchedule fairestButPartial = schedule(
                1L,
                LocalTime.of(12, 0),
                LocalTime.of(12, 15)
        );
        DriverSchedule fullerCoverage = schedule(
                2L,
                LocalTime.of(12, 0),
                LocalTime.of(14, 0)
        );

        DriverSchedule selected = selector.chooseForDemandWindows(
                List.of(fairestButPartial, fullerCoverage),
                Map.of(
                        1L, List.of(fairestButPartial),
                        2L, List.of(fullerCoverage)
                ),
                List.of(
                        demand(LocalTime.of(12, 0)),
                        demand(LocalTime.of(13, 0))
                ),
                Set.of()
        );

        assertThat(selected.driverId()).isEqualTo(2L);
    }

    @Test
    void middayCoverageCanComeFromSplitWorkRows() {
        DriverSchedule firstRow = schedule(
                1L,
                LocalTime.of(12, 0),
                LocalTime.of(12, 30)
        );
        DriverSchedule secondRow = schedule(
                1L,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0)
        );
        DriverSchedule partialOtherDriver = schedule(
                2L,
                LocalTime.of(12, 0),
                LocalTime.of(12, 30)
        );

        DriverSchedule selected = selector.chooseForDemandWindows(
                List.of(partialOtherDriver, firstRow),
                Map.of(
                        1L, List.of(firstRow, secondRow),
                        2L, List.of(partialOtherDriver)
                ),
                List.of(
                        demand(LocalTime.of(12, 0)),
                        demand(LocalTime.of(13, 15))
                ),
                Set.of()
        );

        assertThat(selected.driverId()).isEqualTo(1L);
    }

    private DriverSchedule schedule(Long driverId, LocalTime start, LocalTime end) {
        return new DriverSchedule(
                driverId,
                driverId.toString(),
                "Driver " + driverId,
                DATE,
                start,
                end
        );
    }

    private TransportationDemandWindow demand(LocalTime start) {
        return new TransportationDemandWindow(
                start,
                start.plusMinutes(15),
                1,
                0,
                1,
                PickupPriority.LOW
        );
    }
}
