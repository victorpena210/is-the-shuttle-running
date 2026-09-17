package technology.renatus.routepulse.assignments.service;

import org.junit.jupiter.api.Test;

class FairnessPlanningTests {
    @Test
    void plannedWorkloadRotationAndProtectedRebalancing() {
        FairnessPlanningChecks.runChecks();
    }
}
