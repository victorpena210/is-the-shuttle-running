package technology.renatus.routepulse.reporting.workload.service;

import java.time.LocalDate;
import java.util.List;
import technology.renatus.routepulse.driving.model.UnscheduledDrivingRecord;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.service.DriverService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.reporting.workload.model.DriverWorkloadRow;
import technology.renatus.routepulse.reporting.workload.model.DrivingRecordPage;
import technology.renatus.routepulse.reporting.workload.model.WorkloadSummary;

/** Builds the complete organization-scoped driving-records page model. */
@Service
@Transactional(readOnly = true)
public class DrivingRecordPageService {

    private final DrivingRecordService drivingRecordService;
    private final DriverService driverService;
    private final CurrentOrganizationService currentOrganizationService;

    public DrivingRecordPageService(
            DrivingRecordService drivingRecordService,
            DriverService driverService,
            CurrentOrganizationService currentOrganizationService) {
        this.drivingRecordService = drivingRecordService;
        this.driverService = driverService;
        this.currentOrganizationService = currentOrganizationService;
    }

    public DrivingRecordPage build(
            Authentication authentication,
            LocalDate fromDate,
            LocalDate toDate,
            Long driverId,
            String view) {

        Organization organization = currentOrganizationService.requireOrganization(authentication);
        Long organizationId = organization.getId();
        DateRange range = normalizeRange(fromDate, toDate);
        String selectedView = "records".equalsIgnoreCase(view) ? "records" : "summary";

        List<DriverAssignment> records = List.of();
        List<UnscheduledDrivingRecord> unscheduledRecords = List.of();
        WorkloadSummary workloadSummary = WorkloadSummary.empty(range.fromDate(), range.toDate());
        String filterError = null;

        try {
            records = drivingRecordService.getDrivingRecords(
                    organizationId,
                    range.fromDate(),
                    range.toDate(),
                    driverId);
            unscheduledRecords = drivingRecordService.getUnscheduledRecords(
                    organizationId, range.fromDate(), range.toDate(), driverId);
            workloadSummary = drivingRecordService.getWorkloadSummary(
                    organizationId,
                    range.fromDate(),
                    range.toDate(),
                    driverId);
        } catch (IllegalArgumentException exception) {
            filterError = exception.getMessage();
        }

        DriverWorkloadRow selectedDriverSummary = driverId != null
                && !workloadSummary.getDrivers().isEmpty()
                ? workloadSummary.getDrivers().get(0)
                : null;

        return new DrivingRecordPage(
                organization.getDisplayName(),
                hasOwnerRole(authentication),
                driverService.getAllDrivers(organizationId),
                records,
                unscheduledRecords,
                workloadSummary,
                selectedDriverSummary,
                drivingRecordService.getMakeupStatusMap(organizationId, records),
                AssignmentOutcome.values(),
                range.fromDate(),
                range.toDate(),
                driverId,
                selectedView,
                filterError);
    }

    private DateRange normalizeRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate currentOperationsDate = drivingRecordService.getCurrentOperationsDate();

        if (fromDate == null && toDate == null) {
            return new DateRange(currentOperationsDate.minusDays(30), currentOperationsDate);
        }
        if (fromDate == null) {
            return new DateRange(toDate, toDate);
        }
        if (toDate == null) {
            return new DateRange(fromDate, fromDate);
        }
        return new DateRange(fromDate, toDate);
    }

    private boolean hasOwnerRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_OWNER".equals(authority.getAuthority()));
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {
    }
}
