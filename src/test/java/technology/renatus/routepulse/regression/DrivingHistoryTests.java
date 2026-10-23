package technology.renatus.routepulse.regression;

import org.junit.jupiter.api.Test;

class DrivingHistoryTests {
    @Test
    void historyWorkflowPreservesRecordsAndRejectsAssignmentWrites() throws Exception {
        DrivingHistoryChecks.verify();
    }
}
