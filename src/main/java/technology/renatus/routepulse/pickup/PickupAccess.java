package technology.renatus.routepulse.pickup;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.organization.service.*;

@Service @Transactional(readOnly=true)
public class PickupAccess {
    private final CurrentOrganizationService current;
    private final OrganizationActorService actors;
    private final OrganizationMembershipRepository memberships;
    private final DriverRepository drivers;
    public PickupAccess(CurrentOrganizationService current,OrganizationActorService actors,OrganizationMembershipRepository memberships,DriverRepository drivers) {
        this.current=current;this.actors=actors;this.memberships=memberships;this.drivers=drivers;
    }
    public Scope require(Authentication auth,boolean write) {
        var org=current.requireOrganization(auth);var actor=actors.loadActor(org.getId(),auth.getName());
        var membership=memberships.findByOrganization_IdAndUser_Id(org.getId(),actor.getId()).orElseThrow(PickupAccess::denied);
        boolean manager=Set.of(OrganizationRole.OWNER,OrganizationRole.ADMIN,OrganizationRole.DISPATCHER).contains(membership.getRole());
        if(!actor.isEnabled() || (write && !manager)) throw denied();
        if(!manager && (membership.getRole()!=OrganizationRole.DRIVER ||
                drivers.findByOrganization_IdAndUser_Id(org.getId(),actor.getId()).filter(d->d.isActive()).isEmpty())) throw denied();
        return new Scope(org.getId(),org.getDisplayName(),manager);
    }
    private static AccessDeniedException denied(){return new AccessDeniedException("Pickup assistance is available only to active drivers and management in this organization.");}
    public record Scope(Long org,String name,boolean manager) { }
}
