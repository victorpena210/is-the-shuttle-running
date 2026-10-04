package technology.renatus.routepulse.driving;

import org.junit.jupiter.api.Test;

class UnscheduledDrivingModelTests {
    @Test
    void unscheduledDomainRulesAndScheduledRegression() throws Exception {
        UnscheduledDrivingChecks.runAll();
    }
}
