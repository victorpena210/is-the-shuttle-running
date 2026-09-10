package technology.renatus.routepulse.driving.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.model.PassengerRideConfirmation;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.operations.service.OperationalStatusService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Service
public class PassengerRideService {

    private final OrganizationRepository organizationRepository;
    private final DrivingSessionRepository drivingSessionRepository;
    private final PassengerRideConfirmationRepository passengerRideConfirmationRepository;
    private final OperationalStatusService operationalStatusService;
    private final ZoneId operationsZone;
    private final int cooldownMinutes;

    public PassengerRideService(
            OrganizationRepository organizationRepository,
            DrivingSessionRepository drivingSessionRepository,
            PassengerRideConfirmationRepository passengerRideConfirmationRepository,
            OperationalStatusService operationalStatusService,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone,
            @Value("${app.passenger-ride.cooldown-minutes:30}")
            int cooldownMinutes) {

        if (cooldownMinutes < 1 || cooldownMinutes > 240) {
            throw new IllegalArgumentException(
                    "Passenger ride cooldown must be between 1 and 240 minutes."
            );
        }

        this.organizationRepository = organizationRepository;
        this.drivingSessionRepository = drivingSessionRepository;
        this.passengerRideConfirmationRepository = passengerRideConfirmationRepository;
        this.operationalStatusService = operationalStatusService;
        this.operationsZone = ZoneId.of(operationsZone);
        this.cooldownMinutes = cooldownMinutes;
    }

    @Transactional(readOnly = true)
    public PassengerRideState getState(
            String organizationSlug,
            String deviceToken) {

        Organization organization = requireAccessibleOrganization(
                organizationSlug
        );

        DrivingSession activeSession = findActiveSession(
                organization.getId()
        );

        if (activeSession == null
                || operationalStatusService.blocksPassengerConfirmation(organization.getId())) {
            return new PassengerRideState(
                    false,
                    null,
                    false,
                    cooldownMinutes
            );
        }

        LocalDateTime now = LocalDateTime.now(operationsZone);
        String deviceTokenHash = hashDeviceToken(deviceToken);

        boolean alreadyConfirmedRecently = passengerRideConfirmationRepository
                .existsByAssignment_IdAndDeviceTokenHashAndSubmittedAtAfter(
                        activeSession.getAssignment().getId(),
                        deviceTokenHash,
                        now.minusMinutes(cooldownMinutes)
                );

        return new PassengerRideState(
                true,
                activeSession.getDriver().getName(),
                alreadyConfirmedRecently,
                cooldownMinutes
        );
    }

    @Transactional
    public ConfirmationResult confirmRide(
            String organizationSlug,
            String deviceToken) {

        Organization organization = requireAccessibleOrganization(
                organizationSlug
        );

        DrivingSession activeSession = findActiveSession(
                organization.getId()
        );

        if (activeSession == null
                || operationalStatusService.blocksPassengerConfirmation(organization.getId())) {
            return ConfirmationResult.NO_ACTIVE_SHUTTLE;
        }

        LocalDateTime now = LocalDateTime.now(operationsZone);
        String deviceTokenHash = hashDeviceToken(deviceToken);

        boolean alreadyConfirmedRecently = passengerRideConfirmationRepository
                .existsByAssignment_IdAndDeviceTokenHashAndSubmittedAtAfter(
                        activeSession.getAssignment().getId(),
                        deviceTokenHash,
                        now.minusMinutes(cooldownMinutes)
                );

        if (alreadyConfirmedRecently) {
            return ConfirmationResult.ALREADY_CONFIRMED;
        }

        PassengerRideConfirmation confirmation =
                new PassengerRideConfirmation(
                        activeSession.getAssignment(),
                        activeSession.getDriver(),
                        deviceTokenHash,
                        calculateWindowStart(now),
                        now
                );

        try {
            passengerRideConfirmationRepository.saveAndFlush(confirmation);
        } catch (DataIntegrityViolationException exception) {
            return ConfirmationResult.ALREADY_CONFIRMED;
        }

        return ConfirmationResult.RECORDED;
    }

    @Transactional(readOnly = true)
    public void requirePublicRidePage(String organizationSlug) {
        requireAccessibleOrganization(organizationSlug);
    }

    private Organization requireAccessibleOrganization(
            String organizationSlug) {

        String slug = normalizeSlug(organizationSlug);

        Organization organization = organizationRepository
                .findBySlug(slug)
                .orElseThrow(() -> new PassengerRideNotFoundException(
                        "Passenger ride page was not found."
                ));

        if (!organization.hasApplicationAccess(
                LocalDateTime.now(operationsZone))) {
            throw new PassengerRideNotFoundException(
                    "Passenger ride page was not found."
            );
        }

        return organization;
    }

    private DrivingSession findActiveSession(Long organizationId) {
        return drivingSessionRepository
                .findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc(
                        organizationId
                )
                .orElse(null);
    }

    private LocalDateTime calculateWindowStart(LocalDateTime submittedAt) {
        long totalMinutes = submittedAt.getHour() * 60L
                + submittedAt.getMinute();

        long windowMinute = (totalMinutes / cooldownMinutes)
                * cooldownMinutes;

        int hour = (int) (windowMinute / 60L);
        int minute = (int) (windowMinute % 60L);

        return submittedAt
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);
    }

    private String normalizeSlug(String organizationSlug) {
        if (organizationSlug == null || organizationSlug.isBlank()) {
            throw new PassengerRideNotFoundException(
                    "Passenger ride page was not found."
            );
        }

        return organizationSlug.trim().toLowerCase(Locale.ROOT);
    }

    private String hashDeviceToken(String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) {
            throw new IllegalArgumentException(
                    "Passenger device token is required."
            );
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    deviceToken.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available.",
                    exception
            );
        }
    }

    public record PassengerRideState(
            boolean shuttleRunning,
            String driverName,
            boolean alreadyConfirmedRecently,
            int cooldownMinutes) {
    }

    public enum ConfirmationResult {
        RECORDED,
        ALREADY_CONFIRMED,
        NO_ACTIVE_SHUTTLE
    }

    public static class PassengerRideNotFoundException
            extends RuntimeException {

        public PassengerRideNotFoundException(String message) {
            super(message);
        }
    }
}
