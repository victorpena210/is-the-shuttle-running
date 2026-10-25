package technology.renatus.routepulse.reporting.rankings;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.*;
import technology.renatus.routepulse.driving.model.*;
import technology.renatus.routepulse.organization.model.Organization;

class DrivingCoverageCalculatorTests {
    private final Instant start=Instant.parse("2026-10-25T00:00:00Z");
    private final UserAccount user=mock(UserAccount.class);
    private final Organization org=mock(Organization.class);
    private Driver driver(long id) {
        var driver=mock(Driver.class);when(driver.getId()).thenReturn(id);when(driver.getOrganization()).thenReturn(org);return driver;
    }
    private DrivingCoverageWindow plan(long assigned,String kind,Instant from,Instant to) {
        return new DrivingCoverageWindow(LocalDate.of(2026,10,24),kind,assigned,from,to);
    }
    private DrivingSession run(long driver,int from,int to,DrivingCoverageWindow... plan) {
        var run=DrivingSession.startUnscheduled(org,driver(driver),start.plusSeconds(from*60L),user,DrivingActionMethod.NFC);
        run.captureCoveragePlan(List.of(plan));
        run.end(start.plusSeconds(to*60L),user,DrivingActionMethod.NFC);return run;
    }
    private Map<Long,DrivingCoverageCalculator.Total> totals(DrivingSession... runs) {
        return DrivingCoverageCalculator.calculate(List.of(runs),start.minusSeconds(3600),start.plusSeconds(36000));
    }
    @Test void countsOnlyActualOverlapWithAnotherAssignedDriver() {
        var other=plan(2,"EMPLOYEE_PICKUP",start,start.plusSeconds(10800));
        var own=plan(1,"MORNING_OPENING",start.minusSeconds(3600),start);
        var run=run(1,-15,60,other,own);
        assertThat(totals(run).get(1L)).isEqualTo(new DrivingCoverageCalculator.Total(60,1));
        assertThat(totals(run(2,0,60,other),run(3,0,60))).isEmpty();
        // The snapshot cannot be replaced, including when it originally held no assignments.
        assertThatThrownBy(()->run.captureCoveragePlan(List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->run.getCoveragePlan().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void pausesAndOverlappingCorrectionsDoNotMultiplyTimeOrShiftCount() {
        var other=plan(2,"EMPLOYEE_PICKUP",start,start.plusSeconds(10800));
        var before=run(1,0,30,other);var after=run(1,45,60,other);
        assertThat(totals(before,after).get(1L)).isEqualTo(new DrivingCoverageCalculator.Total(45,1));
        after.reviewTimes(start.plusSeconds(20*60),start.plusSeconds(60*60),user,start.plusSeconds(7200));
        assertThat(totals(before,after).get(1L)).isEqualTo(new DrivingCoverageCalculator.Total(60,1));
        // A correction outside the originally recorded plan cannot invent new coverage.
        after.reviewTimes(start.plusSeconds(4*3600),start.plusSeconds(5*3600),user,start.plusSeconds(6*3600));
        assertThat(totals(before,after).get(1L)).isEqualTo(new DrivingCoverageCalculator.Total(30,1));
    }
    @Test void separatesServicesButAggregatesSubMinuteSegmentsBeforeRounding() {
        var first=plan(2,"MIDDAY_OPENING",start,start.plusSeconds(120));
        var second=plan(2,"SCARE_ACTORS",start.plusSeconds(120),start.plusSeconds(240));
        assertThat(totals(run(1,0,4,first,second)).get(1L)).isEqualTo(new DrivingCoverageCalculator.Total(4,2));
        var a=run(1,0,1,first);a.reviewTimes(start,start.plusSeconds(30),user,start.plusSeconds(600));
        var b=run(1,1,2,first);b.reviewTimes(start.plusSeconds(60),start.plusSeconds(90),user,start.plusSeconds(600));
        assertThat(totals(a,b).get(1L)).isEqualTo(new DrivingCoverageCalculator.Total(1,1));
    }
    @Test void activeLegacyAndUnreviewedTimeoutRecordsDoNotBecomeCompletedCoverage() {
        var other=plan(2,"EMPLOYEE_PICKUP",start,start.plusSeconds(18000));
        var active=DrivingSession.startUnscheduled(org,driver(1),start,user,DrivingActionMethod.QR);
        active.captureCoveragePlan(List.of(other));
        var legacy=DrivingSession.startUnscheduled(org,driver(1),start,user,DrivingActionMethod.NFC);
        legacy.end(start.plusSeconds(3600),user,DrivingActionMethod.NFC);
        var timeout=run(1,0,300,other);
        assertThat(timeout.isDurationReviewNeeded()).isTrue();
        assertThat(totals(active,legacy,timeout)).isEmpty();
        timeout.reviewTimes(start,start.plusSeconds(7200),user,start.plusSeconds(18000));
        assertThat(totals(timeout).get(1L)).isEqualTo(new DrivingCoverageCalculator.Total(120,1));
        var inactivity=DrivingSession.startUnscheduled(org,driver(3),start,user,DrivingActionMethod.NFC);
        inactivity.captureCoveragePlan(List.of(other));
        inactivity.end(start.plusSeconds(600),null,DrivingActionMethod.AUTOMATIC,DrivingSessionEndReason.INACTIVITY_TIMEOUT);
        assertThat(totals(inactivity)).isEmpty();
        inactivity.reviewInactivity(user,"Confirmed these ten minutes of driving",start.plusSeconds(3600));
        assertThat(totals(inactivity).get(3L)).isEqualTo(new DrivingCoverageCalculator.Total(10,1));
    }
    @Test void clipsTheReportingPeriodAndOvernightWindowsAtExactInstants() {
        var other=plan(2,"EMPLOYEE_PICKUP",start.minusSeconds(3600),start.plusSeconds(10800));
        var run=run(1,-30,90,other);
        var result=DrivingCoverageCalculator.calculate(List.of(run),start.plusSeconds(1800),start.plusSeconds(3600));
        assertThat(result.get(1L)).isEqualTo(new DrivingCoverageCalculator.Total(30,1));
        assertThat(DrivingCoverageCalculator.calculate(List.of(run),start.plusSeconds(10800),start.plusSeconds(14400))).isEmpty();
    }
}
