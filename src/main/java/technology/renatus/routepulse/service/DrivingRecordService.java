package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.ShuttlePeriod;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.DrivingRecordRepository;

@Service
@Transactional(readOnly = true)
public class DrivingRecordService {

    private final DrivingRecordRepository
            drivingRecordRepository;

    private final DriverRepository driverRepository;

    private final ZoneId operationsZone;

    public DrivingRecordService(
            DrivingRecordRepository drivingRecordRepository,
            DriverRepository driverRepository,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.drivingRecordRepository =
                drivingRecordRepository;

        this.driverRepository =
                driverRepository;

        this.operationsZone =
                ZoneId.of(operationsZone);
    }

    public List<DriverAssignment> getDrivingRecords(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate,
            Long driverId) {

        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Organization is required."
            );
        }

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "A start date and end date are required."
            );
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "The start date cannot be after the end date."
            );
        }

        List<DriverAssignment> records;

        if (driverId == null) {
            records = drivingRecordRepository
                    .findAllByOrganization_IdAndAssignmentDateBetween(
                            organizationId,
                            fromDate,
                            toDate
                    );
        } else {
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

            records = drivingRecordRepository
                    .findAllByOrganization_IdAndAssignmentDateBetweenAndDriver_Id(
                            organizationId,
                            fromDate,
                            toDate,
                            driverId
                    );
        }

        return records.stream()
                .sorted(
                        Comparator
                                .comparing(
                                        DriverAssignment::getAssignmentDate,
                                        Comparator.reverseOrder()
                                )
                                .thenComparingInt(assignment ->
                                        assignment.getPeriod()
                                                == ShuttlePeriod.MORNING
                                                ? 0
                                                : 1
                                )
                )
                .toList();
    }

    public LocalDate getCurrentOperationsDate() {
        return LocalDate.now(operationsZone);
    }
}