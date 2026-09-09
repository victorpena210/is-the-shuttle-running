package technology.renatus.routepulse.calendar.sixflags;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

/** Owns live monthly calendar retrieval and short-lived in-memory caching. */
final class SixFlagsCalendarLookupService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SixFlagsCalendarLookupService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration STALE_CACHE_LIMIT = Duration.ofHours(6);

    private final SixFlagsCalendarClient calendarClient;
    private final SixFlagsCalendarSnapshotStore snapshotStore;
    private final Map<YearMonth, CachedCalendar> calendarCache = new ConcurrentHashMap<>();

    SixFlagsCalendarLookupService(
            SixFlagsCalendarClient calendarClient,
            SixFlagsCalendarSnapshotStore snapshotStore) {
        this.calendarClient = calendarClient;
        this.snapshotStore = snapshotStore;
    }

    CalendarLookup getCalendarForMonth(LocalDate requestedDate) {
        YearMonth month = YearMonth.from(requestedDate);
        Instant now = Instant.now();
        CachedCalendar cached = calendarCache.get(month);

        if (isFresh(cached, now)) {
            return new CalendarLookup(cached.response(), "MEMORY_CACHE", null);
        }

        try {
            SixFlagsCalendarResponse liveResponse =
                    calendarClient.getCalendarForMonth(requestedDate);
            if (liveResponse != null) {
                calendarCache.put(month, new CachedCalendar(liveResponse, now));
                snapshotStore.persistKnownOperatingDays(liveResponse);
                return new CalendarLookup(liveResponse, "LIVE_API", null);
            }
        } catch (RestClientException exception) {
            String diagnosticCode = diagnosticCodeFor(exception);
            if (isUsableStale(cached, now)) {
                LOGGER.warn(
                        "Using stale in-memory Six Flags calendar for {} after live request failure [{}]",
                        month,
                        diagnosticCode);
                return new CalendarLookup(
                        cached.response(),
                        "MEMORY_CACHE_STALE",
                        diagnosticCode);
            }
            throw exception;
        }

        if (isUsableStale(cached, now)) {
            return new CalendarLookup(
                    cached.response(),
                    "MEMORY_CACHE_STALE",
                    "EMPTY_LIVE_RESPONSE");
        }

        return new CalendarLookup(null, "NONE", "EMPTY_RESPONSE");
    }

    private boolean isFresh(CachedCalendar cached, Instant now) {
        return cached != null && cached.isYoungerThan(now, CACHE_TTL);
    }

    private boolean isUsableStale(CachedCalendar cached, Instant now) {
        return cached != null && cached.isYoungerThan(now, STALE_CACHE_LIMIT);
    }

    private String diagnosticCodeFor(RestClientException exception) {
        if (exception instanceof SixFlagsCalendarException sixFlagsException) {
            return sixFlagsException.getDiagnosticCode();
        }
        return "CALENDAR_UNAVAILABLE";
    }

    private record CachedCalendar(SixFlagsCalendarResponse response, Instant fetchedAt) {
        boolean isYoungerThan(Instant now, Duration maximumAge) {
            return fetchedAt.plus(maximumAge).isAfter(now);
        }
    }

    record CalendarLookup(
            SixFlagsCalendarResponse response,
            String source,
            String diagnosticCode) {
    }
}
