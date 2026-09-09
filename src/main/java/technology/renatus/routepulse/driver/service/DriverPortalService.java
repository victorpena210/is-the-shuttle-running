package technology.renatus.routepulse.driver.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.driver.service.portal.DriverQrScanService;
import technology.renatus.routepulse.driver.service.portal.DriverQrScanService.ScanPreview;
import technology.renatus.routepulse.driver.service.portal.DriverQrScanService.ScanResult;

/** Facade for the driver portal's profile, assignment history, and QR actions. */
@Service
@Transactional(readOnly = true)
public class DriverPortalService {

    private final DriverPortalAccessService accessService;
    private final DriverQrScanService qrScanService;
    private final DriverAssignmentRepository assignmentRepository;
    private final ZoneId operationsZone;

    public DriverPortalService(
            DriverPortalAccessService accessService,
            DriverQrScanService qrScanService,
            DriverAssignmentRepository assignmentRepository,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.accessService = accessService;
        this.qrScanService = qrScanService;
        this.assignmentRepository = assignmentRepository;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public Driver requireCurrentDriver(Authentication authentication) {
        return accessService.requireCurrentDriver(authentication);
    }

    public List<DriverAssignment> getTodayAssignments(Authentication authentication) {
        Driver driver = requireCurrentDriver(authentication);
        return assignmentRepository
                .findAllVisibleToDriverOnDate(
                        driver.getOrganization().getId(),
                        driver.getId(),
                        currentOperationsDate())
                .stream()
                .sorted(Comparator.comparingInt(assignment -> assignment.getPeriod().ordinal()))
                .toList();
    }

    public List<DriverAssignment> getRecentAssignments(Authentication authentication) {
        Driver driver = requireCurrentDriver(authentication);
        return assignmentRepository
                .findAllVisibleToDriver(driver.getOrganization().getId(), driver.getId())
                .stream()
                .sorted(Comparator
                        .comparing(DriverAssignment::getAssignmentDate, Comparator.reverseOrder())
                        .thenComparingInt(assignment -> assignment.getPeriod().ordinal()))
                .limit(20)
                .toList();
    }

    public ScanPreview getScanPreview(Authentication authentication, String qrCode) {
        return qrScanService.getScanPreview(authentication, qrCode);
    }

    @Transactional
    public ScanResult recordQrScan(Authentication authentication, String qrCode) {
        return qrScanService.recordQrScan(authentication, qrCode);
    }

    public LocalDate currentOperationsDate() {
        return LocalDate.now(operationsZone);
    }
}
