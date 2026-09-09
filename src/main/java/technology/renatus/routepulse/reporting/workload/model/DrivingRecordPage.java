package technology.renatus.routepulse.reporting.workload.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;

/** Complete view data for the manager driving-records page. */
public record DrivingRecordPage(
        String organizationName,
        boolean owner,
        List<Driver> drivers,
        List<DriverAssignment> records,
        WorkloadSummary workloadSummary,
        DriverWorkloadRow selectedDriverSummary,
        Map<Long, Boolean> makeupStatus,
        AssignmentOutcome[] outcomes,
        LocalDate fromDate,
        LocalDate toDate,
        Long selectedDriverId,
        String selectedView,
        String filterError) {
}
