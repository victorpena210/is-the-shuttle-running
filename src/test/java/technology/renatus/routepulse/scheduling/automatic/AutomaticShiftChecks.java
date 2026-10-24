package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import technology.renatus.routepulse.scheduling.demand.DriverSuggestionService.*;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.Slot;

public final class AutomaticShiftChecks {
    private static final LocalDate DAY=LocalDate.of(2026,11,1);
    private static final Candidate A=new Candidate(1L,"Alex","100",0,false,null);
    private static final Candidate B=new Candidate(2L,"Blair","200",0,false,null);
    private static Window window(int start,int end,Candidate...drivers) {
        return new Window(DAY.atTime(start,0),DAY.atTime(end,0),List.of(drivers),drivers.length==0?"Another counter worker is needed":"");
    }
    public static void main(String[] args) {run();System.out.println("PASS: automatic rotation, coverage gaps, idempotency, started shifts, duration limits and overnight windows");}
    public static void run() {
        var now=DAY.minusDays(1).atStartOfDay();
        var load=new HashMap<Long,Long>();
        var windows=List.of(window(9,12,A,B),window(19,22,A,B));
        var first=AutomaticShiftPlanner.plan(windows,List.of(),now,load);
        check(first.size()==2&&first.get(0).getDriverId()==1&&first.get(1).getDriverId()==2,"rotate within a day");
        var repeated=AutomaticShiftPlanner.plan(windows,first,now,new HashMap<>());
        check(first.equals(repeated),"unchanged input yields unchanged assignments");
        load=new HashMap<>();
        var opening=AutomaticShiftPlanner.plan(List.of(window(9,12,A,B)),List.of(),now,load);
        var tomorrow=new Window(DAY.plusDays(1).atTime(9,0),DAY.plusDays(1).atTime(12,0),List.of(A,B),"");
        var next=AutomaticShiftPlanner.plan(List.of(tomorrow),List.of(),now,load);
        check(opening.get(0).getDriverId()!=next.get(0).getDriverId(),"planned hours rotate subsequent days");
        var current=AutomaticShiftPlanner.plan(windows,first,DAY.atTime(10,0),new HashMap<>());
        check(current.get(0).equals(first.get(0))&&current.size()==2,"preserve started shift without overlap");
        var closed=AutomaticShiftPlanner.plan(List.of(),first,DAY.atTime(10,0),new HashMap<>());
        check(closed.size()==1&&closed.get(0).equals(first.get(0)),"closed or unknown withdraws future only");
        var gaps=AutomaticShiftPlanner.plan(List.of(window(9,12)),List.of(),now,new HashMap<>());
        check(gaps.size()==1&&gaps.get(0).getDriverId()==null,"no counter coverage yields unassigned gap");
        var longRun=AutomaticShiftPlanner.plan(List.of(window(15,23,A,B)),List.of(),now,new HashMap<>());
        check(longRun.size()==2&&longRun.stream().allMatch(s->s.seconds()<=14400),"four hour maximum");
        check(!longRun.get(0).getDriverId().equals(longRun.get(1).getDriverId()),"rotate at long-run boundary");
        var night=new Window(DAY.atTime(23,45),DAY.plusDays(1).atStartOfDay(),List.of(A),"");
        var midnight=AutomaticShiftPlanner.plan(List.of(night),List.of(),now,new HashMap<>()).get(0);
        check(midnight.getEnd().toLocalDate().equals(DAY.plusDays(1))&&midnight.seconds()==900,"midnight is next day");
        var early=AutomaticShiftPlanner.plan(List.of(window(9,12,A,B)),List.of(),DAY.atTime(10,7,42),new HashMap<>());
        check(early.get(0).getStart().equals(DAY.atTime(10,8)),"never create a new assignment in the past");
        var heavy=new Candidate(1L,"Alex","100",7200,false,null);
        check(AutomaticShiftPlanner.plan(List.of(window(9,12,heavy,B)),List.of(),now,new HashMap<>()).get(0).getDriverId()==2,"actual driving matters");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
