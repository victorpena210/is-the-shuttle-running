package technology.renatus.routepulse.assignments.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentSource;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.assignments.service.planning.AssignmentWindowPlanner;
import technology.renatus.routepulse.assignments.service.planning.DriverScheduleSelector;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.DriverScheduleResult;
import technology.renatus.routepulse.scheduling.domain.MorningDrivingPlan;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.scheduling.planning.MorningDrivingPlanningService;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/**
 * Creates automatic coverage while preserving existing assignments and fairness rules.
 *
 * Morning and afternoon are planned as mostly continuous driving windows with
 * one protected break when a demand-free window of at least 15 minutes exists.
 * Midday is intentionally planned as explicit demand-bearing start/stop runs.
 */
@Service
public class AssignmentPlanningService {

    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final DriverScheduleService driverScheduleService;
    private final UploadedShiftRepository uploadedShiftRepository;
    private final DriverWorkloadService driverWorkloadService;
    private final MorningDrivingPlanningService morningDrivingPlanningService;
    private final TransportationDemandService transportationDemandService;
    private final DriverScheduleSelector scheduleSelector;
    private final AssignmentWindowPlanner windowPlanner;
    private final int protectedBreakMinutes;

    public AssignmentPlanningService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            DriverScheduleService driverScheduleService,
            UploadedShiftRepository uploadedShiftRepository,
            DriverWorkloadService driverWorkloadService,
            MorningDrivingPlanningService morningDrivingPlanningService,
            TransportationDemandService transportationDemandService,
            @Value("${app.driver-break.minimum-free-minutes:15}") int protectedBreakMinutes) {
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.driverScheduleService = driverScheduleService;
        this.uploadedShiftRepository = uploadedShiftRepository;
        this.driverWorkloadService = driverWorkloadService;
        this.morningDrivingPlanningService = morningDrivingPlanningService;
        this.transportationDemandService = transportationDemandService;
        this.scheduleSelector = new DriverScheduleSelector();
        this.windowPlanner = new AssignmentWindowPlanner();
        this.protectedBreakMinutes = Math.max(0, protectedBreakMinutes);
    }

    @Transactional
    public List<DriverAssignment> getOrCreateAssignments(
            Long organizationId,
            LocalDate assignmentDate) {

        List<DriverAssignment> assignments = new ArrayList<>(assignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(organizationId, assignmentDate));

        DriverScheduleResult scheduleResult = driverScheduleService.getDriverSchedulesForDate(
                organizationId,
                assignmentDate
        );
        Map<Long, List<DriverSchedule>> schedulesByDriverId = scheduleResult.scheduledDrivers()
                .stream()
                .collect(Collectors.groupingBy(DriverSchedule::driverId));
        List<DriverSchedule> eligibleDrivers = schedulesByDriverId.values()
                .stream()
                .map(scheduleSelector::earliest)
                .toList();

        if (eligibleDrivers.isEmpty()) {
            return orderByPeriod(assignments);
        }

        // Fairness decides WHO; demand planning and work schedules decide WHEN.
        List<DriverSchedule> fairestOrder = driverWorkloadService.rankEligibleDrivers(
                organizationId,
                assignmentDate,
                eligibleDrivers
        );

        MorningDrivingPlan morningPlan = morningDrivingPlanningService.getPlanForDate(
                organizationId,
                assignmentDate
        );
        TransportationDemandAnalysis demand = transportationDemandService.getDemandForDate(
                organizationId,
                assignmentDate
        );
        ScheduleImport sourceScheduleImport = uploadedShiftRepository
                .findLatestScheduleImportForDate(organizationId, assignmentDate)
                .orElse(null);

        List<DriverAssignment> additions = new ArrayList<>();

        DriverAssignment morning = findAssignment(assignments, ShuttlePeriod.MORNING);
        if (morning == null) {
            morning = createMorningAssignment(
                    organizationId,
                    assignmentDate,
                    fairestOrder,
                    schedulesByDriverId,
                    morningPlan,
                    demand.getMorning(),
                    sourceScheduleImport
            );
            addIfPresent(assignments, additions, morning);
        } else {
            applyMorningDetailsIfMissing(
                    morning,
                    schedulesByDriverId,
                    morningPlan,
                    demand.getMorning()
            );
        }

        Set<Long> usedDriverIds = new HashSet<>();
        addDriverId(usedDriverIds, morning);

        // Keep the prior morning/afternoon fairness relationship intact first.
        DriverAssignment afternoon = findAssignment(assignments, ShuttlePeriod.AFTERNOON);
        if (afternoon == null) {
            afternoon = createContinuousPeriodAssignment(
                    organizationId,
                    assignmentDate,
                    ShuttlePeriod.AFTERNOON,
                    demand.getEvening(),
                    fairestOrder,
                    schedulesByDriverId,
                    usedDriverIds,
                    sourceScheduleImport
            );
            addIfPresent(assignments, additions, afternoon);
        } else {
            applyContinuousDetailsIfMissing(
                    afternoon,
                    demand.getEvening(),
                    schedulesByDriverId
            );
        }
        addDriverId(usedDriverIds, afternoon);

        DriverAssignment midday = findAssignment(assignments, ShuttlePeriod.MIDDAY);
        if (midday == null) {
            midday = createMiddayAssignment(
                    organizationId,
                    assignmentDate,
                    demand.getMidday(),
                    fairestOrder,
                    schedulesByDriverId,
                    usedDriverIds,
                    morning == null ? null : morning.getScheduledDriver().getId(),
                    sourceScheduleImport
            );
            addIfPresent(assignments, additions, midday);
        } else {
            applyMiddayDetailsIfMissing(
                    midday,
                    demand.getMidday(),
                    schedulesByDriverId
            );
        }

        if (!additions.isEmpty()) {
            assignmentRepository.saveAll(additions);
        }

        return orderByPeriod(assignments);
    }

    private DriverAssignment createMorningAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            MorningDrivingPlan morningPlan,
            TransportationDemandPeriod morningDemand,
            ScheduleImport sourceScheduleImport) {

        if (morningPlan == null || !morningPlan.hasDemand()) {
            return null;
        }

        DriverSchedule driverSchedule = scheduleSelector.chooseMorning(
                fairestOrder,
                schedulesByDriverId,
                morningPlan
        );
        if (driverSchedule == null) {
            return null;
        }

        Driver driver = loadDriver(organizationId, driverSchedule.driverId());
        DriverAssignment assignment = DriverAssignment.automatic(
                assignmentDate,
                ShuttlePeriod.MORNING,
                driver,
                sourceScheduleImport
        );

        windowPlanner.applyContinuousPlanWithBreak(
                assignment,
                driverSchedule,
                morningPlan.getScheduledStartTime(),
                morningPlan.getScheduledEndTime(),
                morningDemand.getWindows(),
                protectedBreakMinutes
        );
        return assignment;
    }

    private DriverAssignment createContinuousPeriodAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            ShuttlePeriod period,
            TransportationDemandPeriod demandPeriod,
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            Set<Long> avoidDriverIds,
            ScheduleImport sourceScheduleImport) {

        if (demandPeriod == null
                || !demandPeriod.hasDemand()
                || !demandPeriod.hasRecommendedWindow()) {
            return null;
        }

        DriverSchedule driverSchedule = scheduleSelector.chooseForWindow(
                fairestOrder,
                schedulesByDriverId,
                demandPeriod.getRecommendedStartTime(),
                demandPeriod.getRecommendedEndTime(),
                avoidDriverIds
        );
        if (driverSchedule == null) {
            return null;
        }

        Driver driver = loadDriver(organizationId, driverSchedule.driverId());
        DriverAssignment assignment = DriverAssignment.automatic(
                assignmentDate,
                period,
                driver,
                sourceScheduleImport
        );

        windowPlanner.applyContinuousPlanWithBreak(
                assignment,
                driverSchedule,
                demandPeriod.getRecommendedStartTime(),
                demandPeriod.getRecommendedEndTime(),
                demandPeriod.getWindows(),
                protectedBreakMinutes
        );
        return assignment;
    }

    private DriverAssignment createMiddayAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            TransportationDemandPeriod middayDemand,
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            Set<Long> avoidDriverIds,
            Long overlappingMorningDriverId,
            ScheduleImport sourceScheduleImport) {

        if (middayDemand == null
                || !middayDemand.hasDemand()
                || !middayDemand.hasRecommendedWindow()) {
            return null;
        }

        DriverSchedule driverSchedule = scheduleSelector.chooseForDemandWindows(
                fairestOrder,
                schedulesByDriverId,
                middayDemand.getWindows(),
                avoidDriverIds
        );

        // Prefer a third driver when one is available. If not, allow the
        // afternoon driver before falling back to the morning driver because
        // morning can overlap the first midday turnover window around noon.
        if (driverSchedule == null && overlappingMorningDriverId != null) {
            driverSchedule = scheduleSelector.chooseForDemandWindows(
                    fairestOrder,
                    schedulesByDriverId,
                    middayDemand.getWindows(),
                    Set.of(overlappingMorningDriverId)
            );
        }
        if (driverSchedule == null) {
            driverSchedule = scheduleSelector.chooseForDemandWindows(
                    fairestOrder,
                    schedulesByDriverId,
                    middayDemand.getWindows(),
                    Set.of()
            );
        }
        if (driverSchedule == null) {
            return null;
        }

        Driver driver = loadDriver(organizationId, driverSchedule.driverId());
        DriverAssignment assignment = DriverAssignment.automatic(
                assignmentDate,
                ShuttlePeriod.MIDDAY,
                driver,
                sourceScheduleImport
        );

        if (!windowPlanner.applyMiddayPlan(
                assignment,
                schedulesByDriverId.getOrDefault(driverSchedule.driverId(), List.of()),
                middayDemand.getWindows())) {
            return null;
        }
        return assignment;
    }

    private void applyMorningDetailsIfMissing(
            DriverAssignment assignment,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            MorningDrivingPlan morningPlan,
            TransportationDemandPeriod morningDemand) {

        if (!canGenerateDetails(assignment)
                || morningPlan == null
                || !morningPlan.hasDemand()) {
            return;
        }

        LocalTime requestedStart = assignment.getScheduledStartTime() == null
                ? morningPlan.getScheduledStartTime()
                : assignment.getScheduledStartTime();
        LocalTime requestedEnd = assignment.getScheduledEndTime() == null
                ? morningPlan.getScheduledEndTime()
                : assignment.getScheduledEndTime();

        DriverSchedule schedule = assignedDriverSchedule(
                assignment,
                schedulesByDriverId,
                requestedStart,
                requestedEnd
        );
        if (schedule != null) {
            windowPlanner.applyContinuousPlanWithBreak(
                    assignment,
                    schedule,
                    requestedStart,
                    requestedEnd,
                    morningDemand.getWindows(),
                    protectedBreakMinutes
            );
        }
    }

    private void applyContinuousDetailsIfMissing(
            DriverAssignment assignment,
            TransportationDemandPeriod demandPeriod,
            Map<Long, List<DriverSchedule>> schedulesByDriverId) {

        if (!canGenerateDetails(assignment)
                || demandPeriod == null
                || !demandPeriod.hasDemand()
                || !demandPeriod.hasRecommendedWindow()) {
            return;
        }

        LocalTime requestedStart = assignment.getScheduledStartTime() == null
                ? demandPeriod.getRecommendedStartTime()
                : assignment.getScheduledStartTime();
        LocalTime requestedEnd = assignment.getScheduledEndTime() == null
                ? demandPeriod.getRecommendedEndTime()
                : assignment.getScheduledEndTime();

        DriverSchedule schedule = assignedDriverSchedule(
                assignment,
                schedulesByDriverId,
                requestedStart,
                requestedEnd
        );
        if (schedule != null) {
            windowPlanner.applyContinuousPlanWithBreak(
                    assignment,
                    schedule,
                    requestedStart,
                    requestedEnd,
                    demandPeriod.getWindows(),
                    protectedBreakMinutes
            );
        }
    }

    private void applyMiddayDetailsIfMissing(
            DriverAssignment assignment,
            TransportationDemandPeriod middayDemand,
            Map<Long, List<DriverSchedule>> schedulesByDriverId) {

        if (!canGenerateDetails(assignment)
                || middayDemand == null
                || !middayDemand.hasDemand()
                || !middayDemand.hasRecommendedWindow()) {
            return;
        }

        Long driverId = assignment.getScheduledDriver().getId();
        List<DriverSchedule> driverSchedules = schedulesByDriverId
                .getOrDefault(driverId, List.of());
        if (!driverSchedules.isEmpty()) {
            windowPlanner.applyMiddayPlan(
                    assignment,
                    driverSchedules,
                    middayDemand.getWindows()
            );
        }
    }

    private boolean canGenerateDetails(DriverAssignment assignment) {
        return assignment != null
                && assignment.getAssignmentSource() != AssignmentSource.MANUAL
                && !assignment.hasDrivingSessions()
                && !assignment.hasPlannedDrivingWindows();
    }

    private DriverSchedule assignedDriverSchedule(
            DriverAssignment assignment,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        Long driverId = assignment.getScheduledDriver().getId();
        return scheduleSelector.findBestOverlapping(
                schedulesByDriverId.getOrDefault(driverId, List.of()),
                driverId,
                requestedStart,
                requestedEnd
        );
    }

    private DriverAssignment findAssignment(
            List<DriverAssignment> assignments,
            ShuttlePeriod period) {
        return assignments.stream()
                .filter(assignment -> assignment.getPeriod() == period)
                .findFirst()
                .orElse(null);
    }

    private void addIfPresent(
            List<DriverAssignment> assignments,
            List<DriverAssignment> additions,
            DriverAssignment assignment) {
        if (assignment != null) {
            assignments.add(assignment);
            additions.add(assignment);
        }
    }

    private void addDriverId(Set<Long> usedDriverIds, DriverAssignment assignment) {
        if (assignment != null && assignment.getScheduledDriver() != null) {
            usedDriverIds.add(assignment.getScheduledDriver().getId());
        }
    }

    private Driver loadDriver(Long organizationId, Long driverId) {
        return driverRepository.findByIdAndOrganization_Id(driverId, organizationId)
                .orElseThrow(() -> new IllegalStateException("Eligible driver was not found."));
    }

    private List<DriverAssignment> orderByPeriod(List<DriverAssignment> assignments) {
        return assignments.stream()
                .sorted(Comparator.comparingInt(assignment -> assignment.getPeriod().ordinal()))
                .toList();
    }
}
