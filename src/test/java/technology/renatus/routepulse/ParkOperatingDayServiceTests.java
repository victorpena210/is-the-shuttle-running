package technology.renatus.routepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import technology.renatus.routepulse.calendar.SixFlagsCalendarClient;
import technology.renatus.routepulse.calendar.SixFlagsCalendarDate;
import technology.renatus.routepulse.calendar.SixFlagsCalendarResponse;
import technology.renatus.routepulse.calendar.SixFlagsOperating;
import technology.renatus.routepulse.calendar.SixFlagsOperatingItem;
import technology.renatus.routepulse.model.ParkOperatingDay;
import technology.renatus.routepulse.model.ParkOperatingStatus;
import technology.renatus.routepulse.service.ParkOperatingDayService;

class ParkOperatingDayServiceTests {

    @Test
    void returnsOpenDayWithOperatingHours() {
        SixFlagsCalendarClient client =
                mock(SixFlagsCalendarClient.class);

        LocalDate date = LocalDate.of(2026, 9, 18);

        when(client.getCalendarForMonth(date))
                .thenReturn(
                        responseFor(
                                date,
                                false,
                                "10:30",
                                "22:00",
                                false
                        )
                );

        ParkOperatingDay day =
                new ParkOperatingDayService(client)
                        .getOperatingDay(date);

        assertEquals(ParkOperatingStatus.OPEN, day.status());
        assertEquals(LocalTime.of(10, 30), day.openingTime());
        assertEquals(LocalTime.of(22, 0), day.closingTime());
        assertFalse(day.buyout());
        assertTrue(day.shouldScheduleShuttle());
    }

    @Test
    void returnsClosedWhenCalendarExplicitlyClosesPark() {
        SixFlagsCalendarClient client =
                mock(SixFlagsCalendarClient.class);

        LocalDate date = LocalDate.of(2026, 9, 19);

        when(client.getCalendarForMonth(date))
                .thenReturn(
                        responseFor(
                                date,
                                true,
                                null,
                                null,
                                false
                        )
                );

        ParkOperatingDay day =
                new ParkOperatingDayService(client)
                        .getOperatingDay(date);

        assertEquals(ParkOperatingStatus.CLOSED, day.status());
        assertFalse(day.shouldScheduleShuttle());
    }

    @Test
    void returnsUnknownInsteadOfFailingWhenCalendarIsUnavailable() {
        SixFlagsCalendarClient client =
                mock(SixFlagsCalendarClient.class);

        LocalDate date = LocalDate.of(2026, 9, 20);

        when(client.getCalendarForMonth(date))
                .thenThrow(
                        new RestClientException(
                                "Calendar unavailable"
                        ) {
                        }
                );

        ParkOperatingDay day =
                new ParkOperatingDayService(client)
                        .getOperatingDay(date);

        assertEquals(ParkOperatingStatus.UNKNOWN, day.status());
        assertFalse(day.shouldScheduleShuttle());
    }

    @Test
    void reusesTheMonthlyCalendarCache() {
        SixFlagsCalendarClient client =
                mock(SixFlagsCalendarClient.class);

        LocalDate date = LocalDate.of(2026, 9, 21);

        when(client.getCalendarForMonth(date))
                .thenReturn(
                        responseFor(
                                date,
                                false,
                                "10:30",
                                "22:00",
                                false
                        )
                );

        ParkOperatingDayService service =
                new ParkOperatingDayService(client);

        assertEquals(
                ParkOperatingStatus.OPEN,
                service.getOperatingDay(date).status()
        );

        assertEquals(
                ParkOperatingStatus.OPEN,
                service.getOperatingDay(date).status()
        );

        verify(client, times(1))
                .getCalendarForMonth(date);
    }

    private SixFlagsCalendarResponse responseFor(
            LocalDate date,
            boolean closed,
            String openingTime,
            String closingTime,
            boolean buyout) {

        List<SixFlagsOperating> operating = closed
                ? List.of()
                : List.of(
                        new SixFlagsOperating(
                                24,
                                "Main Park",
                                List.of(
                                        new SixFlagsOperatingItem(
                                                openingTime,
                                                closingTime,
                                                buyout
                                        )
                                )
                        )
                );

        SixFlagsCalendarDate calendarDate =
                new SixFlagsCalendarDate(
                        date.format(
                                java.time.format.DateTimeFormatter
                                        .ofPattern("MM/dd/uuuu")
                        ),
                        closed,
                        operating
                );

        return new SixFlagsCalendarResponse(
                914,
                "SFFT",
                "Six Flags Fiesta Texas",
                List.of(calendarDate)
        );
    }
}
