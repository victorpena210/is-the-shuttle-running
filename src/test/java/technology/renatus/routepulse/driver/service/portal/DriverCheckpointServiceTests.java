package technology.renatus.routepulse.driver.service.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.service.DrivingSessionService;
import technology.renatus.routepulse.organization.model.Organization;

class DriverCheckpointServiceTests {

    private DriverPortalAccessService accessService;
    private DriverScanAssignmentSelector assignmentSelector;
    private DriverCheckpointStartPolicy startPolicy;
    private DriverAssignmentRepository assignmentRepository;
    private DrivingSessionService drivingSessionService;
    private DriverCheckpointService service;

    private Authentication authentication;
    private Driver driver;
    private UserAccount user;
    private Organization organization;
    private DriverAssignment assignment;

    @BeforeEach
    void setUp() {
        accessService = mock(DriverPortalAccessService.class);
        assignmentSelector = mock(DriverScanAssignmentSelector.class);
        startPolicy = mock(DriverCheckpointStartPolicy.class);
        assignmentRepository = mock(DriverAssignmentRepository.class);
        drivingSessionService = mock(DrivingSessionService.class);
        service = new DriverCheckpointService(
                accessService,
                assignmentSelector,
                startPolicy,
                assignmentRepository,
                drivingSessionService,
                "America/Chicago");

        authentication = mock(Authentication.class);
        driver = mock(Driver.class);
        user = mock(UserAccount.class);
        organization = mock(Organization.class);
        assignment = mock(DriverAssignment.class);

        when(driver.getOrganization()).thenReturn(organization);
        when(driver.getId()).thenReturn(10L);
        when(organization.getId()).thenReturn(100L);
        when(accessService.requireCurrentDriver(authentication)).thenReturn(driver);
        when(accessService.requireCurrentUser(authentication)).thenReturn(user);
        when(assignmentSelector.selectStartAssignment(eq(driver), any(), any()))
                .thenReturn(assignment);
        when(assignment.getScheduledDriver()).thenReturn(driver);
        when(assignment.hasDrivingSessions()).thenReturn(false);
        when(assignmentRepository.findAllForUpdateByOrganizationAndDate(eq(100L), any(LocalDate.class)))
                .thenReturn(List.of(assignment));
        when(assignmentRepository.findAllByOrganization_IdAndAssignmentDate(eq(100L), any(LocalDate.class)))
                .thenReturn(List.of(assignment));
    }

    @Test
    void confirmedNfcStartRecordsSignedInDriverAndPhysicalMethod() {
        DriverCheckpointService.CheckpointResult result = service.recordCheckpoint(
                authentication, "checkpoint-code", DrivingActionMethod.NFC,
                DriverCheckpointService.CheckpointAction.START);

        verify(accessService).requireActiveDriver(driver);
        verify(accessService).validateCheckpointCode(organization, "checkpoint-code");
        verify(drivingSessionService).startSession(
                eq(assignment), eq(driver), eq(user), any(Instant.class), eq(DrivingActionMethod.NFC));
        assertThat(result.action()).isEqualTo(DriverCheckpointService.CheckpointAction.START);
        assertThat(result.method()).isEqualTo(DrivingActionMethod.NFC);
    }

    @Test
    void rejectsExplicitStaleActionBeforeStartingSession() {
        assertThatThrownBy(() -> service.recordCheckpoint(
                authentication,
                "checkpoint-code",
                DrivingActionMethod.NFC,
                DriverCheckpointService.CheckpointAction.END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("driving state changed");

        verify(drivingSessionService, never()).startSession(
                any(), any(), any(), any(Instant.class), any());
    }

    @Test
    void unassignedShiftDoesNotPretendDriverIsCoveringSomeoneElse() {
        when(assignment.getScheduledDriver()).thenReturn(null);

        DriverCheckpointService.CheckpointPreview preview = service.getPreview(
                authentication,
                "checkpoint-code",
                DrivingActionMethod.QR);

        assertThat(preview.coverageRequired()).isFalse();
        assertThat(preview.scheduledDriver()).isNull();
        assertThat(preview.action())
                .isEqualTo(DriverCheckpointService.CheckpointPreviewAction.START);
    }
}
