package technology.renatus.routepulse.calendar.sixflags;

import java.time.LocalDate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.model.ParkOperatingDaySnapshot;
import technology.renatus.routepulse.calendar.repository.ParkOperatingDaySnapshotRepository;

/** Persists known Six Flags operating days and provides durable fallback data. */
final class SixFlagsCalendarSnapshotStore {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SixFlagsCalendarSnapshotStore.class);

    private final SixFlagsCalendarClient calendarClient;
    private final ParkOperatingDaySnapshotRepository snapshotRepository;
    private final SixFlagsCalendarParser calendarParser;

    SixFlagsCalendarSnapshotStore(
            SixFlagsCalendarClient calendarClient,
            ParkOperatingDaySnapshotRepository snapshotRepository,
            SixFlagsCalendarParser calendarParser) {
        this.calendarClient = calendarClient;
        this.snapshotRepository = snapshotRepository;
        this.calendarParser = calendarParser;
    }

    void persistKnownOperatingDays(SixFlagsCalendarResponse response) {
        if (snapshotRepository == null || response == null || response.dates() == null) {
            return;
        }

        Instant fetchedAt = Instant.now();
        for (SixFlagsCalendarDate calendarDate : response.dates()) {
            persistCalendarDate(calendarDate, fetchedAt);
        }
    }

    ParkOperatingDay fallback(LocalDate requestedDate, String diagnosticCode) {
        if (snapshotRepository != null) {
            Optional<ParkOperatingDaySnapshot> snapshot = snapshotRepository
                    .findByParkIdAndOperatingDate(calendarClient.getParkId(), requestedDate);
            if (snapshot.isPresent()) {
                return fromSnapshot(snapshot.get(), requestedDate, diagnosticCode);
            }
        }
        return calendarParser.createUnknownDay(requestedDate, diagnosticCode);
    }

    private void persistCalendarDate(
            SixFlagsCalendarDate calendarDate,
            Instant fetchedAt) {
        if (calendarDate == null
                || calendarDate.date() == null
                || calendarDate.date().isBlank()) {
            return;
        }

        try {
            LocalDate operatingDate = calendarParser.parseDate(calendarDate.date());
            ParkOperatingDay parsed = calendarParser.parseOperatingDay(
                    operatingDate,
                    calendarDate,
                    "LIVE_API",
                    null);

            if (parsed.status() == ParkOperatingStatus.UNKNOWN) {
                return;
            }

            Optional<ParkOperatingDaySnapshot> existing = snapshotRepository
                    .findByParkIdAndOperatingDate(calendarClient.getParkId(), operatingDate);
            ParkOperatingDaySnapshot snapshot = existing.orElseGet(() ->
                    new ParkOperatingDaySnapshot(
                            calendarClient.getParkId(),
                            operatingDate,
                            parsed.status(),
                            parsed.openingTime(),
                            parsed.closingTime(),
                            parsed.buyout(),
                            "SIX_FLAGS_API",
                            fetchedAt));

            if (existing.isPresent()) {
                snapshot.refresh(
                        parsed.status(),
                        parsed.openingTime(),
                        parsed.closingTime(),
                        parsed.buyout(),
                        "SIX_FLAGS_API",
                        fetchedAt);
            }
            snapshotRepository.save(snapshot);
        } catch (DateTimeParseException exception) {
            LOGGER.warn(
                    "Skipping persistent calendar snapshot for malformed Six Flags date '{}'",
                    calendarDate.date());
        }
    }

    private ParkOperatingDay fromSnapshot(
            ParkOperatingDaySnapshot saved,
            LocalDate requestedDate,
            String diagnosticCode) {
        LOGGER.warn(
                "Using persisted Six Flags operating-day snapshot for park {} date {} "
                        + "because live verification was unavailable [{}]",
                calendarClient.getParkId(),
                requestedDate,
                diagnosticCode);

        if ("MANAGER_CONFIRMATION".equals(saved.getSource())) {
            LOGGER.warn(
                    "Ignoring legacy global manager calendar confirmation for park {} date {}. "
                            + "A new organization-scoped override with a reason is required.",
                    calendarClient.getParkId(),
                    requestedDate);
            return calendarParser.createUnknownDay(
                    requestedDate,
                    "LEGACY_MANAGER_CONFIRMATION_IGNORED");
        }

        return new ParkOperatingDay(
                requestedDate,
                saved.getOperatingStatus(),
                saved.getOpeningTime(),
                saved.getClosingTime(),
                saved.isBuyout(),
                "PERSISTED_SNAPSHOT",
                diagnosticCode);
    }
}
