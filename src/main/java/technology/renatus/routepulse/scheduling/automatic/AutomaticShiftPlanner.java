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
            LocalDateTime now,Map<Long,Long> plannedSeconds) {
        var fixed=saved.stream().filter(slot->!slot.getStart().isAfter(now)
                && (slot.getDriverId()!=null || !slot.getEnd().isAfter(now)))
                .sorted(Comparator.comparing(Slot::getStart)).toList();
        // Only the remainder is planned work. Elapsed assignments are not evidence of driving.
        for(var slot:fixed) if(slot.getDriverId()!=null && slot.getEnd().isAfter(now))
            plannedSeconds.merge(slot.getDriverId(),Duration.between(now,slot.getEnd()).getSeconds(),Long::sum);
        var ordered=windows.stream().sorted(Comparator.comparing(Window::start)).toList();
        var loads=new HashMap<Long,Long>();
        for(var window:ordered) for(var candidate:window.candidates())
            loads.putIfAbsent(candidate.driverId(),candidate.seconds()+plannedSeconds.getOrDefault(candidate.driverId(),0L));
        var search=new DaySearch(ordered,fixed,now,loads);
        search.visit(0,null,new ArrayList<>(fixed),0);
        var result=search.best;
        // Commit only the selected plan, never the alternatives explored during planning.
        for(var slot:result) if(slot.getStart().isAfter(now) && slot.getDriverId()!=null)
            plannedSeconds.merge(slot.getDriverId(),slot.seconds(),Long::sum);
        return result.stream().sorted(Comparator.comparing(Slot::getStart)).toList();
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
        private List<Slot> best;
        private DaySearch(List<Window> windows,List<Slot> fixed,LocalDateTime now,Map<Long,Long> loads) {
            this.windows=windows;this.fixed=fixed;this.now=now;this.loads=loads;
            this.best=List.copyOf(fixed);
            this.candidates=new ArrayList<>();
            for(int i=0;i<windows.size();i++) candidates.add(runCandidates(windows,i));
        }
        private void visit(int index,LocalDateTime cursor,ArrayList<Slot> result,long score) {
            if(++visited>MAX_SEARCH_NODES || score>bestScore) return;
            if(index==windows.size()) {
                // Equal-scoring plans retain deterministic chronological rotation ties.
                if(score<bestScore) {bestScore=score;best=List.copyOf(result);}
                return;
            }
            var window=windows.get(index);
            var start=cursor;
            if(start==null) {
                start=window.start().isAfter(now)?window.start():now.withSecond(0).withNano(0).plusMinutes(1);
                // One shuttle: never overlap a frozen assignment, even another driver's run.
                for(var slot:fixed) if(slot.overlaps(start,window.end()) && slot.getEnd().isAfter(start)) start=slot.getEnd();
            }
            if(!window.end().isAfter(start)) {visit(index+1,null,result,score);return;}
            var end=start.plusHours(4).isBefore(window.end())?start.plusHours(4):window.end();
            var choices=new ArrayList<>(candidates.get(index));
            choices.sort(Comparator.comparingLong((Candidate c)->loads.get(c.driverId()))
                    .thenComparing(Candidate::lastDriven,Comparator.nullsFirst(Comparator.naturalOrder()))
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
                // Minimize the sum of squared final workloads. Comparing full-day totals
                // accounts for later sole-driver duties and unequal assignment lengths.
                long added=chosen==null?0:seconds*(2*before+seconds);
                if(chosen!=null) loads.put(chosen.driverId(),before+seconds);
                result.add(new Slot(start,end,chosen==null?null:chosen.driverId(),chosen==null?null:chosen.name(),
                        chosen==null || !window.reason().isBlank()?window.reason():"Automatically assigned",window.service()));
                visit(index,end,result,score+added);
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
