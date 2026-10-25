package technology.renatus.routepulse.scheduling.automatic;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.scheduling.demand.DriverSuggestionService.Candidate;
import technology.renatus.routepulse.scheduling.demand.DriverSuggestionService.Window;
import technology.renatus.routepulse.scheduling.demand.ShuttleService;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.Slot;

class AutomaticShiftPlannerTests {
    private static final LocalDate DAY=LocalDate.of(2026,11,1);
    private static final LocalDateTime BEFORE=DAY.minusDays(1).atStartOfDay();
    private static Candidate driver(long id,long minutes,boolean today) {
        return new Candidate(id,"Driver "+id,""+id,minutes*60,today,null);
    }
    private static Window window(int hour,int minutes,ShuttleService service,Candidate...drivers) {
        var start=DAY.atTime(hour,0);
        return new Window(start,start.plusMinutes(minutes),List.of(drivers),"",service);
    }
    private static Window window(int hour,int minutes,Candidate...drivers) {
        return window(hour,minutes,ShuttleService.EMPLOYEE_PICKUP,drivers);
    }
    @Test void rotatesAndPreservesSafeAssignments(){AutomaticShiftChecks.run();}

    @Test void laterSoleDriverDutyInfluencesMorningWithoutResettingHistory() {
        var a=driver(1,0,false);var b=driver(2,0,false);
        var windows=List.of(window(9,180,ShuttleService.MORNING_OPENING,a,b),
                window(17,45,ShuttleService.SCARE_ACTORS,a));
        var planned=new HashMap<Long,Long>();
        var result=AutomaticShiftPlanner.plan(windows,List.of(),BEFORE,planned);
        assertThat(result).extracting(Slot::getDriverId).containsExactly(2L,1L);
        assertThat(planned).containsExactlyInAnyOrderEntriesOf(Map.of(1L,2700L,2L,10800L));
        var busy=driver(2,600,false);
        assertThat(AutomaticShiftPlanner.plan(List.of(window(9,180,a,busy),window(17,45,a)),
                List.of(),BEFORE,new HashMap<>())).extracting(Slot::getDriverId).containsExactly(1L,1L);
    }

    @Test void unequalLengthsAreBalancedAcrossTheEntireDay() {
        var a=driver(1,60,false);var b=driver(2,0,false);
        // Assigning the currently lightest driver first would leave totals of 5h and 3h.
        // Considering both duties gives each driver 4h including actual history.
        var result=AutomaticShiftPlanner.plan(List.of(window(9,180,a,b),window(19,240,a,b)),
                List.of(),BEFORE,new HashMap<>());
        assertThat(result).extracting(Slot::getDriverId).containsExactly(1L,2L);
    }

    @Test void minutesOutrankHavingDrivenTodayAndEarlierPlannedDaysCarryForward() {
        var a=driver(1,10,true);var b=driver(2,120,false);
        var windows=List.of(window(9,180,a,b));
        assertThat(AutomaticShiftPlanner.plan(windows,List.of(),BEFORE,new HashMap<>()))
                .extracting(Slot::getDriverId).containsExactly(1L);
        var load=new HashMap<>(Map.of(1L,14400L));
        assertThat(AutomaticShiftPlanner.plan(windows,List.of(),BEFORE,load))
                .extracting(Slot::getDriverId).containsExactly(2L);
        assertThat(load).containsExactlyInAnyOrderEntriesOf(Map.of(1L,14400L,2L,10800L));
    }

    @Test void equalMinutesRotateByLastActualDrivingIndependentlyOfInputOrder() {
        var recent=new Candidate(1L,"Alex","1",3600,false,DAY.minusDays(1).atStartOfDay(ZoneOffset.UTC));
        var older=new Candidate(2L,"Blair","2",3600,false,DAY.minusDays(5).atStartOfDay(ZoneOffset.UTC));
        for(var candidates:List.of(new Candidate[]{recent,older},new Candidate[]{older,recent}))
            assertThat(AutomaticShiftPlanner.plan(List.of(window(9,180,candidates)),List.of(),BEFORE,new HashMap<>()))
                    .extracting(Slot::getDriverId).containsExactly(2L);
    }

    @Test void startedAssignmentsStayFixedAndOnlyTheirRemainderIsPlanned() {
        var a=driver(1,60,true);var b=driver(2,0,false);
        var fixed=new Slot(DAY.atTime(9,0),DAY.atTime(12,0),1L,a.name(),"Saved",ShuttleService.MORNING_OPENING);
        var load=new HashMap<Long,Long>();
        var result=AutomaticShiftPlanner.plan(List.of(window(9,180,a,b),window(17,45,a,b)),
                List.of(fixed),DAY.atTime(10,0),load);
        assertThat(result).containsExactly(fixed,result.get(1));
        assertThat(result.get(1).getDriverId()).isEqualTo(2L);
        assertThat(load).containsExactlyInAnyOrderEntriesOf(Map.of(1L,7200L,2L,2700L));
        assertThat(AutomaticShiftPlanner.plan(List.of(),result,DAY.atTime(18,0),new HashMap<>())).isEqualTo(result);
    }

