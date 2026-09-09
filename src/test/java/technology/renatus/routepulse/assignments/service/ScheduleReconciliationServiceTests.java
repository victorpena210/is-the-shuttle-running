package technology.renatus.routepulse.assignments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.domain.AssignmentSource;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliation;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliationStatus;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.model.UploadedShift;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

class ScheduleReconciliationServiceTests {

    private static final Long ORGANIZATION_ID = 10L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 14);

    private UploadedShiftRepository uploadedShiftRepository;
    private DriverWorkloadService driverWorkloadService;
    private ScheduleReconciliationService service;
    private ScheduleImport baselineImport;
    private ScheduleImport latestImport;
    private Driver alex;
    private DriverAssignment assignment;

    @BeforeEach
    void setUp() {
        uploadedShiftRepository = mock(UploadedShiftRepository.class);
        driverWorkloadService = mock(DriverWorkloadService.class);
        service = new ScheduleReconciliationService(
                uploadedShiftRepository,
                driverWorkloadService
        );

        baselineImport = importWithId(1L);
        latestImport = importWithId(2L);
        alex = driver(100L, "100", "Alex");
        assignment = assignment(alex, baselineImport, false);

        when(driverWorkloadService.rankEligibleDrivers(
                eq(ORGANIZATION_ID),
                eq(DATE),
                anyList()
        )).thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void identicalSameWeekReuploadDoesNotRequireReview() {
        stubShiftVersion(baselineImport, "100", LocalTime.of(9, 0), LocalTime.of(17, 0));
        stubShiftVersion(latestImport, "100", LocalTime.of(9, 0), LocalTime.of(17, 0));

        List<ScheduleReconciliation> results = service.reconcile(
                ORGANIZATION_ID,
                List.of(assignment),
                List.of(new DriverSchedule(
                        100L, "100", "Alex", DATE,
                        LocalTime.of(9, 0), LocalTime.of(17, 0)
                )),
                TransportationDemandAnalysis.empty(),
                latestImport
        );

        assertThat(results).isEmpty();
        verify(driverWorkloadService, never()).rankEligibleDrivers(
                eq(ORGANIZATION_ID),
                eq(DATE),
                anyList()
        );
    }

    @Test
    void recommendsFairestDriverWhenLatestShiftNoLongerCoversAssignment() {
        Driver jordan = driver(200L, "200", "Jordan");

        stubShiftVersion(baselineImport, "100", LocalTime.of(9, 0), LocalTime.of(17, 0));
        stubShiftVersion(latestImport, "100", LocalTime.of(12, 0), LocalTime.of(20, 0));

        List<ScheduleReconciliation> results = service.reconcile(
                ORGANIZATION_ID,
                List.of(assignment),
                List.of(
                        new DriverSchedule(
                                alex.getId(), "100", "Alex", DATE,
                                LocalTime.of(12, 0), LocalTime.of(20, 0)
                        ),
                        new DriverSchedule(
                                jordan.getId(), "200", "Jordan", DATE,
                                LocalTime.of(9, 0), LocalTime.of(17, 0)
                        )
                ),
                TransportationDemandAnalysis.empty(),
                latestImport
        );

        assertThat(results).hasSize(1);
        ScheduleReconciliation result = results.get(0);
        assertThat(result.status())
                .isEqualTo(ScheduleReconciliationStatus.REPLACEMENT_RECOMMENDED);
        assertThat(result.previousScheduleLabel()).isEqualTo("9:00 AM–5:00 PM");
        assertThat(result.latestScheduleLabel()).isEqualTo("12:00 PM–8:00 PM");
        assertThat(result.drivingWindowLabel()).isEqualTo("9:00 AM–12:00 PM");
        assertThat(result.recommendedDriverId()).isEqualTo(jordan.getId());
        assertThat(result.recommendedDriverName()).isEqualTo("Jordan");
        assertThat(result.requiresAction()).isTrue();
    }

    @Test
    void changedShiftThatStillFullyCoversDrivingWindowCanBeKept() {
        stubShiftVersion(baselineImport, "100", LocalTime.of(9, 0), LocalTime.of(17, 0));
        stubShiftVersion(latestImport, "100", LocalTime.of(8, 0), LocalTime.of(16, 0));

        List<ScheduleReconciliation> results = service.reconcile(
                ORGANIZATION_ID,
                List.of(assignment),
                List.of(new DriverSchedule(
                        100L, "100", "Alex", DATE,
                        LocalTime.of(8, 0), LocalTime.of(16, 0)
                )),
                TransportationDemandAnalysis.empty(),
                latestImport
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status())
                .isEqualTo(ScheduleReconciliationStatus.COVERAGE_STILL_VALID);
        assertThat(results.get(0).hasRecommendation()).isFalse();
        assertThat(results.get(0).requiresAction()).isTrue();
    }

    @Test
    void scheduleChangeAfterDrivingStartedPreservesHistoryWithoutAction() {
        assignment = assignment(alex, baselineImport, true);
        stubShiftVersion(baselineImport, "100", LocalTime.of(9, 0), LocalTime.of(17, 0));
        stubShiftVersion(latestImport, "100", LocalTime.of(12, 0), LocalTime.of(20, 0));

        List<ScheduleReconciliation> results = service.reconcile(
                ORGANIZATION_ID,
                List.of(assignment),
                List.of(),
                TransportationDemandAnalysis.empty(),
                latestImport
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status())
                .isEqualTo(ScheduleReconciliationStatus.HISTORY_PRESERVED);
        assertThat(results.get(0).requiresAction()).isFalse();
    }

    private DriverAssignment assignment(
            Driver scheduledDriver,
            ScheduleImport sourceImport,
            boolean hasDrivingSessions) {

        DriverAssignment result = mock(DriverAssignment.class);
        when(result.getId()).thenReturn(500L);
        when(result.getAssignmentSource()).thenReturn(AssignmentSource.AUTO);
        when(result.getAssignmentDate()).thenReturn(DATE);
        when(result.getPeriod()).thenReturn(ShuttlePeriod.MORNING);
        when(result.getScheduledDriver()).thenReturn(scheduledDriver);
        when(result.getSourceScheduleImport()).thenReturn(sourceImport);
        when(result.getReviewedScheduleImport()).thenReturn(null);
        when(result.getScheduledStartTime()).thenReturn(LocalTime.of(9, 0));
        when(result.getScheduledEndTime()).thenReturn(LocalTime.of(12, 0));
        when(result.hasDrivingSessions()).thenReturn(hasDrivingSessions);
        return result;
    }

    private Driver driver(Long id, String employeeId, String name) {
        Driver driver = mock(Driver.class);
        when(driver.getId()).thenReturn(id);
        when(driver.getEmployeeId()).thenReturn(employeeId);
        when(driver.getName()).thenReturn(name);
        return driver;
    }

    private ScheduleImport importWithId(Long id) {
        ScheduleImport scheduleImport = mock(ScheduleImport.class);
        when(scheduleImport.getId()).thenReturn(id);
        return scheduleImport;
    }

    private void stubShiftVersion(
            ScheduleImport scheduleImport,
            String employeeId,
            LocalTime start,
            LocalTime end) {

        UploadedShift shift = mock(UploadedShift.class);
        when(shift.getStartTime()).thenReturn(start);
        when(shift.getEndTime()).thenReturn(end);

        when(uploadedShiftRepository
                .findAllByOrganization_IdAndScheduleImport_IdAndShiftDateAndEmployeeIdOrderByStartTimeAsc(
                        ORGANIZATION_ID,
                        scheduleImport.getId(),
                        DATE,
                        employeeId
                ))
                .thenReturn(List.of(shift));
    }
}
