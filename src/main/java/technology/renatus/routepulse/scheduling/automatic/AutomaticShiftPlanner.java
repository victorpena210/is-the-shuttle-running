package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import technology.renatus.routepulse.scheduling.demand.DriverSuggestionService.Candidate;
import technology.renatus.routepulse.scheduling.demand.DriverSuggestionService.Window;
import technology.renatus.routepulse.scheduling.demand.ShuttleService;
import technology.renatus.routepulse.scheduling.automatic.AutomaticScheduleDay.Slot;

/** Whole-day workload balancing; planned seconds are never recorded as actual driving. */
public final class AutomaticShiftPlanner {
    private AutomaticShiftPlanner() {}
    public static List<Slot> plan(List<Window> windows,List<Slot> saved,
            LocalDateTime now,Rotation rotation) {
        var fixed=saved.stream().filter(slot->!slot.getStart().isAfter(now)
                && (slot.getDriverId()!=null || !slot.getEnd().isAfter(now)))
                .sorted(Comparator.comparing(Slot::getStart)).toList();
        var ordered=windows.stream().sorted(Comparator.comparing(Window::start)).toList();
        var loads=new HashMap<Long,Long>();
        for(var window:ordered) for(var candidate:window.candidates())
            loads.putIfAbsent(candidate.driverId(),candidate.dailySeconds());
        // Today's actual time plus only the remaining part of a started assignment.
        // Earlier days and future plans never add to this day's workload.
        for(var slot:fixed) if(slot.getDriverId()!=null && slot.getEnd().isAfter(now))
            loads.merge(slot.getDriverId(),Duration.between(now,slot.getEnd()).getSeconds(),Long::sum);
        var search=new DaySearch(ordered,fixed,now,loads,rotation);
        search.visit(0,null,new ArrayList<>(fixed),0,0);
        var result=search.best;
        // Rotate future days from the selected plan. Elapsed plans cannot overwrite
        // the actual driver recorded by the checkpoint (including someone covering).
        for(var slot:result) if(slot.getStart().isAfter(now) && slot.getDriverId()!=null)
            rotation.remember(slot.getDriverId(),slot.getService(),slot.getStart().toLocalDate());
        return result.stream().sorted(Comparator.comparing(Slot::getStart)).toList();
    }

    /** Per-refresh duty rotation, deliberately separate from daily driving totals. */
    public static final class Rotation {
        private record Key(Long driver,ShuttleService service) {}
        private final Map<Key,LocalDate> planned=new HashMap<>();
        private static ShuttleService role(ShuttleService service) {
            return isOpening(service)?ShuttleService.MORNING_OPENING:service;
        }
        private void remember(Long driver,ShuttleService service,LocalDate date) {
            planned.merge(new Key(driver,role(service)),date,(a,b)->a.isAfter(b)?a:b);
        }
        private LocalDate last(Candidate driver,ShuttleService service,LocalDate date) {
            var last=LocalDate.MIN;
            for(var entry:driver.lastServices().entrySet())
                if(role(entry.getKey())==role(service) && entry.getValue().isBefore(date) && entry.getValue().isAfter(last)) last=entry.getValue();
            var next=planned.get(new Key(driver.driverId(),role(service)));
            return next!=null && next.isBefore(date) && next.isAfter(last)?next:last;
        }
        private long penalty(Candidate driver,Window window) {
            var last=last(driver,window.service(),window.start().toLocalDate());
            return window.candidates().stream().filter(c->last(c,window.service(),window.start().toLocalDate()).isBefore(last)).count();
        }
    }

