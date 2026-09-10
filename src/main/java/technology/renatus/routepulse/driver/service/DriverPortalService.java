package technology.renatus.routepulse.driver.service;

import java.time.LocalDate;
import java.time.LocalTime;
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

    /**
     * Pick the assignment the driver should care about right now. Active driving
     * always wins; otherwise choose the earliest assignment with driving time
     * remaining today. This keeps the driver home screen action-first without
     * changing any assignment or QR-session rules.
     */
    public DriverAssignment selectPrimaryTodayAssignment(
            List<DriverAssignment> assignments) {

        if (assignments == null || assignments.isEmpty()) {
            return null;
        }

        return assignments.stream()
                .filter(DriverAssignment::isDriving)
                .findFirst()
                .orElseGet(() -> {
                    LocalTime now = currentOperationsTime();

                    return assignments.stream()
                            .filter(assignment -> hasDrivingTimeRemaining(assignment, now))
                            .min(Comparator
                                    .comparing(assignment -> nextRelevantTime(assignment, now))
                                    .thenComparingInt(assignment -> assignment.getPeriod().ordinal()))
                            .orElse(null);
                });
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

    public LocalTime currentOperationsTime() {
        return LocalTime.now(operationsZone);
    }

    private boolean hasDrivingTimeRemaining(
            DriverAssignment assignment,
            LocalTime now) {

        if (assignment.hasPlannedDrivingWindows()) {
            return assignment.hasRemainingPlannedDrivingWindowAtOrAfter(now);
        }

        if (assignment.getScheduledEndTime() != null) {
            return !now.isAfter(assignment.getScheduledEndTime());
        }

        return !assignment.isDrivingCompleted();
    }

    private LocalTime nextRelevantTime(
            DriverAssignment assignment,
            LocalTime now) {

        if (assignment.hasPlannedDrivingWindows()) {
            LocalTime nextStart = assignment.getNextPlannedDrivingStart(now);
            return nextStart == null ? LocalTime.MAX : nextStart;
        }

        LocalTime start = assignment.getScheduledStartTime();
        if (start == null || !now.isBefore(start)) {
            return now;
        }

        return start;
    }
}
