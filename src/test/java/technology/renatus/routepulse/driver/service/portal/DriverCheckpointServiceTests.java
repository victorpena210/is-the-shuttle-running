package technology.renatus.routepulse.driver.service.portal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import java.time.Instant;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.*;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.service.DrivingSessionService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.*;

class DriverCheckpointServiceTests {
    DriverPortalAccessService access = mock(DriverPortalAccessService.class);
    DrivingSessionService sessions = mock(DrivingSessionService.class);
    DriverCheckpointService service = new DriverCheckpointService(access, sessions);
    Authentication auth = mock(Authentication.class);
    Driver driver = mock(Driver.class);
    Organization org = mock(Organization.class);
    UserAccount user = mock(UserAccount.class);
    @BeforeEach void setup() {
        when(driver.getId()).thenReturn(10L); when(driver.getOrganization()).thenReturn(org);
        when(org.getId()).thenReturn(100L); when(access.requireCurrentDriver(auth)).thenReturn(driver);
        when(access.requireCurrentUser(auth)).thenReturn(user);
    }
    @Test void previewOffersStartWithoutAssignmentsOrWrites() {
        var preview = service.getPreview(auth, "tag", DrivingActionMethod.NFC);
        assertThat(preview.action()).isEqualTo(CheckpointPreviewAction.START);
        assertThat(preview.assignment()).isNull();
        verify(sessions, never()).startUnscheduledSession(any(), any(), any(), any());
    }
    @Test void startRecordsAuthenticatedDriverAndPhysicalMethod() {
        var result = service.recordCheckpoint(auth, "tag", DrivingActionMethod.NFC, CheckpointAction.START);
        assertThat(result.assignment()).isNull();
        verify(access).validateCheckpointCode(org, "tag"); verify(access).requireActiveDriver(driver);
        verify(sessions).lockOrganization(100L);
        verify(sessions).startUnscheduledSession(eq(driver), eq(user), any(Instant.class), eq(DrivingActionMethod.NFC));
    }
    @Test void resumeOpensANewSegment() {
        service.recordCheckpoint(auth, "tag", DrivingActionMethod.QR, CheckpointAction.RESUME);
        verify(sessions).startUnscheduledSession(eq(driver), eq(user), any(), eq(DrivingActionMethod.QR));
    }
    @Test void staleStopOrHandoffCannotStartRun() {
        for (var action : java.util.Arrays.asList(null, CheckpointAction.END, CheckpointAction.PAUSE, CheckpointAction.TAKEOVER))
            assertThatThrownBy(() -> service.recordCheckpoint(auth, "tag", DrivingActionMethod.NFC, action))
                    .isInstanceOf(IllegalStateException.class);
        verify(sessions, never()).startUnscheduledSession(any(), any(), any(), any());
    }
    @Test void duplicateStartCannotEndAnActiveRun() {
        active(driver);
        assertThatThrownBy(() -> service.recordCheckpoint(auth, "tag", DrivingActionMethod.NFC, CheckpointAction.START))
                .isInstanceOf(IllegalStateException.class);
        verify(sessions, never()).endSession(any(), any(), any(), any(), any(), any());
    }
    @Test void pauseClosesOnlyTheActualSegment() {
        var active = active(driver);
        service.recordCheckpoint(auth, "tag", DrivingActionMethod.QR, CheckpointAction.PAUSE);
        verify(sessions).endSession(isNull(), eq(active), eq(user), any(), eq(DrivingActionMethod.QR), eq(DrivingSessionEndReason.PAUSE));
    }
    @Test void endClosesOnlyTheActualSegment() {
        var active = active(driver);
        service.recordCheckpoint(auth, "tag", DrivingActionMethod.NFC, CheckpointAction.END);
        verify(sessions).endSession(isNull(), eq(active), eq(user), any(), eq(DrivingActionMethod.NFC), eq(DrivingSessionEndReason.COMPLETED));
    }
    @Test void takeoverRequiresExplicitConfirmation() {
        Driver other = mock(Driver.class); when(other.getId()).thenReturn(20L); var active = active(other);
        assertThat(service.getPreview(auth,"tag",DrivingActionMethod.NFC).action()).isEqualTo(CheckpointPreviewAction.TAKEOVER);
        assertThatThrownBy(() -> service.recordCheckpoint(auth,"tag",DrivingActionMethod.NFC,CheckpointAction.START))
                .isInstanceOf(IllegalStateException.class);
        service.recordCheckpoint(auth,"tag",DrivingActionMethod.NFC,CheckpointAction.TAKEOVER);
        verify(sessions).takeOverActiveSession(eq(active),eq(driver),eq(user),any(),eq(DrivingActionMethod.NFC));
    }
    @Test void invalidTagCannotStart() {
        doThrow(new IllegalArgumentException("Invalid tag")).when(access).validateCheckpointCode(org,"bad");
        assertThatThrownBy(() -> service.recordCheckpoint(auth,"bad",DrivingActionMethod.NFC,CheckpointAction.START))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sessions);
    }
    private DrivingSession active(Driver current) {
        var session = mock(DrivingSession.class); when(session.getDriver()).thenReturn(current);
        when(sessions.findActiveSession(100L)).thenReturn(session);
        when(sessions.findActiveSessionForUpdate(100L)).thenReturn(session); return session;
    }
}
