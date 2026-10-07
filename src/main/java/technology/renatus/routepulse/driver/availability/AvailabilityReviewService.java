package technology.renatus.routepulse.driver.availability;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.assignments.service.planning.LateOpeningPolicy;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.wardrobe.WardrobeHoursService;

@Service
@Transactional(readOnly = true)
public class AvailabilityReviewService {
    public record Update(Long id, LocalDate date, String driver, String hours, String imported,
            boolean importConflict, Instant confirmedAt, String source) { }
    public record Conflict(Long id, Long driverId, String driver, LocalDate date, String period,
            String drivingHours, String workHours, boolean covered, boolean held, boolean started, List<String> replacements) { }
    private final DriverAvailabilityRepository revisions;
    private final AvailabilityUpdateService updates;
    private final DriverScheduleService schedules;
    private final DriverAssignmentRepository assignments;
    private final DriverRepository drivers;
    private final WardrobeHoursService hours;
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    public AvailabilityReviewService(DriverAvailabilityRepository revisions, AvailabilityUpdateService updates,
            DriverScheduleService schedules, DriverAssignmentRepository assignments, DriverRepository drivers,
            WardrobeHoursService hours, OrganizationRepository organizations, UserAccountRepository users) {
        this.revisions=revisions; this.updates=updates; this.schedules=schedules; this.assignments=assignments;
        this.drivers=drivers; this.hours=hours; this.organizations=organizations; this.users=users;
    }
    public List<Update> updates(Long org, Long onlyDriver) {
        return revisions.latest(org,updates.today(),updates.today().plusDays(366)).stream()
                .filter(r -> !r.isUseImported() && (onlyDriver == null || onlyDriver.equals(r.getDriver().getId())))
                .map(r -> {
                    String imported = updates.imported(r.getDriver(),r.getShiftDate());
                    return new Update(r.getId(),r.getShiftDate(),r.getDriver().getName(),r.getHoursLabel(),
                            imported.isEmpty() ? "No imported work shift" : AvailabilityWindows.label(imported),
                            !imported.equals(r.getWorkWindows()),r.getConfirmedAt(),r.getSource());
                }).toList();
    }
    public List<Conflict> conflicts(Long org, Long onlyDriver) {
        Set<LocalDate> dates = revisions.latest(org,updates.today(),updates.today().plusDays(366)).stream()
                .map(DriverAvailabilityRevision::getShiftDate).collect(Collectors.toCollection(TreeSet::new));
        List<Conflict> result = new ArrayList<>();
        for (var date : dates) {
            var roster = schedules.getDriverSchedulesForDate(org,date).scheduledDrivers();
            var duties = assignments.findAllByOrganization_IdAndAssignmentDate(org,date);
            var closing = hours.getHours(org,date).closingTime();
            for (var a : duties) {
                if (!pending(a) || a.getScheduledDriver() == null || onlyDriver != null && !onlyDriver.equals(a.getScheduledDriver().getId())) continue;
                var own = roster.stream().filter(s -> s.driverId().equals(a.getScheduledDriver().getId())).toList();
                boolean covered = safelyCovered(a,roster,duties,closing);
                if (covered && !a.isAvailabilityUpdateHold()) continue;
                var candidates = onlyDriver == null && !covered && !a.hasDrivingSessions()
                        ? replacements(org,a,roster,duties,closing) : List.<String>of();
                result.add(new Conflict(a.getId(),a.getScheduledDriver().getId(),a.getScheduledDriver().getName(),date,a.getPeriodLabel(),
                        drivingLabel(a),own.isEmpty() ? "Off / unavailable" : AvailabilityWindows.label(AvailabilityWindows.encode(own.stream()
                            .map(s -> new AvailabilityWindows.Window(s.startTime(),s.endTime())).toList())),
                        covered,a.isAvailabilityUpdateHold(),a.hasDrivingSessions(),candidates));
            }
        }
        return List.copyOf(result);
    }
    private List<String> replacements(Long org, DriverAssignment original, List<DriverSchedule> roster,
            List<DriverAssignment> duties, LocalTime closing) {
        var others = duties.stream().filter(a -> !a.getId().equals(original.getId())).toList();
        return drivers.findAllByOrganization_IdAndActiveTrueOrderByNameAsc(org).stream()
                .filter(d -> !d.getId().equals(original.getScheduledDriver().getId()))
                .filter(d -> {
                    var proposed = DriverAssignment.automatic(original.getAssignmentDate(),original.getPeriod(),d);
                    proposed.updateScheduledTimes(original.getScheduledStartTime(),original.getScheduledEndTime());
                    proposed.replacePlannedDrivingWindows(original.getPlannedDrivingWindows());
                    return safelyCovered(proposed,roster,others,closing);
                }).map(Driver::getName).toList();
    }
    static boolean safelyCovered(DriverAssignment a, List<DriverSchedule> roster, List<DriverAssignment> duties, LocalTime closing) {
        var own = roster.stream().filter(s -> s.driverId().equals(a.getScheduledDriver().getId())).toList();
        if (a.hasPlannedDrivingWindows()) {
            if (!a.getPlannedDrivingWindows().stream().allMatch(w -> AvailabilityWindows.covers(own,w.getStartTime(),w.getEndTime()))) return false;
        } else if (!AvailabilityWindows.covers(own,a.getScheduledStartTime(),a.getScheduledEndTime())) return false;
        // Existing counter coverage and simultaneous-duty rules also apply to replacements.
        return LateOpeningPolicy.canAssign(a,roster,duties,closing);
    }
    private static boolean pending(DriverAssignment a) {
        return a.getAssignmentOutcome() == AssignmentOutcome.PENDING || a.getAssignmentOutcome() == AssignmentOutcome.IN_PROGRESS;
    }
    private static String drivingLabel(DriverAssignment a) {
        if (a.hasPlannedDrivingWindows()) return AvailabilityWindows.label(AvailabilityWindows.encode(a.getPlannedDrivingWindows().stream()
                .map(w -> new AvailabilityWindows.Window(w.getStartTime(),w.getEndTime())).toList()));
        return a.getScheduledStartTime() == null || a.getScheduledEndTime() == null ? "Times need review"
                : AvailabilityWindows.label(a.getScheduledStartTime() + "-" + a.getScheduledEndTime());
    }
    @Transactional
    public void keepCoveredAssignment(Long org, Long assignmentId, String actor) {
        organizations.findByIdForUpdate(org).orElseThrow();
        var a = assignments.findByIdAndOrganizationForUpdate(assignmentId,org)
                .orElseThrow(() -> new IllegalArgumentException("Assignment was not found."));
        if (a.getAssignmentDate().isBefore(updates.today()) || !pending(a) || !a.isAvailabilityUpdateHold())
            throw new IllegalArgumentException("This assignment has changed. Refresh before reviewing it.");
        var roster = schedules.getDriverSchedulesForDate(org,a.getAssignmentDate()).scheduledDrivers();
        var duties = assignments.findAllByOrganization_IdAndAssignmentDate(org,a.getAssignmentDate());
        if (!safelyCovered(a,roster,duties,hours.getHours(org,a.getAssignmentDate()).closingTime()))
            throw new IllegalArgumentException("Work hours, other driving duties, or counter coverage still conflict. Adjust the assignment or work hours first.");
        a.confirmAvailabilityReview(users.findByEmailIgnoreCase(actor).orElseThrow());
    }
}
