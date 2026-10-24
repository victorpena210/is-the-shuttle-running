package technology.renatus.routepulse.scheduling.demand;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.repository.*;
import technology.renatus.routepulse.organization.service.OrganizationActorService;

@Service
public class DriverScheduleIdentityService {
    private final DriverRepository drivers;
    private final DriverScheduleWindowRepository schedules;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationActorService actors;
    public DriverScheduleIdentityService(DriverRepository drivers, DriverScheduleWindowRepository schedules,
            OrganizationRepository organizations, OrganizationMembershipRepository memberships, OrganizationActorService actors) {
        this.drivers = drivers; this.schedules = schedules; this.organizations = organizations;
        this.memberships = memberships; this.actors = actors;
    }
    @Transactional(readOnly = true)
    public void requireManager(Long org, String email) {
        var actor = actors.loadActor(org, email);
        var member = memberships.findByOrganization_IdAndUser_Id(org, actor.getId()).orElseThrow(
                () -> new AccessDeniedException("Organization access is required."));
        if (!actor.isEnabled() || !Set.of(OrganizationRole.OWNER, OrganizationRole.ADMIN, OrganizationRole.DISPATCHER).contains(member.getRole()))
            throw new AccessDeniedException("Manager access is required.");
    }
    @Transactional
    public void update(Long org, Long id, String employeeId, String email) {
        requireManager(org, email);
        String cleaned = employeeId == null ? "" : employeeId.trim();
        if (cleaned.isBlank() || cleaned.length() > 50) throw new IllegalArgumentException("Enter an employee ID of 1–50 characters.");
        organizations.findByIdForUpdate(org).orElseThrow(() -> new IllegalStateException("Organization was not found."));
        var driver = drivers.findByIdAndOrganization_Id(id, org).orElseThrow(() -> new IllegalArgumentException("Driver was not found."));
        if (driver.getEmployeeId().equals(cleaned)) return;
        if (drivers.existsByOrganization_IdAndEmployeeId(org, cleaned)) throw new IllegalArgumentException("That employee ID belongs to another driver.");
        // Never leave work hours belonging to the previous identity attached to the driver.
        schedules.clearDriver(org, id);
        driver.changeEmployeeId(cleaned);
    }
}
