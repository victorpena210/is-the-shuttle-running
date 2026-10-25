package technology.renatus.routepulse.reporting.rankings;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;

/** Organization-scoped totals only: no session details, employee IDs, or account data. */
@Service
@Transactional(readOnly=true)
public class DriverRankingService {
    private final DrivingSessionRepository sessions;
    private final DriverRepository drivers;
    private final ZoneId zone;

    public DriverRankingService(DrivingSessionRepository sessions,DriverRepository drivers,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.sessions=sessions;this.drivers=drivers;this.zone=ZoneId.of(zone);
    }
    public Report build(Long organizationId,Period period) {
        return build(organizationId,period,Instant.now());
    }
    Report build(Long organizationId,Period period,Instant now) {
        LocalDate today=now.atZone(zone).toLocalDate();
        LocalDate from=switch(period) {
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
            case ALL -> sessions.firstRecordedStart(organizationId).map(t->t.atZone(zone).toLocalDate()).filter(d->!d.isAfter(today)).orElse(today);
        };
        Instant start=from.atStartOfDay(zone).toInstant();
        Map<Long,Total> totals=new HashMap<>();
        drivers.findAllByOrganization_IdAndActiveTrueOrderByNameAsc(organizationId)
                .forEach(driver->totals.put(driver.getId(),new Total(driver)));
        for(var session:sessions.findOverlappingForServiceReview(organizationId,start,now)) {
            long seconds=secondsWithin(session,start,now);
            if(seconds==0) continue;
            var total=totals.computeIfAbsent(session.getDriver().getId(),id->new Total(session.getDriver()));
            total.seconds+=seconds;total.segments++;
            total.drivingNow|=session.isActive() && now.isBefore(session.getAutomaticEndAt());
        }
        var sorted=totals.values().stream().sorted(Comparator.comparingLong(Total::minutes).reversed()
                .thenComparing(t->t.driver.getName(),String.CASE_INSENSITIVE_ORDER).thenComparing(t->t.driver.getId())).toList();
        List<Row> rows=new ArrayList<>();
        long previous=-1;int rank=0;
        for(int index=0;index<sorted.size();index++) {
            var total=sorted.get(index);
            if(total.minutes()!=previous) rank=index+1;
            rows.add(new Row(total.minutes()==0?0:rank,total.driver.getId(),total.driver.getName(),total.minutes(),
                    total.segments,total.drivingNow,total.driver.isActive()));
            previous=total.minutes();
        }
        return new Report(period,from,today,now.atZone(zone),List.copyOf(rows),
                rows.stream().mapToLong(Row::minutes).sum(),rows.stream().mapToLong(Row::segments).sum());
    }
    static long secondsWithin(DrivingSession session,Instant start,Instant now) {
        Instant clippedStart=session.getStartedAt().isAfter(start)?session.getStartedAt():start;
        Instant end=session.isActive()?session.getAutomaticEndAt():session.getEndedAt();
        if(end.isAfter(now)) end=now;
        return Math.max(0,Duration.between(clippedStart,end).getSeconds());
    }
    private static String duration(long minutes) {
        return minutes<60?minutes+" min":minutes/60+" hr"+(minutes%60==0?"":" "+minutes%60+" min");
    }
    private static final class Total {
        final Driver driver;long seconds;long segments;boolean drivingNow;
        Total(Driver driver) {this.driver=driver;}
        long minutes() {return seconds/60;}
    }
    public enum Period {
        WEEK("week","This week"),MONTH("month","This month"),ALL("all","All time");
        private final String key,label;
        Period(String key,String label) {this.key=key;this.label=label;}
        public String getKey() {return key;}
        public String getLabel() {return label;}
        public static Period parse(String value) {
            return Arrays.stream(values()).filter(p->p.key.equals(value)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Choose This week, This month, or All time."));
        }
    }
    public record Row(int rank,Long driverId,String name,long minutes,long segments,boolean drivingNow,boolean active) {
        public String durationLabel() {return duration(minutes);}
    }
    public record Report(Period period,LocalDate from,LocalDate through,ZonedDateTime updatedAt,List<Row> rows,long minutes,long segments) {
        public String durationLabel() {return duration(minutes);}
    }
}
