package technology.renatus.routepulse.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.retention.model.HistoricalDemandSnapshot;
import technology.renatus.routepulse.scheduling.retention.repository.HistoricalDemandSnapshotRepository;
import technology.renatus.routepulse.scheduling.retention.repository.HistoricalDemandWindowRepository;
import technology.renatus.routepulse.scheduling.retention.service.HistoricalScheduleRetentionService;
import technology.renatus.routepulse.scheduling.retention.service.ScheduleRetentionResult;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class HistoricalScheduleRetentionIntegrationTests
        extends MySqlIntegrationTestSupport {

    @Autowired
    private HistoricalScheduleRetentionService retentionService;

    @Autowired
    private HistoricalDemandSnapshotRepository snapshotRepository;

    @Autowired
    private HistoricalDemandWindowRepository windowRepository;

    @Autowired
    private DrivingSessionRepository drivingSessionRepository;

    @Test
    void completedSchedulesBecomeAnonymousForecastHistoryWhileDrivingHistorySurvives() {
        Organization organization = createOrganization(
                "Retention Organization",
                "retention-organization"
        );
        UserAccount uploader = createUser(
                "retention-uploader@example.com",
                true
        );

        LocalDate currentOperatingDate = LocalDate.of(2026, 9, 10);
        LocalDate completedDate = currentOperatingDate.minusDays(1);
        LocalDate futureDate = currentOperatingDate.plusDays(1);

        csvScheduleProvider.loadSchedule(
                organization.getId(),
                uploader.getEmail(),
                csvFile(
                        "first-version.csv",
                        "100,Old Version,9/9/2026 8:30 AM,9/9/2026 4:30 PM,Park/Ops/Transportation/Shuttle/North Gate,Driver"
                )
        );

        ScheduleImport latestImport = csvScheduleProvider.loadSchedule(
                organization.getId(),
                uploader.getEmail(),
                csvFile(
                        "latest-version.csv",
                        "100,Registered Driver,9/9/2026 9:00 AM,9/9/2026 5:00 PM,Park/Ops/Transportation/Shuttle/North Gate,Driver",
                        "200,Ordinary Employee,9/9/2026 9:15 AM,9/9/2026 5:30 PM,Park/Ops/Rides/Ride Ops,Operator",
                        "300,Current Employee,9/10/2026 10:00 AM,9/10/2026 6:00 PM,Park/Ops/Rides/Ride Ops,Operator",
                        "400,Future Employee,9/11/2026 11:00 AM,9/11/2026 7:00 PM,Park/Ops/Rides/Ride Ops,Operator"
                )
        );

        Driver driver = driverRepository.save(
                new Driver(
                        organization,
                        "100",
                        "Registered Driver",
                        true
                )
        );

        DriverAssignment assignment = DriverAssignment.manual(
                completedDate,
                ShuttlePeriod.MORNING,
                driver,
                "Historical shuttle coverage"
        );
        assignment.updateScheduledTimes(
                LocalTime.of(8, 45),
                LocalTime.of(10, 30)
        );
        assignmentRepository.save(assignment);

        DrivingSession session = DrivingSession.start(
                assignment,
                driver,
                completedDate.atTime(LocalTime.of(9, 0))
                        .atZone(ZoneId.of("America/Chicago"))
                        .toInstant(),
                uploader,
                DrivingActionMethod.MANAGER
        );
        session.end(
                completedDate.atTime(LocalTime.of(10, 20))
                        .atZone(ZoneId.of("America/Chicago"))
                        .toInstant(),
                uploader,
                DrivingActionMethod.MANAGER
        );
        drivingSessionRepository.save(session);

        entityManager.flush();
        entityManager.clear();

        ScheduleRetentionResult result = retentionService
                .archiveAndPurgeBefore(currentOperatingDate);

        assertEquals(1, result.archivedOperatingDays());
        assertEquals(3, result.purgedRawShiftRows());
        assertEquals(0, result.expiredHistoricalSnapshots());

        assertTrue(
                uploadedShiftRepository
                        .findLatestVersionForDate(
                                organization.getId(),
                                completedDate
                        )
                        .isEmpty()
        );
        assertEquals(
                1,
                uploadedShiftRepository
                        .findLatestVersionForDate(
                                organization.getId(),
                                currentOperatingDate
                        )
                        .size()
        );
        assertEquals(
                1,
                uploadedShiftRepository
                        .findLatestVersionForDate(
                                organization.getId(),
                                futureDate
                        )
                        .size()
        );

        HistoricalDemandSnapshot snapshot = snapshotRepository
                .findByOrganization_IdAndOperatingDate(
                        organization.getId(),
                        completedDate
                )
                .orElseThrow();

        assertEquals(2, snapshot.getShiftRowCount());
        assertEquals(2, snapshot.getEmployeeCount());
        assertEquals(2, snapshot.getMorningDemandCount());
        assertEquals(0, snapshot.getMiddayDemandCount());
        assertEquals(2, snapshot.getEveningDemandCount());
        assertEquals(0, snapshot.getOffHoursMovementCount());
        assertEquals(latestImport.getId(), snapshot.getSourceScheduleImport().getId());

        assertEquals(
                4,
                windowRepository
                        .findAllBySnapshot_Organization_IdAndSnapshot_OperatingDateOrderByWindowStartAsc(
                                organization.getId(),
                                completedDate
                        )
                        .size()
        );

        // Registered drivers and actual driving history are permanent records.
        assertTrue(driverRepository.findById(driver.getId()).isPresent());
        assertTrue(assignmentRepository.findById(assignment.getId()).isPresent());
        assertTrue(drivingSessionRepository.findById(session.getId()).isPresent());

        // Import audit metadata remains even though the employee-level rows are gone.
        assertEquals(2, scheduleImportRepository.count());
    }

    @Test
    void aLateReuploadRefreshesTheAnonymousSnapshotBeforePurgingAgain() {
        Organization organization = createOrganization(
                "Reupload Retention Organization",
                "reupload-retention-organization"
        );
        UserAccount uploader = createUser(
                "reupload-retention@example.com",
                true
        );

        LocalDate currentOperatingDate = LocalDate.of(2026, 9, 10);
        LocalDate completedDate = currentOperatingDate.minusDays(1);

        csvScheduleProvider.loadSchedule(
                organization.getId(),
                uploader.getEmail(),
                csvFile(
                        "original.csv",
                        "100,Original Employee,9/9/2026 9:00 AM,9/9/2026 5:00 PM,Park/Ops/Rides/Ride Ops,Operator"
                )
        );

        retentionService.archiveAndPurgeBefore(currentOperatingDate);

        HistoricalDemandSnapshot original = snapshotRepository
                .findByOrganization_IdAndOperatingDate(
                        organization.getId(),
                        completedDate
                )
                .orElseThrow();
        assertEquals(1, original.getEmployeeCount());

        ScheduleImport correctedImport = csvScheduleProvider.loadSchedule(
                organization.getId(),
                uploader.getEmail(),
                csvFile(
                        "corrected.csv",
                        "100,Corrected Employee,9/9/2026 9:00 AM,9/9/2026 5:00 PM,Park/Ops/Rides/Ride Ops,Operator",
                        "200,Added Employee,9/9/2026 9:15 AM,9/9/2026 5:15 PM,Park/Ops/Rides/Ride Ops,Operator"
                )
        );

        ScheduleRetentionResult secondRun = retentionService
                .archiveAndPurgeBefore(currentOperatingDate);

        assertEquals(1, secondRun.archivedOperatingDays());
        assertEquals(2, secondRun.purgedRawShiftRows());
        assertEquals(1, snapshotRepository.count());

        HistoricalDemandSnapshot corrected = snapshotRepository
                .findByOrganization_IdAndOperatingDate(
                        organization.getId(),
                        completedDate
                )
                .orElseThrow();

        assertEquals(2, corrected.getEmployeeCount());
        assertEquals(2, corrected.getShiftRowCount());
        assertEquals(2, corrected.getMorningDemandCount());
        assertEquals(2, corrected.getEveningDemandCount());
        assertEquals(
                correctedImport.getId(),
                corrected.getSourceScheduleImport().getId()
        );
        assertTrue(
                uploadedShiftRepository
                        .findLatestVersionForDate(
                                organization.getId(),
                                completedDate
                        )
                        .isEmpty()
        );
    }

    @Test
    void anonymousForecastHistoryUsesARollingTwelveMonthBoundary() {
        Organization organization = createOrganization(
                "Forecast Retention Organization",
                "forecast-retention-organization"
        );
        UserAccount uploader = createUser(
                "forecast-retention@example.com",
                true
        );

        LocalDate currentOperatingDate = LocalDate.of(2026, 9, 10);
        LocalDate expiredDate = LocalDate.of(2025, 9, 9);
        LocalDate boundaryDate = LocalDate.of(2025, 9, 10);

        csvScheduleProvider.loadSchedule(
                organization.getId(),
                uploader.getEmail(),
                csvFile(
                        "historical.csv",
                        "100,Expired Employee,9/9/2025 9:00 AM,9/9/2025 5:00 PM,Park/Ops/Rides/Ride Ops,Operator",
                        "200,Boundary Employee,9/10/2025 9:00 AM,9/10/2025 5:00 PM,Park/Ops/Rides/Ride Ops,Operator"
                )
        );

        ScheduleRetentionResult result = retentionService
                .archiveAndPurgeBefore(currentOperatingDate);

        assertEquals(2, result.archivedOperatingDays());
        assertEquals(2, result.purgedRawShiftRows());
        assertEquals(1, result.expiredHistoricalSnapshots());

        assertFalse(
                snapshotRepository
                        .findByOrganization_IdAndOperatingDate(
                                organization.getId(),
                                expiredDate
                        )
                        .isPresent()
        );
        assertTrue(
                snapshotRepository
                        .findByOrganization_IdAndOperatingDate(
                                organization.getId(),
                                boundaryDate
                        )
                        .isPresent()
        );
    }
}
