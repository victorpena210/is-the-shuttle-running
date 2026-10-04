package technology.renatus.routepulse.reporting.workload.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.assignments.service.DriverWorkloadService;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.reporting.workload.model.WorkloadSummary;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.model.UnscheduledDrivingRecord;

/** Loads organization-scoped records and assembles workload reports in a read transaction. */
@Service
@Transactional(readOnly = true)
public class DrivingRecordService {

    private final DrivingSessionRepository drivingSessionRepository;
    private final ZoneId operationsZone;
    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final DriverWorkloadService driverWorkloadService;
    private final WorkloadCalculator workloadCalculator;

    public DrivingRecordService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            DriverWorkloadService driverWorkloadService,
            WorkloadCalculator workloadCalculator,
            DrivingSessionRepository drivingSessionRepository,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.drivingSessionRepository = drivingSessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.driverWorkloadService = driverWorkloadService;
        this.workloadCalculator = workloadCalculator;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public List<DriverAssignment> getDrivingRecords(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate,
            Long driverId) {

        validateDateRange(
                organizationId,
                fromDate,
                toDate
        );

        validateDriverIfPresent(
                organizationId,
                driverId
        );

        return assignmentRepository
                .findAllByOrganization_IdAndAssignmentDateBetween(
                        organizationId,
                        fromDate,
                        toDate
                )
                .stream()
                .filter(assignment ->
                        driverId == null
                                || matchesDriver(
                                        assignment,
                                        driverId
                                )
                )
                .sorted(
                        Comparator
                                .comparing(
                                        DriverAssignment::getAssignmentDate,
                                        Comparator.reverseOrder()
                                )
                                .thenComparingInt(
                                        assignment ->
                                                assignment
                                                        .getPeriod()
                                                        .ordinal()
                                )
                )
                .toList();
    }

    public List<UnscheduledDrivingRecord> getUnscheduledRecords(Long organizationId,
            LocalDate fromDate, LocalDate toDate, Long driverId) {
        validateDateRange(organizationId, fromDate, toDate);
        validateDriverIfPresent(organizationId, driverId);
        return drivingSessionRepository.findUnscheduledRecords(organizationId, driverId,
                        fromDate.atStartOfDay(operationsZone).toInstant(),
                        toDate.plusDays(1).atStartOfDay(operationsZone).toInstant())
                .stream().map(session -> UnscheduledDrivingRecord.from(session, operationsZone)).toList();
    }

    public Map<Long, Boolean> getMakeupStatusMap(
            Long organizationId,
            List<DriverAssignment> records) {

        Map<Long, Boolean> result =
                new LinkedHashMap<>();

        for (DriverAssignment assignment : records) {

            result.put(
                    assignment.getId(),
                    driverWorkloadService
                            .isMakeupOutstandingForAssignment(
                                    organizationId,
                                    assignment
                                            .getScheduledDriver()
                                            .getId(),
                                    assignment.getId()
                            )
            );
        }

        return result;
    }

    public LocalDate getCurrentOperationsDate() {

        return LocalDate.now(
                operationsZone
        );
    }

    private boolean matchesDriver(
            DriverAssignment assignment,
            Long driverId) {

        /*
         * Scheduled driver.
         */
        if (Objects.equals(
                assignment
                        .getScheduledDriver()
                        .getId(),
                driverId
        )) {

            return true;
        }

        /*
         * Actual driver.
         */
        return assignment
                .getDrivingSessions()
                .stream()
                .anyMatch(
                        session ->
                                Objects.equals(
                                        session
                                                .getDriver()
                                                .getId(),
                                        driverId
                                )
                );
    }

    private void validateDriverIfPresent(
            Long organizationId,
            Long driverId) {

        if (driverId == null) {
            return;
        }

        driverRepository
                .findByIdAndOrganization_Id(
                        driverId,
                        organizationId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Driver was not found."
                        )
                );
    }

    private void validateDateRange(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "Organization is required."
            );
        }

        if (fromDate == null
                || toDate == null) {

            throw new IllegalArgumentException(
                    "A start date and end date are required."
            );
        }

        if (fromDate.isAfter(toDate)) {

            throw new IllegalArgumentException(
                    "The start date cannot be after the end date."
            );
        }
    }

    public WorkloadSummary getWorkloadSummary(Long organizationId, LocalDate fromDate,
            LocalDate toDate, Long driverId) {
        validateDateRange(organizationId, fromDate, toDate);
        validateDriverIfPresent(organizationId, driverId);
        return workloadCalculator.calculate(fromDate, toDate, driverId,
                driverRepository.findAllByOrganization_IdOrderByNameAsc(organizationId),
                assignmentRepository.findAllByOrganization_IdAndAssignmentDateBetween(
                        organizationId, fromDate, toDate),
                drivingSessionRepository.findUnscheduledRecords(organizationId, driverId,
                        fromDate.atStartOfDay(operationsZone).toInstant(),
                        toDate.plusDays(1).atStartOfDay(operationsZone).toInstant()));
    }
}
