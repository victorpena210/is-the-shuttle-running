package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.calendar.domain.*;
import technology.renatus.routepulse.calendar.model.ParkOperatingDaySnapshot;
import technology.renatus.routepulse.calendar.repository.ParkOperatingDaySnapshotRepository;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleIdentityService;

class AutomaticScheduleServiceTests {
    @Test void confirmationAndRefreshBothTreatStaleOpenSnapshotsAsUnknownWhileKeepingHoursForReview() {
        var now=Instant.now();var date=now.atZone(ZoneOffset.UTC).toLocalDate();
        var calendar=mock(ParkOperatingDayService.class);var snapshots=mock(ParkOperatingDaySnapshotRepository.class);
        var store=mock(AutomaticScheduleStore.class);
        when(calendar.getOperatingDay(any())).thenAnswer(invocation->new ParkOperatingDay(invocation.getArgument(0),
                ParkOperatingStatus.OPEN,LocalTime.of(11,0),LocalTime.of(23,0),false,"PERSISTED_SNAPSHOT","DATE_MISSING_FROM_API"));
        when(snapshots.findByParkIdAndOperatingDate(eq(914),any())).thenReturn(Optional.of(new ParkOperatingDaySnapshot(
                914,date,ParkOperatingStatus.OPEN,LocalTime.of(11,0),LocalTime.of(23,0),false,"SIX_FLAGS_API",now.minusSeconds(90000))));
        var service=new AutomaticScheduleService(calendar,snapshots,store,mock(DriverScheduleIdentityService.class),914,"UTC");
        var observed=service.operatingDay(date);
        assertThat(observed.status()).isEqualTo(ParkOperatingStatus.UNKNOWN);
        assertThat(observed.verificationSource()).isEqualTo("STALE_SNAPSHOT");
        assertThat(observed.openingTime()).isEqualTo(LocalTime.of(11,0));assertThat(observed.closingTime()).isEqualTo(LocalTime.of(23,0));
        service.refresh(1L);
        verify(store).refresh(eq(1L),argThat(days->days.size()==28 && days.stream().allMatch(d->d.status()==ParkOperatingStatus.UNKNOWN)
                && days.get(0).equals(observed)),any());
    }
    @Test void aFreshSnapshotRemainsOpenWithoutNeedingConfirmation() {
        var now=Instant.now();var date=now.atZone(ZoneOffset.UTC).toLocalDate();
        var calendar=mock(ParkOperatingDayService.class);var snapshots=mock(ParkOperatingDaySnapshotRepository.class);
        var day=new ParkOperatingDay(date,ParkOperatingStatus.OPEN,LocalTime.of(11,0),LocalTime.of(23,0),false,"PERSISTED_SNAPSHOT",null);
        when(calendar.getOperatingDay(date)).thenReturn(day);
        when(snapshots.findByParkIdAndOperatingDate(914,date)).thenReturn(Optional.of(new ParkOperatingDaySnapshot(
                914,date,ParkOperatingStatus.OPEN,day.openingTime(),day.closingTime(),false,"SIX_FLAGS_API",now.minusSeconds(3600))));
        var service=new AutomaticScheduleService(calendar,snapshots,mock(AutomaticScheduleStore.class),mock(DriverScheduleIdentityService.class),914,"UTC");
        assertThat(service.operatingDay(date)).isEqualTo(day);
    }
}
