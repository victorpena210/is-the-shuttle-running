package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import technology.renatus.routepulse.scheduling.demand.DriverSuggestionService.*;
import technology.renatus.routepulse.scheduling.demand.ShuttleService;
import technology.renatus.routepulse.calendar.domain.*;
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
        var load=new AutomaticShiftPlanner.Rotation();
        var windows=List.of(window(9,12,A,B),window(19,22,A,B));
        var first=AutomaticShiftPlanner.plan(windows,List.of(),now,load);
        check(first.size()==2&&first.get(0).getDriverId()==1&&first.get(1).getDriverId()==2,"rotate within a day");
        var repeated=AutomaticShiftPlanner.plan(windows,first,now,new AutomaticShiftPlanner.Rotation());
        check(first.equals(repeated),"unchanged input yields unchanged assignments");
        load=new AutomaticShiftPlanner.Rotation();
        var opening=AutomaticShiftPlanner.plan(List.of(window(9,12,A,B)),List.of(),now,load);
        var tomorrow=new Window(DAY.plusDays(1).atTime(9,0),DAY.plusDays(1).atTime(12,0),List.of(A,B),"");
        var next=AutomaticShiftPlanner.plan(List.of(tomorrow),List.of(),now,load);
        check(opening.get(0).getDriverId()!=next.get(0).getDriverId(),"planned duties rotate subsequent days");
        var current=AutomaticShiftPlanner.plan(windows,first,DAY.atTime(10,0),new AutomaticShiftPlanner.Rotation());
        check(current.get(0).equals(first.get(0))&&current.size()==2,"preserve started shift without overlap");
        var closed=AutomaticShiftPlanner.plan(List.of(),first,DAY.atTime(10,0),new AutomaticShiftPlanner.Rotation());
        check(closed.size()==1&&closed.get(0).equals(first.get(0)),"closed or unknown withdraws future only");
        var gaps=AutomaticShiftPlanner.plan(List.of(window(9,12)),List.of(),now,new AutomaticShiftPlanner.Rotation());
        check(gaps.size()==1&&gaps.get(0).getDriverId()==null,"no counter coverage yields unassigned gap");
        var filled=AutomaticShiftPlanner.plan(List.of(window(9,12,A,B)),gaps,DAY.atTime(10,7),new AutomaticShiftPlanner.Rotation());
        check(filled.size()==1&&filled.get(0).getDriverId()==1&&filled.get(0).getStart().equals(DAY.atTime(10,8)),
                "new availability fills the remaining time in a previously unassigned block");
        var longRun=AutomaticShiftPlanner.plan(List.of(window(15,23,A,B)),List.of(),now,new AutomaticShiftPlanner.Rotation());
        check(longRun.size()==2&&longRun.stream().allMatch(s->s.seconds()<=14400),"four hour maximum");
        check(!longRun.get(0).getDriverId().equals(longRun.get(1).getDriverId()),"rotate at long-run boundary");
        var night=new Window(DAY.atTime(23,45),DAY.plusDays(1).atStartOfDay(),List.of(A),"");
        var midnight=AutomaticShiftPlanner.plan(List.of(night),List.of(),now,new AutomaticShiftPlanner.Rotation()).get(0);
        check(midnight.getEnd().toLocalDate().equals(DAY.plusDays(1))&&midnight.seconds()==900,"midnight is next day");
        var early=AutomaticShiftPlanner.plan(List.of(window(9,12,A,B)),List.of(),DAY.atTime(10,7,42),new AutomaticShiftPlanner.Rotation());
        check(early.get(0).getStart().equals(DAY.atTime(10,8)),"never create a new assignment in the past");
        var heavy=new Candidate(1L,"Alex","100",7200,false,null);
        check(AutomaticShiftPlanner.plan(List.of(window(9,12,heavy,B)),List.of(),now,new AutomaticShiftPlanner.Rotation()).get(0).getDriverId()==2,"actual driving matters");
        var eventWindows=List.of(
                new Window(DAY.atTime(15,0),DAY.atTime(17,0),List.of(A,B),"",ShuttleService.MIDDAY_OPENING),
                new Window(DAY.atTime(17,0),DAY.atTime(17,45),List.of(A,B),"",ShuttleService.SCARE_ACTORS));
        var event=AutomaticShiftPlanner.plan(eventWindows,List.of(),now,new AutomaticShiftPlanner.Rotation());
        check(event.size()==2&&event.stream().allMatch(s->s.getDriverId()==1),"midday driver continues through scare actors");
        check(event.get(1).isScareActorTransport()&&event.get(0).getService()==ShuttleService.MIDDAY_OPENING,"service purpose retained");
        check(event.equals(AutomaticShiftPlanner.plan(eventWindows,event,DAY.atTime(17,15),new AutomaticShiftPlanner.Rotation())),"in-progress event remains stable");
        var limitedOpening=List.of(eventWindows.get(0),
                new Window(DAY.atTime(17,0),DAY.atTime(17,45),List.of(B),"",ShuttleService.SCARE_ACTORS));
        var fullRun=AutomaticShiftPlanner.plan(limitedOpening,List.of(),now,new AutomaticShiftPlanner.Rotation());
        check(fullRun.stream().allMatch(s->s.getDriverId()==2),"choose the driver available for opening and scare actors");
        var handoff=AutomaticShiftPlanner.plan(limitedOpening,List.of(event.get(0)),DAY.atTime(16,0),new AutomaticShiftPlanner.Rotation());
        check(handoff.get(0).getDriverId()==1&&handoff.get(1).getDriverId()==2,"a started run stays fixed if changed availability requires an event handoff");
        var splitOpening=List.of(
                new Window(DAY.atTime(15,0),DAY.atTime(16,0),List.of(A,B),"",ShuttleService.MIDDAY_OPENING),
                new Window(DAY.atTime(16,0),DAY.atTime(17,0),List.of(A,B,new Candidate(3L,"Casey","300",0,false,null)),"",ShuttleService.MIDDAY_OPENING),
                eventWindows.get(1));
        check(AutomaticShiftPlanner.plan(splitOpening,List.of(),now,new AutomaticShiftPlanner.Rotation()).stream().allMatch(s->s.getDriverId()==1),
                "another staff arrival must not rotate the opening driver mid-run");
        var policy=new FrightFestTransportPolicy("2026-10-24","2026-11-01");
        for(var date:List.of(LocalDate.of(2026,10,24),DAY))
            check(policy.required(new ParkOperatingDay(date,ParkOperatingStatus.OPEN,null,null,false,"LIVE_API",null)),"inclusive event dates");
        for(var date:List.of(LocalDate.of(2026,10,23),DAY.plusDays(1),DAY.plusYears(1)))
            check(!policy.required(new ParkOperatingDay(date,ParkOperatingStatus.OPEN,null,null,false,"LIVE_API",null)),"event expires and never recurs automatically");
        for(var state:List.of(ParkOperatingStatus.CLOSED,ParkOperatingStatus.UNKNOWN))
            check(!policy.required(new ParkOperatingDay(DAY,state,null,null,false,"LIVE_API",null)),"event requires confirmed open park");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
