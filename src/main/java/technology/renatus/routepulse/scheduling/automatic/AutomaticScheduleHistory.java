package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import technology.renatus.routepulse.driving.model.DrivingSession;

/** Read-only daily evidence from recorded sessions; never creates a past assignment. */
public final class AutomaticScheduleHistory {
    private AutomaticScheduleHistory() {}

    public static List<RecordedRun> forDay(List<DrivingSession> sessions,LocalDate date,ZoneId zone,Instant now) {
        var dayStart=date.atStartOfDay(zone).toInstant();
        var dayEnd=date.plusDays(1).atStartOfDay(zone).toInstant();
        var result=new ArrayList<RecordedRun>();
        for(var session:sessions) {
            var actualEnd=session.isActive()?earlier(now,session.getAutomaticEndAt()):earlier(now,session.getEndedAt());
            var start=session.getStartedAt().isAfter(dayStart)?session.getStartedAt():dayStart;
            var end=earlier(actualEnd,dayEnd);
            if(!end.isAfter(start)) continue;
            boolean inProgress=session.isActive() && !session.hasReachedDurationLimit(now) && now.isBefore(dayEnd);
            String state=inProgress?"In progress":session.getTimeReviewedAt()!=null?"Manager reviewed"
                    :session.hasReachedDurationLimit(now)?"Duration limit reached":"Recorded";
            result.add(new RecordedRun(session.getId(),session.getDriver().getId(),session.getDriver().getName(),
                    start.atZone(zone),end.atZone(zone),Duration.between(start,end).toMinutes(),inProgress,
                    session.getStartedAt().isBefore(dayStart),state));
        }
        return result.stream().sorted(Comparator.comparing(RecordedRun::start).thenComparing(RecordedRun::driverName)).toList();
    }
    private static Instant earlier(Instant first,Instant second) {return first.isBefore(second)?first:second;}

    public record RecordedRun(Long sessionId,Long driverId,String driverName,ZonedDateTime start,ZonedDateTime end,
            long minutes,boolean inProgress,boolean continuedFromPreviousDay,String state) {}
}
