package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import technology.renatus.routepulse.calendar.SixFlagsCalendarClient;
import technology.renatus.routepulse.calendar.SixFlagsCalendarDate;
import technology.renatus.routepulse.calendar.SixFlagsCalendarResponse;
import technology.renatus.routepulse.calendar.SixFlagsOperating;
import technology.renatus.routepulse.calendar.SixFlagsOperatingItem;
import technology.renatus.routepulse.model.ParkOperatingDay;
import technology.renatus.routepulse.model.ParkOperatingStatus;

@Service
public class ParkOperatingDayService {

    /*
     * Six Flags uses operating type 24 for the main park.
     */
    private static final int MAIN_PARK_OPERATING_TYPE_ID = 24;

    /*
     * Formats used by the Six Flags calendar response.
     */
    private static final DateTimeFormatter CALENDAR_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/uuuu");

    private static final DateTimeFormatter OPERATING_TIME_FORMAT =
            DateTimeFormatter.ofPattern("H:mm");

    private final SixFlagsCalendarClient calendarClient;

    public ParkOperatingDayService(
            SixFlagsCalendarClient calendarClient) {

        this.calendarClient = calendarClient;
    }

    public ParkOperatingDay getOperatingDay(
            LocalDate requestedDate) {

        try {
            /*
             * Ask the client for the entire month containing
             * the requested date.
             */
            SixFlagsCalendarResponse response =
                    calendarClient.getCalendarForMonth(
                            requestedDate
                    );

            if (response == null || response.dates() == null) {
                return createUnknownDay(requestedDate);
            }

            /*
             * Find the individual calendar entry that matches
             * the date selected on the dashboard.
             */
            SixFlagsCalendarDate calendarDate =
                    response.dates()
                            .stream()
                            .filter(date ->
                                    date != null
                                            && date.date() != null
                            )
                            .filter(date ->
                                    parseDate(date.date())
                                            .equals(requestedDate)
                            )
                            .findFirst()
                            .orElse(null);

            if (calendarDate == null) {
                return createUnknownDay(requestedDate);
            }

            /*
             * If Six Flags explicitly says the park is closed,
             * no shuttle should be scheduled.
             */
            if (calendarDate.isParkClosed()) {
                return new ParkOperatingDay(
                        requestedDate,
                        ParkOperatingStatus.CLOSED,
                        null,
                        null,
                        false
                );
            }

            if (calendarDate.operatings() == null) {
                return createUnknownDay(requestedDate);
            }

            /*
             * A date can contain hours for multiple operating
             * areas. Find operating type 24, the main park.
             */
            SixFlagsOperating mainParkOperating =
                    calendarDate.operatings()
                            .stream()
                            .filter(operating ->
                                    operating != null
                            )
                            .filter(operating ->
                                    operating.operatingTypeId()
                                            == MAIN_PARK_OPERATING_TYPE_ID
                            )
                            .findFirst()
                            .orElse(null);

            if (mainParkOperating == null
                    || mainParkOperating.items() == null) {

                return createUnknownDay(requestedDate);
            }

            /*
             * Find a main-park item containing both opening
             * and closing times.
             */
            SixFlagsOperatingItem operatingHours =
                    mainParkOperating.items()
                            .stream()
                            .filter(item -> item != null)
                            .filter(item ->
                                    item.timeFrom() != null
                                            && !item.timeFrom().isBlank()
                            )
                            .filter(item ->
                                    item.timeTo() != null
                                            && !item.timeTo().isBlank()
                            )
                            .findFirst()
                            .orElse(null);

            if (operatingHours == null) {
                return createUnknownDay(requestedDate);
            }

            LocalTime openingTime =
                    LocalTime.parse(
                            operatingHours.timeFrom(),
                            OPERATING_TIME_FORMAT
                    );

            LocalTime closingTime =
                    LocalTime.parse(
                            operatingHours.timeTo(),
                            OPERATING_TIME_FORMAT
                    );

            /*
             * Valid main-park hours mean OPEN.
             *
             * Notice that isBuyout does not change the OPEN
             * status. It is only stored so the dashboard can
             * display "Private Buyout."
             */
            return new ParkOperatingDay(
                    requestedDate,
                    ParkOperatingStatus.OPEN,
                    openingTime,
                    closingTime,
                    operatingHours.isBuyout()
            );

        } catch (RestClientException
                | DateTimeParseException exception) {

            /*
             * Website failure, invalid dates, or invalid times
             * mean RoutePulse cannot safely make a decision.
             */
            return createUnknownDay(requestedDate);
        }
    }

    private LocalDate parseDate(String calendarDate) {
        return LocalDate.parse(
                calendarDate,
                CALENDAR_DATE_FORMAT
        );
    }

    private ParkOperatingDay createUnknownDay(
            LocalDate requestedDate) {

        return new ParkOperatingDay(
                requestedDate,
                ParkOperatingStatus.UNKNOWN,
                null,
                null,
                false
        );
    }
}
