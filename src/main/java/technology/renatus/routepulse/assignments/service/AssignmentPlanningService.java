package technology.renatus.routepulse.assignments.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.assignments.service.planning.DriverScheduleSelector;
import technology.renatus.routepulse.assignments.service.planning.MorningAssignmentScheduler;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.DriverScheduleResult;
import technology.renatus.routepulse.scheduling.domain.MorningDrivingPlan;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.scheduling.planning.MorningDrivingPlanningService;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/** Creates automatic coverage while preserving existing assignments and fairness rules. */
@Service
public class AssignmentPlanningService {

    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final DriverScheduleService driverScheduleService;
    private final UploadedShiftRepository uploadedShiftRepository;
    private final DriverWorkloadService driverWorkloadService;
    private final MorningDrivingPlanningService morningDrivingPlanningService;
    private final DriverScheduleSelector scheduleSelector;
    private final MorningAssignmentScheduler morningAssignmentScheduler;

    public AssignmentPlanningService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            DriverScheduleService driverScheduleService,
            UploadedShiftRepository uploadedShiftRepository,
            DriverWorkloadService driverWorkloadService,
            MorningDrivingPlanningService morningDrivingPlanningService) {
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.driverScheduleService = driverScheduleService;
        this.uploadedShiftRepository = uploadedShiftRepository;
        this.driverWorkloadService = driverWorkloadService;
        this.morningDrivingPlanningService = morningDrivingPlanningService;
        this.scheduleSelector = new DriverScheduleSelector();
        this.morningAssignmentScheduler = new MorningAssignmentScheduler(
                driverScheduleService,
                morningDrivingPlanningService,
                scheduleSelector
        );
    }

    @Transactional
    public List<DriverAssignment> getOrCreateAssignments(
            Long organizationId,
            LocalDate assignmentDate) {
        List<DriverAssignment> existingAssignments = assignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(organizationId, assignmentDate);

        if (!existingAssignments.isEmpty()) {
            morningAssignmentScheduler.applyIfMissing(
                    organizationId,
                    assignmentDate,
                    existingAssignments
            );
            return orderByPeriod(existingAssignments);
        }

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
            return List.of();
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
        DriverSchedule morningDriverSchedule = scheduleSelector.chooseMorning(
                fairestOrder,
                schedulesByDriverId,
                morningPlan
        );
        Driver morningDriver = morningDriverSchedule == null
                ? null
                : loadDriver(organizationId, morningDriverSchedule.driverId());

        ScheduleImport sourceScheduleImport = uploadedShiftRepository
                .findLatestScheduleImportForDate(organizationId, assignmentDate)
                .orElse(null);

        List<DriverAssignment> newAssignments = new ArrayList<>();
        addMorningAssignment(
                assignmentDate,
                morningDriver,
                morningDriverSchedule,
                morningPlan,
                sourceScheduleImport,
                newAssignments
        );
        addAfternoonAssignment(
                organizationId,
                assignmentDate,
                fairestOrder,
                morningDriverSchedule,
                sourceScheduleImport,
                newAssignments
        );

        return orderByPeriod(assignmentRepository.saveAll(newAssignments));
    }

    private void addMorningAssignment(
            LocalDate assignmentDate,
            Driver morningDriver,
            DriverSchedule morningDriverSchedule,
            MorningDrivingPlan morningPlan,
            ScheduleImport sourceScheduleImport,
            List<DriverAssignment> assignments) {
        if (morningDriver == null) {
            return;
        }

        DriverAssignment morningAssignment = DriverAssignment.automatic(
                assignmentDate,
                ShuttlePeriod.MORNING,
                morningDriver,
                sourceScheduleImport
        );
        morningAssignmentScheduler.applyIfAvailable(
                morningPlan,
                morningDriverSchedule,
                morningAssignment
        );
        assignments.add(morningAssignment);
    }

    private void addAfternoonAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            List<DriverSchedule> fairestOrder,
            DriverSchedule morningDriverSchedule,
            ScheduleImport sourceScheduleImport,
            List<DriverAssignment> assignments) {
        DriverSchedule afternoonDriverSchedule = scheduleSelector.chooseAfternoon(
                fairestOrder,
                morningDriverSchedule
        );
        if (afternoonDriverSchedule == null) {
            return;
        }

        Driver afternoonDriver = loadDriver(organizationId, afternoonDriverSchedule.driverId());
        assignments.add(DriverAssignment.automatic(
                assignmentDate,
                ShuttlePeriod.AFTERNOON,
                afternoonDriver,
                sourceScheduleImport
        ));
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
