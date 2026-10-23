package technology.renatus.routepulse.driver.availability;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import technology.renatus.routepulse.dashboard.controller.DashboardController;
import technology.renatus.routepulse.driver.controller.DriverPortalController;
import technology.renatus.routepulse.driver.controller.DriverScheduleController;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;

// Retired: schedule notices are no longer part of the application workflow.
public class AvailabilityNoticeAdvice {
    private final DriverAvailabilityRepository revisions;
    private final AvailabilityUpdateService updates;
    private final CurrentOrganizationService organizations;
    private final DriverPortalAccessService access;
    private final DriverAssignmentRepository assignments;
    public AvailabilityNoticeAdvice(DriverAvailabilityRepository revisions, AvailabilityUpdateService updates,
            CurrentOrganizationService organizations, DriverPortalAccessService access, DriverAssignmentRepository assignments) {
        this.revisions=revisions; this.updates=updates; this.organizations=organizations; this.access=access; this.assignments=assignments;
    }
    @ModelAttribute
    public void notices(Authentication auth, HttpServletRequest request, Model model) {
        if (!request.getMethod().equals("GET") || !java.util.Set.of("/app","/driver","/driver/schedule").contains(request.getRequestURI())) return;
        Long org=organizations.requireOrganizationId(auth);
        Long driver=request.getRequestURI().startsWith("/driver") ? access.requireCurrentDriver(auth).getId() : null;
        long count=revisions.latest(org,updates.today(),updates.today().plusDays(366)).stream()
                .filter(r -> !r.isUseImported() && (driver==null || driver.equals(r.getDriver().getId()))).count();
        long held=assignments.findAllByOrganization_IdAndAssignmentDateBetween(org,updates.today(),updates.today().plusDays(366)).stream()
                .filter(a -> a.isAvailabilityUpdateHold() && (driver==null || driver.equals(a.getScheduledDriver().getId()))).count();
        model.addAttribute("availabilityUpdateCount",count);
        model.addAttribute("availabilityHeldCount",held);
    }
}