    @Test void coverageGapsAndRequiredCounterClosureReasonsSurvivePlanning() {
        var a=driver(1,0,false);var b=driver(2,0,false);
        var reason="Temporarily close wardrobe for scare-actor transport; reopen when you return.";
        var windows=List.of(window(9,180,a,b),new Window(DAY.atTime(17,0),DAY.atTime(17,45),List.of(a),reason,ShuttleService.SCARE_ACTORS),
                new Window(DAY.atTime(19,0),DAY.atTime(23,0),List.of(),"No available driver covers this full window"));
        var result=AutomaticShiftPlanner.plan(windows,List.of(),BEFORE,new HashMap<>());
        assertThat(result).extracting(Slot::getDriverId).containsExactly(2L,1L,null);
        assertThat(result.get(1).getReason()).isEqualTo(reason);
        assertThat(result.get(2).getReason()).isEqualTo(windows.get(2).reason());
    }

    @Test void keepActorTransportTogetherWhenOneDriverCanFinishTheWholeRun() {
        var a=driver(1,0,false);var b=driver(2,0,false);
        var windows=List.of(window(9,180,ShuttleService.MORNING_OPENING,a,b),
                window(17,30,ShuttleService.SCARE_ACTORS,a,b),
                new Window(DAY.atTime(17,30),DAY.atTime(17,45),List.of(b),"Temporary closure",ShuttleService.SCARE_ACTORS),
                window(19,240,b));
        var result=AutomaticShiftPlanner.plan(windows,List.of(),BEFORE,new HashMap<>());
        assertThat(result).extracting(Slot::getDriverId).containsExactly(1L,2L,2L,2L);
        // Already-started work remains fixed, even if newly changed availability requires a handoff.
        var started=new Slot(DAY.atTime(17,0),DAY.atTime(17,30),1L,a.name(),"Saved",ShuttleService.SCARE_ACTORS);
        var changed=AutomaticShiftPlanner.plan(windows,List.of(started),DAY.atTime(17,10),new HashMap<>());
        assertThat(changed.get(0)).isEqualTo(started);
        assertThat(changed.get(1).getDriverId()).isEqualTo(2L);
    }

    @Test void dailyWorkloadMatchesExhaustiveReferenceAcrossVariedAvailabilityAndDurations() {
        var random=new Random(4242);
        for(int example=0;example<100;example++) {
            var drivers=new Candidate[]{driver(1,random.nextInt(600),false),driver(2,random.nextInt(600),true),driver(3,random.nextInt(600),false)};
            var planned=new HashMap<Long,Long>();
            for(var c:drivers) planned.put(c.driverId(),(long)random.nextInt(180)*60);
            var baseline=new HashMap<Long,Long>();
            for(var c:drivers) baseline.put(c.driverId(),c.seconds()+planned.get(c.driverId()));
            var windows=new ArrayList<Window>();
            for(int hour:new int[]{9,12,15,20}) {
                var available=Arrays.stream(drivers).filter(c->random.nextBoolean()).toArray(Candidate[]::new);
                windows.add(window(hour,15+random.nextInt(8)*15,available));
            }
            long expected=minimumFinalSquares(windows,0,new HashMap<>(baseline));
            var result=AutomaticShiftPlanner.plan(windows,List.of(),BEFORE,planned);
            var actual=new HashMap<>(baseline);
            for(var slot:result) if(slot.getDriverId()!=null) actual.merge(slot.getDriverId(),slot.seconds(),Long::sum);
            assertThat(squares(actual)).as("daily plan %s",example).isEqualTo(expected);
            for(var c:drivers) assertThat(planned.get(c.driverId())).isEqualTo(actual.get(c.driverId())-c.seconds());
        }
    }
    @Test void boundedSearchKeepsACompleteDeterministicPlanForFragmentedRosters() {
        var drivers=new Candidate[]{driver(1,0,false),driver(2,0,false),driver(3,0,false),
                driver(4,0,false),driver(5,0,false),driver(6,0,false)};
        var windows=new ArrayList<Window>();
        for(int hour=0;hour<16;hour++) windows.add(window(hour,15,drivers));
        var planned=new HashMap<Long,Long>();
        var result=AutomaticShiftPlanner.plan(windows,List.of(),BEFORE,planned);
        assertThat(result).hasSize(16).allSatisfy(slot->{
            assertThat(slot.getDriverId()).isBetween(1L,6L);assertThat(slot.seconds()).isEqualTo(900L);
        });
        assertThat(planned.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(16*900L);
        assertThat(AutomaticShiftPlanner.plan(windows,result,BEFORE,new HashMap<>())).isEqualTo(result);
    }
    private static long minimumFinalSquares(List<Window> windows,int index,Map<Long,Long> loads) {
        if(index==windows.size()) return squares(loads);
        var window=windows.get(index);long seconds=Duration.between(window.start(),window.end()).getSeconds();
        if(window.candidates().isEmpty()) return minimumFinalSquares(windows,index+1,loads);
        long best=Long.MAX_VALUE;
        for(var c:window.candidates()) {
            loads.merge(c.driverId(),seconds,Long::sum);
            best=Math.min(best,minimumFinalSquares(windows,index+1,loads));
            loads.merge(c.driverId(),-seconds,Long::sum);
        }
        return best;
    }
    private static long squares(Map<Long,Long> loads) {return loads.values().stream().mapToLong(n->n*n).sum();}
}
