package technology.renatus.routepulse.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import technology.renatus.routepulse.calendar.SixFlagsCalendarClient;
import technology.renatus.routepulse.calendar.SixFlagsCalendarDate;
import technology.renatus.routepulse.calendar.SixFlagsCalendarException;
import technology.renatus.routepulse.calendar.SixFlagsCalendarResponse;
import technology.renatus.routepulse.calendar.SixFlagsOperating;
import technology.renatus.routepulse.calendar.SixFlagsOperatingItem;
import technology.renatus.routepulse.model.ParkOperatingDay;
import technology.renatus.routepulse.model.ParkOperatingDaySnapshot;
import technology.renatus.routepulse.model.ParkOperatingStatus;
import technology.renatus.routepulse.repository.ParkOperatingDaySnapshotRepository;

@Service
public class ParkOperatingDayService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    ParkOperatingDayService.class
            );

    /*
     * Six Flags uses operating type 24 for the main park.
     */
    private static final int MAIN_PARK_OPERATING_TYPE_ID = 24;

    private static final DateTimeFormatter CALENDAR_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/uuuu");

    private static final DateTimeFormatter OPERATING_TIME_FORMAT =
            DateTimeFormatter.ofPattern("H:mm");

    private static final Duration CACHE_TTL =
            Duration.ofMinutes(15);

    private static final Duration STALE_CACHE_LIMIT =
            Duration.ofHours(6);

    private final SixFlagsCalendarClient calendarClient;
    private final ParkOperatingDaySnapshotRepository snapshotRepository;

    private final Map<YearMonth, CachedCalendar>
            calendarCache = new ConcurrentHashMap<>();

    @Autowired
    public ParkOperatingDayService(
            SixFlagsCalendarClient calendarClient,
            ParkOperatingDaySnapshotRepository snapshotRepository) {

        this.calendarClient = calendarClient;
        this.snapshotRepository = snapshotRepository;
    }

    /*
     * Kept for focused unit tests that do not need a database.
     */
    public ParkOperatingDayService(
            SixFlagsCalendarClient calendarClient) {

        this.calendarClient = calendarClient;
        this.snapshotRepository = null;
    }

    public ParkOperatingDay getOperatingDay(
            LocalDate requestedDate) {

        try {
            CalendarLookup calendarLookup =
                    getCalendarForMonth(requestedDate);

            SixFlagsCalendarResponse response =
                    calendarLookup.response();

            if (response == null) {
                return fallbackToPersistedSnapshot(
                        requestedDate,
                        "EMPTY_RESPONSE"
                );
            }

            if (response.dates() == null) {

                LOGGER.warn(
                        "Six Flags calendar returned no dates for park {} "
                                + "while resolving {}",
                        calendarClient.getParkId(),
                        requestedDate
                );

                return fallbackToPersistedSnapshot(
                        requestedDate,
                        "DATES_MISSING_FROM_RESPONSE"
                );
            }

            SixFlagsCalendarDate calendarDate =
                    findCalendarDate(
                            response,
                            requestedDate
                    );

            if (calendarDate == null) {

                LOGGER.warn(
                        "Six Flags calendar response did not contain {} "
                                + "for park {}. This can happen when the API "
                                + "drops the current operating date.",
                        requestedDate,
                        calendarClient.getParkId()
                );

                return fallbackToPersistedSnapshot(
                        requestedDate,
                        "DATE_MISSING_FROM_API"
                );
            }

            ParkOperatingDay parsedDay =
                    parseOperatingDay(
                            requestedDate,
                            calendarDate,
                            calendarLookup.source(),
                            calendarLookup.diagnosticCode()
                    );

            if (parsedDay.status()
                    == ParkOperatingStatus.UNKNOWN) {

                return fallbackToPersistedSnapshot(
                        requestedDate,
                        parsedDay.diagnosticCode()
                );
            }

            return parsedDay;

        } catch (SixFlagsCalendarException exception) {

            LOGGER.warn(
                    "Six Flags calendar unavailable while resolving {} "
                            + "for park {} [{}]",
                    requestedDate,
                    calendarClient.getParkId(),
                    exception.getDiagnosticCode()
            );

            return fallbackToPersistedSnapshot(
                    requestedDate,
                    exception.getDiagnosticCode()
            );

        } catch (RestClientException exception) {

            LOGGER.warn(
                    "Six Flags calendar request failed while resolving {} "
                            + "for park {}: {}",
                    requestedDate,
                    calendarClient.getParkId(),
                    exception.getMessage()
            );

            return fallbackToPersistedSnapshot(
                    requestedDate,
                    "CALENDAR_UNAVAILABLE"
            );
        }
    }


    public void confirmOpenForDate(
            LocalDate operatingDate,
            String confirmedBy) {

        if (snapshotRepository == null) {
            throw new IllegalStateException(
                    "Persistent park calendar storage is unavailable."
            );
        }

        LocalDateTime confirmedAt =
                LocalDateTime.now(ZoneOffset.UTC);

        ParkOperatingDaySnapshot snapshot =
                snapshotRepository
                        .findByParkIdAndOperatingDate(
                                calendarClient.getParkId(),
                                operatingDate
                        )
                        .orElseGet(() ->
                                new ParkOperatingDaySnapshot(
                                        calendarClient.getParkId(),
                                        operatingDate,
                                        ParkOperatingStatus.OPEN,
                                        null,
                                        null,
                                        false,
                                        "MANAGER_CONFIRMATION",
                                        confirmedAt
                                )
                        );

        snapshot.confirmOpen(
                confirmedBy,
                confirmedAt
        );

        snapshotRepository.save(snapshot);

        LOGGER.warn(
                "Manager {} confirmed park {} open for {} after "
                        + "calendar verification could not be completed",
                confirmedBy,
                calendarClient.getParkId(),
                operatingDate
        );
    }

    private CalendarLookup getCalendarForMonth(
            LocalDate requestedDate) {

        YearMonth month = YearMonth.from(requestedDate);
        Instant now = Instant.now();
        CachedCalendar cached = calendarCache.get(month);

        if (cached != null
                && cached.isYoungerThan(
                        now,
                        CACHE_TTL
                )) {

            return new CalendarLookup(
                    cached.response(),
                    "MEMORY_CACHE",
                    null
            );
        }

        try {
            SixFlagsCalendarResponse liveResponse =
                    calendarClient.getCalendarForMonth(
                            requestedDate
                    );

            if (liveResponse != null) {

                calendarCache.put(
                        month,
                        new CachedCalendar(
                                liveResponse,
                                now
                        )
                );

                persistKnownOperatingDays(
                        liveResponse
                );

                return new CalendarLookup(
                        liveResponse,
                        "LIVE_API",
                        null
                );
            }

        } catch (RestClientException exception) {

            String diagnosticCode =
                    diagnosticCodeFor(exception);

            if (cached != null
                    && cached.isYoungerThan(
                            now,
                            STALE_CACHE_LIMIT
                    )) {

                LOGGER.warn(
                        "Using stale in-memory Six Flags calendar for {} "
                                + "after live request failure [{}]",
                        month,
                        diagnosticCode
                );

                return new CalendarLookup(
                        cached.response(),
                        "MEMORY_CACHE_STALE",
                        diagnosticCode
                );
            }

            throw exception;
        }

        if (cached != null
                && cached.isYoungerThan(
                        now,
                        STALE_CACHE_LIMIT
                )) {

            return new CalendarLookup(
                    cached.response(),
                    "MEMORY_CACHE_STALE",
                    "EMPTY_LIVE_RESPONSE"
            );
        }

        return new CalendarLookup(
                null,
                "NONE",
                "EMPTY_RESPONSE"
        );
    }

    private SixFlagsCalendarDate findCalendarDate(
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

    private ParkOperatingDay parseOperatingDay(
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

    private void persistKnownOperatingDays(
            SixFlagsCalendarResponse response) {

        if (snapshotRepository == null
                || response == null
                || response.dates() == null) {
            return;
        }

        LocalDateTime fetchedAt =
                LocalDateTime.now(ZoneOffset.UTC);

        for (SixFlagsCalendarDate calendarDate
                : response.dates()) {

            if (calendarDate == null
                    || calendarDate.date() == null
                    || calendarDate.date().isBlank()) {
                continue;
            }

            try {
                LocalDate operatingDate =
                        parseDate(calendarDate.date());

                ParkOperatingDay parsed =
                        parseOperatingDay(
                                operatingDate,
                                calendarDate,
                                "LIVE_API",
                                null
                        );

                if (parsed.status()
                        == ParkOperatingStatus.UNKNOWN) {
                    continue;
                }

                Optional<ParkOperatingDaySnapshot> existing =
                        snapshotRepository
                                .findByParkIdAndOperatingDate(
                                        calendarClient.getParkId(),
                                        operatingDate
                                );

                ParkOperatingDaySnapshot snapshot =
                        existing.orElseGet(() ->
                                new ParkOperatingDaySnapshot(
                                        calendarClient.getParkId(),
                                        operatingDate,
                                        parsed.status(),
                                        parsed.openingTime(),
                                        parsed.closingTime(),
                                        parsed.buyout(),
                                        "SIX_FLAGS_API",
                                        fetchedAt
                                )
                        );

                if (existing.isPresent()) {
                    snapshot.refresh(
                            parsed.status(),
                            parsed.openingTime(),
                            parsed.closingTime(),
                            parsed.buyout(),
                            "SIX_FLAGS_API",
                            fetchedAt
                    );
                }

                snapshotRepository.save(snapshot);

            } catch (DateTimeParseException exception) {

                LOGGER.warn(
                        "Skipping persistent calendar snapshot for malformed "
                                + "Six Flags date '{}'",
                        calendarDate.date()
                );
            }
        }
    }

    private ParkOperatingDay fallbackToPersistedSnapshot(
            LocalDate requestedDate,
            String diagnosticCode) {

        if (snapshotRepository != null) {

            Optional<ParkOperatingDaySnapshot> snapshot =
                    snapshotRepository
                            .findByParkIdAndOperatingDate(
                                    calendarClient.getParkId(),
                                    requestedDate
                            );

            if (snapshot.isPresent()) {

                ParkOperatingDaySnapshot saved =
                        snapshot.get();

                LOGGER.warn(
                        "Using persisted Six Flags operating-day snapshot "
                                + "for park {} date {} because live verification "
                                + "was unavailable [{}]",
                        calendarClient.getParkId(),
                        requestedDate,
                        diagnosticCode
                );

                String verificationSource =
                        "MANAGER_CONFIRMATION".equals(
                                saved.getSource()
                        )
                                ? "MANAGER_CONFIRMATION"
                                : "PERSISTED_SNAPSHOT";

                return new ParkOperatingDay(
                        requestedDate,
                        saved.getOperatingStatus(),
                        saved.getOpeningTime(),
                        saved.getClosingTime(),
                        saved.isBuyout(),
                        verificationSource,
                        diagnosticCode
                );
            }
        }

        return createUnknownDay(
                requestedDate,
                diagnosticCode
        );
    }

    private String diagnosticCodeFor(
            RestClientException exception) {

        if (exception instanceof SixFlagsCalendarException
                sixFlagsException) {

            return sixFlagsException
                    .getDiagnosticCode();
        }

        return "CALENDAR_UNAVAILABLE";
    }

    private LocalDate parseDate(
            String calendarDate) {

        return LocalDate.parse(
                calendarDate,
                CALENDAR_DATE_FORMAT
        );
    }

    private ParkOperatingDay createUnknownDay(
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

    private record CachedCalendar(
            SixFlagsCalendarResponse response,
            Instant fetchedAt) {

        boolean isYoungerThan(
                Instant now,
                Duration maximumAge) {

            return fetchedAt
                    .plus(maximumAge)
                    .isAfter(now);
        }
    }

    private record CalendarLookup(
            SixFlagsCalendarResponse response,
            String source,
            String diagnosticCode) {
    }
}
