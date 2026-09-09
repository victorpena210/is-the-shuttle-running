package technology.renatus.routepulse.assignments.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.organization.service.OrganizationActorService;

/** Records reviewed outcomes and future make-up priority under an assignment lock. */
@Service
public class AssignmentReviewService {

    private final ZoneId operationsZone;
    private final DriverAssignmentRepository assignmentRepository;
    private final DriverWorkloadService driverWorkloadService;
    private final OrganizationActorService organizationActorService;

    public AssignmentReviewService(
            DriverAssignmentRepository assignmentRepository,
            DriverWorkloadService driverWorkloadService,
            OrganizationActorService organizationActorService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.assignmentRepository = assignmentRepository;
        this.driverWorkloadService = driverWorkloadService;
        this.organizationActorService = organizationActorService;
        this.operationsZone = ZoneId.of(operationsZone);
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
                && outcome
                        != AssignmentOutcome.MISSED
                && outcome
                        != AssignmentOutcome.COVERED_BY_OTHER_DRIVER
                && outcome
                        != AssignmentOutcome.PARTIALLY_COVERED) {

            throw new IllegalArgumentException(
                    "Future make-up priority can only be applied to a missed or covered assignment."
            );
        }

        DriverAssignment assignment =
                assignmentRepository
                        .findByIdAndOrganizationForUpdate(
                                assignmentId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Driver assignment was not found."
                                )
                        );

        if (assignment.isDriving()) {

            throw new IllegalStateException(
                    "End the active driving session before reviewing this assignment outcome."
            );
        }

        UserAccount actor =
                organizationActorService.loadActor(
                        organizationId,
                        actorEmail
                );

        LocalDateTime now =
                LocalDateTime.now(
                        operationsZone
                );

        assignment.reviewOutcome(
                outcome,
                reason,
                actor,
                now
        );

        driverWorkloadService
                .setMakeupRequired(
                        assignment,
                        requireMakeup,
                        reason,
                        actor,
                        now
                );

        return assignment;
    }

    private void validateReason(
            String reason) {

        if (reason != null
                && reason.strip().length() > 500) {

            throw new IllegalArgumentException(
                    "Outcome reason cannot exceed 500 characters."
            );
        }
    }
}
