package technology.renatus.routepulse.regression;

import org.junit.jupiter.api.Test;

class CleanupRegressionTests {
    @Test void utcAuditTimestampsDisplayInOperationsZone() {
        CleanupRegressionChecks.timestampDisplayUsesOperationsZone();
    }
    @Test void scheduledAndActualWorkStaySeparate() throws Exception {
        CleanupRegressionChecks.workloadAccounting();
    }
    @Test void auditActorsMustBelongToTheOrganization() throws Exception {
        CleanupRegressionChecks.organizationActorIsolation();
    }
    @Test void calendarCacheAndPersistedFallbackSurviveProviderExtraction() {
        CleanupRegressionChecks.calendarFallbackAndCache();
    }
    @Test void dashboardFragmentsRenderAcrossCalendarStates() throws Exception {
        CleanupRegressionChecks.renderDashboardStates();
    }
    @Test void calendarStatusGatesAssignmentsButNotDemand() throws Exception {
        CleanupRegressionChecks.dashboardPlanningGate();
    }
    @Test void reportingFragmentsRenderWithAndWithoutDriverFilters() throws Exception {
        CleanupRegressionChecks.renderReportingStates();
    }
}
