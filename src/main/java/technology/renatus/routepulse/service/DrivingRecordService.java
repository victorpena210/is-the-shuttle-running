package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.AssignmentOutcome;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.ShuttlePeriod;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.DrivingRecordRepository;
import technology.renatus.routepulse.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;

@Service
@Transactional(readOnly = true)
public class DrivingRecordService {

    private final DrivingRecordRepository drivingRecordRepository;
    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final DriverWorkloadService driverWorkloadService;
    private final ZoneId operationsZone;

    public DrivingRecordService(
            DrivingRecordRepository drivingRecordRepository,
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository membershipRepository,
            DriverWorkloadService driverWorkloadService,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.drivingRecordRepository = drivingRecordRepository;
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.driverWorkloadService = driverWorkloadService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public List<DriverAssignment> getDrivingRecords(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate,
            Long driverId) {

        validateDateRange(organizationId, fromDate, toDate);

        if (driverId != null) {
            driverRepository
                    .findByIdAndOrganization_Id(driverId, organizationId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Driver was not found."
                    ));
        }

        return drivingRecordRepository
                .findAllByOrganization_IdAndAssignmentDateBetween(
                        organizationId,
                        fromDate,
                        toDate
                )
                .stream()
                .filter(assignment -> driverId == null
                        || matchesDriver(assignment, driverId))
                .sorted(
                        Comparator
                                .comparing(
                                        DriverAssignment::getAssignmentDate,
                                        Comparator.reverseOrder()
                                )
                                .thenComparingInt(assignment ->
                                        assignment.getPeriod()
                                                == ShuttlePeriod.MORNING
                                                ? 0
                                                : 1
                                )
                )
                .toList();
    }

    public Map<Long, Boolean> getMakeupStatusMap(
            Long organizationId,
            List<DriverAssignment> records) {

        Map<Long, Boolean> result = new LinkedHashMap<>();

        for (DriverAssignment assignment : records) {
            result.put(
                    assignment.getId(),
                    driverWorkloadService.isMakeupOutstandingForAssignment(
                            organizationId,
                            assignment.getScheduledDriver().getId(),
                            assignment.getId()
                    )
            );
        }

        return result;
    }

    @Transactional
    public DriverAssignment reviewAssignmentOutcome(
            Long organizationId,
            Long assignmentId,
            AssignmentOutcome outcome,
            boolean requireMakeup,
            String reason,
            String actorEmail) {

        if (outcome == null) {
            throw new IllegalArgumentException(
                    "Assignment outcome is required."
            );
        }

        validateReason(reason);

        if (requireMakeup
                && outcome != AssignmentOutcome.MISSED
                && outcome != AssignmentOutcome.COVERED_BY_OTHER_DRIVER
                && outcome != AssignmentOutcome.PARTIALLY_COVERED) {
            throw new IllegalArgumentException(
                    "Future make-up priority can only be applied to a missed or covered assignment."
            );
        }

        DriverAssignment assignment = assignmentRepository
                .findByIdAndOrganizationForUpdate(
                        assignmentId,
                        organizationId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Driver assignment was not found."
                ));

        if (assignment.isDriving()) {
            throw new IllegalStateException(
                    "End the active driving session before reviewing this assignment outcome."
            );
        }

        UserAccount actor = loadActor(
                organizationId,
                actorEmail
        );

        LocalDateTime now = LocalDateTime.now(operationsZone);

        assignment.reviewOutcome(
                outcome,
                reason,
                actor,
                now
        );

        driverWorkloadService.setMakeupRequired(
                assignment,
                requireMakeup,
                reason,
                actor,
                now
        );

        return assignment;
    }

    public LocalDate getCurrentOperationsDate() {
        return LocalDate.now(operationsZone);
    }

    private boolean matchesDriver(
            DriverAssignment assignment,
            Long driverId) {

        if (Objects.equals(
                assignment.getScheduledDriver().getId(),
                driverId
        )) {
            return true;
        }

        return assignment.getDrivingSessions().stream()
                .anyMatch(session -> Objects.equals(
                        session.getDriver().getId(),
                        driverId
                ));
    }

    private void validateDateRange(
            Long organizationId,
            LocalDate fromDate,
            LocalDate toDate) {

        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Organization is required."
            );
        }

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "A start date and end date are required."
            );
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "The start date cannot be after the end date."
            );
        }
    }

    private void validateReason(String reason) {
        if (reason != null && reason.strip().length() > 500) {
            throw new IllegalArgumentException(
                    "Outcome reason cannot exceed 500 characters."
            );
        }
    }

    private UserAccount loadActor(
            Long organizationId,
            String actorEmail) {

        if (actorEmail == null || actorEmail.isBlank()) {
            throw new IllegalStateException(
                    "The signed-in user is required for this action."
            );
        }

        UserAccount actor = userAccountRepository
                .findByEmailIgnoreCase(actorEmail.trim())
                .orElseThrow(() -> new IllegalStateException(
                        "The signed-in user could not be found."
                ));

        if (!membershipRepository
                .existsByOrganization_IdAndUser_Id(
                        organizationId,
                        actor.getId()
                )) {
            throw new IllegalStateException(
                    "The signed-in user does not belong to this organization."
            );
        }

        return actor;
    }
}
