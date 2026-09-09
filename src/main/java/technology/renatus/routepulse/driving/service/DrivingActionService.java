package technology.renatus.routepulse.driving.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.organization.service.OrganizationActorService;

/** Starts and ends manager-recorded driving sessions with the original lifecycle checks. */
@Service
public class DrivingActionService {

    private final ZoneId operationsZone;
    private final DriverAssignmentRepository assignmentRepository;
    private final OrganizationActorService organizationActorService;
    private final DrivingSessionService drivingSessionService;

    public DrivingActionService(
            DriverAssignmentRepository assignmentRepository,
            OrganizationActorService organizationActorService,
            DrivingSessionService drivingSessionService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.assignmentRepository = assignmentRepository;
        this.organizationActorService = organizationActorService;
        this.drivingSessionService = drivingSessionService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    @Transactional
    public DriverAssignment startDriving(
            Long organizationId,
            Long assignmentId,
            String actorEmail) {

        DriverAssignment assignment =
                loadAssignment(
                        organizationId,
                        assignmentId
                );

        LocalDateTime now =
                currentOperationsDateTime();

        if (!assignment
                .getAssignmentDate()
                .equals(
                        now.toLocalDate()
                )) {

            throw new IllegalStateException(
                    "Driving can only be started on the assignment date."
            );
        }

        drivingSessionService
                .startSession(
                        organizationId,
                        assignmentId,
                        assignment
                                .getScheduledDriver(),
                        organizationActorService.loadActor(
                                organizationId,
                                actorEmail
                        ),
                        now,
                        DrivingActionMethod.MANAGER
                );

        return assignment;
    }

    @Transactional
    public DriverAssignment endDriving(
            Long organizationId,
            Long assignmentId,
            String actorEmail) {

        DriverAssignment assignment =
                loadAssignment(
                        organizationId,
                        assignmentId
                );

        drivingSessionService
                .endActiveSessionForAssignment(
                        organizationId,
                        assignmentId,
                        organizationActorService.loadActor(
                                organizationId,
                                actorEmail
                        ),
                        currentOperationsDateTime(),
                        DrivingActionMethod.MANAGER
                );

        return assignment;
    }

    private LocalDateTime currentOperationsDateTime() {

        return LocalDateTime.now(
                operationsZone
        );
    }

    private DriverAssignment loadAssignment(
            Long organizationId,
            Long assignmentId) {

        return assignmentRepository
                .findByIdAndOrganization_Id(
                        assignmentId,
                        organizationId
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Driver assignment was not found."
                        )
                );
    }
}
