package technology.renatus.routepulse.calendar.sixflags;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;

/** Translates Six Flags payloads into RoutePulse operating-day values. */
@Component
public class SixFlagsCalendarParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SixFlagsCalendarParser.class);

    private static final int MAIN_PARK_OPERATING_TYPE_ID = 24;

    private static final DateTimeFormatter CALENDAR_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/uuuu");

    private static final DateTimeFormatter OPERATING_TIME_FORMAT =
            DateTimeFormatter.ofPattern("H:mm");

    private final SixFlagsCalendarClient calendarClient;

    public SixFlagsCalendarParser(
            SixFlagsCalendarClient calendarClient) {
        this.calendarClient = calendarClient;
    }

    SixFlagsCalendarDate findCalendarDate(
            SixFlagsCalendarResponse response,
            LocalDate requestedDate) {

        for (SixFlagsCalendarDate calendarDate
                : response.dates()) {

            if (calendarDate == null
                    || calendarDate.date() == null
                    || calendarDate.date().isBlank()) {
                continue;
            }

            try {
                if (parseDate(calendarDate.date())
                        .equals(requestedDate)) {
                    return calendarDate;
                }

            } catch (DateTimeParseException exception) {

                LOGGER.warn(
                        "Ignoring malformed Six Flags calendar date '{}' "
                                + "for park {}",
                        calendarDate.date(),
                        calendarClient.getParkId()
                );
            }
        }

        return null;
    }

    ParkOperatingDay parseOperatingDay(
            LocalDate requestedDate,
            SixFlagsCalendarDate calendarDate,
            String source,
            String inheritedDiagnosticCode) {

        if (calendarDate.isParkClosed()) {

            return new ParkOperatingDay(
                    requestedDate,
                    ParkOperatingStatus.CLOSED,
                    null,
                    null,
                    false,
                    source,
                    inheritedDiagnosticCode
            );
        }

        if (calendarDate.operatings() == null) {

            LOGGER.warn(
                    "Six Flags calendar entry for {} has no operating data",
                    requestedDate
            );

            return createUnknownDay(
                    requestedDate,
                    "OPERATING_DATA_MISSING"
            );
        }

        SixFlagsOperating mainParkOperating =
                calendarDate.operatings()
                        .stream()
                        .filter(operating -> operating != null)
                        .filter(operating ->
                                operating.operatingTypeId()
                                        == MAIN_PARK_OPERATING_TYPE_ID
                        )
                        .findFirst()
                        .orElse(null);

        if (mainParkOperating == null) {

            LOGGER.warn(
                    "Six Flags calendar entry for {} does not include "
                            + "main-park operating type {}",
                    requestedDate,
                    MAIN_PARK_OPERATING_TYPE_ID
            );

            return createUnknownDay(
                    requestedDate,
                    "MAIN_PARK_OPERATING_DATA_MISSING"
            );
        }

        if (mainParkOperating.items() == null) {

            LOGGER.warn(
                    "Six Flags main-park operating entry for {} has no items",
                    requestedDate
            );

            return createUnknownDay(
                    requestedDate,
                    "OPERATING_HOURS_MISSING"
            );
        }

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

            LOGGER.warn(
                    "Six Flags main-park operating entry for {} "
                            + "does not contain usable opening and closing times",
                    requestedDate
            );

            return createUnknownDay(
                    requestedDate,
                    "OPERATING_HOURS_MISSING"
            );
        }

        try {
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

            return new ParkOperatingDay(
                    requestedDate,
                    ParkOperatingStatus.OPEN,
                    openingTime,
                    closingTime,
                    operatingHours.isBuyout(),
                    source,
                    inheritedDiagnosticCode
            );

        } catch (DateTimeParseException exception) {

            LOGGER.warn(
                    "Six Flags calendar returned invalid operating hours "
                            + "for {}: {} - {}",
                    requestedDate,
                    operatingHours.timeFrom(),
                    operatingHours.timeTo()
            );

            return createUnknownDay(
                    requestedDate,
                    "INVALID_OPERATING_HOURS"
            );
        }
    }

    LocalDate parseDate(
            String calendarDate) {

        return LocalDate.parse(
                calendarDate,
                CALENDAR_DATE_FORMAT
        );
    }

    ParkOperatingDay createUnknownDay(
            LocalDate requestedDate,
            String diagnosticCode) {

        return new ParkOperatingDay(
                requestedDate,
                ParkOperatingStatus.UNKNOWN,
                null,
                null,
                false,
                "NONE",
                diagnosticCode
        );
    }
}
