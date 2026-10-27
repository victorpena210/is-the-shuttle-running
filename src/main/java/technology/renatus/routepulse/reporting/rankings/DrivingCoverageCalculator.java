package technology.renatus.routepulse.reporting.rankings;

import java.time.*;
import java.util.*;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.model.DrivingCoverageWindow;

/** Descriptive reporting only. No workload events, points, or scheduling inputs. */
final class DrivingCoverageCalculator {
    private DrivingCoverageCalculator() {}
    static Map<Long,Total> calculate(List<DrivingSession> sessions,Instant from,Instant until) {
        return summarize(matches(sessions,from,until));
    }
    static List<Match> matches(List<DrivingSession> sessions,Instant from,Instant until) {
        var matches=new ArrayList<Match>();
        for(var session:sessions) {
            if(session.isActive() || !session.isCoveragePlanCaptured()
                    || session.isDurationReviewNeeded() || session.isInactivityReviewNeeded()) continue;
            for(var window:session.getCoveragePlan()) {
                if(session.getDriver().getId().equals(window.getEffectiveScheduledDriverId())) continue;
                var start=later(from,later(session.getStartedAt(),window.getStart()));
                var end=earlier(until,earlier(session.getEndedAt(),window.getEnd()));
                if(end.isAfter(start)) matches.add(new Match(session,window,new Span(start,end)));
            }
        }
        return List.copyOf(matches);
    }
    static Map<Long,Total> summarize(List<Match> matches) {
        var spans=new HashMap<Long,List<Span>>();
        var services=new HashMap<Long,Map<Service,List<Span>>>();
        for(var match:matches) {
            var session=match.session();var window=match.window();var span=match.span();
            var driver=session.getDriver().getId();
                spans.computeIfAbsent(driver,id->new ArrayList<>()).add(span);
                services.computeIfAbsent(driver,id->new HashMap<>())
                        .computeIfAbsent(new Service(window.getServiceDate(),window.getServiceKey()),key->new ArrayList<>()).add(span);
        }
        var result=new HashMap<Long,Total>();
        spans.forEach((driver,intervals)->result.put(driver,new Total(seconds(intervals)/60,
                services.get(driver).values().stream().filter(s->seconds(s)>=60).count())));
        return Map.copyOf(result);
    }
    // Union overlaps, including corrected records, so one minute is counted once.
    static long seconds(List<Span> spans) {
        Instant start=null,end=null;long total=0;
        for(var span:spans.stream().sorted(Comparator.comparing(Span::start)).toList()) {
            if(end==null || span.start().isAfter(end)) {
                if(end!=null) total+=Duration.between(start,end).getSeconds();
                start=span.start();end=span.end();
            } else end=later(end,span.end());
        }
        return total+(end==null?0:Duration.between(start,end).getSeconds());
    }
    private static Instant earlier(Instant a,Instant b) {return a.isBefore(b)?a:b;}
    private static Instant later(Instant a,Instant b) {return a.isAfter(b)?a:b;}
    record Span(Instant start,Instant end) {}
    record Match(DrivingSession session,DrivingCoverageWindow window,Span span) {}
    private record Service(LocalDate date,String kind) {}
    record Total(long minutes,long shifts) {}
}
