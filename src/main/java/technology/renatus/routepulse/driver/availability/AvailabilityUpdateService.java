package technology.renatus.routepulse.driver.availability;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;

@Service
@Transactional(readOnly = true)
public class AvailabilityUpdateService {
    private final DriverAvailabilityRepository revisions;
    private final OrganizationRepository organizations;
    private final DriverRepository drivers;
    private final UploadedShiftRepository imports;
    private final DriverAssignmentRepository assignments;
    private final ZoneId zone;
    public AvailabilityUpdateService(DriverAvailabilityRepository revisions, OrganizationRepository organizations,
            DriverRepository drivers, UploadedShiftRepository imports, DriverAssignmentRepository assignments,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.revisions=revisions; this.organizations=organizations; this.drivers=drivers;
        this.imports=imports; this.assignments=assignments; this.zone=ZoneId.of(zone);
    }
    public LocalDate today() { return LocalDate.now(zone); }
    public String zoneName() { return zone.getId(); }
    public long version(Driver driver) { return revisions.version(driver.getOrganization().getId(), driver.getId()); }
    public List<DriverAvailabilityRevision> ownHistory(Driver driver) {
        return revisions.findTop100ByOrganizationIdAndDriver_IdOrderByIdDesc(driver.getOrganization().getId(),driver.getId());
    }
    public String imported(Driver driver, LocalDate date) {
        return AvailabilityWindows.encode(imports.findLatestVersionForDate(driver.getOrganization().getId(),date).stream()
                .filter(s -> s.getEmployeeId().equals(driver.getEmployeeId()))
                .map(s -> new AvailabilityWindows.Window(s.getStartTime(),s.getEndTime())).toList());
    }
    @Transactional
    public int confirm(Driver currentDriver, long expectedVersion, AvailabilityForm form, String source, String digest, String actor) {
        var days = form.validated(today()); Long org = currentDriver.getOrganization().getId();
        organizations.findByIdForUpdate(org).orElseThrow(); // Same lock as automatic planning.
        var driver = drivers.findByIdAndOrganization_Id(currentDriver.getId(),org).orElseThrow();
        if (!driver.isActive()) throw new IllegalArgumentException("Your driver profile is inactive. Ask your manager to reactivate it.");
        if (version(driver) != expectedVersion) throw new IllegalArgumentException("Your schedule changed in another window. Reload and review the latest hours before saving.");
        var previous = revisions.latest(org,days.keySet().iterator().next(),Collections.max(days.keySet()));
        for (var day : days.entrySet()) {
            String baseline = imported(driver,day.getKey());
            String before = previous.stream().filter(r -> r.getDriver().getId().equals(driver.getId()) && r.getShiftDate().equals(day.getKey()) && !r.isUseImported())
                    .map(DriverAvailabilityRevision::getWorkWindows).findFirst().orElse(baseline);
            String after = AvailabilityWindows.encode(day.getValue());
            revisions.save(new DriverAvailabilityRevision(driver,day.getKey(),after,baseline,before,false,source,digest,actor));
            if (!before.equals(after)) holdAssignments(org,day.getKey());
        }
        return days.size();
    }
    private void holdAssignments(Long org, LocalDate date) {
        // A changed counter shift can affect another driver's assignment too.
        assignments.findAllForUpdateByOrganizationAndDate(org,date).forEach(a -> a.holdForAvailabilityUpdate());
    }
    @Transactional
    public void useLatestImport(Long org, Long revisionId, String actor) {
        organizations.findByIdForUpdate(org).orElseThrow();
        var revision = revisions.findByIdAndOrganizationId(revisionId,org)
                .orElseThrow(() -> new IllegalArgumentException("Schedule update was not found."));
        if (revision.getShiftDate().isBefore(today())) throw new IllegalArgumentException("Past work hours cannot be changed here.");
        var latest = revisions.latest(org,revision.getShiftDate(),revision.getShiftDate()).stream()
                .filter(r -> r.getDriver().getId().equals(revision.getDriver().getId())).findFirst().orElseThrow();
        if (!latest.getId().equals(revisionId) || latest.isUseImported())
            throw new IllegalArgumentException("This update has changed. Refresh before reviewing it.");
        String imported = imported(revision.getDriver(),revision.getShiftDate());
        revisions.save(new DriverAvailabilityRevision(revision.getDriver(),revision.getShiftDate(),imported,imported,
                revision.getWorkWindows(),true,"MANAGER_IMPORT",null,actor));
        holdAssignments(org,revision.getShiftDate());
    }
}