    private static final class DaySearch {
        // Typical days have 2–4 decisions. Bound unusual, fragmented rosters while retaining
        // the best complete feasible plan found, including the initial chronological plan.
        private static final int MAX_SEARCH_NODES=50_000;
        private final List<Window> windows;
        private final List<List<Candidate>> candidates;
        private final List<Slot> fixed;
        private final LocalDateTime now;
        private final Map<Long,Long> loads;
        private int visited;
        private long bestScore=Long.MAX_VALUE;
        private long bestRotation=Long.MAX_VALUE;
        private final Rotation rotation;
        private List<Slot> best;
        private DaySearch(List<Window> windows,List<Slot> fixed,LocalDateTime now,Map<Long,Long> loads,Rotation rotation) {
            this.rotation=rotation;
            this.windows=windows;this.fixed=fixed;this.now=now;this.loads=loads;
            this.best=List.copyOf(fixed);
            this.candidates=new ArrayList<>();
            for(int i=0;i<windows.size();i++) candidates.add(runCandidates(windows,i));
        }
        private void visit(int index,LocalDateTime cursor,ArrayList<Slot> result,long score,long rotationScore) {
            if(++visited>MAX_SEARCH_NODES || score>bestScore) return;
            if(index==windows.size()) {
                // Equal-scoring plans retain deterministic chronological rotation ties.
                if(score<bestScore || (score==bestScore && rotationScore<bestRotation)) {
                    bestScore=score;bestRotation=rotationScore;best=List.copyOf(result);
                }
                return;
            }
            var window=windows.get(index);
            var start=cursor;
            if(start==null) {
                start=window.start().isAfter(now)?window.start():now.withSecond(0).withNano(0).plusMinutes(1);
                // One shuttle: never overlap a frozen assignment, even another driver's run.
                for(var slot:fixed) if(slot.overlaps(start,window.end()) && slot.getEnd().isAfter(start)) start=slot.getEnd();
            }
            if(!window.end().isAfter(start)) {visit(index+1,null,result,score,rotationScore);return;}
            var end=start.plusHours(4).isBefore(window.end())?start.plusHours(4):window.end();
            var choices=new ArrayList<>(candidates.get(index));
            choices.sort(Comparator.comparingLong((Candidate c)->loads.get(c.driverId()))
                    .thenComparingLong(c->rotation.penalty(c,window))
                    .thenComparingLong(Candidate::seconds).thenComparing(Candidate::lastDriven,Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(Candidate::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Candidate::driverId));
            // Keep opening and its adjacent scare-actor run together when eligible,
            // including a run already in progress. A service label is not a handoff.
            var prior=result.isEmpty()?null:result.get(result.size()-1);
            if(prior!=null && prior.getDriverId()!=null && prior.getEnd().equals(start)
                    && (window.service()==ShuttleService.SCARE_ACTORS
                        || (isOpening(window.service()) && window.service()==prior.getService()))) {
                var continuing=window.candidates().stream().filter(c->c.driverId().equals(prior.getDriverId())).findFirst().orElse(null);
                var runStart=prior.getStart();
                for(int i=result.size()-2;i>=0;i--) {
                    var earlier=result.get(i);
                    if(!earlier.getEnd().equals(runStart)||!Objects.equals(earlier.getDriverId(),prior.getDriverId())) break;
                    runStart=earlier.getStart();
                }
                var limit=runStart.plusHours(4);
                if(continuing!=null && start.isBefore(limit)) {
                    choices=new ArrayList<>(List.of(continuing));
                    if(end.isAfter(limit)) end=limit;
                }
            }
            if(choices.isEmpty()) choices.add(null);
            long seconds=Duration.between(start,end).getSeconds();
            for(var chosen:choices) {
                long before=chosen==null?0:loads.get(chosen.driverId());
                // Daily balance always wins; duty rotation breaks equally balanced plans.
                // Considering the whole day accounts for later sole-driver duties.
                long added=chosen==null?0:seconds*(2*before+seconds);
                if(chosen!=null) loads.put(chosen.driverId(),before+seconds);
                result.add(new Slot(start,end,chosen==null?null:chosen.driverId(),chosen==null?null:chosen.name(),
                        chosen==null || !window.reason().isBlank()?window.reason():"Automatically assigned",window.service()));
                visit(index,end,result,score+added,rotationScore+(chosen==null?0:seconds*rotation.penalty(chosen,window)));
                result.remove(result.size()-1);
                if(chosen!=null) loads.put(chosen.driverId(),before);
                if(visited>=MAX_SEARCH_NODES) break;
            }
        }
    }
    private static boolean isOpening(ShuttleService service) {
        return service==ShuttleService.MORNING_OPENING || service==ShuttleService.MIDDAY_OPENING;
    }
    private static List<Candidate> runCandidates(
            List<Window> windows,int index) {
        var first=windows.get(index);
        if(!isOpening(first.service()) && first.service()!=ShuttleService.SCARE_ACTORS) return first.candidates();
        var eligible=new ArrayList<>(first.candidates());
        var end=first.end();
        for(int i=index+1;i<windows.size();i++) {
            var next=windows.get(i);
            if(!next.start().equals(end) || !(next.service()==first.service()
                    || (first.service()==ShuttleService.MIDDAY_OPENING && next.service()==ShuttleService.SCARE_ACTORS))) break;
            eligible.removeIf(c->next.candidates().stream().noneMatch(other->other.driverId().equals(c.driverId())));
            end=next.end();
        }
        // If nobody covers the entire run, retain feasible coverage and report gaps.
        return eligible.isEmpty()?first.candidates():eligible;
    }
}
