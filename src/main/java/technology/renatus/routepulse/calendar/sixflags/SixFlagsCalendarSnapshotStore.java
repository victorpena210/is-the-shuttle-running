package technology.renatus.routepulse.calendar.sixflags;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    void confirmOpenForDate(LocalDate operatingDate, String confirmedBy) {
        if (snapshotRepository == null) {
            throw new IllegalStateException("Persistent park calendar storage is unavailable.");
        }

        LocalDateTime confirmedAt = LocalDateTime.now(ZoneOffset.UTC);
        ParkOperatingDaySnapshot snapshot = snapshotRepository
                .findByParkIdAndOperatingDate(calendarClient.getParkId(), operatingDate)
                .orElseGet(() -> new ParkOperatingDaySnapshot(
                        calendarClient.getParkId(),
                        operatingDate,
                        ParkOperatingStatus.OPEN,
                        null,
                        null,
                        false,
                        "MANAGER_CONFIRMATION",
                        confirmedAt));

        snapshot.confirmOpen(confirmedBy, confirmedAt);
        snapshotRepository.save(snapshot);

        LOGGER.warn(
                "Manager {} confirmed park {} open for {} after calendar verification could not be completed",
                confirmedBy,
                calendarClient.getParkId(),
                operatingDate);
    }

    void persistKnownOperatingDays(SixFlagsCalendarResponse response) {
        if (snapshotRepository == null || response == null || response.dates() == null) {
            return;
        }

        LocalDateTime fetchedAt = LocalDateTime.now(ZoneOffset.UTC);
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
            LocalDateTime fetchedAt) {
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

        String verificationSource = "MANAGER_CONFIRMATION".equals(saved.getSource())
                ? "MANAGER_CONFIRMATION"
                : "PERSISTED_SNAPSHOT";

        return new ParkOperatingDay(
                requestedDate,
                saved.getOperatingStatus(),
                saved.getOpeningTime(),
                saved.getClosingTime(),
                saved.isBuyout(),
                verificationSource,
                diagnosticCode);
    }
}
