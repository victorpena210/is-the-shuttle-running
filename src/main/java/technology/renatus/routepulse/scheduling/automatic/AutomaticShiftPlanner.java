package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import technology.renatus.routepulse.scheduling.demand.DriverSuggestionService;
import technology.renatus.routepulse.scheduling.demand.ShuttleService;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.Slot;

/** Deterministic chronological rotation; planned seconds are never recorded as actual driving. */
public final class AutomaticShiftPlanner {
    private AutomaticShiftPlanner() {}
    public static List<Slot> plan(List<DriverSuggestionService.Window> windows,List<Slot> saved,
            LocalDateTime now,Map<Long,Long> plannedSeconds) {
        var result=new ArrayList<Slot>();
        var usedToday=new HashSet<Long>();
        // An unfilled block has no driver to preserve. Newly synced availability must
        // be able to fill its remaining time even when the nominal start has passed.
        for (var slot:saved) if (!slot.getStart().isAfter(now)
                && (slot.getDriverId()!=null || !slot.getEnd().isAfter(now))) {
            result.add(slot);
            if(slot.getDriverId()!=null) {
                usedToday.add(slot.getDriverId());
                // Only the not-yet-driven remainder contributes to future fairness.
                if(slot.getEnd().isAfter(now)) plannedSeconds.merge(slot.getDriverId(),
                        Duration.between(now,slot.getEnd()).getSeconds(),Long::sum);
            }
        }
        for (var window:windows.stream().sorted(Comparator.comparing(DriverSuggestionService.Window::start)).toList()) {
            var start=window.start().isAfter(now)?window.start():now.withSecond(0).withNano(0).plusMinutes(1);
            // One shuttle: never overlap a frozen assignment, including another driver's run.
            for(var fixed:result) if(fixed.overlaps(start,window.end())&&!fixed.getStart().isAfter(now))
                start=fixed.getEnd().isAfter(start)?fixed.getEnd():start;
            if(!window.end().isAfter(start)) continue;
            while(start.isBefore(window.end())) {
                var end=start.plusHours(4).isBefore(window.end())?start.plusHours(4):window.end();
                var chosen=window.candidates().stream().min(Comparator
                        .comparing((DriverSuggestionService.Candidate c)->c.droveToday()||usedToday.contains(c.driverId()))
                        .thenComparingLong(c->c.seconds()+plannedSeconds.getOrDefault(c.driverId(),0L))
                        .thenComparing(DriverSuggestionService.Candidate::lastDriven,Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(DriverSuggestionService.Candidate::name,String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(DriverSuggestionService.Candidate::driverId)).orElse(null);
                // An event label inside opening coverage is a change of work, not a required handoff.
                // Continue with the same eligible driver, but never stretch a continuous run past four hours.
                var prior=result.isEmpty()?null:result.get(result.size()-1);
                if(prior!=null && prior.getDriverId()!=null && prior.getEnd().equals(start)
                        && (window.service()==ShuttleService.SCARE_ACTORS || prior.isScareActorTransport())) {
                    var continuing=window.candidates().stream().filter(c->c.driverId().equals(prior.getDriverId())).findFirst().orElse(null);
                    var runStart=prior.getStart();
                    for(int i=result.size()-2;i>=0;i--) {
                        var earlier=result.get(i);
                        if(!earlier.getEnd().equals(runStart)||!Objects.equals(earlier.getDriverId(),prior.getDriverId())) break;
                        runStart=earlier.getStart();
                    }
                    var limit=runStart.plusHours(4);
                    if(continuing!=null && start.isBefore(limit)) {
                        chosen=continuing;
                        if(end.isAfter(limit)) end=limit;
                    }
                }
                result.add(new Slot(start,end,chosen==null?null:chosen.driverId(),chosen==null?null:chosen.name(),
                        chosen==null?window.reason():"Automatically assigned",window.service()));
                if(chosen!=null) {
                    usedToday.add(chosen.driverId());
                    plannedSeconds.merge(chosen.driverId(),Duration.between(start,end).getSeconds(),Long::sum);
                }
                start=end;
            }
        }
        return result.stream().sorted(Comparator.comparing(Slot::getStart)).toList();
    }
}
