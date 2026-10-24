package technology.renatus.routepulse.driving;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.service.DrivingSessionDurationJob;
import technology.renatus.routepulse.driving.service.DrivingSessionService;

class DrivingSessionDurationJobTests {
    @Test
    void failureForOneOrganizationDoesNotPreventOtherOverdueSessionsFromClosing() {
        var repository = mock(DrivingSessionRepository.class);
        var sessions = mock(DrivingSessionService.class);
        when(repository.findOverdueOrganizationIds(any())).thenReturn(List.of(1L, 2L));
        when(sessions.endOverdueSessions(eq(1L), any())).thenThrow(new IllegalStateException("temporary failure"));
        new DrivingSessionDurationJob(repository, sessions).check();
        verify(sessions).endOverdueSessions(eq(1L), any());
        verify(sessions).endOverdueSessions(eq(2L), any());
    }
}
