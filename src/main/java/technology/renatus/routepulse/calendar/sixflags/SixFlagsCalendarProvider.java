package technology.renatus.routepulse.calendar.sixflags;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.repository.ParkOperatingDaySnapshotRepository;
import technology.renatus.routepulse.calendar.sixflags.SixFlagsCalendarLookupService.CalendarLookup;
import technology.renatus.routepulse.calendar.spi.OperatingCalendarProvider;

/** Provides the configured Six Flags calendar with caching and persisted fallback. */
@Service
public class SixFlagsCalendarProvider implements OperatingCalendarProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SixFlagsCalendarProvider.class);

    private final SixFlagsCalendarClient calendarClient;
    private final SixFlagsCalendarParser calendarParser;
    private final SixFlagsCalendarSnapshotStore snapshotStore;
    private final SixFlagsCalendarLookupService lookupService;

    public SixFlagsCalendarProvider(
            SixFlagsCalendarClient calendarClient,
            ParkOperatingDaySnapshotRepository snapshotRepository,
            SixFlagsCalendarParser calendarParser) {
        this.calendarClient = calendarClient;
        this.calendarParser = calendarParser;
        this.snapshotStore = new SixFlagsCalendarSnapshotStore(
                calendarClient,
                snapshotRepository,
                calendarParser);
        this.lookupService = new SixFlagsCalendarLookupService(calendarClient, snapshotStore);
    }

    @Override
    public ParkOperatingDay getOperatingDay(LocalDate requestedDate) {
        try {
            CalendarLookup lookup = lookupService.getCalendarForMonth(requestedDate);
            SixFlagsCalendarResponse response = lookup.response();

            if (response == null) {
                return snapshotStore.fallback(requestedDate, "EMPTY_RESPONSE");
            }
            if (response.dates() == null) {
                LOGGER.warn(
                        "Six Flags calendar returned no dates for park {} while resolving {}",
                        calendarClient.getParkId(),
                        requestedDate);
                return snapshotStore.fallback(requestedDate, "DATES_MISSING_FROM_RESPONSE");
            }

            SixFlagsCalendarDate calendarDate =
                    calendarParser.findCalendarDate(response, requestedDate);
            if (calendarDate == null) {
                LOGGER.warn(
                        "Six Flags calendar response did not contain {} for park {}. "
                                + "This can happen when the API drops the current operating date.",
                        requestedDate,
                        calendarClient.getParkId());
                return snapshotStore.fallback(requestedDate, "DATE_MISSING_FROM_API");
            }

            ParkOperatingDay parsedDay = calendarParser.parseOperatingDay(
                    requestedDate,
                    calendarDate,
                    lookup.source(),
                    lookup.diagnosticCode());
            if (parsedDay.status() == ParkOperatingStatus.UNKNOWN) {
                return snapshotStore.fallback(requestedDate, parsedDay.diagnosticCode());
            }
            return parsedDay;
        } catch (SixFlagsCalendarException exception) {
            LOGGER.warn(
                    "Six Flags calendar unavailable while resolving {} for park {} [{}]",
                    requestedDate,
                    calendarClient.getParkId(),
                    exception.getDiagnosticCode());
            return snapshotStore.fallback(requestedDate, exception.getDiagnosticCode());
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "Six Flags calendar request failed while resolving {} for park {}: {}",
                    requestedDate,
                    calendarClient.getParkId(),
                    exception.getMessage());
            return snapshotStore.fallback(requestedDate, "CALENDAR_UNAVAILABLE");
        }
    }

    public void confirmOpenForDate(LocalDate operatingDate, String confirmedBy) {
        snapshotStore.confirmOpenForDate(operatingDate, confirmedBy);
    }
}
