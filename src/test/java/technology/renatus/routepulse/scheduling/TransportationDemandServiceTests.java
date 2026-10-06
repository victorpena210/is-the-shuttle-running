package technology.renatus.routepulse.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.importing.ScheduleProvider;
import technology.renatus.routepulse.scheduling.planning.MorningDrivingPlanningService;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;

class TransportationDemandServiceTests {

    private static final Long ORGANIZATION_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 6);

    @Test
    void separatesMorningMiddayEveningAndOffHoursDemand() {

        ScheduleProvider provider = (organizationId, date) -> List.of(
                shift("1001", LocalTime.of(9, 0), LocalTime.of(17, 30)),
                shift("1002", LocalTime.of(9, 7), LocalTime.of(13, 0)),
                shift("1003", LocalTime.of(12, 15), LocalTime.of(18, 0)),
                shift("1004", LocalTime.of(13, 5), LocalTime.of(18, 5)),
                shift("1005", LocalTime.of(17, 10), LocalTime.of(21, 0)),
                shift("1006", LocalTime.of(2, 0), LocalTime.of(5, 0)),
                shift("1007", LocalTime.of(18, 0), LocalTime.of(21, 10))
        );

        TransportationDemandAnalysis analysis = service(provider)
                .getDemandForDate(
                        ORGANIZATION_ID,
                        DATE
                );

        assertEquals(2, analysis.getMorning().getTotalCount());
        assertEquals(3, analysis.getMidday().getTotalCount());
        assertEquals(4, analysis.getEvening().getTotalCount());

        /*
         * 2:00 AM start + 5:00 AM end + 9:10 PM end.
         * The exact 9:00 PM pickup above remains normal evening demand.
         */
        assertEquals(3, analysis.getOffHoursMovementCount());

        assertTrue(analysis.hasOperationalDemand());
        assertTrue(analysis.hasOffHoursDemand());
    }

    @Test
    void usesMorningClockInPlanAndStartsEveningAtFirstPickupWindow() {

        ScheduleProvider provider = (organizationId, date) -> List.of(
                shift("2001", LocalTime.of(9, 0), LocalTime.of(17, 30)),
                shift("2002", LocalTime.of(9, 7), LocalTime.of(21, 0))
        );

        TransportationDemandAnalysis analysis = service(provider)
                .getDemandForDate(
                        ORGANIZATION_ID,
                        DATE
                );

        assertEquals(
                LocalTime.of(8, 45),
                analysis.getMorning().getRecommendedStartTime()
        );

        assertEquals(
                LocalTime.of(9, 30),
                analysis.getMorning().getRecommendedEndTime()
        );

        assertEquals(
                LocalTime.of(17, 30),
                analysis.getEvening().getRecommendedStartTime()
        );

        assertEquals(
                LocalTime.of(21, 30),
                analysis.getEvening().getRecommendedEndTime()
        );
    }

    @Test
    void middayTurnoverCombinesStartsAndEndsInsideTheSameWindow() {

        ScheduleProvider provider = (organizationId, date) -> List.of(
                shift("3001", LocalTime.of(13, 2), LocalTime.of(19, 0)),
                shift("3002", LocalTime.of(9, 0), LocalTime.of(13, 7)),
                shift("3003", LocalTime.of(13, 10), LocalTime.of(20, 0))
        );

        TransportationDemandAnalysis analysis = service(provider)
                .getDemandForDate(
                        ORGANIZATION_ID,
                        DATE
                );

        assertEquals(1, analysis.getMidday().getWindows().size());
        assertEquals(2, analysis.getMidday().getWindows().get(0).getArrivals());
        assertEquals(1, analysis.getMidday().getWindows().get(0).getDepartures());
        assertEquals(3, analysis.getMidday().getWindows().get(0).getDemandCount());
    }

    @Test
    void noonArrivalsAreMorningButHalfPastNoonCanStartAfternoonOpeningDemand() {
        ScheduleProvider noon = (organizationId, date) -> List.of(
                shift("100", LocalTime.NOON, LocalTime.of(21, 0)),
                shift("200", LocalTime.NOON, LocalTime.of(21, 0)),
                shift("400", LocalTime.of(13, 0), LocalTime.of(20, 0)));
        var noonAnalysis = service(noon).getDemandForDate(ORGANIZATION_ID, DATE);
        assertEquals(2, noonAnalysis.getMorning().getTotalCount());
        assertEquals(1, noonAnalysis.getMidday().getTotalCount());

        ScheduleProvider afternoon = (organizationId, date) -> List.of(
                shift("100", LocalTime.of(12, 30), LocalTime.of(21, 0)),
                shift("200", LocalTime.of(12, 30), LocalTime.of(21, 0)),
                shift("400", LocalTime.of(13, 0), LocalTime.of(20, 0)));
        var afternoonAnalysis = service(afternoon).getDemandForDate(ORGANIZATION_ID, DATE);
        assertEquals(0, afternoonAnalysis.getMorning().getTotalCount());
        assertEquals(3, afternoonAnalysis.getMidday().getTotalCount());
        assertEquals(LocalTime.of(12, 30), afternoonAnalysis.getMidday().getRecommendedStartTime());
    }

    private TransportationDemandService service(
            ScheduleProvider provider) {

        MorningDrivingPlanningService morningService =
                new MorningDrivingPlanningService(
                        provider,
                        "09:00",
                        "12:00",
                        15,
                        15
                );

        return new TransportationDemandService(
                provider,
                morningService,
                "09:00",
                "12:00",
                "17:00",
                "21:00",
                15,
                15
        );
    }

    private ShiftRecord shift(
            String employeeId,
            LocalTime start,
            LocalTime end) {

        return new ShiftRecord(
                employeeId,
                "Employee " + employeeId,
                "Operations",
                "Team Member",
                DATE,
                start,
                end,
                "Employee Gate"
        );
    }
}
